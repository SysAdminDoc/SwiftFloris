#!/usr/bin/env python3
"""Verify public documentation version pins against Gradle metadata."""

from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


@dataclass(frozen=True)
class Expectation:
    path: str
    label: str
    pattern: str
    expected: tuple[str, ...]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Check public README/security/reproducible-build version pins against Gradle metadata.",
    )
    parser.add_argument(
        "--root",
        default=str(ROOT),
        help="Repository root. Defaults to the parent of this script directory.",
    )
    return parser.parse_args()


def read_text(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8-sig")
    except FileNotFoundError as exc:
        raise ValueError(f"missing required file: {path.as_posix()}") from exc


def parse_properties(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw in read_text(path).splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        key, separator, value = line.partition("=")
        if separator:
            values[key.strip()] = value.strip()
    return values


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


def require_keys(source: str, values: dict[str, str], keys: tuple[str, ...]) -> list[str]:
    missing = [key for key in keys if key not in values or not values[key]]
    return [f"{source}: missing {key}" for key in missing]


def gradle_wrapper_version(distribution_url: str) -> str | None:
    match = re.search(r"gradle-([0-9]+(?:\.[0-9]+)+)-bin\.zip", distribution_url)
    return match.group(1) if match else None


def build_expectations(root: Path) -> tuple[list[Expectation], list[str]]:
    catalog = parse_version_catalog(root / "gradle" / "libs.versions.toml")
    gradle = parse_properties(root / "gradle.properties")
    wrapper = parse_properties(root / "gradle" / "wrapper" / "gradle-wrapper.properties")

    errors: list[str] = []
    errors.extend(require_keys(
        "gradle/libs.versions.toml",
        catalog,
        (
            "android-gradle-plugin",
            "androidx-compose-bom",
            "androidx-room",
            "kotlin",
            "ksp",
            "kotest",
            "robolectric",
            "roborazzi",
            "sqlcipher-android",
            "tink-android",
        ),
    ))
    errors.extend(require_keys(
        "gradle.properties",
        gradle,
        (
            "projectCompileSdk",
            "projectMinSdk",
            "projectTargetSdk",
            "projectVersionCode",
            "projectVersionName",
        ),
    ))
    errors.extend(require_keys(
        "gradle/wrapper/gradle-wrapper.properties",
        wrapper,
        ("distributionSha256Sum", "distributionUrl"),
    ))
    if errors:
        return [], errors

    wrapper_version = gradle_wrapper_version(wrapper["distributionUrl"])
    if wrapper_version is None:
        errors.append("gradle/wrapper/gradle-wrapper.properties: could not parse Gradle version from distributionUrl")
        return [], errors

    version_name = gradle["projectVersionName"]
    version_code = gradle["projectVersionCode"]
    gradle_sha_prefix = wrapper["distributionSha256Sum"][:8]

    expectations = [
        Expectation(
            "README.md",
            "version badge",
            r"version-v([0-9]+\.[0-9]+\.[0-9]+)-blue",
            (version_name,),
        ),
        Expectation(
            "README.md",
            "highlights version heading",
            r"\| Area \| What's in v([0-9]+\.[0-9]+\.[0-9]+) \| Privacy posture \|",
            (version_name,),
        ),
        Expectation(
            "README.md",
            "current release status",
            r"Current release: \*\*v([0-9]+\.[0-9]+\.[0-9]+)\*\*",
            (version_name,),
        ),
        Expectation(
            "README.md",
            "Kotlin and Compose stack line",
            r"Kotlin ([0-9][0-9.]*[0-9]), Compose BOM ([0-9][0-9.]*[0-9])",
            (catalog["kotlin"], catalog["androidx-compose-bom"]),
        ),
        Expectation(
            "README.md",
            "AGP and Gradle stack line",
            r"AGP ([0-9][0-9.]*[0-9]), Gradle ([0-9][0-9.]*[0-9])",
            (catalog["android-gradle-plugin"], wrapper_version),
        ),
        Expectation(
            "README.md",
            "KSP Room SQLCipher Tink stack line",
            r"KSP ([0-9][0-9.]*[0-9]), Room ([0-9][0-9.]*[0-9]), SQLCipher ([0-9][0-9.]*[0-9]), Tink Android ([0-9][0-9.]*[0-9])\.",
            (catalog["ksp"], catalog["androidx-room"], catalog["sqlcipher-android"], catalog["tink-android"]),
        ),
        Expectation(
            "README.md",
            "Kotest Roborazzi Robolectric stack line",
            r"Kotest ([0-9][0-9.]*[0-9]) unit-test runner; Roborazzi ([0-9][0-9.]*[0-9]) and Robolectric ([0-9][0-9.]*[0-9])",
            (catalog["kotest"], catalog["roborazzi"], catalog["robolectric"]),
        ),
        Expectation(
            "README.md",
            "local release evidence dependency freshness line",
            r"dependency freshness is pinned through Compose BOM ([0-9][0-9.]*[0-9]) / KSP ([0-9][0-9.]*[0-9]) / Roborazzi ([0-9][0-9.]*[0-9])",
            (catalog["androidx-compose-bom"], catalog["ksp"], catalog["roborazzi"]),
        ),
        Expectation(
            "README.md",
            "Gradle prerequisite line",
            r"# Gradle ([0-9][0-9.]*[0-9]) \(use the bundled wrapper\)",
            (wrapper_version,),
        ),
        Expectation(
            "README.md",
            "visual-regression Roborazzi line",
            r"Roborazzi ([0-9][0-9.]*[0-9]), plugin alias active\.",
            (catalog["roborazzi"],),
        ),
        Expectation(
            "README.md",
            "SDK floor target and compile line",
            r"minSdk \*\*([0-9]+)\*\* .*?targetSdk \*\*([0-9]+)\*\* .*?compileSdk \*\*([0-9]+)\*\*",
            (gradle["projectMinSdk"], gradle["projectTargetSdk"], gradle["projectCompileSdk"]),
        ),
        Expectation(
            "docs/SECURITY.md",
            "Tink Android local key storage line",
            r"Tink Android `([0-9][0-9.]*[0-9])`",
            (catalog["tink-android"],),
        ),
        Expectation(
            "docs/SECURITY.md",
            "SQLCipher provider-watch line",
            r"SQLCipher ([0-9][0-9.]*[0-9]), Zetetic",
            (catalog["sqlcipher-android"],),
        ),
        Expectation(
            "docs/REPRODUCIBLE_BUILDS.md",
            "Gradle distribution table row",
            r"\| Gradle distribution \| `gradle/wrapper/gradle-wrapper\.properties` \| ([0-9][0-9.]*[0-9]) \| `distributionSha256Sum=([0-9a-f]{8})\.\.\.` \|",
            (wrapper_version, gradle_sha_prefix),
        ),
        Expectation(
            "docs/REPRODUCIBLE_BUILDS.md",
            "Android Gradle Plugin table row",
            r"\| Android Gradle Plugin \| `gradle/libs\.versions\.toml` `\[versions\] android-gradle-plugin` \| ([0-9][0-9.]*[0-9]) \|",
            (catalog["android-gradle-plugin"],),
        ),
        Expectation(
            "docs/REPRODUCIBLE_BUILDS.md",
            "Kotlin table row",
            r"\| Kotlin \| `gradle/libs\.versions\.toml` `\[versions\] kotlin` \| ([0-9][0-9.]*[0-9]) \|",
            (catalog["kotlin"],),
        ),
        Expectation(
            "docs/REPRODUCIBLE_BUILDS.md",
            "KSP table row",
            r"\| KSP \| `gradle/libs\.versions\.toml` `\[versions\] ksp` \| ([0-9][0-9.]*[0-9]) \|",
            (catalog["ksp"],),
        ),
        Expectation(
            "docs/REPRODUCIBLE_BUILDS.md",
            "clone tag command",
            r"git clone --branch v([0-9]+\.[0-9]+\.[0-9]+) --depth 1",
            (version_name,),
        ),
        Expectation(
            "docs/REPRODUCIBLE_BUILDS.md",
            "published APK variable",
            r"APK_PUBLISHED=app-release-v([0-9]+\.[0-9]+\.[0-9]+)\.apk",
            (version_name,),
        ),
        Expectation(
            "docs/REPRODUCIBLE_BUILDS.md",
            "F-Droid versionName",
            r"versionName: \"([0-9]+\.[0-9]+\.[0-9]+)\"",
            (version_name,),
        ),
        Expectation(
            "docs/REPRODUCIBLE_BUILDS.md",
            "F-Droid versionCode",
            r"versionCode: ([0-9]+)",
            (version_code,),
        ),
        Expectation(
            "docs/REPRODUCIBLE_BUILDS.md",
            "F-Droid commit tag",
            r"commit: v([0-9]+\.[0-9]+\.[0-9]+)",
            (version_name,),
        ),
        Expectation(
            "docs/REPRODUCIBLE_BUILDS.md",
            "F-Droid CurrentVersion",
            r"CurrentVersion: \"([0-9]+\.[0-9]+\.[0-9]+)\"",
            (version_name,),
        ),
        Expectation(
            "docs/REPRODUCIBLE_BUILDS.md",
            "F-Droid CurrentVersionCode",
            r"CurrentVersionCode: ([0-9]+)",
            (version_code,),
        ),
    ]
    return expectations, []


def check_expectation(root: Path, expectation: Expectation) -> str | None:
    path = root / expectation.path
    try:
        text = read_text(path)
    except ValueError as exc:
        return str(exc)

    match = re.search(expectation.pattern, text, flags=re.DOTALL)
    if match is None:
        return f"{expectation.path}: missing {expectation.label}"

    actual = tuple(group.strip() for group in match.groups())
    if actual != expectation.expected:
        return (
            f"{expectation.path}: {expectation.label} reports {actual} "
            f"but Gradle metadata expects {expectation.expected}"
        )
    return None


def main() -> int:
    root = Path(parse_args().root).resolve()
    expectations, errors = build_expectations(root)
    for expectation in expectations:
        error = check_expectation(root, expectation)
        if error is not None:
            errors.append(error)

    if errors:
        for error in errors:
            print(f"::error::public-doc-version-pins: {error}", file=sys.stderr)
        print(f"public doc version pins: FAIL ({len(errors)} error(s))", file=sys.stderr)
        return 1

    print(f"public doc version pins: OK ({len(expectations)} checked claims)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
