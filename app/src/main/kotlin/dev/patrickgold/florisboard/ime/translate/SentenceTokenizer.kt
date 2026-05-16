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

package dev.patrickgold.florisboard.ime.translate

/**
 * ROADMAP §10.5 L2.1d — sentence boundary tokenizer.
 *
 * Bergamot models translate sentence-by-sentence — feeding a whole
 * paragraph through one inference pass produces noticeably worse
 * output than splitting first. The Translate quick-action surface
 * routes paragraph-length selections through this tokenizer before
 * dispatching each sentence to the translator individually, then
 * stitches the results back together preserving inter-sentence
 * whitespace.
 *
 * The tokenizer is intentionally lightweight — it uses the
 * sentence-terminator family (. ! ?) plus their CJK / Arabic / Hindi
 * counterparts (。 ！ ？ ؟ ।) and treats consecutive terminators as
 * one boundary ("Hello!?" becomes one sentence ending with `!?`).
 *
 * No ML, no abbreviation table — abbreviation handling lives in the
 * translation addon itself. This pre-flight keeps the IME slim.
 */
object SentenceTokenizer {

    /** Sentence-terminator code points across the scripts we care about. */
    private val TERMINATORS: Set<Int> = setOf(
        '.'.code, '!'.code, '?'.code,
        0x06D4,           // Arabic full stop ۔
        0x061F,           // Arabic question mark ؟
        0x0964,           // Devanagari danda ।
        0x0965,           // Devanagari double danda ॥
        0x3002,           // CJK ideographic full stop 。
        0xFF01,           // Fullwidth exclamation ！
        0xFF1F,           // Fullwidth question ？
        0x1362,           // Ethiopic full stop ።
    )

    /**
     * Split [text] into sentences. Each sentence includes its
     * trailing terminator + any whitespace separator that immediately
     * follows; this lets the call site concat the translated chunks
     * without re-deriving inter-sentence spacing.
     *
     * Empty input returns an empty list. Input with no terminator
     * returns a single-entry list with the whole string.
     */
    fun split(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val out = ArrayList<String>(4)
        var sentenceStart = 0
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val charCount = Character.charCount(cp)
            if (cp in TERMINATORS) {
                // Consume any consecutive terminators as one boundary.
                var j = i + charCount
                while (j < text.length) {
                    val nextCp = text.codePointAt(j)
                    if (nextCp !in TERMINATORS) break
                    j += Character.charCount(nextCp)
                }
                // Consume trailing whitespace into the closing sentence.
                while (j < text.length) {
                    val ws = text.codePointAt(j)
                    if (!Character.isWhitespace(ws)) break
                    j += Character.charCount(ws)
                }
                out.add(text.substring(sentenceStart, j))
                sentenceStart = j
                i = j
            } else {
                i += charCount
            }
        }
        if (sentenceStart < text.length) {
            out.add(text.substring(sentenceStart))
        }
        return out
    }

    /** Cheap predicate — true when [text] contains ≥ one terminator. */
    fun hasMultipleSentences(text: String): Boolean {
        var i = 0
        var seenTerminator = false
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            if (cp in TERMINATORS) {
                if (seenTerminator) continue
                seenTerminator = true
                // Skip trailing whitespace.
                while (i < text.length) {
                    val nextCp = text.codePointAt(i)
                    if (!Character.isWhitespace(nextCp) && nextCp !in TERMINATORS) {
                        return true
                    }
                    i += Character.charCount(nextCp)
                }
            }
        }
        return false
    }
}
