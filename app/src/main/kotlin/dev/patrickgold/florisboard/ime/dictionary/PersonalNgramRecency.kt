/*
 * Copyright (C) 2026 SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.dictionary

import kotlin.math.pow

internal object PersonalNgramRecency {
    private const val DAY_MS = 86_400_000.0
    private const val HALF_LIFE_DAYS = 21.0

    /**
     * How long an unused n-gram stays suggestible. Recency orders the suggestions; it doesn't
     * decide whether a phrase is remembered, so one typed twice isn't forgotten after a few
     * weeks away. LatinIME drops user-history entries after 300 idle days.
     */
    private const val MAX_IDLE_DAYS = 365.0

    /** Whether an n-gram last seen at [lastSeenMs] is still within memory. Legacy rows (0) are. */
    fun isRemembered(lastSeenMs: Long, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (lastSeenMs <= 0L) return true
        return (nowMs - lastSeenMs).coerceAtLeast(0L).toDouble() / DAY_MS <= MAX_IDLE_DAYS
    }

    /**
     * Which keys of [counts] to keep when a table is over its cap: the highest recency-weighted
     * count first, then the most recently seen. A pair typed once today outlives one typed once
     * last month. Ranking by raw count alone threw out every new pair after a common word at the
     * next save, before it could be typed a second time and become suggestible.
     */
    fun <K> keysToKeep(counts: Map<K, Int>, lastSeen: Map<K, Long>?, keep: Int, nowMs: Long): Set<K> {
        return counts.entries
            .sortedWith(
                compareByDescending<Map.Entry<K, Int>> { decayedScore(it.value, lastSeen?.get(it.key) ?: 0L, nowMs) }
                    .thenByDescending { lastSeen?.get(it.key)?.takeIf { seen -> seen > 0L } ?: nowMs }
            )
            .take(keep)
            .mapTo(HashSet()) { it.key }
    }

    /** Recency-weighted total of [counts], for ranking whole contexts against each other. */
    fun <K> totalScore(counts: Map<K, Int>, lastSeen: Map<K, Long>?, nowMs: Long): Double {
        return counts.entries.sumOf { decayedScore(it.value, lastSeen?.get(it.key) ?: 0L, nowMs) }
    }

    fun decayedScore(count: Int, lastSeenMs: Long, nowMs: Long = System.currentTimeMillis()): Double {
        if (count <= 0) return 0.0
        val safeLastSeen = lastSeenMs.takeIf { it > 0L } ?: nowMs
        val ageDays = ((nowMs - safeLastSeen).coerceAtLeast(0L).toDouble() / DAY_MS)
        val recency = 0.5.pow(ageDays / HALF_LIFE_DAYS)
        return count.toDouble() * recency
    }

    fun normalizedScore(
        count: Int,
        lastSeenMs: Long,
        maxScore: Double,
        nowMs: Long = System.currentTimeMillis(),
    ): Double {
        if (maxScore <= 0.0) return 0.0
        return (decayedScore(count, lastSeenMs, nowMs) / maxScore).coerceIn(0.0, 1.0)
    }
}
