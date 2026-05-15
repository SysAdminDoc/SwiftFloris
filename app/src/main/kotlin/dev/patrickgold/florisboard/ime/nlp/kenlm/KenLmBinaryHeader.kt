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

import java.io.IOException
import java.io.InputStream

/**
 * ROADMAP §7 Next-3.1 — KenLM binary trie header reader (scaffold).
 *
 * KenLM ships its Kneser-Ney n-gram models as memory-mappable binary
 * files with the following on-disk layout (per `lm/binary_format.hh`):
 *
 *  - offset 0   : magic string `"mmap lm http://kheafield.com/code\n\0"`
 *                 padded to **64 bytes** including null terminators
 *  - offset 64  : `ModelType` enum (uint32 little-endian)
 *                 0 = PROBING, 1 = REST_PROBING, 2 = TRIE, 3 = QUANT_TRIE,
 *                 4 = ARRAY_TRIE, 5 = QUANT_ARRAY_TRIE
 *  - offset 68  : `FixedWidthParameters`:
 *                  uint32  order            (max n-gram length)
 *                  uint8   probing_multiplier (only valid for probing)
 *                  uint8   has_vocabulary
 *                  uint8   pointer_bhiksha
 *                  uint8   counts_following_order  (always order entries
 *                          following — uint64 n-gram count per order)
 *  - offset 76  : counts[0..order)  (uint64 each, n-gram counts per order)
 *  - then       : model-type-specific search and vocab arenas
 *
 * For SwiftFloris we only need a streaming "is this a KenLM file? what
 * order? what model type? what per-order n-gram counts?" probe so the
 * Next-3 ranker can decide whether to mmap the file or fall back to the
 * existing bigram chain. The full trie body parsing (probability
 * quantisation, Bhiksha-encoded pointers, vocab strings) is intentionally
 * deferred to Next-3.1a — the JNI bring-up against the upstream KenLM
 * library is the cheapest path there.
 *
 * Reference: https://github.com/kpu/kenlm/blob/master/lm/binary_format.hh
 */
data class KenLmBinaryHeader(
    val modelType: KenLmModelType,
    val order: Int,
    val hasVocabulary: Boolean,
    /** Count of n-grams at each order (1-gram count, 2-gram count, ...). */
    val ngramCounts: List<Long>,
) {
    val totalNgrams: Long get() = ngramCounts.sum()
}

enum class KenLmModelType(val id: Int) {
    PROBING(0),
    REST_PROBING(1),
    TRIE(2),
    QUANT_TRIE(3),
    ARRAY_TRIE(4),
    QUANT_ARRAY_TRIE(5),
    UNKNOWN(-1);

    companion object {
        fun fromId(id: Int): KenLmModelType =
            entries.firstOrNull { it.id == id } ?: UNKNOWN
    }
}

object KenLmBinaryReader {

    /** ASCII "mmap lm http://kheafield.com/code" + LF + NUL, NUL-padded to 64 bytes. */
    private const val MAGIC_LENGTH = 64
    private val MAGIC_BYTES = ("mmap lm http://kheafield.com/code\n\u0000")
        .toByteArray(Charsets.US_ASCII)

    /**
     * Read just the header block of a KenLM binary file and return a
     * structured [KenLmBinaryHeader]. The caller is responsible for closing
     * [input]. Throws [KenLmFormatException] when the magic does not match
     * or when the stream ends prematurely.
     */
    @Throws(IOException::class)
    fun readHeader(input: InputStream): KenLmBinaryHeader {
        val magic = ByteArray(MAGIC_LENGTH)
        readFully(input, magic, MAGIC_LENGTH, "magic")
        validateMagic(magic)

        val modelTypeId = readUInt32Le(input, "model_type")
        val modelType = KenLmModelType.fromId(modelTypeId)

        val order = readUInt32Le(input, "order")
        if (order < 1 || order > 8) {
            throw KenLmFormatException("KenLM order out of range: $order")
        }
        // FixedWidthParameters: probing_multiplier, has_vocabulary,
        // pointer_bhiksha, counts_following_order (each uint8).
        val probingMultiplier = readUInt8(input, "probing_multiplier")
        val hasVocabulary = readUInt8(input, "has_vocabulary") != 0
        val pointerBhiksha = readUInt8(input, "pointer_bhiksha")
        val countsFollowingOrder = readUInt8(input, "counts_following_order")
        if (countsFollowingOrder != order) {
            throw KenLmFormatException(
                "KenLM counts_following_order ($countsFollowingOrder) " +
                    "does not equal order ($order)",
            )
        }
        val ngramCounts = (0 until order).map { idx ->
            readUInt64Le(input, "ngram_counts[$idx]")
        }

        // Touch the read-only params so they're not flagged unused; they
        // become relevant once the trie body parser is wired up.
        @Suppress("UNUSED_VARIABLE")
        val ignored = probingMultiplier to pointerBhiksha

        return KenLmBinaryHeader(
            modelType = modelType,
            order = order,
            hasVocabulary = hasVocabulary,
            ngramCounts = ngramCounts,
        )
    }

    private fun validateMagic(buffer: ByteArray) {
        // KenLM tolerates either NUL-padded magic or the actual ascii copy;
        // only verify the leading ASCII prefix to handle both shapes.
        val expectedPrefix = MAGIC_BYTES.copyOfRange(
            0,
            MAGIC_BYTES.size - 1,
        )
        if (buffer.size < expectedPrefix.size) {
            throw KenLmFormatException("KenLM header too short")
        }
        for (i in expectedPrefix.indices) {
            if (buffer[i] != expectedPrefix[i]) {
                throw KenLmFormatException(
                    "KenLM magic mismatch at offset $i (got 0x${"%02X".format(buffer[i])}, " +
                        "expected 0x${"%02X".format(expectedPrefix[i])})",
                )
            }
        }
    }

    private fun readFully(input: InputStream, buffer: ByteArray, length: Int, fieldName: String) {
        var read = 0
        while (read < length) {
            val n = input.read(buffer, read, length - read)
            if (n == -1) {
                throw KenLmFormatException("KenLM EOF while reading $fieldName (need $length, got $read)")
            }
            read += n
        }
    }

    private fun readUInt8(input: InputStream, fieldName: String): Int {
        val byte = input.read()
        if (byte == -1) {
            throw KenLmFormatException("KenLM EOF reading $fieldName")
        }
        return byte and 0xFF
    }

    private fun readUInt32Le(input: InputStream, fieldName: String): Int {
        val buffer = ByteArray(4)
        readFully(input, buffer, 4, fieldName)
        return (buffer[0].toInt() and 0xFF) or
            ((buffer[1].toInt() and 0xFF) shl 8) or
            ((buffer[2].toInt() and 0xFF) shl 16) or
            ((buffer[3].toInt() and 0xFF) shl 24)
    }

    private fun readUInt64Le(input: InputStream, fieldName: String): Long {
        val buffer = ByteArray(8)
        readFully(input, buffer, 8, fieldName)
        var value = 0L
        for (i in 0 until 8) {
            value = value or ((buffer[i].toLong() and 0xFFL) shl (i * 8))
        }
        return value
    }
}

class KenLmFormatException(message: String) : IOException(message)
