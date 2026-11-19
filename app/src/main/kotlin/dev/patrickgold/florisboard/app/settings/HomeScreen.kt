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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AssistantDirection
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartButton
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.settings.about.AboutScreen
import dev.patrickgold.florisboard.app.settings.clipboard.ClipboardScreen
import dev.patrickgold.florisboard.app.settings.dictionary.DictionaryScreen
import dev.patrickgold.florisboard.app.settings.dictionary.MigrationAssistantScreen
import dev.patrickgold.florisboard.app.settings.gestures.GesturesScreen
import dev.patrickgold.florisboard.app.settings.keyboard.InputFeedbackScreen
import dev.patrickgold.florisboard.app.settings.keyboard.KeyboardScreen
import dev.patrickgold.florisboard.app.settings.media.MediaScreen
import dev.patrickgold.florisboard.app.settings.privacy.PrivacyPostureScreen
import dev.patrickgold.florisboard.app.settings.smartbar.SmartbarScreen
import dev.patrickgold.florisboard.app.settings.theme.ThemeScreen
import dev.patrickgold.florisboard.app.settings.typing.TypingScreen
import dev.patrickgold.florisboard.app.settings.voice.VoiceInputScreen
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.compose.LocalFlorisScreenEmbedded
import dev.patrickgold.florisboard.lib.util.InputMethodUtils
import kotlinx.coroutines.delay
import org.florisboard.lib.compose.FlorisButton
import org.florisboard.lib.compose.FlorisSpacing
import org.florisboard.lib.compose.FlorisSurfaceTokens
import org.florisboard.lib.compose.stringRes

internal const val SettingsHomeListTag = "settings_home_list"

internal fun settingsHomeRowTag(id: String) = "settings_home_row_$id"

private const val ExpandAnimationMillis = 220

@Composable
fun HomeScreen() = FlorisScreen {
    title = stringRes(R.string.settings__home__title)
    navigationIconVisible = false
    previewFieldVisible = true

    val navController = LocalNavController.current
    val context = LocalContext.current
    val isFlorisBoardEnabled by InputMethodUtils.observeIsFlorisboardEnabled(foregroundOnly = true)
    val isFlorisBoardSelected by InputMethodUtils.observeIsFlorisboardSelected(foregroundOnly = true)

    actions {
        if (isFlorisBoardEnabled && isFlorisBoardSelected) {
            SettingsHomeActiveBadge()
        }
    }

    content {
        when {
            !isFlorisBoardEnabled -> SettingsHomeSetupCard(
                tone = MaterialTheme.colorScheme.error,
                icon = Icons.Default.ErrorOutline,
                title = stringRes(R.string.settings__home__ime_not_enabled),
                summary = stringRes(R.string.settings__home__ime_not_enabled_summary),
                actionLabel = stringRes(R.string.settings__home__open_keyboard_settings),
                onAction = { InputMethodUtils.showImeEnablerActivity(context) },
            )
            !isFlorisBoardSelected -> SettingsHomeSetupCard(
                tone = MaterialTheme.colorScheme.tertiary,
                icon = Icons.Outlined.Warning,
                title = stringRes(R.string.settings__home__ime_not_selected),
                summary = stringRes(R.string.settings__home__ime_not_selected_summary),
                actionLabel = stringRes(R.string.settings__home__choose_keyboard),
                onAction = { InputMethodUtils.showImePicker(context) },
            )
        }
        SettingsHomeSearchField(onClick = { navController.navigate(Routes.Settings.Search) })
        SettingsHomeList(entries = settingsHomeEntries())
    }
}

/** One row on the Home screen. */
private sealed interface SettingsHomeEntry {
    val id: String
    val icon: ImageVector
    val title: String
    val summary: String

    /** A settings page that opens in place, under its row. */
    class Page(
        override val id: String,
        override val icon: ImageVector,
        override val title: String,
        override val summary: String,
        val page: @Composable () -> Unit,
    ) : SettingsHomeEntry

    /** A page that needs a screen of its own: a long list, a floating button or a bottom bar. */
    class Link(
        override val id: String,
        override val icon: ImageVector,
        override val title: String,
        override val summary: String,
        val onClick: () -> Unit,
    ) : SettingsHomeEntry

    /** A few related pages, listed in place under the row. */
    class Group(
        override val id: String,
        override val icon: ImageVector,
        override val title: String,
        override val summary: String,
        val links: List<SettingsHomeLink>,
    ) : SettingsHomeEntry
}

private class SettingsHomeLink(val title: String, val onClick: () -> Unit)

