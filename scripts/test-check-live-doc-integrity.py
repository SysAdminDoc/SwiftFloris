#!/usr/bin/env python3
"""Self-test the live-doc integrity checker with tracked and untracked docs."""

from __future__ import annotations

import subprocess
import sys
import os
from pathlib import Path
from tempfile import TemporaryDirectory
from textwrap import dedent


ROOT = Path(__file__).resolve().parents[1]
CHECKER = ROOT / "scripts" / "check-live-doc-integrity.py"


def run(*args: str, cwd: Path, env: dict[str, str] | None = None) -> subprocess.CompletedProcess[str]:
    run_env = os.environ.copy()
    if env is not None:
        run_env.update(env)
    return subprocess.run(
        [*args],
        cwd=cwd,
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        env=run_env,
    )


def run_checker(root: Path, gh_bin: str | None = None) -> subprocess.CompletedProcess[str]:
    env = {"GH_BIN": gh_bin} if gh_bin is not None else None
    return run(sys.executable, str(CHECKER), "--root", str(root), cwd=ROOT, env=env)


def write_fixture(root: Path) -> None:
    (root / "docs").mkdir(parents=True, exist_ok=True)
    (root / ".github" / "ISSUE_TEMPLATE").mkdir(parents=True, exist_ok=True)
    (root / "README.md").write_text(
        dedent(
            """
            # Fixture

            Public docs link to [security](docs/SECURITY.md).
            """
        ).strip()
        + "\n",
        encoding="utf-8",
    )
    (root / "docs" / "SECURITY.md").write_text("# Security\n", encoding="utf-8")
    (root / "docs" / "LOCAL_ONLY.md").write_text("# Local only\n", encoding="utf-8")
    (root / ".github" / "ISSUE_TEMPLATE" / "crash_report.yml").write_text(
        dedent(
            """
            body:
              - type: input
                id: description
              - type: input
                id: reproduce
              - type: input
                id: florisversion
                attributes:
                  label: SwiftFloris Version
              - type: input
                id: installsource
              - type: input
                id: androidversion
              - type: input
                id: device
              - type: input
                id: reproducibility
              - type: input
                id: crashlogsource
                attributes:
                  label: Crash Log Source
              - type: input
                id: crashlog
              - type: checkboxes
                id: checklist
                attributes:
                  options:
                    - label: typed text, clipboard content, personal dictionary content, private APK paths, unrelated device logs
            """
        ).strip()
        + "\n",
        encoding="utf-8",
    )


def make_fake_gh(root: Path) -> str:
    fake_py = root / "fake-gh.py"
    fake_py.write_text(
        dedent(
            """
            import json
            import sys

            args = sys.argv[1:]
            if args[:2] == ["issue", "view"] and args[2] == "9":
                print(json.dumps({
                    "number": 9,
                    "state": "CLOSED",
                    "title": "crash while typing",
                    "closedAt": "2026-06-25T22:37:11Z",
                    "url": "https://github.com/SysAdminDoc/SwiftFloris/issues/9",
                }))
                raise SystemExit(0)
            if args[:2] == ["release", "list"]:
                print(json.dumps([{
                    "tagName": "v1.9.53",
                    "isLatest": True,
                    "publishedAt": "2026-06-25T21:23:57Z",
                }]))
                raise SystemExit(0)
            print("unexpected gh args: " + " ".join(args), file=sys.stderr)
            raise SystemExit(2)
            """
        ).strip()
        + "\n",
        encoding="utf-8",
    )

    if os.name == "nt":
        fake_cmd = root / "gh.cmd"
        fake_cmd.write_text(f'@echo off\n"{sys.executable}" "{fake_py}" %*\n', encoding="utf-8")
        return str(fake_cmd)

    fake_sh = root / "gh"
    fake_sh.write_text(f'#!/bin/sh\nexec "{sys.executable}" "{fake_py}" "$@"\n', encoding="utf-8")
    fake_sh.chmod(0o755)
    return str(fake_sh)


def git_add(root: Path, *paths: str) -> None:
    result = run("git", "add", *paths, cwd=root)
    if result.returncode != 0:
        raise RuntimeError(result.stdout)


def init_repo(root: Path) -> None:
    for args in (
        ("git", "init", "-q"),
        ("git", "config", "user.email", "fixture@example.com"),
        ("git", "config", "user.name", "Fixture"),
    ):
        result = run(*args, cwd=root)
        if result.returncode != 0:
            raise RuntimeError(result.stdout)


def main() -> int:
    with TemporaryDirectory() as tmp:
        fixture = Path(tmp)
        write_fixture(fixture)
        init_repo(fixture)
        git_add(fixture, "README.md", "docs/SECURITY.md")

        passing = run_checker(fixture)
        if passing.returncode != 0:
            print(passing.stdout)
            print("expected tracked local links to pass")
            return 1

        readme = fixture / "README.md"
        readme.write_text(
            readme.read_text(encoding="utf-8").replace(
                "docs/SECURITY.md",
                "docs/LOCAL_ONLY.md",
            ),
            encoding="utf-8",
        )
        git_add(fixture, "README.md")
        failing = run_checker(fixture)
        if failing.returncode != 1 or "untracked linked file" not in failing.stdout:
            print(failing.stdout)
            print("expected link to untracked local markdown to fail")
            return 1

        readme.write_text("# Fixture\n\nDeleted workflows are gone.\n", encoding="utf-8")
        git_add(fixture, "README.md")
        passing = run_checker(fixture)
        if passing.returncode != 0:
            print(passing.stdout)
            print("expected non-literal workflow wording to pass")
            return 1

        readme.write_text("# Fixture\n\nOld path: `.github/workflows/release.yml`.\n", encoding="utf-8")
        git_add(fixture, "README.md")
        failing = run_checker(fixture)
        if failing.returncode != 1 or "deleted" not in failing.stdout:
            print(failing.stdout)
            print("expected deleted workflow literal to fail")
            return 1

        readme.write_text("# Fixture\n\nPublic docs link to [security](docs/SECURITY.md).\n", encoding="utf-8")
        git_add(fixture, "README.md")
        blocked = fixture / "Roadmap_Blocked.md"
        blocked.write_text(
            dedent(
                """
                # Blocked

                - [ ] P0 - Release and close the low-memory SymSpell crash follow-through
                  Why: issue #9 reports typing OOM on released `v1.9.48`; users need the public release channel to advance past `v1.9.48`.
                  Evidence: https://github.com/SysAdminDoc/SwiftFloris/issues/9
                  **Blocker:** Requires human action.
                """
            ).strip()
            + "\n",
            encoding="utf-8",
        )
        stale_blocked = run_checker(fixture, gh_bin=make_fake_gh(fixture))
        if (
            stale_blocked.returncode != 1
            or "closed GitHub issue #9" not in stale_blocked.stdout
            or "latest public GitHub release is v1.9.53" not in stale_blocked.stdout
        ):
            print(stale_blocked.stdout)
            print("expected stale blocked roadmap issue/release gate to fail")
            return 1

    print("live doc integrity checker self-test: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
