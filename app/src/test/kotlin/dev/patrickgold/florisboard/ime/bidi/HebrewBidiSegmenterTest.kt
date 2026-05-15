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

package dev.patrickgold.florisboard.ime.bidi

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class HebrewBidiSegmenterTest : FunSpec({

    test("pure Hebrew string segments as one HEBREW run") {
        val runs = HebrewBidiSegmenter.segment("שלום")
        runs.size shouldBe 1
        runs[0].direction shouldBe HebrewBidiSegmenter.Direction.HEBREW
        runs[0].length shouldBe 4
    }

    test("mixed Hebrew + Latin segments into alternating runs") {
        val runs = HebrewBidiSegmenter.segment("שלום world")
        runs.map { it.direction } shouldBe listOf(
            HebrewBidiSegmenter.Direction.HEBREW,
            HebrewBidiSegmenter.Direction.WHITESPACE,
            HebrewBidiSegmenter.Direction.LATIN,
        )
    }

    test("digit-only string is DIGITS run") {
        val runs = HebrewBidiSegmenter.segment("12345")
        runs.size shouldBe 1
        runs[0].direction shouldBe HebrewBidiSegmenter.Direction.DIGITS
    }

    test("punctuation between Hebrew runs is NEUTRAL") {
        val runs = HebrewBidiSegmenter.segment("שלום,עולם")
        runs.map { it.direction } shouldBe listOf(
            HebrewBidiSegmenter.Direction.HEBREW,
            HebrewBidiSegmenter.Direction.NEUTRAL,
            HebrewBidiSegmenter.Direction.HEBREW,
        )
    }

    test("directionBefore returns Hebrew when cursor sits inside a Hebrew run") {
        val text = "שלום world"
        // Cursor index 3 → character at index 2 is Hebrew.
        HebrewBidiSegmenter.directionBefore(text, 3) shouldBe
            HebrewBidiSegmenter.Direction.HEBREW
    }

    test("directionBefore returns NEUTRAL at the start of the string") {
        HebrewBidiSegmenter.directionBefore("שלום", 0) shouldBe
            HebrewBidiSegmenter.Direction.NEUTRAL
    }

    test("dominantDirection picks the longest letter run") {
        // Hebrew run length 4, Latin run length 11.
        HebrewBidiSegmenter.dominantDirection("שלום-cosmopolitan") shouldBe
            HebrewBidiSegmenter.Direction.LATIN
        // Hebrew run length 6, Latin run length 2.
        HebrewBidiSegmenter.dominantDirection("בוקרטוב-ok") shouldBe
            HebrewBidiSegmenter.Direction.HEBREW
    }

    test("empty string yields no runs and a NEUTRAL dominant direction") {
        HebrewBidiSegmenter.segment("") shouldBe emptyList()
        HebrewBidiSegmenter.dominantDirection("") shouldBe
            HebrewBidiSegmenter.Direction.NEUTRAL
    }
})
