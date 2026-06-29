#!/usr/bin/env python3
"""Self-test the live-doc integrity checker with tracked and untracked docs."""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path
from tempfile import TemporaryDirectory
from textwrap import dedent


ROOT = Path(__file__).resolve().parents[1]
CHECKER = ROOT / "scripts" / "check-live-doc-integrity.py"


def run(*args: str, cwd: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [*args],
        cwd=cwd,
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )


def run_checker(root: Path) -> subprocess.CompletedProcess[str]:
    return run(sys.executable, str(CHECKER), "--root", str(root), cwd=ROOT)


def write_fixture(root: Path) -> None:
    (root / "docs").mkdir(parents=True, exist_ok=True)
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

    print("live doc integrity checker self-test: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
