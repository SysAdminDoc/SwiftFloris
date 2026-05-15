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
 * ROADMAP §10.5 Next-3.1g — KenLM TRIE / QUANT_TRIE navigator.
 *
 * Sibling of [KenLmProbingNavigator] but for the trie-shaped search
 * arena. A TRIE order is laid out as:
 *
 *  1. Sorted `(parentEntryIndex, tailVocabIndex, probIndex, backoffIndex,
 *     nextPointer)` entries — sorted lexicographically by the
 *     `(parentEntryIndex, tailVocabIndex)` pair, so the per-parent
 *     children form a contiguous run.
 *  2. The `nextPointer` field is decoded via the Bhiksha pointer
 *     decoder ([BhikshaPointerDecoder]) — for the current order it's
 *     the index of the *first* child entry in the next-order block.
 *  3. Prob / backoff indices look up centroid floats in the per-order
 *     [KenLmQuantTable] (or, for non-quantised TRIE, raw float32s).
 *
 * For the pure-Kotlin reader stack this navigator owns the
 * **per-order entry table** ([TrieOrderTable]) and walks it without
 * a native dep. The on-disk → in-memory parse is intentionally
 * straight-line — tests build synthetic [TrieOrderTable]s — so the
 * navigator's contract can be verified independent of the
 * mmap-fed byte buffers that will plug in once the trie body parser
 * (Next-3.1b) lands.
 *
 * Reference: kpu/kenlm [`lm/trie.hh`](https://github.com/kpu/kenlm/blob/master/lm/trie.hh).
 */
class KenLmTrieNavigator(
    val vocabulary: KenLmVocabulary,
    val ordersByLevel: Map<Int, TrieOrderTable>,
) {
    val maxOrder: Int = ordersByLevel.keys.max()

    init {
        require(ordersByLevel.isNotEmpty()) { "ordersByLevel must be non-empty" }
        require(1 in ordersByLevel.keys) { "ordersByLevel must contain order 1 (unigram)" }
        for (k in 1..maxOrder) {
            require(k in ordersByLevel.keys) {
                "ordersByLevel is missing order $k"
            }
        }
    }

    /**
     * Find the entry for the n-gram (history + tail) in the longest
     * order whose context chain is present. Returns null when even
     * the tail unigram is missing.
     */
    fun lookup(history: List<String>, tail: String): TrieEntry? {
        val tailVocab = vocabulary.indexOf(tail)
        val maxLookupOrder = minOf(history.size + 1, maxOrder)
        for (order in maxLookupOrder downTo 2) {
            val context = history.takeLast(order - 1)
            val parentEntryIdx = traverseContext(context)
            if (parentEntryIdx >= 0) {
                val hit = ordersByLevel.getValue(order).find(parentEntryIdx, tailVocab)
                if (hit != null) return hit
            }
        }
        return ordersByLevel.getValue(1).find(parentEntryIndex = 0, tailVocabIndex = tailVocab)
    }

    /**
     * Score under standard KenLM backoff. Mirrors
     * [KenLmProbingNavigator.score] semantics: returns
     * `logProb(matched_order) + Σ logBackoff(parent_context_of_skipped_order)`.
     * Returns `Float.NEGATIVE_INFINITY` when even the unigram is
     * missing for [tail].
     */
    fun score(history: List<String>, tail: String): Float {
        val tailVocab = vocabulary.indexOf(tail)
        val maxLookupOrder = minOf(history.size + 1, maxOrder)
        for (order in maxLookupOrder downTo 2) {
            val context = history.takeLast(order - 1)
            val parentEntryIdx = traverseContext(context)
            if (parentEntryIdx >= 0) {
                val hit = ordersByLevel.getValue(order).find(parentEntryIdx, tailVocab)
                if (hit != null) {
                    val backoff = sumSkippedBackoffs(history, order + 1, maxLookupOrder)
                    return hit.logProb + backoff
                }
            }
        }
        val unigram = ordersByLevel.getValue(1)
            .find(parentEntryIndex = 0, tailVocabIndex = tailVocab)
        if (unigram != null) {
            return unigram.logProb + sumSkippedBackoffs(history, 2, maxLookupOrder)
        }
        return Float.NEGATIVE_INFINITY
    }

    /** Walk an n-token context chain. Returns the order-`size` entry
     *  index or -1 when any chain-link is absent. */
    private fun traverseContext(context: List<String>): Int {
        if (context.isEmpty()) return 0
        if (context.size == 1) return vocabulary.indexOf(context[0])
        var parent = vocabulary.indexOf(context[0])
        for (i in 1 until context.size) {
            val tail = vocabulary.indexOf(context[i])
            val table = ordersByLevel.getValue(i + 1)
            val entry = table.find(parent, tail) ?: return -1
            parent = entry.entryIndex
        }
        return parent
    }

    private fun sumSkippedBackoffs(
        history: List<String>,
        skippedFromOrder: Int,
        skippedToOrder: Int,
    ): Float {
        if (skippedFromOrder > skippedToOrder) return 0f
        var sum = 0f
        for (skippedOrder in skippedFromOrder..skippedToOrder) {
            if (history.size < skippedOrder - 1) break
            val parentContext = history.takeLast(skippedOrder - 1)
            val parentTailIdx = vocabulary.indexOf(parentContext.last())
            val grandparentIdx = traverseContext(parentContext.dropLast(1))
            if (grandparentIdx < 0) break
            val parentEntry = ordersByLevel.getValue(skippedOrder - 1)
                .find(grandparentIdx, parentTailIdx)
                ?: break
            sum += parentEntry.logBackoff
        }
        return sum
    }
}

/**
 * One order's entry table for the TRIE search arena.  Entries are
 * conceptually `(parentEntryIndex, tailVocabIndex, logProb,
 * logBackoff, nextPointer)`. The table provides
 * `find(parentEntryIndex, tailVocabIndex)` as the only required
 * lookup — its complexity isn't constrained here (production hits
 * use a binary search over the sorted run + the Bhiksha decoder
 * to fetch `nextPointer` lazily, but a `HashMap` works for tests).
 */
class TrieOrderTable(
    val order: Int,
    private val entries: Map<Pair<Int, Int>, TrieEntry>,
) {
    fun find(parentEntryIndex: Int, tailVocabIndex: Int): TrieEntry? =
        entries[parentEntryIndex to tailVocabIndex]

    val size: Int get() = entries.size

    companion object {
        /** Builder helper for tests. */
        fun fromEntries(order: Int, list: List<TrieEntry>): TrieOrderTable {
            val map = HashMap<Pair<Int, Int>, TrieEntry>(list.size)
            for (e in list) {
                map[e.parentEntryIndex to e.tailVocabIndex] = e
            }
            return TrieOrderTable(order = order, entries = map)
        }
    }
}

/**
 * One row in a TRIE order's entry table. [entryIndex] is the row's
 * position in the order's table — referenced by the next order's
 * `parentEntryIndex` field. [nextPointerStart] (when non-negative)
 * points at the first child entry in the next-order block via the
 * Bhiksha decoder; the highest order leaves it -1.
 */
data class TrieEntry(
    val entryIndex: Int,
    val parentEntryIndex: Int,
    val tailVocabIndex: Int,
    val logProb: Float,
    val logBackoff: Float,
    val nextPointerStart: Int = -1,
)
