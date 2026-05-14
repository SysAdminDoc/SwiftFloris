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

package dev.patrickgold.florisboard.ime.nlp.latin

internal data class ColdStartNextWordPrior(
    val word: String,
    val confidence: Double,
)

/**
 * Tiny local phrase prior used before a user has enough personal n-gram history.
 *
 * SwiftKey feels strong on a fresh install because its language model already knows
 * sentence starts and common short continuations. This keeps that behavior local and
 * transparent while the personal bigram/trigram stores warm up.
 */
internal object ColdStartNextWordPriors {
    fun suggest(
        textBeforeCursor: String,
        languageCode: String,
        maxCandidateCount: Int,
    ): List<ColdStartNextWordPrior> {
        if (maxCandidateCount <= 0 || LatinDictionaryStore.normalizeLanguageCode(languageCode) != "en") {
            return emptyList()
        }
        val words = when {
            isSentenceStart(textBeforeCursor) -> SentenceStart
            else -> {
                val previousWord = previousWordOf(textBeforeCursor)?.lowercase() ?: return emptyList()
                Continuations[previousWord].orEmpty()
            }
        }
        return words
            .take(maxCandidateCount)
            .mapIndexed { index, word ->
                ColdStartNextWordPrior(
                    word = word,
                    confidence = (0.44 - 0.025 * index).coerceAtLeast(0.24),
                )
            }
    }

    private fun isSentenceStart(textBeforeCursor: String): Boolean {
        val trimmed = textBeforeCursor.trimEnd()
        if (trimmed.isEmpty()) return true
        return trimmed.last() in SentenceTerminators
    }

    private fun previousWordOf(textBeforeCursor: String): String? {
        val trimmed = textBeforeCursor.trimEnd()
        if (trimmed.isEmpty()) return null
        var end = trimmed.length
        while (end > 0 && !trimmed[end - 1].isLetter() && trimmed[end - 1] != '\'' && trimmed[end - 1] != '-') {
            end--
        }
        if (end == 0) return null
        var start = end
        while (start > 0) {
            val ch = trimmed[start - 1]
            if (!ch.isLetter() && ch != '\'' && ch != '-') break
            start--
        }
        return trimmed.substring(start, end).takeIf { it.isNotBlank() }
    }

    private val SentenceTerminators = setOf('.', '!', '?', '\n')

    private val SentenceStart = listOf(
        "i",
        "the",
        "this",
        "what",
        "how",
        "you",
        "it",
        "we",
    )

    private val Continuations = mapOf(
        "i" to listOf("am", "have", "will", "think", "can", "don't"),
        "i'm" to listOf("going", "not", "so", "sorry", "sure"),
        "i'll" to listOf("be", "send", "check", "try"),
        "i've" to listOf("been", "got", "seen", "had"),
        "the" to listOf("same", "best", "first", "last", "next", "only"),
        "this" to listOf("is", "was", "will", "one", "looks"),
        "that" to listOf("is", "was", "would", "sounds", "makes"),
        "it" to listOf("is", "was", "will", "looks", "sounds"),
        "you" to listOf("can", "are", "have", "will", "should"),
        "we" to listOf("can", "are", "have", "will", "need"),
        "they" to listOf("are", "were", "will", "have"),
        "he" to listOf("is", "was", "will", "has"),
        "she" to listOf("is", "was", "will", "has"),
        "thank" to listOf("you"),
        "thanks" to listOf("for", "again"),
        "how" to listOf("are", "is", "do", "can", "was"),
        "what" to listOf("are", "is", "do", "was", "would"),
        "where" to listOf("is", "are", "did", "do"),
        "when" to listOf("is", "are", "do", "can"),
        "why" to listOf("is", "are", "did", "would"),
        "let" to listOf("me", "us"),
        "good" to listOf("morning", "night", "luck", "idea"),
        "see" to listOf("you", "the", "if"),
        "talk" to listOf("to", "soon"),
        "on" to listOf("my", "the", "your"),
        "in" to listOf("the", "a", "my"),
        "for" to listOf("the", "you", "me", "a"),
        "of" to listOf("the", "course"),
        "to" to listOf("the", "be", "get", "make"),
        "be" to listOf("able", "there", "ready"),
    )
}
