#!/usr/bin/env python3
"""Compare SwiftFloris benchmark JSON output against committed baselines."""

from __future__ import annotations

import argparse
import json
import os
import sys
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any


REGRESSION_THRESHOLD_PCT = 8.0
IMPROVEMENT_THRESHOLD_PCT = -5.0

WATCHED_TIMING_METRICS: dict[str, tuple[str, ...]] = {
    "backupRestore": (
        "backupCreateMedianMs",
        "restorePrepareMedianMs",
        "restoreApplyMedianMs",
        "restoreTotalMedianMs",
    ),
    "candidateRowRecomposition": (
        "recomposeMedianOfRunMediansMs",
        "recomposeMaxMedianMs",
        "recomposeTotalMedianMs",
        "nlpSuggestMedianOfRunMediansMs",
        "nlpSuggestMaxMedianMs",
    ),
    "dictionaryLoadAndPreload": (
        "dictionaryLoadMedianMs",
        "dictionaryPreloadMedianMs",
        "symSpellDistance1BuildMedianMs",
        "symSpellDistance2BuildMedianMs",
        "postPreloadSpellMedianMs",
        "postPreloadSuggestionMedianMs",
    ),
    "firstSuggestionLatency": (
        "suggestionLatencyMedianMs",
    ),
    "imeFirstRender": (
        "activityTotalTimeMedianMs",
        "activityWaitTimeMedianMs",
        "imeFirstRenderMedianMs",
    ),
    "themeSwitch": (
        "themeSwitchMedianOfRunMediansMs",
        "themeSwitchMaxMedianMs",
        "themeSwitchTotalMedianMs",
        "benchmarkStepMedianOfRunMediansMs",
        "benchmarkStepMaxMedianMs",
        "benchmarkColdStepMedianMs",
        "benchmarkWarmStepMedianMs",
    ),
}

ZERO_CEILING_METRICS: dict[str, tuple[str, ...]] = {
    "backupRestore": (
        "missingSectionsMedian",
        "failedSectionsMedian",
    ),
    "themeSwitch": (
        "loadFailureCountMedian",
    ),
}


@dataclass(frozen=True)
class BenchmarkFile:
    benchmark: str
    path: Path
    measured_at: str
    device: dict[str, Any]
    summary: dict[str, Any]


@dataclass(frozen=True)
class Row:
    benchmark: str
    metric: str
    baseline: float | None
    candidate: float | None
    delta_pct: float | None
    status: str
    note: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Compare candidate SwiftFloris benchmark JSON files against committed baselines.",
    )
    parser.add_argument(
        "--baseline-dir",
        default="docs/benchmark-results",
        help="Directory containing committed baseline-*.json files.",
    )
    parser.add_argument(
        "--candidate-dir",
        default="build/benchmark-results",
        help="Directory containing newly generated candidate benchmark JSON files.",
    )
    parser.add_argument(
        "--report",
        default="build/benchmark-results/benchmark-trend-report.md",
        help="Markdown report path.",
    )
    parser.add_argument(
        "--require-all-baselines",
        action="store_true",
        help="Fail when a committed baseline has no candidate benchmark result.",
    )
    return parser.parse_args()


def load_benchmark(path: Path) -> BenchmarkFile:
    try:
        payload = json.loads(path.read_text(encoding="utf-8-sig"))
    except json.JSONDecodeError as exc:
        raise ValueError(f"{path}: invalid JSON: {exc}") from exc

    benchmark = payload.get("benchmark")
    summary = payload.get("summary")
    if not isinstance(benchmark, str) or not benchmark.strip():
        raise ValueError(f"{path}: missing string 'benchmark'")
    if not isinstance(summary, dict):
        raise ValueError(f"{path}: missing object 'summary'")

    measured_at = payload.get("measuredAt")
    device = payload.get("device")
    return BenchmarkFile(
        benchmark=benchmark,
        path=path,
        measured_at=measured_at if isinstance(measured_at, str) else "",
        device=device if isinstance(device, dict) else {},
        summary=summary,
    )


