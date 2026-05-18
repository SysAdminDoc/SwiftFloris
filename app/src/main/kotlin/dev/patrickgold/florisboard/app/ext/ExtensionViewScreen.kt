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

package dev.patrickgold.florisboard.app.ext

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.settings.theme.ThemeExtensionDeleteNotice
import dev.patrickgold.florisboard.app.settings.theme.ThemeExtensionTrustStatePolicy
import dev.patrickgold.florisboard.extensionManager
import dev.patrickgold.florisboard.ime.nlp.LanguagePackExtension
import dev.patrickgold.florisboard.ime.theme.ThemeExtension
import dev.patrickgold.florisboard.ime.theme.ThemeExtensionComponentImpl
import dev.patrickgold.florisboard.lib.compose.DynamicFontScale
import dev.patrickgold.florisboard.lib.compose.FlorisConfirmDeleteDialog
import dev.patrickgold.florisboard.lib.compose.FlorisHyperlinkText
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.ext.Extension
import dev.patrickgold.florisboard.lib.ext.ExtensionMaintainer
import dev.patrickgold.florisboard.lib.ext.ExtensionMeta
import dev.patrickgold.florisboard.lib.io.FlorisRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.android.showLongToast
import org.florisboard.lib.compose.FlorisErrorCard
import org.florisboard.lib.compose.FlorisInfoCard
import org.florisboard.lib.compose.FlorisOutlinedButton
import org.florisboard.lib.compose.FlorisOutlinedBox
import org.florisboard.lib.compose.defaultFlorisOutlinedBox
import org.florisboard.lib.compose.stringRes

@Composable
fun ExtensionViewScreen(id: String) {
    val context = LocalContext.current
    val extensionManager by context.extensionManager()

    val ext = extensionManager.getExtensionById(id)
    if (ext != null) {
        ViewScreen(ext)
    } else {
        ExtensionNotFoundScreen(id)
    }
}

