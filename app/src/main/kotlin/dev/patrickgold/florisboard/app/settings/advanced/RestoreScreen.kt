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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.AppPackageContract
import dev.patrickgold.florisboard.BuildConfig
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.cacheManager
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.ime.clipboard.ClipboardRestoredFileInfo
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardFileStorage
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardFileInfo
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardFilesDatabase
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import dev.patrickgold.florisboard.lib.cache.CacheManager
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.devtools.flogError
import dev.patrickgold.florisboard.lib.ext.ExtensionManager
import dev.patrickgold.florisboard.lib.io.ZipUtils
import dev.patrickgold.jetpref.datastore.runtime.AndroidAppDataStorage
import dev.patrickgold.jetpref.datastore.runtime.FileBasedStorage
import dev.patrickgold.jetpref.datastore.runtime.ImportStrategy
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
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
import org.florisboard.lib.compose.FlorisNeutralCard
import org.florisboard.lib.compose.FlorisOutlinedBox
import org.florisboard.lib.compose.FlorisOutlinedButton
import org.florisboard.lib.compose.FlorisProgressCard
import org.florisboard.lib.compose.FlorisWarningCard
import org.florisboard.lib.compose.defaultFlorisOutlinedBox
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.kotlin.io.deleteContentsRecursively
import org.florisboard.lib.kotlin.io.readJson
import org.florisboard.lib.kotlin.io.subDir
import org.florisboard.lib.kotlin.io.subFile

object Restore {
    const val MIN_VERSION_CODE = 64
    // Both the SwiftFloris-owned ID and the pre-migration (upstream
    // FlorisBoard) ID are accepted, so old-ID backups carry user data across
    // the application-ID migration without a vendor warning.
    val ACCEPTED_PACKAGE_PREFIXES = listOf(
        AppPackageContract.BASE_APPLICATION_ID,
        AppPackageContract.LEGACY_APPLICATION_ID,
    )
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
    var showEraseRestoreConfirmation by remember { mutableStateOf(false) }
    var lastRestoreNotice by remember { mutableStateOf<RestoreFlowNotice?>(null) }
    var lastRestoreErrorMessage by remember { mutableStateOf<String?>(null) }
    var lastRestoreSummary by remember { mutableStateOf<RestoreOperationSummary?>(null) }
    val unknownRestoreError = stringRes(R.string.backup_and_restore__restore__unknown_error)

