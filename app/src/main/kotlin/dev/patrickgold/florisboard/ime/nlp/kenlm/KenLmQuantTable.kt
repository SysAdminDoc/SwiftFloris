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
 * ROADMAP §10.5 Next-3.1e — KenLM `SeparatelyQuantize` centroid table.
 *
 * KenLM's `QUANT_TRIE` model type stores probability + backoff floats
 * as small **centroid indices** into a per-order codebook to keep the
 * binary file compact. Each order owns:
 *
 *  - One probability table of `2^probBits` centroid floats.
 *  - One backoff table of `2^backoffBits` centroid floats. The highest
 *    order never stores backoffs, so its [hasBackoff] flag is `false`
 *    and the [backoffCentroids] array is empty.
 *
 * At lookup time the n-gram entry stores `probBits` + `backoffBits`
 * of payload, the decoder looks them up in the table, and the
 * resulting floats are the log10 probability + log10 backoff for the
 * entry. Reference: kpu/kenlm [`lm/quantize.hh`](https://github.com/kpu/kenlm/blob/master/lm/quantize.hh).
 *
 * This is the pure-Kotlin reader half. Centroid arrays land via the
 * primary constructor (tests build them synthetically) or via the
 * [KenLmQuantTableSet.parseTableSet] companion (production code feeds
 * the bytes from the KenLM binary just past `KenLmBinaryHeader`).
 */
class KenLmQuantTable(
    val probBits: Int,
    val backoffBits: Int,
    val probCentroids: FloatArray,
    val backoffCentroids: FloatArray,
    val hasBackoff: Boolean,
) {
    init {
        require(probBits in 0..8) { "probBits must be in 0..8, was $probBits" }
        require(backoffBits in 0..8) { "backoffBits must be in 0..8, was $backoffBits" }
        require(probCentroids.size == 1 shl probBits) {
            "probCentroids size ${probCentroids.size} does not match 2^$probBits"
        }
        if (hasBackoff) {
            require(backoffCentroids.size == 1 shl backoffBits) {
                "backoffCentroids size ${backoffCentroids.size} does not match 2^$backoffBits"
            }
        } else {
            require(backoffCentroids.isEmpty()) {
                "highest-order table must have an empty backoff codebook (was ${backoffCentroids.size})"
            }
        }
    }

    /** Decode a probability centroid index back to its log10 probability. */
    fun decodeProb(centroidIndex: Int): Float {
        require(centroidIndex in probCentroids.indices) {
            "probIndex $centroidIndex out of bounds [0, ${probCentroids.size})"
        }
        return probCentroids[centroidIndex]
    }

    /**
     * Decode a backoff centroid index back to its log10 backoff. Throws
     * if [hasBackoff] is false (highest-order table).
     */
    fun decodeBackoff(centroidIndex: Int): Float {
        require(hasBackoff) { "highest-order table has no backoff codebook" }
        require(centroidIndex in backoffCentroids.indices) {
            "backoffIndex $centroidIndex out of bounds [0, ${backoffCentroids.size})"
        }
        return backoffCentroids[centroidIndex]
    }

    companion object {
        /** Build a non-highest-order table with both prob + backoff codebooks. */
        fun withBackoff(
            probBits: Int,
            backoffBits: Int,
            probCentroids: FloatArray,
            backoffCentroids: FloatArray,
        ): KenLmQuantTable = KenLmQuantTable(
            probBits = probBits,
            backoffBits = backoffBits,
            probCentroids = probCentroids,
            backoffCentroids = backoffCentroids,
            hasBackoff = true,
        )

        /** Build a highest-order table; backoff codebook is implicit empty. */
        fun highestOrder(
            probBits: Int,
            probCentroids: FloatArray,
        ): KenLmQuantTable = KenLmQuantTable(
            probBits = probBits,
            backoffBits = 0,
            probCentroids = probCentroids,
            backoffCentroids = FloatArray(0),
            hasBackoff = false,
        )
    }
}

/**
 * One [KenLmQuantTable] per n-gram order. Order indexing is 1-based to
 * match KenLM convention: `tableFor(1)` is the unigram table,
 * `tableFor(order)` is the highest-order table.
 */
class KenLmQuantTableSet(
    val order: Int,
    private val tables: Array<KenLmQuantTable>,
) {
    init {
        require(order >= 1) { "order must be ≥ 1, was $order" }
        require(tables.size == order) {
            "tables.size ${tables.size} does not match order $order"
        }
        require(!tables.last().hasBackoff) {
            "highest-order table must not carry a backoff codebook"
        }
        for (i in 0 until order - 1) {
            require(tables[i].hasBackoff) {
                "non-highest-order table at index $i must carry a backoff codebook"
            }
        }
    }

    /** Look up the table for [ngramOrder] (1-based; 1 = unigram, [order] = highest). */
    fun tableFor(ngramOrder: Int): KenLmQuantTable {
        require(ngramOrder in 1..order) {
            "ngramOrder $ngramOrder out of bounds [1, $order]"
        }
        return tables[ngramOrder - 1]
    }

    companion object {
        /**
         * Parse a quant-table set from the bytes that follow
         * `KenLmBinaryHeader` in a `QUANT_TRIE` model file. Wire
         * format mirrors `lm/quantize.hh::SeparatelyQuantize::SetupMemory`:
         * for each order, write `2^probBits` little-endian float32
         * prob centroids followed by `2^backoffBits` float32 backoff
         * centroids (no backoff block for the highest order).
         *
         * Caller supplies [probBits] + [backoffBits] (KenLM stores
         * those as two uint8 fields immediately after the standard
         * header — typical values are `probBits = 8`, `backoffBits = 8`).
         */
        fun parseTableSet(
            buffer: ByteBuffer,
            order: Int,
            probBits: Int,
            backoffBits: Int,
        ): KenLmQuantTableSet {
            require(order >= 1) { "order must be ≥ 1, was $order" }
            require(probBits in 0..8) { "probBits must be in 0..8, was $probBits" }
            require(backoffBits in 0..8) { "backoffBits must be in 0..8, was $backoffBits" }
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            val probCount = 1 shl probBits
            val backoffCount = 1 shl backoffBits
            val tables = Array(order) { idx ->
                val isHighestOrder = idx == order - 1
                val probArr = FloatArray(probCount) { buffer.float }
                if (isHighestOrder) {
                    KenLmQuantTable.highestOrder(probBits = probBits, probCentroids = probArr)
                } else {
                    val backoffArr = FloatArray(backoffCount) { buffer.float }
                    KenLmQuantTable.withBackoff(
                        probBits = probBits,
                        backoffBits = backoffBits,
                        probCentroids = probArr,
                        backoffCentroids = backoffArr,
                    )
                }
            }
            return KenLmQuantTableSet(order = order, tables = tables)
        }
    }
}
