# SwiftFloris benchmark results

ROADMAP §7 Next-12.1. Macrobenchmark harness lives at
`benchmark/src/main/kotlin/dev/patrickgold/florisboard/benchmark/
KeyboardLatencyBenchmark.kt`. Run on a clocks-locked device:

```bash
# Lock device clocks (Pixel 6 / S25 Ultra example).
adb shell cmd device_config put activity_manager max_phantom_processes 2147483647
adb shell input keyevent KEYCODE_WAKEUP
# … additional clock-locking per Macrobenchmark docs:
# https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview

./gradlew :benchmark:connectedBenchmarkAndroidTest
```

Collect output from
`benchmark/build/outputs/connected_android_test_additional_output/`.

## Latency baseline — pending first device run

| Benchmark | medianFrameDurationCpuMs | medianFrameOverrunMs | TraceSection p50 / p95 |
|---|---|---|---|
| `imeFirstRender` (cold IME view inflation) | _pending_ | _pending_ | `swiftfloris.ime.firstRender`: _pending_ |
| `suggestionStripRecomposition` (warm-start with typed text) | _pending_ | _pending_ | `swiftfloris.nlp.suggest` + `swiftfloris.smartbar.candidates.recompose`: _pending_ |
| `dictionaryColdLoad` (SCOWL 117k load) | _pending_ | _pending_ | `swiftfloris.dict.load` + `swiftfloris.nlp.symspell.build`: _pending_ |
| `themeSwitch` (Snygg stylesheet swap) | _pending_ | _pending_ | `swiftfloris.theme.switch`: _pending_ |

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
| `swiftfloris.theme.switch` | Theme engine | `ThemeManager.activate` swap |

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

Each baseline lives at `docs/benchmark-results/baseline-YYYY-MM-DD.json`
so subsequent runs can compare against the recorded numbers. Format:
raw `BenchmarkResult` JSON as emitted by AndroidX Macrobenchmark.

| Date | Device | Build | Result file |
|---|---|---|---|
| _pending_ | _pending_ | _pending_ | _pending_ |

## How to read a regression

A median frame increase **> 8 %** vs the immediately-preceding
baseline on the same device + build configuration is the
regression threshold. The CI baseline keeps both numbers + the
% delta + a Macrobenchmark output link so triage is one click.

## Definition of done

A v1.X.Y release that touches the IME hot path includes:

1. A fresh benchmark run on a clocks-locked device.
2. The new JSON committed to `docs/benchmark-results/`.
3. The four-benchmark table above updated.
4. A regression-or-improvement line in the release notes.

When the harness reports an improvement > 5 %, claim it in
release notes. When it reports a regression > 8 %, the release is
held until the regression is investigated.
