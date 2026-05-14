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
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe

class MultilingualTokenScorerTest : FunSpec({
    test("secondary-language typed words count as known words") {
        val signal = MultilingualTokenScorer.score(
            localeEvidence = listOf(
                TokenLocaleEvidence(typedFrequency = 0.0, candidateFrequency = 0.0),
                TokenLocaleEvidence(typedFrequency = 0.84, candidateFrequency = 0.84),
            ),
            typedWordKnownByUserDictionary = false,
            candidateMatchesTypedWord = true,
            candidateIsEligibleForAutoCommit = false,
        )

        signal.typedWordKnown shouldBe true
        signal.dictionaryFrequency shouldBe 0.84
        signal.languageConfidence shouldBe 1.0
    }

    test("wrong-language corrections lose confidence when typed word is known elsewhere") {
        val signal = MultilingualTokenScorer.score(
            localeEvidence = listOf(
                TokenLocaleEvidence(typedFrequency = 0.0, candidateFrequency = 0.91),
                TokenLocaleEvidence(typedFrequency = 0.77, candidateFrequency = 0.0),
            ),
            typedWordKnownByUserDictionary = false,
            candidateMatchesTypedWord = false,
            candidateIsEligibleForAutoCommit = true,
        )

        signal.typedWordKnown shouldBe true
        signal.dictionaryFrequency shouldBe 0.91
        signal.languageConfidence shouldBeLessThan 0.4
    }

    test("shared-spelling typed words damp one-language corrections") {
        val signal = MultilingualTokenScorer.score(
            localeEvidence = listOf(
                TokenLocaleEvidence(typedFrequency = 0.58, candidateFrequency = 0.74),
                TokenLocaleEvidence(typedFrequency = 0.91, candidateFrequency = 0.0),
            ),
            typedWordKnownByUserDictionary = false,
            candidateMatchesTypedWord = false,
            candidateIsEligibleForAutoCommit = true,
        )

        signal.typedWordKnown shouldBe true
        signal.dictionaryFrequency shouldBe 0.74
        signal.languageConfidence shouldBe 0.52
    }

    test("single-language typing keeps full language confidence") {
        val signal = MultilingualTokenScorer.score(
            localeEvidence = listOf(
                TokenLocaleEvidence(typedFrequency = 0.0, candidateFrequency = 0.72),
            ),
            typedWordKnownByUserDictionary = false,
            candidateMatchesTypedWord = false,
            candidateIsEligibleForAutoCommit = true,
        )

        signal.typedWordKnown shouldBe false
        signal.dictionaryFrequency shouldBe 0.72
        signal.languageConfidence shouldBe 1.0
    }

    test("trailing context boosts candidates from the active sentence language") {
        val signal = MultilingualTokenScorer.score(
            localeEvidence = listOf(
                TokenLocaleEvidence(typedFrequency = 0.0, candidateFrequency = 0.0, contextFrequency = 0.0),
                TokenLocaleEvidence(typedFrequency = 0.0, candidateFrequency = 0.72, contextFrequency = 0.88),
            ),
            typedWordKnownByUserDictionary = false,
            candidateMatchesTypedWord = false,
            candidateIsEligibleForAutoCommit = false,
        )

        signal.typedWordKnown shouldBe false
        signal.dictionaryFrequency shouldBe 0.72
        signal.languageConfidence shouldBe 0.98
    }

    test("trailing context demotes candidates from the inactive sentence language") {
        val signal = MultilingualTokenScorer.score(
            localeEvidence = listOf(
                TokenLocaleEvidence(typedFrequency = 0.0, candidateFrequency = 0.82, contextFrequency = 0.0),
                TokenLocaleEvidence(typedFrequency = 0.0, candidateFrequency = 0.0, contextFrequency = 0.88),
            ),
            typedWordKnownByUserDictionary = false,
            candidateMatchesTypedWord = false,
            candidateIsEligibleForAutoCommit = true,
        )

        signal.typedWordKnown shouldBe false
        signal.dictionaryFrequency shouldBe 0.82
        signal.languageConfidence shouldBeLessThan 0.4
    }

    test("current token prefix can override trailing context during a language switch") {
        val signal = MultilingualTokenScorer.score(
            localeEvidence = listOf(
                TokenLocaleEvidence(typedFrequency = 0.0, candidateFrequency = 0.84, contextFrequency = 0.0),
                TokenLocaleEvidence(typedFrequency = 0.0, candidateFrequency = 0.0, contextFrequency = 0.88),
            ),
            typedWordKnownByUserDictionary = false,
            candidateMatchesTypedWord = false,
            candidateIsEligibleForAutoCommit = false,
            candidateCompletesTypedWord = true,
            contextLocaleHasTypedPrefixCandidate = false,
        )

        signal.typedWordKnown shouldBe false
        signal.dictionaryFrequency shouldBe 0.84
        signal.languageConfidence shouldBe 0.74
    }

    test("active-language prefix candidates keep trailing context priority") {
        val signal = MultilingualTokenScorer.score(
            localeEvidence = listOf(
                TokenLocaleEvidence(typedFrequency = 0.0, candidateFrequency = 0.84, contextFrequency = 0.0),
                TokenLocaleEvidence(typedFrequency = 0.0, candidateFrequency = 0.0, contextFrequency = 0.88),
            ),
            typedWordKnownByUserDictionary = false,
            candidateMatchesTypedWord = false,
            candidateIsEligibleForAutoCommit = false,
            candidateCompletesTypedWord = true,
            contextLocaleHasTypedPrefixCandidate = true,
        )

        signal.typedWordKnown shouldBe false
        signal.dictionaryFrequency shouldBe 0.84
        signal.languageConfidence shouldBe 0.12
    }

    test("context-language autocorrects lose confidence when they conflict with the current prefix") {
        val signal = MultilingualTokenScorer.score(
            localeEvidence = listOf(
                TokenLocaleEvidence(typedFrequency = 0.0, candidateFrequency = 0.0, contextFrequency = 0.0),
                TokenLocaleEvidence(typedFrequency = 0.0, candidateFrequency = 0.72, contextFrequency = 0.88),
            ),
            typedWordKnownByUserDictionary = false,
            candidateMatchesTypedWord = false,
            candidateIsEligibleForAutoCommit = true,
            candidateConflictsWithTypedPrefix = true,
        )

        signal.typedWordKnown shouldBe false
        signal.dictionaryFrequency shouldBe 0.72
        signal.languageConfidence shouldBe 0.50
    }
})
