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
import java.nio.charset.StandardCharsets

class KenLmModelTypeDispatchTest : FunSpec({

    fun buildVocab(vararg tokens: String): KenLmVocabulary {
        val byteCount = tokens.sumOf { it.toByteArray(StandardCharsets.UTF_8).size + 1 }
        val buf = ByteBuffer.allocate(16 + byteCount).order(ByteOrder.LITTLE_ENDIAN)
        buf.putLong(tokens.size.toLong())
        buf.putLong(byteCount.toLong())
        for (tok in tokens) {
            buf.put(tok.toByteArray(StandardCharsets.UTF_8))
            buf.put(0.toByte())
        }
        buf.position(0)
        return KenLmVocabulary.parse(buf)!!
    }

    fun buildProbingHash(bucketCount: Long, entries: List<ProbingEntry>): KenLmProbingHash {
        val bytes = ByteArray((bucketCount * KenLmProbingHash.BUCKET_BYTES).toInt())
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until bucketCount) {
            buf.position((i * KenLmProbingHash.BUCKET_BYTES).toInt())
            buf.putLong(KenLmProbingHash.EMPTY_KEY)
            buf.putFloat(0f); buf.putFloat(0f)
        }
        val ph = KenLmProbingHash(buf, bucketCount)
        for (entry in entries) {
            var bucket = (Math.floorMod(ph.murmur64(entry.key), bucketCount)).toInt()
            var attempts = 0
            while (attempts < KenLmProbingHash.MAX_PROBE_DEPTH) {
                val offset = bucket * KenLmProbingHash.BUCKET_BYTES
                buf.position(offset)
                val existingKey = buf.long
                if (existingKey == KenLmProbingHash.EMPTY_KEY) {
                    buf.position(offset)
                    buf.putLong(entry.key)
                    buf.putFloat(entry.logProb)
                    buf.putFloat(entry.logBackoff)
                    break
                }
                bucket = ((bucket + 1L) % bucketCount).toInt()
                attempts++
            }
        }
        return ph
    }

    test("PROBING dispatch builds a probing-backed scorer") {
        val vocab = buildVocab("<unk>", "a")
        val aIdx = vocab.indexOf("a")
        val uni = buildProbingHash(
            bucketCount = 8,
            entries = listOf(
                ProbingEntry(KenLmProbingHash.packKey(aIdx, 0), -1.5f, 0f),
            ),
        )
        val bi = buildProbingHash(bucketCount = 8, entries = emptyList())
        val scorer = KenLmModelTypeDispatch.build(
            modelType = KenLmModelType.PROBING,
            vocabulary = vocab,
            probingPath = KenLmModelTypeDispatch.ProbingInputs(
                ordersByLevel = mapOf(1 to uni, 2 to bi),
            ),
        )
        scorer.modelType shouldBe KenLmModelType.PROBING
        scorer.score(emptyList(), "a") shouldBe -1.5f
    }

    test("TRIE dispatch builds a trie-backed scorer") {
        val vocab = buildVocab("<unk>", "a", "b")
        val aIdx = vocab.indexOf("a")
        val bIdx = vocab.indexOf("b")
        val uni = TrieOrderTable.fromEntries(
            order = 1,
            list = listOf(
                TrieEntry(aIdx, 0, aIdx, -1.0f, -0.3f),
                TrieEntry(bIdx, 0, bIdx, -1.4f, 0f),
            ),
        )
        val bi = TrieOrderTable.fromEntries(order = 2, list = emptyList())
        val scorer = KenLmModelTypeDispatch.build(
            modelType = KenLmModelType.TRIE,
            vocabulary = vocab,
            triePath = KenLmModelTypeDispatch.TrieInputs(
                ordersByLevel = mapOf(1 to uni, 2 to bi),
            ),
        )
        scorer.modelType shouldBe KenLmModelType.TRIE
        scorer.score(listOf("a"), "b") shouldBe -1.7f  // unigram(b) -1.4 + parent(a) -0.3
    }

    test("QUANT_TRIE dispatches to the trie navigator") {
        val vocab = buildVocab("<unk>", "a")
        val aIdx = vocab.indexOf("a")
        val uni = TrieOrderTable.fromEntries(
            order = 1,
            list = listOf(TrieEntry(aIdx, 0, aIdx, -1.0f, 0f)),
        )
        val bi = TrieOrderTable.fromEntries(order = 2, list = emptyList())
        val scorer = KenLmModelTypeDispatch.build(
            modelType = KenLmModelType.QUANT_TRIE,
            vocabulary = vocab,
            triePath = KenLmModelTypeDispatch.TrieInputs(
                ordersByLevel = mapOf(1 to uni, 2 to bi),
            ),
        )
        scorer.modelType shouldBe KenLmModelType.QUANT_TRIE
        scorer.score(emptyList(), "a") shouldBe -1.0f
    }

    test("UNKNOWN model type throws IllegalArgumentException") {
        val vocab = buildVocab("<unk>", "a")
        var caught = false
        try {
            KenLmModelTypeDispatch.build(
                modelType = KenLmModelType.UNKNOWN,
                vocabulary = vocab,
            )
        } catch (_: IllegalArgumentException) {
            caught = true
        }
        caught shouldBe true
    }

    test("PROBING dispatch without probingPath throws") {
        val vocab = buildVocab("<unk>", "a")
        var caught = false
        try {
            KenLmModelTypeDispatch.build(
                modelType = KenLmModelType.PROBING,
                vocabulary = vocab,
            )
        } catch (_: IllegalArgumentException) {
            caught = true
        }
        caught shouldBe true
    }
})
