/*
 * Copyright (C) 2025 The FlorisBoard Contributors
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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.sin

/**
 * Visual themes for the glide typing trail.
 *
 * Each theme computes a per-segment color based on [progress] (0 = tail, 1 = head)
 * and an optional [timeMillis] for animated effects.
 */
enum class GlideTrailTheme {
    ACCENT,
    RAINBOW,
    FIRE,
    ICE,
    AURORA,
    GALAXY,
    NEON;

    fun colorAt(progress: Float, timeMillis: Long, accentColor: Color): Color {
        val p = progress.coerceIn(0f, 1f)
        return when (this) {
            ACCENT -> accentColor

            RAINBOW -> {
                // Full 360° hue sweep along the trail, animated over time.
                val hue = ((p * 360f) + (timeMillis * 0.2f)) % 360f
                Color.hsv(hue, 0.9f, 1f)
            }

            FIRE -> {
                // Deep crimson → red → orange → yellow → white-hot
                val t = (timeMillis * 0.003f) // subtle shimmer
                val shimmer = 0.97f + 0.03f * sin(p * 20f + t)
                when {
                    p < 0.25f -> lerp(Color(0xFF8B0000), Color(0xFFDD2200), p * 4f)
                    p < 0.50f -> lerp(Color(0xFFDD2200), Color(0xFFFF6600), (p - 0.25f) * 4f)
                    p < 0.75f -> lerp(Color(0xFFFF6600), Color(0xFFFFCC00), (p - 0.50f) * 4f)
                    else -> {
                        val hot = lerp(Color(0xFFFFCC00), Color(0xFFFFFFCC), (p - 0.75f) * 4f)
                        hot.copy(red = hot.red * shimmer, green = hot.green * shimmer)
                    }
                }
            }

            ICE -> {
                // Dark indigo → electric blue → cyan → white frost
                when {
                    p < 0.30f -> lerp(Color(0xFF0D1B4C), Color(0xFF1155FF), p / 0.30f)
                    p < 0.60f -> lerp(Color(0xFF1155FF), Color(0xFF00DDFF), (p - 0.30f) / 0.30f)
                    p < 0.85f -> lerp(Color(0xFF00DDFF), Color(0xFFAAFFFF), (p - 0.60f) / 0.25f)
                    else -> lerp(Color(0xFFAAFFFF), Color(0xFFEEFFFF), (p - 0.85f) / 0.15f)
                }
            }

            AURORA -> {
                // Northern lights: green → teal → purple → magenta, slowly cycling.
                val hue = ((p * 180f) + (timeMillis * 0.06f) + 120f) % 360f
                Color.hsv(hue, 0.7f, 0.95f)
            }

            GALAXY -> {
                // Deep space: dark purple → electric blue → hot pink → soft lavender
                val t = (timeMillis * 0.0004f)
                val shift = sin(t.toDouble()).toFloat() * 15f
                when {
                    p < 0.25f -> lerp(Color(0xFF1A0033), Color(0xFF4400AA), p * 4f)
                    p < 0.50f -> lerp(Color(0xFF4400AA), Color(0xFF0066FF), (p - 0.25f) * 4f)
                    p < 0.75f -> {
                        val c = lerp(Color(0xFF0066FF), Color(0xFFFF33AA), (p - 0.50f) * 4f)
                        // Subtle hue shift animation
                        Color.hsv(
                            (c.red * 360f + shift).mod(360f),
                            0.85f,
                            0.9f + 0.1f * p,
                        )
                    }
                    else -> lerp(Color(0xFFFF33AA), Color(0xFFCC99FF), (p - 0.75f) * 4f)
                }
            }

            NEON -> {
                // Electric green with bright pulse animation.
                val pulse = 0.75f + 0.25f * sin(timeMillis * 0.015).toFloat()
                val brightness = 0.6f + 0.4f * p
                Color(
                    red = 0.1f * brightness * pulse,
                    green = brightness * pulse,
                    blue = 0.3f * brightness * pulse,
                    alpha = 1f,
                )
            }
        }
    }
}
