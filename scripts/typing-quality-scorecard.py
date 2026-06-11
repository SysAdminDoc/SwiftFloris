#!/usr/bin/env python3
"""Emit a local SwiftFloris typing-quality scorecard.

The scorecard joins deterministic replay fixtures with benchmark JSON produced
by the existing adb harness. The replay fixtures are executable contracts in
the JVM test suite; this script turns the same corpus into a compact report
that can be trended and uploaded by CI.
"""

from __future__ import annotations

import argparse
import json
import math
import sys
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]


@dataclass(frozen=True)
class BenchmarkFile:
    path: Path
    measured_at: str
    summary: dict[str, Any]
    runs: list[dict[str, Any]]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate the SwiftFloris typing-quality scorecard.",
    )
    parser.add_argument(
        "--trace-fixtures",
        default="app/src/test/resources/swiftkey/replay/trace_replay_cases.jsonl",
        help="Tap/autocorrect/next-word replay fixture JSONL.",
    )
    parser.add_argument(
        "--glide-fixtures",
        default="app/src/test/resources/swiftkey/replay/glide_context_cases.jsonl",
        help="Glide context-rescue fixture JSONL.",
    )
    parser.add_argument(
        "--benchmark-dir",
        default="docs/benchmark-results",
        help="Directory containing firstSuggestionLatency benchmark JSON.",
    )
    parser.add_argument(
        "--output-json",
        default="build/typing-quality-scorecard/typing-quality-scorecard.json",
        help="JSON scorecard output path.",
    )
    parser.add_argument(
        "--output-md",
        default="build/typing-quality-scorecard/typing-quality-scorecard.md",
        help="Markdown scorecard output path.",
    )
    parser.add_argument(
        "--max-p95-suggestion-latency-ms",
        type=float,
        default=2500.0,
        help="Fail if first-suggestion p95 exceeds this value.",
    )
    parser.add_argument(
        "--min-tap-correction-top4-rate",
        type=float,
        default=1.0,
        help="Fail if expected tap correction target coverage in top 4 falls below this rate.",
    )
    parser.add_argument(
        "--min-glide-top4-rate",
        type=float,
        default=0.60,
        help="Fail if expected glide replacement coverage in top 4 falls below this rate.",
    )
    parser.add_argument(
        "--min-glide-context-rescue-top4-rate",
        type=float,
        default=1.0,
        help="Fail if context-rescue glide replacement coverage in top 4 falls below this rate.",
    )
    return parser.parse_args()


def resolve(path: str) -> Path:
    candidate = Path(path)
    return candidate if candidate.is_absolute() else ROOT / candidate


