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

package dev.patrickgold.florisboard.ime.nlp

/**
 * docs/archive/SWIFTKEY_PARITY_ROADMAP_2026-05-17 §B4 — geometric-decay weighted
 * blend of trailing-word language evidence. Pulled out of
 * [NlpManager.candidateSignals] so the math has a focused JVM-test
 * surface independent of Android plumbing.
 *
 * Given a list of trailing context words (chronological order — the
 * most-recent word at the END) and a `freqLookup` callback that
 * answers "what's the dictionary frequency of this word in this
 * locale", returns the geometric-decay-weighted average frequency.
 *
 * The most-recent word weighs 1.0 and each word further back is
 * scaled by [decay] (default 0.7). Empty input returns 0.0. A decay
 * of 0.0 collapses to "only the most recent word counts"; a decay
 * of 1.0 collapses to a flat arithmetic mean.
 *
 * The previous behaviour took the MAX frequency across the window
 * (so a single early trailing word locked in the locale signal).
 * The decay-weighted blend lets the active language track a
 * mid-sentence switch smoothly without flipping on the first
 * recognised word.
 */
internal object TrailingContextLanguageBlend {

    /** Geometric-decay default. See `NlpManager.TrailingContextDecay`. */
    const val DEFAULT_DECAY: Double = 0.7

    fun score(
        contextWordsOldestFirst: List<String>,
        freqLookup: (String) -> Double,
        decay: Double = DEFAULT_DECAY,
    ): Double {
        require(decay in 0.0..1.0) { "decay must be in [0.0, 1.0], was $decay" }
        if (contextWordsOldestFirst.isEmpty()) return 0.0
        var weight = 1.0
        var weightSum = 0.0
        var weightedFreqSum = 0.0
        // Walk in reverse so index 0 = most-recent word, weight = 1.0.
        for (i in contextWordsOldestFirst.indices.reversed()) {
            val word = contextWordsOldestFirst[i]
            val freq = freqLookup(word)
            weightedFreqSum += weight * freq
            weightSum += weight
            weight *= decay
        }
        return if (weightSum > 0.0) weightedFreqSum / weightSum else 0.0
    }
}
