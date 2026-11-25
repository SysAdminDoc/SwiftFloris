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

import dev.patrickgold.florisboard.ime.nlp.LanguageCaseMapping
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Turkish casing is not Unicode's default: `I` lowercases to `ı` and `i`
 * uppercases to `İ`. Everything here guards the paths that used the
 * locale-invariant mapping, which silently produced forms the dictionary could
 * never contain.
 */
class TurkishCaseMappingTest : FunSpec({

    val turkish = turkishDictionary(
        "istanbul" to 200,
        "ışık" to 180,
        "için" to 250,
        "değil" to 240,
    )

    context("LanguageCaseMapping") {
        test("lowercases with Turkish rules") {
            LanguageCaseMapping.lowercase("İstanbul", "tr") shouldBe "istanbul"
            LanguageCaseMapping.lowercase("IŞIK", "tr") shouldBe "ışık"
            LanguageCaseMapping.lowercase("DEĞİL", "tr") shouldBe "değil"
        }

        test("uppercases with Turkish rules") {
            LanguageCaseMapping.uppercase("istanbul", "tr") shouldBe "İSTANBUL"
            LanguageCaseMapping.uppercase("ışık", "tr") shouldBe "IŞIK"
        }

        test("capitalizes the first letter with Turkish rules") {
            LanguageCaseMapping.capitalizeFirst("istanbul", "tr") shouldBe "İstanbul"
            LanguageCaseMapping.capitalizeFirst("ışık", "tr") shouldBe "Işık"
        }

        test("keeps the invariant mapping when no language is known") {
            LanguageCaseMapping.lowercase("I", null) shouldBe "i"
            LanguageCaseMapping.lowercase("İstanbul", null) shouldNotBe "istanbul"
            LanguageCaseMapping.localeFor(null) shouldBe java.util.Locale.ROOT
            LanguageCaseMapping.localeFor("  ") shouldBe java.util.Locale.ROOT
        }

        test("leaves English unchanged") {
            LanguageCaseMapping.lowercase("Istanbul", "en") shouldBe "istanbul"
            LanguageCaseMapping.uppercase("istanbul", "en") shouldBe "ISTANBUL"
            LanguageCaseMapping.capitalizeFirst("istanbul", "en") shouldBe "Istanbul"
            LanguageCaseMapping.lowercase("I", "en") shouldBe "i"
        }
    }

    context("normalizeWord") {
        test("folds a capitalized Turkish word to its dictionary form") {
            LatinDictionarySuggester.normalizeWord("İstanbul", "tr") shouldBe "istanbul"
            LatinDictionarySuggester.normalizeWord("IŞIK", "tr") shouldBe "ışık"
            LatinDictionarySuggester.normalizeWord("Değil", "tr") shouldBe "değil"
        }

        test("folds the dotted and dotless I apart") {
            LatinDictionarySuggester.normalizeWord("I", "tr") shouldBe "ı"
            LatinDictionarySuggester.normalizeWord("İ", "tr") shouldBe "i"
            LatinDictionarySuggester.normalizeWord("I", "en") shouldBe "i"
        }

        test("keeps the invariant form when no language is given") {
            // Asset decoding calls this without a language; the stored list is
            // already lowercase, so the old behaviour has to stay reachable.
            LatinDictionarySuggester.normalizeWord("Istanbul") shouldBe "istanbul"
            LatinDictionarySuggester.normalizeWord("I") shouldBe "i"
        }
    }

    context("suggest") {
        test("resolves a capitalized Turkish prefix instead of falling back") {
            // The full word is deliberately not completed back to itself
            // (`completions` drops exact matches), so the prefix is what proves
            // both halves: "İ" folds to "i" for lookup, and the result is
            // re-cased with Turkish rules on the way out.
            val suggestions = LatinDictionarySuggester.suggest(
                rawWord = "İstanbu",
                dictionary = turkish,
                maxCandidateCount = 4,
                languageCode = "tr",
            )

            suggestions.map { it.text } shouldContain "İstanbul"
        }

        test("completes a Turkish prefix typed with a dotted capital I") {
            val suggestions = LatinDictionarySuggester.suggest(
                rawWord = "İçi",
                dictionary = turkish,
                maxCandidateCount = 4,
                languageCode = "tr",
            )

            suggestions.map { it.text } shouldContain "İçin"
        }

        test("uppercases candidates with Turkish rules for an all-caps prefix") {
            val suggestions = LatinDictionarySuggester.suggest(
                rawWord = "IŞI",
                dictionary = turkish,
                maxCandidateCount = 4,
                languageCode = "tr",
            )

            suggestions.map { it.text } shouldContain "IŞIK"
        }

        test("leaves lowercase Turkish input alone") {
            val suggestions = LatinDictionarySuggester.suggest(
                rawWord = "içi",
                dictionary = turkish,
                maxCandidateCount = 4,
                languageCode = "tr",
            )

            suggestions.map { it.text } shouldContain "için"
        }

        test("keeps English capitalization behaviour unchanged") {
            val english = turkishDictionary("the" to 250, "this" to 240)
            LatinDictionarySuggester.suggest("Th", english, maxCandidateCount = 2, languageCode = "en")
                .map { it.text } shouldBe listOf("The", "This")
            LatinDictionarySuggester.suggest("TH", english, maxCandidateCount = 2, languageCode = "en")
                .map { it.text } shouldBe listOf("THE", "THIS")
        }
    }
})

private fun turkishDictionary(vararg words: Pair<String, Int>): LatinDictionarySnapshot {
    val frequencies = words.toMap()
    return LatinDictionarySnapshot(
        frequencies = frequencies,
        sortedWords = frequencies.keys.sorted(),
    )
}
