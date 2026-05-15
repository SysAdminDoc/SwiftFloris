/*
 * Copyright (C) 2022-2026 The FlorisBoard / SwiftFloris Contributors
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

package org.florisboard.lib.android

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

fun Context.systemVibratorOrNull(): Vibrator? {
    return if (AndroidVersion.ATLEAST_API31_S) {
        this.systemServiceOrNull(VibratorManager::class)?.defaultVibrator
    } else {
        this.systemServiceOrNull(Vibrator::class)
    }?.takeIf { it.hasVibrator() }
}

/**
 * Three-tier haptic path with graceful fallback (ROADMAP §6 N3.3 + N3.3a):
 *
 *  1. **Android 16 PWLE envelopes** (`VibrationEffect.WaveformEnvelopeBuilder`,
 *     `Vibrator.areEnvelopeEffectsSupported()`) — when supported, build a
 *     three-point amplitude envelope for the richest tactile feel possible
 *     on the device's actuator. Resolved via reflection so the call still
 *     compiles cleanly against earlier build-tools SDKs where the symbol
 *     name may not yet be exposed.
 *  2. **Android 11+ composition primitives** (`VibrationEffect.startComposition`,
 *     `PRIMITIVE_TICK` / `PRIMITIVE_CLICK` / `PRIMITIVE_LOW_TICK` / `PRIMITIVE_THUD`)
 *     — when the device exposes `areAllPrimitivesSupported(...)` for the
 *     requested set, use a single-primitive composition with an amplitude
 *     scale derived from the user's strength preference. This is the
 *     intermediate richer-than-amplitude path that ships value to the
 *     ~90 % of users on Android 11-15.
 *  3. **Legacy `VibrationEffect.createOneShot(duration, amplitude)`** — the
 *     pre-existing path. Always reachable as a final fallback.
 *
 * The caller picks the path indirectly by passing `duration` + `strength` +
 * an optional `factor`; the wrapper makes the path decision based on what
 * the actuator supports. Per the §1 no-network philosophy, this is
 * entirely on-device — no telemetry pings about haptic capability.
 */
fun Vibrator.vibrate(duration: Int, strength: Int, factor: Double = 1.0) {
    if (duration == 0 || strength == 0) return
    val effectiveDuration = (duration * factor).toLong().coerceAtLeast(1L)
    val effectiveStrength = when {
        this.hasAmplitudeControl() -> (255.0 * ((strength * factor) / 100.0)).toInt().coerceIn(1, 255)
        else -> VibrationEffect.DEFAULT_AMPLITUDE
    }
    val normalisedAmplitude = (effectiveStrength.coerceIn(0, 255) / 255.0f).coerceIn(0.05f, 1.0f)

    // Tier 1 — Android 16 PWLE envelope (gated reflectively; falls through
    // silently if the runtime device doesn't actually support envelopes).
    if (AndroidVersion.ATLEAST_API36_BAKLAVA && tryVibrateEnvelope(effectiveDuration, normalisedAmplitude)) {
        Log.d("Vibrator", "PWLE envelope haptic: dur=${effectiveDuration}ms amp=$normalisedAmplitude")
        return
    }

    // Tier 2 — Android 11+ composition primitive with amplitude scale.
    if (AndroidVersion.ATLEAST_API30_R && tryVibratePrimitive(factor, normalisedAmplitude)) {
        Log.d("Vibrator", "Composition-primitive haptic: factor=$factor amp=$normalisedAmplitude")
        return
    }

    // Tier 3 — legacy one-shot (unchanged behaviour).
    Log.d("Vibrator", "Legacy haptic: duration=$effectiveDuration strength=$effectiveStrength")
    val effect = VibrationEffect.createOneShot(effectiveDuration, effectiveStrength)
    this.vibrate(effect)
}

/**
 * Android 16 PWLE envelope path. Returns `true` if the envelope was
 * successfully built and dispatched; `false` if any reflective step failed
 * or the device reports `areEnvelopeEffectsSupported() == false`. Caller
 * falls back to Tier 2/3 on `false`.
 *
 * Envelope shape: a snappy "quick rise, brief plateau, quick fall" that
 * gives the keypress a crisp tactile shape rather than the dull box-car
 * of a single amplitude-locked oneshot. Three control points:
 *   - t=0          → amplitude 0          (rest)
 *   - t=duration·0.4 → amplitude full     (peak)
 *   - t=duration   → amplitude 0          (settle)
 */
private fun Vibrator.tryVibrateEnvelope(duration: Long, amplitude: Float): Boolean {
    if (Build.VERSION.SDK_INT < 36) return false
    return runCatching {
        // areEnvelopeEffectsSupported() — Android 16 API 36, accessed
        // reflectively so older build-tools (or future renames) don't
        // break the compile.
        val areEnvelopeEffectsSupported = Vibrator::class.java
            .methods
            .firstOrNull { it.name == "areEnvelopeEffectsSupported" && it.parameterCount == 0 }
            ?: return@runCatching false
        val supported = areEnvelopeEffectsSupported.invoke(this) as? Boolean ?: false
        if (!supported) return@runCatching false

        // VibrationEffect.WaveformEnvelopeBuilder — instantiate reflectively.
        val builderClass = Class.forName("android.os.VibrationEffect\$WaveformEnvelopeBuilder")
        val builder = builderClass.getDeclaredConstructor().newInstance()
        // addControlPoint(amplitude, frequencyHz, durationMs)
        // We don't tune frequency (let the platform choose); use 0 = system default.
        val addControlPoint = builderClass.getMethod(
            "addControlPoint",
            java.lang.Float.TYPE,
            java.lang.Float.TYPE,
            java.lang.Long.TYPE,
        )
        val tPeak = (duration * 0.4).toLong().coerceAtLeast(1L)
        val tTail = (duration - tPeak).coerceAtLeast(1L)
        addControlPoint.invoke(builder, amplitude, 0f, tPeak)
        addControlPoint.invoke(builder, amplitude, 0f, tTail / 2L)
        addControlPoint.invoke(builder, 0f, 0f, tTail - (tTail / 2L))
        val build = builderClass.getMethod("build")
        val effect = build.invoke(builder) as VibrationEffect
        this.vibrate(effect)
        true
    }.getOrDefault(false)
}

/**
 * Android 11+ composition primitive path. Picks a primitive by intensity
 * (light vs. standard vs. heavy) and dispatches via
 * `VibrationEffect.startComposition().addPrimitive(...).compose()`. The
 * amplitude argument scales the primitive's built-in waveform; the device
 * does its own actuator-mapping.
 */
private fun Vibrator.tryVibratePrimitive(factor: Double, amplitude: Float): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
    val primitive = when {
        factor <= 0.10 -> VibrationEffect.Composition.PRIMITIVE_LOW_TICK   // long-press repeat / continuous gesture
        factor <= 0.45 -> VibrationEffect.Composition.PRIMITIVE_TICK       // long-press / swipe
        factor < 0.85 -> VibrationEffect.Composition.PRIMITIVE_CLICK       // standard keypress
        else -> VibrationEffect.Composition.PRIMITIVE_CLICK                // explicit keypress; CLICK reads strongest
    }
    if (!this.areAllPrimitivesSupported(primitive)) return false
    return runCatching {
        val composition = VibrationEffect.startComposition().addPrimitive(primitive, amplitude)
        this.vibrate(composition.compose())
        true
    }.getOrDefault(false)
}
