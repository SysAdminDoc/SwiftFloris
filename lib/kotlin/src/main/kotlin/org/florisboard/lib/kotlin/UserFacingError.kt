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

package org.florisboard.lib.kotlin

/**
 * Turns a caught [Throwable] into something safe to put in a toast or a notice
 * card.
 *
 * The text in an exception message is not always ours. `ZipUtils` quotes a
 * rejected archive entry name straight into its `SecurityException`, so a
 * crafted backup archive gets to choose up to 255 characters of what the user
 * is shown, newlines and bidi overrides included. Even where the text is ours
 * it is unbounded: a nested cause chain or a long SAF document URI turns a
 * one-line notice into a wall.
 *
 * So every path that shows a caught throwable to a person routes through here.
 * The full cause still goes to the log, untouched, which is where it is useful.
 */
object UserFacingError {

    /**
     * Longest message shown to the user, ellipsis included.
     *
     * Sized for the surfaces that carry it: a `LENGTH_LONG` toast and the
     * secondary line of a status card, both of which are already scrolling past
     * useful at this length. [sanitize] never returns more than this.
     */
    const val MaxLength: Int = 200

    private const val ELLIPSIS = '…'

    /**
     * Characters that reorder or hide the text around them.
     *
     * A right-to-left override inside an error message reverses how the rest of
     * the line reads, which is a cheap way to make a hostile string look like a
     * different one. These go; the rest of category Cf stays, because ZWJ and
     * ZWNJ carry meaning in Arabic, Persian and Indic scripts and in emoji
     * sequences, and stripping them silently rewrites words.
     */
    private val REORDERING_CODE_POINTS = setOf(
        0x061C, // ARABIC LETTER MARK
        0x200E, // LEFT-TO-RIGHT MARK
        0x200F, // RIGHT-TO-LEFT MARK
        0x202A, // LEFT-TO-RIGHT EMBEDDING
        0x202B, // RIGHT-TO-LEFT EMBEDDING
        0x202C, // POP DIRECTIONAL FORMATTING
        0x202D, // LEFT-TO-RIGHT OVERRIDE
        0x202E, // RIGHT-TO-LEFT OVERRIDE
        0x2066, // LEFT-TO-RIGHT ISOLATE
        0x2067, // RIGHT-TO-LEFT ISOLATE
        0x2068, // FIRST STRONG ISOLATE
        0x2069, // POP DIRECTIONAL ISOLATE
    )

    /**
     * Returns [error]'s message, sanitized and bounded, or [fallback] when it
     * has nothing usable to say.
     */
    fun summarize(error: Throwable?, fallback: String): String {
        val sanitized = sanitize(error?.localizedMessage)
        return sanitized.ifEmpty { fallback }
    }

    /**
     * Collapses [raw] to a single line of at most [MaxLength] characters:
     * control characters and line breaks become spaces, runs of whitespace
     * collapse, reordering marks are dropped, and anything that does not fit is
     * replaced with an ellipsis.
     *
     * Returns an empty string when nothing legible survives, so callers can
     * fall back to their own copy rather than showing whitespace.
     */
    fun sanitize(raw: String?): String {
        if (raw.isNullOrEmpty()) return ""
        val collapsed = collapse(raw)
        if (collapsed.isEmpty() || collapsed.length <= MaxLength) return collapsed
        return truncate(collapsed)
    }

    private fun collapse(raw: String): String {
        val out = StringBuilder(raw.length)
        var pendingSpace = false
        var index = 0
        // By code point, not by char: a category lookup on half of a surrogate
        // pair answers SURROGATE and tells you nothing about the character.
        while (index < raw.length) {
            val codePoint = raw.codePointAt(index)
            val width = Character.charCount(codePoint)
            index += width
            when {
                isSeparator(codePoint) -> pendingSpace = out.isNotEmpty()
                isDropped(codePoint) -> Unit
                else -> {
                    if (pendingSpace) {
                        out.append(' ')
                        pendingSpace = false
                    }
                    out.appendCodePoint(codePoint)
                }
            }
        }
        return out.toString()
    }

    private fun isSeparator(codePoint: Int): Boolean {
        if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) return true
        return Character.getType(codePoint) == Character.CONTROL.toInt()
    }

    private fun isDropped(codePoint: Int): Boolean = codePoint in REORDERING_CODE_POINTS

    /**
     * Cuts [collapsed] so the result, ellipsis included, is exactly at most
     * [MaxLength] characters, never splitting a surrogate pair and preferring a
     * word boundary when one sits close to the cut.
     */
    private fun truncate(collapsed: String): String {
        val budget = MaxLength - 1
        var cut = budget
        // Stepping back off a low surrogate keeps the pair intact rather than
        // leaving half a character in front of the ellipsis.
        if (Character.isLowSurrogate(collapsed[cut])) cut -= 1
        val lastSpace = collapsed.lastIndexOf(' ', cut - 1)
        if (lastSpace >= budget - WORD_BOUNDARY_REACH) cut = lastSpace
        return collapsed.take(cut).trimEnd() + ELLIPSIS
    }

    /** How far back a word boundary may sit before a hard cut is preferred. */
    private const val WORD_BOUNDARY_REACH = 16
}
