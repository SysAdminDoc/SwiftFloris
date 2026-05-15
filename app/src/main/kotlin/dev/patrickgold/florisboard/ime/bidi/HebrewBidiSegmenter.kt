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
 * ROADMAP §7 L4.4 — Hebrew bidi run segmenter.
 *
 * Mixed Hebrew + Latin / digit text needs run-level segmentation so
 * the layout engine can:
 *
 *  1. Apply the right caret-affinity per run (RTL caret sits on the
 *     left of the cursor logical-position glyph; LTR run is the
 *     opposite).
 *  2. Decide which on-key glyph to show on the spacebar / period key
 *     when the cursor straddles a run boundary.
 *  3. Feed correctly-segmented runs to the autocorrect engine — a
 *     Hebrew suggestion shouldn't ever be offered for "iPhone 16" or
 *     "1234".
 *
 * The segmenter is intentionally Hebrew-specific (Arabic / Persian
 * / Urdu already have their own normaliser + shaper in this package).
 * Runs are classified into [Direction.HEBREW], [Direction.LATIN],
 * [Direction.DIGITS], [Direction.WHITESPACE], and [Direction.NEUTRAL]
 * (punctuation + symbols). Whitespace + neutral runs preserve the
 * direction of the *previous* run when the caller flattens, matching
 * the Unicode BiDi algorithm's "weak" / "neutral" handling for the
 * common phone-typing case.
 */
object HebrewBidiSegmenter {

    /** Hebrew letters block (U+0590..U+05FF) — covers Niqqud + cantillation. */
    private val HEBREW_RANGE = 0x0590..0x05FF

    /**
     * Direction class assigned to each character run in a segmented
     * string.
     */
    enum class Direction { HEBREW, LATIN, DIGITS, WHITESPACE, NEUTRAL }

    /**
     * One contiguous run inside the source string. `start` is
     * inclusive, `endExclusive` exclusive — call sites can `substring`
     * the run with `text.substring(run.start, run.endExclusive)`.
     */
    data class Run(val start: Int, val endExclusive: Int, val direction: Direction) {
        val length: Int get() = endExclusive - start
    }

    /** Classify a single code point into a [Direction]. */
    fun classify(codePoint: Int): Direction = when {
        codePoint in HEBREW_RANGE -> Direction.HEBREW
        codePoint in 0x30..0x39 -> Direction.DIGITS
        Character.isWhitespace(codePoint) -> Direction.WHITESPACE
        Character.isLetter(codePoint) -> Direction.LATIN
        else -> Direction.NEUTRAL
    }

    /**
     * Split [text] into contiguous runs whose characters share a
     * direction class. Empty input yields an empty list. Surrogate
     * pairs are honoured (Hebrew alone lives in the BMP, but Latin
     * may carry supplementary letters).
     */
    fun segment(text: String): List<Run> {
        if (text.isEmpty()) return emptyList()
        val runs = ArrayList<Run>(8)
        var runStart = 0
        var runDirection = classify(text.codePointAt(0))
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val dir = classify(cp)
            if (dir != runDirection) {
                runs.add(Run(runStart, i, runDirection))
                runStart = i
                runDirection = dir
            }
            i += Character.charCount(cp)
        }
        runs.add(Run(runStart, text.length, runDirection))
        return runs
    }

    /**
     * Return the [Direction] of the character logically *to the left*
     * of [cursorIndex] (the typical caret-position query). Returns
     * [Direction.NEUTRAL] when the cursor is at the start of the
     * string.
     */
    fun directionBefore(text: String, cursorIndex: Int): Direction {
        if (cursorIndex <= 0 || text.isEmpty()) return Direction.NEUTRAL
        val charIndex = minOf(cursorIndex, text.length) - 1
        return classify(text.codePointAt(charIndex))
    }

    /**
     * Find the dominant direction in [text]. The "dominant" run is
     * the longest non-whitespace, non-neutral run by character count.
     * Falls back to [Direction.NEUTRAL] when only whitespace +
     * punctuation exists.
     *
     * Useful for deciding which subtype to surface in the smartbar
     * when the user types a single isolated word.
     */
    fun dominantDirection(text: String): Direction {
        if (text.isEmpty()) return Direction.NEUTRAL
        var bestLength = 0
        var bestDir = Direction.NEUTRAL
        for (run in segment(text)) {
            if (run.direction == Direction.WHITESPACE) continue
            if (run.direction == Direction.NEUTRAL) continue
            if (run.length > bestLength) {
                bestLength = run.length
                bestDir = run.direction
            }
        }
        return bestDir
    }
}
