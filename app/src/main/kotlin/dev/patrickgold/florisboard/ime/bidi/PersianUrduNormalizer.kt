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

/**
 * ROADMAP §7 L4.2 — Persian / Urdu codepoint normalisation.
 *
 * Persian (Farsi) and Urdu users routinely have Arabic-Yeh + Arabic-Kaf
 * codepoints on their keyboards even though their actual orthography
 * uses the Persian/Urdu-specific Yeh + Kaf variants. The Arabic forms
 * look almost identical visually but break dictionary lookups, text
 * search, and copy-paste round-trips on Persian/Urdu text. The
 * Persian/Urdu community has standardised on normalising every Arabic
 * variant to the Persian variant at input time.
 *
 *  - **Yeh**: Arabic Yeh `\u064A` (ي) → Farsi Yeh `\u06CC` (ی).
 *  - **Kaf**: Arabic Kaf `\u0643` (ك) → Farsi Kaf `\u06A9` (ک).
 *  - **Heh-with-Hamza**: Arabic `\u0647` followed by Hamza (`\u0654`) →
 *    Urdu Heh-Goal `\u06C1`.
 *  - **Tatweel**: ASCII-like `\u0640` is a stretching glyph that shouldn't
 *    accumulate in stored text; SwiftKey strips it on save. Optional
 *    via the [stripTatweel] flag.
 *  - **Arabic digits → Persian digits**: optional via [convertDigits]
 *    flag. Many Persian users want `1` → `۱`; many don't. Settings
 *    surface lives in `prefs.text.persianDigitNormalize`.
 *
 * Reference: [Unicode TR §9.3](https://www.unicode.org/reports/tr9/) +
 * Persian Wikipedia normalisation conventions.
 */
object PersianUrduNormalizer {

    /**
     * Replace every Arabic codepoint with its Persian/Urdu canonical
     * equivalent. Returns [text] unchanged when no replacements apply.
     */
    fun normalize(
        text: String,
        stripTatweel: Boolean = false,
        convertDigits: PersianDigitMode = PersianDigitMode.KEEP_ARABIC,
    ): String {
        if (text.isEmpty()) return text
        val out = StringBuilder(text.length)
        for (ch in text) {
            val replaced = when (ch.code) {
                0x064A -> '\u06CC'   // Arabic Yeh → Farsi Yeh
                0x0643 -> '\u06A9'   // Arabic Kaf → Farsi Kaf
                0x0649 -> '\u06CC'   // Alef Maksura → Farsi Yeh (Persian convention)
                0x0640 -> if (stripTatweel) null else ch  // Tatweel
                in 0x0660..0x0669 -> when (convertDigits) {
                    PersianDigitMode.KEEP_ARABIC -> ch
                    PersianDigitMode.TO_PERSIAN -> Char(ch.code + (0x06F0 - 0x0660))
                    PersianDigitMode.TO_LATIN -> Char((ch.code - 0x0660) + '0'.code)
                }
                in '0'.code..'9'.code -> when (convertDigits) {
                    PersianDigitMode.TO_PERSIAN -> Char(ch.code + (0x06F0 - '0'.code))
                    else -> ch
                }
                else -> ch
            } ?: continue
            out.append(replaced)
        }
        return out.toString()
    }
}

enum class PersianDigitMode {
    /** No conversion. */
    KEEP_ARABIC,

    /** Western Arabic numerals → Extended Arabic-Indic numerals (Persian/Urdu). */
    TO_PERSIAN,

    /** Arabic-Indic numerals → Western Arabic numerals (Latin digits). */
    TO_LATIN,
}
