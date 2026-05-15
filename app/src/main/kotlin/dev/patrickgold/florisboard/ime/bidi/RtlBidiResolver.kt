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
 * ROADMAP §7 L4 — bidirectional (BiDi) text analysis for the composing
 * region.
 *
 * FlorisBoard upstream's RTL handling is **layout-only** — the key
 * positions flip but the composing-region span boundaries are still
 * computed by LTR-character offsets, which produces broken
 * `setComposingRegion(...)` calls when the user mixes Arabic / Persian /
 * Urdu / Hebrew with Latin. Gboard exhibits the same bug class on
 * mixed-direction input.
 *
 * The JVM ships a Unicode Bidirectional Algorithm (UAX #9) reference
 * implementation as `java.text.Bidi` — zero external dependency, no
 * native runtime, exactly the analysis the composing-region selector
 * needs. This wrapper exposes the minimum surface the IME callsite uses:
 *  - [analyze] returns the run boundaries + base direction for a
 *    composing string so the IME can re-emit `setComposingRegion(start,
 *    end)` with the correct visual order.
 *  - [primaryDirection] returns the dominant direction of a string for
 *    quick "is this an RTL paragraph?" tests.
 *  - [hasMixedDirections] is the cheap predicate the IME uses to decide
 *    whether to engage the slower per-run BiDi pass at all.
 *
 * A future L4.2 pass will additionally surface:
 *  - Persian Yeh/Kaf normalisation (`\u064A` → `\u06CC`, `\u0643` → `\u06A9`).
 *  - Arabic connected-form shaping using Unicode presentation forms
 *    (`\uFE70`–`\uFEFC` lookup table).
 *  - Urdu Nastaliq positional shaping via the bundled Noto Nastaliq Urdu
 *    font fallback (NotoNastaliqUrdu-Regular.ttf).
 */
object RtlBidiResolver {

    /**
     * Analyse [text] and return its run breakdown. Runs are
     * left-to-right or right-to-left contiguous substrings of [text];
     * each carries its level (UAX #9 embedding level — even = LTR, odd
     * = RTL) and the character offsets `[start, endExclusive)`.
     *
     * [paragraphBaseDirection] biases the analyser when [text] starts
     * with a strong character of one direction but the user has
     * declared the paragraph is the other (the IME's
     * [editorInfo.imeOptions] + locale heuristics drive this).
     */
    fun analyze(
        text: String,
        paragraphBaseDirection: ParagraphBaseDirection = ParagraphBaseDirection.AUTO,
    ): BidiAnalysis {
        if (text.isEmpty()) {
            return BidiAnalysis(
                isLeftToRight = paragraphBaseDirection != ParagraphBaseDirection.FORCE_RTL,
                isMixed = false,
                runs = emptyList(),
            )
        }
        val flag = when (paragraphBaseDirection) {
            ParagraphBaseDirection.AUTO -> Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT
            ParagraphBaseDirection.FORCE_LTR -> Bidi.DIRECTION_LEFT_TO_RIGHT
            ParagraphBaseDirection.FORCE_RTL -> Bidi.DIRECTION_RIGHT_TO_LEFT
        }
        val bidi = Bidi(text, flag)
        val isLeftToRight = bidi.baseLevel and 1 == 0
        val isMixed = bidi.isMixed
        val runs = ArrayList<BidiRun>(bidi.runCount)
        for (i in 0 until bidi.runCount) {
            val start = bidi.getRunStart(i)
            val end = bidi.getRunLimit(i)
            val level = bidi.getRunLevel(i)
            runs += BidiRun(
                start = start,
                endExclusive = end,
                level = level,
                isRightToLeft = level and 1 == 1,
            )
        }
        return BidiAnalysis(
            isLeftToRight = isLeftToRight,
            isMixed = isMixed,
            runs = runs,
        )
    }

    fun primaryDirection(text: String): TextDirection {
        if (text.isEmpty()) return TextDirection.LTR
        val bidi = Bidi(text, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT)
        return if (bidi.baseLevel and 1 == 1) TextDirection.RTL else TextDirection.LTR
    }

    fun hasMixedDirections(text: String): Boolean {
        if (text.isEmpty()) return false
        return Bidi(text, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT).isMixed
    }
}

enum class ParagraphBaseDirection { AUTO, FORCE_LTR, FORCE_RTL }

enum class TextDirection { LTR, RTL }

data class BidiAnalysis(
    val isLeftToRight: Boolean,
    val isMixed: Boolean,
    val runs: List<BidiRun>,
)

data class BidiRun(
    val start: Int,
    val endExclusive: Int,
    val level: Int,
    val isRightToLeft: Boolean,
) {
    init {
        require(start >= 0) { "start must be non-negative" }
        require(endExclusive > start) { "endExclusive must be > start" }
        require(level >= 0) { "level must be non-negative" }
    }

    val length: Int get() = endExclusive - start
}
