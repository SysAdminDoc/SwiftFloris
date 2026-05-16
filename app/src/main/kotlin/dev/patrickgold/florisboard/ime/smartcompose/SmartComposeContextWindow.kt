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

package dev.patrickgold.florisboard.ime.smartcompose

/**
 * ROADMAP §10.5 L1.1d — context window truncation for smart-compose.
 *
 * The IME hands smart-compose providers the full `precedingText` of
 * the current line, but a Gemma 3 1B model with 4-8 K context
 * doesn't need 50 KB of paragraph history — it needs the last
 * ~256 tokens (≈1.2 KB UTF-8 typical English). Sending more is
 * wasted IPC + cache pressure + a privacy footprint that grows
 * unboundedly with editor scrollback.
 *
 * This helper trims [SmartComposeContext.precedingText] to a
 * sentence-boundary window before dispatch. The trim is sentence-
 * aware (last whole sentence, not last whole word) so the provider
 * sees coherent grammar, not "...sentence cut mi" → garbage
 * suggestions.
 *
 * Strategy:
 *  1. Take the trailing [maxChars] of [precedingText] as a hard cap.
 *  2. From that window, walk back to the nearest sentence boundary
 *     (`.` / `!` / `?` / `。` / `।` / `？` etc.) and start there.
 *  3. If no boundary exists in the window, fall back to the hard
 *     cap (model still sees coherent UTF-8; this is the long-
 *     single-sentence edge case).
 *
 * 1,024 chars is the default cap — covers ≈200-250 English tokens
 * which is the practical context most smart-compose models use even
 * when they advertise a longer window.
 */
object SmartComposeContextWindow {

    /** Sentence-boundary punctuation, mirrored from [SentenceTokenizer]'s set. */
    private val SENTENCE_TERMINATORS: Set<Int> = setOf(
        '.'.code, '!'.code, '?'.code,
        0x06D4, 0x061F,
        0x0964, 0x0965,
        0x3002, 0xFF01, 0xFF1F,
        0x1362,
    )

    const val DEFAULT_MAX_CHARS: Int = 1_024

    /**
     * Return a windowed view of [precedingText] suitable for
     * dispatch into a smart-compose addon. Returns the input
     * unchanged when its length is already ≤ [maxChars].
     */
    fun truncate(precedingText: String, maxChars: Int = DEFAULT_MAX_CHARS): String {
        require(maxChars >= 16) { "maxChars must be ≥ 16 (was $maxChars)" }
        if (precedingText.length <= maxChars) return precedingText
        val hardCap = precedingText.substring(precedingText.length - maxChars)
        val boundary = findFirstSentenceBoundary(hardCap)
        return if (boundary >= 0) hardCap.substring(boundary) else hardCap
    }

    /**
     * Convenience wrapper that returns a new [SmartComposeContext]
     * with `precedingText` truncated; `composingPrefix` + the other
     * fields pass through unchanged.
     */
    fun truncate(
        context: SmartComposeContext,
        maxChars: Int = DEFAULT_MAX_CHARS,
    ): SmartComposeContext {
        val trimmed = truncate(context.precedingText, maxChars)
        if (trimmed === context.precedingText) return context
        return context.copy(precedingText = trimmed)
    }

    /** Return the position of the character immediately after the
     *  first terminator inside [window], or -1 when no terminator
     *  exists. */
    private fun findFirstSentenceBoundary(window: String): Int {
        var i = 0
        while (i < window.length) {
            val cp = window.codePointAt(i)
            val charCount = Character.charCount(cp)
            if (cp in SENTENCE_TERMINATORS) {
                var j = i + charCount
                // Consume run of terminators.
                while (j < window.length) {
                    val nextCp = window.codePointAt(j)
                    if (nextCp !in SENTENCE_TERMINATORS) break
                    j += Character.charCount(nextCp)
                }
                // Skip following whitespace so the model starts on
                // the first letter of the next sentence.
                while (j < window.length) {
                    val ws = window.codePointAt(j)
                    if (!Character.isWhitespace(ws)) break
                    j += Character.charCount(ws)
                }
                if (j < window.length) return j
                // Terminator was last in window — nothing useful after.
                return -1
            }
            i += charCount
        }
        return -1
    }
}
