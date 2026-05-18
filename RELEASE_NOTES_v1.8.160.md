# SwiftFloris v1.8.160

Date: 2026-05-18

## First Suggestion Latency Baseline

This release completes the next Performance Instrumentation item by measuring
cold first-suggestion provider latency on the same SM-S938B / Android 16
device used for the first-render baseline.

### Changed

- Added a benchmark-build-only `SwiftFlorisPerf` marker around
  `LatinLanguageProvider.suggest`, with current-word length and candidate
  count in each log line.
- Added `BenchmarkSuggestionActivity` to the benchmark variant. It invokes
  the Latin suggestion provider against a real `EditorContent` snapshot for
  `teh`, avoiding adb key-event ambiguity while still measuring the same
  provider path.
- Added `tools/benchmark-ime-suggestion-latency.ps1`, which installs the
  benchmark APK, launches the suggestion benchmark activity, parses logcat,
  and writes repeatable JSON to `docs/benchmark-results/`.

### Baseline

Samsung SM-S938B / Android 16 (SDK 36), five cold provider-direct iterations
for `teh`:

- `SwiftFlorisPerf` median `swiftfloris.nlp.firstSuggestionMs`: 1878.616249 ms.
- Median candidate count: 8.

Evidence:
`docs/benchmark-results/baseline-2026-05-18-ime-suggestion-latency.json`.

This number intentionally includes cold provider and dictionary startup cost.
The separate dictionary-load Workstream 7 item remains open so that cost can
be split out in a later release.

### Tests

- `git diff --check` passed.
- `.\gradlew.bat :app:assembleBenchmark :benchmark:assembleBenchmark` passed.
- `pwsh -NoProfile -ExecutionPolicy Bypass -File .\tools\benchmark-ime-suggestion-latency.ps1 -Iterations 5` passed on device `R5CY34G070L`.
- `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` passed.

### Definition of Done

- Version bumped to `1.8.160` / code `1960`.
- `README.md`, `ROADMAP.md`, `PROJECT_CONTEXT.md`, `AGENTS.md`,
  `IMPROVEMENT_PLAN.md`, and `docs/BENCHMARKS.md` updated.
