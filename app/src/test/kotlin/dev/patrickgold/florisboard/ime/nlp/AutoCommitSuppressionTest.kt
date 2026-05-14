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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AutoCommitSuppressionTest : FunSpec({
    test("does not suppress an accepted correction until backspace rejects it") {
        val suppression = AutoCommitSuppression()

        suppression.rememberAccepted(originalText = "teh", correctedText = "the", wordStart = 0)

        suppression.shouldSuppress(currentWord = "teh", candidateText = "the", currentWordStart = 0) shouldBe false
    }

    test("suppresses the rejected correction for the same word slot") {
        val suppression = AutoCommitSuppression()

        suppression.rememberAccepted(originalText = "teh", correctedText = "the", wordStart = 0)
        suppression.rejectAccepted(textBeforeSelection = "the ", cursorPosition = 4) shouldBe true

        suppression.shouldSuppress(currentWord = "teh", candidateText = "the", currentWordStart = 0) shouldBe true
    }

    test("does not suppress a different correction for the rejected word") {
        val suppression = AutoCommitSuppression()

        suppression.rememberAccepted(originalText = "teh", correctedText = "the", wordStart = 0)
        suppression.rejectAccepted(textBeforeSelection = "the ", cursorPosition = 4) shouldBe true

        suppression.shouldSuppress(currentWord = "teh", candidateText = "ten", currentWordStart = 0) shouldBe false
    }

    test("exposes rejected pair penalty without suppressing unrelated candidates") {
        val suppression = AutoCommitSuppression()

        suppression.rememberAccepted(originalText = "teh", correctedText = "the", wordStart = 0)
        suppression.rejectAccepted(textBeforeSelection = "the ", cursorPosition = 4) shouldBe true

        suppression.rejectedPairPenalty(currentWord = "teh", candidateText = "the", currentWordStart = 0) shouldBe 1.0
        suppression.rejectedPairPenalty(currentWord = "teh", candidateText = "ten", currentWordStart = 0) shouldBe 0.0
    }

    test("keeps rejection active while user edits back to the original word") {
        val suppression = AutoCommitSuppression()

        suppression.rememberAccepted(originalText = "teh", correctedText = "the", wordStart = 0)
        suppression.rejectAccepted(textBeforeSelection = "the ", cursorPosition = 4) shouldBe true
        suppression.onContentChanged(currentWord = "th", currentWordStart = 0)

        suppression.shouldSuppress(currentWord = "teh", candidateText = "the", currentWordStart = 0) shouldBe true
    }

    test("does not suppress the same text in a different word slot") {
        val suppression = AutoCommitSuppression()

        suppression.rememberAccepted(originalText = "teh", correctedText = "the", wordStart = 0)
        suppression.rejectAccepted(textBeforeSelection = "the ", cursorPosition = 4) shouldBe true

        suppression.shouldSuppress(currentWord = "teh", candidateText = "the", currentWordStart = 12) shouldBe false
    }

    test("clears rejection after the manually typed word is completed") {
        val suppression = AutoCommitSuppression()

        suppression.rememberAccepted(originalText = "teh", correctedText = "the", wordStart = 0)
        suppression.rejectAccepted(textBeforeSelection = "the ", cursorPosition = 4) shouldBe true
        suppression.shouldSuppress(currentWord = "teh", candidateText = "the", currentWordStart = 0) shouldBe true

        suppression.onContentChanged(currentWord = "", currentWordStart = null)

        suppression.shouldSuppress(currentWord = "teh", candidateText = "the", currentWordStart = 0) shouldBe false
    }

    test("ignores backspace outside the accepted correction") {
        val suppression = AutoCommitSuppression()

        suppression.rememberAccepted(originalText = "teh", correctedText = "the", wordStart = 0)

        suppression.rejectAccepted(textBeforeSelection = "the next", cursorPosition = 8) shouldBe false
        suppression.shouldSuppress(currentWord = "teh", candidateText = "the", currentWordStart = 0) shouldBe false
    }
})
