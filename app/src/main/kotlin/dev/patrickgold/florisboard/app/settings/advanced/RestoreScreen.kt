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

package dev.patrickgold.florisboard.app.settings.advanced

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.BuildConfig
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.cacheManager
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardFileStorage
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import dev.patrickgold.florisboard.lib.cache.CacheManager
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.ext.ExtensionManager
import dev.patrickgold.florisboard.lib.io.ZipUtils
import dev.patrickgold.jetpref.datastore.runtime.AndroidAppDataStorage
import dev.patrickgold.jetpref.datastore.runtime.FileBasedStorage
import dev.patrickgold.jetpref.datastore.runtime.ImportStrategy
import dev.patrickgold.jetpref.datastore.ui.Preference
import java.io.FileNotFoundException
import java.text.DateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.android.readToFile
import org.florisboard.lib.android.showLongToast
import org.florisboard.lib.compose.FlorisButtonBar
import org.florisboard.lib.compose.FlorisEmptyState
import org.florisboard.lib.compose.FlorisErrorCard
import org.florisboard.lib.compose.FlorisInfoCard
import org.florisboard.lib.compose.FlorisOutlinedBox
import org.florisboard.lib.compose.FlorisOutlinedButton
import org.florisboard.lib.compose.FlorisWarningCard
import org.florisboard.lib.compose.defaultFlorisOutlinedBox
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.kotlin.io.deleteContentsRecursively
import org.florisboard.lib.kotlin.io.readJson
import org.florisboard.lib.kotlin.io.subDir
import org.florisboard.lib.kotlin.io.subFile

object Restore {
    const val MIN_VERSION_CODE = 64
    const val PACKAGE_NAME = "dev.patrickgold.florisboard"
    const val BACKUP_ARCHIVE_FILE_NAME = "backup.zip"
}

