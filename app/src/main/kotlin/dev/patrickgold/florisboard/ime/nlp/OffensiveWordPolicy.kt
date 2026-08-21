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

import android.view.textservice.SuggestionsInfo
import java.util.Locale

internal object OffensiveWordPolicy {
    enum class FilterTier {
        NONE,
        SLURS_ONLY,
        ALL,
    }

    private val TokenPattern = Regex("[\\p{L}]+(?:['’][\\p{L}]+)*")

    private val SlurWords = setOf(
        "chink",
        "fag",
        "faggot",
        "gook",
        "kike",
        "nigger",
        "retard",
        "retarded",
        "spic",
        "tranny",
        "wetback",
    )

    private val AllOffensiveWords = SlurWords + setOf(
        "ass",
        "asses",
        "asshole",
        "bastard",
        "bitch",
        "bullshit",
        "cock",
        "cocks",
        "crap",
        "damn",
        "dick",
        "dicks",
        "douche",
        "fuck",
        "fucked",
        "fucker",
        "fuckers",
        "fucking",
        "goddamn",
        "hell",
        "motherfucker",
        "motherfuckers",
        "piss",
        "shit",
        "shits",
        "slut",
        "whore",
    )

    fun tier(
        blockPossiblyOffensive: Boolean,
        blockSlursOnly: Boolean,
    ): FilterTier {
        return when {
            blockPossiblyOffensive -> FilterTier.ALL
            blockSlursOnly -> FilterTier.SLURS_ONLY
            else -> FilterTier.NONE
        }
    }

    fun shouldBlock(text: CharSequence, tier: FilterTier): Boolean {
        if (tier == FilterTier.NONE) return false
        return TokenPattern.findAll(text).any { match ->
            val token = match.value.lowercase(Locale.ROOT).replace('’', '\'')
            when (tier) {
                FilterTier.NONE -> false
                FilterTier.SLURS_ONLY -> token in SlurWords
                FilterTier.ALL -> token in AllOffensiveWords
            }
        }
    }

    fun filterCandidates(
        candidates: List<SuggestionCandidate>,
        tier: FilterTier,
    ): List<SuggestionCandidate> {
        if (tier == FilterTier.NONE) return candidates
        return candidates.filterNot { candidate -> shouldBlock(candidate.text, tier) }
    }

    fun filterSpellingResult(
        result: SpellingResult,
        tier: FilterTier,
    ): SpellingResult {
        if (tier == FilterTier.NONE) return result
        val filteredSuggestions = result.suggestions().filterNot { suggestion -> shouldBlock(suggestion, tier) }
        if (filteredSuggestions.size == result.suggestionsInfo.suggestionsCount) return result

        val hasRecommendedSuggestions = result.suggestionsInfo.suggestionsAttributes and
            SuggestionsInfo.RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS != 0
        return when {
            result.isGrammarError -> SpellingResult.grammarError(
                suggestions = filteredSuggestions.toTypedArray(),
                isHighConfidenceResult = hasRecommendedSuggestions,
            )
            result.isTypo -> SpellingResult.typo(
                suggestions = filteredSuggestions.toTypedArray(),
                isHighConfidenceResult = hasRecommendedSuggestions,
            )
            result.isValidWord -> SpellingResult.validWord()
            else -> SpellingResult.unspecified()
        }
    }
}
