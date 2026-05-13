/*
 * Copyright (C) 2026 The SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.text.keyboard

import android.content.Context
import android.content.SharedPreferences
import dev.patrickgold.florisboard.ime.keyboard.KeyData
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Per-user, per-key adaptive touch model (SwiftKey-style "spatial model").
 *
 * Tracks the running mean + variance of the user's actual tap offset (relative
 * to the visible center of each key, normalised by key half-size) using
 * Welford's online algorithm. When the geometric hit-test lands on a key whose
 * tap falls near a row boundary, the model is consulted to see whether a
 * neighbouring key is a better fit under the user's learned distribution.
 *
 * Stats are partitioned by subtype id so different layouts (QWERTY-EN vs
 * AZERTY-FR) accumulate independently. Learned offsets are persisted locally
 * after small batches of taps so the model keeps improving across restarts.
 */
internal object AdaptiveTouchModel {
    private const val MIN_SAMPLES_PER_KEY = 30
    private const val MIN_VARIANCE = 0.01f
    private const val NEIGHBOUR_HORIZONTAL_TOLERANCE = 0.4f
    private const val NEIGHBOUR_VERTICAL_TOLERANCE = 0.6f
    private const val PERSISTENCE_NAME = "adaptive_touch_model"
    private const val PERSISTENCE_KEY = "buckets_v1"
    private const val PERSISTENCE_VERSION = 1
    private const val PERSIST_EVERY_TAPS = 8
    private const val MAX_PERSISTED_BUCKETS = 24
    private const val MAX_PERSISTED_KEYS_PER_BUCKET = 96
    private const val MAX_PERSISTED_SAMPLE_COUNT = 100_000

    private data class KeyStats(
        var count: Int = 0,
        var meanX: Float = 0f,
        var meanY: Float = 0f,
        var m2X: Float = 0f,
        var m2Y: Float = 0f,
    ) {
        fun varianceX(): Float = if (count > 1) max(m2X / (count - 1), MIN_VARIANCE) else MIN_VARIANCE
        fun varianceY(): Float = if (count > 1) max(m2Y / (count - 1), MIN_VARIANCE) else MIN_VARIANCE

        fun update(nx: Float, ny: Float) {
            count += 1
            val deltaX = nx - meanX
            meanX += deltaX / count
            m2X += deltaX * (nx - meanX)
            val deltaY = ny - meanY
            meanY += deltaY / count
            m2Y += deltaY * (ny - meanY)
        }

        fun logLikelihood(nx: Float, ny: Float): Float {
            val vx = varianceX()
            val vy = varianceY()
            val dx = nx - meanX
            val dy = ny - meanY
            return -0.5f * (dx * dx / vx + dy * dy / vy + ln(vx) + ln(vy))
        }
    }

    private val statsBySubtype: MutableMap<String, MutableMap<Int, KeyStats>> = HashMap()
    private var activeBucket: String = "global"
    private var preferences: SharedPreferences? = null
    private var hasLoadedPersistedState = false
    private var dirtyTapCount = 0

    @Synchronized
    fun initialize(context: Context) {
        preferences = context.applicationContext.getSharedPreferences(PERSISTENCE_NAME, Context.MODE_PRIVATE)
        if (hasLoadedPersistedState) return
        preferences?.getString(PERSISTENCE_KEY, null)?.let { encoded ->
            restoreSnapshotFromString(encoded)
        }
        hasLoadedPersistedState = true
    }

    @Synchronized
    fun setActiveSubtype(bucketKey: String) {
        persistIfDirty()
        activeBucket = bucketKey
    }

    @Synchronized
    fun reset() {
        statsBySubtype.clear()
        dirtyTapCount = 0
        persist()
    }

    @Synchronized
    fun resetActive() {
        statsBySubtype.remove(activeBucket)
        dirtyTapCount = 0
        persist()
    }

    @Synchronized
    fun totalSampleCount(): Int {
        var total = 0
        for (bucket in statsBySubtype.values) {
            for (stats in bucket.values) total += stats.count
        }
        return total
    }

    private fun bucket(): MutableMap<Int, KeyStats> {
        return statsBySubtype.getOrPut(activeBucket) { HashMap() }
    }

    private fun isLearnableKey(key: TextKey): Boolean {
        val code = key.touchModelCode()
        // Only learn from primary letter / number / punctuation taps. Skip
        // modifier and control keys — variance there is dominated by intent
        // (long-press, swipe) rather than spatial accuracy.
        return code > KeyCode.SPACE && code != KeyCode.CJK_SPACE
    }

    /**
     * Records a tap on [key] at absolute screen coords ([touchX], [touchY]).
     * Offsets are normalised by the key's half-size so that learned distributions
     * are scale-invariant across keyboard-height changes.
     */
    @Synchronized
    fun recordTap(key: TextKey, touchX: Float, touchY: Float) {
        if (!isLearnableKey(key)) return
        val bounds = key.visibleBounds
        if (bounds.isEmpty()) return
        val halfW = bounds.width * 0.5f
        val halfH = bounds.height * 0.5f
        if (halfW <= 0f || halfH <= 0f) return
        val centerX = bounds.left + halfW
        val centerY = bounds.top + halfH
        val nx = ((touchX - centerX) / halfW).coerceIn(-2f, 2f)
        val ny = ((touchY - centerY) / halfH).coerceIn(-2f, 2f)
        bucket().getOrPut(key.touchModelCode()) { KeyStats() }.update(nx, ny)
        dirtyTapCount += 1
        if (dirtyTapCount >= PERSIST_EVERY_TAPS) {
            persist()
        }
    }

