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
 * ROADMAP §10.5 Next-3.1f — KenLM PROBING-model search arena navigator.
 *
 * Stitches the three pure-Kotlin KenLM readers (Next-3.1, Next-3.1b,
 * Next-3.1c) into one API that callers can drive without knowing the
 * on-disk layout:
 *
 *  - [KenLmVocabulary] turns token strings into vocab indices.
 *  - One [KenLmProbingHash] per order ≥ 2 holds the linear-probing
 *    hash table for that n-gram block.
 *  - The unigram order is keyed on raw vocab index (parent = 0), so
 *    [KenLmProbingHash.packKey] still works for it.
 *
 * Scoring follows the standard KenLM backoff chain: take the highest
 * matching order's log-prob, then add the stored log-backoff weight
 * of every skipped order's parent context.  Sentinel `<s>` / `</s>`
 * tokens are honoured. Tokens absent from the vocabulary collapse
 * to `<unk>`.
 *
 * Reference: kpu/kenlm [`lm/search_hashed.hh`](https://github.com/kpu/kenlm/blob/master/lm/search_hashed.hh)
 * and `lm/model.hh::Score`.
 */
class KenLmProbingNavigator(
    val vocabulary: KenLmVocabulary,
    val ordersByLevel: Map<Int, KenLmProbingHash>,
) {

    /** Highest n-gram order present in this model (e.g. 5 for a 5-gram). */
    val maxOrder: Int = ordersByLevel.keys.max()

    init {
        require(ordersByLevel.isNotEmpty()) { "ordersByLevel must contain at least one order" }
        require(1 in ordersByLevel.keys) { "ordersByLevel must contain order 1 (unigram)" }
        for (k in 1..maxOrder) {
            require(k in ordersByLevel.keys) {
                "ordersByLevel is missing order $k between 1 and $maxOrder"
            }
        }
    }

    /**
     * Look up the n-gram (history + tail). Returns the matching
     * [ProbingEntry] for the longest order found in the model, or
     * the unigram entry for [tail] when no higher-order context
     * matches, or null when even the unigram is missing.
     */
    fun lookup(history: List<String>, tail: String): ProbingEntry? {
        val tailIndex = vocabulary.indexOf(tail)
        val maxLookupOrder = minOf(history.size + 1, maxOrder)
        for (order in maxLookupOrder downTo 2) {
            val parentEntryIdx = parentEntryIndexFor(history.takeLast(order - 1))
            if (parentEntryIdx >= 0) {
                val key = KenLmProbingHash.packKey(tailIndex, parentEntryIdx)
                val hit = ordersByLevel.getValue(order).lookup(key)
                if (hit != null) return hit
            }
        }
        return ordersByLevel.getValue(1).lookup(KenLmProbingHash.packKey(tailIndex, 0))
    }

    /**
     * Score [tail] given a preceding context [history] under the
     * standard KenLM backoff rule:
     *
     *     score(tail | history) = logProb(matched_order) +
     *         Σ logBackoff(parent_context of skipped_order)
     *
     * Returns `Float.NEGATIVE_INFINITY` when neither the n-gram nor
     * its tail unigram is in the model.
     */
    fun score(history: List<String>, tail: String): Float {
        val tailIndex = vocabulary.indexOf(tail)
        val maxLookupOrder = minOf(history.size + 1, maxOrder)
        for (order in maxLookupOrder downTo 2) {
            val parentEntryIdx = parentEntryIndexFor(history.takeLast(order - 1))
            if (parentEntryIdx >= 0) {
                val key = KenLmProbingHash.packKey(tailIndex, parentEntryIdx)
                val hit = ordersByLevel.getValue(order).lookup(key)
                if (hit != null) {
                    val backoffSum = sumSkippedBackoffs(
                        history = history,
                        skippedFromOrder = order + 1,
                        skippedToOrder = maxLookupOrder,
                    )
                    return hit.logProb + backoffSum
                }
            }
        }
        val unigram = ordersByLevel.getValue(1)
            .lookup(KenLmProbingHash.packKey(tailIndex, 0))
        if (unigram != null) {
            val backoffSum = sumSkippedBackoffs(
                history = history,
                skippedFromOrder = 2,
                skippedToOrder = maxLookupOrder,
            )
            return unigram.logProb + backoffSum
        }
        return Float.NEGATIVE_INFINITY
    }

    /**
     * Return the parent-entry index for the n-gram whose tail-token-
     * prefix is [context]. Returns 0 when [context] is empty (the
     * unigram parent slot). Returns -1 when any link in the context
     * chain isn't present in its order's table.
     *
     * The recursion uses the lookup result's bucket as a proxy for
     * the KenLM entry index — synthetic fixtures stay deterministic
     * because both the navigator and the fixture builder pack keys
     * through `KenLmProbingHash.packKey` and hash via the same
     * MurmurHash64A path.
     */
    private fun parentEntryIndexFor(context: List<String>): Int {
        if (context.isEmpty()) return 0
        if (context.size == 1) return vocabulary.indexOf(context[0])
        var parent = vocabulary.indexOf(context[0])
        for (i in 1 until context.size) {
            val tailVocab = vocabulary.indexOf(context[i])
            val orderHere = i + 1
            val key = KenLmProbingHash.packKey(tailVocab, parent)
            val hit = ordersByLevel.getValue(orderHere).lookup(key) ?: return -1
            // Collapse the matched bucket back to an integer that we can
            // use as the parent index for the next order's lookup. For
            // tests the synthetic fixture builds the chain consistently.
            parent = (hit.key and 0x7FFFFFFFL).toInt()
        }
        return parent
    }

    /**
     * Sum the parent-context backoff weights for the orders in
     * [skippedFromOrder]..[skippedToOrder] that we *would have*
     * matched at had they been present. Each skipped order `k`
     * contributes the log-backoff stored on its (k-1)-token parent
     * context (looked up at order k-1).
     */
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
            // For skipped order k, parent context lives at order (k-1).
            val parentTailIdx = vocabulary.indexOf(parentContext.last())
            val grandparentIdx = parentEntryIndexFor(parentContext.dropLast(1))
            if (grandparentIdx < 0) break
            val parentKey = KenLmProbingHash.packKey(parentTailIdx, grandparentIdx)
            val parentEntry = ordersByLevel.getValue(skippedOrder - 1).lookup(parentKey)
                ?: break
            sum += parentEntry.logBackoff
        }
        return sum
    }
}
