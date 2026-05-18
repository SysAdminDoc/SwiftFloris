# SwiftFloris v1.8.161

Date: 2026-05-18

## Dictionary Load and Preload Baseline

This release completes the next Performance Instrumentation item by measuring
Latin dictionary cold load, preload, and lazy SymSpell index construction on
the same SM-S938B / Android 16 device used for the first-render and
first-suggestion baselines.

### Changed

- Added benchmark-build-only `SwiftFlorisPerf` markers around
  `LatinDictionaryStore.loadSpecificDictionary` and both lazy SymSpell index
  builders.
- Added `BenchmarkDictionaryActivity` to the benchmark variant. It preloads
  `Subtype.DEFAULT`, then probes invalid token `zzzxqq` so the spelling path
  forces distance-1 and distance-2 SymSpell index construction.
- Added `tools/benchmark-ime-dictionary-load.ps1`, which installs the
  benchmark APK, launches the dictionary benchmark activity, parses logcat,
  and writes repeatable JSON to `docs/benchmark-results/`.

### Baseline

Samsung SM-S938B / Android 16 (SDK 36), five cold iterations for `zzzxqq`:

- Median `swiftfloris.dict.loadMs`: 757.353333 ms for 520,837 English entries.
- Median `swiftfloris.dict.preloadMs`: 772.080625 ms.
- Median SymSpell distance-1 build: 500.230156 ms for 94,934 correction words.
- Median SymSpell distance-2 build: 532.298281 ms for 10,534 correction words.
- Median post-preload spell path: 1030.179896 ms.
- Median post-preload suggestion path: 0.421719 ms.

Evidence:
`docs/benchmark-results/baseline-2026-05-18-ime-dictionary-load.json`.

This splits the dictionary/startup cost out from the v1.8.160 first-suggestion
baseline, which intentionally included cold provider startup.

### Tests

- `git diff --check` passed.
- `.\gradlew.bat :app:assembleBenchmark :benchmark:assembleBenchmark` passed.
- `pwsh -NoProfile -ExecutionPolicy Bypass -File .\tools\benchmark-ime-dictionary-load.ps1 -Iterations 5` passed on device `R5CY34G070L`.
- `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` passed.

### Definition of Done

- Version bumped to `1.8.161` / code `1961`.
- `README.md`, `ROADMAP.md`, `PROJECT_CONTEXT.md`, `AGENTS.md`,
  `IMPROVEMENT_PLAN.md`, and `docs/BENCHMARKS.md` updated.
