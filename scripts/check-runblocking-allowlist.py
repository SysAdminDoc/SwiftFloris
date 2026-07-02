#!/usr/bin/env python3
"""Fails when production runBlocking usages drift beyond the reviewed allowlist."""

from __future__ import annotations

import argparse
from collections import Counter
from pathlib import Path
import re
import sys


DEFAULT_ROOT = Path(__file__).resolve().parents[1]
ALLOWED_CATEGORIES = frozenset(
    {
        "main-thread-keystroke",
        "sync-api",
        "cache-fill",
        "provider-init",
    }
)
HOT_PATH_CATEGORIES = frozenset({"main-thread-keystroke"})
RUN_BLOCKING = re.compile(r"\brunBlocking\s*(?:<[^>]+>\s*)?[\(\{]")


def normalize(line: str) -> str:
    return re.sub(r"\s+", " ", line.strip())


def iter_source_files(root: Path) -> list[Path]:
    files: list[Path] = []
    scan_roots = (
        root / "app" / "src" / "main",
        root / "lib",
    )
    for scan_root in scan_roots:
        if not scan_root.exists():
            continue
        for path in scan_root.rglob("*.kt"):
            rel = path.relative_to(root).as_posix()
            if "/build/" in rel:
                continue
            if rel.startswith("lib/") and "/src/main/" not in rel:
                continue
            files.append(path)
    return sorted(files)


def production_occurrences(root: Path) -> Counter[tuple[str, str]]:
    occurrences: Counter[tuple[str, str]] = Counter()
    for path in iter_source_files(root):
        rel = path.relative_to(root).as_posix()
        for line in path.read_text(encoding="utf-8").splitlines():
            if RUN_BLOCKING.search(line):
                occurrences[(rel, normalize(line))] += 1
    return occurrences


def parse_metadata(allowlist_path: Path, line_no: int, metadata: str) -> list[str]:
    errors: list[str] = []
    fields: dict[str, str] = {}
    for raw_field in metadata.split(";"):
        field = raw_field.strip()
        if not field:
            continue
        if "=" not in field:
            errors.append(f"{allowlist_path.as_posix()}:{line_no}: metadata field must be key=value: {field}")
            continue
        key, value = [part.strip() for part in field.split("=", 1)]
        if not key or not value:
            errors.append(f"{allowlist_path.as_posix()}:{line_no}: metadata field must be non-empty key=value")
            continue
        if key in fields:
            errors.append(f"{allowlist_path.as_posix()}:{line_no}: duplicate metadata key: {key}")
            continue
        fields[key] = value

    category = fields.get("category")
    if category is None:
        errors.append(f"{allowlist_path.as_posix()}:{line_no}: missing category metadata")
    elif category not in ALLOWED_CATEGORIES:
        allowed = ", ".join(sorted(ALLOWED_CATEGORIES))
        errors.append(f"{allowlist_path.as_posix()}:{line_no}: unknown category '{category}' (allowed: {allowed})")

    rationale = fields.get("rationale")
    if rationale is None:
        errors.append(f"{allowlist_path.as_posix()}:{line_no}: missing rationale metadata")

    budget = fields.get("budget_ms")
    if category in HOT_PATH_CATEGORIES and budget is None:
        errors.append(f"{allowlist_path.as_posix()}:{line_no}: main-thread hot-path entries require budget_ms")
    if budget is not None:
        try:
            parsed_budget = float(budget)
        except ValueError:
            errors.append(f"{allowlist_path.as_posix()}:{line_no}: budget_ms must be numeric")
        else:
            if parsed_budget <= 0:
                errors.append(f"{allowlist_path.as_posix()}:{line_no}: budget_ms must be > 0")

    return errors


def allowlisted_occurrences(root: Path) -> Counter[tuple[str, str]]:
    allowlist = root / "scripts" / "runblocking-allowlist.txt"
    rel_allowlist = allowlist.relative_to(root)
    if not allowlist.exists():
        raise SystemExit(f"missing allowlist: {rel_allowlist.as_posix()}")
    allowed: Counter[tuple[str, str]] = Counter()
    errors: list[str] = []
    for line_no, raw in enumerate(allowlist.read_text(encoding="utf-8").splitlines(), start=1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        parts = [part.strip() for part in line.split("|", 2)]
        if len(parts) != 3 or not all(parts):
            errors.append(
                f"{rel_allowlist.as_posix()}:{line_no}: expected path | code | "
                "category=<category>; [budget_ms=<ms>;] rationale=<why>"
            )
            continue
        path, code, metadata = parts
        errors.extend(parse_metadata(rel_allowlist, line_no, metadata))
        allowed[(path, normalize(code))] += 1
    if errors:
        raise SystemExit("\n".join(errors))
    return allowed


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=DEFAULT_ROOT)
    args = parser.parse_args(argv)
    root = args.root.resolve()

    actual = production_occurrences(root)
    allowed = allowlisted_occurrences(root)
    unexpected = actual - allowed
    stale = allowed - actual
    if not unexpected and not stale:
        print(f"runBlocking allowlist OK ({sum(actual.values())} reviewed production sites)")
        return 0
    if unexpected:
        print("Unallowlisted production runBlocking sites:", file=sys.stderr)
        for (path, code), count in sorted(unexpected.items()):
            suffix = f" x{count}" if count > 1 else ""
            print(f"  {path} | {code}{suffix}", file=sys.stderr)
        print(
            "Add reviewed entries with category=<category>; rationale=<why>; "
            "main-thread-keystroke entries also require budget_ms=<ms>.",
            file=sys.stderr,
        )
    if stale:
        print("Stale runBlocking allowlist entries:", file=sys.stderr)
        for (path, code), count in sorted(stale.items()):
            suffix = f" x{count}" if count > 1 else ""
            print(f"  {path} | {code}{suffix}", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
