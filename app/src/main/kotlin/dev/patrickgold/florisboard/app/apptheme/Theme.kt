/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.app.apptheme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import dev.patrickgold.florisboard.app.AppTheme
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.jetpref.datastore.model.collectAsState
import org.florisboard.lib.color.neutralDynamicColorScheme
import org.florisboard.lib.color.systemAccentOrDefault

private const val RefinedContrastLevel = 0.18

private const val LightBackgroundTint = 0.018f
private const val LightSurfaceTint = 0.012f
private const val LightContainerLowTint = 0.024f
private const val LightContainerTint = 0.032f
private const val LightContainerHighTint = 0.040f
private const val LightOutlineTint = 0.060f

private const val DarkBackgroundTint = 0.060f
private const val DarkSurfaceTint = 0.050f
private const val DarkContainerLowTint = 0.070f
private const val DarkContainerTint = 0.085f
private const val DarkContainerHighTint = 0.100f
private const val DarkOutlineTint = 0.130f

private const val AmoledContainerLowTint = 0.030f
private const val AmoledContainerTint = 0.045f
private const val AmoledContainerHighTint = 0.060f
private const val AmoledOutlineTint = 0.100f

/**
 * How a settings theme looks under the current system setting. Colors, surfaces and the system
 * bars all read this one value, so AUTO AMOLED can't end up black in light mode.
 */
internal data class AppThemePosture(val isDark: Boolean, val useAmoledSurfaces: Boolean) {
    val lightSystemBars: Boolean get() = !isDark
}

internal fun AppTheme.posture(systemDark: Boolean): AppThemePosture = when (this) {
    AppTheme.AUTO -> AppThemePosture(isDark = systemDark, useAmoledSurfaces = false)
    AppTheme.AUTO_AMOLED -> AppThemePosture(isDark = systemDark, useAmoledSurfaces = systemDark)
    AppTheme.LIGHT -> AppThemePosture(isDark = false, useAmoledSurfaces = false)
    AppTheme.DARK -> AppThemePosture(isDark = true, useAmoledSurfaces = false)
    AppTheme.AMOLED_DARK -> AppThemePosture(isDark = true, useAmoledSurfaces = true)
}

internal fun appColorScheme(posture: AppThemePosture, seedColor: Color): ColorScheme {
    return neutralDynamicColorScheme(
        primary = seedColor,
        isDark = posture.isDark,
        isAmoled = posture.useAmoledSurfaces,
        contrastLevel = RefinedContrastLevel,
        modifyColorScheme = {
            it.refinedSurfaces(
                tint = seedColor,
                isDark = posture.isDark,
                isAmoled = posture.useAmoledSurfaces,
            )
        },
    )
}

@Composable
fun getColorScheme(
    theme: AppTheme,
): ColorScheme {
    return appColorScheme(theme.posture(isSystemInDarkTheme()), currentSeedColor())
}

@Composable
private fun currentSeedColor(): Color {
    val prefs by FlorisPreferenceStore
    val accentColor by prefs.other.accentColor.collectAsState()
    return systemAccentOrDefault(accentColor)
}

fun ColorScheme.amoled(): ColorScheme {
    return this.copy(background = Color.Black, surface = Color.Black)
}

internal fun ColorScheme.refinedSurfaces(tint: Color, isDark: Boolean, isAmoled: Boolean): ColorScheme {
    return if (isAmoled) {
        copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceDim = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color.Black.accentTint(tint, AmoledContainerLowTint),
            surfaceContainer = Color.Black.accentTint(tint, AmoledContainerTint),
            surfaceContainerHigh = Color.Black.accentTint(tint, AmoledContainerHighTint),
            surfaceContainerHighest = Color.Black.accentTint(tint, DarkContainerHighTint),
            outlineVariant = outlineVariant.accentTint(tint, AmoledOutlineTint),
        )
    } else if (isDark) {
        copy(
            background = background.accentTint(tint, DarkBackgroundTint),
            surface = surface.accentTint(tint, DarkSurfaceTint),
            surfaceDim = surfaceDim.accentTint(tint, DarkBackgroundTint),
            surfaceContainerLowest = surfaceContainerLowest.accentTint(tint, DarkBackgroundTint),
            surfaceContainerLow = surfaceContainerLow.accentTint(tint, DarkContainerLowTint),
            surfaceContainer = surfaceContainer.accentTint(tint, DarkContainerTint),
            surfaceContainerHigh = surfaceContainerHigh.accentTint(tint, DarkContainerHighTint),
            surfaceContainerHighest = surfaceContainerHighest.accentTint(tint, DarkContainerHighTint),
            outlineVariant = outlineVariant.accentTint(tint, DarkOutlineTint),
        )
    } else {
        copy(
            background = background.accentTint(tint, LightBackgroundTint),
            surface = surface.accentTint(tint, LightSurfaceTint),
            surfaceDim = surfaceDim.accentTint(tint, LightBackgroundTint),
            surfaceBright = surfaceBright.accentTint(tint, LightSurfaceTint),
            surfaceContainerLowest = surfaceContainerLowest.accentTint(tint, LightSurfaceTint),
            surfaceContainerLow = surfaceContainerLow.accentTint(tint, LightContainerLowTint),
            surfaceContainer = surfaceContainer.accentTint(tint, LightContainerTint),
            surfaceContainerHigh = surfaceContainerHigh.accentTint(tint, LightContainerHighTint),
            surfaceContainerHighest = surfaceContainerHighest.accentTint(tint, LightContainerHighTint),
            outlineVariant = outlineVariant.accentTint(tint, LightOutlineTint),
        )
    }
}

private fun Color.accentTint(tint: Color, fraction: Float): Color {
    return lerp(this, tint, fraction)
}

@Composable
fun FlorisAppTheme(
    theme: AppTheme,
    content: @Composable () -> Unit,
) {
    val posture = theme.posture(isSystemInDarkTheme())
    val colors = appColorScheme(posture, currentSeedColor())

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = posture.lightSystemBars
                isAppearanceLightNavigationBars = posture.lightSystemBars
            }
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        shapes = Shapes,
        content = content,
    )
}
