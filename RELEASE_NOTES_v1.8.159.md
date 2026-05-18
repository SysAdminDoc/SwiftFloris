# SwiftFloris v1.8.159

Date: 2026-05-18

## IME First-Render Benchmark Baseline

This release starts Performance Instrumentation Workstream 7 by reactivating
the benchmark module and committing the first repeatable cold IME first-render
baseline.

### Changed

- Re-enabled `:benchmark` in `settings.gradle.kts` and updated the benchmark
  module for the AGP 9 / Gradle 9 build.
- Retargeted benchmark sources at the `.bench` app id and added shared device
  helpers for selecting the benchmark IME and launching benchmark activities.
- Added a benchmark-only `BenchmarkInputActivity` so adb and Macrobenchmark
  runs can show the IME without touching production UI.
- Added a benchmark-build-only `SwiftFlorisPerf` first-render log marker in
  `FlorisImeService.onCreateInputView`.
- Added `tools/benchmark-ime-first-render.ps1`, which installs the benchmark
  APK, selects the benchmark IME, records five adb runs, restores the previous
  input method, and writes JSON to `docs/benchmark-results/`.

### Baseline

Samsung SM-S938B / Android 16 (SDK 36), five iterations:

- `am start -W` median `TotalTime`: 31.0 ms.
- `am start -W` median `WaitTime`: 34.0 ms.
- `SwiftFlorisPerf` median `swiftfloris.ime.firstRenderMs`: 18.335469 ms.

Evidence: `docs/benchmark-results/baseline-2026-05-18-ime-first-render.json`.

### Tests

- `git diff --check` passed.
- `.\gradlew.bat :app:assembleBenchmark :benchmark:assembleBenchmark` passed.
- `pwsh -NoProfile -ExecutionPolicy Bypass -File .\tools\benchmark-ime-first-render.ps1 -Iterations 5` passed on device `R5CY34G070L` and restored the previous IME.
- `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` passed.

### Definition of Done

- Version bumped to `1.8.159` / code `1959`.
- `README.md`, `ROADMAP.md`, `PROJECT_CONTEXT.md`, `ARCHITECTURE.md`,
  `AGENTS.md`, `IMPROVEMENT_PLAN.md`, and `docs/BENCHMARKS.md` updated.
