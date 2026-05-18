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

package dev.patrickgold.florisboard.ime.window

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import dev.patrickgold.florisboard.ime.theme.LocalActiveThemeName
import dev.patrickgold.florisboard.lib.ext.ExtensionComponentName
import kotlin.math.PI
import kotlin.math.sin

internal const val AuroraAnimatedThemeComponentId = "aurora_animated"

internal fun isAuroraAnimatedTheme(themeName: ExtensionComponentName): Boolean {
    return themeName.extensionId == "org.florisboard.themes" &&
        themeName.componentId == AuroraAnimatedThemeComponentId
}

@Composable
internal fun BoxScope.AuroraAnimatedThemeBackground(
    activeThemeName: ExtensionComponentName = LocalActiveThemeName.current,
    reducedMotion: Boolean = rememberImeReducedMotion(),
) {
    if (!isAuroraAnimatedTheme(activeThemeName)) return

    var phase = 0f
    if (!reducedMotion) {
        val infiniteTransition = rememberInfiniteTransition()
        val animatedPhase by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 8_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        )
        phase = animatedPhase
    }

    Box(
        modifier = Modifier
            .matchParentSize()
            .clipToBounds(),
    ) {
        AuroraBand(
            color = Color(0x4D38BDF8),
            phase = phase,
            lane = 0,
            yOffset = 0.10f,
            amplitude = 0.12f,
        )
        AuroraBand(
            color = Color(0x40C084FC),
            phase = (phase + 0.31f) % 1f,
            lane = 1,
            yOffset = 0.28f,
            amplitude = 0.16f,
        )
        AuroraBand(
            color = Color(0x33F472B6),
            phase = (phase + 0.62f) % 1f,
            lane = 2,
            yOffset = 0.48f,
            amplitude = 0.13f,
        )
    }
}

@Composable
private fun AuroraBand(
    color: Color,
    phase: Float,
    lane: Int,
    yOffset: Float,
    amplitude: Float,
) {
    val shape = remember(phase, lane, yOffset, amplitude) {
        auroraBandShape(
            phase = phase,
            lane = lane,
            yOffset = yOffset,
            amplitude = amplitude,
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = color, shape = shape),
    )
}

private fun auroraBandShape(
    phase: Float,
    lane: Int,
    yOffset: Float,
    amplitude: Float,
): Shape = GenericShape { size, _ ->
    val width = size.width.coerceAtLeast(1f)
    val height = size.height.coerceAtLeast(1f)
    val clampedPhase = phase.coerceIn(0f, 1f)
    val laneOffset = lane * 0.19f
    val topBase = height * yOffset
    val waveAmplitude = height * amplitude

    fun waveY(xRatio: Float): Float {
        val radians = ((xRatio * 1.8f) + clampedPhase + laneOffset) * 2.0 * PI
        return topBase + (sin(radians).toFloat() * waveAmplitude)
    }

    moveTo(-0.08f * width, waveY(-0.08f))
    cubicTo(
        0.14f * width,
        waveY(0.08f),
        0.30f * width,
        waveY(0.26f),
        0.50f * width,
        waveY(0.50f),
    )
    cubicTo(
        0.70f * width,
        waveY(0.74f),
        0.86f * width,
        waveY(0.92f),
        1.08f * width,
        waveY(1.08f),
    )
    lineTo(1.08f * width, 1.10f * height)
    lineTo(-0.08f * width, 1.10f * height)
    close()
}

@Composable
private fun rememberImeReducedMotion(): Boolean {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(configuration) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f) == 0f
    }
}
