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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ColdStartNextWordPriorsTest : FunSpec({
    test("suggests SwiftKey-like sentence starts before personal history exists") {
        val suggestions = ColdStartNextWordPriors.suggest(
            textBeforeCursor = "",
            languageCode = "en-US",
            maxCandidateCount = 4,
        )

        suggestions.map { it.word } shouldBe listOf("i", "the", "this", "what")
        suggestions.map { it.confidence } shouldBe listOf(0.44, 0.415, 0.39, 0.365)
    }

    test("suggests common continuations for the previous word") {
        val suggestions = ColdStartNextWordPriors.suggest(
            textBeforeCursor = "I ",
            languageCode = "en",
            maxCandidateCount = 5,
        )

        suggestions.map { it.word } shouldBe listOf("am", "have", "will", "think", "can")
    }

    test("prefers phrase continuations over one-word continuations") {
        val suggestions = ColdStartNextWordPriors.suggest(
            textBeforeCursor = "Let me ",
            languageCode = "en",
            maxCandidateCount = 4,
        )

        suggestions.map { it.word } shouldBe listOf("know", "see", "check", "try")
    }

    test("uses three-word phrase continuations when available") {
        val suggestions = ColdStartNextWordPriors.suggest(
            textBeforeCursor = "as soon as ",
            languageCode = "en",
            maxCandidateCount = 3,
        )

        suggestions.map { it.word } shouldBe listOf("possible", "i", "we")
    }

    test("scores partial-word candidates against cold-start phrase context") {
        ColdStartNextWordPriors.score(
            textBeforeCursor = "Let me ",
            languageCode = "en-US",
            candidateWord = "know",
        ) shouldBe 0.44

        ColdStartNextWordPriors.score(
            textBeforeCursor = "Let me ",
            languageCode = "en-US",
            candidateWord = "you",
        ) shouldBe 0.0
    }

    test("scores common contraction continuations for glide rescoring") {
        ColdStartNextWordPriors.score(
            textBeforeCursor = "We're ",
            languageCode = "en-US",
            candidateWord = "going",
        ) shouldBe 0.44

        ColdStartNextWordPriors.score(
            textBeforeCursor = "I'd ",
            languageCode = "en-US",
            candidateWord = "like",
        ) shouldBe 0.44
    }

    test("suggests localized sentence starts for bundled Latin languages") {
        ColdStartNextWordPriors.suggest(
            textBeforeCursor = "",
            languageCode = "de-DE",
            maxCandidateCount = 4,
        ).map { it.word } shouldBe listOf("ich", "das", "die", "der")

        ColdStartNextWordPriors.suggest(
            textBeforeCursor = "",
            languageCode = "es",
            maxCandidateCount = 4,
        ).map { it.word } shouldBe listOf("yo", "el", "la", "qué")

        ColdStartNextWordPriors.suggest(
            textBeforeCursor = "",
            languageCode = "cs",
            maxCandidateCount = 4,
        ).map { it.word } shouldBe listOf("já", "to", "co", "jak")
    }

    test("uses localized phrase continuations before generic dictionary bootstrap") {
        ColdStartNextWordPriors.suggest(
            textBeforeCursor = "Muchas gracias ",
            languageCode = "es-MX",
            maxCandidateCount = 3,
        ).map { it.word } shouldBe listOf("por", "de", "otra")

        ColdStartNextWordPriors.score(
            textBeforeCursor = "Merci beaucoup ",
            languageCode = "fr",
            candidateWord = "pour",
        ) shouldBe 0.44

        ColdStartNextWordPriors.score(
            textBeforeCursor = "Bom dia ",
            languageCode = "pt-BR",
            candidateWord = "como",
        ) shouldBe 0.44
    }

    test("does not inject priors for unsupported languages") {
        ColdStartNextWordPriors.suggest(
            textBeforeCursor = "",
            languageCode = "nl",
            maxCandidateCount = 4,
        ) shouldBe emptyList()
    }
})
