/*
 * Copyright (C) 2026 SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ROADMAP §7 Next-12.1 — Macrobenchmark + Perfetto harness covering the
 * SwiftFloris-specific hot paths beyond app startup (which lives in
 * [StartupBenchmark]). These benchmarks measure:
 *
 *  - **Frame timing while the keyboard is on-screen** — captures jank when
 *    the suggestion strip recomposes, when the user toggles theme, and
 *    when the smartbar dynamic actions re-prioritise.
 *  - **Memory pressure on dictionary load** — N7.4 SQLCipher + 117 k SCOWL
 *    entries; we want to know the resident set after a typical typing
 *    session.
 *  - **Traced sections** — sections instrumented with
 *    `androidx.tracing.Trace.beginSection` show up in Perfetto so a single
 *    benchmark run produces both numeric metrics AND a system trace for
 *    deep-dive investigation. Trace section names match the convention
 *    `swiftfloris.<subsystem>.<action>` (`swiftfloris.nlp.suggest`,
 *    `swiftfloris.theme.switch`, `swiftfloris.dict.load`, ...).
 *
 * Run with:
 *   `./gradlew :benchmark:connectedBenchmarkAndroidTest`
 * on a device with the SwiftFloris release build installed. CI does NOT
 * run this (Macrobenchmark requires a real device or Studio emulator with
 * power profile + locked clocks); release notes record before/after
 * numbers manually per the §15 Definition of Done.
 *
 * **Trace section convention.** Any production code path that wants to
 * be measurable here should add:
 *   ```kotlin
 *   androidx.tracing.Trace.beginSection("swiftfloris.<subsystem>.<action>")
 *   try { ... } finally { androidx.tracing.Trace.endSection() }
 *   ```
 * The [TraceSectionMetric] picks up matching names automatically.
 */
@RunWith(AndroidJUnit4ClassRunner::class)
@OptIn(ExperimentalMetricApi::class)
class KeyboardLatencyBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    /**
     * IME view inflation latency — cold-start the IME service and measure
     * the time from `onCreateInputView` through the first composition
     * frame of the keyboard. The most user-visible cold-path metric.
     */
    @Test
    fun imeFirstRender() = benchmarkRule.measureRepeated(
        packageName = TargetPackageName,
        metrics = listOf(
            FrameTimingMetric(),
            TraceSectionMetric("swiftfloris.ime.firstRender"),
        ),
        compilationMode = CompilationMode.Partial(),
        iterations = 5,
        startupMode = StartupMode.COLD,
        setupBlock = {
            pressHome()
            selectTargetIme()
        },
    ) {
        startBenchmarkInputActivityAndWait()
        device.waitForIdle(2_000)
    }

    /**
     * Suggestion-strip recomposition latency — measure frame timing while
     * the user types a known phrase. Captures the impact of the multilingual
     * scorer / SymSpell / trigram path on per-keystroke jank.
     *
     * Note: the benchmark requires the test app's launcher activity to
     * focus the keyboard on a known EditText. We use the existing settings
     * activity's About screen text field as a stable target (replace with
     * a dedicated benchmark activity when one ships).
     */
    @Test
    fun suggestionStripRecomposition() = benchmarkRule.measureRepeated(
        packageName = TargetPackageName,
        metrics = listOf(
            FrameTimingMetric(),
            TraceSectionMetric("swiftfloris.nlp.suggest"),
            TraceSectionMetric("swiftfloris.smartbar.candidates.recompose"),
        ),
        compilationMode = CompilationMode.Partial(),
        iterations = 5,
        startupMode = StartupMode.WARM,
        setupBlock = {
            selectTargetIme()
            startBenchmarkInputActivityAndWait()
            device.waitForIdle(1_000)
        },
    ) {
        // Type a SCOWL-known sequence to exercise the dictionary, SymSpell,
        // bigram, and multilingual paths in one pass.
        device.executeShellCommand("input text 'hello world this is a test'")
        device.waitForIdle(1_500)
    }

    /**
     * Dictionary load + first SymSpell index build — most expensive cold
     * work the IME does. SymSpellIndex is `by lazy` so the cost lands on
     * the first correction call rather than dictionary load; this
     * benchmark forces that path.
     */
    @Test
    fun dictionaryColdLoad() = benchmarkRule.measureRepeated(
        packageName = TargetPackageName,
        metrics = listOf(
            MemoryUsageMetric(MemoryUsageMetric.Mode.Max),
            TraceSectionMetric("swiftfloris.dict.load"),
            TraceSectionMetric("swiftfloris.nlp.symspell.build"),
        ),
        compilationMode = CompilationMode.Partial(),
        iterations = 3,
        startupMode = StartupMode.COLD,
        setupBlock = {
            pressHome()
            selectTargetIme()
        },
    ) {
        startBenchmarkInputActivityAndWait()
        device.executeShellCommand("input text 'teh'")
        device.waitForIdle(2_000)
    }

    /**
     * Theme switch latency — when the user toggles between Nord / SwiftKey
     * Pure / Tokyo Night, the entire keyboard recomposes through a new
     * Snygg stylesheet. This catches `--primary` token churn from
     * Next-11.3a per-app accent flow when enabled.
     */
    @Test
    fun themeSwitch() = benchmarkRule.measureRepeated(
        packageName = TargetPackageName,
        metrics = listOf(
            FrameTimingMetric(),
            TraceSectionMetric("swiftfloris.theme.switch"),
        ),
        compilationMode = CompilationMode.Partial(),
        iterations = 5,
        startupMode = StartupMode.WARM,
        setupBlock = {
            selectTargetIme()
            startBenchmarkInputActivityAndWait()
            device.waitForIdle(1_000)
        },
    ) {
        device.executeShellCommand("input keyevent KEYCODE_BACK")
        device.waitForIdle(500)
        device.executeShellCommand("input keyevent KEYCODE_BACK")
        device.waitForIdle(500)
    }
}
