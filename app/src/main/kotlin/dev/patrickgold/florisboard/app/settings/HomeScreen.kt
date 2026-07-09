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

package dev.patrickgold.florisboard.app.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.filled.AssistantDirection
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartButton
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.BuildConfig
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.util.InputMethodUtils
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import org.florisboard.lib.compose.FlorisIconButton
import org.florisboard.lib.compose.FlorisOutlinedButton
import org.florisboard.lib.compose.stringRes

@Composable
fun HomeScreen() = FlorisScreen {
    title = stringRes(R.string.settings__home__title)
    navigationIconVisible = false
    previewFieldVisible = true

    val navController = LocalNavController.current
    val context = LocalContext.current

    actions {
        FlorisIconButton(
            onClick = { navController.navigate(Routes.Settings.Search) },
            icon = Icons.Default.Search,
            contentDescription = stringRes(R.string.settings__search__title),
        )
    }

    content {
        val isFlorisBoardEnabled by InputMethodUtils.observeIsFlorisboardEnabled(foregroundOnly = true)
        val isFlorisBoardSelected by InputMethodUtils.observeIsFlorisboardSelected(foregroundOnly = true)

        val statusTone: SettingsHomeStatusTone
        val statusIcon: ImageVector
        val statusTitle: String
        val statusSummary: String
        val statusSignalValue: String
        val statusActionLabel: String?
        val statusAction: (() -> Unit)?
        when {
            !isFlorisBoardEnabled -> {
                statusTone = SettingsHomeStatusTone.Error
                statusIcon = Icons.Default.ErrorOutline
                statusTitle = stringRes(R.string.settings__home__ime_not_enabled)
                statusSummary = stringRes(R.string.settings__home__ime_not_enabled_summary)
                statusSignalValue = stringRes(R.string.settings__home__status_signal_setup_needed)
                statusActionLabel = stringRes(R.string.settings__home__open_keyboard_settings)
                statusAction = { InputMethodUtils.showImeEnablerActivity(context) }
            }
            !isFlorisBoardSelected -> {
                statusTone = SettingsHomeStatusTone.Warning
                statusIcon = Icons.Outlined.Warning
                statusTitle = stringRes(R.string.settings__home__ime_not_selected)
                statusSummary = stringRes(R.string.settings__home__ime_not_selected_summary)
                statusSignalValue = stringRes(R.string.settings__home__status_signal_choose_keyboard)
                statusActionLabel = stringRes(R.string.settings__home__choose_keyboard)
                statusAction = { InputMethodUtils.showImePicker(context) }
            }
            else -> {
                statusTone = SettingsHomeStatusTone.Success
                statusIcon = Icons.Default.CheckCircle
                statusTitle = stringRes(R.string.settings__home__ime_ready)
                statusSummary = stringRes(R.string.settings__home__ime_ready_summary)
                statusSignalValue = stringRes(R.string.settings__home__status_signal_ready)
                statusActionLabel = null
                statusAction = null
            }
        }

        SettingsHomeDashboard(
            modifier = Modifier.padding(8.dp),
            statusTone = statusTone,
            statusIcon = statusIcon,
            statusTitle = statusTitle,
            statusSummary = statusSummary,
            statusSignalValue = statusSignalValue,
            statusActionLabel = statusActionLabel,
            buildVersion = BuildConfig.VERSION_NAME.substringBefore("-"),
            onStatusAction = statusAction,
            onSearch = { navController.navigate(Routes.Settings.Search) },
            onImport = { navController.navigate(Routes.Settings.MigrationAssistant) },
            onPrivacy = { navController.navigate(Routes.Settings.PrivacyPosture) },
            onAbout = { navController.navigate(Routes.Settings.About) },
        )

        PreferenceGroup(title = stringRes(R.string.settings__home__section_typing)) {
            Preference(
                icon = Icons.Outlined.Keyboard,
                title = stringRes(R.string.settings__keyboard__title),
                summary = stringRes(R.string.settings__home__keyboard_summary),
                onClick = { navController.navigate(Routes.Settings.Keyboard) },
            )
            Preference(
                icon = Icons.Default.Spellcheck,
                title = stringRes(R.string.settings__typing__title),
                summary = stringRes(R.string.settings__home__typing_summary),
                onClick = { navController.navigate(Routes.Settings.Typing) },
            )
            Preference(
                icon = Icons.Default.SmartButton,
                title = stringRes(R.string.settings__smartbar__title),
                summary = stringRes(R.string.settings__home__smartbar_summary),
                onClick = { navController.navigate(Routes.Settings.Smartbar) },
            )
            Preference(
                icon = Icons.Default.Vibration,
                title = stringRes(R.string.settings__input_feedback__title),
                summary = stringRes(R.string.settings__home__input_feedback_summary),
                onClick = { navController.navigate(Routes.Settings.InputFeedback) },
            )
            Preference(
                icon = Icons.Default.Gesture,
                title = stringRes(R.string.settings__gestures__title),
                summary = stringRes(R.string.settings__home__gestures_summary),
                onClick = { navController.navigate(Routes.Settings.Gestures) },
            )
            Preference(
                icon = Icons.Default.Mic,
                title = stringRes(R.string.settings__voice_input__title),
                summary = stringRes(R.string.settings__home__voice_input_summary),
                onClick = { navController.navigate(Routes.Settings.VoiceInput) },
            )
        }

        PreferenceGroup(title = stringRes(R.string.settings__home__section_personalization)) {
            Preference(
                icon = Icons.Outlined.Palette,
                title = stringRes(R.string.settings__theme__title),
                summary = stringRes(R.string.settings__home__theme_summary),
                onClick = { navController.navigate(Routes.Settings.Theme) },
            )
            Preference(
                icon = Icons.Default.SentimentSatisfiedAlt,
                title = stringRes(R.string.settings__media__title),
                summary = stringRes(R.string.settings__home__media_summary),
                onClick = { navController.navigate(Routes.Settings.Media) },
            )
            Preference(
                icon = Icons.Default.Language,
                title = stringRes(R.string.settings__localization__title),
                summary = stringRes(R.string.settings__home__localization_summary),
                onClick = { navController.navigate(Routes.Settings.Localization) },
            )
        }

        PreferenceGroup(title = stringRes(R.string.settings__home__section_privacy_data)) {
            Preference(
                icon = Icons.AutoMirrored.Outlined.Assignment,
                title = stringRes(R.string.settings__clipboard__title),
                summary = stringRes(R.string.settings__home__clipboard_summary),
                onClick = { navController.navigate(Routes.Settings.Clipboard) },
            )
            Preference(
                icon = Icons.AutoMirrored.Filled.LibraryBooks,
                title = stringRes(R.string.settings__dictionary__title),
                summary = stringRes(R.string.settings__home__dictionary_summary),
                onClick = { navController.navigate(Routes.Settings.Dictionary) },
            )
            Preference(
                icon = Icons.Default.Sync,
                title = stringRes(R.string.settings__sync__title),
                summary = stringRes(R.string.settings__home__sync_summary),
                onClick = { navController.navigate(Routes.Settings.Sync) },
            )
            Preference(
                icon = Icons.Default.Shield,
                title = stringRes(R.string.settings__privacy_posture__title),
                summary = stringRes(R.string.settings__privacy_posture__home_summary),
                onClick = { navController.navigate(Routes.Settings.PrivacyPosture) },
            )
            Preference(
                icon = Icons.AutoMirrored.Filled.AssistantDirection,
                title = stringRes(R.string.settings__migration_assistant__title),
                summary = stringRes(R.string.settings__migration_assistant__home_summary),
                onClick = { navController.navigate(Routes.Settings.MigrationAssistant) },
            )
            Preference(
                icon = Icons.Default.Archive,
                title = stringRes(R.string.backup_and_restore__back_up__title),
                summary = stringRes(R.string.settings__home__backup_summary),
                onClick = { navController.navigate(Routes.Settings.Backup) },
            )
            Preference(
                icon = Icons.Default.SettingsBackupRestore,
                title = stringRes(R.string.backup_and_restore__restore__title),
                summary = stringRes(R.string.settings__home__restore_summary),
                onClick = { navController.navigate(Routes.Settings.Restore) },
            )
        }

        PreferenceGroup(title = stringRes(R.string.settings__home__section_advanced)) {
            Preference(
                icon = Icons.Default.Extension,
                title = stringRes(R.string.ext__home__title),
                summary = stringRes(R.string.settings__home__extensions_summary),
                onClick = { navController.navigate(Routes.Ext.Home) },
            )
            Preference(
                icon = Icons.Default.Extension,
                title = stringRes(R.string.settings__addons__title),
                summary = stringRes(R.string.settings__home__addons_summary),
                onClick = { navController.navigate(Routes.Settings.Addons) },
            )
            Preference(
                icon = Icons.Default.Extension,
                title = stringRes(R.string.settings__mcp__title),
                summary = stringRes(R.string.settings__home__mcp_summary),
                onClick = { navController.navigate(Routes.Settings.Mcp) },
            )
            Preference(
                icon = ImageVector.vectorResource(R.drawable.ic_keyboard_keys),
                title = stringRes(R.string.physical_keyboard__title),
                summary = stringRes(R.string.settings__other__physical_keyboard_summary),
                onClick = { navController.navigate(Routes.Settings.PhysicalKeyboard) },
            )
            Preference(
                icon = Icons.Outlined.Build,
                title = stringRes(R.string.settings__other__title),
                summary = stringRes(R.string.settings__home__other_summary),
                onClick = { navController.navigate(Routes.Settings.Other) },
            )
        }

        PreferenceGroup(title = stringRes(R.string.settings__home__section_about)) {
            Preference(
                icon = Icons.Outlined.Info,
                title = stringRes(R.string.about__title),
                summary = stringRes(R.string.settings__home__about_summary),
                onClick = { navController.navigate(Routes.Settings.About) },
            )
        }
    }
}

