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

class YiddishBidiSegmenterTest : FunSpec({

    test("Yiddish DOUBLE VAV digraph U+05F0 is HEBREW direction") {
        YiddishBidiSegmenter.classify(0x05F0) shouldBe
            HebrewBidiSegmenter.Direction.HEBREW
    }

    test("isYiddishDigraph picks the three Yiddish-only code points") {
        YiddishBidiSegmenter.isYiddishDigraph(0x05F0) shouldBe true  // DOUBLE VAV ױ→וו precursor
        YiddishBidiSegmenter.isYiddishDigraph(0x05F1) shouldBe true  // VAV YOD ױ
        YiddishBidiSegmenter.isYiddishDigraph(0x05F2) shouldBe true  // DOUBLE YOD ײ
        YiddishBidiSegmenter.isYiddishDigraph(0x05E9) shouldBe false // Hebrew SHIN (not Yiddish-only)
    }

    test("yiddishDigraphCount counts only the Yiddish-only digraphs") {
        val text = "\u05F0\u05F1\u05D0\u05F2"  // 4 chars: 3 Yiddish digraphs + 1 alef
        YiddishBidiSegmenter.yiddishDigraphCount(text) shouldBe 3
    }

    test("mixed Yiddish + Latin text segments into alternating runs") {
        val runs = YiddishBidiSegmenter.segment("\u05D0\u05F0 word")
        runs.map { it.direction } shouldBe listOf(
            HebrewBidiSegmenter.Direction.HEBREW,
            HebrewBidiSegmenter.Direction.WHITESPACE,
            HebrewBidiSegmenter.Direction.LATIN,
        )
    }

    test("pure Yiddish without Latin segments as one HEBREW run") {
        val text = "\u05D0\u05F0\u05F2"  // alef + Yiddish double vav + Yiddish double yod
        val runs = YiddishBidiSegmenter.segment(text)
        runs.size shouldBe 1
        runs[0].direction shouldBe HebrewBidiSegmenter.Direction.HEBREW
    }

    test("empty text yields empty run list and zero digraph count") {
        YiddishBidiSegmenter.segment("") shouldBe emptyList()
        YiddishBidiSegmenter.yiddishDigraphCount("") shouldBe 0
    }
})
