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

internal data class ImmediateAutocorrectCorrection(
    val dictionaryWord: String,
    val text: String,
)

internal object ImmediateAutocorrect {
    fun englishFirstPersonPronoun(
        rawWord: String,
        languageCode: String,
    ): ImmediateAutocorrectCorrection? {
        if (normalizeLanguageCode(languageCode) != "en") {
            return null
        }

        val typedWord = rawWord.trim().trim { char -> !char.isLetter() && char != '\'' }
        val normalizedWord = typedWord.lowercase()
        val correction = EnglishFirstPersonPronounCorrections[normalizedWord] ?: return null
        val letters = typedWord.filter { it.isLetter() }
        val isAllCapsInput = letters.length > 1 && letters.all { it.isUpperCase() }
        if (typedWord.firstOrNull()?.equals('i', ignoreCase = true) != true ||
            typedWord == correction.text ||
            isAllCapsInput
        ) {
            return null
        }
        return correction
    }

    fun englishFirstPersonPronounCandidate(
        rawWord: String,
        languageCode: String,
    ): WordSuggestionCandidate? {
        val correction = englishFirstPersonPronoun(rawWord, languageCode) ?: return null
        // Only the standalone "i" → "I" substitution is safe to auto-commit without dictionary
        // context, because there is no English word "i". Multi-letter forms ("im", "ill", "id",
        // "ive") collide with real words; the LatinLanguageProvider path adds those as suggestions
        // with a dictionary check before allowing auto-commit.
        val typedNormalized = rawWord.trim().trim { c -> !c.isLetter() && c != '\'' }.lowercase()
        if (typedNormalized != "i") return null
        return WordSuggestionCandidate(
            text = correction.text,
            confidence = 1.0,
            isEligibleForAutoCommit = true,
            isEligibleForUserRemoval = false,
        )
    }

    private fun normalizeLanguageCode(languageCode: String): String {
        return languageCode
            .substringBefore('-')
            .substringBefore('_')
            .lowercase()
            .ifBlank { "en" }
    }

    private val EnglishFirstPersonPronounCorrections = mapOf(
        "i" to ImmediateAutocorrectCorrection("i", "I"),
        "id" to ImmediateAutocorrectCorrection("i'd", "I'd"),
        "i'd" to ImmediateAutocorrectCorrection("i'd", "I'd"),
        "ill" to ImmediateAutocorrectCorrection("i'll", "I'll"),
        "i'll" to ImmediateAutocorrectCorrection("i'll", "I'll"),
        "im" to ImmediateAutocorrectCorrection("i'm", "I'm"),
        "i'm" to ImmediateAutocorrectCorrection("i'm", "I'm"),
        "ive" to ImmediateAutocorrectCorrection("i've", "I've"),
        "i've" to ImmediateAutocorrectCorrection("i've", "I've"),
    )
}
