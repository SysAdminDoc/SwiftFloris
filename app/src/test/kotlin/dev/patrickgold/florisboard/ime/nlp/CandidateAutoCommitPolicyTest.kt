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

import dev.patrickgold.florisboard.ime.text.key.KeyVariation
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class CandidateAutoCommitPolicyTest : FunSpec({
    test("auto-commit disabled blocks every candidate source") {
        CandidateAutoCommitPolicy.selectAutoCommitCandidate(
            autoCorrectEnabled = false,
            currentWord = "omw",
            currentWordStart = 0,
            candidates = listOf(candidate("home", eligible = true)),
            candidateSignals = emptyMap(),
            keyVariation = KeyVariation.NORMAL,
            rejectionPolicy = AutoCommitSuppression(),
            userDictionaryShortcutCandidate = candidate("on my way", eligible = true),
            immediatePhraseRepairCandidate = candidate("a lot", eligible = true),
            immediateAutoCommitCandidate = candidate("don't", eligible = true),
        ) shouldBe null
    }

    test("shortcut candidate outranks phrase repair active strip and immediate fallback") {
        val shortcut = candidate("on my way", eligible = true)

        CandidateAutoCommitPolicy.selectAutoCommitCandidate(
            autoCorrectEnabled = true,
            currentWord = "omw",
            currentWordStart = 0,
            candidates = listOf(candidate("onward", eligible = true)),
            candidateSignals = emptyMap(),
            keyVariation = KeyVariation.NORMAL,
            rejectionPolicy = AutoCommitSuppression(),
            userDictionaryShortcutCandidate = shortcut,
            immediatePhraseRepairCandidate = candidate("on my", eligible = true),
            immediateAutoCommitCandidate = candidate("I'm", eligible = true),
        ) shouldBe shortcut
    }

    test("phrase repair outranks active strip candidate") {
        val phraseRepair = candidate("a lot", eligible = true)

        CandidateAutoCommitPolicy.selectAutoCommitCandidate(
            autoCorrectEnabled = true,
            currentWord = "alot",
            currentWordStart = 0,
            candidates = listOf(candidate("slot", eligible = true)),
            candidateSignals = emptyMap(),
            keyVariation = KeyVariation.NORMAL,
            rejectionPolicy = AutoCommitSuppression(),
            userDictionaryShortcutCandidate = null,
            immediatePhraseRepairCandidate = phraseRepair,
            immediateAutoCommitCandidate = candidate("I'll", eligible = true),
        ) shouldBe phraseRepair
    }

    test("active strip auto-commit candidate requires auto-commit eligibility and language confidence") {
        val safe = candidate("received", eligible = true)
        val nonEligible = candidate("receiver", eligible = false)

        CandidateAutoCommitPolicy.selectAutoCommitCandidate(
            autoCorrectEnabled = true,
            currentWord = "recieved",
            currentWordStart = 0,
            candidates = listOf(nonEligible, safe),
            candidateSignals = mapOf(
                "receiver" to SwiftKeyCandidateSignals(languageConfidence = 1.0),
                "received" to SwiftKeyCandidateSignals(languageConfidence = 1.0),
            ),
            keyVariation = KeyVariation.NORMAL,
            rejectionPolicy = AutoCommitSuppression(),
            userDictionaryShortcutCandidate = null,
            immediatePhraseRepairCandidate = null,
            immediateAutoCommitCandidate = null,
        ) shouldBe safe

        CandidateAutoCommitPolicy.selectAutoCommitCandidate(
            autoCorrectEnabled = true,
            currentWord = "recieved",
            currentWordStart = 0,
            candidates = listOf(safe),
            candidateSignals = mapOf("received" to SwiftKeyCandidateSignals(languageConfidence = 0.0)),
            keyVariation = KeyVariation.NORMAL,
            rejectionPolicy = AutoCommitSuppression(),
            userDictionaryShortcutCandidate = null,
            immediatePhraseRepairCandidate = null,
            immediateAutoCommitCandidate = null,
        ) shouldBe null
    }

    test("immediate fallback is used after active candidates fail eligibility") {
        val fallback = candidate("can't", eligible = true)

        CandidateAutoCommitPolicy.selectAutoCommitCandidate(
            autoCorrectEnabled = true,
            currentWord = "cant",
            currentWordStart = 0,
            candidates = listOf(candidate("canto", eligible = false)),
            candidateSignals = emptyMap(),
            keyVariation = KeyVariation.NORMAL,
            rejectionPolicy = AutoCommitSuppression(),
            userDictionaryShortcutCandidate = null,
            immediatePhraseRepairCandidate = null,
            immediateAutoCommitCandidate = fallback,
        ) shouldBe fallback
    }

    test("rejection keeps the manually restored literal before any source can reapply the correction") {
        val rejectionPolicy = rejectedCorrectionPolicy()

        CandidateAutoCommitPolicy.selectAutoCommitCandidate(
            autoCorrectEnabled = true,
            currentWord = "teh",
            currentWordStart = 0,
            candidates = listOf(candidate("the", eligible = true)),
            candidateSignals = emptyMap(),
            keyVariation = KeyVariation.NORMAL,
            rejectionPolicy = rejectionPolicy,
            userDictionaryShortcutCandidate = candidate("the", eligible = true),
            immediatePhraseRepairCandidate = null,
            immediateAutoCommitCandidate = candidate("the", eligible = true),
        ) shouldBe null
    }

    test("spacebar candidate disabled when autocorrect and quick prediction insert are both off") {
        CandidateAutoCommitPolicy.selectSpacebarCandidate(
            autoCorrectEnabled = false,
            quickPredictionInsertEnabled = false,
            currentWord = "",
            currentWordStart = null,
            textBeforeCursor = "",
            candidates = listOf(candidate("hello", confidence = 1.0)),
            candidateSignals = emptyMap(),
            keyVariation = KeyVariation.NORMAL,
            rejectionPolicy = AutoCommitSuppression(),
            userDictionaryShortcutCandidate = null,
            immediatePhraseRepairCandidate = null,
            immediateAutoCommitCandidate = null,
        ) shouldBe null
    }

    test("spacebar quick prediction can insert a next-word candidate with autocorrect off") {
        val prediction = candidate("world", confidence = 0.95)

        CandidateAutoCommitPolicy.selectSpacebarCandidate(
            autoCorrectEnabled = false,
            quickPredictionInsertEnabled = true,
            currentWord = "",
            currentWordStart = null,
            textBeforeCursor = "Hello. ",
            candidates = listOf(candidate("hello", confidence = 0.2), prediction),
            candidateSignals = emptyMap(),
            keyVariation = KeyVariation.NORMAL,
            rejectionPolicy = AutoCommitSuppression(),
            userDictionaryShortcutCandidate = null,
            immediatePhraseRepairCandidate = null,
            immediateAutoCommitCandidate = null,
        ) shouldBe prediction
    }

    test("spacebar autocorrect uses explicit shortcut before quick prediction") {
        val shortcut = candidate("on my way", eligible = true)

        CandidateAutoCommitPolicy.selectSpacebarCandidate(
            autoCorrectEnabled = true,
            quickPredictionInsertEnabled = true,
            currentWord = "omw",
            currentWordStart = 0,
            textBeforeCursor = "omw",
            candidates = listOf(candidate("tomorrow", confidence = 1.0)),
            candidateSignals = emptyMap(),
            keyVariation = KeyVariation.NORMAL,
            rejectionPolicy = AutoCommitSuppression(),
            userDictionaryShortcutCandidate = shortcut,
            immediatePhraseRepairCandidate = null,
            immediateAutoCommitCandidate = null,
        ) shouldBe shortcut
    }

    test("password fields block auto-commit from every candidate source") {
        // Composing is disabled in password fields, so a commit would append
        // after the typed text instead of replacing it — even an eligible
        // immediate contraction candidate (don't) must never fire there.
        CandidateAutoCommitPolicy.selectAutoCommitCandidate(
            autoCorrectEnabled = true,
            keyVariation = KeyVariation.PASSWORD,
            currentWord = "dont",
            currentWordStart = 0,
            candidates = listOf(candidate("don't", eligible = true)),
            candidateSignals = mapOf("don't" to SwiftKeyCandidateSignals(languageConfidence = 1.0)),
            rejectionPolicy = AutoCommitSuppression(),
            userDictionaryShortcutCandidate = candidate("on my way", eligible = true),
            immediatePhraseRepairCandidate = candidate("a lot", eligible = true),
            immediateAutoCommitCandidate = candidate("don't", eligible = true),
        ) shouldBe null
    }

    test("password fields block spacebar auto-commit and quick prediction insert") {
        CandidateAutoCommitPolicy.selectSpacebarCandidate(
            autoCorrectEnabled = true,
            quickPredictionInsertEnabled = true,
            keyVariation = KeyVariation.PASSWORD,
            currentWord = "dont",
            currentWordStart = 0,
            textBeforeCursor = "dont",
            candidates = listOf(candidate("don't", eligible = true, confidence = 1.0)),
            candidateSignals = mapOf("don't" to SwiftKeyCandidateSignals(languageConfidence = 1.0)),
            rejectionPolicy = AutoCommitSuppression(),
            userDictionaryShortcutCandidate = candidate("on my way", eligible = true),
            immediatePhraseRepairCandidate = null,
            immediateAutoCommitCandidate = candidate("don't", eligible = true),
        ) shouldBe null
    }

    test("plain-space prediction suppression mirrors quick prediction availability") {
        CandidateAutoCommitPolicy.shouldSuppressPlainSpaceForPrediction(
            quickPredictionInsertEnabled = true,
            currentWord = "",
            textBeforeCursor = "Hello. ",
            candidates = listOf(candidate("world", confidence = 0.95)),
            candidateSignals = emptyMap(),
        ) shouldBe true

        CandidateAutoCommitPolicy.shouldSuppressPlainSpaceForPrediction(
            quickPredictionInsertEnabled = true,
            currentWord = "hel",
            textBeforeCursor = "hel",
            candidates = listOf(candidate("hello", confidence = 0.95)),
            candidateSignals = emptyMap(),
        ) shouldBe false
    }
})

private fun candidate(
    text: String,
    confidence: Double = 1.0,
    eligible: Boolean = false,
): WordSuggestionCandidate {
    return WordSuggestionCandidate(
        text = text,
        confidence = confidence,
        isEligibleForAutoCommit = eligible,
    )
}

private fun rejectedCorrectionPolicy(): AutoCommitSuppression {
    return AutoCommitSuppression().also { policy ->
        policy.rememberAccepted(originalText = "teh", correctedText = "the", wordStart = 0)
        policy.rejectAccepted(textBeforeSelection = "the ", cursorPosition = 4)
    }
}