def parse_timestamp(value: str) -> datetime:
    if not value:
        return datetime.min
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return datetime.min


def latest_by_benchmark(files: list[BenchmarkFile]) -> dict[str, BenchmarkFile]:
    latest: dict[str, BenchmarkFile] = {}
    for item in files:
        existing = latest.get(item.benchmark)
        if existing is None:
            latest[item.benchmark] = item
            continue
        if (parse_timestamp(item.measured_at), item.path.name) >= (
            parse_timestamp(existing.measured_at),
            existing.path.name,
        ):
            latest[item.benchmark] = item
    return latest


def load_files(directory: Path, pattern: str) -> list[BenchmarkFile]:
    if not directory.exists():
        return []
    files = []
    for path in sorted(directory.glob(pattern)):
        if path.is_file():
            files.append(load_benchmark(path))
    return files


def as_float(value: Any) -> float | None:
    if value is None or isinstance(value, bool):
        return None
    if isinstance(value, (int, float)):
        return float(value)
    return None


def format_number(value: float | None) -> str:
    if value is None:
        return "n/a"
    if abs(value) >= 100:
        return f"{value:.3f}"
    if abs(value) >= 10:
        return f"{value:.4f}"
    return f"{value:.6f}"


def format_delta(value: float | None) -> str:
    if value is None:
        return "n/a"
    sign = "+" if value > 0 else ""
    return f"{sign}{value:.2f}%"


def device_label(device: dict[str, Any]) -> str:
    model = str(device.get("model") or "").strip()
    release = str(device.get("androidRelease") or "").strip()
    sdk = str(device.get("sdk") or "").strip()
    parts = [part for part in (model, f"Android {release}" if release else "", f"SDK {sdk}" if sdk else "") if part]
    return " / ".join(parts) if parts else "unknown"


def compare_timing_metric(benchmark: str, metric: str, baseline: Any, candidate: Any) -> Row:
    baseline_value = as_float(baseline)
    candidate_value = as_float(candidate)
    if baseline_value is None or candidate_value is None:
        return Row(benchmark, metric, baseline_value, candidate_value, None, "ERROR", "missing numeric value")
    if baseline_value <= 0:
        return Row(benchmark, metric, baseline_value, candidate_value, None, "ERROR", "baseline must be > 0")

    delta_pct = ((candidate_value - baseline_value) / baseline_value) * 100.0
    if delta_pct > REGRESSION_THRESHOLD_PCT:
        status = "REGRESSION"
        note = f"exceeds +{REGRESSION_THRESHOLD_PCT:.0f}% hold threshold"
    elif delta_pct <= IMPROVEMENT_THRESHOLD_PCT:
        status = "IMPROVEMENT"
        note = f"beats {abs(IMPROVEMENT_THRESHOLD_PCT):.0f}% improvement note threshold"
    else:
        status = "PASS"
        note = "within trend window"
    return Row(benchmark, metric, baseline_value, candidate_value, delta_pct, status, note)


def compare_ceiling_metric(benchmark: str, metric: str, candidate: Any) -> Row:
    candidate_value = as_float(candidate)
    if candidate_value is None:
        return Row(benchmark, metric, 0.0, None, None, "ERROR", "missing numeric guardrail")
    if candidate_value > 0.0:
        return Row(benchmark, metric, 0.0, candidate_value, None, "REGRESSION", "must remain 0")
    return Row(benchmark, metric, 0.0, candidate_value, None, "PASS", "guardrail clear")


def compare_benchmark(baseline: BenchmarkFile, candidate: BenchmarkFile) -> list[Row]:
    rows: list[Row] = []
    metrics = WATCHED_TIMING_METRICS.get(baseline.benchmark)
    if metrics is None:
        metrics = tuple(
            key for key, value in baseline.summary.items()
            if key.endswith("Ms") and as_float(value) is not None
        )

    if not metrics:
        rows.append(Row(baseline.benchmark, "(summary)", None, None, None, "ERROR", "no watched timing metrics"))
        return rows

    for metric in metrics:
        rows.append(compare_timing_metric(
            baseline.benchmark,
            metric,
            baseline.summary.get(metric),
            candidate.summary.get(metric),
        ))

    for metric in ZERO_CEILING_METRICS.get(baseline.benchmark, ()):
        rows.append(compare_ceiling_metric(baseline.benchmark, metric, candidate.summary.get(metric)))

    return rows