    /**
     * Given the geometric hit-test result [primary] and the touch coords,
     * returns either [primary] or a neighbouring key from [keyboard] if the
     * model judges the tap to be more likely under the neighbour's learned
     * distribution. Returns [primary] when there is not enough learned data.
     */
    @Synchronized
    fun refine(keyboard: TextKeyboard, primary: TextKey, touchX: Float, touchY: Float): TextKey {
        if (!isLearnableKey(primary)) return primary
        val bucketStats = statsBySubtype[activeBucket] ?: return primary
        val primaryStats = bucketStats[primary.computedData.code] ?: return primary
        if (primaryStats.count < MIN_SAMPLES_PER_KEY) return primary

        val pBounds = primary.visibleBounds
        if (pBounds.isEmpty()) return primary
        val pHalfW = pBounds.width * 0.5f
        val pHalfH = pBounds.height * 0.5f
        if (pHalfW <= 0f || pHalfH <= 0f) return primary
        val pCenterX = pBounds.left + pHalfW
        val pCenterY = pBounds.top + pHalfH
        val pNx = ((touchX - pCenterX) / pHalfW).coerceIn(-2f, 2f)
        val pNy = ((touchY - pCenterY) / pHalfH).coerceIn(-2f, 2f)
        var bestKey = primary
        var bestScore = primaryStats.logLikelihood(pNx, pNy)

        for (candidate in keyboard.keys()) {
            if (candidate === primary || !isLearnableKey(candidate)) continue
            val cBounds = candidate.visibleBounds
            if (cBounds.isEmpty()) continue
            val cHalfW = cBounds.width * 0.5f
            val cHalfH = cBounds.height * 0.5f
            if (cHalfW <= 0f || cHalfH <= 0f) continue
            val cCenterX = cBounds.left + cHalfW
            val cCenterY = cBounds.top + cHalfH
            val rawDx = touchX - cCenterX
            val rawDy = touchY - cCenterY
            // Restrict the candidate set to actual neighbours of the primary
            // hit (same row + immediately adjacent column, give or take key
            // size). Keys far away can never legitimately win.
            if (kotlin.math.abs(rawDx) > pHalfW + cHalfW + NEIGHBOUR_HORIZONTAL_TOLERANCE * pHalfW) continue
            if (kotlin.math.abs(rawDy) > pHalfH + cHalfH + NEIGHBOUR_VERTICAL_TOLERANCE * pHalfH) continue
            val candStats = bucketStats[candidate.touchModelCode()] ?: continue
            if (candStats.count < MIN_SAMPLES_PER_KEY) continue
            val cNx = (rawDx / cHalfW).coerceIn(-2f, 2f)
            val cNy = (rawDy / cHalfH).coerceIn(-2f, 2f)
            val score = candStats.logLikelihood(cNx, cNy)
            if (score > bestScore) {
                bestScore = score
                bestKey = candidate
            }
        }
        return bestKey
    }

    /**
     * Returns the adjusted screen-pixel center of [keyCode] for the active subtype,
     * given the geometric [fallbackCenterX], [fallbackCenterY] and the key's
     * [halfWidth], [halfHeight]. When the key has fewer than [MIN_SAMPLES_PER_KEY]
     * recorded taps, returns the geometric fallback (no adjustment).
     *
     * Used by the glide classifier to personalise ideal-trace templates so that
     * matching scores reward swipes that pass through where this user actually
     * aims, not the visual key center.
     */
    @Synchronized
    fun adjustedCenter(
        keyCode: Int,
        fallbackCenterX: Float,
        fallbackCenterY: Float,
        halfWidth: Float,
        halfHeight: Float,
    ): Pair<Float, Float> {
        val bucket = statsBySubtype[activeBucket] ?: return fallbackCenterX to fallbackCenterY
        val stats = bucket[keyCode] ?: return fallbackCenterX to fallbackCenterY
        if (stats.count < MIN_SAMPLES_PER_KEY) return fallbackCenterX to fallbackCenterY
        // Cap the bias so even a heavily-skewed learner can never drag the template
        // outside the visible key — that would make the ideal trace miss the key entirely.
        val biasedX = fallbackCenterX + stats.meanX.coerceIn(-0.5f, 0.5f) * halfWidth
        val biasedY = fallbackCenterY + stats.meanY.coerceIn(-0.5f, 0.5f) * halfHeight
        return biasedX to biasedY
    }

