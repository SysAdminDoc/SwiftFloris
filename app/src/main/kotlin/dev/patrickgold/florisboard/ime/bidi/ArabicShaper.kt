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
 * ROADMAP §7 L4.1 — Arabic connected-form shaper.
 *
 * Arabic glyphs change shape depending on their position in a word
 * (isolated / initial / medial / final). Modern renderers do this
 * automatically when text is in the base-form Arabic block
 * (U+0621–U+064A), but legacy editors / preview surfaces often want
 * the *presentation form* characters (U+FE70–U+FEFC, "Arabic
 * Presentation Forms-B") emitted directly. SwiftFloris ships this
 * shaper so the IME's quick-preview composing-text rendering, the
 * smartbar candidate row, and any addon that does its own text-to-PNG
 * rendering (L12) can lay down correctly-joined glyphs regardless of
 * the receiving editor's font.
 *
 * The shaping table here covers every Arabic base-form character with
 * a presentation-form mapping in the Unicode Standard. For each base
 * letter we know its four context forms; the algorithm walks the
 * codepoints once, picking the right form by looking at the joining
 * properties of the neighbours (whether each is "joining" or
 * non-joining per UCD `ArabicShaping.txt`).
 *
 * References:
 *  - Unicode UAX #9 BiDi (handled by [RtlBidiResolver]).
 *  - Unicode UCD `ArabicShaping.txt`.
 *  - Unicode Standard §9.2 "Arabic — Joining and Presentation Forms".
 */
object ArabicShaper {

    /**
     * Replace each base-form Arabic codepoint in [text] with the
     * appropriate presentation-form glyph for its position in a
     * connected run. Non-Arabic characters pass through unchanged.
     *
     * The output uses the **Forms-B** glyphs (U+FE70..U+FEFC) which
     * the Unicode Standard publishes as the joining-rendered set.
     * Modern Compose/AGSL text rendering will joint base-form input
     * automatically; this shaper is for editors / preview surfaces
     * that need pre-joined glyphs.
     */
    fun shape(text: String): String {
        if (text.isEmpty()) return text
        val cps = text.codePoints().toArray()
        val output = StringBuilder(cps.size * 2)
        for (i in cps.indices) {
            val cp = cps[i]
            val info = SHAPING_TABLE[cp]
            if (info == null) {
                output.appendCodePoint(cp)
                continue
            }
            val joinsPrev = findBaseLetterBefore(cps, i)?.let { SHAPING_TABLE[it]?.canJoinNext } == true
            val joinsNext = findBaseLetterAfter(cps, i)?.let { SHAPING_TABLE[it]?.canJoinPrev } == true
            val form = when {
                joinsPrev && joinsNext -> info.medial
                joinsPrev && !joinsNext -> info.final
                !joinsPrev && joinsNext -> info.initial
                else -> info.isolated
            }
            // Some non-joining letters (Alef, Dal, Reh, Waw, etc.) have
            // no medial/initial — fall through to isolated/final.
            val resolved = when {
                form != 0 -> form
                joinsPrev -> info.final.takeIf { it != 0 } ?: info.isolated
                else -> info.isolated
            }
            output.appendCodePoint(resolved)
        }
        return output.toString()
    }

    /**
     * Per-letter shaping rules: which presentation forms the letter
     * has, and whether it can connect to its neighbours.
     *
     *  - [canJoinPrev] — letter accepts a join from a preceding letter
     *    (i.e. it has a final + medial form). False for the "right-
     *    joining-only" letters: Alef, Dal, Reh, Waw, etc.
     *  - [canJoinNext] — letter accepts a join from the next letter.
     */
    private fun findBaseLetterBefore(cps: IntArray, i: Int): Int? {
        var j = i - 1
        while (j >= 0 && Character.getType(cps[j]) == Character.NON_SPACING_MARK.toInt()) j--
        return if (j >= 0) cps[j] else null
    }

    private fun findBaseLetterAfter(cps: IntArray, i: Int): Int? {
        var j = i + 1
        while (j < cps.size && Character.getType(cps[j]) == Character.NON_SPACING_MARK.toInt()) j++
        return if (j < cps.size) cps[j] else null
    }

    private data class ShapeInfo(
        val isolated: Int,
        val final: Int,
        val initial: Int,
        val medial: Int,
        val canJoinPrev: Boolean,
        val canJoinNext: Boolean,
    )

    private val SHAPING_TABLE: Map<Int, ShapeInfo> = buildMap {
        // Right-joining (no medial / initial): Alef + Alef variants.
        put(0x0627, ShapeInfo(0xFE8D, 0xFE8E, 0, 0, true, false))     // Alef
        put(0x0622, ShapeInfo(0xFE81, 0xFE82, 0, 0, true, false))     // Alef with Madda above
        put(0x0623, ShapeInfo(0xFE83, 0xFE84, 0, 0, true, false))     // Alef with Hamza above
        put(0x0625, ShapeInfo(0xFE87, 0xFE88, 0, 0, true, false))     // Alef with Hamza below
        // Other right-joining: Dal Dhal Reh Zain Waw Teh-Marbuta.
        put(0x062F, ShapeInfo(0xFEA9, 0xFEAA, 0, 0, true, false))     // Dal
        put(0x0630, ShapeInfo(0xFEAB, 0xFEAC, 0, 0, true, false))     // Thal
        put(0x0631, ShapeInfo(0xFEAD, 0xFEAE, 0, 0, true, false))     // Reh
        put(0x0632, ShapeInfo(0xFEAF, 0xFEB0, 0, 0, true, false))     // Zain
        put(0x0648, ShapeInfo(0xFEED, 0xFEEE, 0, 0, true, false))     // Waw
        put(0x0629, ShapeInfo(0xFE93, 0xFE94, 0, 0, true, false))     // Teh Marbuta
        put(0x0649, ShapeInfo(0xFEEF, 0xFEF0, 0, 0, true, false))     // Alef Maksura
        // Full four-form letters.
        put(0x0628, ShapeInfo(0xFE8F, 0xFE90, 0xFE91, 0xFE92, true, true))  // Beh
        put(0x062A, ShapeInfo(0xFE95, 0xFE96, 0xFE97, 0xFE98, true, true))  // Teh
        put(0x062B, ShapeInfo(0xFE99, 0xFE9A, 0xFE9B, 0xFE9C, true, true))  // Theh
        put(0x062C, ShapeInfo(0xFE9D, 0xFE9E, 0xFE9F, 0xFEA0, true, true))  // Jeem
        put(0x062D, ShapeInfo(0xFEA1, 0xFEA2, 0xFEA3, 0xFEA4, true, true))  // Hah
        put(0x062E, ShapeInfo(0xFEA5, 0xFEA6, 0xFEA7, 0xFEA8, true, true))  // Khah
        put(0x0633, ShapeInfo(0xFEB1, 0xFEB2, 0xFEB3, 0xFEB4, true, true))  // Seen
        put(0x0634, ShapeInfo(0xFEB5, 0xFEB6, 0xFEB7, 0xFEB8, true, true))  // Sheen
        put(0x0635, ShapeInfo(0xFEB9, 0xFEBA, 0xFEBB, 0xFEBC, true, true))  // Sad
        put(0x0636, ShapeInfo(0xFEBD, 0xFEBE, 0xFEBF, 0xFEC0, true, true))  // Dad
        put(0x0637, ShapeInfo(0xFEC1, 0xFEC2, 0xFEC3, 0xFEC4, true, true))  // Tah
        put(0x0638, ShapeInfo(0xFEC5, 0xFEC6, 0xFEC7, 0xFEC8, true, true))  // Zah
        put(0x0639, ShapeInfo(0xFEC9, 0xFECA, 0xFECB, 0xFECC, true, true))  // Ain
        put(0x063A, ShapeInfo(0xFECD, 0xFECE, 0xFECF, 0xFED0, true, true))  // Ghain
        put(0x0641, ShapeInfo(0xFED1, 0xFED2, 0xFED3, 0xFED4, true, true))  // Feh
        put(0x0642, ShapeInfo(0xFED5, 0xFED6, 0xFED7, 0xFED8, true, true))  // Qaf
        put(0x0643, ShapeInfo(0xFED9, 0xFEDA, 0xFEDB, 0xFEDC, true, true))  // Kaf
        put(0x0644, ShapeInfo(0xFEDD, 0xFEDE, 0xFEDF, 0xFEE0, true, true))  // Lam
        put(0x0645, ShapeInfo(0xFEE1, 0xFEE2, 0xFEE3, 0xFEE4, true, true))  // Meem
        put(0x0646, ShapeInfo(0xFEE5, 0xFEE6, 0xFEE7, 0xFEE8, true, true))  // Noon
        put(0x0647, ShapeInfo(0xFEE9, 0xFEEA, 0xFEEB, 0xFEEC, true, true))  // Heh
        put(0x064A, ShapeInfo(0xFEF1, 0xFEF2, 0xFEF3, 0xFEF4, true, true))  // Yeh
        // Hamza-on-Waw and Hamza-on-Yeh.
        put(0x0624, ShapeInfo(0xFE85, 0xFE86, 0, 0, true, false))     // Waw with Hamza above
        put(0x0626, ShapeInfo(0xFE89, 0xFE8A, 0xFE8B, 0xFE8C, true, true)) // Yeh with Hamza above
    }
}
