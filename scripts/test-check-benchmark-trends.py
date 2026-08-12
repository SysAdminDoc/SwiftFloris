#!/usr/bin/env python3
"""Self-test the benchmark trend gate with tiny JSON fixtures."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path
from tempfile import TemporaryDirectory


ROOT = Path(__file__).resolve().parents[1]
CHECKER = ROOT / "scripts" / "check-benchmark-trends.py"


def write_result(path: Path, benchmark: str, value: float) -> None:
    summary = {"activityTotalTimeMedianMs": value}
    if benchmark == "imeFirstRender":
        summary = {
            "activityTotalTimeMedianMs": value,
            "activityWaitTimeMedianMs": value,
            "imeFirstRenderMedianMs": value,
        }
    path.write_text(
        json.dumps(
            {
                "benchmark": benchmark,
                "measuredAt": "2026-06-11T00:00:00Z",
                "device": {
                    "deviceKey": "sha256:fixture",
                    "model": "fixture",
                    "androidRelease": "16",
                    "sdk": "36",
                },
                "summary": summary,
            },
        ),
        encoding="utf-8",
    )


def run_checker(baseline_dir: Path, candidate_dir: Path, report: Path, *args: str) -> int:
    completed = subprocess.run(
        [
            sys.executable,
            str(CHECKER),
            "--baseline-dir",
            str(baseline_dir),
            "--candidate-dir",
            str(candidate_dir),
            "--report",
            str(report),
            *args,
        ],
        cwd=ROOT,
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )
    if completed.returncode not in {0, 1}:
        print(completed.stdout)
    return completed.returncode


def main() -> int:
    with TemporaryDirectory() as tmp:
        root = Path(tmp)
        baseline_dir = root / "baseline"
        candidate_dir = root / "candidate"
        baseline_dir.mkdir()
        candidate_dir.mkdir()
        report = root / "report.md"

        write_result(baseline_dir / "baseline-ime-first-render.json", "imeFirstRender", 100.0)
        write_result(candidate_dir / "candidate-ime-first-render.json", "imeFirstRender", 107.0)
        if run_checker(baseline_dir, candidate_dir, report, "--require-benchmark", "imeFirstRender") != 0:
            print("expected in-threshold benchmark to pass")
            return 1

        write_result(candidate_dir / "candidate-ime-first-render.json", "imeFirstRender", 109.0)
        if run_checker(baseline_dir, candidate_dir, report, "--require-benchmark", "imeFirstRender") != 1:
            print("expected over-threshold benchmark to fail")
            return 1

        (candidate_dir / "candidate-ime-first-render.json").unlink()
        write_result(candidate_dir / "candidate-theme-switch.json", "themeSwitch", 1.0)
        if run_checker(baseline_dir, candidate_dir, report, "--require-benchmark", "imeFirstRender") != 1:
            print("expected missing required benchmark to fail")
            return 1

        (candidate_dir / "candidate-theme-switch.json").write_text(
            json.dumps(
                {
                    "benchmark": "themeSwitch",
                    "device": {"serial": "fixture-value"},
                    "summary": {"themeSwitchMedianOfRunMediansMs": 1.0},
                },
            ),
            encoding="utf-8",
        )
        if run_checker(baseline_dir, candidate_dir, report) != 2:
            print("expected device serial field to be rejected")
            return 1

    print("benchmark trend checker self-test: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
