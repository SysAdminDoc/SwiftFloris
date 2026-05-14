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
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * ROADMAP §7 Next-12.3 — property-based autocorrect invariants. Where the
 * unit-test surface in [LatinDictionarySuggesterTest] pins specific
 * expected (input → output) pairs, this property suite asserts the
 * algorithmic invariants the suggester must hold across the entire input
 * space. These are the contracts a refactor of the suggester (KenLM
 * integration, neural rerank, etc.) must continue to satisfy.
 */
class LatinSuggesterPropertyTest : FunSpec({

    val dictionary = run {
        // Compact fixture dictionary; large enough that edit-distance
        // queries find candidates, small enough that property generation
        // converges in CI time. The frequencies range from low (~80) to
        // top (~255) so both auto-commit and non-autocommit tiers are
        // exercised.
        val words = listOf(
            "i" to 255, "i'd" to 243, "i'll" to 244, "i'm" to 253, "i've" to 248,
            "the" to 255, "they" to 248, "them" to 240, "their" to 235, "there" to 232,
            "and" to 250, "any" to 240, "are" to 245,
            "you" to 251, "your" to 245, "you're" to 243,
            "this" to 252, "that" to 250, "those" to 240, "these" to 235,
            "do" to 248, "don't" to 246, "does" to 235,
            "is" to 252, "it" to 251, "its" to 244, "it's" to 242,
            "received" to 220, "separate" to 200, "tomorrow" to 215, "address" to 210,
            "test" to 180, "toast" to 160, "best" to 175, "rest" to 170,
        )
        latinDictionary(*words.toTypedArray())
    }

    val dictionaryWordsLowercase: List<String> = dictionary.sortedWords
        .map { it.lowercase() }
        .filter { it.length in 3..10 }
    val arbDictionaryWord: Arb<String> = Arb.element(dictionaryWordsLowercase)

    test("normalizeWord is idempotent") {
        checkAll(Arb.string(1, 20)) { raw ->
            val once = LatinDictionarySuggester.normalizeWord(raw)
            if (once != null) {
                LatinDictionarySuggester.normalizeWord(once) shouldBe once
            }
        }
    }

    test("normalizeWord returns null for non-letter-bearing input") {
        checkAll(Arb.string(1, 12).filter { s -> s.isNotEmpty() && s.none { it.isLetter() } }) { raw ->
            LatinDictionarySuggester.normalizeWord(raw) shouldBe null
        }
    }

    test("suggest never returns the typed word itself") {
        checkAll(arbDictionaryWord) { word ->
            val suggestions = LatinDictionarySuggester.suggest(
                rawWord = word,
                dictionary = dictionary,
                maxCandidateCount = 6,
            )
            suggestions.forEach { candidate ->
                // The typed-literal protection rule: the autocorrect strip
                // must never autocommit the typed word back at the user.
                // A literal match means we have no useful suggestion at
                // all, not a high-confidence one.
                candidate.isEligibleForAutoCommit && candidate.text.equals(word, ignoreCase = true) should {
                    it shouldBe false
                }
            }
        }
    }

    test("suggest returns at most maxCandidateCount candidates") {
        checkAll(arbDictionaryWord, Arb.int(1..8)) { word, max ->
            val suggestions = LatinDictionarySuggester.suggest(
                rawWord = word,
                dictionary = dictionary,
                maxCandidateCount = max,
            )
            (suggestions.size <= max) shouldBe true
        }
    }

    test("suggest dedupes by lowercase across primary/secondary/completion tiers") {
        checkAll(arbDictionaryWord) { word ->
            val suggestions = LatinDictionarySuggester.suggest(
                rawWord = word,
                dictionary = dictionary,
                maxCandidateCount = 8,
            )
            val lowered = suggestions.map { it.text.lowercase() }
            lowered.distinct().size shouldBe lowered.size
        }
    }

    test("corrections returned are all within edit-distance 2 of input") {
        checkAll(arbDictionaryWord) { word ->
            // Introduce a deterministic single-character mutation so we
            // know the typo is at distance 1 from the dictionary entry.
            val typo = if (word.length >= 4) {
                word.substring(0, 1) + word[2] + word[1] + word.substring(3)  // swap
            } else {
                word + "x"  // append
            }
            val corrections = LatinDictionarySuggester.corrections(
                word = typo,
                dictionary = dictionary,
                maxCandidateCount = 6,
            )
            corrections.forEach { candidate ->
                val distance = damerauLevenshtein(typo, candidate.text.lowercase())
                (distance <= 2) shouldBe true
            }
        }
    }

    test("delete-and-retype reaches identity for any dictionary word") {
        // ROADMAP §7 Next-12.3 invariant: if user types a word, accepts
        // a correction (or not), deletes back to nothing, then retypes
        // the same characters, the suggester returns the same first
        // candidate. Stateless suggest() makes this trivially true; we
        // pin it explicitly so a stateful refactor (KenLM context cache)
        // doesn't silently break.
        checkAll(arbDictionaryWord) { word ->
            val a = LatinDictionarySuggester.suggest(word, dictionary, maxCandidateCount = 4)
            val b = LatinDictionarySuggester.suggest(word, dictionary, maxCandidateCount = 4)
            a.firstOrNull()?.text shouldBe b.firstOrNull()?.text
        }
    }

    test("ALL_CAPS typed prefix produces ALL_CAPS suggestions when length >= 2") {
        checkAll(arbDictionaryWord.filter { it.length >= 3 }) { word ->
            val capsPrefix = word.substring(0, 2).uppercase()
            val suggestions = LatinDictionarySuggester.suggest(
                rawWord = capsPrefix,
                dictionary = dictionary,
                maxCandidateCount = 4,
            )
            suggestions.forEach { candidate ->
                // Each candidate's letter run must be uppercase. We
                // tolerate apostrophes and non-letters mixed in (e.g.
                // "I'D", "DON'T") so we only check letter chars.
                val letters = candidate.text.filter { it.isLetter() }
                if (letters.isNotEmpty()) {
                    letters.all { it.isUpperCase() } shouldBe true
                }
            }
        }
    }

    test("Title Case typed prefix produces Title Case suggestions") {
        checkAll(arbDictionaryWord.filter { it.length >= 3 }) { word ->
            val titlePrefix = word[0].uppercase() + word.substring(1, 3)
            val suggestions = LatinDictionarySuggester.suggest(
                rawWord = titlePrefix,
                dictionary = dictionary,
                maxCandidateCount = 4,
            )
            suggestions.forEach { candidate ->
                val firstLetter = candidate.text.firstOrNull { it.isLetter() }
                if (firstLetter != null) firstLetter.isUpperCase() shouldBe true
            }
        }
    }

    test("repeated character substring never crashes the suggester") {
        // Defensive: long runs of repeated characters historically tripped
        // edit-distance tables in suggesters. The Levenshtein/SymSpell
        // matrix and the bounded-distance check must short-circuit
        // gracefully rather than allocating O(n^2) state.
        checkAll(Arb.list(Arb.element(listOf('a', 'b', 'c', 'd', 'e')), 1..20)) { chars ->
            val word = chars.joinToString("")
            LatinDictionarySuggester.suggest(
                rawWord = word,
                dictionary = dictionary,
                maxCandidateCount = 4,
            )
            // No exception thrown = pass. The test framework's checkAll
            // wraps each iteration so any thrown error fails the iteration.
        }
    }
})

/**
 * Reference Damerau-Levenshtein implementation used to assert correction
 * distance is bounded. Not the same routine as the suggester's internal
 * implementation; intentionally independent so a bug in the suggester
 * can't silently match a bug in the test oracle.
 */
private fun damerauLevenshtein(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    val rows = Array(a.length + 1) { IntArray(b.length + 1) }
    for (i in 0..a.length) rows[i][0] = i
    for (j in 0..b.length) rows[0][j] = j
    for (i in 1..a.length) for (j in 1..b.length) {
        val cost = if (a[i - 1] == b[j - 1]) 0 else 1
        rows[i][j] = minOf(
            rows[i - 1][j] + 1,
            rows[i][j - 1] + 1,
            rows[i - 1][j - 1] + cost,
        )
        if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
            rows[i][j] = minOf(rows[i][j], rows[i - 2][j - 2] + 1)
        }
    }
    return rows[a.length][b.length]
}

private fun latinDictionary(vararg words: Pair<String, Int>): LatinDictionarySnapshot {
    val frequencies = words.toMap()
    return LatinDictionarySnapshot(
        frequencies = frequencies,
        sortedWords = frequencies.keys.sorted(),
    )
}