def load_jsonl(path: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    with path.open("r", encoding="utf-8") as handle:
        for line_no, raw_line in enumerate(handle, start=1):
            line = raw_line.strip()
            if not line:
                continue
            try:
                payload = json.loads(line)
            except json.JSONDecodeError as exc:
                raise ValueError(f"{path}:{line_no}: invalid JSON: {exc}") from exc
            if not isinstance(payload, dict):
                raise ValueError(f"{path}:{line_no}: expected JSON object")
            rows.append(payload)
    return rows


def latest_first_suggestion_latency(directory: Path) -> BenchmarkFile | None:
    if not directory.exists():
        return None
    latest: BenchmarkFile | None = None
    for path in sorted(directory.glob("*.json")):
        payload = json.loads(path.read_text(encoding="utf-8-sig"))
        if payload.get("benchmark") != "firstSuggestionLatency":
            continue
        summary = payload.get("summary")
        runs = payload.get("runs")
        if not isinstance(summary, dict):
            raise ValueError(f"{path}: missing object 'summary'")
        if not isinstance(runs, list):
            runs = []
        candidate = BenchmarkFile(
            path=path,
            measured_at=str(payload.get("measuredAt") or ""),
            summary=summary,
            runs=[run for run in runs if isinstance(run, dict)],
        )
        if latest is None or latest_sort_key(candidate) >= latest_sort_key(latest):
            latest = candidate
    return latest


def latest_sort_key(item: BenchmarkFile) -> tuple[datetime, str]:
    try:
        parsed = datetime.fromisoformat(item.measured_at.replace("Z", "+00:00"))
    except ValueError:
        parsed = datetime.min
    return parsed, item.path.name


def percentile(values: list[float], pct: float) -> float | None:
    if not values:
        return None
    sorted_values = sorted(values)
    index = max(0, min(len(sorted_values) - 1, math.ceil((pct / 100.0) * len(sorted_values)) - 1))
    return sorted_values[index]


def rate(numerator: int, denominator: int) -> float:
    return float(numerator) / float(denominator) if denominator else 0.0


def score_trace_fixtures(cases: list[dict[str, Any]]) -> dict[str, Any]:
    correction_cases = []
    prediction_cases = []
    literal_protection_cases = []
    for case in cases:
        current_word = str(case.get("currentWord") or "")
        expected_spacebar = case.get("expectedSpacebarText")
        quick_prediction = bool(case.get("quickPredictionInsert"))
        typed_word_known = bool(case.get("typedWordKnown"))
        expected_ranked = case.get("expectedRanked")
        if not isinstance(expected_ranked, list):
            expected_ranked = []

        if current_word and isinstance(expected_spacebar, str) and expected_spacebar != current_word:
            correction_cases.append((case, expected_ranked))
        if (quick_prediction or not current_word) and isinstance(expected_spacebar, str):
            prediction_cases.append((case, expected_ranked))
        if current_word and typed_word_known and expected_spacebar is None:
            literal_protection_cases.append((case, expected_ranked))

    correction_top4 = sum(
        1 for case, ranked in correction_cases
        if str(case.get("expectedSpacebarText")) in [str(item) for item in ranked[:4]]
    )
    prediction_top4 = sum(
        1 for case, ranked in prediction_cases
        if str(case.get("expectedSpacebarText")) in [str(item) for item in ranked[:4]]
    )
    literal_protection_hits = sum(
        1 for case, ranked in literal_protection_cases
        if str(case.get("currentWord") or "") in [str(item) for item in ranked[:4]]
    )

    return {
        "caseCount": len(cases),
        "tapCorrection": {
            "caseCount": len(correction_cases),
            "top4TargetCount": correction_top4,
            "top4TargetRate": rate(correction_top4, len(correction_cases)),
        },
        "nextWordPrediction": {
            "caseCount": len(prediction_cases),
            "top4TargetCount": prediction_top4,
            "top4TargetRate": rate(prediction_top4, len(prediction_cases)),
        },
        "typedLiteralProtection": {
            "caseCount": len(literal_protection_cases),
            "top4LiteralCount": literal_protection_hits,
            "top4LiteralRate": rate(literal_protection_hits, len(literal_protection_cases)),
        },
    }


def score_glide_fixtures(cases: list[dict[str, Any]]) -> dict[str, Any]:
    top1 = 0
    top4 = 0
    rescue_cases = 0
    rescue_top4 = 0
    context_score_wins = 0
    for case in cases:
        candidates = [str(item) for item in case.get("candidateWords", []) if isinstance(item, str)]
        expected = str(case.get("expectedReplacement") or "")
        committed = str(case.get("committedWord") or "")
        context_scores = case.get("contextScores")
        if expected and candidates[:1] == [expected]:
            top1 += 1
        if expected and expected in candidates[:4]:
            top4 += 1
        if expected and expected != committed:
            rescue_cases += 1
            if expected in candidates[:4]:
                rescue_top4 += 1
            if isinstance(context_scores, dict):
                expected_score = float(context_scores.get(expected.lower(), 0.0) or 0.0)
                committed_score = float(context_scores.get(committed.lower(), 0.0) or 0.0)
                if expected_score > committed_score:
                    context_score_wins += 1

    return {
        "caseCount": len(cases),
        "top1ReplacementCount": top1,
        "top1ReplacementRate": rate(top1, len(cases)),
        "top4ReplacementCount": top4,
        "top4ReplacementRate": rate(top4, len(cases)),
        "contextRescue": {
            "caseCount": rescue_cases,
            "top4ReplacementCount": rescue_top4,
            "top4ReplacementRate": rate(rescue_top4, rescue_cases),
            "contextScoreWinCount": context_score_wins,
            "contextScoreWinRate": rate(context_score_wins, rescue_cases),
        },
    }


def score_latency(benchmark_dir: Path) -> dict[str, Any]:
    benchmark = latest_first_suggestion_latency(benchmark_dir)
    if benchmark is None:
        return {"available": False}
    runs = [
        float(run["suggestionLatencyMs"])
        for run in benchmark.runs
        if isinstance(run.get("suggestionLatencyMs"), (int, float))
    ]
    summary_latency = benchmark.summary.get("suggestionLatencyMedianMs")
    return {
        "available": True,
        "path": str(benchmark.path.relative_to(ROOT)) if benchmark.path.is_relative_to(ROOT) else str(benchmark.path),
        "measuredAt": benchmark.measured_at,
        "runCount": len(runs),
        "medianMs": float(summary_latency) if isinstance(summary_latency, (int, float)) else percentile(runs, 50.0),
        "p95Ms": percentile(runs, 95.0),
    }


def validate(scorecard: dict[str, Any], args: argparse.Namespace) -> list[str]:
    failures: list[str] = []
    trace = scorecard["traceReplay"]
    tap = trace["tapCorrection"]
    prediction = trace["nextWordPrediction"]
    glide = scorecard["glideReplay"]
    latency = scorecard["firstSuggestionLatency"]

    if tap["caseCount"] <= 0:
        failures.append("tap correction fixture coverage is empty")
    if tap["top4TargetRate"] < args.min_tap_correction_top4_rate:
        failures.append("tap correction top-4 target coverage is below threshold")
    if prediction["caseCount"] <= 0:
        failures.append("next-word prediction fixture coverage is empty")
    if glide["caseCount"] <= 0:
        failures.append("glide fixture coverage is empty")
    if glide["top4ReplacementRate"] < args.min_glide_top4_rate:
        failures.append("glide top-4 replacement coverage is below threshold")
    if glide["contextRescue"]["top4ReplacementRate"] < args.min_glide_context_rescue_top4_rate:
        failures.append("glide context-rescue top-4 replacement coverage is below threshold")
    if not latency.get("available"):
        failures.append("firstSuggestionLatency benchmark JSON is missing")
    elif latency.get("p95Ms") is None:
        failures.append("firstSuggestionLatency benchmark has no run-level latency samples")
    elif float(latency["p95Ms"]) > args.max_p95_suggestion_latency_ms:
        failures.append(
            f"firstSuggestionLatency p95 {latency['p95Ms']:.3f} ms exceeds "
            f"{args.max_p95_suggestion_latency_ms:.3f} ms"
        )
    return failures


def write_markdown(scorecard: dict[str, Any], path: Path) -> None:
    trace = scorecard["traceReplay"]
    glide = scorecard["glideReplay"]
    latency = scorecard["firstSuggestionLatency"]
    failures = scorecard["failures"]
    status = "PASS" if not failures else "FAIL"
    lines = [
        f"# SwiftFloris Typing Quality Scorecard",
        "",
        f"Status: {status}",
        "",
        "| Area | Metric | Value |",
        "| --- | --- | ---: |",
        f"| Tap correction | Fixture cases | {trace['tapCorrection']['caseCount']} |",
        f"| Tap correction | Top-4 target rate | {trace['tapCorrection']['top4TargetRate']:.3f} |",
        f"| Next-word prediction | Fixture cases | {trace['nextWordPrediction']['caseCount']} |",
        f"| Next-word prediction | Top-4 target rate | {trace['nextWordPrediction']['top4TargetRate']:.3f} |",
        f"| Glide | Fixture cases | {glide['caseCount']} |",
        f"| Glide | Top-1 replacement rate | {glide['top1ReplacementRate']:.3f} |",
        f"| Glide | Top-4 replacement rate | {glide['top4ReplacementRate']:.3f} |",
        f"| Glide context rescue | Cases | {glide['contextRescue']['caseCount']} |",
        f"| Glide context rescue | Top-4 replacement rate | {glide['contextRescue']['top4ReplacementRate']:.3f} |",
    ]
    if latency.get("available"):
        lines.extend([
            f"| First suggestion latency | Median ms | {latency.get('medianMs') or 0.0:.3f} |",
            f"| First suggestion latency | p95 ms | {latency.get('p95Ms') or 0.0:.3f} |",
            f"| First suggestion latency | Runs | {latency.get('runCount') or 0} |",
        ])
    else:
        lines.append("| First suggestion latency | Benchmark | missing |")
    if failures:
        lines.extend(["", "## Failures", ""])
        lines.extend(f"- {failure}" for failure in failures)
    lines.append("")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines), encoding="utf-8")


