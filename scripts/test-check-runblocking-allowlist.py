#!/usr/bin/env python3
"""Self-test the production runBlocking allowlist gate with small fixtures."""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path
from tempfile import TemporaryDirectory
from textwrap import dedent


ROOT = Path(__file__).resolve().parents[1]
CHECKER = ROOT / "scripts" / "check-runblocking-allowlist.py"


def write_fixture(root: Path, allowlist: str) -> None:
    source_dir = root / "app" / "src" / "main" / "kotlin" / "demo"
    source_dir.mkdir(parents=True, exist_ok=True)
    (root / "scripts").mkdir(exist_ok=True)
    (source_dir / "Hot.kt").write_text(
        dedent(
            """
            package demo

            import kotlinx.coroutines.runBlocking

            fun key() { runBlocking { } }
            fun api() = runBlocking { "ok" }
            """
        ).strip()
        + "\n",
        encoding="utf-8",
    )
    (root / "scripts" / "runblocking-allowlist.txt").write_text(
        dedent(allowlist).strip() + "\n",
        encoding="utf-8",
    )


def run_checker(root: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [sys.executable, str(CHECKER), "--root", str(root)],
        cwd=ROOT,
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )


def main() -> int:
    passing_allowlist = """
        app/src/main/kotlin/demo/Hot.kt | fun key() { runBlocking { } } | category=main-thread-keystroke; budget_ms=1.0; rationale=CPU-only fixture
        app/src/main/kotlin/demo/Hot.kt | fun api() = runBlocking { "ok" } | category=sync-api; rationale=Synchronous fixture API
    """
    with TemporaryDirectory() as tmp:
        fixture = Path(tmp)
        write_fixture(fixture, passing_allowlist)
        passing = run_checker(fixture)
        if passing.returncode != 0 or "2 reviewed production sites" not in passing.stdout:
            print(passing.stdout)
            print("expected valid structured allowlist to pass")
            return 1

    missing_category = """
        app/src/main/kotlin/demo/Hot.kt | fun key() { runBlocking { } } | budget_ms=1.0; rationale=CPU-only fixture
        app/src/main/kotlin/demo/Hot.kt | fun api() = runBlocking { "ok" } | category=sync-api; rationale=Synchronous fixture API
    """
    with TemporaryDirectory() as tmp:
        fixture = Path(tmp)
        write_fixture(fixture, missing_category)
        failing = run_checker(fixture)
        if failing.returncode != 1 or "missing category metadata" not in failing.stdout:
            print(failing.stdout)
            print("expected missing category metadata to fail")
            return 1

    missing_hot_path_budget = """
        app/src/main/kotlin/demo/Hot.kt | fun key() { runBlocking { } } | category=main-thread-keystroke; rationale=CPU-only fixture
        app/src/main/kotlin/demo/Hot.kt | fun api() = runBlocking { "ok" } | category=sync-api; rationale=Synchronous fixture API
    """
    with TemporaryDirectory() as tmp:
        fixture = Path(tmp)
        write_fixture(fixture, missing_hot_path_budget)
        failing = run_checker(fixture)
        if failing.returncode != 1 or "main-thread hot-path entries require budget_ms" not in failing.stdout:
            print(failing.stdout)
            print("expected missing main-thread budget to fail")
            return 1

    stale_or_unexpected = """
        app/src/main/kotlin/demo/Hot.kt | fun key() { runBlocking { } } | category=main-thread-keystroke; budget_ms=1.0; rationale=CPU-only fixture
    """
    with TemporaryDirectory() as tmp:
        fixture = Path(tmp)
        write_fixture(fixture, stale_or_unexpected)
        failing = run_checker(fixture)
        if failing.returncode != 1 or "Unallowlisted production runBlocking sites" not in failing.stdout:
            print(failing.stdout)
            print("expected unexpected production site to fail")
            return 1

    print("runBlocking allowlist checker self-test: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
