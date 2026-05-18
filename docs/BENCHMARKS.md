# SwiftFloris benchmark results

ROADMAP §7 Next-12.1. Macrobenchmark harness lives at
`benchmark/src/main/kotlin/dev/patrickgold/florisboard/benchmark/
KeyboardLatencyBenchmark.kt`. The adb harness scripts live under `tools/`.
Run on a clocks-locked device:

```bash
# Lock device clocks (Pixel 6 / S25 Ultra example).
adb shell cmd device_config put activity_manager max_phantom_processes 2147483647
adb shell input keyevent KEYCODE_WAKEUP
# … additional clock-locking per Macrobenchmark docs:
# https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview

./gradlew :app:assembleBenchmark :benchmark:assembleBenchmark

# Repeatable adb baseline for IME first render.
pwsh -NoProfile -ExecutionPolicy Bypass -File tools/benchmark-ime-first-render.ps1 -Iterations 5

# Repeatable adb baseline for cold first suggestion provider latency.
pwsh -NoProfile -ExecutionPolicy Bypass -File tools/benchmark-ime-suggestion-latency.ps1 -Iterations 5

# Repeatable adb baseline for dictionary cold load, preload, and lazy indexes.
pwsh -NoProfile -ExecutionPolicy Bypass -File tools/benchmark-ime-dictionary-load.ps1 -Iterations 5

# Repeatable adb baseline for candidate-row recomposition during warm typing.
pwsh -NoProfile -ExecutionPolicy Bypass -File tools/benchmark-ime-candidate-row.ps1 -Iterations 5

# Repeatable adb baseline for theme switching while the IME is visible.
pwsh -NoProfile -ExecutionPolicy Bypass -File tools/benchmark-ime-theme-switch.ps1 -Iterations 5

# AndroidX Macrobenchmark trace/frame runs.
./gradlew :benchmark:connectedBenchmarkAndroidTest
```

Collect output from
`benchmark/build/outputs/connected_android_test_additional_output/` for
AndroidX Macrobenchmark runs. The adb harness scripts write JSON to
`docs/benchmark-results/`.

## Latency baseline

| Benchmark | Device / iterations | Launch median | TraceSection / log median | Evidence |
|---|---|---|---|---|
| `imeFirstRender` (cold IME view inflation) | Samsung SM-S938B / Android 16, 5 runs | `am start -W`: `TotalTime` 31.0 ms, `WaitTime` 34.0 ms | `SwiftFlorisPerf`: `swiftfloris.ime.firstRenderMs` 18.335469 ms | [`baseline-2026-05-18-ime-first-render.json`](benchmark-results/baseline-2026-05-18-ime-first-render.json) |
| `firstSuggestionLatency` (cold provider-direct `teh`) | Samsung SM-S938B / Android 16, 5 runs | `am start -W`: activity launch recorded per run | `SwiftFlorisPerf`: `swiftfloris.nlp.firstSuggestionMs` 1878.616249 ms, 8 candidates | [`baseline-2026-05-18-ime-suggestion-latency.json`](benchmark-results/baseline-2026-05-18-ime-suggestion-latency.json) |
| `dictionaryColdLoad` (SCOWL load + preload + SymSpell indexes) | Samsung SM-S938B / Android 16, 5 runs | `am start -W`: activity launch recorded per run | `SwiftFlorisPerf`: `swiftfloris.dict.loadMs` 757.353333 ms; `swiftfloris.dict.preloadMs` 772.080625 ms; SymSpell d1 500.230156 ms / d2 532.298281 ms; post-preload spell 1030.179896 ms | [`baseline-2026-05-18-ime-dictionary-load.json`](benchmark-results/baseline-2026-05-18-ime-dictionary-load.json) |
| `candidateRowRecomposition` (warm typing phrase) | Samsung SM-S938B / Android 16, 5 runs | `am start -W`: activity launch recorded per run | `SwiftFlorisPerf`: 9.0 recompositions/run; median body 0.326563 ms; median max 0.770365 ms; median total 4.069529 ms; paired median `swiftfloris.nlp.suggestMs` 0.339896 ms | [`baseline-2026-05-18-ime-candidate-row.json`](benchmark-results/baseline-2026-05-18-ime-candidate-row.json) |
| `themeSwitch` (Snygg stylesheet swap) | Samsung SM-S938B / Android 16, 5 runs | `am start -W`: activity launch recorded per run | `SwiftFlorisPerf`: 5.0 switches/run; median body 18.541197 ms; median max 19.587708 ms; median total 57.505571 ms; cold-step median 19.221354 ms; warm cached-step median 0.2808075 ms; 0 load failures | [`baseline-2026-05-18-ime-theme-switch.json`](benchmark-results/baseline-2026-05-18-ime-theme-switch.json) |

## Glide trace benchmark — pending first corpus run

