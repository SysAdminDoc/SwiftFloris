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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.ext.ExtensionListScreenType
import dev.patrickgold.florisboard.app.settings.dictionary.UserDictionaryType
import dev.patrickgold.florisboard.app.settings.localization.LanguagePackManagerScreenAction
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.material.ui.JetPrefListItem
import org.florisboard.lib.compose.florisScrollbar
import org.florisboard.lib.compose.stringRes

@Composable
fun SettingsSearchScreen() = FlorisScreen {
    title = stringRes(R.string.settings__search__title)
    scrollable = false
    previewFieldVisible = true

    val navController = LocalNavController.current
    val resources = LocalResources.current
    val resolveString = remember(resources) {
        { resId: Int -> resources.getString(resId) }
    }

    content {
        var searchQuery by rememberSaveable { mutableStateOf("") }
        val results = remember(searchQuery, resources) {
            SettingsSearchIndex.search(searchQuery, resolveString)
        }
        val state = rememberLazyListState()

        Column(modifier = Modifier.fillMaxSize()) {
            TextField(
                modifier = Modifier.fillMaxWidth(),
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringRes(R.string.settings__search__placeholder)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                    )
                },
                singleLine = true,
                shape = RectangleShape,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
            )
            when {
                searchQuery.isBlank() -> {
                    Text(
                        modifier = Modifier
                            .padding(16.dp)
                            .align(Alignment.CenterHorizontally),
                        text = stringRes(R.string.settings__search__empty_query),
                        color = LocalContentColor.current.copy(alpha = 0.54f),
                    )
                }
                results.isEmpty() -> {
                    Text(
                        modifier = Modifier
                            .padding(16.dp)
                            .align(Alignment.CenterHorizontally),
                        text = stringRes(R.string.settings__search__no_results, "search_term" to searchQuery),
                        color = LocalContentColor.current.copy(alpha = 0.54f),
                    )
                }
            }
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .florisScrollbar(state, isVertical = true),
                state = state,
            ) {
                items(results, key = { it.entry.id }) { result ->
                    val entry = result.entry
                    val screenTitle = resolveString(entry.screenTitleResId)
                    val summary = entry.summaryResId?.let(resolveString)
                    JetPrefListItem(
                        modifier = Modifier.clickable {
                            SettingsSearchHighlightStore.mark(entry, searchQuery, resolveString)
                            navController.navigateSearchDestination(entry.destination)
                        },
                        text = resolveString(entry.titleResId),
                        secondaryText = if (summary.isNullOrBlank()) {
                            screenTitle
                        } else {
                            stringRes(
                                R.string.settings__search__result_summary,
                                "screen_title" to screenTitle,
                                "summary" to summary,
                            )
                        },
                    )
                }
            }
        }
    }
}

private fun androidx.navigation.NavController.navigateSearchDestination(destination: SettingsSearchDestination) {
    when (destination) {
        SettingsSearchDestination.HOME -> navigate(Routes.Settings.Home)
        SettingsSearchDestination.LOCALIZATION -> navigate(Routes.Settings.Localization)
        SettingsSearchDestination.SELECT_LOCALE -> navigate(Routes.Settings.SelectLocale)
        SettingsSearchDestination.LANGUAGE_PACK_MANAGER ->
            navigate(Routes.Settings.LanguagePackManager(LanguagePackManagerScreenAction.MANAGE))
        SettingsSearchDestination.SUBTYPE_ADD -> navigate(Routes.Settings.SubtypeAdd)
        SettingsSearchDestination.THEME -> navigate(Routes.Settings.Theme)
        SettingsSearchDestination.THEME_MANAGER ->
            navigate(Routes.Ext.List(ExtensionListScreenType.EXT_THEME, showUpdate = true))
        SettingsSearchDestination.KEYBOARD -> navigate(Routes.Settings.Keyboard)
        SettingsSearchDestination.INPUT_FEEDBACK -> navigate(Routes.Settings.InputFeedback)
        SettingsSearchDestination.SMARTBAR -> navigate(Routes.Settings.Smartbar)
        SettingsSearchDestination.TYPING -> navigate(Routes.Settings.Typing)
        SettingsSearchDestination.TYPING_STATS -> navigate(Routes.Settings.TypingStats)
        SettingsSearchDestination.VOICE_INPUT -> navigate(Routes.Settings.VoiceInput)
        SettingsSearchDestination.DICTIONARY -> navigate(Routes.Settings.Dictionary)
        SettingsSearchDestination.USER_DICTIONARY_SYSTEM ->
            navigate(Routes.Settings.UserDictionary(UserDictionaryType.SYSTEM))
        SettingsSearchDestination.USER_DICTIONARY_FLORIS ->
            navigate(Routes.Settings.UserDictionary(UserDictionaryType.FLORIS))
        SettingsSearchDestination.SYNC -> navigate(Routes.Settings.Sync)
        SettingsSearchDestination.MCP -> navigate(Routes.Settings.Mcp)
        SettingsSearchDestination.ADDONS -> navigate(Routes.Settings.Addons)
        SettingsSearchDestination.EXTENSIONS -> navigate(Routes.Ext.Home)
        SettingsSearchDestination.GESTURES -> navigate(Routes.Settings.Gestures)
        SettingsSearchDestination.CLIPBOARD -> navigate(Routes.Settings.Clipboard)
        SettingsSearchDestination.MEDIA -> navigate(Routes.Settings.Media)
        SettingsSearchDestination.OTHER -> navigate(Routes.Settings.Other)
        SettingsSearchDestination.PHYSICAL_KEYBOARD -> navigate(Routes.Settings.PhysicalKeyboard)
        SettingsSearchDestination.BACKUP -> navigate(Routes.Settings.Backup)
        SettingsSearchDestination.RESTORE -> navigate(Routes.Settings.Restore)
        SettingsSearchDestination.PRIVACY_AUDIT -> navigate(Routes.Settings.PrivacyAuditLog)
        SettingsSearchDestination.ABOUT -> navigate(Routes.Settings.About)
        SettingsSearchDestination.AI_FEATURES -> navigate(Routes.Settings.AiFeatures)
        SettingsSearchDestination.PROJECT_LICENSE -> navigate(Routes.Settings.ProjectLicense)
        SettingsSearchDestination.THIRD_PARTY_LICENSES -> navigate(Routes.Settings.ThirdPartyLicenses)
        SettingsSearchDestination.DEVTOOLS -> navigate(Routes.Devtools.Home)
    }
}
