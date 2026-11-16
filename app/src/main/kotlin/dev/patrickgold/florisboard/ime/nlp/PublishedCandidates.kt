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
 * Where in the text a candidate list was ranked: the word being typed, where it starts, and
 * where the cursor was. Two lists with the same key were ranked for the same word in the same
 * spot. The cursor also tells next-word predictions apart, which all have an empty word.
 */
internal data class CandidateWordKey(
    val word: String,
    val wordStart: Int?,
    val cursor: Int,
)

/**
 * The candidate strip as last shown, with the signals and the [CandidateWordKey] its word
 * guesses were ranked for. Lists that weren't ranked (glide previews, a learned-word prompt,
 * a cleared strip) have no key.
 *
 * Ranking runs off the main thread, so a space typed right after a letter can arrive before
 * the list for that letter is out. Auto-correct may only pick from a list ranked for the word
 * it replaces. A burst of "river violet " turned "river" into "riverbed" from the list
 * ranked for "riv", and "violet" into "riverbed" from the previous word's list.
 */
internal data class PublishedCandidates(
    val candidates: List<SuggestionCandidate> = emptyList(),
    val signals: Map<String, SwiftKeyCandidateSignals> = emptyMap(),
    val rankedFor: CandidateWordKey? = null,
) {
    /**
     * This list when it was ranked for [current], otherwise an empty one: the typed word then
     * stays as typed, apart from corrections worked out from [current] itself.
     */
    fun forAutoCommit(current: CandidateWordKey): PublishedCandidates {
        return if (rankedFor == current) this else NotRankedForThisWord
    }

    companion object {
        private val NotRankedForThisWord = PublishedCandidates()
    }
}
