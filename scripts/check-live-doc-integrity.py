#!/usr/bin/env python3
"""Check live (non-archive) Markdown for broken local links and stale canonical-source references."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path, PurePosixPath


ROOT = Path(__file__).resolve().parents[1]

ARCHIVE_DIRS = {"docs/archive", ".ai", "docs/outreach"}

EXCLUDED_FILES = {
    "CLAUDE.md",
    "AGENTS.md",
    "RESEARCH.md",
    "Roadmap_Blocked.md",
}

EXCLUDED_PREFIXES = [
    "docs/AUDIT_",
    "docs/research-feature-plan-",
]

FORBIDDEN_CANONICAL_REFS = [
    (re.compile(r"\bPROJECT_CONTEXT\.md\b"), "PROJECT_CONTEXT.md (absent — use README.md)"),
    (re.compile(r"\.github/workflows/"), ".github/workflows/ (deleted — builds are local)"),
]

LINK_PATTERN = re.compile(r"\[([^\]]*)\]\(([^)]+)\)")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Check live Markdown for broken links and stale references.")
    parser.add_argument("--root", default=str(ROOT), help="Repository root.")
    return parser.parse_args()


def is_excluded(path: Path, root: Path) -> bool:
    rel = path.relative_to(root).as_posix()
    if rel in EXCLUDED_FILES:
        return True
    if any(rel.startswith(d + "/") or rel.startswith(d + "\\") for d in ARCHIVE_DIRS):
        return True
    if any(rel.startswith(p) for p in EXCLUDED_PREFIXES):
        return True
    return False


def collect_live_markdown(root: Path) -> list[Path]:
    files: list[Path] = []
    for md in root.rglob("*.md"):
        if md.name.startswith("."):
            continue
        rel = md.relative_to(root).as_posix()
        if any(part.startswith(".") for part in PurePosixPath(rel).parts):
            continue
        if is_excluded(md, root):
            continue
        if "node_modules" in rel or "build/" in rel:
            continue
        files.append(md)
    return sorted(files)


def check_file(path: Path, root: Path) -> list[str]:
    errors: list[str] = []
    rel_path = path.relative_to(root).as_posix()
    try:
        text = path.read_text(encoding="utf-8-sig")
    except Exception as exc:
        return [f"{rel_path}: cannot read ({exc})"]

    for line_no, line in enumerate(text.splitlines(), start=1):
        for pattern, label in FORBIDDEN_CANONICAL_REFS:
            if pattern.search(line):
                errors.append(f"{rel_path}:{line_no}: references {label}")

        for match in LINK_PATTERN.finditer(line):
            target = match.group(2)
            if target.startswith("http://") or target.startswith("https://"):
                continue
            if target.startswith("mailto:"):
                continue
            if target.startswith("#"):
                continue
            local_path = target.split("#")[0].split("?")[0]
            if not local_path:
                continue
            resolved = (path.parent / local_path).resolve()
            if not resolved.exists():
                errors.append(f"{rel_path}:{line_no}: broken link [{match.group(1)}]({target})")

    return errors


def main() -> int:
    root = Path(parse_args().root).resolve()
    files = collect_live_markdown(root)
    all_errors: list[str] = []
    for md in files:
        all_errors.extend(check_file(md, root))

    if all_errors:
        for error in all_errors:
            print(f"::error::live-doc-integrity: {error}", file=sys.stderr)
        print(f"live doc integrity: FAIL ({len(all_errors)} error(s))", file=sys.stderr)
        return 1

    print(f"live doc integrity: OK ({len(files)} files checked)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
