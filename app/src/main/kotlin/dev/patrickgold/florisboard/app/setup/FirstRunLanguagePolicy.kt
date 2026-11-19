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
import dev.patrickgold.florisboard.ime.nlp.latin.LatinDictionarySuggester
import dev.patrickgold.florisboard.lib.FlorisLocale
import dev.patrickgold.florisboard.lib.util.foldForSearch
import java.util.Locale

/**
 * The keyboard languages first-run setup offers. Each of the phone's languages, in the phone's order, gets the
 * bundled preset that fits it best, so the same phone always sees the same list.
 */
internal object FirstRunLanguagePolicy {
    val EnglishLocale: FlorisLocale = FlorisLocale.from("en", "US")

    /** Countries the preset catalog writes differently than Android does. */
    private val CatalogCountries = mapOf("GB" to "UK")

    /** Countries the Latin American Spanish preset (`es-419`) is for. */
    private val LatinAmerica = setOf(
        "AR", "BO", "CL", "CO", "CR", "CU", "DO", "EC", "GT", "HN", "MX", "NI", "PA", "PE", "PR", "PY", "SV", "UY",
        "VE",
    )

    /**
     * Languages whose standard layout is the preset without a country. The catalog's `ko-KR` preset is the
     * phonetic Korean layout, so a Korean phone matching it by country would get the uncommon one.
     */
    private val StandardPresetHasNoCountry = setOf("ko")

    /** Chinese is written with Traditional characters here, which a Simplified preset doesn't serve. */
    private val TraditionalChinese = setOf("TW", "HK", "MO")

    /**
     * The script every bundled preset of a language types, for languages also written in another one. A phone
     * set to Latin Serbian (`sr-Latn-RS`) gets no Serbian suggestion, since the `sr-RS` preset is Cyrillic, and
     * one set to Hindi in Latin letters (`hi-Latn-IN`) doesn't get the Devanagari Hindi one.
     */
    private val PresetScripts = mapOf(
        "sr" to "Cyrl", "az" to "Latn", "hi" to "Deva", "ur" to "Arab", "ku" to "Latn", "ckb" to "Arab",
        "hoc" to "Wara", "kab" to "Latn",
    )

    /**
     * Presets for a way of typing few people use, which setup leaves to the search. The one Chinese preset is
     * Zhengma, typed by the shapes of the characters, while most people type Chinese by its sound (Pinyin).
     */
    private val UncommonVariants = setOf("zhengma")

    /** One preset per phone language that has one, best fit first within each language, without repeats. */
    fun suggestions(systemLocales: List<Locale>, presets: List<SubtypePreset>): List<SubtypePreset> {
        return systemLocales.mapNotNull { bestMatch(it, presets) }.distinct()
    }

    /** The English preset setup offers when the phone's language has none, or next to the ones it has. */
    fun englishFallback(presets: List<SubtypePreset>): SubtypePreset? {
        return presets.find { it.locale == EnglishLocale && it.secondaryLocales.isEmpty() }
    }

    /**
     * The preset for [systemLocale]: the same language and country, then the country the catalog spells another
     * way or the region it groups the country under, then for English the US or British preset by how the country
     * spells, then the language's preset without a country, then the language's first preset in catalog order.
     * Null when the catalog has nothing for the language.
     */
    fun bestMatch(systemLocale: Locale, presets: List<SubtypePreset>): SubtypePreset? {
        val language = systemLocale.language
        val presetScript = PresetScripts[language]
        if (presetScript != null && systemLocale.script.isNotEmpty() && systemLocale.script != presetScript) return null
        val candidates = presets.filter { preset ->
            preset.secondaryLocales.isEmpty() &&
                preset.locale.language == language &&
                preset.locale.variant.lowercase(Locale.ROOT) !in UncommonVariants &&
                sameChineseScript(systemLocale, preset.locale)
        }
        if (candidates.isEmpty()) return null
        if (language in StandardPresetHasNoCountry) {
            candidates.find { it.locale.country.isEmpty() }?.let { return it }
        }
        val country = systemLocale.country
        val catalogCountries = buildSet {
            if (country.isNotEmpty()) add(country)
            CatalogCountries[country]?.let { add(it) }
            if (language == "es" && country in LatinAmerica) add("419")
        }
        candidates.find { it.locale.country == country && country.isNotEmpty() && it.locale.variant.isEmpty() }
            ?.let { return it }
        candidates.find { it.locale.country in catalogCountries && it.locale.variant.isEmpty() }?.let { return it }
        candidates.find { it.locale.country in catalogCountries }?.let { return it }
        if (language == "en") {
            // English the catalog has no country for is written the US way or the British way, and the
            // keyboard's spelling fixes go by the country of the preset picked.
            val spelling = if (country in LatinDictionarySuggester.UsSpellingRegions) "US" else "UK"
            candidates.find { it.locale.country == spelling && it.locale.variant.isEmpty() }?.let { return it }
        }
        return candidates.find { it.locale.country.isEmpty() } ?: candidates.first()
    }

