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

/** Test-only [KenLmScorer] that counts how many times `score` ran. */
private class CountingScorer(
    override val modelType: KenLmModelType = KenLmModelType.PROBING,
    override val maxOrder: Int = 3,
) : KenLmScorer {
    var calls: Int = 0
        private set
    val table = HashMap<String, Float>()
    override fun score(history: List<String>, tail: String): Float {
        calls++
        return table["${history.joinToString(" ")}|$tail"] ?: -99f
    }
}

class KenLmScoreCacheTest : FunSpec({

    test("repeat lookups hit the cache and skip the delegate") {
        val under = CountingScorer().apply {
            table["alpha|beta"] = -1.5f
        }
        val cache = KenLmScoreCache(under)
        cache.score(listOf("alpha"), "beta") shouldBe -1.5f
        cache.score(listOf("alpha"), "beta") shouldBe -1.5f
        cache.score(listOf("alpha"), "beta") shouldBe -1.5f
        under.calls shouldBe 1
        cache.hits shouldBe 2
        cache.misses shouldBe 1
    }

    test("different (history, tail) tuples produce different cache entries") {
        val under = CountingScorer().apply {
            table["alpha|beta"] = -1.0f
            table["alpha|gamma"] = -2.0f
            table["delta|beta"] = -3.0f
        }
        val cache = KenLmScoreCache(under)
        cache.score(listOf("alpha"), "beta") shouldBe -1.0f
        cache.score(listOf("alpha"), "gamma") shouldBe -2.0f
        cache.score(listOf("delta"), "beta") shouldBe -3.0f
        under.calls shouldBe 3
        cache.size() shouldBe 3
    }

    test("eviction kicks in at capacity") {
        val under = CountingScorer().apply {
            table.putAll(
                (0..9).associate { "ctx-$it|tail" to it.toFloat() },
            )
        }
        val cache = KenLmScoreCache(under, capacity = 3)
        // Fill with 5 distinct keys; capacity 3 forces evictions.
        repeat(5) { i ->
            cache.score(listOf("ctx-$i"), "tail")
        }
        cache.size() shouldBe 3
    }

    test("clear resets counts + cache") {
        val under = CountingScorer().apply { table["a|b"] = -1f }
        val cache = KenLmScoreCache(under)
        cache.score(listOf("a"), "b")
        cache.size() shouldBe 1
        cache.misses shouldBe 1
        cache.clear()
        cache.size() shouldBe 0
        cache.hits shouldBe 0
        cache.misses shouldBe 0
    }

    test("the unit-separator key prevents history/tail collisions") {
        val under = CountingScorer().apply {
            table["the cat|sat"] = -0.3f
            table["the|cat sat"] = -0.5f
        }
        val cache = KenLmScoreCache(under)
        cache.score(listOf("the", "cat"), "sat") shouldBe -0.3f
        cache.score(listOf("the"), "cat sat") shouldBe -0.5f
        // Both stored separately — cache size is 2, not 1.
        cache.size() shouldBe 2
        under.calls shouldBe 2
    }

    test("capacity must be ≥ 1") {
        var caught = false
        try {
            KenLmScoreCache(CountingScorer(), capacity = 0)
        } catch (_: IllegalArgumentException) {
            caught = true
        }
        caught shouldBe true
    }

    test("score-cache exposes the delegate's modelType + maxOrder") {
        val under = CountingScorer(modelType = KenLmModelType.QUANT_TRIE, maxOrder = 5)
        val cache = KenLmScoreCache(under)
        cache.modelType shouldBe KenLmModelType.QUANT_TRIE
        cache.maxOrder shouldBe 5
    }
})
