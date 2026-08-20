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

package dev.patrickgold.florisboard.app.settings.dictionary

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.app.settings.search.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.florisboard.app.settings.search.SwitchPreference
import org.florisboard.lib.compose.FlorisInfoCard
import org.florisboard.lib.compose.stringRes

@Composable
fun DictionaryScreen() = FlorisScreen {
    title = stringRes(R.string.settings__dictionary__title)
    previewFieldVisible = true

    val navController = LocalNavController.current

    content {
        FlorisInfoCard(
            modifier = Modifier.padding(8.dp),
            text = stringRes(R.string.settings__dictionary__local_import_title),
            secondaryText = stringRes(R.string.settings__dictionary__local_import_summary),
            actionLabel = stringRes(R.string.action__import_file),
            onClick = {
                navController.navigate(
                    Routes.Settings.UserDictionary(
                        type = UserDictionaryType.FLORIS,
                        action = UserDictionaryScreenAction.IMPORT,
                    ),
                )
            },
        )

        PreferenceGroup(title = stringRes(R.string.pref__dictionary__group_system__label)) {
            SwitchPreference(
                prefs.dictionary.enableSystemUserDictionary,
                title = stringRes(R.string.pref__dictionary__enable_system_user_dictionary__label),
                summary = stringRes(R.string.pref__dictionary__enable_system_user_dictionary__summary),
            )
            Preference(
                title = stringRes(R.string.pref__dictionary__manage_system_user_dictionary__label),
                summary = stringRes(R.string.pref__dictionary__manage_system_user_dictionary__summary),
                onClick = { navController.navigate(Routes.Settings.UserDictionary(UserDictionaryType.SYSTEM)) },
                enabledIf = { prefs.dictionary.enableSystemUserDictionary isEqualTo true },
            )
        }

        PreferenceGroup(title = stringRes(R.string.pref__dictionary__group_internal__label)) {
            SwitchPreference(
                prefs.dictionary.enableFlorisUserDictionary,
                title = stringRes(R.string.pref__dictionary__enable_internal_user_dictionary__label),
                summary = stringRes(R.string.pref__dictionary__enable_internal_user_dictionary__summary),
            )
            Preference(
                title = stringRes(R.string.pref__dictionary__manage_floris_user_dictionary__label),
                summary = stringRes(R.string.pref__dictionary__manage_floris_user_dictionary__summary),
                onClick = { navController.navigate(Routes.Settings.UserDictionary(UserDictionaryType.FLORIS)) },
                enabledIf = { prefs.dictionary.enableFlorisUserDictionary isEqualTo true },
            )
            Preference(
                title = stringRes(R.string.pref__dictionary__manage_learned_entries__label),
                summary = stringRes(R.string.pref__dictionary__manage_learned_entries__summary),
                onClick = { navController.navigate(Routes.Settings.LearnedEntries) },
            )
            SwitchPreference(
                prefs.dictionary.previewPersonalDictionaryImports,
                title = stringRes(R.string.pref__dictionary__preview_imports__label),
                summary = stringRes(R.string.pref__dictionary__preview_imports__summary),
            )
        }
    }
}
