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
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import dev.patrickgold.florisboard.app.AppTheme
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.jetpref.datastore.model.collectAsState
import org.florisboard.lib.color.neutralDynamicColorScheme
import org.florisboard.lib.color.systemAccentOrDefault

private const val RefinedContrastLevel = 0.18

private val LightBackground = Color(0xFFFAFCF8)
private val LightSurfaceBase = Color(0xFFFFFFFF)
private val LightSurfaceLow = Color(0xFFF4F7F2)
private val LightSurfaceContainer = Color(0xFFEEF3EC)
private val LightSurfaceHigh = Color(0xFFE8EEE5)
private val LightOutlineVariant = Color(0xFFC7D1C2)

private val DarkBackground = Color(0xFF0B0F0D)
private val DarkSurface = Color(0xFF111612)
private val DarkSurfaceLow = Color(0xFF161D18)
private val DarkSurfaceContainer = Color(0xFF1B231E)
private val DarkSurfaceHigh = Color(0xFF222B25)
private val DarkOutlineVariant = Color(0xFF334239)

@Composable
fun getColorScheme(
    theme: AppTheme,
): ColorScheme {
    val prefs by FlorisPreferenceStore
    val accentColor by prefs.other.accentColor.collectAsState()

    val seedColor = systemAccentOrDefault(accentColor)
    val systemDark = isSystemInDarkTheme()

    return when (theme) {
        AppTheme.AUTO, AppTheme.AUTO_AMOLED -> {
            neutralDynamicColorScheme(
                primary = seedColor,
                isDark = systemDark,
                isAmoled = theme == AppTheme.AUTO_AMOLED,
                contrastLevel = RefinedContrastLevel,
                modifyColorScheme = {
                    it.refinedSurfaces(
                        isDark = systemDark,
                        isAmoled = theme == AppTheme.AUTO_AMOLED,
                    )
                },
            )
        }

        AppTheme.DARK, AppTheme.LIGHT -> {
            neutralDynamicColorScheme(
                primary = seedColor,
                isDark = theme == AppTheme.DARK,
                contrastLevel = RefinedContrastLevel,
                modifyColorScheme = {
                    it.refinedSurfaces(isDark = theme == AppTheme.DARK, isAmoled = false)
                },
            )
        }

        AppTheme.AMOLED_DARK -> {
            neutralDynamicColorScheme(
                primary = seedColor,
                isDark = true,
                isAmoled = true,
                contrastLevel = RefinedContrastLevel,
                modifyColorScheme = {
                    it.refinedSurfaces(isDark = true, isAmoled = true)
                },
            )
        }
    }
}

fun ColorScheme.amoled(): ColorScheme {
    return this.copy(background = Color.Black, surface = Color.Black)
}

private fun ColorScheme.refinedSurfaces(isDark: Boolean, isAmoled: Boolean): ColorScheme {
    return if (isDark) {
        copy(
            background = if (isAmoled) Color.Black else DarkBackground,
            surface = if (isAmoled) Color.Black else DarkSurface,
            surfaceDim = if (isAmoled) Color.Black else DarkBackground,
            surfaceContainerLowest = if (isAmoled) Color.Black else DarkBackground,
            surfaceContainerLow = if (isAmoled) Color(0xFF080B09) else DarkSurfaceLow,
            surfaceContainer = if (isAmoled) Color(0xFF0D120F) else DarkSurfaceContainer,
            surfaceContainerHigh = if (isAmoled) Color(0xFF141A16) else DarkSurfaceHigh,
            surfaceContainerHighest = if (isAmoled) Color(0xFF1A211C) else Color(0xFF2A342D),
            outlineVariant = DarkOutlineVariant,
        )
    } else {
        copy(
            background = LightBackground,
            surface = LightSurfaceBase,
            surfaceDim = Color(0xFFE1E8DD),
            surfaceBright = LightSurfaceBase,
            surfaceContainerLowest = LightSurfaceBase,
            surfaceContainerLow = LightSurfaceLow,
            surfaceContainer = LightSurfaceContainer,
            surfaceContainerHigh = LightSurfaceHigh,
            surfaceContainerHighest = Color(0xFFE1E8DD),
            outlineVariant = LightOutlineVariant,
        )
    }
}

@Composable
fun FlorisAppTheme(
    theme: AppTheme,
    content: @Composable () -> Unit,
) {
    val colors = getColorScheme(theme = theme)

    val darkTheme =
        theme == AppTheme.DARK
            || theme == AppTheme.AMOLED_DARK
            || (theme == AppTheme.AUTO && isSystemInDarkTheme())
            || (theme == AppTheme.AUTO_AMOLED && isSystemInDarkTheme())

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
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
