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

package dev.patrickgold.florisboard.ime.indic

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class IndicTransliteratorTest : FunSpec({
    val xlit = IndicTransliterator(IndicScriptTable.ItransToDevanagari)

    test("table is loaded and exposes a sane size + max key length") {
        (IndicScriptTable.ItransToDevanagari.size() > 40) shouldBe true
        (IndicScriptTable.ItransToDevanagari.maxKeyLength >= 2) shouldBe true
    }

    test("vowels transliterate to their Devanagari independent forms") {
        xlit.transliterate("a") shouldBe "अ"
        xlit.transliterate("aa") shouldBe "आ"
        xlit.transliterate("i") shouldBe "इ"
        xlit.transliterate("ii") shouldBe "ई"
    }

    test("greedy longest-match prefers 'aa' over 'a' twice") {
        // If the greedy match worked correctly, "aa" produces ONE
        // character (आ) rather than two (अअ).
        xlit.transliterate("aa") shouldBe "आ"
    }

    test("Latin consonants map to single Devanagari consonants") {
        xlit.transliterate("k") shouldBe "क"
        xlit.transliterate("kh") shouldBe "ख"
        xlit.transliterate("g") shouldBe "ग"
        xlit.transliterate("n") shouldBe "न"
        xlit.transliterate("h") shouldBe "ह"
    }

    test("digits round-trip to Devanagari numerals") {
        xlit.transliterate("0123456789") shouldBe "०१२३४५६७८९"
    }

    test("unknown characters pass through unchanged") {
        // ASCII space + Latin punctuation falls through.
        xlit.transliterate("k & b") shouldBe "क & ब"
    }

    test("empty input returns empty output") {
        xlit.transliterate("") shouldBe ""
    }

    test("danda + double-danda Devanagari punctuation map correctly") {
        xlit.transliterate("|") shouldBe "।"
        xlit.transliterate("||") shouldBe "॥"
    }
})
