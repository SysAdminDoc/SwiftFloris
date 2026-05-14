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
