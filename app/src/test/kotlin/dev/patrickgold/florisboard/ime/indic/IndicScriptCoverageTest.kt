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

class IndicScriptCoverageTest : FunSpec({
    test("Bengali table maps 'k' to ক and digit 7 to ৭") {
        val xlit = IndicTransliterator(IndicScriptTable.ItransToBengali)
        xlit.transliterate("k") shouldBe "ক"
        xlit.transliterate("7") shouldBe "৭"
    }

    test("Tamil table maps 'k' to க, 'ng' to ங, 'L' to ள") {
        val xlit = IndicTransliterator(IndicScriptTable.ItransToTamil)
        xlit.transliterate("k") shouldBe "க"
        xlit.transliterate("ng") shouldBe "ங"
        xlit.transliterate("L") shouldBe "ள"
    }

    test("Telugu table maps 'p' to ప and digit 5 to ౫") {
        val xlit = IndicTransliterator(IndicScriptTable.ItransToTelugu)
        xlit.transliterate("p") shouldBe "ప"
        xlit.transliterate("5") shouldBe "౫"
    }

    test("Gujarati table maps 'm' to મ and digit 0 to ૦") {
        val xlit = IndicTransliterator(IndicScriptTable.ItransToGujarati)
        xlit.transliterate("m") shouldBe "મ"
        xlit.transliterate("0") shouldBe "૦"
    }

    test("Gurmukhi table maps 'b' to ਬ and 'sh' to ਸ਼") {
        val xlit = IndicTransliterator(IndicScriptTable.ItransToGurmukhi)
        xlit.transliterate("b") shouldBe "ਬ"
        xlit.transliterate("sh") shouldBe "ਸ਼"
    }

    test("Kannada table maps 'r' to ರ and digit 9 to ೯") {
        val xlit = IndicTransliterator(IndicScriptTable.ItransToKannada)
        xlit.transliterate("r") shouldBe "ರ"
        xlit.transliterate("9") shouldBe "೯"
    }

    test("every Indic table reports a non-empty mappings + sane max key length") {
        val tables = listOf(
            IndicScriptTable.ItransToDevanagari,
            IndicScriptTable.ItransToBengali,
            IndicScriptTable.ItransToTamil,
            IndicScriptTable.ItransToTelugu,
            IndicScriptTable.ItransToGujarati,
            IndicScriptTable.ItransToGurmukhi,
            IndicScriptTable.ItransToKannada,
        )
        tables.forEach { table ->
            (table.size() > 30) shouldBe true
            (table.maxKeyLength >= 2) shouldBe true
        }
    }
})
