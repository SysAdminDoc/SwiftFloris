#!/usr/bin/env python3
"""Fails when production runBlocking usages drift beyond the reviewed allowlist."""

from __future__ import annotations

from collections import Counter
from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[1]
ALLOWLIST = ROOT / "scripts" / "runblocking-allowlist.txt"
SCAN_ROOTS = (
    ROOT / "app" / "src" / "main",
    ROOT / "lib",
)
RUN_BLOCKING = re.compile(r"\brunBlocking\s*(?:<[^>]+>\s*)?[\(\{]")


def normalize(line: str) -> str:
    return re.sub(r"\s+", " ", line.strip())


def iter_source_files() -> list[Path]:
    files: list[Path] = []
    for scan_root in SCAN_ROOTS:
        if not scan_root.exists():
            continue
        for path in scan_root.rglob("*.kt"):
            rel = path.relative_to(ROOT).as_posix()
            if "/build/" in rel:
                continue
            if rel.startswith("lib/") and "/src/main/" not in rel:
                continue
            files.append(path)
    return sorted(files)


def production_occurrences() -> Counter[tuple[str, str]]:
    occurrences: Counter[tuple[str, str]] = Counter()
    for path in iter_source_files():
        rel = path.relative_to(ROOT).as_posix()
        for line in path.read_text(encoding="utf-8").splitlines():
            if RUN_BLOCKING.search(line):
                occurrences[(rel, normalize(line))] += 1
    return occurrences


def allowlisted_occurrences() -> Counter[tuple[str, str]]:
    if not ALLOWLIST.exists():
        raise SystemExit(f"missing allowlist: {ALLOWLIST.relative_to(ROOT).as_posix()}")
    allowed: Counter[tuple[str, str]] = Counter()
    errors: list[str] = []
    for line_no, raw in enumerate(ALLOWLIST.read_text(encoding="utf-8").splitlines(), start=1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        parts = [part.strip() for part in line.split("|", 2)]
        if len(parts) != 3 or not all(parts):
            errors.append(f"{ALLOWLIST.relative_to(ROOT).as_posix()}:{line_no}: expected path | code | rationale")
            continue
        path, code, _rationale = parts
        allowed[(path, normalize(code))] += 1
    if errors:
        raise SystemExit("\n".join(errors))
    return allowed


def main() -> int:
    actual = production_occurrences()
    allowed = allowlisted_occurrences()
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
    if stale:
        print("Stale runBlocking allowlist entries:", file=sys.stderr)
        for (path, code), count in sorted(stale.items()):
            suffix = f" x{count}" if count > 1 else ""
            print(f"  {path} | {code}{suffix}", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
