#!/usr/bin/env python3
"""Check live (non-archive) Markdown for broken local links and stale canonical-source references."""

from __future__ import annotations

import argparse
import json
import os
import re
import shlex
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
STALE_RESEARCH_PLAN_REF = "RESEARCH_FEATURE" + "_PLAN.md"
STALE_RESEARCH_PLAN_SCAN_ROOTS = (
    "app/",
    "lib/",
    "scripts/",
    "docs/",
    "README.md",
    "ROADMAP.md",
)

CRASH_REPORT_TEMPLATE = ".github/ISSUE_TEMPLATE/crash_report.yml"
PULL_REQUEST_TEMPLATE = ".github/PULL_REQUEST_TEMPLATE.md"
APP_BUILD_GRADLE = "app/build.gradle.kts"
BLOCKED_ROADMAP = "Roadmap_Blocked.md"
DEFAULT_GITHUB_REPO = "SysAdminDoc/SwiftFloris"
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
GITHUB_ISSUE_URL_PATTERN = re.compile(
    r"https://github\.com/(?P<repo>[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+)/issues/(?P<number>[0-9]+)"
)
RELEASE_ADVANCE_PATTERN = re.compile(r"\badvance[sd]?\s+past\s+`?(?P<tag>v[0-9]+[.][0-9]+[.][0-9]+)`?", re.IGNORECASE)
VERSION_PATTERN = re.compile(r"\bv(?P<major>[0-9]+)[.](?P<minor>[0-9]+)[.](?P<patch>[0-9]+)\b")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Check live Markdown for broken links and stale references.")
    parser.add_argument("--root", default=str(ROOT), help="Repository root.")
    return parser.parse_args()


def gh_command() -> list[str]:
    raw = os.environ.get("GH_BIN")
    if raw:
        return shlex.split(raw, posix=os.name != "nt")
    return ["gh"]


