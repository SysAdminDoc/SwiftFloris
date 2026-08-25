#!/usr/bin/env python3
"""Self-test repository hygiene handling for release-named paths."""

from __future__ import annotations

import os
import subprocess
import sys
from pathlib import Path
from shutil import copy2, which
from tempfile import TemporaryDirectory

ROOT = Path(__file__).resolve().parents[1]
CHECKER = ROOT / "scripts" / "check-repo-hygiene.sh"
ROOT_LOG_CHECKER = ROOT / "scripts" / "check-no-root-crash-logs.sh"


def resolve_bash() -> str:
    if sys.platform != "win32":
        return "bash"
    git_executable = which("git")
    if git_executable is not None:
        git_bash = Path(git_executable).resolve().parent.parent / "bin" / "bash.exe"
        if git_bash.is_file():
            return str(git_bash)
    for program_files_var in ("ProgramFiles", "ProgramFiles(x86)"):
        program_files = os.environ.get(program_files_var)
        if program_files:
            git_bash = Path(program_files) / "Git" / "bin" / "bash.exe"
            if git_bash.is_file():
                return str(git_bash)
    return "bash"


BASH = resolve_bash()


def run(*args: str, cwd: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [*args],
        cwd=cwd,
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )


def init_repo(root: Path) -> None:
    for args in (
        ("git", "init", "-q"),
        ("git", "config", "user.email", "fixture@example.com"),
        ("git", "config", "user.name", "Fixture"),
    ):
        result = run(*args, cwd=root)
        if result.returncode != 0:
            raise RuntimeError(result.stdout)
    scripts_dir = root / "scripts"
    scripts_dir.mkdir()
    copy2(CHECKER, scripts_dir / CHECKER.name)
    copy2(ROOT_LOG_CHECKER, scripts_dir / ROOT_LOG_CHECKER.name)
    result = run("git", "add", "scripts", cwd=root)
    if result.returncode != 0:
        raise RuntimeError(result.stdout)


def run_checker(root: Path) -> subprocess.CompletedProcess[str]:
    return run(BASH, "scripts/check-repo-hygiene.sh", cwd=root)


def main() -> int:
    with TemporaryDirectory() as tmp:
        fixture = Path(tmp)
        init_repo(fixture)
        release_source = fixture / "app" / "src" / "release" / "kotlin" / "Example.kt"
        release_source.parent.mkdir(parents=True)
        release_source.write_text("package fixture\n", encoding="utf-8")
        run("git", "add", "app/src/release/kotlin/Example.kt", cwd=fixture)
        passing = run_checker(fixture)
        if passing.returncode != 0:
            print(passing.stdout)
            print("expected Android release source to pass")
            return 1

    with TemporaryDirectory() as tmp:
        fixture = Path(tmp)
        init_repo(fixture)
        generated_release = fixture / "release" / "app.apk"
        generated_release.parent.mkdir()
        generated_release.write_bytes(b"fixture")
        run("git", "add", "release/app.apk", cwd=fixture)
        failing = run_checker(fixture)
        if failing.returncode != 1 or "release/app.apk" not in failing.stdout:
            print(failing.stdout)
            print("expected generated release output to fail")
            return 1

    with TemporaryDirectory() as tmp:
        fixture = Path(tmp)
        init_repo(fixture)
        bytecode = fixture / "scripts" / "__pycache__" / "example.cpython-313.pyc"
        bytecode.parent.mkdir(parents=True)
        bytecode.write_bytes(b"fixture")
        run("git", "add", "-f", "scripts/__pycache__/example.cpython-313.pyc", cwd=fixture)
        failing = run_checker(fixture)
        if failing.returncode != 1 or "__pycache__/example.cpython-313.pyc" not in failing.stdout:
            print(failing.stdout)
            print("expected tracked Python bytecode to fail")
            return 1

    print("repository hygiene checker self-test: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
