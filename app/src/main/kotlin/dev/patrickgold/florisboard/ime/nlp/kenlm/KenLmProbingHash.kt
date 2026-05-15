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

/**
 * ROADMAP §7 Next-3.1c — KenLM PROBING-model search-arena scaffold.
 *
 * The PROBING model type (id = 0 / 1 = REST_PROBING) lays the search
 * structure out as an **open-addressed linear-probing hash table**
 * keyed on a `(vocabIndex_n_gram_tail, parentEntryIndex)` pair. Per
 * `lm/search_hashed.hh`, each entry is:
 *
 *  - uint64 key  — packed (vocabIndex_top << 32) | parentEntryIndex
 *  - float prob  — log10-base probability
 *  - float backoff — log10-base backoff (zero for highest-order entries)
 *
 * The table is sized `multiplier × entryCount` where `multiplier` is
 * the `probing_multiplier` byte from the header's
 * `FixedWidthParameters`. Empty buckets carry key = 0xFFFF_FFFF_FFFF_FFFF.
 *
 * This scaffold ships the **bucket-walking iteration primitive** the
 * actual log-prob lookup will use. The probability/backoff decode +
 * the n-gram traversal across order-by-order tables stays Next-3.1d.
 *
 * Reference: https://github.com/kpu/kenlm/blob/master/lm/search_hashed.hh
 */
class KenLmProbingHash(
    private val buffer: ByteBuffer,
    val bucketCount: Long,
) {
    init {
        require(bucketCount > 0L) { "bucketCount must be positive; was $bucketCount" }
        require(bucketCount <= MAX_BUCKETS) {
            "bucketCount must be <= $MAX_BUCKETS; was $bucketCount"
        }
        buffer.order(ByteOrder.LITTLE_ENDIAN)
    }

    /**
     * Lookup the n-gram entry keyed by [packedKey]. Returns the
     * decoded entry or null when the bucket walk doesn't find a
     * matching key (i.e. the n-gram is not in this order's table).
     *
     * Hash collision resolution uses **linear probing**: start at
     * `hash(key) % bucketCount` and advance one bucket at a time
     * until either a match or an empty bucket is reached.
     */
    fun lookup(packedKey: Long): ProbingEntry? {
        var bucket = (Math.floorMod(murmur64(packedKey), bucketCount)).toInt()
        var probes = 0
        while (probes < MAX_PROBE_DEPTH) {
            val entry = readBucket(bucket)
            if (entry.key == EMPTY_KEY) return null
            if (entry.key == packedKey) return entry
            bucket = ((bucket + 1L) % bucketCount).toInt()
            probes++
        }
        return null
    }

    private fun readBucket(bucket: Int): ProbingEntry {
        val offset = bucket * BUCKET_BYTES
        if (offset < 0 || offset + BUCKET_BYTES > buffer.capacity()) {
            return ProbingEntry(EMPTY_KEY, 0f, 0f)
        }
        // Save and restore position for thread-safety.
        val originalPos = buffer.position()
        try {
            buffer.position(offset)
            val key = buffer.long
            val prob = buffer.float
            val backoff = buffer.float
            return ProbingEntry(key = key, logProb = prob, logBackoff = backoff)
        } finally {
            buffer.position(originalPos)
        }
    }

    /**
     * MurmurHash64A — KenLM uses this for `probing_multiplier`-sized
     * open-addressed tables. Public-domain implementation matching
     * `util/murmur_hash.cc`.
     */
    @Suppress("MagicNumber")
    internal fun murmur64(value: Long): Long {
        val seed = 0L
        val m = -0x395b586ca42e166bL
        val r = 47
        var h = seed xor (8L * m)
        var k = value
        k *= m
        k = k xor (k ushr r)
        k *= m
        h = h xor k
        h *= m
        h = h xor (h ushr r)
        h *= m
        h = h xor (h ushr r)
        return h
    }

    companion object {
        /** Packed-key sentinel for an empty bucket (per `lm/search_hashed.hh`). */
        const val EMPTY_KEY: Long = -1L  // 0xFFFF_FFFF_FFFF_FFFFL

        /** 8 bytes key + 4 bytes prob + 4 bytes backoff = 16 bytes per bucket. */
        const val BUCKET_BYTES: Int = 16

        /** Hard cap on hash table size — anything larger forces the
         *  PROBING model toward the dictionary-pack addon path. */
        const val MAX_BUCKETS: Long = 64_000_000L

        /** Linear-probe depth ceiling. KenLM tables are typically
         *  loaded at < 0.8 fill, so collision chains are short; cap
         *  at 256 for safety. */
        const val MAX_PROBE_DEPTH: Int = 256

        /**
         * Pack a `(vocabIndex_top, parentEntryIndex)` pair into the
         * 64-bit key shape KenLM uses on disk. The top 32 bits are
         * the n-gram-tail vocab index, the low 32 bits are the
         * parent-entry index from the previous order's table.
         */
        fun packKey(tailVocabIndex: Int, parentEntryIndex: Int): Long {
            require(tailVocabIndex >= 0) { "tailVocabIndex must be non-negative" }
            require(parentEntryIndex >= 0) { "parentEntryIndex must be non-negative" }
            return (tailVocabIndex.toLong() shl 32) or
                (parentEntryIndex.toLong() and 0xFFFF_FFFFL)
        }
    }
}

/**
 * One decoded bucket from a [KenLmProbingHash] lookup. The base-10
 * log values are exactly what `lm/query.cc` reports for the same
 * n-gram so a JNI-free Kotlin scorer can match upstream output.
 */
data class ProbingEntry(
    val key: Long,
    val logProb: Float,
    val logBackoff: Float,
)
