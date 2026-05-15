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
 * ROADMAP §7 L4.3 — Hebrew vocalisation (Niqqud) normalisation.
 *
 * Modern Hebrew typically writes consonant-only forms (כתב חסר, ktav
 * khaser) and Niqqud (vowel-point marks U+05B0..U+05BC) only appears
 * in liturgical / educational / poetry contexts. Users often paste
 * pointed text into chat / email surfaces but want it stripped on
 * commit (or vice-versa). This normalizer is the surface the Settings
 * → Hebrew → "Strip Niqqud" toggle drives.
 *
 * Additionally normalises the **Hebrew Geresh** / **Gershayim**
 * punctuation marks: standard punctuation typed via the IME's
 * `'` and `"` keys is rewritten to U+05F3 / U+05F4 when the active
 * subtype is Hebrew, so the apostrophe/quote in Hebrew abbreviation
 * conventions (e.g. ד״ר for "Dr.") renders correctly.
 *
 * Reference: [Unicode Standard §9.1 "Hebrew"](https://www.unicode.org/charts/PDF/U0590.pdf).
 */
object HebrewNiqqudNormalizer {

    /** Range of Hebrew points (Niqqud + cantillation) to consider for stripping. */
    private val NIQQUD_RANGE = 0x0591..0x05C7

    /**
     * Strip every Niqqud / cantillation mark from [text] when
     * [stripNiqqud] is true. ASCII apostrophe + quote are rewritten to
     * Geresh / Gershayim (U+05F3 / U+05F4) when [useGereshGershayim]
     * is true.
     */
    fun normalize(
        text: String,
        stripNiqqud: Boolean = false,
        useGereshGershayim: Boolean = false,
    ): String {
        if (text.isEmpty()) return text
        if (!stripNiqqud && !useGereshGershayim) return text
        val out = StringBuilder(text.length)
        for (ch in text) {
            val cp = ch.code
            when {
                stripNiqqud && cp in NIQQUD_RANGE -> continue
                useGereshGershayim && cp == '\''.code -> out.append('\u05F3')
                useGereshGershayim && cp == '"'.code -> out.append('\u05F4')
                else -> out.append(ch)
            }
        }
        return out.toString()
    }

    /** True when [ch] is in the Niqqud / cantillation range. */
    fun isNiqqud(ch: Char): Boolean = ch.code in NIQQUD_RANGE

    /** Return the number of Niqqud marks in [text]. Cheap predicate for
     *  the "Strip Niqqud" toggle to know whether to bother running the
     *  full normalization pass on commit. */
    fun niqqudCount(text: String): Int = text.count { isNiqqud(it) }
}
