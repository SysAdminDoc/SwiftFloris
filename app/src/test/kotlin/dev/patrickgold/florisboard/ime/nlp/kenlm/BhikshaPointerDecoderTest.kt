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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.random.Random

class BhikshaPointerDecoderTest : FunSpec({

    test("round-trip a sorted pointer array at lowBitsWidth=8") {
        val pointers = longArrayOf(0, 5, 17, 33, 64, 129, 256, 511, 1024, 2047)
        val decoder = BhikshaPointerDecoder.encode(pointers, lowBitsWidth = 8)
        decoder.entryCount shouldBe pointers.size
        for (i in pointers.indices) {
            decoder.decode(i) shouldBe pointers[i]
        }
    }

    test("round-trip at the degenerate lowBitsWidth=0 (everything goes to high bits)") {
        val pointers = longArrayOf(0, 1, 1, 2, 3, 5, 8, 13, 21, 34)
        val decoder = BhikshaPointerDecoder.encode(pointers, lowBitsWidth = 0)
        for (i in pointers.indices) {
            decoder.decode(i) shouldBe pointers[i]
        }
    }

    test("round-trip with duplicates and a long monotone run") {
        val pointers = longArrayOf(0, 0, 0, 1, 1, 7, 7, 7, 9, 9, 9, 9, 100, 1000)
        val decoder = BhikshaPointerDecoder.encode(pointers, lowBitsWidth = 4)
        for (i in pointers.indices) {
            decoder.decode(i) shouldBe pointers[i]
        }
    }

    test("randomized round-trip survives 200 random sorted arrays") {
        val rng = Random(seed = 0xC0FFEEL)
        repeat(200) {
            val n = rng.nextInt(0, 64)
            val arr = LongArray(n) { rng.nextLong(0, 1L shl 20) }
            arr.sort()
            val lowBits = rng.nextInt(0, 18)
            val decoder = BhikshaPointerDecoder.encode(arr, lowBitsWidth = lowBits)
            for (i in arr.indices) {
                decoder.decode(i) shouldBe arr[i]
            }
        }
    }

    test("encode rejects non-monotone input") {
        var caught = false
        try {
            BhikshaPointerDecoder.encode(longArrayOf(5, 4, 6), lowBitsWidth = 2)
        } catch (_: IllegalArgumentException) {
            caught = true
        }
        caught shouldBe true
    }

    test("decode rejects out-of-range entry index") {
        val decoder = BhikshaPointerDecoder.encode(longArrayOf(0, 1, 2), lowBitsWidth = 1)
        var caught = false
        try {
            decoder.decode(3)
        } catch (_: IllegalArgumentException) {
            caught = true
        }
        caught shouldBe true
    }

    test("empty pointer array yields a valid zero-entry decoder") {
        val decoder = BhikshaPointerDecoder.encode(longArrayOf(), lowBitsWidth = 4)
        decoder.entryCount shouldBe 0
    }
})
