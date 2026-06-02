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

import java.text.Bidi

/**
 * ROADMAP §7 L4.7 — visual ↔ logical text reordering.
 *
 * Most surfaces SwiftFloris hands text to (Android `EditorInfo`, the
 * underlying input connection, the user's clipboard) store text in
 * **logical order** — characters in the order the user typed them.
 * The platform's `TextView`s render in **visual order** by applying
 * the Unicode Bidi algorithm at paint time.  But certain surfaces —
 * notably the smartbar candidate row when typing a single isolated
 * RTL word — render text without re-running the Bidi algorithm, and
 * a few legacy editor surfaces persist the visual-order
 * representation.
 *
 * This helper bridges the two using the JVM-stdlib
 * [`java.text.Bidi`](https://docs.oracle.com/javase/8/docs/api/java/text/Bidi.html)
 * implementation (which is the same ICU-backed engine that
 * Android's text-rendering layer uses internally). No native dep.
 *
 * Reference: Unicode Standard Annex #9 — Bidirectional Algorithm.
 */
object VisualLogicalReorderer {

    /**
     * Reorder [logical] from logical order into visual order for the
     * paragraph's base direction. Returns the input unchanged when
     * the paragraph is purely LTR.
     *
     * [baseIsRtl] picks the paragraph base direction.
     */
    fun logicalToVisual(logical: String, baseIsRtl: Boolean): String {
        if (logical.isEmpty()) return logical
        val bidi = Bidi(
            logical,
            if (baseIsRtl) Bidi.DIRECTION_DEFAULT_RIGHT_TO_LEFT else Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT,
        )
        if (bidi.isLeftToRight) return logical
        return visualReorderByRuns(bidi, logical)
    }

    /**
     * Reorder [visual] from visual order back to logical order. Used
     * when interpreting RTL text pasted from a legacy surface that
     * stores in visual order.
     *
     * Note: under the Unicode bidi algorithm not every visual-order
     * string round-trips losslessly to a unique logical form (the
     * algorithm folds run-internal information into the runs), but
     * for the common single-script case this helper produces the
     * expected logical order.
     */
    fun visualToLogical(visual: String, baseIsRtl: Boolean): String {
        if (visual.isEmpty()) return visual
        // For single-script RTL text in visual order the logical form
        // is literally the reverse character sequence.
        if (baseIsRtl && !visual.any { Character.isDigit(it) }) {
            return reverseByCodePoint(visual)
        }
        // Mixed-direction visual → logical isn't well-defined for
        // arbitrary input; treat as identity to avoid surprising
        // callers that don't know which surface produced the string.
        return visual
    }

    /**
     * True when [text] under the paragraph base direction [baseIsRtl]
     * needs visual reordering — i.e. logical-order and visual-order
     * differ.
     */
    fun needsReordering(text: String, baseIsRtl: Boolean): Boolean {
        if (text.isEmpty()) return false
        val bidi = Bidi(
            text,
            if (baseIsRtl) Bidi.DIRECTION_DEFAULT_RIGHT_TO_LEFT else Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT,
        )
        return !bidi.baseIsLeftToRight() || bidi.isMixed
    }

    private fun visualReorderByRuns(bidi: Bidi, logical: String): String {
        val runCount = bidi.runCount
        if (runCount == 0) return logical

        // Resolve runs in logical order, then ask the ICU-backed engine
        // for the correct visual order via UAX#9 rule L2. `sortedWith`
        // by ascending level is NOT L2: L2 reverses contiguous spans
        // from the highest level down to the lowest odd level, which
        // preserves the spatial interleaving of LTR/RTL runs that a
        // level-ascending sort destroys. `Bidi.reorderVisually` is the
        // stdlib implementation of L2.
        val levels = ByteArray(runCount)
        val visualToLogicalRun = Array(runCount) { it }
        for (i in 0 until runCount) {
            levels[i] = bidi.getRunLevel(i).toByte()
        }
        // Reorders `visualToLogicalRun` in place into visual order.
        Bidi.reorderVisually(levels, 0, visualToLogicalRun, 0, runCount)

        val out = StringBuilder(logical.length)
        for (visualIndex in 0 until runCount) {
            val runIndex = visualToLogicalRun[visualIndex] as Int
            val start = bidi.getRunStart(runIndex)
            val end = bidi.getRunLimit(runIndex)
            val level = bidi.getRunLevel(runIndex)
            val run = logical.substring(start, end)
            if (level and 1 == 1) {
                // Odd level = RTL run; reverse for visual order. Reverse
                // by Unicode code point (not UTF-16 char) so
                // supplementary-plane characters keep their surrogate
                // pairs intact and combining-mark sequences are not torn
                // from their base.
                out.append(reverseByCodePoint(run))
            } else {
                out.append(run)
            }
        }
        return out.toString()
    }

    /**
     * Reverse [s] by Unicode code point. `String.reversed()` reverses by
     * UTF-16 code unit, which swaps the order of the high/low surrogate
     * of any supplementary-plane character (corrupting it into two
     * unpaired surrogates) and detaches combining marks from their base.
     */
    private fun reverseByCodePoint(s: String): String {
        val sb = StringBuilder(s.length)
        var i = s.length
        while (i > 0) {
            val cp = s.codePointBefore(i)
            sb.appendCodePoint(cp)
            i -= Character.charCount(cp)
        }
        return sb.toString()
    }
}
