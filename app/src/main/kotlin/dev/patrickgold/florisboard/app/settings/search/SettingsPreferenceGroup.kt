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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.jetpref.datastore.model.PreferenceDataEvaluatorScope
import dev.patrickgold.jetpref.datastore.model.PreferenceModel
import dev.patrickgold.jetpref.datastore.ui.LocalIconSpaceReserved
import dev.patrickgold.jetpref.datastore.ui.LocalIsPrefEnabled
import dev.patrickgold.jetpref.datastore.ui.LocalIsPrefVisible
import dev.patrickgold.jetpref.datastore.ui.PreferenceUiScope

/** Corner radius of a settings group's card. */
internal val SettingsGroupCornerRadius = 16.dp

/**
 * A settings section: a small uppercase heading in the accent color over a rounded card holding the section's rows.
 * Same parameters and enabled/visible rules as JetPref's own `PreferenceGroup`, which drew a plain heading over
 * loose rows.
 */
@Composable
fun <T : PreferenceModel> PreferenceUiScope<T>.PreferenceGroup(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconSpaceReserved: Boolean = LocalIconSpaceReserved.current,
    title: String,
    enabledIf: @Composable PreferenceDataEvaluatorScope.() -> Boolean = { true },
    visibleIf: @Composable PreferenceDataEvaluatorScope.() -> Boolean = { true },
    content: @Composable PreferenceUiScope<T>.() -> Unit,
) {
    val isEnabled = LocalIsPrefEnabled.current && PreferenceDataEvaluatorScope.enabledIf()
    val isVisible = LocalIsPrefVisible.current && PreferenceDataEvaluatorScope.visibleIf()
    if (!isVisible) return
    val groupPrefs = this.prefs
    val shape = RoundedCornerShape(SettingsGroupCornerRadius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        if (title.isNotBlank()) {
            SettingsGroupHeading(title = title, icon = icon)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f), shape),
        ) card@{
            CompositionLocalProvider(
                LocalIsPrefEnabled provides isEnabled,
                LocalIsPrefVisible provides isVisible,
                LocalIconSpaceReserved provides iconSpaceReserved,
            ) {
                content(PreferenceUiScope(groupPrefs, this@card))
            }
        }
    }
}

@Composable
private fun SettingsGroupHeading(title: String, icon: ImageVector?) {
    val locale = LocalConfiguration.current.locales[0]
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
            // TalkBack reads the title as written, not letter by letter from the uppercased label.
            .semantics(mergeDescendants = true) {
                heading()
                contentDescription = title
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) {
            Icon(
                modifier = Modifier.size(16.dp),
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = title.uppercase(locale),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp,
            ),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