def run_gh_json(root: Path, args: list[str]) -> tuple[object | None, str | None]:
    try:
        result = subprocess.run(
            [*gh_command(), *args],
            cwd=root,
            check=False,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
    except FileNotFoundError:
        return None, "gh is unavailable; skipped blocked-roadmap freshness checks"

    if result.returncode != 0:
        detail = (result.stderr or result.stdout).strip()
        if detail:
            return None, f"gh {' '.join(args[:3])} failed: {detail}"
        return None, f"gh {' '.join(args[:3])} failed with exit code {result.returncode}"

    try:
        return json.loads(result.stdout), None
    except json.JSONDecodeError as exc:
        return None, f"gh {' '.join(args[:3])} returned invalid JSON: {exc}"


def parse_version(tag: str) -> tuple[int, int, int] | None:
    match = VERSION_PATTERN.search(tag)
    if match is None:
        return None
    return (
        int(match.group("major")),
        int(match.group("minor")),
        int(match.group("patch")),
    )


def latest_github_release(root: Path, repo: str, warnings: list[str]) -> str | None:
    data, warning = run_gh_json(
        root,
        ["release", "list", "--repo", repo, "--limit", "20", "--json", "tagName,isLatest,publishedAt"],
    )
    if warning is not None:
        warnings.append(warning)
        return None
    if not isinstance(data, list) or not data:
        warnings.append(f"gh release list for {repo} returned no releases")
        return None
    latest = next((release for release in data if isinstance(release, dict) and release.get("isLatest")), data[0])
    if not isinstance(latest, dict) or not isinstance(latest.get("tagName"), str):
        warnings.append(f"gh release list for {repo} did not include tagName")
        return None
    return latest["tagName"]


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


def check_stale_research_plan_refs(root: Path, tracked_paths: set[str] | None) -> list[str]:
    candidates = (
        sorted(tracked_paths)
        if tracked_paths is not None
        else [
            path.relative_to(root).as_posix()
            for path in root.rglob("*")
            if path.is_file() and ".git" not in path.parts
        ]
    )
    errors: list[str] = []
    for rel in candidates:
        if not (
            rel in STALE_RESEARCH_PLAN_SCAN_ROOTS
            or any(rel.startswith(prefix) for prefix in STALE_RESEARCH_PLAN_SCAN_ROOTS if prefix.endswith("/"))
        ):
            continue
        path = root / rel
        try:
            text = path.read_text(encoding="utf-8-sig")
        except UnicodeDecodeError:
            continue
        except Exception as exc:
            errors.append(f"{rel}: cannot read ({exc})")
            continue
        for line_no, line in enumerate(text.splitlines(), start=1):
            if STALE_RESEARCH_PLAN_REF in line:
                errors.append(f"{rel}:{line_no}: references retired {STALE_RESEARCH_PLAN_REF}; use RESEARCH.md or feature-contract wording")
    return errors


def first_gradle_string(text: str, key: str) -> str | None:
    match = re.search(rf"\b{re.escape(key)}\s*=\s*\"([^\"]+)\"", text)
    return match.group(1) if match else None


def debug_application_id_suffix(text: str) -> str | None:
    match = re.search(r'\bnamed\("debug"\)\s*\{(?P<body>.*?)\n\s*\}', text, flags=re.DOTALL)
    if match is None:
        match = re.search(r"\bdebug\s*\{(?P<body>.*?)\n\s*\}", text, flags=re.DOTALL)
    if match is None:
        return None
    return first_gradle_string(match.group("body"), "applicationIdSuffix")


def check_pr_template_debug_package(root: Path) -> list[str]:
    template_path = root / PULL_REQUEST_TEMPLATE
    build_path = root / APP_BUILD_GRADLE
    if not template_path.exists():
        return []
    if not build_path.exists():
        return [f"{APP_BUILD_GRADLE}: missing app Gradle file"]

    try:
        template_text = template_path.read_text(encoding="utf-8-sig")
        build_text = build_path.read_text(encoding="utf-8-sig")
    except Exception as exc:
        return [f"{PULL_REQUEST_TEMPLATE}: cannot check debug package id ({exc})"]

    application_id = first_gradle_string(build_text, "applicationId")
    debug_suffix = debug_application_id_suffix(build_text)
    namespace = first_gradle_string(build_text, "namespace")
    errors: list[str] = []
    if application_id is None:
        errors.append(f"{APP_BUILD_GRADLE}: could not parse applicationId")
    if debug_suffix is None:
        errors.append(f"{APP_BUILD_GRADLE}: could not parse debug applicationIdSuffix")
    if namespace is None:
        errors.append(f"{APP_BUILD_GRADLE}: could not parse namespace")
    if errors:
        return errors

    debug_application_id = f"{application_id}{debug_suffix}"
    if debug_application_id not in template_text:
        errors.append(f"{PULL_REQUEST_TEMPLATE}: missing actual debug package id {debug_application_id}")
    stale_debug_id = "dev.patrickgold.florisboard.debug"
    if stale_debug_id in template_text and stale_debug_id != debug_application_id:
        errors.append(f"{PULL_REQUEST_TEMPLATE}: still references stale debug package id {stale_debug_id}")
    if namespace not in template_text:
        errors.append(f"{PULL_REQUEST_TEMPLATE}: should explain upstream namespace {namespace} separately")
    if "not the install package ID" not in template_text:
        errors.append(f"{PULL_REQUEST_TEMPLATE}: should separate Gradle namespace from install identity")
    return errors


def check_blocked_roadmap_freshness(root: Path) -> tuple[list[str], list[str]]:
    blocked_path = root / BLOCKED_ROADMAP
    if not blocked_path.exists():
        return [], []

    try:
        text = blocked_path.read_text(encoding="utf-8-sig")
    except Exception as exc:
        return [f"{BLOCKED_ROADMAP}: cannot read ({exc})"], []

    errors: list[str] = []
    warnings: list[str] = []
    issue_cache: dict[tuple[str, str], dict[str, object] | None] = {}
    latest_release_cache: dict[str, str | None] = {}

    for line_no, line in enumerate(text.splitlines(), start=1):
        for match in GITHUB_ISSUE_URL_PATTERN.finditer(line):
            repo = match.group("repo")
            number = match.group("number")
            cache_key = (repo, number)
            if cache_key not in issue_cache:
                data, warning = run_gh_json(
                    root,
                    ["issue", "view", number, "--repo", repo, "--json", "number,state,title,closedAt,url"],
                )
                if warning is not None:
                    warnings.append(warning)
                    issue_cache[cache_key] = None
                elif isinstance(data, dict):
                    issue_cache[cache_key] = data
                else:
                    warnings.append(f"gh issue view {number} for {repo} did not return an object")
                    issue_cache[cache_key] = None

            issue = issue_cache[cache_key]
            if issue is not None and issue.get("state") == "CLOSED":
                closed_at = issue.get("closedAt") or "unknown close time"
                errors.append(
                    f"{BLOCKED_ROADMAP}:{line_no}: blocked item references closed GitHub issue "
                    f"#{number} in {repo} ({closed_at})"
                )

        release_match = RELEASE_ADVANCE_PATTERN.search(line)
        if release_match is not None:
            baseline_tag = release_match.group("tag")
            baseline_version = parse_version(baseline_tag)
            if baseline_version is None:
                continue
            if DEFAULT_GITHUB_REPO not in latest_release_cache:
                latest_release_cache[DEFAULT_GITHUB_REPO] = latest_github_release(
                    root,
                    DEFAULT_GITHUB_REPO,
                    warnings,
                )
            latest_tag = latest_release_cache[DEFAULT_GITHUB_REPO]
            latest_version = parse_version(latest_tag or "")
            if latest_tag is not None and latest_version is not None and latest_version > baseline_version:
                errors.append(
                    f"{BLOCKED_ROADMAP}:{line_no}: release-follow-through blocker says the public channel "
                    f"must advance past {baseline_tag}, but latest public GitHub release is {latest_tag}"
                )

    return errors, warnings


def main() -> int:
    root = Path(parse_args().root).resolve()
    tracked_paths = collect_tracked_paths(root)
    files = collect_live_markdown(root, tracked_paths)
    all_errors: list[str] = []
    all_warnings: list[str] = []
    for md in files:
        all_errors.extend(check_file(md, root, tracked_paths))
    all_errors.extend(check_crash_report_template(root))
    all_errors.extend(check_stale_research_plan_refs(root, tracked_paths))
    all_errors.extend(check_pr_template_debug_package(root))
    blocked_errors, blocked_warnings = check_blocked_roadmap_freshness(root)
    all_errors.extend(blocked_errors)
    all_warnings.extend(blocked_warnings)

    for warning in sorted(set(all_warnings)):
        print(f"::warning::live-doc-integrity: {warning}", file=sys.stderr)

    if all_errors:
        for error in all_errors:
            print(f"::error::live-doc-integrity: {error}", file=sys.stderr)
        print(f"live doc integrity: FAIL ({len(all_errors)} error(s))", file=sys.stderr)
        return 1

    warning_suffix = f", {len(set(all_warnings))} warning(s)" if all_warnings else ""
    print(f"live doc integrity: OK ({len(files)} files checked{warning_suffix})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
