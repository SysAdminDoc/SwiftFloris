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
import java.nio.ByteBuffer
import java.nio.ByteOrder

class KenLmQuantTableTest : FunSpec({

    test("withBackoff round-trips prob + backoff indices") {
        val table = KenLmQuantTable.withBackoff(
            probBits = 2,
            backoffBits = 2,
            probCentroids = floatArrayOf(-1.0f, -2.0f, -3.0f, -4.0f),
            backoffCentroids = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f),
        )
        table.decodeProb(0) shouldBe -1.0f
        table.decodeProb(3) shouldBe -4.0f
        table.decodeBackoff(2) shouldBe 0.3f
    }

    test("highestOrder ships an empty backoff codebook and throws on backoff decode") {
        val table = KenLmQuantTable.highestOrder(
            probBits = 3,
            probCentroids = FloatArray(8) { it * -0.5f },
        )
        table.hasBackoff shouldBe false
        table.backoffCentroids.size shouldBe 0
        table.decodeProb(7) shouldBe -3.5f
        var caught = false
        try {
            table.decodeBackoff(0)
        } catch (_: IllegalArgumentException) {
            caught = true
        }
        caught shouldBe true
    }

    test("constructor rejects probCentroids size mismatch") {
        var caught = false
        try {
            KenLmQuantTable.withBackoff(
                probBits = 3,
                backoffBits = 2,
                probCentroids = FloatArray(7),
                backoffCentroids = FloatArray(4),
            )
        } catch (_: IllegalArgumentException) {
            caught = true
        }
        caught shouldBe true
    }

    test("KenLmQuantTableSet exposes per-order tables 1-indexed") {
        val unigram = KenLmQuantTable.withBackoff(
            probBits = 1,
            backoffBits = 1,
            probCentroids = floatArrayOf(-0.5f, -1.5f),
            backoffCentroids = floatArrayOf(0.0f, 0.1f),
        )
        val bigram = KenLmQuantTable.withBackoff(
            probBits = 1,
            backoffBits = 1,
            probCentroids = floatArrayOf(-0.7f, -1.7f),
            backoffCentroids = floatArrayOf(0.05f, 0.15f),
        )
        val trigram = KenLmQuantTable.highestOrder(
            probBits = 1,
            probCentroids = floatArrayOf(-0.9f, -1.9f),
        )
        val set = KenLmQuantTableSet(order = 3, tables = arrayOf(unigram, bigram, trigram))
        set.tableFor(1).decodeProb(0) shouldBe -0.5f
        set.tableFor(2).decodeBackoff(1) shouldBe 0.15f
        set.tableFor(3).hasBackoff shouldBe false
    }

    test("KenLmQuantTableSet rejects highest order that still carries a backoff codebook") {
        val table1 = KenLmQuantTable.withBackoff(
            probBits = 1,
            backoffBits = 1,
            probCentroids = floatArrayOf(-0.5f, -1.5f),
            backoffCentroids = floatArrayOf(0.0f, 0.1f),
        )
        // Two tables, both with backoff → highest-order invariant violated.
        var caught = false
        try {
            KenLmQuantTableSet(order = 2, tables = arrayOf(table1, table1))
        } catch (_: IllegalArgumentException) {
            caught = true
        }
        caught shouldBe true
    }

    test("parseTableSet reads centroids in little-endian order with no backoff block at top order") {
        val probBits = 1
        val backoffBits = 1
        val order = 3
        val probCount = 1 shl probBits
        val backoffCount = 1 shl backoffBits
        // 2 prob + 2 backoff per non-top order × 2 orders + 2 prob for top.
        val totalFloats = (order - 1) * (probCount + backoffCount) + probCount
        val buffer = ByteBuffer.allocate(totalFloats * 4).order(ByteOrder.LITTLE_ENDIAN)
        // Order 1: probs -1, -2; backoffs 0.1, 0.2.
        buffer.putFloat(-1f); buffer.putFloat(-2f)
        buffer.putFloat(0.1f); buffer.putFloat(0.2f)
        // Order 2: probs -3, -4; backoffs 0.3, 0.4.
        buffer.putFloat(-3f); buffer.putFloat(-4f)
        buffer.putFloat(0.3f); buffer.putFloat(0.4f)
        // Order 3 (highest): probs -5, -6 — no backoff block.
        buffer.putFloat(-5f); buffer.putFloat(-6f)
        buffer.position(0)
        val set = KenLmQuantTableSet.parseTableSet(
            buffer = buffer,
            order = order,
            probBits = probBits,
            backoffBits = backoffBits,
        )
        set.tableFor(1).decodeProb(0) shouldBe -1f
        set.tableFor(1).decodeBackoff(1) shouldBe 0.2f
        set.tableFor(2).decodeProb(1) shouldBe -4f
        set.tableFor(2).decodeBackoff(0) shouldBe 0.3f
        set.tableFor(3).decodeProb(1) shouldBe -6f
        set.tableFor(3).hasBackoff shouldBe false
    }
})
