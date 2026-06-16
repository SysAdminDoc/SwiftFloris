#!/usr/bin/env python3
"""Evaluate SwiftFloris glide typing accuracy against a test set.

Reads a JSONL file where each line is a benchmark case:
  {"word": "hello", "candidates": ["hello", "jello", "help"], "topK": 4, "runtimeMs": 12.3}

The 'candidates' array is ordered by classifier rank. The evaluation reports
top-1 accuracy (first candidate matches), top-K accuracy (expected word in
first K candidates), and runtime statistics.

Usage:
  python scripts/glide-benchmark.py results.jsonl
  python scripts/glide-benchmark.py results.jsonl --top-k 4 --output-json build/glide-benchmark.json
"""

from __future__ import annotations

import argparse
import json
import math
import sys
from dataclasses import dataclass, field
from pathlib import Path


@dataclass
class BenchmarkCase:
    word: str
    candidates: list[str]
    runtime_ms: float = 0.0


@dataclass
class BenchmarkReport:
    total_cases: int = 0
    top1_hits: int = 0
    topk_hits: int = 0
    top_k: int = 4
    runtimes_ms: list[float] = field(default_factory=list)

    @property
    def top1_accuracy(self) -> float:
        return self.top1_hits / self.total_cases if self.total_cases else 0.0

    @property
    def topk_accuracy(self) -> float:
        return self.topk_hits / self.total_cases if self.total_cases else 0.0

    @property
    def top1_error(self) -> float:
        return 1.0 - self.top1_accuracy

    @property
    def topk_error(self) -> float:
        return 1.0 - self.topk_accuracy

    @property
    def median_runtime_ms(self) -> float | None:
        if not self.runtimes_ms:
            return None
        s = sorted(self.runtimes_ms)
        mid = len(s) // 2
        return s[mid] if len(s) % 2 else (s[mid - 1] + s[mid]) / 2.0

    @property
    def p95_runtime_ms(self) -> float | None:
        if not self.runtimes_ms:
            return None
        s = sorted(self.runtimes_ms)
        idx = max(0, min(len(s) - 1, math.ceil(0.95 * len(s)) - 1))
        return s[idx]


def load_results(path: Path) -> list[BenchmarkCase]:
    cases: list[BenchmarkCase] = []
    with path.open("r", encoding="utf-8") as f:
        for line_no, raw in enumerate(f, 1):
            line = raw.strip()
            if not line:
                continue
            try:
                obj = json.loads(line)
            except json.JSONDecodeError as e:
                print(f"WARNING: {path}:{line_no}: invalid JSON: {e}", file=sys.stderr)
                continue
            cases.append(BenchmarkCase(
                word=str(obj.get("word", "")),
                candidates=[str(c) for c in obj.get("candidates", [])],
                runtime_ms=float(obj.get("runtimeMs", 0.0)),
            ))
    return cases


def evaluate(cases: list[BenchmarkCase], top_k: int = 4) -> BenchmarkReport:
    report = BenchmarkReport(top_k=top_k)
    for case in cases:
        report.total_cases += 1
        normalized = case.word.lower()
        candidate_lower = [c.lower() for c in case.candidates]
        if candidate_lower and candidate_lower[0] == normalized:
            report.top1_hits += 1
        if normalized in candidate_lower[:top_k]:
            report.topk_hits += 1
        if case.runtime_ms > 0:
            report.runtimes_ms.append(case.runtime_ms)
    return report


def format_markdown(report: BenchmarkReport) -> str:
    lines = [
        "# SwiftFloris Glide Benchmark Report",
        "",
        f"| Metric | Value |",
        f"| --- | ---: |",
        f"| Total cases | {report.total_cases} |",
        f"| Top-1 accuracy | {report.top1_accuracy:.3f} |",
        f"| Top-1 error | {report.top1_error:.3f} |",
        f"| Top-{report.top_k} accuracy | {report.topk_accuracy:.3f} |",
        f"| Top-{report.top_k} error | {report.topk_error:.3f} |",
    ]
    if report.median_runtime_ms is not None:
        lines.append(f"| Median runtime | {report.median_runtime_ms:.1f} ms |")
        lines.append(f"| p95 runtime | {report.p95_runtime_ms:.1f} ms |")
        lines.append(f"| Runtime samples | {len(report.runtimes_ms)} |")
    return "\n".join(lines) + "\n"


def format_json(report: BenchmarkReport) -> dict:
    result: dict = {
        "totalCases": report.total_cases,
        "topK": report.top_k,
        "top1Accuracy": round(report.top1_accuracy, 4),
        "top1Error": round(report.top1_error, 4),
        "topKAccuracy": round(report.topk_accuracy, 4),
        "topKError": round(report.topk_error, 4),
    }
    if report.median_runtime_ms is not None:
        result["medianRuntimeMs"] = round(report.median_runtime_ms, 2)
        result["p95RuntimeMs"] = round(report.p95_runtime_ms or 0, 2)
        result["runtimeSamples"] = len(report.runtimes_ms)
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description="Evaluate SwiftFloris glide benchmark results.")
    parser.add_argument("results", type=Path, help="JSONL results file from the benchmark harness.")
    parser.add_argument("--top-k", type=int, default=4, help="K for top-K accuracy (default: 4).")
    parser.add_argument("--output-json", type=Path, default=None, help="Write JSON report to this path.")
    parser.add_argument("--output-md", type=Path, default=None, help="Write Markdown report to this path.")
    args = parser.parse_args()

    cases = load_results(args.results)
    if not cases:
        print("ERROR: no benchmark cases found.", file=sys.stderr)
        sys.exit(1)

    report = evaluate(cases, top_k=args.top_k)
    print(format_markdown(report))

    if args.output_json:
        args.output_json.parent.mkdir(parents=True, exist_ok=True)
        args.output_json.write_text(json.dumps(format_json(report), indent=2) + "\n", encoding="utf-8")
        print(f"JSON report written to {args.output_json}")

    if args.output_md:
        args.output_md.parent.mkdir(parents=True, exist_ok=True)
        args.output_md.write_text(format_markdown(report), encoding="utf-8")
        print(f"Markdown report written to {args.output_md}")

    status = "PASS" if report.topk_accuracy >= 0.60 else "FAIL"
    print(f"\nStatus: {status} (top-{report.top_k} accuracy {report.topk_accuracy:.3f} vs threshold 0.600)")
    sys.exit(0 if status == "PASS" else 1)


if __name__ == "__main__":
    main()