private enum class SettingsHomeStatusTone {
    Success,
    Warning,
    Error,
}

@Composable
private fun SettingsHomeDashboard(
    statusTone: SettingsHomeStatusTone,
    statusIcon: ImageVector,
    statusTitle: String,
    statusSummary: String,
    statusSignalValue: String,
    statusActionLabel: String?,
    buildVersion: String,
    modifier: Modifier = Modifier,
    onStatusAction: (() -> Unit)?,
    onSearch: () -> Unit,
    onImport: () -> Unit,
    onPrivacy: () -> Unit,
    onAbout: () -> Unit,
) {
    val toneColor = statusTone.toColor()
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(
            width = 1.dp,
            color = toneColor.copy(alpha = 0.32f),
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsHomeDashboardHeader()
            SettingsHomeStatusRow(
                toneColor = toneColor,
                icon = statusIcon,
                title = statusTitle,
                summary = statusSummary,
                actionLabel = statusActionLabel,
                onAction = onStatusAction,
            )
            SettingsHomeSearchRail(onSearch = onSearch)
            SettingsHomeSignalGrid(
                toneColor = toneColor,
                statusValue = statusSignalValue,
                buildVersion = buildVersion,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f))
            SettingsHomeQuickActions(
                onImport = onImport,
                onPrivacy = onPrivacy,
                onAbout = onAbout,
            )
        }
    }
}