def markdown_report(
    rows: list[Row],
    baselines: dict[str, BenchmarkFile],
    candidates: dict[str, BenchmarkFile],
    missing: list[str],
) -> str:
    failing = [row for row in rows if row.status in {"ERROR", "REGRESSION"}]
    lines = [
        "# SwiftFloris Benchmark Trend Report",
        "",
        f"- Regression hold threshold: > +{REGRESSION_THRESHOLD_PCT:.0f}% on watched timing medians.",
        f"- Improvement note threshold: <= {IMPROVEMENT_THRESHOLD_PCT:.0f}% on watched timing medians.",
        "- Zero guardrails: restore missing/failed sections and theme load failures must remain 0.",
        f"- Overall result: {'FAIL' if failing or missing else 'PASS'}",
        "",
        "## Results",
        "",
        "| Benchmark | Metric | Baseline | Candidate | Delta | Status | Note |",
        "|---|---|---:|---:|---:|---|---|",
    ]
    for row in rows:
        lines.append(
            "| "
            f"{row.benchmark} | "
            f"`{row.metric}` | "
            f"{format_number(row.baseline)} | "
            f"{format_number(row.candidate)} | "
            f"{format_delta(row.delta_pct)} | "
            f"{row.status} | "
            f"{row.note} |"
        )
    for benchmark in missing:
        lines.append(f"| {benchmark} | n/a | n/a | n/a | n/a | MISSING | no candidate JSON |")

    lines.extend([
        "",
        "## Inputs",
        "",
        "| Benchmark | Baseline | Candidate | Baseline device | Candidate device |",
        "|---|---|---|---|---|",
    ])
    for benchmark in sorted(baselines):
        baseline = baselines[benchmark]
        candidate = candidates.get(benchmark)
        lines.append(
            "| "
            f"{benchmark} | "
            f"`{baseline.path.as_posix()}` | "
            f"{'`' + candidate.path.as_posix() + '`' if candidate else 'n/a'} | "
            f"{device_label(baseline.device)} | "
            f"{device_label(candidate.device) if candidate else 'n/a'} |"
        )
    lines.append("")
    return "\n".join(lines)


def main() -> int:
    args = parse_args()
    baseline_dir = Path(args.baseline_dir)
    candidate_dir = Path(args.candidate_dir)
    report_path = Path(args.report)

    try:
        baselines = latest_by_benchmark(load_files(baseline_dir, "baseline-*.json"))
        candidates = latest_by_benchmark(load_files(candidate_dir, "*.json"))
    except ValueError as exc:
        print(f"benchmark trend check: {exc}", file=sys.stderr)
        return 2

    if not baselines:
        print(f"benchmark trend check: no baselines found in {baseline_dir}", file=sys.stderr)
        return 2
    if not candidates:
        print(f"benchmark trend check: no candidate results found in {candidate_dir}", file=sys.stderr)
        return 2

    rows: list[Row] = []
    missing: list[str] = []
    for benchmark in sorted(baselines):
        candidate = candidates.get(benchmark)
        if candidate is None:
            missing.append(benchmark)
            continue
        rows.extend(compare_benchmark(baselines[benchmark], candidate))

    if not args.require_all_baselines:
        missing = []

    report = markdown_report(rows, baselines, candidates, missing)
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(report, encoding="utf-8")

    step_summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if step_summary:
        with open(step_summary, "a", encoding="utf-8") as handle:
            handle.write(report)
            handle.write("\n")

    failing = [row for row in rows if row.status in {"ERROR", "REGRESSION"}]
    print(report)
    if failing or missing:
        print(f"benchmark trend check: FAIL ({len(failing)} failing metrics, {len(missing)} missing candidates)")
        return 1
    print(f"benchmark trend check: PASS ({len(rows)} metrics checked)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
