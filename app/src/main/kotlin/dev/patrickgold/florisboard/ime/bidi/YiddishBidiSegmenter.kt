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
 * ROADMAP §7 L4.5 — Yiddish-aware bidi run segmenter.
 *
 * Yiddish uses the Hebrew script (Hebrew alphabet, optional Niqqud,
 * occasional Hebrew-only digraphs ייִ / ױ / וו / וי / ײ at U+05F0..U+05F2).
 * The bidi run-classification rules are the same as Hebrew but with two
 * Yiddish-specific quirks:
 *
 *  1. The **Yiddish digraph block** (U+05F0..U+05F2 — DOUBLE VAV, VAV
 *     YOD, DOUBLE YOD) lives outside the Hebrew letter block but
 *     belongs in the same direction class.
 *  2. **Latin loanword runs** are extremely common in modern written
 *     Yiddish (English compounds + scientific terms). The segmenter
 *     splits these out so the autocorrect layer can suggest either
 *     Yiddish or English candidates depending on the active run.
 *
 * This sibling of [HebrewBidiSegmenter] reuses the same `Direction`
 * enum + run shape; the underlying classifier just treats the
 * Yiddish digraph block as `HEBREW`. Yiddish text without Niqqud
 * round-trips identically through both segmenters; the difference
 * only surfaces when Yiddish-only digraphs appear.
 */
object YiddishBidiSegmenter {

    /** Hebrew + Yiddish-digraph block; matches the Yiddish letter run shape. */
    private val HEBREW_OR_YIDDISH_RANGE = 0x0590..0x05FF
    /** Yiddish-only digraph block (DOUBLE VAV, VAV YOD, DOUBLE YOD). */
    private val YIDDISH_DIGRAPH_RANGE = 0x05F0..0x05F2

    /** Reuse the [HebrewBidiSegmenter.Direction] enum for symmetry. */
    fun classify(codePoint: Int): HebrewBidiSegmenter.Direction = when {
        codePoint in HEBREW_OR_YIDDISH_RANGE -> HebrewBidiSegmenter.Direction.HEBREW
        codePoint in 0x30..0x39 -> HebrewBidiSegmenter.Direction.DIGITS
        Character.isWhitespace(codePoint) -> HebrewBidiSegmenter.Direction.WHITESPACE
        Character.isLetter(codePoint) -> HebrewBidiSegmenter.Direction.LATIN
        else -> HebrewBidiSegmenter.Direction.NEUTRAL
    }

    /**
     * True when [codePoint] falls in the Yiddish-distinctive digraph
     * block (U+05F0..U+05F2). Useful when the autocorrect engine
     * wants to route differently for Yiddish-only spelling
     * candidates.
     */
    fun isYiddishDigraph(codePoint: Int): Boolean = codePoint in YIDDISH_DIGRAPH_RANGE

    /** Run a [HebrewBidiSegmenter.Run]-shaped segmentation pass. */
    fun segment(text: String): List<HebrewBidiSegmenter.Run> {
        if (text.isEmpty()) return emptyList()
        val runs = ArrayList<HebrewBidiSegmenter.Run>(8)
        var runStart = 0
        var runDirection = classify(text.codePointAt(0))
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val dir = classify(cp)
            if (dir != runDirection) {
                runs.add(HebrewBidiSegmenter.Run(runStart, i, runDirection))
                runStart = i
                runDirection = dir
            }
            i += Character.charCount(cp)
        }
        runs.add(HebrewBidiSegmenter.Run(runStart, text.length, runDirection))
        return runs
    }

    /** Count Yiddish-distinctive digraphs in [text]. */
    fun yiddishDigraphCount(text: String): Int {
        var count = 0
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (isYiddishDigraph(cp)) count++
            i += Character.charCount(cp)
        }
        return count
    }
}
