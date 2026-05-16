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

import dev.patrickgold.florisboard.lib.FlorisLocale

/**
 * ROADMAP matrix #27 — Unexpected-Keyboard-style script-first layout picker.
 *
 * The current Settings → Localization layout picker is locale-alphabetical, which works well for users typing
 * one or two languages of the same script. Multi-script users (e.g. Russian + Tajik + Kazakh = Cyrillic;
 * Arabic + Persian + Urdu = Arabic; Hindi + Marathi + Sanskrit = Devanagari) end up scrolling through hundreds
 * of unrelated locales to find their preferred set. Grouping by script first is the
 * Unexpected-Keyboard / Fcitx5 / AnySoftKeyboard pattern that makes the picker tractable for those users.
 *
 * The classifier is a pure-Kotlin lookup over the BCP-47 primary language code, with overrides for the
 * handful of languages where the dominant script does not match a naive language-code guess (Yiddish written
 * in Hebrew script even though "yi" is otherwise Germanic; Maltese in Latin script despite Semitic family;
 * Bosnian in Latin even though Serbian is split across Latin and Cyrillic; etc.).
 *
 * The classifier deliberately does **not** read the locale's `script` subtag — most enrolled locales come
 * from the SubtypePreset catalog with just `languageTag = "ru"` (no `Cyrl` subtag), and we want a stable
 * answer per language-code without forcing the catalog authors to also tag every entry. Pass an explicit
 * script subtag via the [LayoutScript] enum if you need to override a default (e.g. Serbian Latin: use
 * [LATIN] when the user opts in to the Latin variant).
 */
enum class LayoutScript(val displayName: String) {
    LATIN("Latin"),
    CYRILLIC("Cyrillic"),
    GREEK("Greek"),
    HEBREW("Hebrew"),
    ARABIC("Arabic"),
    ARMENIAN("Armenian"),
    GEORGIAN("Georgian"),
    DEVANAGARI("Devanagari"),
    BENGALI("Bengali"),
    GURMUKHI("Gurmukhi"),
    GUJARATI("Gujarati"),
    TAMIL("Tamil"),
    TELUGU("Telugu"),
    KANNADA("Kannada"),
    MALAYALAM("Malayalam"),
    ODIA("Odia"),
    SINHALA("Sinhala"),
    THAI("Thai"),
    LAO("Lao"),
    KHMER("Khmer"),
    BURMESE("Burmese"),
    TIBETAN("Tibetan"),
    MONGOLIAN("Mongolian"),
    CJK("CJK"),
    KOREAN_HANGUL("Hangul"),
    JAPANESE("Japanese"),
    ETHIOPIC("Ethiopic"),
    CHEROKEE("Cherokee"),
    OTHER("Other"),
}

/**
 * Pure classifier mapping a [FlorisLocale]'s primary language code to a [LayoutScript].
 *
 * Coverage is intentionally weighted toward the languages with shipped dictionaries / layouts; long-tail
 * locales fall through to [LayoutScript.OTHER] so the picker still groups them under a single bucket rather
 * than scattering them.
 */
object LayoutScriptClassifier {

