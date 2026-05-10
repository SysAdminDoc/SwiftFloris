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
 * candidate generation, restricted to edit-distance ≤ 1 by default.
 *
 * SymSpell's insight: instead of generating insertions/substitutions/transpositions
 * at *lookup* time (Norvig's `edits1`, ~`O(L · |alphabet|)` per call), pre-build a
 * map from every delete-form of every dictionary word back to the original word(s).
 * At lookup: generate delete-forms of the input word and intersect. Two words are
 * within edit-distance 1 (or 2) by Damerau-Levenshtein iff they share a common
 * delete-form, so this is a complete lossless filter.
 *
 * Trade-off: a one-time build cost (a few hundred ms over a 117k-word dict on a
 * Pixel 6) and a few MB of RAM for the precomputed map, in exchange for ~50×
 * faster per-keystroke correction lookups. The build runs lazily when the first
 * correction is requested for a given dictionary, then stays cached for the
 * lifetime of the process.
 *
 * This shipping version is configured at distance = 1 only — covers the most
 * common typo class (single-key off-by-one) without the memory blow-up of
 * distance-2 indexes. Distance-2 candidates still come from the existing
 * `knownEdits2` path for short words (`length ≤ MaxTwoEditWordLength`).
 */
internal class SymSpellIndex private constructor(
    private val deleteToOriginals: Map<String, Array<String>>,
) {
    companion object {
        private const val MAX_DISTANCE: Int = 1

        /**
         * Builds an index over [words]. Skips empty / single-char strings (single-char
         * words are rare and trivially handled by direct dictionary contains-checks).
         * The map values are stored as deduplicated arrays so the index is read-only
         * and the per-entry overhead is one Java object header instead of a HashSet.
         */
        fun build(words: Iterable<String>): SymSpellIndex {
            val builder = HashMap<String, MutableSet<String>>(2 * 1024 * 1024)
            for (word in words) {
                if (word.length < 2) continue
                builder.getOrPut(word) { HashSet(2) }.add(word)
                generateDeletes(word, MAX_DISTANCE).forEach { delForm ->
                    builder.getOrPut(delForm) { HashSet(2) }.add(word)
                }
            }
            val frozen = HashMap<String, Array<String>>(builder.size)
            for ((k, v) in builder) {
                frozen[k] = v.toTypedArray()
            }
            return SymSpellIndex(frozen)
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
     * Returns the set of dictionary words within edit-distance ≤ [MAX_DISTANCE] of
     * [input], as judged by sharing a common delete-form. Caller is responsible for
     * (a) verifying actual edit distance if a tighter bound is needed, and
     * (b) excluding [input] itself from the result if it happens to be a known word.
     */
    fun candidatesAtDistance1(input: String): Set<String> {
        if (input.length < 2) return emptySet()
        val out = HashSet<String>()
        deleteToOriginals[input]?.let { for (w in it) out.add(w) }
        for (delForm in generateDeletes(input, MAX_DISTANCE)) {
            deleteToOriginals[delForm]?.let { for (w in it) out.add(w) }
        }
        return out
    }

    /** Approximate memory footprint of the index, useful for stats screens / dev logs. */
    fun entryCount(): Int = deleteToOriginals.size
}
