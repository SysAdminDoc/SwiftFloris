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
import io.kotest.matchers.shouldBe

class SmartComposeResultFilterTest : FunSpec({

    test("NoSuggestion passes through unchanged") {
        SmartComposeResultFilter.filter(SmartComposeResult.NoSuggestion) shouldBe
            SmartComposeResult.NoSuggestion
    }

    test("low-confidence candidates are dropped") {
        val input = SmartComposeResult.Suggestion(
            listOf(
                SmartComposeCandidate("high", 0.9f, 1),
                SmartComposeCandidate("medium", 0.5f, 1),
                SmartComposeCandidate("noise", 0.1f, 1),
            ),
        )
        val out = SmartComposeResultFilter.filter(input) as SmartComposeResult.Suggestion
        out.candidates.map { it.text } shouldBe listOf("high", "medium")
    }

    test("internal whitespace is collapsed") {
        val input = SmartComposeResult.Suggestion(
            listOf(SmartComposeCandidate("hello   world", 0.9f, 2)),
        )
        val out = SmartComposeResultFilter.filter(input) as SmartComposeResult.Suggestion
        out.candidates.single().text shouldBe "hello world"
    }

    test("blank-after-trim candidates are dropped") {
        val input = SmartComposeResult.Suggestion(
            listOf(
                SmartComposeCandidate("   ", 0.9f, 1),
                SmartComposeCandidate("real", 0.9f, 1),
            ),
        )
        val out = SmartComposeResultFilter.filter(input) as SmartComposeResult.Suggestion
        out.candidates.map { it.text } shouldBe listOf("real")
    }

    test("duplicates collapse to the highest-confidence variant") {
        val input = SmartComposeResult.Suggestion(
            listOf(
                SmartComposeCandidate("world", 0.8f, 1),
                SmartComposeCandidate("world", 0.95f, 1),
                SmartComposeCandidate("there", 0.6f, 1),
            ),
        )
        val out = SmartComposeResultFilter.filter(input) as SmartComposeResult.Suggestion
        out.candidates.map { it.text } shouldBe listOf("world", "there")
        out.candidates.first().confidence shouldBe 0.95f
    }

    test("candidates are sorted by descending confidence") {
        val input = SmartComposeResult.Suggestion(
            listOf(
                SmartComposeCandidate("low", 0.4f, 1),
                SmartComposeCandidate("high", 0.95f, 1),
                SmartComposeCandidate("mid", 0.7f, 1),
            ),
        )
        val out = SmartComposeResultFilter.filter(input) as SmartComposeResult.Suggestion
        out.candidates.map { it.text } shouldBe listOf("high", "mid", "low")
    }

    test("output is clamped to maxCandidates") {
        val input = SmartComposeResult.Suggestion(
            (1..10).map { SmartComposeCandidate("c$it", 0.5f + it * 0.04f, 1) },
        )
        val out = SmartComposeResultFilter.filter(input, maxCandidates = 3)
            as SmartComposeResult.Suggestion
        out.candidates.size shouldBe 3
    }

    test("empty-after-filter downgrades Suggestion → NoSuggestion") {
        val input = SmartComposeResult.Suggestion(
            listOf(
                SmartComposeCandidate("noise1", 0.05f, 1),
                SmartComposeCandidate("noise2", 0.10f, 1),
            ),
        )
        SmartComposeResultFilter.filter(input) shouldBe SmartComposeResult.NoSuggestion
    }

    test("minConfidence outside [0, 1] is rejected") {
        var caught = false
        try {
            SmartComposeResultFilter.filter(SmartComposeResult.NoSuggestion, minConfidence = 1.5f)
        } catch (_: IllegalArgumentException) {
            caught = true
        }
        caught shouldBe true
    }
})
