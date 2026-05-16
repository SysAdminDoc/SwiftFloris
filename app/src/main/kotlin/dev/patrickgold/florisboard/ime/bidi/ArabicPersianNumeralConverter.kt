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
 * ROADMAP §7 L4.6 — Western Arabic ↔ Arabic-Indic ↔ Extended Arabic-Indic
 * (Persian / Urdu) digit conversion.
 *
 * Three digit families share semantics but live at different
 * Unicode positions:
 *
 *  - **Western Arabic** (U+0030..U+0039) — the everyday ASCII digits
 *    the rest of the IME assumes.
 *  - **Arabic-Indic** (U+0660..U+0669) — used in most Arabic-locale
 *    typography (Saudi Arabia, Egypt, Levant).
 *  - **Extended Arabic-Indic** (U+06F0..U+06F9) — Persian + Urdu
 *    locales (Iran, Pakistan, Afghanistan).
 *
 * Modern Arabic-locale users frequently want the IME to:
 *
 *  1. **Display** native digits in the smartbar suggestions even
 *     though the underlying buffer carries Western digits.
 *  2. **Commit** native digits when the user types Western digits
 *     under an Arabic / Persian / Urdu subtype.
 *  3. **Normalise** received text to Western digits before feeding
 *     the autocorrect engine (which trains on Western-digit corpora).
 *
 * This converter ships the three pairwise transforms + a
 * "normalise-anything-to-Western" pass for the autocorrect feed.
 *
 * Reference: [Unicode Standard §9.2 "Arabic"](https://www.unicode.org/charts/PDF/U0600.pdf).
 */
object ArabicPersianNumeralConverter {

    private const val WESTERN_BASE = 0x0030
    private const val ARABIC_INDIC_BASE = 0x0660
    private const val EXTENDED_ARABIC_INDIC_BASE = 0x06F0

    /** Convert every Western digit (0..9) in [text] to its Arabic-Indic counterpart. */
    fun westernToArabicIndic(text: String): String =
        rewriteDigits(text, fromBase = WESTERN_BASE, toBase = ARABIC_INDIC_BASE)

    /** Convert every Arabic-Indic digit (٠..٩) in [text] back to Western. */
    fun arabicIndicToWestern(text: String): String =
        rewriteDigits(text, fromBase = ARABIC_INDIC_BASE, toBase = WESTERN_BASE)

    /** Convert every Western digit to its Extended Arabic-Indic (Persian / Urdu) counterpart. */
    fun westernToExtendedArabicIndic(text: String): String =
        rewriteDigits(text, fromBase = WESTERN_BASE, toBase = EXTENDED_ARABIC_INDIC_BASE)

    /** Convert every Extended Arabic-Indic digit (۰..۹) in [text] back to Western. */
    fun extendedArabicIndicToWestern(text: String): String =
        rewriteDigits(text, fromBase = EXTENDED_ARABIC_INDIC_BASE, toBase = WESTERN_BASE)

    /**
     * Normalise every digit in [text] — regardless of family — to its
     * Western form. Useful for the autocorrect pre-feed so the
     * ranking model doesn't have to learn three digit families.
     */
    fun normaliseToWestern(text: String): String {
        if (text.isEmpty()) return text
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val charCount = Character.charCount(cp)
            val normalised = when (cp) {
                in ARABIC_INDIC_BASE..ARABIC_INDIC_BASE + 9 ->
                    WESTERN_BASE + (cp - ARABIC_INDIC_BASE)
                in EXTENDED_ARABIC_INDIC_BASE..EXTENDED_ARABIC_INDIC_BASE + 9 ->
                    WESTERN_BASE + (cp - EXTENDED_ARABIC_INDIC_BASE)
                else -> cp
            }
            out.append(Character.toChars(normalised))
            i += charCount
        }
        return out.toString()
    }

    /** True when [codePoint] is in any of the three digit families. */
    fun isAnyDigit(codePoint: Int): Boolean =
        codePoint in WESTERN_BASE..WESTERN_BASE + 9 ||
            codePoint in ARABIC_INDIC_BASE..ARABIC_INDIC_BASE + 9 ||
            codePoint in EXTENDED_ARABIC_INDIC_BASE..EXTENDED_ARABIC_INDIC_BASE + 9

    private fun rewriteDigits(text: String, fromBase: Int, toBase: Int): String {
        if (text.isEmpty()) return text
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val charCount = Character.charCount(cp)
            val mapped = if (cp in fromBase..fromBase + 9) {
                toBase + (cp - fromBase)
            } else {
                cp
            }
            out.append(Character.toChars(mapped))
            i += charCount
        }
        return out.toString()
    }
}
