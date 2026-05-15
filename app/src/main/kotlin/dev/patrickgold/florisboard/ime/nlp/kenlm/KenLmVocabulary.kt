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

package dev.patrickgold.florisboard.ime.nlp.kenlm

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * ROADMAP §7 Next-3.1b — KenLM vocabulary string-arena parser.
 *
 * Builds on the Next-3.1 header reader + Next-3.1a mmap trie reader.
 * After the header block, KenLM's body lays out (per
 * `lm/binary_format.hh`):
 *
 *  - Search arena (model-type-specific; e.g. probing hash table for
 *    PROBING, Bhiksha-encoded next-pointer arrays for TRIE).
 *  - Vocabulary arena (when `has_vocabulary = true`):
 *      uint64 string_count
 *      uint64 strings_byte_length
 *      bytes[strings_byte_length] — concatenated `\0`-terminated UTF-8
 *        token strings; index `i` corresponds to the i-th string in
 *        insertion order. Index 0 is conventionally `<unk>`.
 *
 * This module owns the vocabulary-arena read path. The search arena is
 * the next slice (probability table + Bhiksha pointer decode for
 * unigram → trigram → … navigation).
 *
 * Reference: https://github.com/kpu/kenlm/blob/master/lm/vocab.hh
 *
 * Layout used here matches the **default `lm_build_binary`** output;
 * variants produced by `--rest` and quantization flags share the
 * same vocabulary block shape.
 */
class KenLmVocabulary(
    private val tokens: List<String>,
    private val indexByToken: Map<String, Int>,
) {

    val size: Int get() = tokens.size

    /** Vocabulary index for [token], or `0` (`<unk>` slot) when absent. */
    fun indexOf(token: String): Int = indexByToken[token] ?: UNK_INDEX

    /** Token string at vocabulary index [index]. */
    fun tokenAt(index: Int): String {
        require(index in tokens.indices) { "index $index out of range" }
        return tokens[index]
    }

    /** All tokens in insertion order (`<unk>` first). */
    fun allTokens(): List<String> = tokens

    /** True when [token] is in the vocabulary AND not the unknown slot. */
    fun contains(token: String): Boolean = indexByToken[token] != null && indexByToken[token] != UNK_INDEX

    companion object {
        const val UNK_TOKEN: String = "<unk>"
        const val UNK_INDEX: Int = 0
        const val BEGIN_SENTENCE_TOKEN: String = "<s>"
        const val END_SENTENCE_TOKEN: String = "</s>"

        /**
         * Parse a vocabulary arena buffer into a [KenLmVocabulary].
         * The buffer position should be at the start of the vocabulary
         * block (the `uint64 string_count` field). Returns null when
         * the buffer is too short or malformed; callers should
         * gracefully fall back to a no-vocab KenLM lookup path.
         */
        fun parse(buffer: ByteBuffer): KenLmVocabulary? {
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            if (buffer.remaining() < 16) return null
            val stringCount = buffer.long
            val stringsByteLength = buffer.long
            if (stringCount < 1 || stringCount > MAX_VOCAB_SIZE) return null
            if (stringsByteLength < 1 || stringsByteLength > MAX_STRINGS_BYTES) return null
            if (buffer.remaining() < stringsByteLength) return null

            val tokens = ArrayList<String>(stringCount.toInt())
            val indexByToken = HashMap<String, Int>(stringCount.toInt())
            val bytes = ByteArray(stringsByteLength.toInt())
            buffer.get(bytes)

            var start = 0
            var i = 0
            while (start < bytes.size && tokens.size < stringCount) {
                var end = start
                while (end < bytes.size && bytes[end] != 0.toByte()) end++
                val token = String(bytes, start, end - start, StandardCharsets.UTF_8)
                tokens.add(token)
                indexByToken[token] = i
                i++
                start = end + 1
            }
            if (tokens.isEmpty()) return null
            return KenLmVocabulary(tokens, indexByToken)
        }

        /** Hard caps. 8M tokens / 256 MB string arena — bigger models
         *  push into the dictionary-pack addon path, not the base APK. */
        const val MAX_VOCAB_SIZE: Long = 8_000_000L
        const val MAX_STRINGS_BYTES: Long = 256L * 1024 * 1024
    }
}
