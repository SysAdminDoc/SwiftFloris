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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.BuildConfig
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.util.InputMethodUtils
import dev.patrickgold.florisboard.app.settings.search.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import org.florisboard.lib.compose.FlorisSurfaceTokens
import org.florisboard.lib.compose.FlorisIconButton
import org.florisboard.lib.compose.FlorisButton
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
            modifier = Modifier
                .padding(8.dp)
                .testTag(SettingsHomeDashboardTag),
            statusTone = statusTone,
            statusIcon = statusIcon,
            statusTitle = statusTitle,
            statusSummary = statusSummary,
            statusSignalValue = statusSignalValue,
            statusActionLabel = statusActionLabel,
            buildVersion = BuildConfig.VERSION_NAME.substringBefore("-"),
            onStatusAction = statusAction,
            onSearch = { navController.navigate(Routes.Settings.Search) },
            onTyping = { navController.navigate(Routes.Settings.Typing) },
            onPersonalization = { navController.navigate(Routes.Settings.Theme) },
            onPrivacyData = { navController.navigate(Routes.Settings.PrivacyPosture) },
            onAdvanced = { navController.navigate(Routes.Settings.Addons) },
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

internal const val SettingsHomeDashboardTag = "settings_home_dashboard"

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
    onTyping: () -> Unit,
    onPersonalization: () -> Unit,
    onPrivacyData: () -> Unit,
    onAdvanced: () -> Unit,
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
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
            SettingsHomeNavigationCards(
                onTyping = onTyping,
                onPersonalization = onPersonalization,
                onPrivacyData = onPrivacyData,
                onAdvanced = onAdvanced,
                onAbout = onAbout,
            )
            SettingsHomeSignalGrid(
                toneColor = toneColor,
                statusValue = statusSignalValue,
                buildVersion = buildVersion,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = FlorisSurfaceTokens.HairlineAlpha))
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
        SettingsHomeTrustChips()
    }
}

@Composable
private fun SettingsHomeTrustChips() {
    val noCloud = stringRes(R.string.settings__home__trust_chip_no_cloud)
    val noTelemetry = stringRes(R.string.settings__home__trust_chip_no_telemetry)
    val offline = stringRes(R.string.settings__home__trust_chip_offline)
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    SettingsHomeAdaptiveGrid(
        maxColumns = 3,
        minCellWidth = 88.dp,
        spacing = 6.dp,
        singleRowWeights = listOf(1f, 1.28f, 0.92f),
        cells = listOf(
            { modifier -> SettingsHomeTrustChip(modifier, Icons.Default.CloudOff, noCloud, primary) },
            { modifier -> SettingsHomeTrustChip(modifier, Icons.Default.Shield, noTelemetry, secondary) },
            { modifier -> SettingsHomeTrustChip(modifier, Icons.Default.CheckCircle, offline, tertiary) },
        ),
    )
}

/**
 * Lays [cells] out in rows of up to [maxColumns]. A column is dropped whenever a cell would be
 * narrower than [minCellWidth] grown by the text size, so large text gets whole rows instead of
 * cut-off labels. Cells in a row share its height.
 */
