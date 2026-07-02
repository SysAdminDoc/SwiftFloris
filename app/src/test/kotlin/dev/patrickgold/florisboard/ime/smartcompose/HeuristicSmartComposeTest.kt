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

package dev.patrickgold.florisboard.ime.smartcompose

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * SmartCompose feature contract F18 — pure-core coverage for the heuristic
 * ghost-text ranking + confidence model, with no Android / coroutine
 * dependency.
 */
class HeuristicSmartComposeTest : FunSpec({

    test("lastTwoWords returns the two trailing words") {
        HeuristicSmartCompose.lastTwoWords("I am on my") shouldBe ("on" to "my")
    }

    test("lastTwoWords tolerates trailing whitespace") {
        HeuristicSmartCompose.lastTwoWords("see you  ") shouldBe ("see" to "you")
    }

    test("lastTwoWords with a single word has only prev1") {
        HeuristicSmartCompose.lastTwoWords("hello") shouldBe (null to "hello")
    }

    test("lastTwoWords on blank input has no context") {
        HeuristicSmartCompose.lastTwoWords("   ") shouldBe (null to null)
        HeuristicSmartCompose.lastTwoWords("") shouldBe (null to null)
    }

    test("trigram tier clears the 0.45 ghost-text gate, cold-start does not") {
        HeuristicSmartCompose.confidenceFor(HeuristicTier.TRIGRAM, 0) shouldBeGreaterThanOrEqual 0.45f
        HeuristicSmartCompose.confidenceFor(HeuristicTier.BIGRAM, 0) shouldBeGreaterThanOrEqual 0.45f
        HeuristicSmartCompose.confidenceFor(HeuristicTier.COLD_START, 0) shouldBeLessThan 0.45f
    }

    test("confidence decays with rank and stays in (0, 1]") {
        val first = HeuristicSmartCompose.confidenceFor(HeuristicTier.TRIGRAM, 0)
        val second = HeuristicSmartCompose.confidenceFor(HeuristicTier.TRIGRAM, 1)
        (second < first) shouldBe true
        HeuristicSmartCompose.confidenceFor(HeuristicTier.COLD_START, 99) shouldBeGreaterThanOrEqual 0.05f
    }

    test("buildResult prefers the trigram tier when present") {
        val result = HeuristicSmartCompose.buildResult(
            trigram = listOf("brown"),
            bigram = listOf("dog"),
            coldStart = listOf("the"),
            maxCandidates = 3,
        ).shouldBeInstanceOf<SmartComposeResult.Suggestion>()
        result.candidates.single().text shouldBe "brown"
        result.candidates.single().confidence shouldBeGreaterThanOrEqual 0.45f
    }

    test("buildResult falls back to the bigram tier") {
        val result = HeuristicSmartCompose.buildResult(
            trigram = emptyList(),
            bigram = listOf("dog", "cat"),
            coldStart = listOf("the"),
            maxCandidates = 3,
        ).shouldBeInstanceOf<SmartComposeResult.Suggestion>()
        result.candidates.first().text shouldBe "dog"
        result.candidates.first().confidence shouldBeGreaterThanOrEqual 0.45f
    }

    test("buildResult falls back to cold-start priors below the gate") {
        val result = HeuristicSmartCompose.buildResult(
            trigram = emptyList(),
            bigram = emptyList(),
            coldStart = listOf("the"),
            maxCandidates = 3,
        ).shouldBeInstanceOf<SmartComposeResult.Suggestion>()
        result.candidates.single().text shouldBe "the"
        result.candidates.single().confidence shouldBeLessThan 0.45f
    }

    test("buildResult returns NoSuggestion when every tier is empty") {
        HeuristicSmartCompose.buildResult(
            emptyList(), emptyList(), emptyList(), maxCandidates = 3,
        ) shouldBe SmartComposeResult.NoSuggestion
    }

    test("buildResult honours maxCandidates") {
        val result = HeuristicSmartCompose.buildResult(
            trigram = listOf("a", "b", "c", "d"),
            bigram = emptyList(),
            coldStart = emptyList(),
            maxCandidates = 2,
        ).shouldBeInstanceOf<SmartComposeResult.Suggestion>()
        result.candidates.size shouldBe 2
    }
})
