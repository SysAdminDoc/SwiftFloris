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

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.cacheManager
import dev.patrickgold.florisboard.extensionManager
import dev.patrickgold.florisboard.ime.keyboard.KeyboardExtension
import dev.patrickgold.florisboard.ime.nlp.LanguagePackExtension
import dev.patrickgold.florisboard.ime.theme.ThemeExtension
import dev.patrickgold.florisboard.lib.NATIVE_NULLPTR
import dev.patrickgold.florisboard.lib.cache.CacheManager
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.io.FileRegistry
import kotlinx.coroutines.launch
import org.florisboard.lib.android.showLongToast
import org.florisboard.lib.compose.FlorisBulletSpacer
import org.florisboard.lib.compose.FlorisButtonBar
import org.florisboard.lib.compose.FlorisEmptyState
import org.florisboard.lib.compose.FlorisErrorCard
import org.florisboard.lib.compose.FlorisInfoCard
import org.florisboard.lib.compose.FlorisOutlinedBox
import org.florisboard.lib.compose.FlorisWarningCard
import org.florisboard.lib.compose.defaultFlorisOutlinedBox
import org.florisboard.lib.compose.florisHorizontalScroll
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.kotlin.resultOk

enum class ExtensionImportScreenType(
    val id: String,
    @param:StringRes val titleResId: Int,
    val supportedFiles: List<FileRegistry.Entry>,
) {
    EXT_ANY(
        id = "ext-any",
        titleResId = R.string.ext__import__ext_any,
        supportedFiles = listOf(FileRegistry.FlexExtension),
    ),
    EXT_KEYBOARD(
        id = "ext-keyboard",
        titleResId = R.string.ext__import__ext_keyboard,
        supportedFiles = listOf(FileRegistry.FlexExtension),
    ),
    EXT_THEME(
        id = "ext-theme",
        titleResId = R.string.ext__import__ext_theme,
        supportedFiles = listOf(FileRegistry.FlexExtension),
    ),
    EXT_LANGUAGEPACK(
        id = "ext-languagepack",
        titleResId = R.string.ext__import__ext_languagepack,
        supportedFiles = listOf(FileRegistry.FlexExtension),
    );
}