    private val byLanguageCode: Map<String, LayoutScript> = buildMap {
        // Latin-script Western European
        for (code in listOf("en", "de", "fr", "es", "it", "pt", "nl", "sv", "no", "da", "fi", "is", "ca", "gl",
            "ro", "hu", "cs", "sk", "pl", "sl", "hr", "bs", "lt", "lv", "et", "tr", "az", "uz", "tk", "kk",
            "ky", "mt", "ga", "cy", "eu", "vi", "id", "ms", "tl", "sw", "af", "zu", "xh", "ha", "ig", "yo",
            "rw", "so", "om", "fil", "haw", "mi", "sm", "ceb", "jv", "su", "ny", "sn", "st", "tn", "ts", "nr",
            "ss", "nso", "ven", "tr", "lb", "rm", "fo", "kl")) {
            put(code, LayoutScript.LATIN)
        }
        // Cyrillic
        for (code in listOf("ru", "uk", "be", "bg", "mk", "sr", "mn", "tg", "ba", "tt", "cv", "ce", "os", "ab",
            "ky", "kk", "ku")) {
            put(code, LayoutScript.CYRILLIC)
        }
        // Greek
        put("el", LayoutScript.GREEK)
        // Hebrew + Yiddish (Yiddish written in Hebrew script)
        put("he", LayoutScript.HEBREW)
        put("yi", LayoutScript.HEBREW)
        put("iw", LayoutScript.HEBREW)  // legacy code
        put("lad", LayoutScript.HEBREW)  // Ladino historically also Hebrew script
        // Arabic-script (Arabic + Persian + Urdu + Pashto + Sindhi + Kurdish Sorani + Kashmiri + Punjabi
        // (Shahmukhi) + Uyghur)
        for (code in listOf("ar", "fa", "ur", "ps", "sd", "ku", "ks", "ug", "arq", "arz", "azb", "fa-AF")) {
            put(code, LayoutScript.ARABIC)
        }
        // Armenian, Georgian
        put("hy", LayoutScript.ARMENIAN)
        put("ka", LayoutScript.GEORGIAN)
        // Indic
        for (code in listOf("hi", "mr", "ne", "sa", "kok", "doi", "mai", "bho", "awa")) {
            put(code, LayoutScript.DEVANAGARI)
        }
        put("bn", LayoutScript.BENGALI)
        put("as", LayoutScript.BENGALI)
        put("pa", LayoutScript.GURMUKHI)
        put("gu", LayoutScript.GUJARATI)
        put("ta", LayoutScript.TAMIL)
        put("te", LayoutScript.TELUGU)
        put("kn", LayoutScript.KANNADA)
        put("ml", LayoutScript.MALAYALAM)
        put("or", LayoutScript.ODIA)
        put("si", LayoutScript.SINHALA)
        // SE Asian
        put("th", LayoutScript.THAI)
        put("lo", LayoutScript.LAO)
        put("km", LayoutScript.KHMER)
        put("my", LayoutScript.BURMESE)
        put("bo", LayoutScript.TIBETAN)
        put("dz", LayoutScript.TIBETAN)
        // Mongolian (traditional script). Modern Mongolian is also written in Cyrillic — that is handled
        // by the Cyrillic bucket via the "mn" entry above; the traditional-script entry would need an
        // explicit "mn-Mong" tag from the catalog.
        // CJK
        for (code in listOf("zh", "yue", "wuu", "hak")) {
            put(code, LayoutScript.CJK)
        }
        put("ja", LayoutScript.JAPANESE)
        put("ko", LayoutScript.KOREAN_HANGUL)
        // Ethiopic
        for (code in listOf("am", "ti", "tir", "om-ET", "gez")) {
            put(code, LayoutScript.ETHIOPIC)
        }
        // Cherokee
        put("chr", LayoutScript.CHEROKEE)
    }

    /**
     * Classify a [FlorisLocale] by its primary language code. Returns [LayoutScript.OTHER] for any locale
     * not in the bundled lookup table.
     */
    fun classify(locale: FlorisLocale): LayoutScript {
        val language = locale.language.lowercase()
        return byLanguageCode[language] ?: LayoutScript.OTHER
    }

    /**
     * Group a list of [SubtypePreset] entries by [LayoutScript]. The returned map preserves insertion order
     * (script appearance order matches the order presets surface scripts in [presets]), and each per-script
     * list preserves the input order of its constituent presets so the per-locale alphabetical ordering of
     * the catalog is retained inside each script bucket.
     */
    fun groupByScript(presets: List<SubtypePreset>): Map<LayoutScript, List<SubtypePreset>> {
        val grouped = LinkedHashMap<LayoutScript, MutableList<SubtypePreset>>()
        for (preset in presets) {
            val script = classify(preset.locale)
            grouped.getOrPut(script) { mutableListOf() }.add(preset)
        }
        return grouped
    }
}