ROADMAP N1.4 now has the JVM-side replay and reporting pieces:
`SwipeTraceImporter` loads JSON Array / JSON Lines traces,
`SwipeTraceReplay.toPointerData(...)` maps normalized samples into
`GlideTypingGesture.Detector.PointerData`, and
`SwipeTraceBenchmark.evaluate(...)` computes top-1 / top-3 / top-N
accuracy, failures, average predictor latency, and capped miss samples.

The remaining evidence step needs the MIT-licensed FUTO swipe corpus
downloaded outside the repo and a device or host runner that wires the
records into `StatisticalGlideTypingClassifier`.

| Corpus | Engine | Records | Top-1 | Top-3 | Top-N | Avg latency | Notes |
|---|---|---:|---:|---:|---:|---:|---|
| FUTO MIT swipe corpus | `StatisticalGlideTypingClassifier` | _pending_ | _pending_ | _pending_ | _pending_ | _pending_ | Requires corpus download + replay runner |
| FUTO nightly reference model | _external reference_ | _pending_ | _pending_ | _pending_ | _pending_ | _pending_ | Record from published run only; do not ingest FUTO app code |

## Trace-section naming convention

Every production hot path that we benchmark wraps itself with
`androidx.tracing.Trace.beginSection("swiftfloris.<subsystem>.<action>")`.
Current sections:

| Section | Subsystem | When entered |
|---|---|---|
| `swiftfloris.ime.firstRender` | IME bootstrap | `FlorisImeService.onCreateInputView` start → first frame |
| `swiftfloris.dict.load` | NLP dictionary | `LatinDictionaryStore.dictionaryForLanguage` cold path |
| `swiftfloris.nlp.symspell.build` | NLP correction index | `LatinDictionarySnapshot.symSpellIndex` lazy-init |
| `swiftfloris.nlp.suggest` | NLP suggestion pipeline | `NlpManager.suggest` start → IO-bound completion |
| `swiftfloris.smartbar.candidates.recompose` | Smartbar UI | Candidates row Compose re-emit |
| `swiftfloris.theme.switch` | Theme engine | `ThemeManager.updateActiveTheme` swap |

Add a new section by wrapping its hot-path call site:

```kotlin
import androidx.tracing.Trace
Trace.beginSection("swiftfloris.<subsystem>.<action>")
try {
    // … work …
} finally {
    Trace.endSection()
}
```

## Historical baselines

Each baseline lives at `docs/benchmark-results/baseline-YYYY-MM-DD*.json`
so subsequent runs can compare against the recorded numbers. Format:
raw `BenchmarkResult` JSON as emitted by AndroidX Macrobenchmark, or the
script-emitted JSON for adb-only baselines.

| Date | Device | Build | Result file |
|---|---|---|---|
| 2026-05-18 | Samsung SM-S938B / Android 16 (SDK 36) | v1.8.163 benchmark APK (`dev.patrickgold.florisboard.bench`) | [`baseline-2026-05-18-ime-theme-switch.json`](benchmark-results/baseline-2026-05-18-ime-theme-switch.json) |
| 2026-05-18 | Samsung SM-S938B / Android 16 (SDK 36) | v1.8.162 benchmark APK (`dev.patrickgold.florisboard.bench`) | [`baseline-2026-05-18-ime-candidate-row.json`](benchmark-results/baseline-2026-05-18-ime-candidate-row.json) |
| 2026-05-18 | Samsung SM-S938B / Android 16 (SDK 36) | v1.8.161 benchmark APK (`dev.patrickgold.florisboard.bench`) | [`baseline-2026-05-18-ime-dictionary-load.json`](benchmark-results/baseline-2026-05-18-ime-dictionary-load.json) |
| 2026-05-18 | Samsung SM-S938B / Android 16 (SDK 36) | v1.8.160 benchmark APK (`dev.patrickgold.florisboard.bench`) | [`baseline-2026-05-18-ime-suggestion-latency.json`](benchmark-results/baseline-2026-05-18-ime-suggestion-latency.json) |
| 2026-05-18 | Samsung SM-S938B / Android 16 (SDK 36) | v1.8.159 benchmark APK (`dev.patrickgold.florisboard.bench`) | [`baseline-2026-05-18-ime-first-render.json`](benchmark-results/baseline-2026-05-18-ime-first-render.json) |

## How to read a regression

A median frame increase **> 8 %** vs the immediately-preceding
baseline on the same device + build configuration is the
regression threshold. The CI baseline keeps both numbers + the
% delta + a Macrobenchmark output link so triage is one click.

## Definition of done

A v1.X.Y release that touches the IME hot path includes:

1. A fresh benchmark run on a clocks-locked device.
2. The new JSON committed to `docs/benchmark-results/`.
3. The latency baseline table above updated.
4. A regression-or-improvement line in the release notes.

When the harness reports an improvement > 5 %, claim it in
release notes. When it reports a regression > 8 %, the release is
held until the regression is investigated.
