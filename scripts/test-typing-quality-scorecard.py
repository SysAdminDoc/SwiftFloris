#!/usr/bin/env python3
"""Self-test the typing-quality scorecard script with tiny fixtures."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path
from tempfile import TemporaryDirectory


ROOT = Path(__file__).resolve().parents[1]
SCORECARD = ROOT / "scripts" / "typing-quality-scorecard.py"


def write_jsonl(path: Path, rows: list[dict[str, object]]) -> None:
    path.write_text("\n".join(json.dumps(row) for row in rows) + "\n", encoding="utf-8")


def write_latency(path: Path, values: list[float]) -> None:
    path.write_text(
        json.dumps(
            {
                "benchmark": "firstSuggestionLatency",
                "measuredAt": "2026-06-11T00:00:00Z",
                "summary": {"suggestionLatencyMedianMs": values[len(values) // 2]},
                "runs": [{"suggestionLatencyMs": value} for value in values],
            },
        ),
        encoding="utf-8",
    )


def run_scorecard(root: Path, *extra_args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [
            sys.executable,
            str(SCORECARD),
            "--trace-fixtures",
            str(root / "trace.jsonl"),
            "--glide-fixtures",
            str(root / "glide.jsonl"),
            "--benchmark-dir",
            str(root / "benchmarks"),
            "--output-json",
            str(root / "out" / "scorecard.json"),
            "--output-md",
            str(root / "out" / "scorecard.md"),
            *extra_args,
        ],
        cwd=ROOT,
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )


def main() -> int:
    with TemporaryDirectory() as tmp:
        root = Path(tmp)
        benchmarks = root / "benchmarks"
        benchmarks.mkdir()
        write_jsonl(
            root / "trace.jsonl",
            [
                {
                    "name": "tap correction",
                    "currentWord": "teh",
                    "typedWordKnown": False,
                    "quickPredictionInsert": False,
                    "expectedRanked": ["teh", "the"],
                    "expectedSpacebarText": "the",
                },
                {
                    "name": "prediction",
                    "currentWord": "",
                    "typedWordKnown": False,
                    "quickPredictionInsert": True,
                    "expectedRanked": ["I'm", "I"],
                    "expectedSpacebarText": "I",
                },
            ],
        )
        write_jsonl(
            root / "glide.jsonl",
            [
                {
                    "name": "glide context",
                    "committedWord": "in",
                    "candidateWords": ["in", "I'm", "on"],
                    "nextWord": "going",
                    "contextScores": {"i'm": 0.5, "in": 0.0},
                    "expectedReplacement": "I'm",
                }
            ],
        )
        write_latency(benchmarks / "candidate-suggestion.json", [10.0, 12.0, 14.0, 16.0, 18.0])

        passing = run_scorecard(root, "--max-p95-suggestion-latency-ms", "20")
        if passing.returncode != 0:
            print(passing.stdout)
            print("expected valid scorecard fixtures to pass")
            return 1

        failing = run_scorecard(root, "--max-p95-suggestion-latency-ms", "15")
        if failing.returncode != 1:
            print(failing.stdout)
            print("expected p95 latency threshold failure")
            return 1

    print("typing quality scorecard self-test: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
