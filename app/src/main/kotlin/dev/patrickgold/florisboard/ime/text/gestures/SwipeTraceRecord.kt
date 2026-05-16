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

package dev.patrickgold.florisboard.ime.text.gestures

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * ROADMAP §6 N1.4 — labelled swipe trace record for the replay / glide benchmark harness.
 *
 * FUTO publishes a >1M-sample swipe-trace dataset on Hugging Face under MIT (see HF-FUTO-SWIPE). The data
 * card uses one JSON / TSV record per trace, each carrying the layout name + expected word + a list of
 * timestamped (x, y) samples. Importing those traces into the SwiftFloris replay harness lets the
 * existing `StatisticalGlideTypingClassifier` (and any future neural glide model) be benchmarked against
 * the same evaluation set as FUTO's nightly model, *without* ingesting FUTO's Source-First app code.
 *
 * The schema here intentionally:
 * - Stays decoupled from the production `GlideTypingGesture.Detector.PointerData` type so callers can
 *   convert in either direction without bringing test infrastructure into the production class graph.
 * - Is `@Serializable` so the importer can read JSON Lines (one [SwipeTraceRecord] per line) directly.
 * - Carries the layout reference and source-data attribution string so a benchmark report can cite which
 *   trace set produced which numbers.
 *
 * Coordinates use the *normalized* `[0.0, 1.0]` range over the keyboard rectangle. This is the format
 * FUTO publishes and what the replay harness expects so that the same trace can be replayed against
 * keyboard renders of different physical sizes.
 */
@Serializable
data class SwipeTraceRecord(
    /** Expected word — what the user intended to type. */
    @SerialName("word") val word: String,
    /** Layout identifier, e.g. "qwerty-en", "azerty-fr". */
    @SerialName("layout") val layout: String,
    /** Sample list in trace order. */
    @SerialName("samples") val samples: List<SwipeTraceSample>,
    /** BCP-47 language tag (e.g. "en", "de"). Optional but populated by the FUTO data card. */
    @SerialName("language_tag") val languageTag: String? = null,
    /** Free-text source / provenance attribution. */
    @SerialName("source") val source: String? = null,
) {
    init {
        require(word.isNotBlank()) { "word must not be blank" }
        require(layout.isNotBlank()) { "layout must not be blank" }
        require(samples.isNotEmpty()) { "samples must not be empty" }
    }

    /** Total dwell time of the trace in milliseconds (last sample t - first sample t). */
    val durationMillis: Long
        get() = if (samples.size < 2) 0L else samples.last().t - samples.first().t

    /** Sample count of the trace. */
    val sampleCount: Int
        get() = samples.size
}

/**
 * One trace sample: normalized (x, y) on `[0.0, 1.0]` plus relative timestamp in milliseconds from the
 * start of the trace.
 */
@Serializable
data class SwipeTraceSample(
    @SerialName("x") val x: Float,
    @SerialName("y") val y: Float,
    @SerialName("t") val t: Long,
) {
    init {
        require(x in 0f..1f) { "x must be in [0, 1]; was $x" }
        require(y in 0f..1f) { "y must be in [0, 1]; was $y" }
        require(t >= 0L) { "t must be non-negative; was $t" }
    }
}
