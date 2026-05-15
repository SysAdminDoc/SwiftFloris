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

class IndicScriptExtendedTest : FunSpec({
    test("Malayalam table maps 'k' to ക and digit 7 to ൭") {
        val xlit = IndicTransliterator(IndicScriptTable.ItransToMalayalam)
        xlit.transliterate("k") shouldBe "ക"
        xlit.transliterate("7") shouldBe "൭"
    }

    test("Malayalam supports the chillu-precursor 'L' → ള") {
        val xlit = IndicTransliterator(IndicScriptTable.ItransToMalayalam)
        xlit.transliterate("L") shouldBe "ള"
    }

    test("Odia table maps 'p' to ପ and digit 0 to ୦") {
        val xlit = IndicTransliterator(IndicScriptTable.ItransToOdia)
        xlit.transliterate("p") shouldBe "ପ"
        xlit.transliterate("0") shouldBe "୦"
    }

    test("Sinhala table maps 'm' to ම and 'sh' to ශ") {
        val xlit = IndicTransliterator(IndicScriptTable.ItransToSinhala)
        xlit.transliterate("m") shouldBe "ම"
        xlit.transliterate("sh") shouldBe "ශ"
    }

    test("Sinhala digits stay Western (no script-native Unicode digits)") {
        val xlit = IndicTransliterator(IndicScriptTable.ItransToSinhala)
        xlit.transliterate("0123456789") shouldBe "0123456789"
    }

    test("every newly-shipped Indic table reports a sane size") {
        val tables = listOf(
            IndicScriptTable.ItransToMalayalam,
            IndicScriptTable.ItransToOdia,
            IndicScriptTable.ItransToSinhala,
        )
        tables.forEach { (it.size() > 30) shouldBe true }
    }
})
