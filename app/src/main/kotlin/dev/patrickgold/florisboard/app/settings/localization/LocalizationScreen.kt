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
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.enumDisplayEntriesOf
import dev.patrickgold.florisboard.ime.core.DisplayLanguageNamesIn
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.keyboard.LayoutType
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.subtypeManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.ListPreference
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import dev.patrickgold.jetpref.material.ui.JetPrefListItem
import kotlinx.serialization.json.Json
import org.florisboard.lib.compose.FlorisWarningCard
import org.florisboard.lib.compose.stringRes

internal val SubtypeSaver = Saver<MutableState<Subtype?>, String>(
    save = {
        Json.encodeToString<Subtype?>(it.value)
    },
    restore = {
        mutableStateOf(Json.decodeFromString(it))
    },
)

@Composable
fun LocalizationScreen() = FlorisScreen {
    title = stringRes(R.string.settings__localization__title)
    previewFieldVisible = true
    iconSpaceReserved = false

    val navController = LocalNavController.current
    val context = LocalContext.current
    val prefs by FlorisPreferenceStore
    val keyboardManager by context.keyboardManager()
    val subtypeManager by context.subtypeManager()
    var chosenSubtypeToDelete: Subtype? by rememberSaveable(saver = SubtypeSaver) { mutableStateOf(null) }
    val displayLanguageNamesIn by prefs.localization.displayLanguageNamesIn.collectAsState()

    floatingActionButton {
        ExtendedFloatingActionButton(
            icon = {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringRes(R.string.settings__localization__subtype_add_title),
                )
            },
            text = {
                Text(
                    text = stringRes(R.string.settings__localization__subtype_add_title),
                )
            },
            shape = MaterialTheme.shapes.medium,
            onClick = { navController.navigate(Routes.Settings.SubtypeAdd) },
        )
    }

    content {
        PreferenceGroup(title = stringRes(R.string.settings__localization__group_display__label)) {
            ListPreference(
                prefs.localization.displayLanguageNamesIn,
                title = stringRes(R.string.settings__localization__display_language_names_in__label),
                entries = enumDisplayEntriesOf(DisplayLanguageNamesIn::class),
            )
            SwitchPreference(
                prefs.localization.displayKeyboardLabelsInSubtypeLanguage,
                title = stringRes(R.string.settings__localization__display_keyboard_labels_in_subtype_language),
            )
        }

        PreferenceGroup(title = stringRes(R.string.settings__localization__group_language_packs__label)) {
            Preference(
                title = stringRes(R.string.settings__localization__language_pack_title),
                summary = stringRes(R.string.settings__localization__language_pack_summary),
                onClick = {
                    navController.navigate(Routes.Settings.LanguagePackManager(LanguagePackManagerScreenAction.MANAGE))
                },
            )
        }

        PreferenceGroup(title = stringRes(R.string.settings__localization__group_subtypes__label)) {
            val subtypes by subtypeManager.subtypesFlow.collectAsState()
            if (subtypes.isEmpty()) {
                FlorisWarningCard(
                    modifier = Modifier.padding(all = 8.dp),
                    text = stringRes(R.string.settings__localization__subtype_no_subtypes_configured_warning),
                )
            } else {
                val currencySets by keyboardManager.resources.currencySets.collectAsState()
                val layouts by keyboardManager.resources.layouts.collectAsState()
                for (subtype in subtypes) {
                    val cMeta = layouts[LayoutType.CHARACTERS]?.get(subtype.layoutMap.characters)
                    val sMeta = layouts[LayoutType.SYMBOLS]?.get(subtype.layoutMap.symbols)
                    val currMeta = currencySets[subtype.currencySet]
                    val summary = stringRes(
                        id = R.string.settings__localization__subtype_summary,
                        "characters_name" to (cMeta?.label ?: stringRes(
                            id = R.string.settings__localization__subtype_component_not_installed,
                            "component_id" to subtype.layoutMap.characters.toString(),
                        )),
                        "symbols_name" to (sMeta?.label ?: stringRes(
                            id = R.string.settings__localization__subtype_component_not_installed,
                            "component_id" to subtype.layoutMap.symbols.toString(),
                        )),
                        "currency_set_name" to (currMeta?.label ?: stringRes(
                            id = R.string.settings__localization__subtype_component_not_installed,
                            "component_id" to subtype.currencySet.toString(),
                        )),
                    )
                    JetPrefListItem(
                        modifier = Modifier.clickable {
                            navController.navigate(
                                Routes.Settings.SubtypeEdit(subtype.id)
                            )
                        },
                        text = subtype.displayName(displayLanguageNamesIn),
                        secondaryText = summary,
                        trailing = {
                            IconButton(
                                onClick = {
                                    chosenSubtypeToDelete = subtype
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringRes(
                                        R.string.settings__localization__subtype_delete_action,
                                    ),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    DeleteSubtypeConfirmationDialog(
        subtypeToDelete = chosenSubtypeToDelete,
        displayLanguageNamesIn = displayLanguageNamesIn,
        onDismiss = {
            chosenSubtypeToDelete = null
        },
        onConfirm = {
            chosenSubtypeToDelete?.let { subtypeManager.removeSubtype(subtypeToRemove = it) }
            chosenSubtypeToDelete = null
        },
    )

}

@Composable
fun DeleteSubtypeConfirmationDialog(
    subtypeToDelete: Subtype?,
    displayLanguageNamesIn: DisplayLanguageNamesIn = DisplayLanguageNamesIn.SYSTEM_LOCALE,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    subtypeToDelete?.let { subtype ->
        JetPrefAlertDialog(
            title = stringRes(R.string.settings__localization__subtype_delete_confirmation_title),
            confirmLabel = stringRes(R.string.action__delete),
            dismissLabel = stringRes(R.string.action__cancel),
            onDismiss = onDismiss,
            onConfirm = onConfirm,
        ) {
            Text(
                stringRes(
                    R.string.settings__localization__subtype_delete_confirmation_warning,
                    "subtype_name" to subtype.displayName(displayLanguageNamesIn),
                )
            )
        }
    }
}
