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

/**
 * Tests for the KenLM PROBING navigator. Builds synthetic vocabularies +
 * pre-populated probing-hash buckets so the full unigram → bigram
 * walk-back can be exercised without a real KenLM file.
 */
class KenLmProbingNavigatorTest : FunSpec({

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

    /**
     * Build a [KenLmProbingHash] over [bucketCount] buckets and
     * populate it with the supplied entries. Uses the same MurmurHash
     * the production reader uses so the lookup() walk finds them.
     */
    fun buildProbingHash(
        bucketCount: Long,
        entries: List<ProbingEntry>,
    ): KenLmProbingHash {
        val bytes = ByteArray((bucketCount * KenLmProbingHash.BUCKET_BYTES).toInt())
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        // Initialise every bucket to EMPTY_KEY.
        for (i in 0 until bucketCount) {
            buf.position((i * KenLmProbingHash.BUCKET_BYTES).toInt())
            buf.putLong(KenLmProbingHash.EMPTY_KEY)
            buf.putFloat(0f)
            buf.putFloat(0f)
        }
        // Probe each entry into its slot.
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

    test("bigram lookup returns the populated entry") {
        val vocab = buildVocab("<unk>", "hello", "world")
        val helloIdx = vocab.indexOf("hello")
        val worldIdx = vocab.indexOf("world")
        // Unigram table — one entry for "hello" and one for "world".
        val unigram = buildProbingHash(
            bucketCount = 16,
            entries = listOf(
                ProbingEntry(KenLmProbingHash.packKey(helloIdx, 0), -1.5f, -0.2f),
                ProbingEntry(KenLmProbingHash.packKey(worldIdx, 0), -1.7f, -0.1f),
            ),
        )
        // Bigram table — one entry: hello → world.
        val bigram = buildProbingHash(
            bucketCount = 16,
            entries = listOf(
                ProbingEntry(KenLmProbingHash.packKey(worldIdx, helloIdx), -0.5f, 0f),
            ),
        )
        val nav = KenLmProbingNavigator(
            vocabulary = vocab,
            ordersByLevel = mapOf(1 to unigram, 2 to bigram),
        )
        val hit = nav.lookup(history = listOf("hello"), tail = "world")
        hit shouldBe ProbingEntry(KenLmProbingHash.packKey(worldIdx, helloIdx), -0.5f, 0f)
    }

    test("missing bigram falls back to unigram + parent backoff") {
        val vocab = buildVocab("<unk>", "alpha", "beta", "gamma")
        val alphaIdx = vocab.indexOf("alpha")
        val gammaIdx = vocab.indexOf("gamma")
        val unigram = buildProbingHash(
            bucketCount = 16,
            entries = listOf(
                ProbingEntry(KenLmProbingHash.packKey(alphaIdx, 0), -1.2f, -0.3f),
                ProbingEntry(KenLmProbingHash.packKey(gammaIdx, 0), -2.4f, 0f),
            ),
        )
        // Empty bigram — only the table itself, no entries.
        val bigram = buildProbingHash(bucketCount = 16, entries = emptyList())
        val nav = KenLmProbingNavigator(
            vocabulary = vocab,
            ordersByLevel = mapOf(1 to unigram, 2 to bigram),
        )
        // history "alpha" → tail "gamma": no bigram, falls back to unigram(gamma).
        val score = nav.score(history = listOf("alpha"), tail = "gamma")
        // Unigram logProb -2.4 + parent (alpha) backoff -0.3 = -2.7.
        score shouldBe -2.7f
    }

    test("unknown tail collapses to <unk> slot") {
        val vocab = buildVocab("<unk>", "alpha")
        val unkIdx = vocab.indexOf("<unk>")
        val unigram = buildProbingHash(
            bucketCount = 8,
            entries = listOf(
                ProbingEntry(KenLmProbingHash.packKey(unkIdx, 0), -3.0f, 0f),
            ),
        )
        val bigram = buildProbingHash(bucketCount = 8, entries = emptyList())
        val nav = KenLmProbingNavigator(
            vocabulary = vocab,
            ordersByLevel = mapOf(1 to unigram, 2 to bigram),
        )
        val score = nav.score(history = emptyList(), tail = "neverheardof")
        score shouldBe -3.0f
    }

    test("missing-from-vocab + no <unk> entry returns NEGATIVE_INFINITY") {
        val vocab = buildVocab("<unk>", "alpha")
        val unigram = buildProbingHash(bucketCount = 8, entries = emptyList())
        val bigram = buildProbingHash(bucketCount = 8, entries = emptyList())
        val nav = KenLmProbingNavigator(
            vocabulary = vocab,
            ordersByLevel = mapOf(1 to unigram, 2 to bigram),
        )
        val score = nav.score(history = emptyList(), tail = "ghost")
        score shouldBe Float.NEGATIVE_INFINITY
    }

    test("navigator requires order 1 in the ordersByLevel map") {
        val vocab = buildVocab("<unk>", "alpha")
        val bigram = buildProbingHash(bucketCount = 8, entries = emptyList())
        var caught = false
        try {
            KenLmProbingNavigator(vocabulary = vocab, ordersByLevel = mapOf(2 to bigram))
        } catch (_: IllegalArgumentException) {
            caught = true
        }
        caught shouldBe true
    }
})