@Composable
private fun settingsHomeEntries(): List<SettingsHomeEntry> {
    val navController = LocalNavController.current
    return listOf(
        SettingsHomeEntry.Page(
            id = "keyboard",
            icon = Icons.Outlined.Keyboard,
            title = stringRes(R.string.settings__keyboard__title),
            summary = stringRes(R.string.settings__home__keyboard_summary),
        ) { KeyboardScreen() },
        SettingsHomeEntry.Page(
            id = "typing",
            icon = Icons.Default.Spellcheck,
            title = stringRes(R.string.settings__typing__title),
            summary = stringRes(R.string.settings__home__typing_summary),
        ) { TypingScreen() },
        SettingsHomeEntry.Page(
            id = "smartbar",
            icon = Icons.Default.SmartButton,
            title = stringRes(R.string.settings__smartbar__title),
            summary = stringRes(R.string.settings__home__smartbar_summary),
        ) { SmartbarScreen() },
        SettingsHomeEntry.Page(
            id = "input-feedback",
            icon = Icons.Default.Vibration,
            title = stringRes(R.string.settings__input_feedback__title),
            summary = stringRes(R.string.settings__home__input_feedback_summary),
        ) { InputFeedbackScreen() },
        SettingsHomeEntry.Page(
            id = "gestures",
            icon = Icons.Default.Gesture,
            title = stringRes(R.string.settings__gestures__title),
            summary = stringRes(R.string.settings__home__gestures_summary),
        ) { GesturesScreen() },
        SettingsHomeEntry.Page(
            id = "voice",
            icon = Icons.Default.Mic,
            title = stringRes(R.string.settings__voice_input__title),
            summary = stringRes(R.string.settings__home__voice_input_summary),
        ) { VoiceInputScreen() },
        SettingsHomeEntry.Page(
            id = "theme",
            icon = Icons.Outlined.Palette,
            title = stringRes(R.string.settings__theme__title),
            summary = stringRes(R.string.settings__home__theme_summary),
        ) { ThemeScreen() },
        SettingsHomeEntry.Page(
            id = "media",
            icon = Icons.Default.SentimentSatisfiedAlt,
            title = stringRes(R.string.settings__media__title),
            summary = stringRes(R.string.settings__home__media_summary),
        ) { MediaScreen() },
        SettingsHomeEntry.Link(
            id = "localization",
            icon = Icons.Default.Language,
            title = stringRes(R.string.settings__localization__title),
            summary = stringRes(R.string.settings__home__localization_summary),
            onClick = { navController.navigate(Routes.Settings.Localization) },
        ),
        SettingsHomeEntry.Page(
            id = "clipboard",
            icon = Icons.AutoMirrored.Outlined.Assignment,
            title = stringRes(R.string.settings__clipboard__title),
            summary = stringRes(R.string.settings__home__clipboard_summary),
        ) { ClipboardScreen() },
        SettingsHomeEntry.Page(
            id = "dictionary",
            icon = Icons.AutoMirrored.Filled.LibraryBooks,
            title = stringRes(R.string.settings__dictionary__title),
            summary = stringRes(R.string.settings__home__dictionary_summary),
        ) { DictionaryScreen() },
        SettingsHomeEntry.Page(
            id = "migration",
            icon = Icons.AutoMirrored.Filled.AssistantDirection,
            title = stringRes(R.string.settings__migration_assistant__title),
            summary = stringRes(R.string.settings__migration_assistant__home_summary),
        ) { MigrationAssistantScreen() },
        SettingsHomeEntry.Link(
            id = "sync",
            icon = Icons.Default.Sync,
            title = stringRes(R.string.settings__sync__title),
            summary = stringRes(R.string.settings__home__sync_summary),
            onClick = { navController.navigate(Routes.Settings.Sync) },
        ),
        SettingsHomeEntry.Group(
            id = "backup",
            icon = Icons.Default.Archive,
            title = stringRes(R.string.settings__home__backup_restore_title),
            summary = stringRes(R.string.settings__home__backup_restore_summary),
            links = listOf(
                SettingsHomeLink(stringRes(R.string.backup_and_restore__back_up__title)) {
                    navController.navigate(Routes.Settings.Backup)
                },
                SettingsHomeLink(stringRes(R.string.backup_and_restore__restore__title)) {
                    navController.navigate(Routes.Settings.Restore)
                },
            ),
        ),
        SettingsHomeEntry.Page(
            id = "privacy",
            icon = Icons.Default.Shield,
            title = stringRes(R.string.settings__privacy_posture__title),
            summary = stringRes(R.string.settings__privacy_posture__home_summary),
        ) { PrivacyPostureScreen() },
        SettingsHomeEntry.Group(
            id = "advanced",
            icon = Icons.Outlined.Build,
            title = stringRes(R.string.settings__home__section_advanced),
            summary = stringRes(R.string.settings__home__nav_advanced_summary),
            links = listOf(
                SettingsHomeLink(stringRes(R.string.ext__home__title)) {
                    navController.navigate(Routes.Ext.Home)
                },
                SettingsHomeLink(stringRes(R.string.settings__addons__title)) {
                    navController.navigate(Routes.Settings.Addons)
                },
                SettingsHomeLink(stringRes(R.string.settings__mcp__title)) {
                    navController.navigate(Routes.Settings.Mcp)
                },
                SettingsHomeLink(stringRes(R.string.physical_keyboard__title)) {
                    navController.navigate(Routes.Settings.PhysicalKeyboard)
                },
                SettingsHomeLink(stringRes(R.string.settings__other__title)) {
                    navController.navigate(Routes.Settings.Other)
                },
            ),
        ),
        SettingsHomeEntry.Page(
            id = "about",
            icon = Icons.Outlined.Info,
            title = stringRes(R.string.about__title),
            summary = stringRes(R.string.settings__home__about_summary),
        ) { AboutScreen() },
    )
}

