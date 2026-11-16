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
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

/**
 * The cases come from a typing trace of "river violet " sent as one burst: space committed
 * "riverbed" for "river" from the list ranked for "riv", and "riverbed" for "violet" from the
 * list left over from the word before.
 */
class PublishedCandidatesTest : FunSpec({
    test("a list ranked for an earlier prefix can't replace the finished word") {
        val rankedForRiv = PublishedCandidates(
            candidates = listOf(word("riv"), word("riverbed", eligible = true), word("river")),
            rankedFor = CandidateWordKey(word = "riv", wordStart = 0, cursor = 3),
        )
        val typed = CandidateWordKey(word = "river", wordStart = 0, cursor = 5)

        spacebarPick(typed, rankedForRiv.candidates)?.text shouldBe "riverbed"
        spacebarPick(typed, rankedForRiv.forAutoCommit(typed).candidates) shouldBe null
    }

    test("the previous word's list can't replace the next word") {
        val rankedForRiver = PublishedCandidates(
            candidates = listOf(word("riverbed", eligible = true), word("river"), word("rivers")),
            rankedFor = CandidateWordKey(word = "river", wordStart = 0, cursor = 5),
        )
        val typed = CandidateWordKey(word = "violet", wordStart = 6, cursor = 12)

        spacebarPick(typed, rankedForRiver.candidates)?.text shouldBe "river"
        spacebarPick(typed, rankedForRiver.forAutoCommit(typed).candidates) shouldBe null
    }

    test("the list ranked for this word still corrects it") {
        val key = CandidateWordKey(word = "teh", wordStart = 4, cursor = 7)
        val rankedForTeh = PublishedCandidates(
            candidates = listOf(word("teh"), word("the", eligible = true), word("ten")),
            signals = mapOf("the" to SwiftKeyCandidateSignals()),
            rankedFor = key,
        )

        val usable = rankedForTeh.forAutoCommit(key.copy())

        usable shouldBe rankedForTeh
        spacebarPick(key, usable.candidates)?.text shouldBe "the"
    }

    test("next-word predictions made after one word aren't used after another") {
        val afterRiver = PublishedCandidates(
            candidates = listOf(word("the"), word("bank"), word("bed")),
            rankedFor = CandidateWordKey(word = "", wordStart = null, cursor = 6),
        )

        afterRiver.forAutoCommit(CandidateWordKey(word = "", wordStart = null, cursor = 13))
            .candidates.shouldBeEmpty()
    }

    test("a list that wasn't ranked is never used to correct") {
        val glidePreview = PublishedCandidates(candidates = listOf(word("river", eligible = true)))

        glidePreview.forAutoCommit(CandidateWordKey(word = "rivet", wordStart = 0, cursor = 5))
            .candidates.shouldBeEmpty()
    }
})

private fun spacebarPick(typed: CandidateWordKey, candidates: List<SuggestionCandidate>): SuggestionCandidate? {
    return CandidateAutoCommitPolicy.selectSpacebarCandidate(
        autoCorrectEnabled = true,
        quickPredictionInsertEnabled = false,
        keyVariation = KeyVariation.NORMAL,
        currentWord = typed.word,
        currentWordStart = typed.wordStart,
        textBeforeCursor = typed.word,
        candidates = candidates,
        candidateSignals = emptyMap(),
        rejectionPolicy = AutoCommitSuppression(),
        userDictionaryShortcutCandidate = null,
        immediatePhraseRepairCandidate = null,
        immediateAutoCommitCandidate = null,
    )
}

private fun word(text: String, eligible: Boolean = false): WordSuggestionCandidate {
    return WordSuggestionCandidate(
        text = text,
        confidence = 0.95,
        isEligibleForAutoCommit = eligible,
    )
}