    // Close the workspace when the screen leaves composition (system-back / nav-up),
    // not only via the Cancel button. prepareRestoreWorkspace extracts the archive —
    // including clipboard plaintext and the decrypted jetpref datastore — into the
    // cache dir; without this, leaving any other way leaves that plaintext on disk.
    // rememberUpdatedState so onDispose sees the latest workspace/flag, and we skip
    // closing mid-restore so we don't pull the dir out from under an in-flight copy.
    val currentRestoreWorkspace by rememberUpdatedState(restoreWorkspace)
    val currentIsRestoreInProgress by rememberUpdatedState(isRestoreInProgress)
    DisposableEffect(Unit) {
        onDispose {
            if (!currentIsRestoreInProgress) currentRestoreWorkspace?.close()
        }
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
            val workspaceFilesDir = workspace.outputDir.subDir("files")
            val clipboardFilesDir = workspace.outputDir.subDir("clipboard")
            val validation = BackupRestorePolicy.validateRestoreArchive(
                metadata = workspace.metadata,
                currentVersionCode = BuildConfig.VERSION_CODE,
                minimumVersionCode = Restore.MIN_VERSION_CODE,
                expectedPackagePrefixes = Restore.ACCEPTED_PACKAGE_PREFIXES,
                hasRestorableContent = BackupRestorePolicy.hasRestorableContent(
                    hasJetprefDatastore = workspace.outputDir
                        .subDir(AndroidAppDataStorage.JETPREF_DIR_NAME)
                        .subFile("${FlorisPreferenceModel.NAME}.${AndroidAppDataStorage.JETPREF_FILE_EXT}")
                        .exists(),
                    hasImeKeyboard = workspaceFilesDir.subDir(ExtensionManager.IME_KEYBOARD_PATH).exists(),
                    hasImeTheme = workspaceFilesDir.subDir(ExtensionManager.IME_THEME_PATH).exists(),
                    hasClipboardTextItems = clipboardFilesDir.subFile(Backup.CLIPBOARD_TEXT_ITEMS_JSON_NAME).exists(),
                    hasClipboardImageItems = clipboardFilesDir.subFile(Backup.CLIPBOARD_IMAGES_JSON_NAME).exists(),
                    hasClipboardVideoItems = clipboardFilesDir.subFile(Backup.CLIPBOARD_VIDEO_JSON_NAME).exists(),
                ),
            )
            workspace.restoreWarningId = validation.warningId
            workspace.restoreErrorId = validation.errorId
            workspace
        } catch (error: Throwable) {
            workspace.close()
            throw error
        }
    }

    val restoreDataFromFileSystemLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri == null) {
                lastRestoreNotice = RestoreFlowNotice.Cancelled
                lastRestoreErrorMessage = null
                lastRestoreSummary = null
                return@rememberLauncherForActivityResult
            }
            restoreScope.launch {
                if (isRestoreInProgress) return@launch
                isRestoreInProgress = true
                lastRestoreNotice = null
                lastRestoreErrorMessage = null
                lastRestoreSummary = null
                runCatching {
                    restoreWorkspace?.close()
                    restoreWorkspace = null
                    prepareRestoreWorkspace(uri)
                }.onSuccess { workspace ->
                    restoreWorkspace = workspace
                }.onFailure { error ->
                    flogError { error.stackTraceToString() }
                    val errorMessage = BackupRestorePolicy.restoreErrorMessage(error, unknownRestoreError)
                    context.showLongToast(
                        R.string.backup_and_restore__restore__failure,
                        "error_message" to errorMessage,
                    )
                    lastRestoreNotice = RestoreFlowNotice.Failure
                    lastRestoreErrorMessage = errorMessage
                }
                isRestoreInProgress = false
            }
        },
    )

    suspend fun performRestore(): RestoreOperationSummary {
        val workspace = restoreWorkspace!!
        val shouldReset = importStrategy == ImportStrategy.Erase
        var summary = RestoreOperationSummary()

        fun markSelected() {
            summary = summary.copy(selectedSections = summary.selectedSections + 1)
        }

        fun markRestored() {
            summary = summary.copy(restoredSections = summary.restoredSections + 1)
        }

        fun markMissing() {
            summary = summary.copy(missingSections = summary.missingSections + 1)
        }

        fun markFailed(error: Throwable) {
            val errorMessage = BackupRestorePolicy.restoreErrorMessage(error, unknownRestoreError)
            summary = summary.copy(
                failedSections = summary.failedSections + 1,
                firstFailureMessage = summary.firstFailureMessage ?: errorMessage,
            )
        }

        suspend fun restoreSelectedSection(
            sourceExists: Boolean,
            block: suspend () -> Unit,
        ) {
            markSelected()
            if (!sourceExists) {
                markMissing()
                return
            }
            runCatching {
                block()
            }.onSuccess {
                markRestored()
            }.onFailure { error ->
                flogError { error.stackTraceToString() }
                markFailed(error)
            }
        }

        fun insertRestoredClipboardFileInfos(restoredFileInfos: List<ClipboardFileInfo>) {
            if (restoredFileInfos.isEmpty()) return
            val clipboardFilesDb = ClipboardFilesDatabase.new(context)
            try {
                clipboardFilesDb.clipboardFilesDao().insert(*restoredFileInfos.toTypedArray())
            } finally {
                clipboardFilesDb.close()
            }
        }

        if (restoreFilesSelector.jetprefDatastore) {
            val file = workspace.outputDir
                .subDir(AndroidAppDataStorage.JETPREF_DIR_NAME)
                .subFile("${FlorisPreferenceModel.NAME}.${AndroidAppDataStorage.JETPREF_FILE_EXT}")
            restoreSelectedSection(sourceExists = file.exists()) {
                val fileBasedStorage = FileBasedStorage(file.path)
                FlorisPreferenceStore.import(importStrategy, fileBasedStorage).getOrThrow()
            }
        }
        val workspaceFilesDir = workspace.outputDir.subDir("files")
        if (restoreFilesSelector.imeKeyboard) {
            val srcDir = workspaceFilesDir.subDir(ExtensionManager.IME_KEYBOARD_PATH)
            val dstDir = context.filesDir.subDir(ExtensionManager.IME_KEYBOARD_PATH)
            restoreSelectedSection(sourceExists = srcDir.exists()) {
                if (shouldReset) {
                    dstDir.deleteContentsRecursively()
                }
                srcDir.copyRecursively(dstDir, overwrite = true)
            }
        }
        if (restoreFilesSelector.imeTheme) {
            val srcDir = workspaceFilesDir.subDir(ExtensionManager.IME_THEME_PATH)
            val dstDir = context.filesDir.subDir(ExtensionManager.IME_THEME_PATH)
            restoreSelectedSection(sourceExists = srcDir.exists()) {
                if (shouldReset) {
                    dstDir.deleteContentsRecursively()
                }
                srcDir.copyRecursively(dstDir, overwrite = true)
            }
        }
        val clipboardManager = context.clipboardManager().value
        var clipboardWasReset = false

        fun ensureClipboardReset() {
            if (shouldReset && !clipboardWasReset) {
                clipboardManager.clearFullHistory()
                ClipboardFileStorage.resetClipboardFileStorage(context)
                clipboardWasReset = true
            }
        }

        if (restoreFilesSelector.provideClipboardItems()) {
            val clipboardFilesDir = workspace.outputDir.subDir("clipboard")

            if (restoreFilesSelector.clipboardTextItems) {
                val clipboardItems = clipboardFilesDir.subFile(Backup.CLIPBOARD_TEXT_ITEMS_JSON_NAME)
                restoreSelectedSection(sourceExists = clipboardItems.exists()) {
                    ensureClipboardReset()
                    val clipboardItemsList = clipboardItems.readJson<List<ClipboardItem>>()
                    clipboardManager.restoreHistory(items = clipboardItemsList.filter { it.type == ItemType.TEXT })
                }
            }
            if (restoreFilesSelector.clipboardImageItems) {
                val clipboardItems = clipboardFilesDir.subFile(Backup.CLIPBOARD_IMAGES_JSON_NAME)
                restoreSelectedSection(sourceExists = clipboardItems.exists()) {
                    ensureClipboardReset()
                    val restoredFileInfos = mutableListOf<ClipboardFileInfo>()
                    val clipboardItemsList = clipboardItems.readJson<List<ClipboardItem>>()
                    val restoredItems = clipboardItemsList.filter { it.type == ItemType.IMAGE }
                    for (item in restoredItems) {
                        val restoredFileId = item.uri?.path?.split('/')?.lastOrNull() ?: continue
                        val restoredFile = ClipboardFileStorage.insertFileFromBackupIfNotExisting(
                            context,
                            clipboardFilesDir.subFile(
                                relPath = "${ClipboardFileStorage.CLIPBOARD_FILES_PATH}/$restoredFileId"
                            )
                        )
                        restoredFileId.toLongOrNull()?.let { id ->
                            ClipboardRestoredFileInfo.create(item, id, restoredFile.length())
                                ?.let(restoredFileInfos::add)
                        }
                    }
                    clipboardManager.restoreHistory(items = restoredItems)
                    insertRestoredClipboardFileInfos(restoredFileInfos)
                }
            }
            if (restoreFilesSelector.clipboardVideoItems) {
                val clipboardItems = clipboardFilesDir.subFile(Backup.CLIPBOARD_VIDEO_JSON_NAME)
                restoreSelectedSection(sourceExists = clipboardItems.exists()) {
                    ensureClipboardReset()
                    val restoredFileInfos = mutableListOf<ClipboardFileInfo>()
                    val clipboardItemsList = clipboardItems.readJson<List<ClipboardItem>>()
                    val restoredItems = clipboardItemsList.filter { it.type == ItemType.VIDEO }
                    for (item in restoredItems) {
                        val restoredFileId = item.uri?.path?.split('/')?.lastOrNull() ?: continue
                        val restoredFile = ClipboardFileStorage.insertFileFromBackupIfNotExisting(
                            context,
                            clipboardFilesDir.subFile(
                                relPath = "${ClipboardFileStorage.CLIPBOARD_FILES_PATH}/$restoredFileId"
                            )
                        )
                        restoredFileId.toLongOrNull()?.let { id ->
                            ClipboardRestoredFileInfo.create(item, id, restoredFile.length())
                                ?.let(restoredFileInfos::add)
                        }
                    }
                    clipboardManager.restoreHistory(items = restoredItems)
                    insertRestoredClipboardFileInfos(restoredFileInfos)
                }
            }
        }
        return summary
    }

    fun startRestore() {
        restoreScope.launch {
            if (isRestoreInProgress) return@launch
            isRestoreInProgress = true
            lastRestoreNotice = null
            lastRestoreErrorMessage = null
            lastRestoreSummary = null
            try {
                val summary = withContext(Dispatchers.IO) {
                    performRestore()
                }
                val result = summary.result
                lastRestoreSummary = summary
                lastRestoreErrorMessage = summary.firstFailureMessage
                lastRestoreNotice = BackupRestorePolicy.noticeForRestoreOperationResult(result)
                when (result) {
                    RestoreOperationResult.Success -> {
                        context.showLongToast(R.string.backup_and_restore__restore__success)
                        navController.navigateUp()
                    }
                    RestoreOperationResult.PartialFailure -> {
                        context.showLongToast(R.string.backup_and_restore__restore__partial_failure_toast)
                    }
                    RestoreOperationResult.Failure -> {
                        context.showLongToast(
                            R.string.backup_and_restore__restore__failure,
                            "error_message" to (lastRestoreErrorMessage ?: unknownRestoreError),
                        )
                    }
                    RestoreOperationResult.Cancelled -> {
                        context.showLongToast(R.string.backup_and_restore__restore__cancelled)
                    }
                }
            } catch (e: Throwable) {
                flogError { e.stackTraceToString() }
                val errorMessage = BackupRestorePolicy.restoreErrorMessage(e, unknownRestoreError)
                lastRestoreNotice = RestoreFlowNotice.Failure
                lastRestoreErrorMessage = errorMessage
                context.showLongToast(
                    R.string.backup_and_restore__restore__failure,
                    "error_message" to errorMessage,
                )
            } finally {
                isRestoreInProgress = false
            }
        }
    }

    if (showEraseRestoreConfirmation) {
        JetPrefAlertDialog(
            title = stringRes(R.string.backup_and_restore__restore__confirm_erase_title),
            confirmLabel = stringRes(R.string.action__restore),
            dismissLabel = stringRes(R.string.action__cancel),
            onConfirm = {
                showEraseRestoreConfirmation = false
                startRestore()
            },
            onDismiss = {
                showEraseRestoreConfirmation = false
            },
        ) {
            Text(text = stringRes(R.string.backup_and_restore__restore__confirm_erase_message))
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
                enabled = !isRestoreInProgress,
            )
            ButtonBarButton(
                onClick = {
                    if (importStrategy == ImportStrategy.Erase) {
                        showEraseRestoreConfirmation = true
                    } else {
                        startRestore()
                    }
                },
                text = if (isRestoreInProgress && restoreWorkspace != null) {
                    stringRes(R.string.backup_and_restore__restore__in_progress)
                } else {
                    stringRes(R.string.action__restore)
                },
                enabled = BackupRestorePolicy.canStartRestore(
                    hasWorkspace = restoreWorkspace != null,
                    restoreErrorId = restoreWorkspace?.restoreErrorId,
                    hasSelectedFiles = restoreFilesSelector.atLeastOneSelected(),
                    isRestoreInProgress = isRestoreInProgress,
                ),
            )
        }
    }

    content {
        val workspace = restoreWorkspace
        when (BackupRestorePolicy.resolveRestoreFlowNotice(
            isRestoreInProgress = isRestoreInProgress,
            hasWorkspace = workspace != null,
            eraseMode = importStrategy == ImportStrategy.Erase,
            lastTerminalNotice = lastRestoreNotice,
        )) {
            RestoreFlowNotice.LoadingArchive -> FlorisProgressCard(
                modifier = Modifier.padding(8.dp),
                text = stringRes(R.string.backup_and_restore__restore__loading_file),
                secondaryText = stringRes(R.string.backup_and_restore__restore__loading_file_summary),
            )
            RestoreFlowNotice.Restoring -> FlorisProgressCard(
                modifier = Modifier.padding(8.dp),
                text = stringRes(R.string.backup_and_restore__restore__in_progress),
                secondaryText = stringRes(R.string.backup_and_restore__restore__in_progress_summary),
            )
            RestoreFlowNotice.EraseRecoveryCopy -> FlorisWarningCard(
                modifier = Modifier.padding(8.dp),
                text = stringRes(R.string.backup_and_restore__restore__erase_recovery_copy_title),
                secondaryText = stringRes(R.string.backup_and_restore__restore__erase_recovery_copy_summary),
            )
            RestoreFlowNotice.Cancelled -> FlorisNeutralCard(
                modifier = Modifier.padding(8.dp),
                text = stringRes(R.string.backup_and_restore__restore__cancelled),
                secondaryText = stringRes(R.string.backup_and_restore__restore__cancelled_summary),
            )
            RestoreFlowNotice.Failure -> FlorisErrorCard(
                modifier = Modifier.padding(8.dp),
                text = stringRes(R.string.backup_and_restore__restore__failure_title),
                secondaryText = stringRes(
                    R.string.backup_and_restore__restore__failure_recovery,
                    "error_message" to (lastRestoreErrorMessage ?: unknownRestoreError),
                ),
            )
            RestoreFlowNotice.PartialFailure -> {
                val summary = lastRestoreSummary
                FlorisWarningCard(
                    modifier = Modifier.padding(8.dp),
                    text = stringRes(R.string.backup_and_restore__restore__partial_failure_title),
                    secondaryText = stringRes(
                        R.string.backup_and_restore__restore__partial_failure_summary,
                        "restored_count" to (summary?.restoredSections ?: 0),
                        "problem_count" to (summary?.problemSections ?: 0),
                    ),
                )
            }
            RestoreFlowNotice.Success,
            RestoreFlowNotice.None,
            -> Unit
        }
        FlorisOutlinedBox(
            modifier = Modifier.defaultFlorisOutlinedBox(),
            title = stringRes(R.string.backup_and_restore__restore__mode),
        ) {
            RadioListItem(
                onClick = {
                    importStrategy = ImportStrategy.Merge
                },
                selected = importStrategy == ImportStrategy.Merge,
                enabled = !isRestoreInProgress,
                text = stringRes(R.string.backup_and_restore__restore__mode_merge),
                secondaryText = stringRes(R.string.backup_and_restore__restore__mode_merge_summary),
            )
            RadioListItem(
                onClick = {
                    importStrategy = ImportStrategy.Erase
                },
                selected = importStrategy == ImportStrategy.Erase,
                enabled = !isRestoreInProgress,
                text = stringRes(R.string.backup_and_restore__restore__mode_erase_and_overwrite),
                secondaryText = stringRes(R.string.backup_and_restore__restore__mode_erase_and_overwrite_summary),
            )
        }
        FlorisOutlinedButton(
            onClick = {
                runCatching {
                    restoreDataFromFileSystemLauncher.launch("*/*")
                }.onFailure { error ->
                    flogError { error.stackTraceToString() }
                    val errorMessage = BackupRestorePolicy.restoreErrorMessage(error, unknownRestoreError)
                    lastRestoreNotice = RestoreFlowNotice.Failure
                    lastRestoreErrorMessage = errorMessage
                    lastRestoreSummary = null
                    restoreScope.launch {
                        context.showLongToast(
                            R.string.backup_and_restore__restore__failure,
                            "error_message" to errorMessage,
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
        if (workspace == null) {
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
                    enabled = !isRestoreInProgress,
                )
            }
        }
    }
}