@Composable
private fun SettingsHomeDashboardHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringRes(R.string.settings__home__dashboard_eyebrow),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = stringRes(R.string.settings__home__privacy_posture),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = stringRes(R.string.settings__home__dashboard_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsHomeStatusRow(
    toneColor: Color,
    icon: ImageVector,
    title: String,
    summary: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
) {
    val statusA11y = stringRes(
        R.string.settings__home__overview_status_a11y,
        "status_title" to title,
        "status_summary" to summary,
    )
    Column(
        modifier = Modifier.semantics {
            contentDescription = statusA11y
        },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(44.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(toneColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    modifier = Modifier.size(24.dp),
                    imageVector = icon,
                    contentDescription = null,
                    tint = toneColor,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            FlorisOutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                text = actionLabel,
                onClick = onAction,
            )
        }
    }
}

@Composable
private fun SettingsHomeSignalGrid(
    toneColor: Color,
    statusValue: String,
    buildVersion: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingsHomeSignalTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.CloudOff,
                title = stringRes(R.string.settings__home__signal_privacy_title),
                value = stringRes(R.string.settings__home__signal_privacy_value),
                tint = MaterialTheme.colorScheme.primary,
            )
            SettingsHomeSignalTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Build,
                title = stringRes(R.string.settings__home__signal_build_title),
                value = stringRes(
                    R.string.settings__home__signal_build_value,
                    "version" to buildVersion,
                ),
                tint = MaterialTheme.colorScheme.tertiary,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingsHomeSignalTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.VerifiedUser,
                title = stringRes(R.string.settings__home__signal_status_title),
                value = statusValue,
                tint = toneColor,
            )
            SettingsHomeSignalTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Sync,
                title = stringRes(R.string.settings__home__signal_sync_title),
                value = stringRes(R.string.settings__home__signal_sync_value),
                tint = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun SettingsHomeSignalTile(
    icon: ImageVector,
    title: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 68.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(
                width = 1.dp,
                color = tint.copy(alpha = 0.24f),
                shape = MaterialTheme.shapes.small,
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            modifier = Modifier.size(18.dp),
            imageVector = icon,
            contentDescription = null,
            tint = tint,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SettingsHomeSearchRail(
    onSearch: () -> Unit,
) {
    val description = stringRes(R.string.settings__home__search_a11y)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f),
                shape = MaterialTheme.shapes.small,
            )
            .clickable(role = Role.Button, onClick = onSearch)
            .semantics(mergeDescendants = true) {
                contentDescription = description
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier
                .padding(end = 14.dp)
                .size(24.dp),
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = stringRes(R.string.settings__search__placeholder),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = stringRes(R.string.settings__home__search_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SettingsHomeQuickActions(
    onImport: () -> Unit,
    onPrivacy: () -> Unit,
    onAbout: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringRes(R.string.settings__home__quick_actions_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingsHomeActionButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.FileOpen,
                label = stringRes(R.string.settings__home__quick_action_import),
                onClick = onImport,
            )
            SettingsHomeActionButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Shield,
                label = stringRes(R.string.settings__home__quick_action_privacy),
                onClick = onPrivacy,
            )
            SettingsHomeActionButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Info,
                label = stringRes(R.string.settings__home__quick_action_about),
                onClick = onAbout,
            )
        }
    }
}

@Composable
private fun SettingsHomeActionButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    FlorisOutlinedButton(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
        icon = icon,
        text = label,
        onClick = onClick,
    )
}

@Composable
private fun SettingsHomeStatusTone.toColor(): Color {
    return when (this) {
        SettingsHomeStatusTone.Success -> MaterialTheme.colorScheme.primary
        SettingsHomeStatusTone.Warning -> MaterialTheme.colorScheme.tertiary
        SettingsHomeStatusTone.Error -> MaterialTheme.colorScheme.error
    }
}
