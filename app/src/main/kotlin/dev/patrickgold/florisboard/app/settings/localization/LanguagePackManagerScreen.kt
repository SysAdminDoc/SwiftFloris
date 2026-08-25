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

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.ext.ExtensionImportScreenType
import dev.patrickgold.florisboard.extensionManager
import dev.patrickgold.florisboard.ime.nlp.LanguagePackKind
import dev.patrickgold.florisboard.lib.compose.FlorisConfirmDeleteDialog
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.ext.Extension
import dev.patrickgold.florisboard.subtypeManager
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import dev.patrickgold.florisboard.app.settings.search.Preference
import dev.patrickgold.jetpref.material.ui.JetPrefListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.android.showLongToast
import org.florisboard.lib.compose.FlorisEmptyState
import org.florisboard.lib.compose.FlorisErrorCard
import org.florisboard.lib.compose.FlorisOutlinedBox
import org.florisboard.lib.compose.FlorisProgressCard
import org.florisboard.lib.compose.FlorisSuccessCard
import org.florisboard.lib.compose.FlorisTextButton
import org.florisboard.lib.compose.defaultFlorisOutlinedBox
import org.florisboard.lib.compose.rippleClickable
import org.florisboard.lib.compose.pluralsRes
import org.florisboard.lib.compose.stringRes
import dev.patrickgold.florisboard.lib.util.summarizeForUser

enum class LanguagePackManagerScreenAction(val id: String) {
    MANAGE("manage-installed-language-packs");
}

