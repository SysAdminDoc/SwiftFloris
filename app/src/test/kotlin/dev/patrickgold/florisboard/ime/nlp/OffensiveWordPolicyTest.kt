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
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class OffensiveWordPolicyTest : FunSpec({
    test("tier precedence keeps the legacy all-offensive switch authoritative") {
        OffensiveWordPolicy.tier(blockPossiblyOffensive = true, blockSlursOnly = false) shouldBe
            OffensiveWordPolicy.FilterTier.ALL
        OffensiveWordPolicy.tier(blockPossiblyOffensive = true, blockSlursOnly = true) shouldBe
            OffensiveWordPolicy.FilterTier.ALL
        OffensiveWordPolicy.tier(blockPossiblyOffensive = false, blockSlursOnly = true) shouldBe
            OffensiveWordPolicy.FilterTier.SLURS_ONLY
        OffensiveWordPolicy.tier(blockPossiblyOffensive = false, blockSlursOnly = false) shouldBe
            OffensiveWordPolicy.FilterTier.NONE
    }

    test("slurs-only filtering preserves ordinary profanity and exact word boundaries") {
        OffensiveWordPolicy.shouldBlock("a slur", OffensiveWordPolicy.FilterTier.SLURS_ONLY) shouldBe false
        OffensiveWordPolicy.shouldBlock("a known slur word", OffensiveWordPolicy.FilterTier.SLURS_ONLY) shouldBe false
        OffensiveWordPolicy.shouldBlock("chink", OffensiveWordPolicy.FilterTier.SLURS_ONLY) shouldBe true
        OffensiveWordPolicy.shouldBlock("fuck", OffensiveWordPolicy.FilterTier.SLURS_ONLY) shouldBe false
        OffensiveWordPolicy.shouldBlock("assistant", OffensiveWordPolicy.FilterTier.SLURS_ONLY) shouldBe false
        OffensiveWordPolicy.shouldBlock("class", OffensiveWordPolicy.FilterTier.ALL) shouldBe false
    }

    test("all filtering blocks profanity and slurs in punctuation-wrapped phrases") {
        OffensiveWordPolicy.shouldBlock("fuck", OffensiveWordPolicy.FilterTier.ALL) shouldBe true
        OffensiveWordPolicy.shouldBlock("well, shit!", OffensiveWordPolicy.FilterTier.ALL) shouldBe true
        OffensiveWordPolicy.shouldBlock("that slur", OffensiveWordPolicy.FilterTier.ALL) shouldBe false
    }

    test("candidate filtering preserves order and exact word boundaries") {
        val candidates = listOf(
            WordSuggestionCandidate("shit"),
            WordSuggestionCandidate("shuttle"),
            WordSuggestionCandidate("class"),
        )
        OffensiveWordPolicy.filterCandidates(candidates, OffensiveWordPolicy.FilterTier.ALL)
            .map { it.text.toString() } shouldContainExactly listOf("shuttle", "class")

        OffensiveWordPolicy.shouldBlock("shit", OffensiveWordPolicy.FilterTier.ALL) shouldBe true
    }
})
