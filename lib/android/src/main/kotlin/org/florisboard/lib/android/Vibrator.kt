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
import android.os.VibrationAttributes
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
 * Two-tier haptic path with graceful fallback (ROADMAP §6 N3.3):
 *
 *  1. **Android 11+ composition primitives** (`VibrationEffect.startComposition`,
 *     `PRIMITIVE_TICK` / `PRIMITIVE_CLICK` / `PRIMITIVE_LOW_TICK`) when the
 *     device supports the primitive, scaled by the user's strength preference.
 *  2. **`VibrationEffect.createOneShot(duration, amplitude)`**, always
 *     reachable as the final fallback.
 *
 * An Android 16 envelope tier used to sit in front of these. It passed 0 Hz as
 * every control point's frequency, which the platform rejects when the effect
 * is built, so it never vibrated anything and threw on every key press on
 * Android 16 and later before falling through to the primitive.
 *
 * [ignoreSystemSettings] is the keyboard's "regardless of system settings"
 * mode. A vibration sent without attributes reaches Android 13+ as touch
 * feedback, which the phone's touch vibration switch turns off
 * (`ignored_for_settings` in `dumpsys vibrator_manager`), so that mode sends it
 * as physical emulation instead: the usage for a vibration standing in for a
 * real button, which a key press is, and one no haptic feedback switch disables.
 */
fun Vibrator.vibrate(duration: Int, strength: Int, factor: Double = 1.0, ignoreSystemSettings: Boolean = false) {
    if (duration == 0 || strength == 0) return
    val effectiveDuration = (duration * factor).toLong().coerceAtLeast(1L)
    val effectiveStrength = when {
        this.hasAmplitudeControl() -> (255.0 * ((strength * factor) / 100.0)).toInt().coerceIn(1, 255)
        else -> VibrationEffect.DEFAULT_AMPLITUDE
    }
    val normalisedAmplitude = (effectiveStrength.coerceIn(0, 255) / 255.0f).coerceIn(0.05f, 1.0f)

    // Tier 1: Android 11+ composition primitive with amplitude scale.
    if (AndroidVersion.ATLEAST_API30_R && tryVibratePrimitive(factor, normalisedAmplitude, ignoreSystemSettings)) {
        Log.d("Vibrator", "Composition-primitive haptic: factor=$factor amp=$normalisedAmplitude")
        return
    }

    // Tier 2: one-shot.
    Log.d("Vibrator", "Legacy haptic: duration=$effectiveDuration strength=$effectiveStrength")
    val effect = VibrationEffect.createOneShot(effectiveDuration, effectiveStrength)
    dispatch(effect, ignoreSystemSettings)
}

private fun Vibrator.dispatch(effect: VibrationEffect, ignoreSystemSettings: Boolean) {
    if (ignoreSystemSettings && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        this.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_PHYSICAL_EMULATION))
    } else {
        this.vibrate(effect)
    }
}

/**
 * Android 11+ composition primitive path. Picks a primitive by intensity
 * (light vs. standard vs. heavy) and dispatches via
 * `VibrationEffect.startComposition().addPrimitive(...).compose()`. The
 * amplitude argument scales the primitive's built-in waveform; the device
 * does its own actuator-mapping.
 */
private fun Vibrator.tryVibratePrimitive(factor: Double, amplitude: Float, ignoreSystemSettings: Boolean): Boolean {
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
        dispatch(composition.compose(), ignoreSystemSettings)
        true
    }.getOrDefault(false)
}
