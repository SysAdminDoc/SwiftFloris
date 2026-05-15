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

class KenLmTrieNavigatorTest : FunSpec({

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

    test("trie bigram lookup returns the populated entry") {
        val vocab = buildVocab("<unk>", "alpha", "beta")
        val alphaIdx = vocab.indexOf("alpha")
        val betaIdx = vocab.indexOf("beta")
        val unigramTable = TrieOrderTable.fromEntries(
            order = 1,
            list = listOf(
                TrieEntry(
                    entryIndex = alphaIdx, parentEntryIndex = 0,
                    tailVocabIndex = alphaIdx, logProb = -1.0f, logBackoff = -0.4f,
                ),
                TrieEntry(
                    entryIndex = betaIdx, parentEntryIndex = 0,
                    tailVocabIndex = betaIdx, logProb = -1.5f, logBackoff = 0f,
                ),
            ),
        )
        val bigramTable = TrieOrderTable.fromEntries(
            order = 2,
            list = listOf(
                TrieEntry(
                    entryIndex = 0, parentEntryIndex = alphaIdx,
                    tailVocabIndex = betaIdx, logProb = -0.5f, logBackoff = 0f,
                ),
            ),
        )
        val nav = KenLmTrieNavigator(
            vocabulary = vocab,
            ordersByLevel = mapOf(1 to unigramTable, 2 to bigramTable),
        )
        nav.lookup(history = listOf("alpha"), tail = "beta")?.logProb shouldBe -0.5f
        nav.score(history = listOf("alpha"), tail = "beta") shouldBe -0.5f
    }

    test("missing trie bigram falls back to unigram + parent backoff") {
        val vocab = buildVocab("<unk>", "alpha", "beta")
        val alphaIdx = vocab.indexOf("alpha")
        val betaIdx = vocab.indexOf("beta")
        val unigramTable = TrieOrderTable.fromEntries(
            order = 1,
            list = listOf(
                TrieEntry(
                    entryIndex = alphaIdx, parentEntryIndex = 0,
                    tailVocabIndex = alphaIdx, logProb = -1.0f, logBackoff = -0.4f,
                ),
                TrieEntry(
                    entryIndex = betaIdx, parentEntryIndex = 0,
                    tailVocabIndex = betaIdx, logProb = -1.5f, logBackoff = 0f,
                ),
            ),
        )
        val bigramTable = TrieOrderTable.fromEntries(order = 2, list = emptyList())
        val nav = KenLmTrieNavigator(
            vocabulary = vocab,
            ordersByLevel = mapOf(1 to unigramTable, 2 to bigramTable),
        )
        // Bigram (alpha → beta) absent; fall back to unigram(beta) -1.5
        // plus parent backoff unigram(alpha) -0.4 = -1.9.
        nav.score(history = listOf("alpha"), tail = "beta") shouldBe -1.9f
    }

    test("absent tail returns NEGATIVE_INFINITY when no <unk> entry exists") {
        val vocab = buildVocab("<unk>", "alpha")
        val unigramTable = TrieOrderTable.fromEntries(order = 1, list = emptyList())
        val bigramTable = TrieOrderTable.fromEntries(order = 2, list = emptyList())
        val nav = KenLmTrieNavigator(
            vocabulary = vocab,
            ordersByLevel = mapOf(1 to unigramTable, 2 to bigramTable),
        )
        nav.score(history = emptyList(), tail = "ghost") shouldBe Float.NEGATIVE_INFINITY
    }

    test("trie navigator requires order 1") {
        val vocab = buildVocab("<unk>", "alpha")
        val bigramTable = TrieOrderTable.fromEntries(order = 2, list = emptyList())
        var caught = false
        try {
            KenLmTrieNavigator(
                vocabulary = vocab,
                ordersByLevel = mapOf(2 to bigramTable),
            )
        } catch (_: IllegalArgumentException) {
            caught = true
        }
        caught shouldBe true
    }

    test("trigram chain walks both bigram and trigram tables") {
        val vocab = buildVocab("<unk>", "a", "b", "c")
        val aIdx = vocab.indexOf("a")
        val bIdx = vocab.indexOf("b")
        val cIdx = vocab.indexOf("c")
        val uni = TrieOrderTable.fromEntries(
            order = 1,
            list = listOf(
                TrieEntry(aIdx, 0, aIdx, -1.0f, -0.1f),
                TrieEntry(bIdx, 0, bIdx, -1.1f, -0.2f),
                TrieEntry(cIdx, 0, cIdx, -1.2f, 0f),
            ),
        )
        val bigramEntryIndex = 99
        val bi = TrieOrderTable.fromEntries(
            order = 2,
            list = listOf(
                TrieEntry(
                    entryIndex = bigramEntryIndex,
                    parentEntryIndex = aIdx,
                    tailVocabIndex = bIdx,
                    logProb = -0.5f, logBackoff = -0.05f,
                ),
            ),
        )
        val tri = TrieOrderTable.fromEntries(
            order = 3,
            list = listOf(
                TrieEntry(
                    entryIndex = 0,
                    parentEntryIndex = bigramEntryIndex,
                    tailVocabIndex = cIdx,
                    logProb = -0.3f, logBackoff = 0f,
                ),
            ),
        )
        val nav = KenLmTrieNavigator(
            vocabulary = vocab,
            ordersByLevel = mapOf(1 to uni, 2 to bi, 3 to tri),
        )
        nav.score(history = listOf("a", "b"), tail = "c") shouldBe -0.3f
    }
})