def main() -> int:
    args = parse_args()
    trace_cases = load_jsonl(resolve(args.trace_fixtures))
    glide_cases = load_jsonl(resolve(args.glide_fixtures))
    scorecard = {
        "traceReplay": score_trace_fixtures(trace_cases),
        "glideReplay": score_glide_fixtures(glide_cases),
        "firstSuggestionLatency": score_latency(resolve(args.benchmark_dir)),
        "thresholds": {
            "maxP95SuggestionLatencyMs": args.max_p95_suggestion_latency_ms,
            "minTapCorrectionTop4Rate": args.min_tap_correction_top4_rate,
            "minGlideTop4Rate": args.min_glide_top4_rate,
            "minGlideContextRescueTop4Rate": args.min_glide_context_rescue_top4_rate,
        },
    }
    failures = validate(scorecard, args)
    scorecard["status"] = "PASS" if not failures else "FAIL"
    scorecard["failures"] = failures

    output_json = resolve(args.output_json)
    output_md = resolve(args.output_md)
    output_json.parent.mkdir(parents=True, exist_ok=True)
    output_json.write_text(json.dumps(scorecard, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    write_markdown(scorecard, output_md)

    print(f"typing quality scorecard: {scorecard['status']}")
    print(f"json: {output_json}")
    print(f"markdown: {output_md}")
    for failure in failures:
        print(f"failure: {failure}", file=sys.stderr)
    return 0 if not failures else 1


if __name__ == "__main__":
    raise SystemExit(main())
