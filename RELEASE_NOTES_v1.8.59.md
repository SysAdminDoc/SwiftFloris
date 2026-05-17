# SwiftFloris v1.8.59 — 2026-05-17

Phase D3 — Typing-stats accuracy-delta number.

## Why ship this now

Phase D3 of `SWIFTKEY_PARITY_ROADMAP_2026-05-17.md` closes the
small "personalization stats" gap called out in the SwiftKey parity
audit. SwiftKey exposes an accuracy-improvement style number; this
slice keeps the same local-only spirit by computing a current-week
versus previous-week accepted-correction delta from the existing
`CorrectionOutcomePriors` store.

No new permissions, dependencies, network surfaces, or background
jobs are added.

## What changed

### Weekly correction counters

`CorrectionOutcomePriors` now keeps a bounded weekly metadata ledger
alongside its existing typed/corrected pair priors:

- accepted corrections increment the current week's accepted counter;
- rejected corrections increment the current week's rejected counter
  for future diagnostics;
- weekly rows persist in the same TSV file with `#week` metadata
  records, while old five-column pair rows remain backwards-compatible;
- reset paths clear both pair priors and weekly stats.

The public internal read surface is:

```kotlin
CorrectionOutcomePriors.accuracyDelta(): CorrectionAccuracyDelta
```

It reports current-week accepted corrections, previous-week accepted
corrections, percentage change when a previous-week baseline exists,
and a trend enum (`NO_BASELINE`, `FEWER`, `MORE`, `UNCHANGED`).

### Typing Stats UI

Settings → Typing → Typing stats now includes:

> Accepted corrections this week

The summary reports:

- no data yet;
- this week's count when there is no previous-week baseline;
- "X% fewer than last week (current vs previous)";
- "X% more than last week (current vs previous)";
- unchanged count when both weeks match.

This is intentionally phrased as accepted corrections, not a universal
"typing accuracy" claim. It is a local proxy based on corrections the
user accepted from the keyboard.

## Tests

Added `CorrectionOutcomePriorsTest` coverage for:

- week-over-week accepted-correction comparison;
- percentage calculation;
- `NO_BASELINE` before a previous-week accepted-correction baseline
  exists;
- unchanged trend when both weeks match.

## Versioning

- `gradle.properties`: `projectVersionCode=1859`,
  `projectVersionName=1.8.59`.

## Verification

Per-file syntactic review plus `git diff --check`.

This VM still has no JDK / Android SDK on the path, so run before
merge on the main Android build host:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Manual QA:

1. Use the keyboard enough to accept several autocorrections.
2. Open Settings → Typing → Typing stats.
3. Confirm the new "Accepted corrections this week" row appears.
4. After a week boundary with accepted corrections in both buckets,
   confirm the row reports fewer / more / unchanged versus the prior
   week.

## What's next

The remaining unblocked SwiftKey-parity work is now:

- B1 — sentence-position priors expansion;
- B2 — quick-prediction-insert tuning;
- C1 — split-keyboard renderer wire-up;
- C3 — High-Contrast AAA theme + animated theme;
- D1 — calendar quick-insert.

B5 remains blocked on human-captured local `swiftkey_trace.jsonl`
fixtures; Phase E remains blocked on the L1 LiteRT-LM addon bring-up.
