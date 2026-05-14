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

internal data class PreviousWordContext(
    val prev2: String?,
    val prev1: String?,
)

internal object TypingContextExtractor {
    fun prefixBeforeCurrentWord(textBeforeSelection: CharSequence, currentWord: String): String {
        var before = textBeforeSelection.toString()
        val activeWord = currentWord.trim()
        if (activeWord.isNotEmpty() && before.endsWith(activeWord)) {
            before = before.dropLast(activeWord.length)
        }
        return before
    }

    fun sentenceLocalPrefixBeforeCurrentWord(textBeforeSelection: CharSequence, currentWord: String): String {
        return sentenceLocalPrefix(prefixBeforeCurrentWord(textBeforeSelection, currentWord))
    }

    fun previousWordsBeforeCurrentWord(textBeforeSelection: CharSequence, currentWord: String): PreviousWordContext {
        val words = previousWordListBeforeCurrentWord(
            textBeforeSelection = textBeforeSelection,
            currentWord = currentWord,
            maxDepth = 2,
        )
        return PreviousWordContext(
            prev2 = words.getOrNull(words.size - 2),
            prev1 = words.lastOrNull(),
        )
    }

    fun previousWordListBeforeCurrentWord(
        textBeforeSelection: CharSequence,
        currentWord: String,
        maxDepth: Int,
    ): List<String> {
        if (maxDepth <= 0) return emptyList()
        val before = sentenceLocalPrefixBeforeCurrentWord(textBeforeSelection, currentWord)
        return previousWordsOf(before, maxDepth)
    }

    private fun sentenceLocalPrefix(textBeforeCursor: String): String {
        val boundary = textBeforeCursor.indexOfLast { it in SentenceTerminators }
        return if (boundary >= 0) {
            textBeforeCursor.substring(boundary + 1)
        } else {
            textBeforeCursor
        }
    }

    private fun previousWordsOf(textBeforeCursor: String, maxDepth: Int): List<String> {
        if (maxDepth <= 0) return emptyList()
        var working = textBeforeCursor
        val words = ArrayDeque<String>()
        repeat(maxDepth) {
            val trimmed = working.trimEnd()
            if (trimmed.isEmpty()) return words.toList()
            var end = trimmed.length
            while (end > 0 && !trimmed[end - 1].isContextWordChar()) end--
            var start = end
            while (start > 0 && trimmed[start - 1].isContextWordChar()) start--
            if (start == end) return words.toList()
            words.addFirst(trimmed.substring(start, end))
            working = trimmed.substring(0, start)
        }
        return words.toList()
    }

    private fun Char.isContextWordChar(): Boolean {
        return isLetter() || this == '\'' || this == '\u2019' || this == '-'
    }

    private val SentenceTerminators = setOf('.', '!', '?', '\n')
}
