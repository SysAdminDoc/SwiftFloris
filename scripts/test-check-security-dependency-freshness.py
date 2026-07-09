#!/usr/bin/env python3
"""Self-test the security dependency freshness gate with small fixtures."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path
from tempfile import TemporaryDirectory
from textwrap import dedent


ROOT = Path(__file__).resolve().parents[1]
CHECKER = ROOT / "scripts" / "check-security-dependency-freshness.py"


def write_fixture(root: Path, version: str = "4.17.0", overrides: list[dict[str, object]] | None = None) -> None:
    (root / "gradle").mkdir(parents=True, exist_ok=True)
    (root / ".github").mkdir(exist_ok=True)
    (root / "gradle" / "libs.versions.toml").write_text(
        dedent(
            f"""
            [versions]
            sqlcipher-android = "{version}"
            """
        ).strip()
        + "\n",
        encoding="utf-8",
    )
    (root / ".github" / "security-dependency-freshness.json").write_text(
        json.dumps(
            {
                "$schema": "https://json-schema.org/draft/2020-12/schema",
                "dependencies": [
                    {
                        "catalogKey": "sqlcipher-android",
                        "module": "net.zetetic:sqlcipher-android",
                        "minimumVersion": "4.17.0",
                        "reviewedOn": "2026-07-09",
                        "reason": "Fixture floor.",
                    },
                ],
                "overrides": overrides or [],
            },
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )


def run_checker(root: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [
            sys.executable,
            str(CHECKER),
            "--catalog",
            str(root / "gradle" / "libs.versions.toml"),
            "--config",
            str(root / ".github" / "security-dependency-freshness.json"),
            "--today",
            "2026-07-09",
        ],
        cwd=ROOT,
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )


def expect(result: subprocess.CompletedProcess[str], code: int, needle: str) -> bool:
    if result.returncode == code and needle in result.stdout:
        return True
    print(result.stdout)
    print(f"expected exit {code} with output containing {needle!r}")
    return False


def main() -> int:
    with TemporaryDirectory() as tmp:
        fixture = Path(tmp)
        write_fixture(fixture)
        if not expect(run_checker(fixture), 0, "security dependency freshness: OK"):
            return 1

        write_fixture(fixture, version="4.16.0")
        if not expect(run_checker(fixture), 1, "below reviewed minimum 4.17.0"):
            return 1

        write_fixture(
            fixture,
            version="4.16.0",
            overrides=[
                {
                    "catalogKey": "sqlcipher-android",
                    "owner": "matt_parker@outlook.com",
                    "rationale": "Fixture emergency release while upstream rollout is pending.",
                    "expiry": "2999-01-01",
                },
            ],
        )
        if not expect(run_checker(fixture), 0, "overridden by matt_parker@outlook.com"):
            return 1

        write_fixture(
            fixture,
            version="4.16.0",
            overrides=[
                {
                    "catalogKey": "sqlcipher-android",
                    "owner": "matt_parker@outlook.com",
                    "rationale": "Expired fixture.",
                    "expiry": "2000-01-01",
                },
            ],
        )
        if not expect(run_checker(fixture), 1, "expired on 2000-01-01"):
            return 1

        write_fixture(
            fixture,
            version="4.16.0",
            overrides=[
                {
                    "catalogKey": "sqlcipher-android",
                    "rationale": "Missing owner fixture.",
                    "expiry": "2999-01-01",
                },
            ],
        )
        if not expect(run_checker(fixture), 1, "override missing required field"):
            return 1

    print("security dependency freshness checker self-test: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
