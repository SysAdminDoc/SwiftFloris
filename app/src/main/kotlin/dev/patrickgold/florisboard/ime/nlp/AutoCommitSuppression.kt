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

internal class AutoCommitSuppression {
    private var acceptedAutoCommit: AcceptedAutoCommit? = null
    private var rejectedAutoCommit: RejectedAutoCommit? = null

    fun rememberAccepted(
        originalText: CharSequence,
        correctedText: CharSequence,
        wordStart: Int?,
    ) {
        val original = normalizeWord(originalText) ?: return
        val corrected = normalizeWord(correctedText) ?: return
        if (original == corrected) return
        acceptedAutoCommit = AcceptedAutoCommit(
            original = original,
            corrected = corrected,
            correctedDisplayLength = correctedText.length,
            wordStart = wordStart,
        )
    }

    fun rejectAccepted(textBeforeSelection: CharSequence, cursorPosition: Int?): Boolean {
        val accepted = acceptedAutoCommit ?: return false
        if (!accepted.matchesCursor(textBeforeSelection, cursorPosition)) {
            return false
        }
        rejectedAutoCommit = RejectedAutoCommit(
            original = accepted.original,
            corrected = accepted.corrected,
            wordStart = accepted.wordStart,
        )
        acceptedAutoCommit = null
        return true
    }

    fun shouldSuppress(
        currentWord: CharSequence,
        candidateText: CharSequence,
        currentWordStart: Int?,
    ): Boolean {
        val shouldSuppress = rejectedPairPenalty(
            currentWord = currentWord,
            candidateText = candidateText,
            currentWordStart = currentWordStart,
        ) >= 1.0
        if (shouldSuppress) {
            rejectedAutoCommit = rejectedAutoCommit?.copy(hasSuppressed = true)
        }
        return shouldSuppress
    }

    fun rejectedPairPenalty(
        currentWord: CharSequence,
        candidateText: CharSequence,
        currentWordStart: Int?,
    ): Double {
        val rejected = rejectedAutoCommit ?: return 0.0
        val current = normalizeWord(currentWord) ?: return 0.0
        val candidate = normalizeWord(candidateText) ?: return 0.0
        val isSameWordSlot = rejected.wordStart == null ||
            currentWordStart == null ||
            rejected.wordStart == currentWordStart
        if (!isSameWordSlot || current != rejected.original) {
            return 0.0
        }
        return if (candidate == rejected.corrected) 1.0 else 0.0
    }

    fun onContentChanged(currentWord: CharSequence, currentWordStart: Int?) {
        val accepted = acceptedAutoCommit
        val current = normalizeWord(currentWord)
        if (accepted != null && current != null) {
            val isSameWordSlot = accepted.wordStart == null ||
                currentWordStart == null ||
                accepted.wordStart == currentWordStart
            if (!isSameWordSlot && current != accepted.original && current != accepted.corrected) {
                acceptedAutoCommit = null
            }
        }

        val rejected = rejectedAutoCommit
        if (rejected?.hasSuppressed == true) {
            val isCurrentRejectedWord = current == rejected.original &&
                (rejected.wordStart == null || currentWordStart == null || rejected.wordStart == currentWordStart)
            if (!isCurrentRejectedWord) {
                rejectedAutoCommit = null
            }
        }
    }

    private fun AcceptedAutoCommit.matchesCursor(
        textBeforeSelection: CharSequence,
        cursorPosition: Int?,
    ): Boolean {
        if (wordStart != null && cursorPosition != null) {
            val correctedEnd = wordStart + correctedDisplayLength
            if (cursorPosition < correctedEnd || cursorPosition > correctedEnd + 1) {
                return false
            }
        }
        return normalizedLastWord(textBeforeSelection) == corrected
    }

    private data class AcceptedAutoCommit(
        val original: String,
        val corrected: String,
        val correctedDisplayLength: Int,
        val wordStart: Int?,
    )

    private data class RejectedAutoCommit(
        val original: String,
        val corrected: String,
        val wordStart: Int?,
        val hasSuppressed: Boolean = false,
    )

    private companion object {
        fun normalizeWord(text: CharSequence): String? {
            val normalized = text
                .trimWordEdges()
                .lowercase()
            return normalized.takeIf { word -> word.any { it.isLetterOrDigit() } }
        }

        fun normalizedLastWord(text: CharSequence): String? {
            var end = text.length
            while (end > 0 && !text[end - 1].isWordChar()) {
                end--
            }
            var start = end
            while (start > 0 && text[start - 1].isWordChar()) {
                start--
            }
            return if (start < end) {
                normalizeWord(text.subSequence(start, end))
            } else {
                null
            }
        }

        fun CharSequence.trimWordEdges(): String {
            var start = 0
            var end = length
            while (start < end && !this[start].isWordChar()) {
                start++
            }
            while (end > start && !this[end - 1].isWordChar()) {
                end--
            }
            return subSequence(start, end).toString()
        }

        fun Char.isWordChar(): Boolean {
            return isLetterOrDigit() || this == '\'' || this == '\u2019'
        }
    }
}
