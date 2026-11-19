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

package dev.patrickgold.florisboard.app.setup

import dev.patrickgold.florisboard.ime.core.SubtypePreset
import dev.patrickgold.florisboard.ime.keyboard.KeyboardExtension
import dev.patrickgold.florisboard.lib.FlorisLocale
import dev.patrickgold.florisboard.lib.ext.ExtensionJsonConfig
import dev.patrickgold.florisboard.lib.io.loadJsonAsset
import dev.patrickgold.florisboard.lib.util.foldForSearch
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import java.io.File
import java.util.Locale

/** Matches phone languages against the preset catalog the app ships, so a catalog change that breaks one shows. */
class FirstRunLanguagePolicyTest : FunSpec({
    val presets = bundledPresets()

    fun tagOf(locale: Locale): String? = FirstRunLanguagePolicy.bestMatch(locale, presets)?.locale?.languageTag()

    test("the phone's country picks that country's preset") {
        tagOf(Locale.forLanguageTag("pt-BR")) shouldBe "pt-BR"
        tagOf(Locale.forLanguageTag("pt-PT")) shouldBe "pt-PT"
        tagOf(Locale.forLanguageTag("de-DE")) shouldBe "de-DE"
        tagOf(Locale.forLanguageTag("de-AT")) shouldBe "de-AT"
        tagOf(Locale.forLanguageTag("es-ES")) shouldBe "es-ES"
        tagOf(Locale.forLanguageTag("es-US")) shouldBe "es-US"
        tagOf(Locale.forLanguageTag("en-US")) shouldBe "en-US"
    }

    test("a country the catalog spells another way or groups under a region still finds its preset") {
        tagOf(Locale.forLanguageTag("en-GB")) shouldBe "en-UK"
        tagOf(Locale.forLanguageTag("es-MX")) shouldBe "es-419"
        tagOf(Locale.forLanguageTag("es-AR")) shouldBe "es-419"
        // Android's own "Latin American Spanish" setting.
        tagOf(Locale.forLanguageTag("es-419")) shouldBe "es-419"
        tagOf(Locale.forLanguageTag("es-GQ")) shouldBe "es-ES"
        // The only Japanese preset is the JIS layout, which carries a variant.
        tagOf(Locale.forLanguageTag("ja-JP")) shouldBe "ja-JP-jis"
    }

    test("a country without its own preset gets the language's preset without a country, else its first one") {
        tagOf(Locale.forLanguageTag("ar-EG")) shouldBe "ar"
        tagOf(Locale.forLanguageTag("ar-SA")) shouldBe "ar"
        tagOf(Locale.forLanguageTag("de-LU")) shouldBe "de-DE"
        tagOf(Locale.forLanguageTag("pt")) shouldBe "pt-PT"
        tagOf(Locale.forLanguageTag("th-TH")) shouldBe "th-KD"
    }

    test("English without a preset of its own gets the US or British one, by how the country spells") {
        tagOf(Locale.forLanguageTag("en-IN")) shouldBe "en-UK"
        tagOf(Locale.forLanguageTag("en-IE")) shouldBe "en-UK"
        tagOf(Locale.forLanguageTag("en-NZ")) shouldBe "en-UK"
        tagOf(Locale.forLanguageTag("en-PH")) shouldBe "en-US"
        tagOf(Locale.forLanguageTag("en")) shouldBe "en-US"
        tagOf(Locale.forLanguageTag("en-CA")) shouldBe "en-CA"
        tagOf(Locale.forLanguageTag("en-AU")) shouldBe "en-AU"
    }

    test("a Korean phone gets the standard Korean layout rather than the phonetic ko-KR preset") {
        val korean = FirstRunLanguagePolicy.bestMatch(Locale.forLanguageTag("ko-KR"), presets)!!
        korean.locale.languageTag() shouldBe "ko"
        korean.preferred.characters.componentId shouldBe "korean"
    }

    test("Hebrew matches the catalog's iw-IL preset under either code") {
        val hebrew = presets.single { it.locale == FlorisLocale.fromTag("iw-IL") }
        FirstRunLanguagePolicy.bestMatch(Locale.forLanguageTag("he-IL"), presets) shouldBe hebrew
        FirstRunLanguagePolicy.bestMatch(Locale.forLanguageTag("iw-IL"), presets) shouldBe hebrew
    }

    test("Chinese gets no suggestion, since the one Chinese preset is the little-used Zhengma method") {
        tagOf(Locale.forLanguageTag("zh-CN")).shouldBeNull()
        tagOf(Locale.forLanguageTag("zh-Hans-SG")).shouldBeNull()
        // It's still there to search for.
        FirstRunLanguagePolicy.search("zhengma", presets) { FirstRunLanguagePolicy.searchNames(it, "") }
            .map { it.locale.languageTag() } shouldContainExactly listOf("zh-CN-zhengma")
    }

    test("a Simplified Chinese phone is told Zhengma is the one Chinese keyboard, rather than that there's none") {
        fun uncommonTag(tag: String): String? {
            return FirstRunLanguagePolicy.uncommonOnly(Locale.forLanguageTag(tag), presets)?.locale?.languageTag()
        }
        val zhengma = FirstRunLanguagePolicy.uncommonOnly(Locale.forLanguageTag("zh-CN"), presets)!!
        zhengma.locale.languageTag() shouldBe "zh-CN-zhengma"
        FirstRunLanguagePolicy.uncommonMethodName(zhengma) shouldBe "Zhengma"
        uncommonTag("zh-Hans-SG") shouldBe "zh-CN-zhengma"
        uncommonTag("zh-TW").shouldBeNull()
        uncommonTag("zh-Hant-HK").shouldBeNull()
        // A language with a common preset gets that one, and one with none gets nothing to name.
        uncommonTag("de-DE").shouldBeNull()
        uncommonTag("sw-KE").shouldBeNull()

        // An uncommon preset in another script than the phone's isn't named either.
        val cyrillicOnly = listOf(zhengma.copy(locale = FlorisLocale.from("sr", "RS", "zhengma")))
        FirstRunLanguagePolicy.uncommonOnly(Locale.forLanguageTag("sr-RS"), cyrillicOnly) shouldBe cyrillicOnly[0]
        FirstRunLanguagePolicy.uncommonOnly(Locale.forLanguageTag("sr-Latn-RS"), cyrillicOnly).shouldBeNull()
    }

    test("the step names the phone's first language when it has only Zhengma, even with other languages suggested") {
        fun noticeFor(vararg tags: String): FirstRunLanguagePolicy.Notice {
            return FirstRunLanguagePolicy.notice(tags.map { Locale.forLanguageTag(it) }, presets)
        }
        val chinese = Locale.forLanguageTag("zh-CN")
        val zhengma = FirstRunLanguagePolicy.uncommonOnly(chinese, presets)!!
        // Alone, English is the one keyboard shown, so the message can point at it. With Japanese suggested too
        // it can't, since Japanese is the main button then.
        noticeFor("zh-CN") shouldBe
            FirstRunLanguagePolicy.Notice.UncommonOnly(chinese, zhengma, othersSuggested = false)
        noticeFor("zh-CN", "en-US") shouldBe
            FirstRunLanguagePolicy.Notice.UncommonOnly(chinese, zhengma, othersSuggested = true)
        noticeFor("zh-CN", "ja-JP") shouldBe
            FirstRunLanguagePolicy.Notice.UncommonOnly(chinese, zhengma, othersSuggested = true)
        // Only the first language is talked about, and one without any keyboard is named only when nothing matches.
        noticeFor("en-US", "zh-CN") shouldBe FirstRunLanguagePolicy.Notice.Suggested
        noticeFor("sw-KE") shouldBe FirstRunLanguagePolicy.Notice.NoMatch(Locale.forLanguageTag("sw-KE"))
        noticeFor("sw-KE", "de-DE") shouldBe FirstRunLanguagePolicy.Notice.Suggested
        noticeFor("de-DE") shouldBe FirstRunLanguagePolicy.Notice.Suggested
    }

    test("Chinese follows the script, so a Traditional Chinese phone isn't offered a Simplified preset") {
        val simplified = presets.single { it.locale.language == "zh" }.copy(locale = FlorisLocale.from("zh", "CN"))
        fun tagWith(tag: String): String? {
            val match = FirstRunLanguagePolicy.bestMatch(Locale.forLanguageTag(tag), listOf(simplified))
            return match?.locale?.languageTag()
        }

        tagWith("zh-CN") shouldBe "zh-CN"
        tagWith("zh-Hans-SG") shouldBe "zh-CN"
        tagWith("zh-TW").shouldBeNull()
        tagWith("zh-Hant-HK").shouldBeNull()
        tagWith("zh-HK").shouldBeNull()
    }

    test("a two-language preset is never suggested on its own") {
        val bilingual = presets.first().copy(secondaryLocales = listOf(FlorisLocale.from("es", "US")))
        FirstRunLanguagePolicy.bestMatch(Locale.forLanguageTag("en-US"), listOf(bilingual)).shouldBeNull()
    }

    test("suggestions follow the phone's language order, one per language preset, without repeats") {
        val suggested = FirstRunLanguagePolicy.suggestions(
            systemLocales = listOf(
                Locale.forLanguageTag("de-DE"),
                Locale.forLanguageTag("sw-KE"),
                Locale.forLanguageTag("en-GB"),
                Locale.forLanguageTag("de-LU"),
            ),
            presets = presets,
        )
        suggested.map { it.locale.languageTag() } shouldContainExactly listOf("de-DE", "en-UK")
    }

    test("a phone language the catalog lacks gets no suggestion, and English is there to fall back on") {
        FirstRunLanguagePolicy.suggestions(listOf(Locale.forLanguageTag("sw-KE")), presets).shouldBeEmpty()
        val english = FirstRunLanguagePolicy.englishFallback(presets)!!
        english.locale.languageTag() shouldBe "en-US"
        english.secondaryLocales.shouldBeEmpty()
    }

    test("search matches names without caring about case or accents, and language tags") {
        val eCircumflex = Char(0x00EA)
        val capitalECircumflex = Char(0x00CA)
        val names = mapOf(
            "pt-BR" to "Portugu${eCircumflex}s (Brasil)",
            "pt-PT" to "Portugu${eCircumflex}s (Portugal)",
            "de-DE" to "Deutsch (Deutschland)",
        )
        val namesOf = { preset: SubtypePreset -> listOf(names[preset.locale.languageTag()] ?: "Other") }
        fun search(query: String): List<String> {
            return FirstRunLanguagePolicy.search(query, presets, namesOf).map { it.locale.languageTag() }
        }

        search("portugues") shouldContainExactly listOf("pt-PT", "pt-BR")
        search("PORTUGU${capitalECircumflex}S") shouldContainExactly listOf("pt-PT", "pt-BR")
        search("  deutsch ") shouldContainExactly listOf("de-DE")
        search("PT-br") shouldContainExactly listOf("pt-BR")
        search("klingon").shouldBeEmpty()
        FirstRunLanguagePolicy.search("   ", presets, namesOf) shouldBe presets
    }

    test("search finds letters that have no accent to drop, spelled out plainly") {
        // "Łódź", "Straße" and "Ærø".
        val names = listOf(
            "${Char(0x0141)}${Char(0x00F3)}d${Char(0x017A)}",
            "Stra${Char(0x00DF)}e",
            "${Char(0x00C6)}r${Char(0x00F8)}",
        )
        val namesOf = { preset: SubtypePreset -> listOf(names[presets.indexOf(preset) % names.size]) }
        fun found(query: String) = FirstRunLanguagePolicy.search(query, presets, namesOf).isNotEmpty()

        found("lodz") shouldBe true
        found("strasse") shouldBe true
        found("aero") shouldBe true
        found("strabe") shouldBe false
    }

    test("setup spells letters out the same way the settings searches do") {
        // "Ŀ", "Ŧ" and "Ə": a middle-dot L, a barred T and a schwa, which setup used to leave alone.
        val names = listOf("${Char(0x013F)}ela", "${Char(0x0166)}ana", "${Char(0x018F)}li")
        val namesOf = { preset: SubtypePreset -> listOf(names[presets.indexOf(preset) % names.size]) }
        fun found(query: String) = FirstRunLanguagePolicy.search(query, presets, namesOf).isNotEmpty()

        found("lela") shouldBe true
        found("tana") shouldBe true
        found("eli") shouldBe true
        for (name in names) {
            withClue(name) { found(foldForSearch(name)) shouldBe true }
        }
    }

    test("the picker finds a language by its own name and its English one, whatever language the phone shows") {
        val german = presets.single { it.locale.languageTag() == "de-DE" }
        // A phone set to Brazilian Portuguese shows German as "alemão (Alemanha)".
        val shownInPortuguese = german.locale.displayName(FlorisLocale.from("pt", "BR"))
        fun findsGerman(query: String) = german in FirstRunLanguagePolicy.search(query, presets) { preset ->
            FirstRunLanguagePolicy.searchNames(preset, if (preset == german) shownInPortuguese else "")
        }

        findsGerman("alemao") shouldBe true
        findsGerman("Deutsch") shouldBe true
        findsGerman("german") shouldBe true
        findsGerman("french") shouldBe false

        // Faroese calls itself "føroyskt".
        val faroese = presets.single { it.locale.languageTag() == "fo" }
        val found = FirstRunLanguagePolicy.search("foroyskt", presets) { FirstRunLanguagePolicy.searchNames(it, "") }
        found shouldContain faroese
    }

    test("Serbian follows the script, since the Serbian preset is Cyrillic") {
        presets.single { it.locale.language == "sr" }.preferred.characters.componentId shouldBe "serbian_cyrillic"
        tagOf(Locale.forLanguageTag("sr-RS")) shouldBe "sr-RS"
        tagOf(Locale.forLanguageTag("sr-Cyrl-RS")) shouldBe "sr-RS"
        tagOf(Locale.forLanguageTag("sr-Latn-RS")).shouldBeNull()
        tagOf(Locale.forLanguageTag("az-AZ")) shouldBe "az"
        tagOf(Locale.forLanguageTag("az-Cyrl-AZ")).shouldBeNull()
    }

    test("Hindi, Urdu and Kurdish follow the script their one preset types") {
        fun layoutOf(language: String): String {
            return presets.single { it.locale.language == language }.preferred.characters.componentId
        }
        layoutOf("hi") shouldBe "hindi_in"
        layoutOf("ur") shouldBe "urdu_phonetic"
        layoutOf("ku") shouldBe "kurdish_kurmanci"

        tagOf(Locale.forLanguageTag("hi-IN")) shouldBe "hi-IN"
        tagOf(Locale.forLanguageTag("hi-Latn-IN")).shouldBeNull()
        tagOf(Locale.forLanguageTag("ur-PK")) shouldBe "ur-PK"
        tagOf(Locale.forLanguageTag("ur-Latn-PK")).shouldBeNull()
        tagOf(Locale.forLanguageTag("ku-TR")) shouldBe "ku"
        tagOf(Locale.forLanguageTag("ku-Arab-IQ")).shouldBeNull()
    }

    test("Sorani Kurdish, Ho and Kabyle follow the script their one preset types") {
        fun layoutOf(language: String): String {
            return presets.single { it.locale.language == language }.preferred.characters.componentId
        }
        layoutOf("ckb") shouldBe "kurdish"
        layoutOf("hoc") shouldBe "warang_citi"
        layoutOf("kab") shouldBe "qwerty"

        tagOf(Locale.forLanguageTag("ckb-IQ")) shouldBe "ckb"
        tagOf(Locale.forLanguageTag("ckb-Arab-IQ")) shouldBe "ckb"
        tagOf(Locale.forLanguageTag("ckb-Latn-IQ")).shouldBeNull()
        tagOf(Locale.forLanguageTag("hoc-IN")) shouldBe "hoc"
        tagOf(Locale.forLanguageTag("hoc-Deva-IN")).shouldBeNull()
        tagOf(Locale.forLanguageTag("kab-DZ")) shouldBe "kab"
        tagOf(Locale.forLanguageTag("kab-Tfng-DZ")).shouldBeNull()
    }

    test("the no-keyboard message names the form of a language the catalog lacks, or the language alone") {
        fun missing(tag: String): String {
            return FirstRunLanguagePolicy.missingLanguageName(Locale.forLanguageTag(tag), presets, Locale.US)
        }

        missing("sw-KE") shouldBe "Swahili"
        missing("zh-TW") shouldBe "Chinese (Taiwan)"
        val serbianLatin = missing("sr-Latn-RS")
        serbianLatin shouldStartWith "Serbian ("
        serbianLatin shouldContain "Latin"
        serbianLatin shouldContain "Serbia"
    }
})

private fun bundledPresets(): List<SubtypePreset> {
    val file = listOf(
        File("app/src/main/assets/ime/keyboard/org.florisboard.localization/extension.json"),
        File("src/main/assets/ime/keyboard/org.florisboard.localization/extension.json"),
    ).first { it.exists() }
    return loadJsonAsset(file.readText(), KeyboardExtension.serializer(), ExtensionJsonConfig)
        .getOrThrow()
        .subtypePresets
}
