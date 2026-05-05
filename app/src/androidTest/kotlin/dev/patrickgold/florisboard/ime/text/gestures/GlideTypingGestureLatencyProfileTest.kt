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

package dev.patrickgold.florisboard.ime.text.gestures

import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GlideTypingGestureLatencyProfileTest {
    @Test
    fun profileDetectorLatencyOnCurrentDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val detector = GlideTypingGesture.Detector(context)
        val listener = CountingListener()
        detector.registerListener(listener)

        repeat(WARMUP_ITERATIONS) {
            listener.reset()
            runSyntheticGlide(detector)
        }

        val samples = mutableListOf<Long>()
        repeat(PROFILE_ITERATIONS) {
            listener.reset()
            val startNanos = SystemClock.elapsedRealtimeNanos()
            runSyntheticGlide(detector)
            samples.add(SystemClock.elapsedRealtimeNanos() - startNanos)

            assertEquals("Synthetic glide should complete exactly once", 1, listener.completedCount)
            assertTrue("Synthetic glide should emit trail points", listener.addedPoints > 0)
        }

        detector.unregisterListener(listener)
        val stats = LatencyStats.from(samples)
        assertTrue(
            "Gesture detector p95 latency exceeded ${MAX_PROFILE_P95_MICROS}us: ${stats.p95Micros}us",
            stats.p95Micros < MAX_PROFILE_P95_MICROS,
        )
        Log.i(
            TAG,
            "Glide detector latency profile: " +
                "device=${Build.MANUFACTURER} ${Build.MODEL}, " +
                "sdk=${Build.VERSION.SDK_INT}, " +
                "iterations=$PROFILE_ITERATIONS, " +
                stats.toLogString(),
        )
    }

    private fun runSyntheticGlide(detector: GlideTypingGesture.Detector) {
        val downTime = SystemClock.uptimeMillis()
        sendEvent(detector, downTime, downTime, MotionEvent.ACTION_DOWN, 120f, 120f)

        repeat(MOVE_EVENT_COUNT) { index ->
            val progress = (index + 1).toFloat() / MOVE_EVENT_COUNT.toFloat()
            val x = 120f + (720f * progress)
            val y = 120f + if (index % 2 == 0) 90f else 140f
            sendEvent(
                detector = detector,
                downTime = downTime,
                eventTime = downTime + ((index + 1) * EVENT_STEP_MILLIS),
                action = MotionEvent.ACTION_MOVE,
                x = x,
                y = y,
            )
        }

        sendEvent(
            detector = detector,
            downTime = downTime,
            eventTime = downTime + ((MOVE_EVENT_COUNT + 1) * EVENT_STEP_MILLIS),
            action = MotionEvent.ACTION_UP,
            x = 840f,
            y = 220f,
        )
    }

    private fun sendEvent(
        detector: GlideTypingGesture.Detector,
        downTime: Long,
        eventTime: Long,
        action: Int,
        x: Float,
        y: Float,
    ) {
        val event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
        try {
            detector.onTouchEvent(event, initialKey = null)
        } finally {
            event.recycle()
        }
    }

    private class CountingListener : GlideTypingGesture.Listener {
        var completedCount = 0
            private set
        var addedPoints = 0
            private set

        override fun onGlideComplete(data: GlideTypingGesture.Detector.PointerData) {
            completedCount += 1
        }

        override fun onGlideAddPoint(point: GlideTypingGesture.Detector.Position) {
            addedPoints += 1
        }

        fun reset() {
            completedCount = 0
            addedPoints = 0
        }
    }

    private data class LatencyStats(
        val minMicros: Long,
        val p50Micros: Long,
        val p95Micros: Long,
        val maxMicros: Long,
        val avgMicros: Double,
    ) {
        fun toLogString(): String {
            return "minMicros=$minMicros, " +
                "p50Micros=$p50Micros, " +
                "p95Micros=$p95Micros, " +
                "maxMicros=$maxMicros, " +
                "avgMicros=${String.format(Locale.US, "%.1f", avgMicros)}"
        }

        companion object {
            fun from(samplesNanos: List<Long>): LatencyStats {
                val sortedMicros = samplesNanos.map { it / 1_000L }.sorted()
                return LatencyStats(
                    minMicros = sortedMicros.first(),
                    p50Micros = sortedMicros.percentile(0.50),
                    p95Micros = sortedMicros.percentile(0.95),
                    maxMicros = sortedMicros.last(),
                    avgMicros = sortedMicros.average(),
                )
            }

            private fun List<Long>.percentile(percentile: Double): Long {
                val index = ((size - 1) * percentile).roundToInt().coerceIn(0, lastIndex)
                return this[index]
            }
        }
    }

    private companion object {
        const val TAG = "GlideGestureProfile"
        const val WARMUP_ITERATIONS = 20
        const val PROFILE_ITERATIONS = 100
        const val MOVE_EVENT_COUNT = 28
        const val EVENT_STEP_MILLIS = 4L
        const val MAX_PROFILE_P95_MICROS = 50_000L
    }
}