@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun LanguagePackManagerScreen(action: LanguagePackManagerScreenAction?) = FlorisScreen {
    title = stringRes(when (action) {
        LanguagePackManagerScreenAction.MANAGE -> R.string.settings__localization__language_pack_title
        else -> error("LanguagePack manager screen action must not be null")
    })

    val navController = LocalNavController.current
    val context = LocalContext.current
    val detailsUnavailable = stringRes(R.string.error__details_unavailable)
    val scope = rememberCoroutineScope()
    val extensionManager by context.extensionManager()
    val subtypeManager by context.subtypeManager()

    val indexedLanguagePackExtensions by extensionManager.languagePacks.collectAsState()
    val subtypes by subtypeManager.subtypesFlow.collectAsState()
    val activeLocaleTags = remember(subtypes) {
        subtypes.flatMap { subtype ->
            subtype.locales().map { it.localeTag() }
        }.toSet()
    }
    val languagePackCatalog = remember(indexedLanguagePackExtensions, activeLocaleTags) {
        LanguagePackManagerPolicy.catalogEntries(
            extensions = indexedLanguagePackExtensions,
            activeLocaleTags = activeLocaleTags,
        )
    }

    var languagePackExtToDelete by remember { mutableStateOf<Extension?>(null) }
    var isDeleteInProgress by remember { mutableStateOf(false) }
    var lastNotice by remember { mutableStateOf<LanguagePackManagerNotice?>(null) }
    var lastErrorMessage by remember { mutableStateOf<String?>(null) }

    content {
        when (LanguagePackManagerPolicy.resolveNotice(
            isDeleteInProgress = isDeleteInProgress,
            lastTerminalNotice = lastNotice,
        )) {
            LanguagePackManagerNotice.DeleteInProgress -> FlorisProgressCard(
                modifier = Modifier.defaultFlorisOutlinedBox(),
                text = stringRes(R.string.settings__localization__language_pack_delete_in_progress),
                secondaryText = stringRes(R.string.settings__localization__language_pack_delete_in_progress_summary),
            )
            LanguagePackManagerNotice.DeleteSuccess -> FlorisSuccessCard(
                modifier = Modifier.defaultFlorisOutlinedBox(),
                text = stringRes(R.string.settings__localization__language_pack_delete_success),
                secondaryText = stringRes(R.string.settings__localization__language_pack_delete_success_summary),
            )
            LanguagePackManagerNotice.DeleteFailure -> FlorisErrorCard(
                modifier = Modifier.defaultFlorisOutlinedBox(),
                text = stringRes(R.string.settings__localization__language_pack_delete_failure),
                secondaryText = stringRes(
                    R.string.settings__localization__language_pack_delete_failure_summary,
                    "error_message" to (lastErrorMessage ?: stringRes(R.string.ext__import__error_details_unavailable)),
                ),
            )
            LanguagePackManagerNotice.None -> Unit
        }
        if (action == LanguagePackManagerScreenAction.MANAGE) {
            FlorisOutlinedBox(
                modifier = Modifier.defaultFlorisOutlinedBox(),
            ) {
                // Add a keyboard language first, and unconditionally. Two
                // language packs ship in assets, so the catalog is never empty
                // on a real device and an affordance shown only in the empty
                // state is an affordance nobody sees. Discussion #21 is somebody
                // hunting for Portuguese on this screen while pt.fldic was
                // already bundled, which is the thing this row answers.
                Preference(
                    onClick = { navController.navigate(Routes.Settings.SubtypeAdd) },
                    icon = Icons.Default.Add,
                    title = stringRes(R.string.settings__localization__subtype_add_title),
                    summary = stringRes(R.string.settings__localization__subtype_add_summary),
                )
                Preference(
                    onClick = {
                        if (LanguagePackManagerPolicy.canTriggerImport(isDeleteInProgress)) {
                            navController.navigate(Routes.Ext.Import(ExtensionImportScreenType.EXT_LANGUAGEPACK, null))
                        }
                    },
                    icon = Icons.AutoMirrored.Filled.Input,
                    title = stringRes(R.string.action__import),
                )
            }
        }
        if (languagePackCatalog.isEmpty()) {
            // Import already has its own row above this state, so the empty
            // state points at what people are usually actually after when they
            // land here: adding a language they can type in. Discussion #21 was
            // someone hunting for Portuguese on this screen while pt.fldic was
            // already bundled.
            FlorisEmptyState(
                modifier = Modifier.padding(16.dp),
                icon = Icons.Default.Language,
                title = stringRes(R.string.settings__localization__language_pack_empty_title),
                message = stringRes(R.string.settings__localization__language_pack_empty_message),
            )
        }
        for (entry in languagePackCatalog) key(entry.extensionId) {
            // The catalog is derived from a remembered snapshot while this
            // lookup reads live manager state, so a delete that lands between
            // the two leaves an entry with no extension. Skip it and let the
            // next recomposition drop the row, rather than crashing Settings.
            // A null check rather than an early return: `key` is inline, and a
            // non-local return out of it cannot be represented in dex.
            val ext = extensionManager.getExtensionById(entry.extensionId)
            if (ext != null) {
                FlorisOutlinedBox(
                    modifier = Modifier.defaultFlorisOutlinedBox(),
                    title = entry.title,
                    onTitleClick = { navController.navigate(Routes.Ext.View(entry.extensionId)) },
                    subtitle = languagePackEntrySummary(entry),
                    onSubtitleClick = { navController.navigate(Routes.Ext.View(entry.extensionId)) },
                ) {
                    Column(
                        // Allowing horizontal scroll to fit translations in descriptions.
                        Modifier
                            .horizontalScroll(rememberScrollState())
                            .width(intrinsicSize = IntrinsicSize.Max),
                    ) {
                        for (component in entry.components) key(entry.extensionId, component.id) {
                            JetPrefListItem(
                                modifier = Modifier.rippleClickable {
                                    navController.navigate(Routes.Ext.View(entry.extensionId))
                                },
                                text = component.label,
                                secondaryText = languagePackComponentSummary(component),
                            )
                        }
                    }
                    val canDelete = LanguagePackManagerPolicy.canDelete(
                        extensionCanBeDeleted = extensionManager.canDelete(ext),
                        isDeleteInProgress = isDeleteInProgress,
                    )
                    if (action == LanguagePackManagerScreenAction.MANAGE && extensionManager.canDelete(ext)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp),
                        ) {
                            FlorisTextButton(
                                onClick = {
                                    languagePackExtToDelete = ext
                                },
                                icon = Icons.Default.Delete,
                                text = stringRes(R.string.action__delete),
                                enabled = canDelete,
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                            )
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        if (languagePackExtToDelete != null) {
            FlorisConfirmDeleteDialog(
                onConfirm = {
                    languagePackExtToDelete?.let { extToDelete ->
                        languagePackExtToDelete = null
                        scope.launch {
                            if (isDeleteInProgress) return@launch
                            isDeleteInProgress = true
                            lastNotice = null
                            lastErrorMessage = null
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    extensionManager.delete(extToDelete)
                                }
                            }.onSuccess {
                                lastNotice = LanguagePackManagerNotice.DeleteSuccess
                                context.showLongToast(R.string.settings__localization__language_pack_delete_success)
                            }.onFailure { error ->
                                val errorMessage = error.summarizeForUser(detailsUnavailable)
                                lastNotice = LanguagePackManagerNotice.DeleteFailure
                                lastErrorMessage = errorMessage
                                context.showLongToast(
                                    R.string.error__snackbar_message_template,
                                    "error_message" to errorMessage,
                                )
                            }
                            isDeleteInProgress = false
                        }
                    }
                },
                onDismiss = { languagePackExtToDelete = null },
                what = languagePackExtToDelete!!.meta.title,
            )
        }
    }
}

@Composable
private fun languagePackEntrySummary(entry: LanguagePackCatalogEntry): String {
    val kind = when (entry.kind) {
        LanguagePackKind.HAN_SHAPE_BASED -> stringRes(R.string.settings__localization__language_pack_kind_han)
        LanguagePackKind.GENERIC -> stringRes(R.string.settings__localization__language_pack_kind_generic)
    }
    return when (entry.state) {
        LanguagePackRuntimeState.ActiveForSubtype -> stringRes(
            R.string.settings__localization__language_pack_extension_active_summary,
            "active" to entry.activeComponentCount.toString(),
            "total" to entry.componentCount.toString(),
            "kind" to kind,
        )
        LanguagePackRuntimeState.InstalledStandby -> stringRes(
            R.string.settings__localization__language_pack_extension_standby_summary,
            "total" to entry.componentCount.toString(),
            "kind" to kind,
        )
        LanguagePackRuntimeState.MetadataOnly -> pluralsRes(
            R.plurals.settings__localization__language_pack_extension_metadata_summary,
            entry.componentCount,
            "total" to entry.componentCount.toString(),
        )
        LanguagePackRuntimeState.DataUnavailable -> stringRes(
            R.string.settings__localization__language_pack_extension_unavailable_summary,
            "active" to entry.activeComponentCount.toString(),
            "total" to entry.componentCount.toString(),
            "kind" to kind,
        )
    }
}

@Composable
private fun languagePackComponentSummary(component: LanguagePackCatalogComponent): String {
    return if (component.isActive) {
        stringRes(
            R.string.settings__localization__language_pack_component_active_summary,
            "locale" to component.localeTag,
        )
    } else {
        stringRes(
            R.string.settings__localization__language_pack_component_standby_summary,
            "locale" to component.localeTag,
        )
    }
}
