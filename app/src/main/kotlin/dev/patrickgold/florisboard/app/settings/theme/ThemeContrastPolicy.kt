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

package dev.patrickgold.florisboard.app.settings.theme

import androidx.compose.ui.graphics.Color
import org.florisboard.lib.snygg.value.SnyggDefinedVarValue
import org.florisboard.lib.snygg.value.SnyggStaticColorValue
import org.florisboard.lib.snygg.value.SnyggValue
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

internal const val ThemeTextContrastMinimum = 4.5

internal data class ThemeContrastWarning(
    val contrastRatio: Double,
)

/**
 * Returns a warning when an editable foreground/background pair is statically
 * resolvable but falls below the WCAG AA text contrast floor.
 *
 * Dynamic colors, inherited values, and translucent backgrounds are left to
 * the preview because their final contrast depends on runtime context.
 */
internal fun themeContrastWarning(
    propertyName: String,
    propertyValue: SnyggValue,
    siblingProperties: Map<String, SnyggValue>,
    definedVariables: Map<String, SnyggValue>,
): ThemeContrastWarning? {
    val (foregroundValue, backgroundValue) = when (propertyName) {
        "foreground" -> propertyValue to siblingProperties["background"]
        "background" -> siblingProperties["foreground"] to propertyValue
        else -> return null
    }
    val foreground = foregroundValue?.let { resolveThemeColor(it, definedVariables) } ?: return null
    val background = backgroundValue?.let { resolveThemeColor(it, definedVariables) } ?: return null
    if (foreground.alpha <= 0f || background.alpha < 1f) return null

    val visibleForeground = foreground.compositeOver(background)
    val foregroundLuminance = visibleForeground.relativeLuminance()
    val backgroundLuminance = background.relativeLuminance()
    val lighter = max(foregroundLuminance, backgroundLuminance)
    val darker = min(foregroundLuminance, backgroundLuminance)
    val contrastRatio = (lighter + 0.05) / (darker + 0.05)

    return contrastRatio
        .takeUnless { it >= ThemeTextContrastMinimum }
        ?.let(::ThemeContrastWarning)
}

private fun resolveThemeColor(
    value: SnyggValue,
    definedVariables: Map<String, SnyggValue>,
    seenVariables: Set<String> = emptySet(),
): Color? {
    return when (value) {
        is SnyggStaticColorValue -> value.color
        is SnyggDefinedVarValue -> {
            if (value.key in seenVariables) {
                null
            } else {
                definedVariables[value.key]?.let {
                    resolveThemeColor(it, definedVariables, seenVariables + value.key)
                }
            }
        }
        else -> null
    }
}

private fun Color.compositeOver(background: Color): Color {
    val foregroundAlpha = alpha.coerceIn(0f, 1f)
    return Color(
        red = red * foregroundAlpha + background.red * (1f - foregroundAlpha),
        green = green * foregroundAlpha + background.green * (1f - foregroundAlpha),
        blue = blue * foregroundAlpha + background.blue * (1f - foregroundAlpha),
        alpha = 1f,
    )
}

private fun Color.relativeLuminance(): Double {
    fun linearize(channel: Float): Double {
        val normalized = channel.toDouble()
        return if (normalized <= 0.03928) {
            normalized / 12.92
        } else {
            ((normalized + 0.055) / 1.055).pow(2.4)
        }
    }

    return 0.2126 * linearize(red) +
        0.7152 * linearize(green) +
        0.0722 * linearize(blue)
}
