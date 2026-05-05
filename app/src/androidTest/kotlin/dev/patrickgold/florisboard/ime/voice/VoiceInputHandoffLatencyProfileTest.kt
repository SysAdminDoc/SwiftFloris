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

package dev.patrickgold.florisboard.ime.voice

import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import kotlin.math.roundToInt
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VoiceInputHandoffLatencyProfileTest {
    @Test
    fun profileVoiceInputPreflightLatencyOnCurrentDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = VoiceInputManager(context)

        repeat(WARMUP_ITERATIONS) {
            manager.readPreflightSnapshot()
        }

        val samples = mutableListOf<Long>()
        var lastSnapshot = VoiceInputPreflightSnapshot.empty()
        repeat(PROFILE_ITERATIONS) {
            val startNanos = SystemClock.elapsedRealtimeNanos()
            lastSnapshot = manager.readPreflightSnapshot()
            samples.add(SystemClock.elapsedRealtimeNanos() - startNanos)
        }

        val stats = LatencyStats.from(samples)
        assertTrue(
            "Voice input preflight p95 latency exceeded ${MAX_PROFILE_P95_MICROS}us: ${stats.p95Micros}us",
            stats.p95Micros < MAX_PROFILE_P95_MICROS,
        )
        Log.i(
            TAG,
            "Voice input handoff preflight latency profile: " +
                "device=${Build.MANUFACTURER} ${Build.MODEL}, " +
                "sdk=${Build.VERSION.SDK_INT}, " +
                "iterations=$PROFILE_ITERATIONS, " +
                "${lastSnapshot.toLogString()}, " +
                stats.toLogString(),
        )
    }

    private fun VoiceInputManager.readPreflightSnapshot(): VoiceInputPreflightSnapshot {
        return VoiceInputPreflightSnapshot(
            futoInstalled = isFutoVoiceInputInstalled(),
            futoEnabled = isFutoVoiceInputEnabled(),
            futoMicGranted = isFutoMicrophonePermissionGranted(),
            anyVoiceProviderEnabled = isExternalVoiceInputMethodEnabled(),
            readyForHandoff = isVoiceInputReadyForHandoff(),
            setupReason = resolveSetupReason(),
        )
    }

    private data class VoiceInputPreflightSnapshot(
        val futoInstalled: Boolean,
        val futoEnabled: Boolean,
        val futoMicGranted: Boolean,
        val anyVoiceProviderEnabled: Boolean,
        val readyForHandoff: Boolean,
        val setupReason: VoiceInputSetupReason,
    ) {
        fun toLogString(): String {
            return "futoInstalled=$futoInstalled, " +
                "futoEnabled=$futoEnabled, " +
                "futoMicGranted=$futoMicGranted, " +
                "anyVoiceProviderEnabled=$anyVoiceProviderEnabled, " +
                "readyForHandoff=$readyForHandoff, " +
                "setupReason=$setupReason"
        }

        companion object {
            fun empty(): VoiceInputPreflightSnapshot {
                return VoiceInputPreflightSnapshot(
                    futoInstalled = false,
                    futoEnabled = false,
                    futoMicGranted = false,
                    anyVoiceProviderEnabled = false,
                    readyForHandoff = false,
                    setupReason = VoiceInputSetupReason.NO_ENABLED_PROVIDER,
                )
            }
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
        const val TAG = "VoiceInputProfile"
        const val WARMUP_ITERATIONS = 20
        const val PROFILE_ITERATIONS = 100
        const val MAX_PROFILE_P95_MICROS = 75_000L
    }
}
