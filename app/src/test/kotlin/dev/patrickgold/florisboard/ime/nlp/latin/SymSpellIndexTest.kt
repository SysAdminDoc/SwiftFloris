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
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.shouldBe

class SymSpellIndexTest : FunSpec({
    test("distance-two build stops before crossing delete-entry budget") {
        val index = SymSpellIndex.build(
            words = listOf("received", "tomorrow", "separate", "address"),
            maxDistance = 2,
            maxDeleteEntries = 40,
        )

        index.isComplete shouldBe false
        index.indexedWordCount shouldBe 1
        (index.entryCount() <= 40) shouldBe true
        index.candidates("recved") shouldContain "received"
        index.candidates("tomorow") shouldNotContain "tomorrow"
    }

    test("unlimited budget keeps existing complete-index behavior") {
        val index = SymSpellIndex.build(
            words = listOf("received", "tomorrow", "separate", "address"),
            maxDistance = 2,
        )

        index.isComplete shouldBe true
        index.indexedWordCount shouldBe 4
        index.candidates("recved") shouldContain "received"
        index.candidates("tomorow") shouldContain "tomorrow"
    }

    test("words longer than distance-2 cap are skipped") {
        val longWord = "internationalization"
        longWord.length shouldBeGreaterThan 16

        val index = SymSpellIndex.build(
            words = listOf(longWord, "hello", "world"),
            maxDistance = 2,
        )

        index.indexedWordCount shouldBe 2
        index.candidates("helo") shouldContain "hello"
        index.candidates("wrld") shouldContain "world"
    }

    test("distance-1 accepts words up to 30 chars") {
        val word = "abcdefghijklmnopqrstuvwxyz1234"
        word.length shouldBe 30

        val index = SymSpellIndex.build(
            words = listOf(word),
            maxDistance = 1,
        )

        index.indexedWordCount shouldBe 1
    }

    test("distance-1 skips words over 30 chars") {
        val word = "a".repeat(31)

        val index = SymSpellIndex.build(
            words = listOf(word, "hello"),
            maxDistance = 1,
        )

        index.indexedWordCount shouldBe 1
    }

    test("heapScaledBudget returns full budget on large heaps") {
        val budget = SymSpellIndex.heapScaledBudget(750_000)
        budget shouldBeGreaterThanOrEqualTo 1_000
    }

    test("heapScaledBudget preserves unlimited sentinel") {
        val budget = SymSpellIndex.heapScaledBudget(SymSpellIndex.UnlimitedDeleteEntryBudget)
        budget shouldBe SymSpellIndex.UnlimitedDeleteEntryBudget
    }

    test("large vocabulary with tight budget produces partial index without crash") {
        val words = (1..50_000).map { "word${it}abcdefgh" }

        val index = SymSpellIndex.build(
            words = words,
            maxDistance = 1,
            maxDeleteEntries = 5_000,
        )

        index.isComplete shouldBe false
        (index.entryCount() <= 5_000) shouldBe true
        index.indexedWordCount shouldBeGreaterThan 0
    }
})
