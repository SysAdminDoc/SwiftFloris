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

package dev.patrickgold.florisboard.ime.handwriting

/**
 * ROADMAP §7 Next-4.2 — stroke-recogniser facade.
 *
 * SwiftFloris' `:app` module deliberately does *not* take a direct
 * dependency on Google ML Kit Digital Ink. ML Kit's
 * `RemoteModelManager.download(...)` requires `INTERNET` at runtime,
 * which would break the §1 no-network promise that this IME's F-Droid
 * privacy review depends on. Instead, this facade defines the
 * stroke-recogniser contract; an **out-of-tree signed addon APK**
 * could provide the actual model binding. No handwriting recognizer
 * addon currently ships and no production path activates this registry,
 * so the facade remains contract-only.
 *
 * Until an addon registers a real implementation, [Default] is used:
 * it accepts strokes but returns `NoRecognition` so the IME falls
 * back to the touch-keyboard path. The Settings → Handwriting screen
 * already discloses this state via the `Next-4.3` user-toggle copy.
 *
 * Bridge surface (kept narrow so the addon binding doesn't grow into
 * `:app`):
 *  - [recognize] converts a stroke list into ranked text candidates.
 *  - [isReady] returns whether a real recogniser is bound for [locale].
 *  - [supportedLocales] lists the locales the bound recogniser handles.
 */
interface StrokeRecognizer {
    fun recognize(strokes: List<Stroke>, locale: String): StrokeRecognitionResult
    fun isReady(locale: String): Boolean
    val supportedLocales: Set<String>

    /**
     * Default no-op implementation. Returns [StrokeRecognitionResult.NoRecognition]
     * for every input so the IME callsite always has a valid value to
     * forward to the touch fallback path.
     */
    object Default : StrokeRecognizer {
        override fun recognize(strokes: List<Stroke>, locale: String): StrokeRecognitionResult =
            StrokeRecognitionResult.NoRecognition

        override fun isReady(locale: String): Boolean = false

        override val supportedLocales: Set<String> = emptySet()
    }
}

/**
 * Recogniser output. [Candidates] carries the ranked best matches with
 * a confidence in `[0, 1]`. [NoRecognition] is the recogniser's "I don't
 * know" answer — distinct from `Candidates(emptyList())` because the
 * IME treats them differently (no-recognition keeps the strokes on
 * screen for a retry; empty candidates discards them).
 */
sealed class StrokeRecognitionResult {
    object NoRecognition : StrokeRecognitionResult()
    data class Candidates(val candidates: List<StrokeCandidate>) : StrokeRecognitionResult()
}

data class StrokeCandidate(
    val text: String,
    val confidence: Float,
) {
    init {
        require(confidence in 0f..1f) { "confidence must be in [0, 1]; was $confidence" }
        require(text.isNotEmpty()) { "candidate text must not be empty" }
    }
}

/**
 * Process-wide registry mediating between the IME and a potential bound
 * recogniser. No production binding ships, so [active] stays
 * [StrokeRecognizer.Default].
 */
object StrokeRecognizerRegistry {

    @Volatile
    private var current: StrokeRecognizer = StrokeRecognizer.Default

    val active: StrokeRecognizer
        get() = current

    fun setActive(recognizer: StrokeRecognizer) {
        current = recognizer
    }

    /** Test hook — restores the default no-op recogniser. */
    fun reset() {
        current = StrokeRecognizer.Default
    }
}
