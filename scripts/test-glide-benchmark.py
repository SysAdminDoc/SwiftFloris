#!/usr/bin/env python3
"""Self-test the glide benchmark reporter with tiny synthetic results."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path
from tempfile import TemporaryDirectory


ROOT = Path(__file__).resolve().parents[1]
BENCHMARK = ROOT / "scripts" / "glide-benchmark.py"


def write_jsonl(path: Path, rows: list[dict[str, object]]) -> None:
    path.write_text("\n".join(json.dumps(row) for row in rows) + "\n", encoding="utf-8")


def main() -> int:
    with TemporaryDirectory() as tmp:
        root = Path(tmp)
        results = root / "glide-results.jsonl"
        output_json = root / "report.json"
        output_md = root / "report.md"
        write_jsonl(
            results,
            [
                {"word": "hello", "candidates": ["hello", "help"], "runtimeMs": 5.0},
                {"word": "world", "candidates": ["word", "world"], "runtimeMs": 10.0},
                {"word": "swift", "candidates": ["shift", "swim", "swift"], "runtimeMs": 20.0},
                {"word": "floris", "failed": True, "runtimeMs": 40.0, "error": "fixture timeout"},
            ],
        )

        completed = subprocess.run(
            [
                sys.executable,
                str(BENCHMARK),
                str(results),
                "--top-k",
                "4",
                "--output-json",
                str(output_json),
                "--output-md",
                str(output_md),
            ],
            cwd=ROOT,
            check=False,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
        )
        if completed.returncode != 0:
            print(completed.stdout)
            print("expected synthetic glide benchmark results to pass")
            return 1

        stdout = completed.stdout
        if "| Failed cases | 1 |" not in stdout or "| p95 runtime | 40.0 ms |" not in stdout:
            print(stdout)
            print("expected markdown output to include failure count and p95 runtime")
            return 1

        report = json.loads(output_json.read_text(encoding="utf-8"))
        if report.get("failedCases") != 1 or report.get("p95RuntimeMs") != 40.0:
            print(report)
            print("expected JSON output to include failure count and p95 runtime")
            return 1

        if "| Evaluated cases | 3 |" not in output_md.read_text(encoding="utf-8"):
            print("expected markdown file to include evaluated case count")
            return 1

    print("glide benchmark self-test: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
