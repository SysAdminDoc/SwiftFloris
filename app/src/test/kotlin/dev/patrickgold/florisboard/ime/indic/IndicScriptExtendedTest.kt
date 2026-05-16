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

    test("Burmese table maps 'k' to က and digit 5 to ၅") {
        val xlit = IndicTransliterator(IndicScriptTable.ItransToBurmese)
        xlit.transliterate("k") shouldBe "က"
        xlit.transliterate("5") shouldBe "၅"
    }

    test("Lao table maps 'k' to ກ and digit 9 to ໙") {
        val xlit = IndicTransliterator(IndicScriptTable.ItransToLao)
        xlit.transliterate("k") shouldBe "ກ"
        xlit.transliterate("9") shouldBe "໙"
    }

    test("Tibetan table maps 'k' to ཀ and digit 0 to ༠") {
        val xlit = IndicTransliterator(IndicScriptTable.ItransToTibetan)
        xlit.transliterate("k") shouldBe "ཀ"
        xlit.transliterate("0") shouldBe "༠"
    }

    test("Tibetan two-letter ASCII digraphs win over the single-char prefix (greedy match)") {
        val xlit = IndicTransliterator(IndicScriptTable.ItransToTibetan)
        // 'ng' must produce ང, not nga / n+g, because greedy longest match.
        xlit.transliterate("ng") shouldBe "ང"
    }

    test("Khmer table maps 'k' to ក and digit 7 to ៧") {
        val xlit = IndicTransliterator(IndicScriptTable.ItransToKhmer)
        xlit.transliterate("k") shouldBe "ក"
        xlit.transliterate("7") shouldBe "៧"
    }

    test("Thai table maps 'k' to ก and digit 3 to ๓") {
        val xlit = IndicTransliterator(IndicScriptTable.ItransToThai)
        xlit.transliterate("k") shouldBe "ก"
        xlit.transliterate("3") shouldBe "๓"
    }

    test("Khmer two-letter ASCII digraph 'ng' wins over 'n'+'g' (greedy match)") {
        val xlit = IndicTransliterator(IndicScriptTable.ItransToKhmer)
        xlit.transliterate("ng") shouldBe "ង"
    }

    test("Khmer + Thai tables both report sane sizes") {
        val tables = listOf(IndicScriptTable.ItransToKhmer, IndicScriptTable.ItransToThai)
        tables.forEach { (it.size() > 25) shouldBe true }
    }

    test("Mongolian table maps 'a' to ᠠ and digit 5 to ᠕") {
        val xlit = IndicTransliterator(IndicScriptTable.LatinToMongolian)
        xlit.transliterate("a") shouldBe "ᠠ"
        xlit.transliterate("5") shouldBe "᠕"
    }

    test("Javanese table maps 'k' to ꦏ and digit 7 to ꧗") {
        val xlit = IndicTransliterator(IndicScriptTable.ItransToJavanese)
        xlit.transliterate("k") shouldBe "ꦏ"
        xlit.transliterate("7") shouldBe "꧗"
    }

    test("Sundanese table maps 'k' to ᮊ and digit 0 to ᮰") {
        val xlit = IndicTransliterator(IndicScriptTable.ItransToSundanese)
        xlit.transliterate("k") shouldBe "ᮊ"
        xlit.transliterate("0") shouldBe "᮰"
    }

    test("Adlam table maps 'b' to 𞤦 and digit 4 to 𞥔") {
        val xlit = IndicTransliterator(IndicScriptTable.LatinToAdlam)
        xlit.transliterate("b") shouldBe "𞤦"
        xlit.transliterate("4") shouldBe "𞥔"
    }

    test("NKo table maps 'b' to ߓ and digit 2 to ߂") {
        val xlit = IndicTransliterator(IndicScriptTable.LatinToNKo)
        xlit.transliterate("b") shouldBe "ߓ"
        xlit.transliterate("2") shouldBe "߂"
    }

    test("Cherokee table maps the romanised syllable 'tla' to the single Cherokee glyph Ꮬ") {
        val xlit = IndicTransliterator(IndicScriptTable.LatinToCherokee)
        xlit.transliterate("tla") shouldBe "Ꮬ"
        xlit.transliterate("a") shouldBe "Ꭰ"
    }

    test("Cherokee 'qua' wins greedy over 'q'+rest (longest-match)") {
        val xlit = IndicTransliterator(IndicScriptTable.LatinToCherokee)
        xlit.transliterate("qua") shouldBe "Ꮖ"
    }

    test("Coptic table maps 'a' to ⲁ and 'sh' to the Coptic-extra ϣ") {
        val xlit = IndicTransliterator(IndicScriptTable.LatinToCoptic)
        xlit.transliterate("a") shouldBe "ⲁ"
        xlit.transliterate("sh") shouldBe "ϣ"
    }

    test("Georgian Mkhedruli table maps 'a' to ა and case-sensitive 'T' to თ") {
        val xlit = IndicTransliterator(IndicScriptTable.LatinToGeorgian)
        xlit.transliterate("a") shouldBe "ა"
        xlit.transliterate("T") shouldBe "თ"
    }

    test("Glagolitic table maps 'a' to ⰰ and 'sh' to ⱎ (greedy digraph)") {
        val xlit = IndicTransliterator(IndicScriptTable.LatinToGlagolitic)
        xlit.transliterate("a") shouldBe "ⰰ"
        xlit.transliterate("sh") shouldBe "ⱎ"
    }

    test("Samaritan table maps 'a' to ࠀ and 'sh' to ࠔ") {
        val xlit = IndicTransliterator(IndicScriptTable.LatinToSamaritan)
        xlit.transliterate("a") shouldBe "ࠀ"
        xlit.transliterate("sh") shouldBe "ࠔ"
    }

    test("Mandaic table maps 'a' to ࡀ and 'sh' to ࡔ") {
        val xlit = IndicTransliterator(IndicScriptTable.LatinToMandaic)
        xlit.transliterate("a") shouldBe "ࡀ"
        xlit.transliterate("sh") shouldBe "ࡔ"
    }

    test("Old Permic table maps 'a' to 𐍐 (supplementary plane round-trip)") {
        val xlit = IndicTransliterator(IndicScriptTable.LatinToOldPermic)
        xlit.transliterate("a") shouldBe "𐍐"
        xlit.transliterate("dz") shouldBe "𐍗"
    }

    test("Phoenician table maps 'a' to 𐤀 and 'sh' to 𐤔 (supplementary plane)") {
        val xlit = IndicTransliterator(IndicScriptTable.LatinToPhoenician)
        xlit.transliterate("a") shouldBe "𐤀"
        xlit.transliterate("sh") shouldBe "𐤔"
    }

    test("Imperial Aramaic table maps 'a' to 𐡀 and 'sh' to 𐡔") {
        val xlit = IndicTransliterator(IndicScriptTable.LatinToImperialAramaic)
        xlit.transliterate("a") shouldBe "𐡀"
        xlit.transliterate("sh") shouldBe "𐡔"
    }

    test("Avestan table maps 'a' to 𐬀 and aspirated 'kh' to 𐬑") {
        val xlit = IndicTransliterator(IndicScriptTable.LatinToAvestan)
        xlit.transliterate("a") shouldBe "𐬀"
        xlit.transliterate("kh") shouldBe "𐬑"
    }

    test("Carian table maps 'a' to 𐊠 (supplementary plane)") {
        val xlit = IndicTransliterator(IndicScriptTable.LatinToCarian)
        xlit.transliterate("a") shouldBe "𐊠"
    }

    test("Lycian table maps 'a' to 𐊀 and aspirated 'th' to 𐊈") {
        val xlit = IndicTransliterator(IndicScriptTable.LatinToLycian)
        xlit.transliterate("a") shouldBe "𐊀"
        xlit.transliterate("th") shouldBe "𐊈"
    }

    test("Lydian table maps 'a' to 𐤠 and 'ng' digraph to 𐤵 (greedy match)") {
        val xlit = IndicTransliterator(IndicScriptTable.LatinToLydian)
        xlit.transliterate("a") shouldBe "𐤠"
        xlit.transliterate("ng") shouldBe "𐤵"
    }

    test("Caucasian Albanian table maps 'a' to 𐔰 and 'sh' to 𐕓") {
        val xlit = IndicTransliterator(IndicScriptTable.LatinToCaucasianAlbanian)
        xlit.transliterate("a") shouldBe "𐔰"
        xlit.transliterate("sh") shouldBe "𐕓"
    }

    test("Elbasan table maps 'a' to 𐔀 (supplementary plane)") {
        val xlit = IndicTransliterator(IndicScriptTable.LatinToElbasan)
        xlit.transliterate("a") shouldBe "𐔀"
    }

    test("Vai syllabary maps 'pa' to ꕒ and longest-match wins over 'p' alone") {
        val xlit = IndicTransliterator(IndicScriptTable.LatinToVai)
        xlit.transliterate("pa") shouldBe "ꕒ"
    }

    test("Caucasian Albanian + Elbasan + Vai tables all report sane sizes") {
        val tables = listOf(
            IndicScriptTable.LatinToCaucasianAlbanian,
            IndicScriptTable.LatinToElbasan,
            IndicScriptTable.LatinToVai,
        )
        tables.forEach { (it.size() > 18) shouldBe true }
    }

    test("Carian + Lycian + Lydian tables all report sane sizes") {
        val tables = listOf(
            IndicScriptTable.LatinToCarian,
            IndicScriptTable.LatinToLycian,
            IndicScriptTable.LatinToLydian,
        )
        tables.forEach { (it.size() > 18) shouldBe true }
    }

    test("Phoenician + Imperial Aramaic + Avestan tables all report sane sizes") {
        val tables = listOf(
            IndicScriptTable.LatinToPhoenician,
            IndicScriptTable.LatinToImperialAramaic,
            IndicScriptTable.LatinToAvestan,
        )
        tables.forEach { (it.size() > 18) shouldBe true }
    }

    test("Samaritan + Mandaic + Old Permic tables all report sane sizes") {
        val tables = listOf(
            IndicScriptTable.LatinToSamaritan,
            IndicScriptTable.LatinToMandaic,
            IndicScriptTable.LatinToOldPermic,
        )
        tables.forEach { (it.size() > 18) shouldBe true }
    }

    test("Coptic + Georgian + Glagolitic tables all report sane sizes") {
        val tables = listOf(
            IndicScriptTable.LatinToCoptic,
            IndicScriptTable.LatinToGeorgian,
            IndicScriptTable.LatinToGlagolitic,
        )
        tables.forEach { (it.size() > 20) shouldBe true }
    }

    test("Adlam + NKo + Cherokee tables all report sane sizes") {
        val tables = listOf(
            IndicScriptTable.LatinToAdlam,
            IndicScriptTable.LatinToNKo,
            IndicScriptTable.LatinToCherokee,
        )
        tables.forEach { (it.size() > 20) shouldBe true }
    }

    test("Mongolian + Javanese + Sundanese tables all report sane sizes") {
        val tables = listOf(
            IndicScriptTable.LatinToMongolian,
            IndicScriptTable.ItransToJavanese,
            IndicScriptTable.ItransToSundanese,
        )
        tables.forEach { (it.size() > 20) shouldBe true }
    }

    test("Burmese + Lao + Tibetan tables all report sane sizes") {
        val tables = listOf(
            IndicScriptTable.ItransToBurmese,
            IndicScriptTable.ItransToLao,
            IndicScriptTable.ItransToTibetan,
        )
        tables.forEach { (it.size() > 25) shouldBe true }
    }
})
