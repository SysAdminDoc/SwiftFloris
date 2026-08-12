#!/usr/bin/env python3
"""Regression tests for the release-time OSV severity gate."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path
from tempfile import TemporaryDirectory


ROOT = Path(__file__).resolve().parents[1]
GATE = ROOT / "scripts" / "osv-release-gate.py"


def run_gate(payload: dict[str, object]) -> subprocess.CompletedProcess[str]:
    with TemporaryDirectory() as tmp:
        root = Path(tmp)
        result_path = root / "osv-result.json"
        result_path.write_text(json.dumps(payload), encoding="utf-8")
        return subprocess.run(
            [sys.executable, str(GATE), str(result_path)],
            cwd=root,
            check=False,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
        )


def advisory(score: object, *, database_severity: str | None = None) -> dict[str, object]:
    vulnerability: dict[str, object] = {
        "id": "TEST-OSV-1",
        "summary": "synthetic advisory",
        "severity": [{"type": "CVSS_V3", "score": score}],
    }
    if database_severity is not None:
        vulnerability["database_specific"] = {"severity": database_severity}
    return {
        "packages": [
            {
                "package": {"name": "synthetic-package"},
                "vulnerabilities": [vulnerability],
            }
        ]
    }


def payload(vulnerability: dict[str, object]) -> dict[str, object]:
    return {"results": [vulnerability]}


def expect_blocked(result: subprocess.CompletedProcess[str], needle: str) -> None:
    if result.returncode != 1 or needle not in result.stdout:
        raise AssertionError(
            f"expected blocked result containing {needle!r}; "
            f"exit={result.returncode}\n{result.stdout}"
        )


def main() -> int:
    cvss_31_critical = payload(
        advisory("CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H")
    )
    result = run_gate(cvss_31_critical)
    expect_blocked(result, "CRITICAL")
    expect_blocked(result, "TEST-OSV-1")

    numeric_high = payload(advisory("7.5"))
    result = run_gate(numeric_high)
    expect_blocked(result, "HIGH")

    cvss_40_database_critical = payload(
        advisory(
            "CVSS:4.0/AV:N/AC:L/AT:N/PR:N/UI:N/VC:H/VI:H/VA:H/SC:N/SI:N/SA:N",
            database_severity="CRITICAL",
        )
    )
    result = run_gate(cvss_40_database_critical)
    expect_blocked(result, "CRITICAL")
    if "MEDIUM TEST-OSV-1" in result.stdout:
        raise AssertionError("CVSS:4.0 vector prefix was treated as a numeric score")

    unknown = payload(advisory("not-a-cvss-score"))
    result = run_gate(unknown)
    expect_blocked(result, "UNKNOWN")

    low = payload(advisory("3.9"))
    result = run_gate(low)
    if result.returncode != 0 or "OSV release gate: PASS" not in result.stdout:
        raise AssertionError(f"expected low finding to pass\n{result.stdout}")

    print("OSV release gate self-test: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
