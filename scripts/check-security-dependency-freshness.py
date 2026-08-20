#!/usr/bin/env python3
"""Gate security-critical dependency freshness against reviewed local floors."""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from datetime import date, datetime
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CONFIG = ROOT / ".github" / "security-dependency-freshness.json"
DEFAULT_CATALOG = ROOT / "gradle" / "libs.versions.toml"
VERSION_RE = re.compile(r"^([0-9]+(?:\.[0-9]+)*)(?:-([0-9A-Za-z][0-9A-Za-z.-]*))?(?:\+.*)?$")


@dataclass(frozen=True)
class ParsedVersion:
    numbers: tuple[int, ...]
    suffix: str


# Catalog keys whose version carries a security consequence: the crypto that
# wraps every local secret, the storage engines that hold the personal
# dictionary and clipboard, and the build toolchain that produces the APK.
# Every key here must have a floor in the freshness policy, so adding a
# security-relevant pin cannot quietly ship without one.
SECURITY_RELEVANT_CATALOG_KEYS = frozenset(
    {
        "android-gradle-plugin",
        "androidx-room",
        "androidx-sqlite",
        "kotlin",
        "ksp",
        "sqlcipher-android",
        "tink-android",
    }
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Check security-critical dependency versions against reviewed freshness floors.",
    )
    parser.add_argument(
        "--catalog",
        default=str(DEFAULT_CATALOG),
        help="Gradle version catalog path. Defaults to gradle/libs.versions.toml.",
    )
    parser.add_argument(
        "--config",
        default=str(DEFAULT_CONFIG),
        help="Security dependency freshness policy JSON path.",
    )
    parser.add_argument(
        "--today",
        default=date.today().isoformat(),
        help="Date used for override expiry checks, in YYYY-MM-DD format.",
    )
    return parser.parse_args()


