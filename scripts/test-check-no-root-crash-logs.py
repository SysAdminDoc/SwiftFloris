#!/usr/bin/env python3
"""Self-test the root JVM crash/replay log gate with tiny git fixtures."""

from __future__ import annotations

import subprocess
import sys
from shutil import copy2
from pathlib import Path
from tempfile import TemporaryDirectory


ROOT = Path(__file__).resolve().parents[1]
CHECKER = ROOT / "scripts" / "check-no-root-crash-logs.sh"


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
    (root / ".gitignore").write_text("*.log\n", encoding="utf-8")
    (root / "scripts").mkdir()
    copy2(CHECKER, root / "scripts" / "check-no-root-crash-logs.sh")
    result = run("git", "add", ".gitignore", cwd=root)
    if result.returncode != 0:
        raise RuntimeError(result.stdout)


def run_checker(root: Path) -> subprocess.CompletedProcess[str]:
    return run("bash", "scripts/check-no-root-crash-logs.sh", cwd=root)


def assert_result(
    completed: subprocess.CompletedProcess[str],
    expected_code: int,
    expected_text: str,
    label: str,
) -> bool:
    if completed.returncode != expected_code or expected_text not in completed.stdout:
        print(completed.stdout)
        print(f"expected {label}")
        return False
    return True


def main() -> int:
    with TemporaryDirectory() as tmp:
        fixture = Path(tmp)
        init_repo(fixture)
        passing = run_checker(fixture)
        if not assert_result(passing, 0, "No root JVM crash/replay logs", "clean repo to pass"):
            return 1

    with TemporaryDirectory() as tmp:
        fixture = Path(tmp)
        init_repo(fixture)
        (fixture / "hs_err_pid123.log").write_text("fixture\n", encoding="utf-8")
        failing = run_checker(fixture)
        if not assert_result(failing, 1, "hs_err_pid123.log", "ignored root crash log to fail"):
            return 1
        if ".ai/local-crash-logs/<date>/" not in failing.stdout:
            print(failing.stdout)
            print("expected cleanup destination in ignored-log failure")
            return 1

    with TemporaryDirectory() as tmp:
        fixture = Path(tmp)
        init_repo(fixture)
        (fixture / "replay_pid456.log").write_text("fixture\n", encoding="utf-8")
        result = run("git", "add", "-f", "replay_pid456.log", cwd=fixture)
        if result.returncode != 0:
            print(result.stdout)
            return 1
        failing = run_checker(fixture)
        if not assert_result(failing, 1, "Root JVM crash/replay logs are tracked", "tracked root replay log to fail"):
            return 1
        if "replay_pid456.log" not in failing.stdout:
            print(failing.stdout)
            print("expected tracked replay path in failure")
            return 1

    with TemporaryDirectory() as tmp:
        fixture = Path(tmp)
        init_repo(fixture)
        nested = fixture / "nested"
        nested.mkdir()
        (nested / "hs_err_pid789.log").write_text("fixture\n", encoding="utf-8")
        passing = run_checker(fixture)
        if not assert_result(passing, 0, "No root JVM crash/replay logs", "nested logs to pass"):
            return 1

    print("root crash log checker self-test: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
