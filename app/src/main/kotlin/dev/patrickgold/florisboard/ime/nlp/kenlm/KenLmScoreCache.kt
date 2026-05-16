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
 * ROADMAP §10.5 Next-3.1i — LRU score cache wrapping a [KenLmScorer].
 *
 * KenLM scoring through the pure-Kotlin navigator stack is bounded
 * by the per-order hash / trie walks — fast, but called per keystroke
 * per candidate per token. A small LRU is enough to swallow the
 * repeated lookups during normal typing (the user types `the c` and
 * the smartbar asks about `the cat`, `the cab`, `the can`, `the car`
 * which all share the same `the` bigram parent).
 *
 * The cache key is the `(history, tail)` pair, keyed by:
 *
 *  - The full history tokens, joined by the unit separator U+001F so
 *    `the cat | sat` and `the | cat sat` don't collide.
 *  - The tail token, separated from history by U+001E.
 *
 * Cache eviction is **insertion-order LRU** via [LinkedHashMap].
 * Default capacity 4,096 entries — a single typed-word session
 * rarely exceeds 1,500 lookups, so 4k absorbs a full text-thread
 * worth of suggestions without forced eviction.
 */
class KenLmScoreCache(
    private val delegate: KenLmScorer,
    val capacity: Int = DEFAULT_CAPACITY,
) : KenLmScorer {

    override val modelType: KenLmModelType get() = delegate.modelType
    override val maxOrder: Int get() = delegate.maxOrder

    init {
        require(capacity >= 1) { "capacity must be ≥ 1 (was $capacity)" }
    }

    private val cache: LinkedHashMap<String, Float> =
        object : LinkedHashMap<String, Float>(16, 0.75f, /* accessOrder= */ true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, Float>): Boolean {
                return size > capacity
            }
        }

    private val cacheLock = Any()

    /** Hits + misses since instantiation — exposed for diagnostics. */
    @Volatile
    var hits: Long = 0L
        private set

    @Volatile
    var misses: Long = 0L
        private set

    override fun score(history: List<String>, tail: String): Float {
        val key = buildKey(history, tail)
        synchronized(cacheLock) {
            cache[key]?.let {
                hits++
                return it
            }
        }
        val computed = delegate.score(history, tail)
        synchronized(cacheLock) {
            cache[key] = computed
            misses++
        }
        return computed
    }

    /** Drop the entire cache — used when the underlying model is swapped. */
    fun clear() = synchronized(cacheLock) {
        cache.clear()
        hits = 0L
        misses = 0L
    }

    /** Current entry count (post-eviction). */
    fun size(): Int = synchronized(cacheLock) { cache.size }

    private fun buildKey(history: List<String>, tail: String): String =
        history.joinToString(separator = "\u001F") + "\u001E" + tail

    companion object {
        const val DEFAULT_CAPACITY: Int = 4_096
    }
}