    /**
     * Snapshot of per-key tap-offset stats for a debug-mode heat-map. Returns
     * the active bucket only; map is a defensive copy.
     */
    @Synchronized
    fun snapshotActive(): Map<Int, FloatArray> {
        val bucket = statsBySubtype[activeBucket] ?: return emptyMap()
        val result = HashMap<Int, FloatArray>(bucket.size)
        for ((code, stats) in bucket) {
            result[code] = floatArrayOf(
                stats.count.toFloat(),
                stats.meanX,
                stats.meanY,
                min(stats.varianceX(), 4f),
                min(stats.varianceY(), 4f),
            )
        }
        return result
    }

    @Synchronized
    internal fun encodeSnapshotForPersistence(): String {
        return Json.encodeToString(persistedState())
    }

    @Synchronized
    internal fun restoreSnapshotForPersistence(encoded: String): Boolean {
        return restoreSnapshotFromString(encoded)
    }

    private fun persistIfDirty() {
        if (dirtyTapCount > 0) {
            persist()
        }
    }

    private fun persist() {
        val prefs = preferences ?: return
        prefs.edit()
            .putString(PERSISTENCE_KEY, encodeSnapshotForPersistence())
            .apply()
        dirtyTapCount = 0
    }

    private fun restoreSnapshotFromString(encoded: String): Boolean {
        val state = try {
            Json.decodeFromString<PersistedAdaptiveTouchState>(encoded)
        } catch (_: IllegalArgumentException) {
            return false
        } catch (_: SerializationException) {
            return false
        }
        if (state.version != PERSISTENCE_VERSION) return false

        val restored = HashMap<String, MutableMap<Int, KeyStats>>()
        for (bucket in state.buckets.take(MAX_PERSISTED_BUCKETS)) {
            val bucketKey = bucket.key.takeIf { it.isNotBlank() } ?: continue
            val stats = HashMap<Int, KeyStats>()
            for (entry in bucket.stats.take(MAX_PERSISTED_KEYS_PER_BUCKET)) {
                entry.toKeyStats()?.let { keyStats ->
                    stats[entry.code] = keyStats
                }
            }
            if (stats.isNotEmpty()) {
                restored[bucketKey] = stats
            }
        }

        statsBySubtype.clear()
        statsBySubtype.putAll(restored)
        dirtyTapCount = 0
        return true
    }

    private fun persistedState(): PersistedAdaptiveTouchState {
        return PersistedAdaptiveTouchState(
            version = PERSISTENCE_VERSION,
            buckets = statsBySubtype.entries
                .sortedBy { it.key }
                .take(MAX_PERSISTED_BUCKETS)
                .map { (bucketKey, bucketStats) ->
                    PersistedAdaptiveTouchBucket(
                        key = bucketKey,
                        stats = bucketStats.entries
                            .sortedBy { it.key }
                            .take(MAX_PERSISTED_KEYS_PER_BUCKET)
                            .map { (code, stats) -> stats.toPersisted(code) },
                    )
                },
        )
    }

    private fun KeyStats.toPersisted(code: Int): PersistedAdaptiveTouchKeyStats {
        return PersistedAdaptiveTouchKeyStats(
            code = code,
            count = count.coerceIn(0, MAX_PERSISTED_SAMPLE_COUNT),
            meanX = meanX.coerceIn(-2f, 2f),
            meanY = meanY.coerceIn(-2f, 2f),
            m2X = m2X.coerceIn(0f, MAX_PERSISTED_SAMPLE_COUNT * 4f),
            m2Y = m2Y.coerceIn(0f, MAX_PERSISTED_SAMPLE_COUNT * 4f),
        )
    }

    private fun PersistedAdaptiveTouchKeyStats.toKeyStats(): KeyStats? {
        if (code <= KeyCode.SPACE || count <= 0) return null
        if (!meanX.isPersistableFloat() || !meanY.isPersistableFloat()) return null
        if (!m2X.isPersistableFloat() || !m2Y.isPersistableFloat()) return null
        return KeyStats(
            count = count.coerceIn(1, MAX_PERSISTED_SAMPLE_COUNT),
            meanX = meanX.coerceIn(-2f, 2f),
            meanY = meanY.coerceIn(-2f, 2f),
            m2X = m2X.coerceIn(0f, MAX_PERSISTED_SAMPLE_COUNT * 4f),
            m2Y = m2Y.coerceIn(0f, MAX_PERSISTED_SAMPLE_COUNT * 4f),
        )
    }

    private fun Float.isPersistableFloat(): Boolean {
        return !isNaN() && !isInfinite()
    }

    private fun TextKey.touchModelCode(): Int {
        val computedCode = computedData.code
        if (computedCode != KeyCode.UNSPECIFIED) {
            return computedCode
        }
        return (data as? KeyData)?.code ?: KeyCode.UNSPECIFIED
    }

    @Serializable
    private data class PersistedAdaptiveTouchState(
        val version: Int,
        val buckets: List<PersistedAdaptiveTouchBucket>,
    )

    @Serializable
    private data class PersistedAdaptiveTouchBucket(
        val key: String,
        val stats: List<PersistedAdaptiveTouchKeyStats>,
    )

    @Serializable
    private data class PersistedAdaptiveTouchKeyStats(
        val code: Int,
        val count: Int,
        val meanX: Float,
        val meanY: Float,
        val m2X: Float,
        val m2Y: Float,
    )
}