@Composable
private fun ViewScreen(ext: Extension) = FlorisScreen {
    title = ext.meta.title

    val navController = LocalNavController.current
    val context = LocalContext.current
    val extensionManager by context.extensionManager()
    val scope = rememberCoroutineScope()

    var extToDelete by remember { mutableStateOf<Extension?>(null) }
    var isDeleteInProgress by rememberSaveable { mutableStateOf(false) }
    var lastDeleteNotice by rememberSaveable { mutableStateOf<ThemeExtensionDeleteNotice?>(null) }
    var lastDeleteErrorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    content {
        val canDeleteExtension = extensionManager.canDelete(ext)
        when (ThemeExtensionTrustStatePolicy.resolveDeleteNotice(isDeleteInProgress, lastDeleteNotice)) {
            ThemeExtensionDeleteNotice.None -> Unit
            ThemeExtensionDeleteNotice.DeleteInProgress -> FlorisInfoCard(
                modifier = Modifier.defaultFlorisOutlinedBox(),
                text = stringRes(R.string.ext__view__delete_in_progress),
                secondaryText = stringRes(R.string.ext__view__delete_in_progress_summary),
            )
            ThemeExtensionDeleteNotice.DeleteFailure -> FlorisErrorCard(
                modifier = Modifier.defaultFlorisOutlinedBox(),
                text = stringRes(R.string.ext__view__delete_failure),
                secondaryText = stringRes(
                    R.string.ext__view__delete_failure_summary,
                    "error_message" to (lastDeleteErrorMessage ?: stringRes(
                        R.string.ext__import__error_details_unavailable,
                    )),
                ),
            )
        }
        FlorisInfoCard(
            modifier = Modifier.defaultFlorisOutlinedBox(),
            text = stringRes(R.string.ext__view__overview_title),
            secondaryText = buildString {
                append(
                    ext.meta.description
                        ?.takeIf { it.isNotBlank() }
                        ?: stringRes(R.string.ext__view__no_description),
                )
                append("\n\n")
                append(
                    stringRes(
                        if (canDeleteExtension) {
                            R.string.ext__view__user_extension_summary
                        } else {
                            R.string.ext__view__core_extension_summary
                        },
                    ),
                )
            },
            showIcon = false,
        )
        FlorisOutlinedBox(
            modifier = Modifier.defaultFlorisOutlinedBox(),
            title = stringRes(R.string.ext__view__metadata_title),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                ExtensionMetaRowScrollableChips(
                    label = stringRes(R.string.ext__meta__maintainers),
                    showDividerAbove = false,
                ) {
                    for ((n, maintainer) in ext.meta.maintainers.withIndex()) {
                        if (n > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        ExtensionMaintainerChip(maintainer)
                    }
                }
                ExtensionMetaRowSimpleText(label = stringRes(R.string.ext__meta__id)) {
                    ExtensionMetaValueText(text = ext.meta.id, monospace = true)
                }
                ExtensionMetaRowSimpleText(label = stringRes(R.string.ext__meta__version)) {
                    ExtensionMetaValueText(text = ext.meta.version, monospace = true)
                }
                if (ext.meta.keywords != null && ext.meta.keywords!!.isNotEmpty()) {
                    ExtensionMetaRowScrollableChips(label = stringRes(R.string.ext__meta__keywords)) {
                        for ((n, keyword) in ext.meta.keywords!!.withIndex()) {
                            if (n > 0) {
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            ExtensionKeywordChip(keyword)
                        }
                    }
                }
                if (!ext.meta.homepage.isNullOrBlank()) {
                    ExtensionMetaRowSimpleText(label = stringRes(R.string.ext__meta__homepage)) {
                        FlorisHyperlinkText(
                            text = FlorisRef.fromUrl(ext.meta.homepage!!).authority,
                            url = ext.meta.homepage!!,
                        )
                    }
                }
                if (!ext.meta.issueTracker.isNullOrBlank()) {
                    ExtensionMetaRowSimpleText(label = stringRes(R.string.ext__meta__issue_tracker)) {
                        FlorisHyperlinkText(
                            text = FlorisRef.fromUrl(ext.meta.issueTracker!!).authority,
                            url = ext.meta.issueTracker!!,
                        )
                    }
                }
                ExtensionMetaRowSimpleText(label = stringRes(R.string.ext__meta__license)) {
                    ExtensionMetaValueText(text = ext.meta.license, monospace = true)
                }
            }
        }
        FlorisOutlinedBox(
            modifier = Modifier.defaultFlorisOutlinedBox(),
            title = stringRes(R.string.ext__view__actions_title),
        ) {
            Text(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                text = stringRes(R.string.ext__view__actions_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                val canDelete = ThemeExtensionTrustStatePolicy.canDeleteExtension(
                    extensionCanBeDeleted = canDeleteExtension,
                    isDeleteInProgress = isDeleteInProgress,
                )
                if (canDeleteExtension) {
                    FlorisOutlinedButton(
                        onClick = {
                            if (canDelete) {
                                extToDelete = ext
                            }
                        },
                        icon = Icons.Default.Delete,
                        text = stringRes(R.string.action__delete),
                        enabled = canDelete,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                FlorisOutlinedButton(
                    onClick = {
                        navController.navigate(Routes.Ext.Export(ext.meta.id))
                    },
                    icon = Icons.Default.Share,
                    text = stringRes(R.string.action__export),
                    enabled = ThemeExtensionTrustStatePolicy.canExportExtension(isDeleteInProgress),
                )
            }
        }

        when (ext) {
            is ThemeExtension -> {
                ExtensionComponentListView(
                    title = stringRes(R.string.ext__meta__components_theme),
                    components = ext.themes,
                ) { component ->
                    ExtensionComponentView(
                        modifier = Modifier.defaultFlorisOutlinedBox(),
                        meta = ext.meta,
                        component = component,
                    )
                }
            }
            is LanguagePackExtension -> {
                ExtensionComponentListView(
                    title = stringRes(R.string.ext__meta__components_language_pack),
                    components = ext.items,
                ) { component ->
                    ExtensionComponentView(
                        modifier = Modifier.defaultFlorisOutlinedBox(),
                        meta = ext.meta,
                        component = component,
                    )
                }
            }
            else -> {
                // Render nothing
            }
        }

        if (extToDelete != null) {
            FlorisConfirmDeleteDialog(
                onConfirm = {
                    val target = extToDelete ?: return@FlorisConfirmDeleteDialog
                    if (!ThemeExtensionTrustStatePolicy.canDeleteExtension(canDeleteExtension, isDeleteInProgress)) {
                        extToDelete = null
                        return@FlorisConfirmDeleteDialog
                    }
                    extToDelete = null
                    isDeleteInProgress = true
                    lastDeleteNotice = null
                    lastDeleteErrorMessage = null
                    scope.launch {
                        val deleteResult = runCatching {
                            withContext(Dispatchers.IO) {
                                extensionManager.delete(target)
                            }
                        }
                        isDeleteInProgress = false
                        deleteResult.onSuccess {
                            navController.popBackStack()
                        }.onFailure { error ->
                            lastDeleteNotice = ThemeExtensionDeleteNotice.DeleteFailure
                            lastDeleteErrorMessage = error.localizedMessage ?: error.message
                            context.showLongToast(
                                R.string.error__snackbar_message_template,
                                "error_message" to (lastDeleteErrorMessage ?: ""),
                            )
                        }
                    }
                },
                onDismiss = { extToDelete = null },
                what = extToDelete!!.meta.title,
            )
        }
    }
}

@Composable
private fun ExtensionMetaRowSimpleText(
    label: String,
    modifier: Modifier = Modifier,
    showDividerAbove: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    val labelMaxLines = DynamicFontScale.maxLines(compact = 1, expanded = 2, fontScale = fontScale)

    if (showDividerAbove) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.56f))
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            modifier = Modifier
                .weight(0.40f)
                .padding(end = 16.dp),
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = labelMaxLines,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.weight(0.60f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun ExtensionMetaRowScrollableChips(
    label: String,
    modifier: Modifier = Modifier,
    showDividerAbove: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    val labelMaxLines = DynamicFontScale.maxLines(compact = 1, expanded = 2, fontScale = fontScale)

    if (showDividerAbove) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.56f))
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            modifier = Modifier
                .weight(0.40f)
                .padding(end = 16.dp),
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = labelMaxLines,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier
                .weight(0.60f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }
}

@Composable
private fun ExtensionMetaValueText(
    text: String,
    monospace: Boolean = false,
) {
    val fontScale = LocalDensity.current.fontScale
    val valueMaxLines = DynamicFontScale.maxLines(compact = 1, expanded = 3, fontScale = fontScale)

    SelectionContainer {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = if (monospace) FontFamily.Monospace else null,
            maxLines = valueMaxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun PreviewExtensionViewerScreen() {
    val testExtension = ThemeExtension(
        meta = ExtensionMeta(
            id = "com.example.theme.test",
            version = "2.4.3",
            title = "Test theme",
            description = "This is a test theme to preview the extension viewer screen UI.",
            keywords = listOf("Beach", "Sea", "Sun"),
            homepage = "https://example.com",
            issueTracker = "https://git.example.com/issues",
            maintainers = listOf(
                "Max Mustermann <max.mustermann@example.com> (maxmustermann.example.com)",
            ).map { ExtensionMaintainer.fromOrTakeRaw(it) },
            license = "apache-2.0",
        ),
        dependencies = null,
        themes = listOf(
            ThemeExtensionComponentImpl(id = "test", label = "Test", authors = listOf(), stylesheetPath = "test.json"),
        ),
    )
    ViewScreen(ext = testExtension)
}