def read_text(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8-sig")
    except FileNotFoundError as exc:
        raise ValueError(f"missing required file: {path.as_posix()}") from exc


def read_json(path: Path) -> object:
    try:
        return json.loads(read_text(path))
    except json.JSONDecodeError as exc:
        raise ValueError(f"{path.as_posix()}: invalid JSON: {exc}") from exc


def parse_iso_date(value: object, label: str) -> date:
    if not isinstance(value, str):
        raise ValueError(f"{label}: expected YYYY-MM-DD string")
    try:
        return datetime.strptime(value, "%Y-%m-%d").date()
    except ValueError as exc:
        raise ValueError(f"{label}: expected YYYY-MM-DD") from exc


def parse_version_catalog(path: Path) -> dict[str, str]:
    versions: dict[str, str] = {}
    in_versions = False
    for raw in read_text(path).splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("[") and line.endswith("]"):
            in_versions = line == "[versions]"
            continue
        if not in_versions:
            continue
        match = re.match(r"([A-Za-z0-9_.-]+)\s*=\s*\"([^\"]+)\"", line)
        if match:
            versions[match.group(1)] = match.group(2)
    return versions


def parse_version(value: str) -> ParsedVersion:
    match = VERSION_RE.match(value)
    if match is None:
        raise ValueError(f"unsupported version format: {value!r}")
    return ParsedVersion(
        numbers=tuple(int(part) for part in match.group(1).split(".")),
        suffix=match.group(2) or "",
    )


def compare_versions(left: str, right: str) -> int:
    left_version = parse_version(left)
    right_version = parse_version(right)
    max_len = max(len(left_version.numbers), len(right_version.numbers))
    left_numbers = left_version.numbers + (0,) * (max_len - len(left_version.numbers))
    right_numbers = right_version.numbers + (0,) * (max_len - len(right_version.numbers))
    if left_numbers < right_numbers:
        return -1
    if left_numbers > right_numbers:
        return 1
    if left_version.suffix == right_version.suffix:
        return 0
    if not left_version.suffix:
        return 1
    if not right_version.suffix:
        return -1
    return -1 if left_version.suffix < right_version.suffix else 1


def require_string(mapping: dict[str, Any], field: str, label: str) -> str:
    value = mapping.get(field)
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{label}: missing required field {field}")
    return value.strip()


def load_policy(path: Path, today: date) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    raw = read_json(path)
    if not isinstance(raw, dict):
        raise ValueError(f"{path.as_posix()}: expected top-level JSON object")
    dependencies = raw.get("dependencies")
    overrides = raw.get("overrides", [])
    if not isinstance(dependencies, list) or not dependencies:
        raise ValueError(f"{path.as_posix()}: dependencies must be a non-empty array")
    if not isinstance(overrides, list):
        raise ValueError(f"{path.as_posix()}: overrides must be an array")
    for index, dependency in enumerate(dependencies):
        if not isinstance(dependency, dict):
            raise ValueError(f"{path.as_posix()}: dependencies[{index}] must be an object")
        label = f"{path.as_posix()}: dependencies[{index}]"
        require_string(dependency, "catalogKey", label)
        require_string(dependency, "module", label)
        require_string(dependency, "minimumVersion", label)
        require_string(dependency, "reason", label)
        reviewed_on = parse_iso_date(dependency.get("reviewedOn"), f"{label}.reviewedOn")
        if reviewed_on > today:
            raise ValueError(f"{label}.reviewedOn: cannot be in the future")
        parse_version(dependency["minimumVersion"])
    for index, override in enumerate(overrides):
        if not isinstance(override, dict):
            raise ValueError(f"{path.as_posix()}: overrides[{index}] must be an object")
        # An override is matched on catalogKey *and* module, so one missing
        # either field would silently never apply. Reject it here instead.
        label = f"{path.as_posix()}: overrides[{index}]"
        require_string(override, "catalogKey", label)
        require_string(override, "module", label)
    return dependencies, overrides


def active_override_for(
    catalog_key: str,
    module: str,
    overrides: list[dict[str, Any]],
    today: date,
    errors: list[str],
    warnings: list[str],
) -> dict[str, Any] | None:
    active: dict[str, Any] | None = None
    for index, override in enumerate(overrides):
        # Both fields must match. Requiring only one to match let an override
        # written for a different coordinate suppress this one's floor.
        if override.get("catalogKey") != catalog_key or override.get("module") != module:
            continue
        label = f".github/security-dependency-freshness.json overrides[{index}]"
        try:
            owner = require_string(override, "owner", label)
            require_string(override, "rationale", label)
            expiry = parse_iso_date(override.get("expiry"), f"{label}.expiry")
        except ValueError as exc:
            errors.append(f"{catalog_key}: override missing required field: {exc}")
            continue
        if expiry < today:
            warnings.append(f"{catalog_key}: override owned by {owner} expired on {expiry.isoformat()}")
            continue
        active = override
    return active


def main() -> int:
    args = parse_args()
    catalog_path = Path(args.catalog).resolve()
    config_path = Path(args.config).resolve()
    try:
        today = parse_iso_date(args.today, "--today")
        catalog = parse_version_catalog(catalog_path)
        dependencies, overrides = load_policy(config_path, today)
    except ValueError as exc:
        print(f"::error::security-dependency-freshness: {exc}", file=sys.stderr)
        print("security dependency freshness: FAIL (invalid configuration)", file=sys.stderr)
        return 1

    errors: list[str] = []
    warnings: list[str] = []
    checked = 0

    # A floor only protects what it names. Listing one dependency and printing
    # "OK (1 checked dependency floor(s))" reads like a pass while the crypto,
    # storage and build-toolchain pins carry no floor at all, so require every
    # security-relevant catalog key to be covered.
    # Scoped to keys the catalog actually pins: a dependency that is not used
    # carries no risk, and synthetic fixture catalogs legitimately omit most of
    # them. What must not happen is a security-relevant pin shipping unfloored.
    floored_keys = {dependency["catalogKey"] for dependency in dependencies}
    for catalog_key in sorted(SECURITY_RELEVANT_CATALOG_KEYS & catalog.keys()):
        if catalog_key not in floored_keys:
            errors.append(
                f"{catalog_key}: security-relevant dependency has no freshness floor in "
                f"{config_path.as_posix()}; add a minimumVersion/reviewedOn/reason entry"
            )

    for dependency in dependencies:
        catalog_key = dependency["catalogKey"]
        module = dependency["module"]
        minimum_version = dependency["minimumVersion"]
        current_version = catalog.get(catalog_key)
        if current_version is None:
            errors.append(f"{catalog_key}: missing from {catalog_path.as_posix()}")
            continue
        try:
            is_current = compare_versions(current_version, minimum_version) >= 0
        except ValueError as exc:
            errors.append(f"{catalog_key}: {exc}")
            continue
        checked += 1
        if is_current:
            continue
        override = active_override_for(catalog_key, module, overrides, today, errors, warnings)
        if override is None:
            errors.append(
                f"{catalog_key} ({module}) is {current_version}, below reviewed minimum {minimum_version}; "
                "update the dependency or add a temporary owner/rationale/expiry override"
            )
            continue
        warnings.append(
            f"{catalog_key} ({module}) is {current_version}, below reviewed minimum {minimum_version}; "
            f"overridden by {override['owner']} until {override['expiry']}: {override['rationale']}"
        )

    for warning in warnings:
        print(f"::warning::security-dependency-freshness: {warning}", file=sys.stderr)
    if errors:
        for error in errors:
            print(f"::error::security-dependency-freshness: {error}", file=sys.stderr)
        print(f"security dependency freshness: FAIL ({len(errors)} error(s))", file=sys.stderr)
        return 1

    print(f"security dependency freshness: OK ({checked} checked dependency floor(s))")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
