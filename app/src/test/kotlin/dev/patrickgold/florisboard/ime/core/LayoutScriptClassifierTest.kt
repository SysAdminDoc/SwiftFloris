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

package dev.patrickgold.florisboard.ime.core

import dev.patrickgold.florisboard.ime.keyboard.extCoreComposer
import dev.patrickgold.florisboard.ime.keyboard.extCoreCurrencySet
import dev.patrickgold.florisboard.lib.FlorisLocale
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private fun classify(tag: String): LayoutScript =
    LayoutScriptClassifier.classify(FlorisLocale.fromTag(tag))

private fun preset(tag: String): SubtypePreset = SubtypePreset(
    locale = FlorisLocale.fromTag(tag),
    composer = extCoreComposer("default"),
    currencySet = extCoreCurrencySet("dollar"),
    preferred = SubtypeLayoutMap(),
)

class LayoutScriptClassifierTest : FunSpec({

    test("classify maps Western European Latin codes to LATIN") {
        for (tag in listOf("en", "de", "fr", "es", "it", "pt", "nl", "sv", "no", "da", "fi", "is", "pl", "cs",
            "sk", "hu", "ro", "hr", "sl", "tr", "vi", "id", "ms", "sw", "af")) {
            classify(tag) shouldBe LayoutScript.LATIN
        }
    }

    test("classify maps Cyrillic-script codes to CYRILLIC") {
        for (tag in listOf("ru", "uk", "be", "bg", "mk", "sr", "mn")) {
            classify(tag) shouldBe LayoutScript.CYRILLIC
        }
    }

    test("classify maps Hebrew + Yiddish + Ladino to HEBREW") {
        classify("he") shouldBe LayoutScript.HEBREW
        classify("iw") shouldBe LayoutScript.HEBREW  // legacy code
        classify("yi") shouldBe LayoutScript.HEBREW
        classify("lad") shouldBe LayoutScript.HEBREW
    }

    test("classify maps Arabic-script languages to ARABIC") {
        for (tag in listOf("ar", "fa", "ur", "ps", "sd", "ku", "ks", "ug")) {
            classify(tag) shouldBe LayoutScript.ARABIC
        }
    }

    test("classify maps Indic languages to their correct sub-script") {
        classify("hi") shouldBe LayoutScript.DEVANAGARI
        classify("mr") shouldBe LayoutScript.DEVANAGARI
        classify("ne") shouldBe LayoutScript.DEVANAGARI
        classify("sa") shouldBe LayoutScript.DEVANAGARI
        classify("bn") shouldBe LayoutScript.BENGALI
        classify("as") shouldBe LayoutScript.BENGALI
        classify("pa") shouldBe LayoutScript.GURMUKHI
        classify("gu") shouldBe LayoutScript.GUJARATI
        classify("ta") shouldBe LayoutScript.TAMIL
        classify("te") shouldBe LayoutScript.TELUGU
        classify("kn") shouldBe LayoutScript.KANNADA
        classify("ml") shouldBe LayoutScript.MALAYALAM
        classify("or") shouldBe LayoutScript.ODIA
        classify("si") shouldBe LayoutScript.SINHALA
    }

    test("classify maps SE-Asian Brahmic languages to their correct sub-script") {
        classify("th") shouldBe LayoutScript.THAI
        classify("lo") shouldBe LayoutScript.LAO
        classify("km") shouldBe LayoutScript.KHMER
        classify("my") shouldBe LayoutScript.BURMESE
        classify("bo") shouldBe LayoutScript.TIBETAN
        classify("dz") shouldBe LayoutScript.TIBETAN
    }

    test("classify maps CJK + Japanese + Korean to their own buckets") {
        classify("zh") shouldBe LayoutScript.CJK
        classify("yue") shouldBe LayoutScript.CJK
        classify("ja") shouldBe LayoutScript.JAPANESE
        classify("ko") shouldBe LayoutScript.KOREAN_HANGUL
    }

    test("classify maps Ethiopic languages to ETHIOPIC") {
        classify("am") shouldBe LayoutScript.ETHIOPIC
        classify("ti") shouldBe LayoutScript.ETHIOPIC
    }

    test("classify maps Cherokee to CHEROKEE") {
        classify("chr") shouldBe LayoutScript.CHEROKEE
    }

    test("classify falls back to OTHER for unknown codes") {
        classify("xx") shouldBe LayoutScript.OTHER
        classify("zz") shouldBe LayoutScript.OTHER
        classify("qqq") shouldBe LayoutScript.OTHER
    }

    test("classify is case-insensitive on language code") {
        classify("EN") shouldBe LayoutScript.LATIN
        classify("Ru") shouldBe LayoutScript.CYRILLIC
        classify("ZH") shouldBe LayoutScript.CJK
    }

    test("groupByScript groups presets in insertion order with per-script preset order preserved") {
        val presets = listOf(
            preset("en"),
            preset("ru"),
            preset("de"),
            preset("uk"),
            preset("ja"),
            preset("zh"),
            preset("fr"),
        )

        val grouped = LayoutScriptClassifier.groupByScript(presets)

        grouped.keys.toList() shouldBe listOf(
            LayoutScript.LATIN,
            LayoutScript.CYRILLIC,
            LayoutScript.JAPANESE,
            LayoutScript.CJK,
        )
        grouped[LayoutScript.LATIN] shouldBe listOf(presets[0], presets[2], presets[6])
        grouped[LayoutScript.CYRILLIC] shouldBe listOf(presets[1], presets[3])
    }

    test("groupByScript collects unknown locales under OTHER without losing them") {
        val presets = listOf(
            preset("en"),
            preset("xx"),
            preset("zz"),
        )
        val grouped = LayoutScriptClassifier.groupByScript(presets)

        grouped[LayoutScript.OTHER] shouldBe listOf(presets[1], presets[2])
    }
})