    /**
     * The presets one of whose names, as [namesOf] gives them, or whose language tag contains [query], ignoring
     * case and accents. A blank query keeps them all.
     */
    fun search(
        query: String,
        presets: List<SubtypePreset>,
        namesOf: (SubtypePreset) -> List<String>,
    ): List<SubtypePreset> {
        val wanted = fold(query.trim())
        if (wanted.isEmpty()) return presets
        return presets.filter { preset ->
            namesOf(preset).any { fold(it).contains(wanted) } || fold(preset.locale.languageTag()).contains(wanted)
        }
    }

    /**
     * The names the picker finds [preset] by: [shownName], the one it shows, then its name in its own language
     * ("Deutsch") and in English ("German"), so a phone in another language still finds it either way.
     */
    fun searchNames(preset: SubtypePreset, shownName: String): List<String> {
        val locales = listOf(preset.locale) + preset.secondaryLocales
        return listOf(
            shownName,
            locales.joinToString(" + ") { it.displayName(it) },
            locales.joinToString(" + ") { it.displayName(EnglishLocale) },
        ).distinct()
    }

    /**
     * The name the "no keyboard for this language yet" message gives [systemLocale], in [displayLocale]: the
     * language alone ("Swahili"), or with its script and country ("Chinese (Taiwan)", "Serbian (Latin,
     * Serbia)") when the catalog has the language but not that form of it.
     */
    fun missingLanguageName(systemLocale: Locale, presets: List<SubtypePreset>, displayLocale: Locale): String {
        val catalogHasLanguage = presets.any { it.locale.language == systemLocale.language }
        return if (catalogHasLanguage) {
            systemLocale.getDisplayName(displayLocale)
        } else {
            systemLocale.getDisplayLanguage(displayLocale)
        }
    }

    /**
     * The preset setup leaves to the search for being a way of typing few people use, when it's the only one for
     * [systemLocale] (Zhengma on a phone in Simplified Chinese), so the message can name it instead of saying the
     * language has none. Null when [bestMatch] has a preset or the catalog has nothing at all for it.
     */
    fun uncommonOnly(systemLocale: Locale, presets: List<SubtypePreset>): SubtypePreset? {
        if (bestMatch(systemLocale, presets) != null) return null
        val presetScript = PresetScripts[systemLocale.language]
        if (presetScript != null && systemLocale.script.isNotEmpty() && systemLocale.script != presetScript) return null
        return presets.find { preset ->
            preset.secondaryLocales.isEmpty() &&
                preset.locale.language == systemLocale.language &&
                preset.locale.variant.lowercase(Locale.ROOT) in UncommonVariants &&
                sameChineseScript(systemLocale, preset.locale)
        }
    }

    /** The way of typing an [uncommonOnly] preset is for, by its variant ("Zhengma"). */
    fun uncommonMethodName(preset: SubtypePreset): String {
        return preset.locale.variant.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
    }

    /**
     * What the language step says above its buttons, about the first of [systemLocales] (never empty). A first
     * language with only an [uncommonOnly] preset is named even when the phone's other languages get suggestions,
     * so a phone set to Chinese and then English still hears that Zhengma is the one Chinese keyboard.
     */
    fun notice(systemLocales: List<Locale>, presets: List<SubtypePreset>): Notice {
        val first = systemLocales.first()
        val uncommon = uncommonOnly(first, presets)
        val othersSuggested = suggestions(systemLocales, presets).isNotEmpty()
        return when {
            uncommon != null -> Notice.UncommonOnly(first, uncommon, othersSuggested)
            !othersSuggested -> Notice.NoMatch(first)
            else -> Notice.Suggested
        }
    }

    sealed interface Notice {
        /** The phone's languages have keyboards, so the step only asks for a pick. */
        data object Suggested : Notice

        /**
         * [systemLocale] has only [preset], a way of typing few people use. [othersSuggested] says the phone's other
         * languages have keyboards shown, so English isn't the only one to point at.
         */
        data class UncommonOnly(
            val systemLocale: Locale,
            val preset: SubtypePreset,
            val othersSuggested: Boolean,
        ) : Notice

        /** None of the phone's languages has a keyboard, so [systemLocale] is named as missing. */
        data class NoMatch(val systemLocale: Locale) : Notice
    }

    private fun sameChineseScript(systemLocale: Locale, presetLocale: FlorisLocale): Boolean {
        if (systemLocale.language != "zh") return true
        val traditional = when (systemLocale.script) {
            "Hant" -> true
            "Hans" -> false
            else -> systemLocale.country in TraditionalChinese
        }
        return traditional == (presetLocale.country in TraditionalChinese)
    }

    /** The settings searches' fold, so setup finds a language by the same spellings they do. */
    private fun fold(text: String): String = foldForSearch(text)
}
