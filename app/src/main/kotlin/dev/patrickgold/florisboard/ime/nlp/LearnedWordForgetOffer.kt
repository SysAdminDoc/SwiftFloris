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

import java.util.concurrent.atomic.AtomicReference

/**
 * The "Don't learn" chip for a word the space just learned. Posting it straight to the strip
 * didn't work: the space it came from starts a newer suggestion pass, which replaced the chip
 * before it was drawn. Instead every pass asks [chipFor], which keeps showing it while the cursor
 * sits right after the learned word, and ends the offer once the user types on.
 */
internal class LearnedWordForgetOffer {
    private class Offer(
        val candidate: LearnedWordForgetSuggestionCandidate,
        val latestRequestIdWhenMade: Long,
    )

    private val offered = AtomicReference<Offer?>(null)

    /**
     * Offers [candidate]. [latestRequestId] is the newest suggestion request so far. Passes up to
     * it ranked text from before the word was learned, so they may show the chip but not end it.
     */
    fun offer(candidate: LearnedWordForgetSuggestionCandidate, latestRequestId: Long) {
        offered.set(Offer(candidate, latestRequestId))
    }

    fun clear() {
        offered.set(null)
    }

    /**
     * The chip for pass [requestId] over [currentWord] with [textBeforeSelection], or `null`. A
     * pass newer than the offer ends it when a new word starts after the learned one, or the
     * cursor is no longer right after it. A pass for the learned word itself (or a prefix of it)
     * leaves the offer alone.
     */
    fun chipFor(
        currentWord: String,
        textBeforeSelection: CharSequence,
        requestId: Long,
    ): LearnedWordForgetSuggestionCandidate? {
        val offer = offered.get() ?: return null
        val candidate = offer.candidate
        val mayEnd = requestId > offer.latestRequestIdWhenMade
        if (currentWord.isNotEmpty()) {
            val beforeWord = textBeforeSelection.removeSuffix(currentWord)
            val typingNextWord = beforeWord.isNotEmpty() && beforeWord.last().isWhitespace() &&
                endsWithWord(beforeWord, candidate.word)
            if (typingNextWord && mayEnd) offered.compareAndSet(offer, null)
            return null
        }
        if (!endsWithWord(textBeforeSelection, candidate.word)) {
            if (mayEnd) offered.compareAndSet(offer, null)
            return null
        }
        return candidate
    }

    /**
     * The offered chip while [textBeforeSelection] still ends right after its word, for adding it
     * to a strip that's already shown. Never ends the offer.
     */
    fun chipAfter(textBeforeSelection: CharSequence): LearnedWordForgetSuggestionCandidate? {
        val candidate = offered.get()?.candidate ?: return null
        return candidate.takeIf { endsWithWord(textBeforeSelection, it.word) }
    }

    companion object {
        /** Puts [chip] in the left slot and keeps the middle one, which the space key reads. */
        fun place(chip: SuggestionCandidate, candidates: List<SuggestionCandidate>): List<SuggestionCandidate> {
            return if (candidates.size <= 1) listOf(chip) + candidates else listOf(chip) + candidates.drop(1)
        }

        /**
         * Whether [text] ends with [word] as a whole word, ignoring spaces and tabs after it. A
         * line break after the word counts as moving on.
         */
        fun endsWithWord(text: CharSequence, word: String): Boolean {
            val trimmed = text.trimEnd(' ', '\t')
            if (!trimmed.endsWith(word, ignoreCase = true)) return false
            val start = trimmed.length - word.length
            return start == 0 || !trimmed[start - 1].isLetterOrDigit()
        }
    }
}
