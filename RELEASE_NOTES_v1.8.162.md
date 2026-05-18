# SwiftFloris v1.8.162

Date: 2026-05-18

## Candidate Row Recomposition Baseline

This release completes the next Performance Instrumentation item by measuring
candidate-row recomposition during a warm typing phrase on the same SM-S938B /
Android 16 device used for the first-render, first-suggestion, and dictionary
baselines.

### Changed

- Added a benchmark-build-only `SwiftFlorisPerf` marker in `CandidatesRow`
  that records recomposition body duration, candidate count, and display mode.
- Added `tools/benchmark-ime-candidate-row.ps1`, which installs the benchmark
  APK, selects SwiftFloris temporarily, opens `BenchmarkInputActivity`, clears
  startup log noise, types `hello world this is a test`, parses candidate-row
  and NLP log markers, writes JSON to `docs/benchmark-results/`, and restores
  the previous IME.

### Baseline

Samsung SM-S938B / Android 16 (SDK 36), five warm typing iterations:

- Median candidate-row recompositions per run: 9.0.
- Median candidate-row recomposition body: 0.326563 ms.
- Median max candidate-row recomposition body: 0.770365 ms.
- Median total candidate-row recomposition body per run: 4.069529 ms.
- Median paired `swiftfloris.nlp.suggestMs`: 0.339896 ms.
- Median paired max `swiftfloris.nlp.suggestMs`: 150.826823 ms.

Evidence:
`docs/benchmark-results/baseline-2026-05-18-ime-candidate-row.json`.

The candidate row itself is not the observed hotspot in this run; the larger
spikes come from paired NLP work, including lazy correction-index work in some
iterations.

### Tests

- `git diff --check` passed.
- `.\gradlew.bat :app:assembleBenchmark :benchmark:assembleBenchmark` passed.
- `pwsh -NoProfile -ExecutionPolicy Bypass -File .\tools\benchmark-ime-candidate-row.ps1 -Iterations 5` passed on device `R5CY34G070L`.
- `adb shell settings get secure default_input_method` returned `com.touchtype.swiftkey/com.touchtype.KeyboardService` after the benchmark restored the previous IME.
- `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` passed.

### Definition of Done

- Version bumped to `1.8.162` / code `1962`.
- `README.md`, `ROADMAP.md`, `PROJECT_CONTEXT.md`, `AGENTS.md`,
  `IMPROVEMENT_PLAN.md`, and `docs/BENCHMARKS.md` updated.
