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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.nio.ByteBuffer
import java.nio.ByteOrder

private fun buildTable(bucketCount: Int, entries: Map<Long, Pair<Float, Float>>): ByteBuffer {
    val buffer = ByteBuffer.allocate(bucketCount * KenLmProbingHash.BUCKET_BYTES)
        .order(ByteOrder.LITTLE_ENDIAN)
    // Initialise every bucket to the empty-key sentinel.
    for (i in 0 until bucketCount) {
        val offset = i * KenLmProbingHash.BUCKET_BYTES
        buffer.position(offset)
        buffer.putLong(KenLmProbingHash.EMPTY_KEY)
        buffer.putFloat(0f)
        buffer.putFloat(0f)
    }
    // Place each entry at its murmur64 bucket (or the next open one).
    val probe = KenLmProbingHash(buffer.duplicate(), bucketCount.toLong())
    for ((key, value) in entries) {
        var bucket = (Math.floorMod(probe.murmur64(key), bucketCount.toLong())).toInt()
        var probes = 0
        while (probes < bucketCount) {
            val offset = bucket * KenLmProbingHash.BUCKET_BYTES
            buffer.position(offset)
            val existing = buffer.long
            if (existing == KenLmProbingHash.EMPTY_KEY) {
                buffer.position(offset)
                buffer.putLong(key)
                buffer.putFloat(value.first)
                buffer.putFloat(value.second)
                break
            }
            bucket = (bucket + 1) % bucketCount
            probes++
        }
    }
    return buffer
}

class KenLmProbingHashTest : FunSpec({
    test("packKey packs vocabIndex_top into the high 32 bits") {
        val packed = KenLmProbingHash.packKey(
            tailVocabIndex = 0x12345678,
            parentEntryIndex = 0x1ABCDEF0,
        )
        ((packed ushr 32) and 0xFFFF_FFFFL) shouldBe 0x12345678L
        (packed and 0xFFFF_FFFFL) shouldBe 0x1ABCDEF0L
    }

    test("constructor rejects non-positive bucket count") {
        shouldThrow<IllegalArgumentException> {
            KenLmProbingHash(ByteBuffer.allocate(0), 0L)
        }
    }

    test("lookup of a stored key returns its prob + backoff") {
        val key = KenLmProbingHash.packKey(42, 7)
        val buffer = buildTable(
            bucketCount = 8,
            entries = mapOf(key to (-2.5f to -0.5f)),
        )
        val table = KenLmProbingHash(buffer, 8L)
        val entry = table.lookup(key).shouldNotBeNull()
        entry.key shouldBe key
        entry.logProb shouldBe -2.5f
        entry.logBackoff shouldBe -0.5f
    }

    test("lookup of an absent key returns null") {
        val table = KenLmProbingHash(
            buildTable(bucketCount = 8, entries = emptyMap()),
            8L,
        )
        table.lookup(KenLmProbingHash.packKey(1, 1)).shouldBeNull()
    }

    test("linear probing finds an entry that collided into the next bucket") {
        // Plant 4 entries; the probe-collision path is exercised by the
        // buildTable helper inserting them with chained probing too.
        val keys = listOf(
            KenLmProbingHash.packKey(1, 0),
            KenLmProbingHash.packKey(2, 0),
            KenLmProbingHash.packKey(3, 0),
            KenLmProbingHash.packKey(4, 0),
        )
        val buffer = buildTable(
            bucketCount = 4,
            entries = keys.mapIndexed { i, k -> k to (-i.toFloat() to 0f) }.toMap(),
        )
        val table = KenLmProbingHash(buffer, 4L)
        keys.forEachIndexed { i, k ->
            val entry = table.lookup(k).shouldNotBeNull()
            entry.logProb shouldBe -i.toFloat()
        }
    }

    test("murmur64 is deterministic for the same input") {
        val table = KenLmProbingHash(ByteBuffer.allocate(64), 4L)
        table.murmur64(42L) shouldBe table.murmur64(42L)
    }
})
