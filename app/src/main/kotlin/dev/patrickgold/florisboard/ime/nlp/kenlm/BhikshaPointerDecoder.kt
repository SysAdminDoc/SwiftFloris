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

/**
 * ROADMAP §10.5 Next-3.1d — Bhiksha "ArrayBhiksha" next-pointer decoder.
 *
 * KenLM `TRIE` / `QUANT_TRIE` models store the per-entry next-pointer
 * (offset of the first child in the next-order block) using a compact
 * two-part encoding:
 *
 *  - A fixed-width **low bits** array packs the bottom `lowBitsWidth`
 *    bits of every pointer head-to-tail inside [lowBitsArena].
 *  - A monotonic **high bits** bitmap inside [highBitsBitmap] encodes
 *    the top bits as bit-positions: for the `i`-th pointer with high
 *    value `h`, bit `(h + i)` is set. Since the pointers are sorted
 *    non-decreasingly by construction, those bit-positions are
 *    strictly increasing, which lets the decoder recover entry `i`'s
 *    high by locating the `(i + 1)`-th set bit and subtracting `i`.
 *
 * Reference: kpu/kenlm [`util/bit_packing.hh`](https://github.com/kpu/kenlm/blob/master/util/bit_packing.hh)
 * + [`lm/trie.hh`](https://github.com/kpu/kenlm/blob/master/lm/trie.hh)
 * (the `ArrayBhiksha` class).
 *
 * This is a pure-Kotlin reader — it doesn't replace the JNI bring-up
 * planned for Next-3.1b's "real" scoring path, but it gives the
 * search-arena navigator (Next-3.1c) a way to walk the trie body for
 * `TRIE` / `QUANT_TRIE` model types without taking on a native dep.
 */
class BhikshaPointerDecoder(
    val lowBitsWidth: Int,
    private val lowBitsArena: ByteArray,
    private val highBitsBitmap: ByteArray,
    val entryCount: Int,
) {

    init {
        require(lowBitsWidth in 0..62) {
            "lowBitsWidth must be in 0..62, was $lowBitsWidth"
        }
        require(entryCount >= 0) { "entryCount must be non-negative, was $entryCount" }
        val requiredLowBytes = ((entryCount.toLong() * lowBitsWidth) + 7) / 8
        require(lowBitsArena.size.toLong() >= requiredLowBytes) {
            "lowBitsArena too small: have ${lowBitsArena.size}, need $requiredLowBytes"
        }
    }

    /** Decode the pointer at [entryIndex] (0-based). */
    fun decode(entryIndex: Int): Long {
        require(entryIndex in 0 until entryCount) {
            "entryIndex $entryIndex out of bounds [0, $entryCount)"
        }
        val low = readLowBits(entryIndex)
        val high = readHighBits(entryIndex)
        return (high shl lowBitsWidth) or low
    }

    private fun readLowBits(entryIndex: Int): Long {
        if (lowBitsWidth == 0) return 0L
        val bitOffset = entryIndex.toLong() * lowBitsWidth
        val byteOffset = (bitOffset ushr 3).toInt()
        val bitInByte = (bitOffset and 7L).toInt()
        var acc = 0L
        val bytesToRead = ((bitInByte + lowBitsWidth + 7) ushr 3).coerceAtMost(8)
        for (i in 0 until bytesToRead) {
            val src = byteOffset + i
            if (src < lowBitsArena.size) {
                acc = acc or ((lowBitsArena[src].toLong() and 0xFFL) shl (i * 8))
            }
        }
        acc = acc ushr bitInByte
        val mask = if (lowBitsWidth == 64) -1L else (1L shl lowBitsWidth) - 1L
        return acc and mask
    }

    private fun readHighBits(entryIndex: Int): Long {
        // Find the (entryIndex + 1)-th set bit, subtract entryIndex.
        var setBitsRemaining = entryIndex + 1
        for (byteIdx in highBitsBitmap.indices) {
            val byte = highBitsBitmap[byteIdx].toInt() and 0xFF
            if (byte == 0) continue
            for (bit in 0..7) {
                if (((byte ushr bit) and 1) == 1) {
                    setBitsRemaining--
                    if (setBitsRemaining == 0) {
                        return (byteIdx * 8L + bit) - entryIndex.toLong()
                    }
                }
            }
        }
        throw IllegalStateException(
            "high-bits bitmap underflow: only ${entryIndex + 1 - setBitsRemaining} " +
                "set bits available, asked for entry $entryIndex",
        )
    }

    companion object {
        /**
         * Encode [pointers] into a fresh decoder. Useful for tests + for
         * synthetic search-arena fixtures.  Real KenLM files arrive with
         * the two arrays already populated and just feed the
         * primary [BhikshaPointerDecoder] constructor.
         *
         * [pointers] must be sorted non-decreasingly. [lowBitsWidth]
         * picks the trade-off: smaller `lowBitsWidth` shrinks the
         * low-bits arena but grows the high-bits bitmap. KenLM picks
         * `floor(log2(maxPointer / N))` when `N > 0`; tests can pass
         * any width in `0..62`.
         */
        fun encode(pointers: LongArray, lowBitsWidth: Int): BhikshaPointerDecoder {
            require(lowBitsWidth in 0..62)
            require(pointers.all { it >= 0 }) { "pointers must be non-negative" }
            for (i in 1 until pointers.size) {
                require(pointers[i] >= pointers[i - 1]) {
                    "pointers must be non-decreasing for Bhiksha encoding"
                }
            }
            val n = pointers.size
            val lowMask = if (lowBitsWidth == 64) -1L else (1L shl lowBitsWidth) - 1L
            val lowBytes = ByteArray((((n.toLong() * lowBitsWidth) + 7) / 8).toInt())
            for (i in 0 until n) {
                val low = pointers[i] and lowMask
                val bitOffset = i.toLong() * lowBitsWidth
                var byteIdx = (bitOffset ushr 3).toInt()
                var bitInByte = (bitOffset and 7L).toInt()
                var remaining = lowBitsWidth
                var value = low
                while (remaining > 0) {
                    val take = minOf(8 - bitInByte, remaining)
                    val chunk = (value and ((1L shl take) - 1L)).toInt()
                    val shifted = (chunk shl bitInByte) and 0xFF
                    lowBytes[byteIdx] = ((lowBytes[byteIdx].toInt() and 0xFF) or shifted).toByte()
                    value = value ushr take
                    remaining -= take
                    bitInByte = 0
                    byteIdx++
                }
            }
            val maxBitPos = if (n == 0) {
                0
            } else {
                val maxHigh = pointers[n - 1] ushr lowBitsWidth
                (maxHigh + (n - 1)).toInt()
            }
            val highBytes = ByteArray((maxBitPos / 8) + 1)
            for (i in 0 until n) {
                val high = pointers[i] ushr lowBitsWidth
                val pos = (high + i).toInt()
                highBytes[pos / 8] =
                    ((highBytes[pos / 8].toInt() and 0xFF) or (1 shl (pos % 8))).toByte()
            }
            return BhikshaPointerDecoder(
                lowBitsWidth = lowBitsWidth,
                lowBitsArena = lowBytes,
                highBitsBitmap = highBytes,
                entryCount = n,
            )
        }
    }
}
