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

package dev.patrickgold.florisboard.app.settings.search

import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp

/** Above this font scale a settings row's text wraps instead of being cut short. */
internal const val SettingsRowWrapFontScale = 1.3f

/**
 * The list item every settings row is drawn with. It's JetPref's `JetPrefListItem`, which cuts the
 * title to one line and the summary to two with "...", except that above
 * [SettingsRowWrapFontScale] all of its text wraps, so someone using large text can still read
 * what a setting does. At normal sizes it lays out exactly as JetPref's does, so lists don't grow.
 */
@Composable
fun SettingsListItem(
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    overlineText: String? = null,
    text: String,
    secondaryText: String? = null,
    singleLineSecondaryText: Boolean = false,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    colors: ListItemColors = ListItemDefaults.colors(),
    tonalElevation: Dp = ListItemDefaults.Elevation,
    shadowElevation: Dp = ListItemDefaults.Elevation,
) {
    val wraps = LocalDensity.current.fontScale > SettingsRowWrapFontScale
    ListItem(
        modifier = modifier.alpha(if (enabled) 1.0f else 0.38f),
        leadingContent = icon,
        overlineContent = overlineText?.takeIf { it.isNotBlank() }?.let { str ->
            {
                Text(
                    text = str,
                    maxLines = if (wraps) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        headlineContent = {
            Text(
                text = text,
                maxLines = if (wraps) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = secondaryText?.takeIf { it.isNotBlank() }?.let { str ->
            {
                Text(
                    text = str,
                    maxLines = when {
                        wraps -> Int.MAX_VALUE
                        singleLineSecondaryText -> 1
                        else -> 2
                    },
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        trailingContent = trailing,
        colors = colors,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
    )
}
