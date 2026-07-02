#!/usr/bin/env python3
"""Check live (non-archive) Markdown for broken local links and stale canonical-source references."""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path, PurePosixPath


ROOT = Path(__file__).resolve().parents[1]

ARCHIVE_DIRS = {"docs/archive", ".ai", "docs/outreach"}

EXCLUDED_FILES = {
    "CLAUDE.md",
    "AGENTS.md",
    "ROADMAP.md",
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

CRASH_REPORT_TEMPLATE = ".github/ISSUE_TEMPLATE/crash_report.yml"
REQUIRED_CRASH_TEMPLATE_IDS = [
    "description",
    "reproduce",
    "florisversion",
    "installsource",
    "androidversion",
    "device",
    "reproducibility",
    "crashlogsource",
    "crashlog",
    "checklist",
]
REQUIRED_CRASH_REDACTION_TERMS = [
    "typed text",
    "clipboard content",
    "personal dictionary content",
    "private apk paths",
    "unrelated device logs",
]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Check live Markdown for broken links and stale references.")
    parser.add_argument("--root", default=str(ROOT), help="Repository root.")
    return parser.parse_args()


def collect_tracked_paths(root: Path) -> set[str] | None:
    result = subprocess.run(
        ["git", "-C", str(root), "ls-files", "-z"],
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
    )
    if result.returncode != 0:
        return None
    return {part.decode("utf-8").replace("\\", "/") for part in result.stdout.split(b"\0") if part}


def is_excluded(path: Path, root: Path) -> bool:
    rel = path.relative_to(root).as_posix()
    if rel in EXCLUDED_FILES:
        return True
    if any(rel.startswith(d + "/") or rel.startswith(d + "\\") for d in ARCHIVE_DIRS):
        return True
    if any(rel.startswith(p) for p in EXCLUDED_PREFIXES):
        return True
    return False


def collect_live_markdown(root: Path, tracked_paths: set[str] | None) -> list[Path]:
    files: list[Path] = []
    candidates = (
        [root / rel for rel in sorted(tracked_paths) if rel.endswith(".md")]
        if tracked_paths is not None
        else sorted(root.rglob("*.md"))
    )
    for md in candidates:
        if not md.exists() or md.name.startswith("."):
            continue
        try:
            rel = md.relative_to(root).as_posix()
        except ValueError:
            continue
        if any(part.startswith(".") for part in PurePosixPath(rel).parts):
            continue
        if is_excluded(md, root):
            continue
        if "node_modules" in rel or "build/" in rel:
            continue
        files.append(md)
    return sorted(files)


def has_tracked_directory(rel: str, tracked_paths: set[str]) -> bool:
    prefix = rel.rstrip("/") + "/"
    return any(path.startswith(prefix) for path in tracked_paths)


def check_file(path: Path, root: Path, tracked_paths: set[str] | None) -> list[str]:
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
            try:
                resolved_rel = resolved.relative_to(root).as_posix()
            except ValueError:
                errors.append(f"{rel_path}:{line_no}: local link points outside repo [{match.group(1)}]({target})")
                continue
            if not resolved.exists():
                errors.append(f"{rel_path}:{line_no}: broken link [{match.group(1)}]({target})")
                continue
            if tracked_paths is None:
                continue
            if resolved.is_dir():
                if not has_tracked_directory(resolved_rel, tracked_paths):
                    errors.append(f"{rel_path}:{line_no}: untracked linked directory [{match.group(1)}]({target})")
            elif resolved_rel not in tracked_paths:
                errors.append(f"{rel_path}:{line_no}: untracked linked file [{match.group(1)}]({target})")

    return errors


def check_crash_report_template(root: Path) -> list[str]:
    template_path = root / CRASH_REPORT_TEMPLATE
    if not template_path.exists():
        return [f"{CRASH_REPORT_TEMPLATE}: missing crash report template"]

    try:
        text = template_path.read_text(encoding="utf-8-sig")
    except Exception as exc:
        return [f"{CRASH_REPORT_TEMPLATE}: cannot read ({exc})"]

    errors: list[str] = []
    for field_id in REQUIRED_CRASH_TEMPLATE_IDS:
        if re.search(rf"^\s*id:\s*{re.escape(field_id)}\s*$", text, flags=re.MULTILINE) is None:
            errors.append(f"{CRASH_REPORT_TEMPLATE}: missing required field id '{field_id}'")

    lower_text = text.lower()
    for term in REQUIRED_CRASH_REDACTION_TERMS:
        if term not in lower_text:
            errors.append(f"{CRASH_REPORT_TEMPLATE}: redaction checklist missing '{term}'")

    if "SwiftFloris Version" not in text:
        errors.append(f"{CRASH_REPORT_TEMPLATE}: version field must name SwiftFloris")
    if "Crash Log Source" not in text:
        errors.append(f"{CRASH_REPORT_TEMPLATE}: crash-log source field must be explicit")

    return errors


def main() -> int:
    root = Path(parse_args().root).resolve()
    tracked_paths = collect_tracked_paths(root)
    files = collect_live_markdown(root, tracked_paths)
    all_errors: list[str] = []
    for md in files:
        all_errors.extend(check_file(md, root, tracked_paths))
    all_errors.extend(check_crash_report_template(root))

    if all_errors:
        for error in all_errors:
            print(f"::error::live-doc-integrity: {error}", file=sys.stderr)
        print(f"live doc integrity: FAIL ({len(all_errors)} error(s))", file=sys.stderr)
        return 1

    print(f"live doc integrity: OK ({len(files)} files checked)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