@Composable
fun ExtensionImportScreen(type: ExtensionImportScreenType, initUuid: String?) = FlorisScreen {
    title = stringRes(type.titleResId)

    val navController = LocalNavController.current
    val context = LocalContext.current
    val cacheManager by context.cacheManager()
    val extensionManager by context.extensionManager()
    val scope = rememberCoroutineScope()

    fun getSkipReason(fileInfo: CacheManager.FileInfo): Int {
        val ext = fileInfo.ext
        val existingSource = ext?.meta?.id
            ?.let(extensionManager::getExtensionById)
            .let(ExtensionImportPolicy::existingSourceFor)
        return ExtensionImportPolicy.decideFile(
            fileMatchesFilter = FileRegistry.matchesFileFilter(fileInfo, type.supportedFiles),
            extension = ext,
            requestedType = type,
            existingSource = existingSource,
        ).skipReason
    }

    fun Result<CacheManager.ImporterWorkspace>.mapSkipReasons(): Result<CacheManager.ImporterWorkspace> {
        return this.map { workspace ->
            workspace.inputFileInfos.forEach { fileInfo ->
                fileInfo.skipReason = getSkipReason(fileInfo)
            }
            workspace
        }
    }

    var importResult by remember(initUuid) {
        val workspace = initUuid?.let { cacheManager.importer.getWorkspaceByUuid(it) }
            ?.let { resultOk(it) }
            ?.mapSkipReasons()
        mutableStateOf(workspace)
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = { uriList ->
            // If uri is null it indicates that the selection activity
            //  was cancelled (mostly by pressing the back button), so
            //  we don't display an error message here.
            if (uriList.isEmpty()) return@rememberLauncherForActivityResult
            importResult?.getOrNull()?.close()
            importResult = runCatching { cacheManager.readFromUriIntoCache(uriList) }.mapSkipReasons()
        },
    )
    val selectFiles = {
        importLauncher.launch("*/*")
    }

    bottomBar {
        FlorisButtonBar {
            ButtonBarSpacer()
            ButtonBarTextButton(
                text = stringRes(R.string.action__cancel),
            ) {
                importResult?.getOrNull()?.close()
                navController.popBackStack()
            }
            val enabled = remember(importResult) {
                importResult?.getOrNull()?.takeIf { workspace ->
                    workspace.inputFileInfos.any { it.skipReason == NATIVE_NULLPTR.toInt() }
                } != null
            }
            ButtonBarButton(
                text = stringRes(R.string.action__import),
                enabled = enabled,
            ) {
                val workspace = importResult!!.getOrThrow()
                runCatching {
                    for (fileInfo in workspace.inputFileInfos) {
                        if (fileInfo.skipReason != NATIVE_NULLPTR.toInt()) {
                            continue
                        }
                        val ext = fileInfo.ext
                        when (type) {
                            ExtensionImportScreenType.EXT_ANY -> {
                                ext?.let { extensionManager.import(it) }
                            }
                            ExtensionImportScreenType.EXT_KEYBOARD -> {
                                ext.takeIf { it is KeyboardExtension }?.let { extensionManager.import(it) }
                            }
                            ExtensionImportScreenType.EXT_THEME -> {
                                ext.takeIf { it is ThemeExtension }?.let { extensionManager.import(it) }
                            }
                            ExtensionImportScreenType.EXT_LANGUAGEPACK -> {
                                ext.takeIf { it is LanguagePackExtension }?.let { extensionManager.import(it) }
                            }
                        }
                    }
                }.onSuccess {
                    workspace.close()
                    scope.launch {
                        context.showLongToast(R.string.ext__import__success)
                        navController.popBackStack()
                    }
                }.onFailure { error ->
                    scope.launch {
                        context.showLongToast(R.string.ext__import__failure, "error_message" to error.localizedMessage)
                    }
                }
            }
        }
    }

    content {
        val result = importResult
        when {
            result == null -> {
                FlorisEmptyState(
                    modifier = Modifier.padding(16.dp),
                    icon = Icons.AutoMirrored.Filled.Input,
                    title = stringRes(R.string.ext__import__empty_title),
                    message = stringRes(R.string.ext__import__empty_message),
                    actionLabel = stringRes(R.string.action__select_files),
                    onAction = selectFiles.takeIf { initUuid == null },
                )
            }
            result.isSuccess -> {
                val workspace = result.getOrThrow()
                val importableFileCount = workspace.inputFileInfos.count { fileInfo ->
                    fileInfo.skipReason == NATIVE_NULLPTR.toInt()
                }
                val skippedFileCount = workspace.inputFileInfos.size - importableFileCount
                if (importableFileCount > 0) {
                    FlorisInfoCard(
                        modifier = Modifier.defaultFlorisOutlinedBox(),
                        text = stringRes(R.string.ext__import__review_title),
                        secondaryText = stringRes(
                            if (skippedFileCount > 0) {
                                R.string.ext__import__review_message_with_skips
                            } else {
                                R.string.ext__import__review_message_all_ready
                            },
                        ),
                        actionLabel = stringRes(R.string.action__select_files).takeIf { initUuid == null },
                        onClick = selectFiles.takeIf { initUuid == null },
                    )
                } else {
                    FlorisWarningCard(
                        modifier = Modifier.defaultFlorisOutlinedBox(),
                        text = stringRes(R.string.ext__import__none_ready_title),
                        secondaryText = stringRes(R.string.ext__import__none_ready_message),
                        actionLabel = stringRes(R.string.action__select_files).takeIf { initUuid == null },
                        onClick = selectFiles.takeIf { initUuid == null },
                    )
                }
                for (fileInfo in workspace.inputFileInfos) {
                    FileInfoView(fileInfo)
                }
            }
            result.isFailure -> {
                FlorisErrorCard(
                    modifier = Modifier.defaultFlorisOutlinedBox(),
                    text = stringRes(R.string.ext__import__error_title),
                    secondaryText = stringRes(R.string.ext__import__error_message),
                    actionLabel = stringRes(R.string.action__select_files).takeIf { initUuid == null },
                    onClick = selectFiles.takeIf { initUuid == null },
                )
                FlorisOutlinedBox(
                    modifier = Modifier.defaultFlorisOutlinedBox(),
                    title = stringRes(R.string.ext__import__error_details_title),
                ) {
                    SelectionContainer {
                        Text(
                            modifier = Modifier
                                .florisHorizontalScroll()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            text = result.exceptionOrNull()?.stackTraceToString()
                                ?: stringRes(R.string.ext__import__error_details_unavailable),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileInfoView(
    fileInfo: CacheManager.FileInfo,
) {
    val context = LocalContext.current
    val isImportable = fileInfo.skipReason == NATIVE_NULLPTR.toInt()
    FlorisOutlinedBox(
        modifier = Modifier.defaultFlorisOutlinedBox(),
        title = fileInfo.file.name,
        subtitle = fileInfo.mediaType ?: stringRes(R.string.ext__import__unknown_file_type),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            val metadataColor = MaterialTheme.colorScheme.onSurfaceVariant
            val statusColor = if (isImportable) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }
            val ext = fileInfo.ext
            Text(
                text = stringRes(
                    if (isImportable) {
                        R.string.ext__import__file_ready
                    } else {
                        R.string.ext__import__file_skipped
                    },
                ),
                style = MaterialTheme.typography.labelLarge,
                color = statusColor,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row {
                Text(
                    text = Formatter.formatShortFileSize(context, fileInfo.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = metadataColor,
                )
                if (ext != null) {
                    FlorisBulletSpacer()
                    Text(
                        text = ext.meta.id,
                        style = MaterialTheme.typography.bodyMedium,
                        color = metadataColor,
                    )
                    FlorisBulletSpacer()
                    Text(
                        text = ext.meta.version,
                        style = MaterialTheme.typography.bodyMedium,
                        color = metadataColor,
                    )
                }
            }
            if (ext != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = ext.meta.title,
                    style = MaterialTheme.typography.titleSmall,
                )
                ext.meta.description?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = metadataColor,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                val maintainers = remember(ext) {
                    ext.meta.maintainers.joinToString { it.name }
                }
                Text(
                    text = stringRes(R.string.ext__meta__maintainers_by, "maintainers" to maintainers),
                    style = MaterialTheme.typography.bodyMedium,
                    color = metadataColor,
                )
                val components = remember(ext) { ext.components() }
                if (components.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringRes(R.string.ext__import__components_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = metadataColor,
                    )
                    for (component in components) {
                        Text(
                            text = component.id,
                            style = MaterialTheme.typography.bodyMedium,
                            color = metadataColor,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
            if (!isImportable) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringRes(R.string.ext__import__file_skip),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = stringRes(fileInfo.skipReason),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
