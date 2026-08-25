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
 * is shown, newlines and control characters included. Even where the text is
 * ours it is unbounded: a nested cause chain or a long SAF document URI turns a
 * one-line notice into a wall.
 *
 * So every path that shows a caught throwable to a person routes through here.
 * The full cause still goes to the log, untouched, which is where it is useful.
 */
object UserFacingError {

    /**
     * Longest message shown to the user.
     *
     * Sized for the surfaces that carry it: a `LENGTH_LONG` toast and the
     * secondary line of a status card, both of which are already scrolling past
     * useful at this length.
     */
    const val MaxLength: Int = 200

    private const val ELLIPSIS = '…'

    /**
     * Returns [error]'s message, sanitized and bounded, or [fallback] when it
     * has nothing usable to say.
     */
    fun summarize(error: Throwable?, fallback: String): String {
        val sanitized = sanitize(error?.localizedMessage)
        return sanitized.ifEmpty { fallback }
    }

    /**
     * Collapses [raw] to a single bounded line: control characters and line
     * breaks become spaces, runs of whitespace collapse, and anything past
     * [MaxLength] is replaced with an ellipsis.
     *
     * Returns an empty string when nothing legible survives, so callers can
     * fall back to their own copy rather than showing whitespace.
     */
    fun sanitize(raw: String?): String {
        if (raw.isNullOrEmpty()) return ""
        val collapsed = buildString(raw.length) {
            var pendingSpace = false
            for (char in raw) {
                // Category Cc/Cf plus every whitespace form, so a newline, a
                // tab, a BiDi override and a NUL all become the same space.
                val isSeparator = char.isWhitespace() ||
                    char.category == CharCategory.CONTROL ||
                    char.category == CharCategory.FORMAT
                if (isSeparator) {
                    pendingSpace = isNotEmpty()
                    continue
                }
                if (pendingSpace) {
                    append(' ')
                    pendingSpace = false
                }
                append(char)
            }
        }
        if (collapsed.isEmpty()) return ""
        if (collapsed.length <= MaxLength) return collapsed
        // Cut back to a word boundary when one is close, so the tail is not a
        // half-word, but never lose more than a short word doing it.
        val hardCut = collapsed.take(MaxLength)
        val lastSpace = hardCut.lastIndexOf(' ')
        val body = if (lastSpace >= MaxLength - 16) hardCut.take(lastSpace) else hardCut
        return body.trimEnd() + ELLIPSIS
    }
}