private val HomeRowIconSize = 24.dp

@Composable
private fun SettingsHomeList(entries: List<SettingsHomeEntry>) {
    // One row open at a time keeps the page short.
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = FlorisSurfaceTokens.HairlineAlpha)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = FlorisSpacing.Small)
            .testTag(SettingsHomeListTag),
    ) {
        entries.forEachIndexed { index, entry ->
            if (index > 0) {
                HorizontalDivider(color = dividerColor)
            }
            val expanded = expandedId == entry.id
            SettingsHomeRow(
                entry = entry,
                expanded = expanded,
                onClick = {
                    if (entry is SettingsHomeEntry.Link) {
                        entry.onClick()
                    } else {
                        expandedId = if (expanded) null else entry.id
                    }
                },
            )
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(ExpandAnimationMillis)) + fadeIn(tween(ExpandAnimationMillis)),
                exit = shrinkVertically(tween(ExpandAnimationMillis)) + fadeOut(tween(ExpandAnimationMillis)),
            ) {
                when (entry) {
                    is SettingsHomeEntry.Page -> Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = FlorisSpacing.Medium),
                    ) {
                        CompositionLocalProvider(LocalFlorisScreenEmbedded provides true) {
                            entry.page()
                        }
                    }
                    is SettingsHomeEntry.Group -> Column(modifier = Modifier.padding(bottom = FlorisSpacing.Small)) {
                        entry.links.forEach { link -> SettingsHomeLinkRow(link) }
                    }
                    is SettingsHomeEntry.Link -> Unit
                }
            }
        }
    }
}

@Composable
private fun SettingsHomeRow(
    entry: SettingsHomeEntry,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val expandable = entry !is SettingsHomeEntry.Link
    val stateText = stringRes(if (expanded) R.string.settings__home__expanded else R.string.settings__home__collapsed)
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(expanded) {
        if (expanded) {
            // Wait for a row above to finish closing, then keep this one on screen.
            delay(ExpandAnimationMillis.toLong() + 40L)
            requester.bringIntoView()
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(requester)
            .testTag(settingsHomeRowTag(entry.id))
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                if (expandable) stateDescription = stateText
            }
            .padding(horizontal = FlorisSpacing.Small, vertical = FlorisSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier.size(HomeRowIconSize),
            imageVector = entry.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(FlorisSpacing.Large))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = entry.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = when {
                !expandable -> Icons.AutoMirrored.Filled.KeyboardArrowRight
                expanded -> Icons.Default.ExpandLess
                else -> Icons.Default.ExpandMore
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsHomeLinkRow(link: SettingsHomeLink) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = link.onClick)
            // The start lines the link up with the row titles above it: the row's padding, icon and gap.
            .padding(start = FlorisSpacing.Small + HomeRowIconSize + FlorisSpacing.Large, end = FlorisSpacing.Small, top = FlorisSpacing.Medium, bottom = FlorisSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = link.title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsHomeSearchField(onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = FlorisSpacing.XSmall)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = FlorisSurfaceTokens.HairlineAlpha), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { }
            .padding(horizontal = FlorisSpacing.Medium, vertical = FlorisSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier.size(HomeRowIconSize),
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(FlorisSpacing.Medium))
        Text(
            text = stringRes(R.string.settings__search__placeholder),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsHomeSetupCard(
    tone: Color,
    icon: ImageVector,
    title: String,
    summary: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = FlorisSpacing.XSmall)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, tone.copy(alpha = 0.4f), shape)
            .padding(FlorisSpacing.Large),
        verticalArrangement = Arrangement.spacedBy(FlorisSpacing.Medium),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                modifier = Modifier.size(HomeRowIconSize),
                imageVector = icon,
                contentDescription = null,
                tint = tone,
            )
            Spacer(modifier = Modifier.width(FlorisSpacing.Medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        FlorisButton(
            modifier = Modifier.fillMaxWidth(),
            icon = icon,
            text = actionLabel,
            onClick = onAction,
            maxLines = 2,
        )
    }
}

@Composable
private fun SettingsHomeActiveBadge() {
    Row(
        modifier = Modifier.padding(end = FlorisSpacing.Large),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FlorisSpacing.Small),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Text(
            text = stringRes(R.string.settings__home__active),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
