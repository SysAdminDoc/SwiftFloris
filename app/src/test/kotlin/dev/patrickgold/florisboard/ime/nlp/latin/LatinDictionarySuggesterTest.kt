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

class LatinDictionarySuggesterTest : FunSpec({
    val dictionary = latinDictionary(
        "i" to 255,
        "i'd" to 243,
        "i'll" to 244,
        "i'm" to 253,
        "i've" to 248,
        "don't" to 252,
        "you're" to 250,
        "they're" to 240,
        "we've" to 235,
        "won't" to 240,
        "the" to 255,
        "this" to 252,
        "those" to 248,
        "there" to 220,
        "their" to 215,
        "then" to 210,
        "test" to 180,
        "toast" to 160,
        "address" to 210,
        "received" to 250,
    )

    test("suggest returns autocorrect candidate for transposed typo") {
        val suggestions = LatinDictionarySuggester.suggest("teh", dictionary, maxCandidateCount = 4)

        suggestions.first().text shouldBe "the"
        suggestions.first().isEligibleForAutoCommit shouldBe true
    }

    test("suggest auto-commits lowercase English first-person pronoun") {
        val suggestions = LatinDictionarySuggester.suggest("i", dictionary, maxCandidateCount = 4)

        suggestions.first().text shouldBe "I"
        suggestions.first().isEligibleForAutoCommit shouldBe true
    }

    test("suggest auto-commits common lowercase English first-person contractions") {
        mapOf(
            "id" to "I'd",
            "ill" to "I'll",
            "im" to "I'm",
            "ive" to "I've",
        ).forEach { (typed, expected) ->
            val suggestions = LatinDictionarySuggester.suggest(typed, dictionary, maxCandidateCount = 4)

            suggestions.first().text shouldBe expected
            suggestions.first().isEligibleForAutoCommit shouldBe true
        }
    }

    test("suggest auto-commits sentence-start English first-person contractions") {
        mapOf(
            "Id" to "I'd",
            "Ill" to "I'll",
            "Im" to "I'm",
            "Ive" to "I've",
        ).forEach { (typed, expected) ->
            val suggestions = LatinDictionarySuggester.suggest(typed, dictionary, maxCandidateCount = 4)

            suggestions.first().text shouldBe expected
            suggestions.first().isEligibleForAutoCommit shouldBe true
        }
    }

    test("suggest auto-commits safe-tier non-pronoun contractions") {
        // SAFE-tier contractions are auto-committed by the dict-aware path whenever the
        // canonical contracted form exists in the dictionary. Case is preserved.
        mapOf(
            "dont" to "don't",
            "Dont" to "Don't",
            "youre" to "you're",
            "Youre" to "You're",
            "theyre" to "they're",
            "weve" to "we've",
            "wont" to "won't",
        ).forEach { (typed, expected) ->
            val suggestions = LatinDictionarySuggester.suggest(typed, dictionary, maxCandidateCount = 4)

            suggestions.first().text shouldBe expected
            suggestions.first().isEligibleForAutoCommit shouldBe true
        }
    }

    test("suggest does not apply English pronoun casing to other Latin languages") {
        LatinDictionarySuggester.suggest(
            rawWord = "i",
            dictionary = dictionary,
            maxCandidateCount = 4,
            languageCode = "it",
        ) shouldBe emptyList()
    }

    test("suggest returns frequency-ranked prefix completions") {
        // Prefix completions remain frequency-ranked; "this" and "those" outrank
        // the lower-frequency "they're", "there", and "their" entries.
        val suggestions = LatinDictionarySuggester.suggest("th", dictionary, maxCandidateCount = 3)

        suggestions.map { it.text } shouldBe listOf("the", "this", "those")
        suggestions.any { it.isEligibleForAutoCommit } shouldBe false
    }

    test("suggest keeps completions ahead of corrections for an active prefix") {
        val suggestions = LatinDictionarySuggester.suggest("ther", dictionary, maxCandidateCount = 3)

        suggestions.first().text shouldBe "there"
        suggestions.first().isEligibleForAutoCommit shouldBe false
    }

    test("suggest promotes high confidence correction ahead of prefix completion for misspelling") {
        val suggestions = LatinDictionarySuggester.suggest("Thos", dictionary, maxCandidateCount = 3)

        suggestions.map { it.text } shouldBe listOf("This", "Those")
        suggestions.first().isEligibleForAutoCommit shouldBe true
    }

    test("suggest uses bounded distance-two SymSpell for deeper typos") {
        val suggestions = LatinDictionarySuggester.suggest("recved", dictionary, maxCandidateCount = 3)

        suggestions.first().text shouldBe "received"
        suggestions.first().isEligibleForAutoCommit shouldBe true
    }

    test("suggest preserves typed capitalization") {
        val suggestions = LatinDictionarySuggester.suggest("Teh", dictionary, maxCandidateCount = 4)

        suggestions.first().text shouldBe "The"
    }

    // ROADMAP §7 Next-3.3 — capitalization-aware suggestions: the typed prefix's
    // case pattern (lowercase / Title Case / ALL_CAPS) is applied to every
    // candidate the suggester returns, for both prefix completions and
    // edit-distance corrections. This matches the SwiftKey "Foo if F, foo if f"
    // contract from FlorisBoard #1007.
    test("suggest applies Title case to prefix completions when prefix is capitalized") {
        val suggestions = LatinDictionarySuggester.suggest("Th", dictionary, maxCandidateCount = 3)

        suggestions.map { it.text } shouldBe listOf("The", "This", "Those")
    }

    test("suggest applies ALL_CAPS to prefix completions when prefix is ALL_CAPS") {
        val suggestions = LatinDictionarySuggester.suggest("TH", dictionary, maxCandidateCount = 3)

        suggestions.map { it.text } shouldBe listOf("THE", "THIS", "THOSE")
    }

    test("suggest leaves prefix completions lowercase when prefix is lowercase") {
        val suggestions = LatinDictionarySuggester.suggest("th", dictionary, maxCandidateCount = 3)

        suggestions.map { it.text } shouldBe listOf("the", "this", "those")
    }

    test("suggest applies typed case to long-distance corrections too") {
        val suggestions = LatinDictionarySuggester.suggest("RECVED", dictionary, maxCandidateCount = 3)

        suggestions.first().text shouldBe "RECEIVED"
    }

    // ROADMAP §7 Next-3.3a — single-letter proper-noun completion.
    // Capital single-letter prefixes ("F") surface high-frequency dictionary
    // words starting with that letter, case-matched. Lowercase single letters
    // ("f") return nothing (avoids flooding the strip).
    test("single capital letter surfaces dictionary completions case-matched") {
        val suggestions = LatinDictionarySuggester.suggest("T", dictionary, maxCandidateCount = 4)

        // All returned candidates must start with "T", be length >= 2, and
        // none should auto-commit (single-letter prefix is too ambiguous).
        suggestions.isNotEmpty() shouldBe true
        suggestions.all { it.text.startsWith("T") } shouldBe true
        suggestions.all { it.text.length >= 2 } shouldBe true
        suggestions.all { !it.isEligibleForAutoCommit } shouldBe true
    }

    test("single lowercase letter returns no completions") {
        val suggestions = LatinDictionarySuggester.suggest("t", dictionary, maxCandidateCount = 4)
        // Lowercase single letter should NOT trigger the proper-noun completion
        // path — would flood the strip every time the user begins a normal word.
        suggestions shouldBe emptyList()
    }

    test("suggest ignores non-word tokens") {
        LatinDictionarySuggester.suggest("123", dictionary, maxCandidateCount = 4) shouldBe emptyList()
        LatinDictionarySuggester.suggest("mail@example.com", dictionary, maxCandidateCount = 4) shouldBe emptyList()
    }
})

private fun latinDictionary(vararg words: Pair<String, Int>): LatinDictionarySnapshot {
    val frequencies = words.toMap()
    return LatinDictionarySnapshot(
        frequencies = frequencies,
        sortedWords = frequencies.keys.sorted(),
    )
}