@Composable
fun RestoreScreen() = FlorisScreen {
    title = stringRes(R.string.backup_and_restore__restore__title)
    previewFieldVisible = false

    val navController = LocalNavController.current
    val context = LocalContext.current
    val cacheManager by context.cacheManager()

    val restoreFilesSelector = remember { Backup.FilesSelector() }
    var importStrategy by remember { mutableStateOf(ImportStrategy.Merge) }
    val restoreScope = rememberCoroutineScope()
    var isRestoreInProgress by remember { mutableStateOf(false) }
    var restoreWorkspace by remember {
        mutableStateOf<CacheManager.BackupAndRestoreWorkspace?>(null)
    }

    suspend fun prepareRestoreWorkspace(uri: Uri): CacheManager.BackupAndRestoreWorkspace = withContext(Dispatchers.IO) {
        val workspace = cacheManager.backupAndRestore.new()
        try {
            workspace.zipFile = workspace.inputDir.subFile(Restore.BACKUP_ARCHIVE_FILE_NAME)
            context.contentResolver.readToFile(uri, workspace.zipFile)
            ZipUtils.unzip(workspace.zipFile, workspace.outputDir)
            workspace.metadata = try {
                workspace.outputDir.subFile(Backup.METADATA_JSON_NAME).readJson()
            } catch (e: FileNotFoundException) {
                error("Invalid archive: either backup_metadata.json is missing or file is not a ZIP archive.")
            }
            workspace.restoreWarningId = when {
                workspace.metadata.versionCode != BuildConfig.VERSION_CODE -> {
                    R.string.backup_and_restore__restore__metadata_warn_different_version
                }
                !workspace.metadata.packageName.startsWith(Restore.PACKAGE_NAME) -> {
                    R.string.backup_and_restore__restore__metadata_warn_different_vendor
                }
                else -> null
            }
            workspace.restoreErrorId = when {
                workspace.metadata.packageName.isBlank() || workspace.metadata.versionCode < Restore.MIN_VERSION_CODE -> {
                    R.string.backup_and_restore__restore__metadata_error_invalid_metadata
                }
                else -> null
            }
            workspace
        } catch (error: Throwable) {
            workspace.close()
            throw error
        }
    }

    val restoreDataFromFileSystemLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            restoreScope.launch {
                if (isRestoreInProgress) return@launch
                isRestoreInProgress = true
                runCatching {
                    restoreWorkspace?.close()
                    restoreWorkspace = null
                    prepareRestoreWorkspace(uri)
                }.onSuccess { workspace ->
                    restoreWorkspace = workspace
                }.onFailure { error ->
                    context.showLongToast(
                        R.string.backup_and_restore__restore__failure,
                        "error_message" to error.localizedMessage,
                    )
                }
                isRestoreInProgress = false
            }
        },
    )

    suspend fun performRestore() {
        val workspace = restoreWorkspace!!
        val shouldReset = importStrategy == ImportStrategy.Erase
        if (restoreFilesSelector.jetprefDatastore) {
            val file = workspace.outputDir
                .subDir(AndroidAppDataStorage.JETPREF_DIR_NAME)
                .subFile("${FlorisPreferenceModel.NAME}.${AndroidAppDataStorage.JETPREF_FILE_EXT}")
            if (file.exists()) {
                val fileBasedStorage = FileBasedStorage(file.path)
                FlorisPreferenceStore.import(importStrategy, fileBasedStorage).getOrThrow()
            }
        }
        val workspaceFilesDir = workspace.outputDir.subDir("files")
        if (restoreFilesSelector.imeKeyboard) {
            val srcDir = workspaceFilesDir.subDir(ExtensionManager.IME_KEYBOARD_PATH)
            val dstDir = context.filesDir.subDir(ExtensionManager.IME_KEYBOARD_PATH)
            if (shouldReset) {
                dstDir.deleteContentsRecursively()
            }
            if (srcDir.exists()) {
                srcDir.copyRecursively(dstDir, overwrite = true)
            }
        }
        if (restoreFilesSelector.imeTheme) {
            val srcDir = workspaceFilesDir.subDir(ExtensionManager.IME_THEME_PATH)
            val dstDir = context.filesDir.subDir(ExtensionManager.IME_THEME_PATH)
            if (shouldReset) {
                dstDir.deleteContentsRecursively()
            }
            if (srcDir.exists()) {
                srcDir.copyRecursively(dstDir, overwrite = true)
            }
        }
        val clipboardManager = context.clipboardManager().value
        if (shouldReset) {
            clipboardManager.clearFullHistory()
            ClipboardFileStorage.resetClipboardFileStorage(context)
        }

        if (restoreFilesSelector.provideClipboardItems()) {
            val clipboardFilesDir = workspace.outputDir.subDir("clipboard")

            if (restoreFilesSelector.clipboardTextItems) {
                val clipboardItems = clipboardFilesDir.subFile(Backup.CLIPBOARD_TEXT_ITEMS_JSON_NAME)
                if (clipboardItems.exists()) {
                    val clipboardItemsList = clipboardItems.readJson<List<ClipboardItem>>()
                    clipboardManager.restoreHistory(items = clipboardItemsList.filter { it.type == ItemType.TEXT })
                }
            }
            if (restoreFilesSelector.clipboardImageItems) {
                val clipboardItems = clipboardFilesDir.subFile(Backup.CLIPBOARD_IMAGES_JSON_NAME)
                if (clipboardItems.exists()) {
                    val clipboardItemsList = clipboardItems.readJson<List<ClipboardItem>>()
                    for (item in clipboardItemsList.filter { it.type == ItemType.IMAGE }) {
                        ClipboardFileStorage.insertFileFromBackupIfNotExisting(
                            context,
                            clipboardFilesDir.subFile(
                                relPath = "${ClipboardFileStorage.CLIPBOARD_FILES_PATH}/${
                                    item.uri!!.path!!.split(
                                        '/'
                                    ).last()
                                }"
                            )
                        )
                    }
                    clipboardManager.restoreHistory(items = clipboardItemsList.filter { it.type == ItemType.IMAGE })
                }
            }
            if (restoreFilesSelector.clipboardVideoItems) {
                val clipboardItems = clipboardFilesDir.subFile(Backup.CLIPBOARD_VIDEO_JSON_NAME)
                if (clipboardItems.exists()) {
                    val clipboardItemsList = clipboardItems.readJson<List<ClipboardItem>>()
                    for (item in clipboardItemsList.filter { it.type == ItemType.VIDEO }) {
                        ClipboardFileStorage.insertFileFromBackupIfNotExisting(
                            context,
                            clipboardFilesDir.subFile(
                                relPath = "${ClipboardFileStorage.CLIPBOARD_FILES_PATH}/${
                                    item.uri!!.path!!.split(
                                        '/'
                                    ).last()
                                }"
                            )
                        )
                    }
                    clipboardManager.restoreHistory(items = clipboardItemsList.filter { it.type == ItemType.VIDEO })
                }
            }
        }
    }

    bottomBar {
        FlorisButtonBar {
            ButtonBarSpacer()
            ButtonBarTextButton(
                onClick = {
                    restoreWorkspace?.close()
                    navController.navigateUp()
                },
                text = stringRes(R.string.action__cancel),
            )
            ButtonBarButton(
                onClick = {
                    restoreScope.launch {
                        if (isRestoreInProgress) return@launch
                        isRestoreInProgress = true
                        try {
                            withContext(Dispatchers.IO) {
                                performRestore()
                            }
                            context.showLongToast(R.string.backup_and_restore__restore__success)
                            navController.navigateUp()
                        } catch (e: Throwable) {
                            e.printStackTrace()
                            context.showLongToast(
                                R.string.backup_and_restore__restore__failure,
                                "error_message" to e.localizedMessage,
                            )
                        } finally {
                            isRestoreInProgress = false
                        }
                    }
                },
                text = stringRes(R.string.action__restore),
                enabled = restoreWorkspace != null && restoreWorkspace?.restoreErrorId == null && !isRestoreInProgress,
            )
        }
    }

    content {
        FlorisOutlinedBox(
            modifier = Modifier.defaultFlorisOutlinedBox(),
            title = stringRes(R.string.backup_and_restore__restore__mode),
        ) {
            RadioListItem(
                onClick = {
                    importStrategy = ImportStrategy.Merge
                },
                selected = importStrategy == ImportStrategy.Merge,
                text = stringRes(R.string.backup_and_restore__restore__mode_merge),
                secondaryText = stringRes(R.string.backup_and_restore__restore__mode_merge_summary),
            )
            RadioListItem(
                onClick = {
                    importStrategy = ImportStrategy.Erase
                },
                selected = importStrategy == ImportStrategy.Erase,
                text = stringRes(R.string.backup_and_restore__restore__mode_erase_and_overwrite),
                secondaryText = stringRes(R.string.backup_and_restore__restore__mode_erase_and_overwrite_summary),
            )
        }
        FlorisOutlinedButton(
            onClick = {
                runCatching {
                    restoreDataFromFileSystemLauncher.launch("*/*")
                }.onFailure { error ->
                    restoreScope.launch {
                        context.showLongToast(
                            R.string.backup_and_restore__restore__failure,
                            "error_message" to error.localizedMessage,
                        )
                    }
                }
            },
            modifier = Modifier
                .padding(vertical = 16.dp)
                .align(Alignment.CenterHorizontally),
            text = if (isRestoreInProgress && restoreWorkspace == null) {
                stringRes(R.string.backup_and_restore__restore__loading_file)
            } else {
                stringRes(R.string.action__select_file)
            },
            enabled = !isRestoreInProgress,
        )
        val workspace = restoreWorkspace
        if (isRestoreInProgress && workspace == null) {
            FlorisInfoCard(
                modifier = Modifier.padding(8.dp),
                text = stringRes(R.string.backup_and_restore__restore__loading_file),
                secondaryText = stringRes(R.string.backup_and_restore__restore__loading_file_summary),
            )
        } else if (workspace == null) {
            FlorisEmptyState(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                icon = Icons.Default.Archive,
                title = stringRes(R.string.backup_and_restore__restore__empty_title),
                message = stringRes(R.string.backup_and_restore__restore__empty_message),
            )
        } else {
            FlorisOutlinedBox(
                modifier = Modifier.defaultFlorisOutlinedBox(),
                title = stringRes(R.string.backup_and_restore__restore__metadata),
            ) {
                Preference(
                    icon = Icons.Default.Code,
                    title = workspace.metadata.packageName,
                )
                Preference(
                    icon = Icons.Outlined.Info,
                    title = "${workspace.metadata.versionName} (${workspace.metadata.versionCode})",
                )
                Preference(
                    icon = Icons.Default.Schedule,
                    title = remember(workspace.metadata.timestamp) {
                        val formatter = DateFormat.getDateTimeInstance()
                        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                        calendar.timeInMillis = workspace.metadata.timestamp
                        formatter.format(calendar.time)
                    },
                )
                if (workspace.restoreErrorId != null) {
                    FlorisErrorCard(
                        modifier = Modifier.padding(8.dp),
                        text = stringRes(R.string.backup_and_restore__restore__metadata_error_title),
                        secondaryText = stringRes(workspace.restoreErrorId!!),
                    )
                } else if (workspace.restoreWarningId != null) {
                    FlorisWarningCard(
                        modifier = Modifier.padding(8.dp),
                        text = stringRes(R.string.backup_and_restore__restore__metadata_warning_title),
                        secondaryText = stringRes(workspace.restoreWarningId!!),
                    )
                }
            }
            if (workspace.restoreErrorId == null) {
                BackupFilesSelector(
                    filesSelector = restoreFilesSelector,
                    title = stringRes(R.string.backup_and_restore__restore__files),
                )
            }
        }
    }
}
