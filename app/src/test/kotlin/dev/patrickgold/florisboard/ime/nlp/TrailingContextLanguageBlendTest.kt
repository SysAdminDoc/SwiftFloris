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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.math.abs

class TrailingContextLanguageBlendTest : FunSpec({

    fun lookup(table: Map<String, Double>): (String) -> Double = { word ->
        table[word] ?: 0.0
    }

    test("empty context returns zero score") {
        TrailingContextLanguageBlend.score(
            contextWordsOldestFirst = emptyList(),
            freqLookup = { _ -> 1.0 },
        ) shouldBe 0.0
    }

    test("single context word collapses to its raw frequency") {
        TrailingContextLanguageBlend.score(
            contextWordsOldestFirst = listOf("hello"),
            freqLookup = lookup(mapOf("hello" to 0.8)),
        ) shouldBe 0.8
    }

    test("most-recent word weighs 1.0; older words decay by 0.7 per step back") {
        // EN-only context: "the" (oldest), "quick", "brown" (recent).
        // All three have freq 1.0 → blended score = 1.0 regardless of
        // decay factor when every word is in the same locale.
        val score = TrailingContextLanguageBlend.score(
            contextWordsOldestFirst = listOf("the", "quick", "brown"),
            freqLookup = lookup(mapOf("the" to 1.0, "quick" to 1.0, "brown" to 1.0)),
        )
        score shouldBe 1.0
    }

    test("a recent in-locale word outweighs an older out-of-locale word (mid-sentence switch)") {
        // ES locale lookup: "hola" is the only Spanish word.
        // Window oldest→recent: "the" (EN, freq=0), "old" (EN, freq=0),
        // "house" (EN, freq=0), "hola" (ES, freq=0.9).
        // Most-recent word weighs 1.0 → blended should be > 0.45
        // (≈ 0.9 / (1 + 0.7 + 0.49 + 0.343) ≈ 0.347).
        val score = TrailingContextLanguageBlend.score(
            contextWordsOldestFirst = listOf("the", "old", "house", "hola"),
            freqLookup = lookup(mapOf("hola" to 0.9)),
        )
        // The expected weighted average:
        //   (0 * 0.343 + 0 * 0.49 + 0 * 0.7 + 0.9 * 1.0)
        //   / (0.343 + 0.49 + 0.7 + 1.0)
        //   = 0.9 / 2.533
        //   ≈ 0.3553
        abs(score - (0.9 / (1.0 + 0.7 + 0.49 + 0.343))) shouldBeLessThan 1e-6
    }

    test("an older in-locale word matters less than the recent out-of-locale window") {
        // ES locale lookup: "hola" is the only Spanish word, at the
        // OLDEST position. Window oldest→recent: "hola" (ES, freq=0.9),
        // "the" (EN, 0), "old" (EN, 0), "house" (EN, 0).
        val score = TrailingContextLanguageBlend.score(
            contextWordsOldestFirst = listOf("hola", "the", "old", "house"),
            freqLookup = lookup(mapOf("hola" to 0.9)),
        )
        // Position 3 (oldest) has weight 0.7^3 = 0.343.
        // weighted = 0.9 * 0.343 = 0.3087
        // weightSum = 1 + 0.7 + 0.49 + 0.343 = 2.533
        // score ≈ 0.1219
        abs(score - (0.9 * 0.343 / 2.533)) shouldBeLessThan 1e-3
        // And the recent-Spanish case (previous test) MUST score
        // strictly higher than this oldest-Spanish case — that's the
        // point of B4.
        val recentSpanish = TrailingContextLanguageBlend.score(
            contextWordsOldestFirst = listOf("the", "old", "house", "hola"),
            freqLookup = lookup(mapOf("hola" to 0.9)),
        )
        recentSpanish shouldBeGreaterThan score
    }

    test("decay=1.0 collapses to a flat arithmetic mean") {
        val score = TrailingContextLanguageBlend.score(
            contextWordsOldestFirst = listOf("a", "b", "c", "d"),
            freqLookup = lookup(mapOf("a" to 0.0, "b" to 0.0, "c" to 0.0, "d" to 1.0)),
            decay = 1.0,
        )
        score shouldBe 0.25
    }

    test("decay=0.0 collapses to 'only the most recent word counts'") {
        val score = TrailingContextLanguageBlend.score(
            contextWordsOldestFirst = listOf("a", "b", "c", "d"),
            freqLookup = lookup(mapOf("a" to 1.0, "b" to 1.0, "c" to 1.0, "d" to 0.42)),
            decay = 0.0,
        )
        score shouldBe 0.42
    }

    test("rejects decay outside [0.0, 1.0]") {
        shouldThrow<IllegalArgumentException> {
            TrailingContextLanguageBlend.score(
                contextWordsOldestFirst = listOf("x"),
                freqLookup = { 1.0 },
                decay = 1.5,
            )
        }
        shouldThrow<IllegalArgumentException> {
            TrailingContextLanguageBlend.score(
                contextWordsOldestFirst = listOf("x"),
                freqLookup = { 1.0 },
                decay = -0.1,
            )
        }
    }

    test("regression vs. previous MAX behaviour: a single ES word in a 4-word EN window no longer dominates") {
        // Scenario: 4-word window, 3 EN words + 1 ES word. The
        // previous MAX scoring would surface a score of 1.0 for ES
        // because of the single matching word. The B4 weighted blend
        // should report a meaningfully lower number (because the EN
        // words are recent and the ES word is oldest), reflecting
        // that the sentence has mostly drifted out of Spanish.
        val esBlended = TrailingContextLanguageBlend.score(
            contextWordsOldestFirst = listOf("hola", "the", "quick", "brown"),
            freqLookup = lookup(mapOf("hola" to 1.0)),
        )
        // ES weighted should be strictly less than the legacy MAX
        // (1.0) — that's the entire point of the B4 fix.
        esBlended shouldBeLessThan 1.0
        // And much less than 0.5 (it's pulling against three recent
        // non-Spanish words).
        esBlended shouldBeLessThan 0.5
    }
})
