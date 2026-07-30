/*
 * Copyright (C) 2024-2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.app.ext

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.extensionManager
import dev.patrickgold.florisboard.ime.theme.ThemeExtension
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.ext.ExtensionManager
import dev.patrickgold.florisboard.lib.ext.ExtensionQuarantineReason
import org.florisboard.lib.compose.FlorisEmptyState
import org.florisboard.lib.compose.FlorisOutlinedBox
import org.florisboard.lib.compose.FlorisTextButton
import org.florisboard.lib.compose.FlorisWarningCard
import org.florisboard.lib.compose.defaultFlorisOutlinedBox
import org.florisboard.lib.compose.florisScrollbar
import org.florisboard.lib.compose.stringRes

enum class ExtensionListScreenType(
    val id: String,
    @param:StringRes val titleResId: Int,
    val getExtensionIndex: (ExtensionManager) -> ExtensionManager.ExtensionIndex<*>,
    val launchExtensionCreate: ((NavController) -> Unit)?,
) {
    EXT_THEME(
        id = "ext-theme",
        titleResId = R.string.ext__list__ext_theme,
        getExtensionIndex = { it.themes },
        launchExtensionCreate = { it.navigate(Routes.Ext.Edit("null", ThemeExtension.SERIAL_TYPE)) },
    ),
    EXT_KEYBOARD(
        id = "ext-keyboard",
        titleResId = R.string.ext__list__ext_keyboard,
        getExtensionIndex = { it.keyboardExtensions },
        launchExtensionCreate = null,//{ it.navigate(Routes.Ext.Edit("null", KeyboardExtension.SERIAL_TYPE)) },
    ),
    EXT_LANGUAGEPACK(
        id = "ext-languagepack",
        titleResId = R.string.ext__list__ext_languagepack,
        getExtensionIndex = { it.languagePacks },
        launchExtensionCreate = null,//{ it.navigate(Routes.Ext.Edit("null", LanguagePackExtension.SERIAL_TYPE)) },
    );
}

private fun ExtensionListScreenType.importScreenType(): ExtensionImportScreenType {
    return when (this) {
        ExtensionListScreenType.EXT_THEME -> ExtensionImportScreenType.EXT_THEME
        ExtensionListScreenType.EXT_KEYBOARD -> ExtensionImportScreenType.EXT_KEYBOARD
        ExtensionListScreenType.EXT_LANGUAGEPACK -> ExtensionImportScreenType.EXT_LANGUAGEPACK
    }
}

@Composable
fun ExtensionListScreen(type: ExtensionListScreenType, showUpdate: Boolean) = FlorisScreen {
    title = stringRes(type.titleResId)
    previewFieldVisible = false
    scrollable = false

    val context = LocalContext.current
    val navController = LocalNavController.current
    val extensionManager by context.extensionManager()
    val selectedIndex = type.getExtensionIndex(extensionManager)
    val extensionIndex by selectedIndex.collectAsState()
    val quarantinedExtensions by selectedIndex.quarantined.collectAsState()

    var fabHeight by remember {
        mutableIntStateOf(0)
    }
    val fabHeightDp = with(LocalDensity.current) { fabHeight.toDp()+16.dp }
    val listState = rememberLazyListState()

    content {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .florisScrollbar(state = listState, isVertical = true),
            state = listState,
            contentPadding = PaddingValues(bottom = fabHeightDp),
        ) {
            if (showUpdate) {
                item {
                    ImportExtensionBox(navController)
                }
            }
            if (quarantinedExtensions.isNotEmpty()) {
                item {
                    val visibleDiagnostics = mutableListOf<String>()
                    for (record in quarantinedExtensions.take(5)) {
                        visibleDiagnostics += "${record.fileName}: ${
                            stringRes(record.reason.messageRes())
                        }"
                    }
                    val additionalCount = (quarantinedExtensions.size - 5).coerceAtLeast(0)
                    val additionalSummary = if (additionalCount > 0) {
                        stringRes(
                            R.string.ext__list__quarantine_additional,
                            "count" to additionalCount,
                        )
                    } else {
                        ""
                    }
                    FlorisWarningCard(
                        modifier = Modifier.defaultFlorisOutlinedBox(),
                        text = stringRes(
                            R.string.ext__list__quarantine_title,
                            "count" to quarantinedExtensions.size,
                        ),
                        secondaryText = stringRes(
                            R.string.ext__list__quarantine_summary,
                            "diagnostics" to visibleDiagnostics.joinToString("\n"),
                            "additional" to additionalSummary,
                        ),
                    )
                }
            }
            if (extensionIndex.isEmpty()) {
                item {
                    FlorisEmptyState(
                        modifier = Modifier.padding(16.dp),
                        icon = Icons.Default.Extension,
                        title = stringRes(R.string.ext__list__empty_title),
                        message = stringRes(R.string.ext__list__empty),
                        actionLabel = stringRes(R.string.action__import),
                        onAction = {
                            navController.navigate(Routes.Ext.Import(type.importScreenType(), null))
                        },
                    )
                }
            }
            items(extensionIndex) { ext ->
                FlorisOutlinedBox(
                    modifier = Modifier.defaultFlorisOutlinedBox(),
                    title = ext.meta.title,
                    subtitle = ext.meta.id,
                ) {
                    if (!ext.meta.description.isNullOrBlank()) {
                        Text(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            text = ext.meta.description!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp),
                    ) {
                        FlorisTextButton(
                            onClick = {
                                navController.navigate(Routes.Ext.View(ext.meta.id))
                            },
                            icon = Icons.Outlined.Info,
                            text = stringRes(id = R.string.ext__list__view_details),//stringRes(R.string.action__add),
                            colors = ButtonDefaults.textButtonColors(),
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        FlorisTextButton(
                            onClick = {
                                navController.navigate(Routes.Ext.Edit(ext.meta.id))
                            },
                            icon = Icons.Default.Edit,
                            text = stringRes(R.string.action__edit),
                            enabled = extensionManager.canDelete(ext),
                        )
                    }
                }
            }
        }
    }

    if (type.launchExtensionCreate != null) {
        floatingActionButton {
            ExtendedFloatingActionButton(
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringRes(id = R.string.ext__editor__title_create_any),
                    )
                },
                text = {
                    Text(
                        text = stringRes(id = R.string.ext__editor__title_create_any),
                    )
                },
                modifier = Modifier.onGloballyPositioned {
                    fabHeight = it.size.height
                },
                shape = MaterialTheme.shapes.medium,
                onClick = { type.launchExtensionCreate.invoke(navController) },
            )
        }
    }
}

@StringRes
private fun ExtensionQuarantineReason.messageRes(): Int {
    return when (this) {
        ExtensionQuarantineReason.MANIFEST_TOO_LARGE ->
            R.string.ext__list__quarantine_reason_manifest_too_large
        ExtensionQuarantineReason.MANIFEST_MALFORMED ->
            R.string.ext__list__quarantine_reason_manifest_malformed
        ExtensionQuarantineReason.INVALID_METADATA ->
            R.string.ext__list__quarantine_reason_invalid_metadata
        ExtensionQuarantineReason.TOO_MANY_COMPONENTS ->
            R.string.ext__list__quarantine_reason_too_many_components
        ExtensionQuarantineReason.INVALID_COMPONENT_ID ->
            R.string.ext__list__quarantine_reason_invalid_component_id
        ExtensionQuarantineReason.DUPLICATE_COMPONENT_ID ->
            R.string.ext__list__quarantine_reason_duplicate_component_id
        ExtensionQuarantineReason.UNKNOWN_LAYOUT_TYPE ->
            R.string.ext__list__quarantine_reason_unknown_layout_type
        ExtensionQuarantineReason.UNSAFE_COMPONENT_PATH ->
            R.string.ext__list__quarantine_reason_unsafe_component_path
        ExtensionQuarantineReason.MISSING_COMPONENT_FILE ->
            R.string.ext__list__quarantine_reason_missing_component_file
        ExtensionQuarantineReason.COMPONENT_TOO_LARGE ->
            R.string.ext__list__quarantine_reason_component_too_large
        ExtensionQuarantineReason.UNREADABLE_ARCHIVE ->
            R.string.ext__list__quarantine_reason_unreadable_archive
    }
}
