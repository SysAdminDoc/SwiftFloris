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

package dev.patrickgold.florisboard.app.settings.localization

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.ime.core.DisplayLanguageNamesIn
import dev.patrickgold.florisboard.lib.FlorisLocale
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.florisboard.app.settings.search.SettingsListItem
import kotlinx.coroutines.delay
import org.florisboard.lib.compose.florisScrollbar
import org.florisboard.lib.compose.pluralsRes
import org.florisboard.lib.compose.stringRes

const val SelectLocaleScreenResultLanguageTag = "SelectLocaleScreen.languageTag"

@Composable
fun SelectLocaleScreen() = FlorisScreen {
    title = stringRes(R.string.settings__localization__subtype_select_locale)
    scrollable = false

    val prefs by FlorisPreferenceStore
    val navController = LocalNavController.current

    val displayLanguageNamesIn by prefs.localization.displayLanguageNamesIn.collectAsState()
    var searchTermValue by remember { mutableStateOf(TextFieldValue()) }
    val context = LocalContext.current
    val systemLocales = remember(context, displayLanguageNamesIn) {
        FlorisLocale.extendedAvailableLocales(context).sortedBy { locale ->
            when (displayLanguageNamesIn) {
                DisplayLanguageNamesIn.SYSTEM_LOCALE -> locale.displayName()
                DisplayLanguageNamesIn.NATIVE_LOCALE -> locale.displayName(locale)
            }.lowercase()
        }
    }
    val searchIndex = remember(systemLocales) { localeSearchIndex(systemLocales) }

    val filteredSystemLocales = remember(searchIndex, searchTermValue.text) {
        searchIndex.filter(searchTermValue.text)
    }
    // The list follows every keystroke, but the result line waits for typing to pause, so TalkBack reads one
    // count per search instead of one per letter.
    var settledQuery by remember { mutableStateOf("") }
    LaunchedEffect(searchTermValue.text) {
        delay(LocaleSearchSettleMillis)
        settledQuery = searchTermValue.text
    }
    val settledResultCount = remember(searchIndex, settledQuery) { searchIndex.filter(settledQuery).size }

    content {
        val state = rememberLazyListState()
        Column(modifier = Modifier.fillMaxSize()) {
            TextField(
                modifier = Modifier.fillMaxWidth(),
                value = searchTermValue,
                onValueChange = { searchTermValue = it },
                placeholder = { Text(stringRes(R.string.settings__localization__subtype_search_locale_placeholder)) },
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
            if (settledQuery.isNotBlank()) {
                Text(
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.CenterHorizontally)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    text = if (settledResultCount == 0) {
                        stringRes(
                            R.string.settings__localization__subtype_search_locale_not_found,
                            "search_term" to settledQuery.trim(),
                        )
                    } else {
                        pluralsRes(
                            R.plurals.settings__localization__subtype_search_locale_results,
                            settledResultCount,
                            "count" to settledResultCount,
                        )
                    },
                    color = LocalContentColor.current.copy(alpha = 0.54f),
                )
            }
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .florisScrollbar(state, isVertical = true),
                state = state,
            ) {
                items(filteredSystemLocales) { systemLocale ->
                    SettingsListItem(
                        modifier = Modifier.clickable(role = Role.Button) {
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set(SelectLocaleScreenResultLanguageTag, systemLocale.languageTag())
                            navController.popBackStack()
                        },
                        text = when (displayLanguageNamesIn) {
                            DisplayLanguageNamesIn.SYSTEM_LOCALE -> systemLocale.displayName()
                            DisplayLanguageNamesIn.NATIVE_LOCALE -> systemLocale.displayName(systemLocale)
                        },
                    )
                }
            }
        }
    }
}
