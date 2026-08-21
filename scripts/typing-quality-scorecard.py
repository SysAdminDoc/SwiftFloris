#!/usr/bin/env python3
"""Emit a local SwiftFloris typing-quality scorecard.

The scorecard joins deterministic replay fixtures with benchmark JSON produced
by the existing adb harness. The replay fixtures are executable contracts in
the JVM test suite; this script turns the same corpus into a compact report.
"""

from __future__ import annotations

import argparse
import json
import math
import sys
from dataclasses import dataclass
from datetime import datetime, timezone
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
    parser.add_argument(
        "--min-glide-endpoint-plausibility-rate",
        type=float,
        default=1.0,
        help="Fail if endpoint-plausibility glide replay outcomes fall below this rate.",
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
                raise TypeError(f"{path}:{line_no}: expected JSON object")
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
            raise TypeError(f"{path}: missing object 'summary'")
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
        if parsed.tzinfo is None:
            parsed = parsed.replace(tzinfo=timezone.utc)
    except ValueError:
        parsed = datetime.min.replace(tzinfo=timezone.utc)
    return parsed, item.path.name


def percentile(values: list[float], pct: float) -> float | None:
    if not values:
        return None
    sorted_values = sorted(values)
    index = max(0, min(len(sorted_values) - 1, math.ceil((pct / 100.0) * len(sorted_values)) - 1))
    return sorted_values[index]


def rate(numerator: int, denominator: int) -> float:
    return float(numerator) / float(denominator) if denominator else 0.0


AUTOCORRECT_THRESHOLD_PERCENTAGES = tuple(range(50, 101, 5))
AUTOCORRECT_MIN_LANGUAGE_CONFIDENCE = 0.40


def candidate_confidence(case: dict[str, Any], text: str) -> float:
    scored = case.get("scored")
    if not isinstance(scored, list):
        return 0.0
    for candidate in scored:
        if isinstance(candidate, dict) and str(candidate.get("text") or "") == text:
            value = candidate.get("providerConfidence")
            if isinstance(value, (int, float)):
                return max(0.0, min(1.0, float(value)))
    return 0.0


def eligible_nonliteral_candidates(case: dict[str, Any]) -> list[dict[str, Any]]:
    current_word = str(case.get("currentWord") or "")
    scored = case.get("scored")
    if not isinstance(scored, list):
        return []
    candidates = []
    for candidate in scored:
        if not isinstance(candidate, dict):
            continue
        if str(candidate.get("text") or "") == current_word:
            continue
        if not bool(candidate.get("autoCommitEligible")):
            continue
        language_confidence = candidate.get("languageConfidence", 1.0)
        if not isinstance(language_confidence, (int, float)):
            continue
        if float(language_confidence) < AUTOCORRECT_MIN_LANGUAGE_CONFIDENCE:
            continue
        candidates.append(candidate)
    return candidates


def score_autocorrect_thresholds(cases: list[dict[str, Any]]) -> dict[str, Any]:
    correction_cases = [
        case
        for case in cases
        if str(case.get("currentWord") or "") and
        isinstance(case.get("expectedSpacebarText"), str) and
        str(case.get("expectedSpacebarText")) != str(case.get("currentWord") or "")
    ]
    literal_cases = [
        case
        for case in cases
        if str(case.get("currentWord") or "") and
        bool(case.get("typedWordKnown")) and
        case.get("expectedSpacebarText") is None
    ]

    rows = []
    for percent in AUTOCORRECT_THRESHOLD_PERCENTAGES:
        threshold = percent / 100.0
        accepted_corrections = sum(
            1
            for case in correction_cases
            if candidate_confidence(case, str(case.get("expectedSpacebarText"))) >= threshold
        )
        false_positives = sum(
            1
            for case in literal_cases
            if any(
                float(candidate.get("providerConfidence", 0.0) or 0.0) >= threshold
                for candidate in eligible_nonliteral_candidates(case)
            )
        )
        protected_literals = len(literal_cases) - false_positives
        decision_count = len(correction_cases) + len(literal_cases)
        correct_decisions = accepted_corrections + protected_literals
        accuracy = rate(correct_decisions, decision_count)
        coverage = rate(accepted_corrections, len(correction_cases))
        precision = rate(accepted_corrections, accepted_corrections + false_positives)
        score = (accuracy * 0.70) + (coverage * 0.30)
        rows.append(
            {
                "percent": percent,
                "threshold": threshold,
                "correctionCaseCount": len(correction_cases),
                "acceptedCorrectionCount": accepted_corrections,
                "literalProtectionCaseCount": len(literal_cases),
                "falsePositiveCount": false_positives,
                "protectedLiteralCount": protected_literals,
                "accuracy": accuracy,
                "coverage": coverage,
                "precision": precision,
                "score": score,
            },
        )

    selected = max(rows, key=lambda row: (row["score"], row["accuracy"], -row["percent"]))
    return {
        "selectedDefaultPercent": selected["percent"],
        "thresholds": rows,
    }


GLIDE_CONTEXT_MAX_RECOVERABLE_WORD_LENGTH = 4
GLIDE_CONTEXT_MAX_CANDIDATES_TO_RESCORE = 4
GLIDE_CONTEXT_RANK_STEP_PENALTY = 0.12
GLIDE_CONTEXT_WEIGHT = 0.55
GLIDE_CONTEXT_MIN_SCORE = 0.35
GLIDE_CONTEXT_MIN_SWITCH_MARGIN = 0.10
GLIDE_ENDPOINT_PLAUSIBILITY_TAG = "glide-endpoint-plausibility"
GLIDE_ENDPOINT_NEIGHBORS = {
    "q": {"w"},
    "w": {"q", "e"},
    "e": {"w", "r"},
    "r": {"e", "t"},
    "t": {"r", "y"},
    "y": {"t", "u"},
    "u": {"y", "i"},
    "i": {"u", "o"},
    "o": {"i", "p"},
    "p": {"o"},
    "a": {"s"},
    "s": {"a", "d"},
    "d": {"s", "f"},
    "f": {"d", "g"},
    "g": {"f", "h"},
    "h": {"g", "j"},
    "j": {"h", "k"},
    "k": {"j", "l"},
    "l": {"k"},
    "z": {"x"},
    "x": {"z", "c"},
    "c": {"x", "v"},
    "v": {"c", "b"},
    "b": {"v", "n"},
    "n": {"b", "m"},
    "m": {"n"},
}


def normalize_glide_word_for_context(word: str) -> str | None:
    normalized = word.strip()
    while normalized and not (normalized[0].isalpha() or normalized[0] in ("'", "\u2019")):
        normalized = normalized[1:]
    while normalized and not (normalized[-1].isalpha() or normalized[-1] in ("'", "\u2019")):
        normalized = normalized[:-1]
    normalized = normalized.lower()
    if not normalized or not any(char.isalpha() for char in normalized):
        return None
    if any(not (char.isalpha() or char in ("'", "\u2019")) for char in normalized):
        return None
    return normalized


def endpoints_plausible(committed: str, candidate: str) -> bool:
    committed_letters = "".join(char for char in committed if char.isalpha())
    candidate_letters = "".join(char for char in candidate if char.isalpha())
    if not committed_letters or not candidate_letters:
        return False
    return endpoint_plausible(committed_letters[0], candidate_letters[0]) and endpoint_plausible(
        committed_letters[-1],
        candidate_letters[-1],
    )


def endpoint_plausible(left: str, right: str) -> bool:
    return left == right or right in GLIDE_ENDPOINT_NEIGHBORS.get(left, set())


def glide_gesture_rank_prior(index: int) -> float:
    return max(0.0, 1.0 - max(0, index) * GLIDE_CONTEXT_RANK_STEP_PENALTY)


def glide_context_score(word: str, context_scores: dict[str, Any]) -> float:
    value = context_scores.get(word, 0.0)
    if not isinstance(value, (int, float)):
        return 0.0
    return min(1.0, max(0.0, float(value)))


def choose_glide_context_replacement(case: dict[str, Any]) -> str | None:
    committed = normalize_glide_word_for_context(str(case.get("committedWord") or ""))
    next_word = normalize_glide_word_for_context(str(case.get("nextWord") or ""))
    if committed is None or next_word is None:
        return None
    if len(committed) > GLIDE_CONTEXT_MAX_RECOVERABLE_WORD_LENGTH:
        return None

    context_scores = case.get("contextScores")
    if not isinstance(context_scores, dict):
        context_scores = {}

    candidates: list[tuple[str, str]] = []
    seen: set[str] = set()
    for word in case.get("candidateWords", []):
        if not isinstance(word, str):
            continue
        normalized = normalize_glide_word_for_context(word)
        if normalized is None or normalized in seen:
            continue
        seen.add(normalized)
        candidates.append((normalized, word))
        if len(candidates) >= GLIDE_CONTEXT_MAX_CANDIDATES_TO_RESCORE:
            break

    if len(candidates) < 2 or all(normalized != committed for normalized, _ in candidates):
        return None

    current_index = next(index for index, (normalized, _) in enumerate(candidates) if normalized == committed)
    current_score = glide_gesture_rank_prior(current_index) + (
        glide_context_score(committed, context_scores) * GLIDE_CONTEXT_WEIGHT
    )
    scored_candidates: list[tuple[str, str, float]] = []
    for index, (normalized, original) in enumerate(candidates):
        if normalized == committed or not endpoints_plausible(committed, normalized):
            continue
        score = glide_gesture_rank_prior(index) + (
            glide_context_score(normalized, context_scores) * GLIDE_CONTEXT_WEIGHT
        )
        scored_candidates.append((normalized, original, score))
    if not scored_candidates:
        return None

    best_normalized, best_original, best_score = max(scored_candidates, key=lambda item: item[2])
    best_context = glide_context_score(best_normalized, context_scores)
    if best_context >= GLIDE_CONTEXT_MIN_SCORE and best_score >= current_score + GLIDE_CONTEXT_MIN_SWITCH_MARGIN:
        return best_original
    return None


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
    replacement_cases = 0
    replay_hits = 0
    rescue_cases = 0
    rescue_top4 = 0
    endpoint_cases = 0
    endpoint_hits = 0
    context_score_wins = 0
    for case in cases:
        candidates = [str(item) for item in case.get("candidateWords", []) if isinstance(item, str)]
        expected = str(case.get("expectedReplacement") or "")
        committed = str(case.get("committedWord") or "")
        tags = case.get("tags")
        if not isinstance(tags, list):
            tags = []
        context_scores = case.get("contextScores")
        replayed = choose_glide_context_replacement(case)
        expected_replay = expected or None
        if replayed == expected_replay:
            replay_hits += 1
        if GLIDE_ENDPOINT_PLAUSIBILITY_TAG in tags:
            endpoint_cases += 1
            if replayed == expected_replay:
                endpoint_hits += 1
        if expected:
            replacement_cases += 1
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
        "replacementCaseCount": replacement_cases,
        "top1ReplacementCount": top1,
        "top1ReplacementRate": rate(top1, replacement_cases),
        "top4ReplacementCount": top4,
        "top4ReplacementRate": rate(top4, replacement_cases),
        "replayHitCount": replay_hits,
        "replayHitRate": rate(replay_hits, len(cases)),
        "contextRescue": {
            "caseCount": rescue_cases,
            "top4ReplacementCount": rescue_top4,
            "top4ReplacementRate": rate(rescue_top4, rescue_cases),
            "contextScoreWinCount": context_score_wins,
            "contextScoreWinRate": rate(context_score_wins, rescue_cases),
        },
        "endpointPlausibility": {
            "caseCount": endpoint_cases,
            "replayHitCount": endpoint_hits,
            "replayHitRate": rate(endpoint_hits, endpoint_cases),
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
    if glide["replacementCaseCount"] <= 0:
        failures.append("glide replacement fixture coverage is empty")
    if glide["top4ReplacementRate"] < args.min_glide_top4_rate:
        failures.append("glide top-4 replacement coverage is below threshold")
    if glide["contextRescue"]["top4ReplacementRate"] < args.min_glide_context_rescue_top4_rate:
        failures.append("glide context-rescue top-4 replacement coverage is below threshold")
    if glide["endpointPlausibility"]["caseCount"] <= 0:
        failures.append("glide endpoint-plausibility fixture coverage is empty")
    if glide["endpointPlausibility"]["replayHitRate"] < args.min_glide_endpoint_plausibility_rate:
        failures.append("glide endpoint-plausibility replay coverage is below threshold")
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
    autocorrect = scorecard["autoCorrectConfidence"]
    glide = scorecard["glideReplay"]
    latency = scorecard["firstSuggestionLatency"]
    failures = scorecard["failures"]
    status = "PASS" if not failures else "FAIL"
    lines = [
        "# SwiftFloris Typing Quality Scorecard",
        "",
        f"Status: {status}",
        "",
        "| Area | Metric | Value |",
        "| --- | --- | ---: |",
        f"| Tap correction | Fixture cases | {trace['tapCorrection']['caseCount']} |",
        f"| Tap correction | Top-4 target rate | {trace['tapCorrection']['top4TargetRate']:.3f} |",
        f"| Next-word prediction | Fixture cases | {trace['nextWordPrediction']['caseCount']} |",
        f"| Next-word prediction | Top-4 target rate | {trace['nextWordPrediction']['top4TargetRate']:.3f} |",
        f"| Autocorrect confidence | Scorecard-selected default | {autocorrect['selectedDefaultPercent']}% |",
        f"| Glide | Fixture cases | {glide['caseCount']} |",
        f"| Glide | Replacement cases | {glide['replacementCaseCount']} |",
        f"| Glide | Top-1 replacement rate | {glide['top1ReplacementRate']:.3f} |",
        f"| Glide | Top-4 replacement rate | {glide['top4ReplacementRate']:.3f} |",
        f"| Glide | Replay hit rate | {glide['replayHitRate']:.3f} |",
        f"| Glide context rescue | Cases | {glide['contextRescue']['caseCount']} |",
        f"| Glide context rescue | Top-4 replacement rate | {glide['contextRescue']['top4ReplacementRate']:.3f} |",
        f"| Glide endpoint plausibility | Cases | {glide['endpointPlausibility']['caseCount']} |",
        f"| Glide endpoint plausibility | Replay hit rate | {glide['endpointPlausibility']['replayHitRate']:.3f} |",
    ]
    if latency.get("available"):
        lines.extend([
            f"| First suggestion latency | Median ms | {latency.get('medianMs') or 0.0:.3f} |",
            f"| First suggestion latency | p95 ms | {latency.get('p95Ms') or 0.0:.3f} |",
            f"| First suggestion latency | Runs | {latency.get('runCount') or 0} |",
        ])
    else:
        lines.append("| First suggestion latency | Benchmark | missing |")
    lines.extend([
        "",
        "## Autocorrect confidence thresholds",
        "",
        "| Threshold | Accuracy | Coverage | Precision | Score |",
        "| ---: | ---: | ---: | ---: | ---: |",
    ])
    lines.extend(
        f"| {row['percent']}% | {row['accuracy']:.3f} | {row['coverage']:.3f} | "
        f"{row['precision']:.3f} | {row['score']:.3f} |"
        for row in autocorrect["thresholds"]
    )
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
        "autoCorrectConfidence": score_autocorrect_thresholds(trace_cases),
        "glideReplay": score_glide_fixtures(glide_cases),
        "firstSuggestionLatency": score_latency(resolve(args.benchmark_dir)),
        "thresholds": {
            "maxP95SuggestionLatencyMs": args.max_p95_suggestion_latency_ms,
            "minTapCorrectionTop4Rate": args.min_tap_correction_top4_rate,
            "minGlideTop4Rate": args.min_glide_top4_rate,
            "minGlideContextRescueTop4Rate": args.min_glide_context_rescue_top4_rate,
            "minGlideEndpointPlausibilityRate": args.min_glide_endpoint_plausibility_rate,
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
