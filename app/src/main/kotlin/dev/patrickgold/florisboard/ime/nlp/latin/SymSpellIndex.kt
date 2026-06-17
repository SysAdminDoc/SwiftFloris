/*
 * Copyright (C) 2026 The SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.nlp.latin

/**
 * Pure-Kotlin port of [SymSpell](https://github.com/wolfgarbe/SymSpell)'s delete-only
 * candidate generation, restricted to edit-distance <= 1 by default.
 *
 * SymSpell's insight: instead of generating insertions/substitutions/transpositions
 * at *lookup* time (Norvig's `edits1`, ~`O(L · |alphabet|)` per call), pre-build a
 * map from every delete-form of every dictionary word back to the original word(s).
 * At lookup: generate delete-forms of the input word and intersect. Words within
 * Damerau-Levenshtein distance 1 or 2 share at least one delete-form, so this is a
 * fast candidate generator. Callers still verify the exact distance when using a
 * distance-2 index because the shared-delete test is intentionally a superset.
 *
 * Trade-off: a one-time build cost and a few MB of RAM for the precomputed map,
 * in exchange for ~50x faster per-keystroke correction lookups. Callers should
 * pass a bounded, high-confidence correction vocabulary instead of a full
 * recognition dictionary; indexing hundreds of thousands of rare words can exceed
 * Android's IME heap during active typing.
 *
 * The default index is distance = 1. Distance-2 indexes are also supported, but
 * should only be built over a bounded high-frequency vocabulary.
 */
internal class SymSpellIndex private constructor(
    private val deleteToOriginals: Map<String, Array<String>>,
    private val maxDistance: Int,
    val indexedWordCount: Int,
    val isComplete: Boolean,
) {
    companion object {
        private const val DefaultMaxDistance: Int = 1
        private const val MaxSupportedDistance: Int = 2
        const val UnlimitedDeleteEntryBudget: Int = Int.MAX_VALUE

        private const val MaxWordLengthForDistance1: Int = 30
        private const val MaxWordLengthForDistance2: Int = 16

        /**
         * Returns a delete-entry budget scaled to the available heap. On devices with
         * small heaps (256 MB) where the keyboard shares the process, the default 750k
         * d1 / 320k d2 budgets can push the HashMap + HashSet allocations past the OOM
         * threshold. This scales the caller-provided budget down proportionally when the
         * heap ceiling is low, keeping the index buildable on constrained devices at the
         * cost of fewer correction candidates (the index reports [isComplete] = false).
         */
        fun heapScaledBudget(requestedBudget: Int): Int {
            if (requestedBudget == UnlimitedDeleteEntryBudget) return requestedBudget
            val rt = Runtime.getRuntime()
            val maxHeapMb = rt.maxMemory() / (1024 * 1024)
            if (maxHeapMb >= 384) return requestedBudget
            val scale = (maxHeapMb.toDouble() / 384.0).coerceIn(0.15, 1.0)
            return (requestedBudget * scale).toInt().coerceAtLeast(1_000)
        }

        /**
         * Builds an index over [words]. Skips empty / single-char strings (single-char
         * words are rare and trivially handled by direct dictionary contains-checks).
         * The map values are stored as deduplicated arrays so the index is read-only
         * and the per-entry overhead is one Java object header instead of a HashSet.
         *
         * If an [OutOfMemoryError] occurs during the build, the method catches it and
         * returns a partial index rather than crashing the keyboard.
         */
        fun build(
            words: Iterable<String>,
            maxDistance: Int = DefaultMaxDistance,
            maxDeleteEntries: Int = UnlimitedDeleteEntryBudget,
        ): SymSpellIndex {
            val boundedDistance = maxDistance.coerceIn(DefaultMaxDistance, MaxSupportedDistance)
            val maxWordLen = if (boundedDistance >= 2) MaxWordLengthForDistance2 else MaxWordLengthForDistance1
            val wordList = if (words is Collection) words else words.toList()
            val entryBudget = heapScaledBudget(maxDeleteEntries).coerceAtLeast(0)
            val expectedDeleteCount = wordList.sumOf { word ->
                if (word.length > maxWordLen) 0
                else ((word.length + 1) * boundedDistance).coerceAtMost(32)
            }
            val initialCapacity = expectedDeleteCount
                .coerceAtMost(entryBudget)
                .coerceIn(16, 1_048_576)
            val builder = HashMap<String, MutableSet<String>>(initialCapacity)
            var indexedWordCount = 0
            var isComplete = true
            try {
                for (word in wordList) {
                    if (word.length < 2 || word.length > maxWordLen) continue
                    val deleteForms = generateDeletes(word, boundedDistance)
                    val newEntryCount = (if (builder.containsKey(word)) 0 else 1) +
                        deleteForms.count { !builder.containsKey(it) }
                    if (builder.size + newEntryCount > entryBudget) {
                        isComplete = false
                        break
                    }
                    builder.getOrPut(word) { HashSet(2) }.add(word)
                    for (delForm in deleteForms) {
                        builder.getOrPut(delForm) { HashSet(2) }.add(word)
                    }
                    indexedWordCount++
                }
            } catch (_: OutOfMemoryError) {
                isComplete = false
            }
            val frozen = HashMap<String, Array<String>>(builder.size)
            for ((k, v) in builder) {
                frozen[k] = v.toTypedArray()
            }
            return SymSpellIndex(
                deleteToOriginals = frozen,
                maxDistance = boundedDistance,
                indexedWordCount = indexedWordCount,
                isComplete = isComplete,
            )
        }

        /**
         * Generates every string reachable from [word] by removing exactly 1..[maxDistance]
         * characters. For distance = 1 over an L-char word, that's L outputs.
         */
        private fun generateDeletes(word: String, maxDistance: Int): Set<String> {
            if (word.length < 2) return emptySet()
            val result = HashSet<String>(word.length * maxDistance + 4)
            generateDeletesRecursive(word, maxDistance, result)
            result.remove(word)
            return result
        }

        private fun generateDeletesRecursive(word: String, depthRemaining: Int, accum: MutableSet<String>) {
            if (depthRemaining <= 0 || word.length <= 1) return
            for (i in word.indices) {
                val delForm = word.substring(0, i) + word.substring(i + 1)
                if (accum.add(delForm) && depthRemaining > 1) {
                    generateDeletesRecursive(delForm, depthRemaining - 1, accum)
                }
            }
        }
    }

    /**
     * Returns candidate dictionary words as judged by sharing a common delete-form.
     * Caller is responsible for verifying exact edit distance and excluding [input].
     */
    fun candidates(input: String): Set<String> {
        if (input.length < 2) return emptySet()
        val out = HashSet<String>()
        deleteToOriginals[input]?.let { for (w in it) out.add(w) }
        for (delForm in generateDeletes(input, maxDistance)) {
            deleteToOriginals[delForm]?.let { for (w in it) out.add(w) }
        }
        return out
    }

    fun candidatesAtDistance1(input: String): Set<String> = candidates(input)

    /** Approximate memory footprint of the index, useful for stats screens / dev logs. */
    fun entryCount(): Int = deleteToOriginals.size
}
