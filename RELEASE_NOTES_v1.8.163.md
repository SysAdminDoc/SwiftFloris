# SwiftFloris v1.8.163

Date: 2026-05-18

## Theme-Switch Benchmark Baseline

This release completes the next Performance Instrumentation item by measuring
theme switching while the benchmark IME is visible on the same SM-S938B /
Android 16 device used for the previous latency baselines.

### Changed

- Added benchmark-build-only direct switch timing in `ThemeManager`, including
  the active theme name, source marker, load-failure flag, and cached-theme
  count.
- Added `BenchmarkThemeSwitchActivity`, which focuses an input field, waits for
  the IME to render, and directly switches across SwiftKey Pure Light, M3E Nord
  Dark, M3E SwiftKey Pure Dark, then cached repeats.
- Added `tools/benchmark-ime-theme-switch.ps1`, which installs the benchmark
  APK, temporarily selects SwiftFloris, parses direct switch and step markers,
  writes JSON to `docs/benchmark-results/`, and restores the previous IME.

### Baseline

Samsung SM-S938B / Android 16 (SDK 36), five theme-switch iterations:

- Median direct theme switches per run: 5.0.
- Median `swiftfloris.theme.switchMs` body: 18.541197 ms.
- Median max `swiftfloris.theme.switchMs` body: 19.587708 ms.
- Median total direct switch body per run: 57.505571 ms.
- Median cold benchmark step: 19.221354 ms.
- Median warm cached benchmark step: 0.2808075 ms.
- Median load failures: 0.0.

Evidence:
`docs/benchmark-results/baseline-2026-05-18-ime-theme-switch.json`.

### Tests

- `.\gradlew.bat :app:assembleBenchmark :benchmark:assembleBenchmark` passed.
- `pwsh -NoProfile -ExecutionPolicy Bypass -File .\tools\benchmark-ime-theme-switch.ps1 -Iterations 5` passed on device `R5CY34G070L`.
- `adb shell settings get secure default_input_method` returned `com.touchtype.swiftkey/com.touchtype.KeyboardService` after the benchmark restored the previous IME.
- `git diff --check` passed.
- `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` passed.

### Definition of Done

- Version bumped to `1.8.163` / code `1963`.
- `README.md`, `ROADMAP.md`, `PROJECT_CONTEXT.md`, `ARCHITECTURE.md`,
  `AGENTS.md`, `IMPROVEMENT_PLAN.md`, and `docs/BENCHMARKS.md` updated.
