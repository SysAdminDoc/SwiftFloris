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

package dev.patrickgold.florisboard.ime.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import org.florisboard.lib.compose.stringRes

private data class PerAppAccentPreviewSample(
    val label: String,
    val color: Color,
)

private val PerAppAccentPreviewSamples = listOf(
    PerAppAccentPreviewSample("Slack", Color(0xFF611F69)),
    PerAppAccentPreviewSample("WhatsApp", Color(0xFF25D366)),
    PerAppAccentPreviewSample("Discord", Color(0xFF5865F2)),
    PerAppAccentPreviewSample("Telegram", Color(0xFF229ED9)),
)

@Composable
fun PerAppAccentPreview(modifier: Modifier = Modifier) {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val selectedSample = PerAppAccentPreviewSamples[selectedIndex]
    val defaultAccent = MaterialTheme.colorScheme.primary

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringRes(R.string.settings__per_app_accent_preview__title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringRes(R.string.settings__per_app_accent_preview__summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PerAppAccentPreviewSamples.forEachIndexed { index, sample ->
                    AccentSampleTile(
                        sample = sample,
                        selected = index == selectedIndex,
                        onClick = { selectedIndex = index },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KeyboardPreviewPane(
                    label = stringRes(R.string.settings__per_app_accent_preview__default_label),
                    accent = defaultAccent,
                    modifier = Modifier.weight(1f),
                )
                KeyboardPreviewPane(
                    label = stringRes(R.string.settings__per_app_accent_preview__app_label)
                        .replace("{app}", selectedSample.label),
                    accent = selectedSample.color,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AccentSampleTile(
    sample: PerAppAccentPreviewSample,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val outlineColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Surface(
        modifier = Modifier
            .width(84.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, outlineColor),
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(sample.color),
            )
            Text(
                text = sample.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun KeyboardPreviewPane(
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            PreviewKey(accent = accent, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                PreviewKey(modifier = Modifier.weight(1f))
                PreviewKey(modifier = Modifier.weight(1f))
                PreviewKey(modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                PreviewKey(modifier = Modifier.weight(1f))
                PreviewKey(accent = accent, modifier = Modifier.weight(1.8f))
                PreviewKey(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PreviewKey(
    modifier: Modifier = Modifier,
    accent: Color? = null,
) {
    val background = accent ?: MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = modifier
            .height(16.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(background),
    )
}
