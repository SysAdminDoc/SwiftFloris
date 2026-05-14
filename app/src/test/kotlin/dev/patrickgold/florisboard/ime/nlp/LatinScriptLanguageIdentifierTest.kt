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
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe

class LatinScriptLanguageIdentifierTest : FunSpec({
    test("scores Spanish current token and trailing context above English") {
        val scores = LatinScriptLanguageIdentifier.score(
            currentWord = "grac",
            previousWords = listOf("hola", "amigo"),
            locales = listOf("en", "es"),
        )

        scores shouldContainKey "es"
        scores["es"] shouldBe 1.0
        scores.getValue("en") shouldBeLessThan 0.2
    }

    test("current token can override trailing context during a language switch") {
        val scores = LatinScriptLanguageIdentifier.score(
            currentWord = "th",
            previousWords = listOf("hola", "gracias"),
            locales = listOf("en", "es"),
        )

        scores shouldContainKey "en"
        scores["en"] shouldBe 1.0
        scores.getValue("es") shouldBeLessThan 0.8
    }

    test("normalizes accents before scoring Portuguese") {
        val scores = LatinScriptLanguageIdentifier.score(
            currentWord = "obrigad",
            previousWords = listOf("voc\u00EA", "n\u00E3o"),
            locales = listOf("en-US", "pt-BR"),
        )

        scores["pt"] shouldBe 1.0
        scores.getValue("en") shouldBeLessThan 0.2
    }

    test("returns no signal for unsupported or ambiguous short input") {
        LatinScriptLanguageIdentifier.score(
            currentWord = "x",
            previousWords = emptyList(),
            locales = listOf("en", "es"),
        ) shouldBe emptyMap()
    }

    test("keeps related Latin languages separable with current-token evidence") {
        val scores = LatinScriptLanguageIdentifier.score(
            currentWord = "merci",
            previousWords = listOf("pour", "vous"),
            locales = listOf("fr", "it", "pt"),
        )

        scores["fr"] shouldBe 1.0
        scores.getValue("it") shouldBeLessThan 0.4
        scores.getValue("pt") shouldBeLessThan 0.4
    }

    test("scores German markers strongly when enrolled with English") {
        val scores = LatinScriptLanguageIdentifier.score(
            currentWord = "nicht",
            previousWords = listOf("ich", "bin"),
            locales = listOf("en", "de"),
        )

        scores["de"] shouldBe 1.0
        scores.getValue("de") shouldBeGreaterThan scores.getValue("en")
    }
})
