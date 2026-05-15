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

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.SmartButton
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.util.InputMethodUtils
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import org.florisboard.lib.compose.FlorisErrorCard
import org.florisboard.lib.compose.FlorisInfoCard
import org.florisboard.lib.compose.FlorisWarningCard
import org.florisboard.lib.compose.stringRes

@Composable
fun HomeScreen() = FlorisScreen {
    title = stringRes(R.string.settings__home__title)
    navigationIconVisible = false
    previewFieldVisible = true

    val navController = LocalNavController.current
    val context = LocalContext.current

    content {
        val isFlorisBoardEnabled by InputMethodUtils.observeIsFlorisboardEnabled(foregroundOnly = true)
        val isFlorisBoardSelected by InputMethodUtils.observeIsFlorisboardSelected(foregroundOnly = true)
        if (!isFlorisBoardEnabled) {
            FlorisErrorCard(
                modifier = Modifier.padding(8.dp),
                text = stringRes(R.string.settings__home__ime_not_enabled),
                secondaryText = stringRes(R.string.settings__home__ime_not_enabled_summary),
                actionLabel = stringRes(R.string.settings__home__open_keyboard_settings),
                onClick = { InputMethodUtils.showImeEnablerActivity(context) },
            )
        } else if (!isFlorisBoardSelected) {
            FlorisWarningCard(
                modifier = Modifier.padding(8.dp),
                text = stringRes(R.string.settings__home__ime_not_selected),
                secondaryText = stringRes(R.string.settings__home__ime_not_selected_summary),
                actionLabel = stringRes(R.string.settings__home__choose_keyboard),
                onClick = { InputMethodUtils.showImePicker(context) },
            )
        } else {
            FlorisInfoCard(
                modifier = Modifier.padding(8.dp),
                text = stringRes(R.string.settings__home__ime_ready),
                secondaryText = stringRes(R.string.settings__home__ime_ready_summary),
            )
        }

        PreferenceGroup(title = stringRes(R.string.settings__home__section_essentials)) {
            Preference(
                icon = Icons.Default.Language,
                title = stringRes(R.string.settings__localization__title),
                summary = stringRes(R.string.settings__home__localization_summary),
                onClick = { navController.navigate(Routes.Settings.Localization) },
            )
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
                icon = Icons.Outlined.Palette,
                title = stringRes(R.string.settings__theme__title),
                summary = stringRes(R.string.settings__home__theme_summary),
                onClick = { navController.navigate(Routes.Settings.Theme) },
            )
        }

        PreferenceGroup(title = stringRes(R.string.settings__home__section_experience)) {
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
            Preference(
                icon = Icons.AutoMirrored.Outlined.Assignment,
                title = stringRes(R.string.settings__clipboard__title),
                summary = stringRes(R.string.settings__home__clipboard_summary),
                onClick = { navController.navigate(Routes.Settings.Clipboard) },
            )
            Preference(
                icon = Icons.Default.SentimentSatisfiedAlt,
                title = stringRes(R.string.settings__media__title),
                summary = stringRes(R.string.settings__home__media_summary),
                onClick = { navController.navigate(Routes.Settings.Media) },
            )
        }

        PreferenceGroup(title = stringRes(R.string.settings__home__section_data)) {
            Preference(
                icon = Icons.AutoMirrored.Filled.LibraryBooks,
                title = stringRes(R.string.settings__dictionary__title),
                summary = stringRes(R.string.settings__home__dictionary_summary),
                onClick = { navController.navigate(Routes.Settings.Dictionary) },
            )
            Preference(
                icon = Icons.Default.Extension,
                title = stringRes(R.string.ext__home__title),
                summary = stringRes(R.string.settings__home__extensions_summary),
                onClick = { navController.navigate(Routes.Ext.Home) },
            )
            Preference(
                icon = Icons.Default.Sync,
                title = stringRes(R.string.settings__sync__title),
                summary = stringRes(R.string.settings__home__sync_summary),
                onClick = { navController.navigate(Routes.Settings.Sync) },
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

        PreferenceGroup(title = stringRes(R.string.settings__home__section_system)) {
            Preference(
                icon = Icons.Outlined.Build,
                title = stringRes(R.string.settings__other__title),
                summary = stringRes(R.string.settings__home__other_summary),
                onClick = { navController.navigate(Routes.Settings.Other) },
            )
            Preference(
                icon = Icons.Outlined.Info,
                title = stringRes(R.string.about__title),
                summary = stringRes(R.string.settings__home__about_summary),
                onClick = { navController.navigate(Routes.Settings.About) },
            )
        }
    }
}
