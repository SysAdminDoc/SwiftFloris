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

package dev.patrickgold.florisboard.app.settings.localization

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.core.PerAppSubtypeMemory
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.subtypeManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

@Composable
fun PerAppLanguageScreen() = FlorisScreen {
    title = stringRes(R.string.settings__per_app_language__title)
    previewFieldVisible = true
    iconSpaceReserved = false

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs by FlorisPreferenceStore
    val subtypeManager by context.subtypeManager()
    val subtypes by subtypeManager.subtypesFlow.collectAsState()
    val rawMemory by prefs.localization.perAppSubtypeMemory.collectAsState()
    val availableSubtypeIds = remember(subtypes) { subtypes.map { it.id }.toSet() }
    val rememberedAppCount = remember(rawMemory, availableSubtypeIds) {
        PerAppSubtypeMemory.count(rawMemory, availableSubtypeIds)
    }

    content {
        PreferenceGroup(title = stringRes(R.string.settings__per_app_language__group_memory)) {
            SwitchPreference(
                prefs.localization.rememberSubtypePerAppEnabled,
                title = stringRes(R.string.settings__per_app_language__enabled),
                summary = stringRes(R.string.settings__per_app_language__enabled_summary),
            )
            Preference(
                icon = Icons.Default.Delete,
                title = stringRes(R.string.settings__per_app_language__clear),
                summary = stringRes(
                    R.string.settings__per_app_language__clear_summary,
                    "count" to rememberedAppCount.toString(),
                ),
                enabledIf = { rememberedAppCount > 0 },
                onClick = {
                    coroutineScope.launch {
                        prefs.localization.perAppSubtypeMemory.set(PerAppSubtypeMemory.EmptyJson)
                    }
                },
            )
        }

        PreferenceGroup(title = stringRes(R.string.settings__per_app_language__group_behavior)) {
            Preference(
                icon = Icons.Default.Language,
                title = stringRes(R.string.settings__per_app_language__privacy_title),
                summary = stringRes(R.string.settings__per_app_language__privacy_summary),
            )
        }
    }
}