@Composable
private fun SettingsHomeAdaptiveGrid(
    maxColumns: Int,
    minCellWidth: Dp,
    cells: List<@Composable (Modifier) -> Unit>,
    spacing: Dp = 8.dp,
    singleRowWeights: List<Float>? = null,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = settingsHomeColumnCount(
            availableWidth = maxWidth,
            fontScale = LocalDensity.current.fontScale,
            maxColumns = maxColumns,
            minCellWidth = minCellWidth,
            spacing = spacing,
        )
        val weights = singleRowWeights?.takeIf { columns >= cells.size }
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            cells.chunked(columns).forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                ) {
                    row.forEachIndexed { index, cell ->
                        cell(Modifier.weight(weights?.get(index) ?: 1f).fillMaxHeight())
                    }
                    repeat(columns - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

internal fun settingsHomeColumnCount(
    availableWidth: Dp,
    fontScale: Float,
    maxColumns: Int,
    minCellWidth: Dp,
    spacing: Dp,
): Int {
    val neededWidth = minCellWidth * fontScale.coerceAtLeast(1f)
    for (columns in maxColumns downTo 2) {
        val cellWidth = (availableWidth - spacing * (columns - 1)) / columns
        if (cellWidth >= neededWidth) return columns
    }
    return 1
}

@Composable
private fun SettingsHomeTrustChip(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    tint: Color,
) {
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 32.dp)
            .clip(MaterialTheme.shapes.small)
            .background(tint.copy(alpha = 0.10f))
            .border(
                width = 1.dp,
                color = tint.copy(alpha = 0.24f),
                shape = MaterialTheme.shapes.small,
            )
            .padding(horizontal = 7.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier
                .padding(end = 5.dp)
                .size(14.dp),
            imageVector = icon,
            contentDescription = null,
            tint = tint,
        )
        Text(
            modifier = Modifier.weight(1f),
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
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
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            FlorisButton(
                modifier = Modifier.fillMaxWidth(),
                icon = icon,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                text = actionLabel,
                onClick = onAction,
                maxLines = 2,
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
    val privacyTitle = stringRes(R.string.settings__home__signal_privacy_title)
    val privacyValue = stringRes(R.string.settings__home__signal_privacy_value)
    val buildTitle = stringRes(R.string.settings__home__signal_build_title)
    val buildValue = stringRes(R.string.settings__home__signal_build_value, "version" to buildVersion)
    val statusTitle = stringRes(R.string.settings__home__signal_status_title)
    val syncTitle = stringRes(R.string.settings__home__signal_sync_title)
    val syncValue = stringRes(R.string.settings__home__signal_sync_value)
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    SettingsHomeAdaptiveGrid(
        maxColumns = 2,
        minCellWidth = 120.dp,
        cells = listOf(
            { modifier -> SettingsHomeSignalTile(modifier, Icons.Default.CloudOff, privacyTitle, privacyValue, primary) },
            { modifier -> SettingsHomeSignalTile(modifier, Icons.Outlined.Build, buildTitle, buildValue, tertiary) },
            { modifier -> SettingsHomeSignalTile(modifier, Icons.Default.VerifiedUser, statusTitle, statusValue, toneColor) },
            { modifier -> SettingsHomeSignalTile(modifier, Icons.Default.Sync, syncTitle, syncValue, secondary) },
        ),
    )
}

@Composable
private fun SettingsHomeNavigationCards(
    onTyping: () -> Unit,
    onPersonalization: () -> Unit,
    onPrivacyData: () -> Unit,
    onAdvanced: () -> Unit,
    onAbout: () -> Unit,
) {
    val typingTitle = stringRes(R.string.settings__home__section_typing)
    val typingSummary = stringRes(R.string.settings__home__nav_typing_summary)
    val personalizationTitle = stringRes(R.string.settings__home__section_personalization)
    val personalizationSummary = stringRes(R.string.settings__home__nav_personalization_summary)
    val privacyTitle = stringRes(R.string.settings__home__section_privacy_data)
    val privacySummary = stringRes(R.string.settings__home__nav_privacy_summary)
    val advancedTitle = stringRes(R.string.settings__home__section_advanced)
    val advancedSummary = stringRes(R.string.settings__home__nav_advanced_summary)
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringRes(R.string.settings__home__navigation_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        SettingsHomeAdaptiveGrid(
            maxColumns = 2,
            minCellWidth = 120.dp,
            cells = listOf(
                { modifier ->
                    SettingsHomeNavigationCard(
                        modifier, Icons.Outlined.Keyboard, typingTitle, typingSummary, primary, onTyping,
                    )
                },
                { modifier ->
                    SettingsHomeNavigationCard(
                        modifier, Icons.Outlined.Palette, personalizationTitle, personalizationSummary,
                        secondary, onPersonalization,
                    )
                },
                { modifier ->
                    SettingsHomeNavigationCard(
                        modifier, Icons.Default.Shield, privacyTitle, privacySummary, tertiary, onPrivacyData,
                    )
                },
                { modifier ->
                    SettingsHomeNavigationCard(
                        modifier, Icons.Default.Extension, advancedTitle, advancedSummary, primary, onAdvanced,
                    )
                },
            ),
        )
        SettingsHomeNavigationCard(
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Outlined.Info,
            title = stringRes(R.string.settings__home__section_about),
            summary = stringRes(R.string.settings__home__nav_about_summary),
            tint = MaterialTheme.colorScheme.secondary,
            onClick = onAbout,
        )
    }
}

@Composable
private fun SettingsHomeNavigationCard(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    summary: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 86.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(
                width = 1.dp,
                color = tint.copy(alpha = 0.24f),
                shape = MaterialTheme.shapes.small,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(MaterialTheme.shapes.small)
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                modifier = Modifier.size(17.dp),
                imageVector = icon,
                contentDescription = null,
                tint = tint,
            )
        }
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsHomeSignalTile(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    value: String,
    tint: Color,
) {
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 60.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(
                width = 1.dp,
                color = tint.copy(alpha = 0.24f),
                shape = MaterialTheme.shapes.small,
            )
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            modifier = Modifier.size(16.dp),
            imageVector = icon,
            contentDescription = null,
            tint = tint,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
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
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = FlorisSurfaceTokens.HairlineAlpha),
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
            )
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = stringRes(R.string.settings__home__search_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    val importLabel = stringRes(R.string.settings__home__quick_action_import)
    val privacyLabel = stringRes(R.string.settings__home__quick_action_privacy)
    val aboutLabel = stringRes(R.string.settings__home__quick_action_about)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringRes(R.string.settings__home__quick_actions_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        SettingsHomeAdaptiveGrid(
            maxColumns = 3,
            minCellWidth = 90.dp,
            cells = listOf(
                { modifier -> SettingsHomeActionButton(modifier, Icons.Default.FileOpen, importLabel, onImport) },
                { modifier -> SettingsHomeActionButton(modifier, Icons.Default.Shield, privacyLabel, onPrivacy) },
                { modifier -> SettingsHomeActionButton(modifier, Icons.Outlined.Info, aboutLabel, onAbout) },
            ),
        )
    }
}

@Composable
private fun SettingsHomeActionButton(
    modifier: Modifier,
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
        maxLines = 2,
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
