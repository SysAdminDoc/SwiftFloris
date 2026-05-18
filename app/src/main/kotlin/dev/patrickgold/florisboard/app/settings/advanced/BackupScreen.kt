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

import android.content.ContentUris
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import dev.patrickgold.florisboard.BuildConfig
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.cacheManager
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardFileStorage
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import dev.patrickgold.florisboard.lib.cache.CacheManager
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.devtools.flogError
import dev.patrickgold.florisboard.lib.ext.ExtensionManager
import dev.patrickgold.florisboard.lib.io.FileRegistry
import dev.patrickgold.florisboard.lib.io.ZipUtils
import dev.patrickgold.jetpref.datastore.runtime.AndroidAppDataStorage
import dev.patrickgold.jetpref.datastore.runtime.FileBasedStorage
import dev.patrickgold.jetpref.material.ui.JetPrefListItem
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.florisboard.lib.android.showLongToast
import org.florisboard.lib.android.writeFromFile
import org.florisboard.lib.compose.FlorisButtonBar
import org.florisboard.lib.compose.FlorisErrorCard
import org.florisboard.lib.compose.FlorisNeutralCard
import org.florisboard.lib.compose.FlorisOutlinedBox
import org.florisboard.lib.compose.FlorisProgressCard
import org.florisboard.lib.compose.FlorisWarningCard
import org.florisboard.lib.compose.defaultFlorisOutlinedBox
import org.florisboard.lib.compose.rippleClickable
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.kotlin.io.subDir
import org.florisboard.lib.kotlin.io.subFile
import org.florisboard.lib.kotlin.io.writeJson

object Backup {
    const val FILE_PROVIDER_AUTHORITY = "${BuildConfig.APPLICATION_ID}.provider.file"
    const val METADATA_JSON_NAME = "backup_metadata.json"
    const val CLIPBOARD_TEXT_ITEMS_JSON_NAME = "clipboard_text_items.json"
    const val CLIPBOARD_IMAGES_JSON_NAME = "clipboard_images.json"
    const val CLIPBOARD_VIDEO_JSON_NAME = "clipboard_video.json"

    fun defaultFileName(metadata: Metadata): String {
        return "backup_${metadata.packageName}_${metadata.versionCode}_${metadata.timestamp}.zip"
    }

    enum class Destination {
        FILE_SYS,
        SHARE_INTENT;
    }

    class FilesSelector {
        var jetprefDatastore by mutableStateOf(true)
        var imeKeyboard by mutableStateOf(true)
        var imeTheme by mutableStateOf(true)
        var clipboardTextItems by mutableStateOf(false)
        var clipboardImageItems by mutableStateOf(false)
        var clipboardVideoItems by mutableStateOf(false)

        private var _clipboardData: MutableState<ToggleableState> = mutableStateOf(ToggleableState.Off)
        val clipboardData: State<ToggleableState> = _clipboardData

        fun updateCheckboxState() {
            val newValue = if (
                !clipboardVideoItems && !clipboardImageItems && !clipboardTextItems
            ) {
                ToggleableState.Off
            } else if (
                clipboardVideoItems && clipboardImageItems && clipboardTextItems
            ) {
                ToggleableState.On
            } else {
                ToggleableState.Indeterminate
            }
            _clipboardData.value = newValue
        }

        fun provideClipboardItems(): Boolean {
            return clipboardTextItems || clipboardImageItems || clipboardVideoItems
        }

        fun atLeastOneSelected(): Boolean {
            return jetprefDatastore || imeKeyboard || imeTheme || clipboardTextItems || clipboardImageItems || clipboardVideoItems
        }
    }

    @Serializable
    data class Metadata(
        @SerialName("package")
        val packageName: String,
        val versionCode: Int,
        val versionName: String,
        val timestamp: Long,
    )
}

@Composable
fun BackupScreen() = FlorisScreen {
    title = stringRes(R.string.backup_and_restore__back_up__title)
    previewFieldVisible = false

    val navController = LocalNavController.current
    val context = LocalContext.current
    val cacheManager by context.cacheManager()
    val scope = rememberCoroutineScope()

    var backupDestination by remember { mutableStateOf(Backup.Destination.FILE_SYS) }
    val backupFilesSelector = remember { Backup.FilesSelector() }
    var backupWorkspace by remember { mutableStateOf<CacheManager.BackupAndRestoreWorkspace?>(null) }
    var isBackupInProgress by remember { mutableStateOf(false) }
    var lastBackupNotice by remember { mutableStateOf<BackupFlowNotice?>(null) }
    var lastBackupErrorMessage by remember { mutableStateOf<String?>(null) }

    val backUpToFileSystemLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
        onResult = { uri ->
            if (uri == null) {
                val result = BackupRestorePolicy.classifyBackupDocumentResult(
                    uriSelected = false,
                    writeSucceeded = false,
                )
                if (result != BackupDocumentResult.Cancelled) return@rememberLauncherForActivityResult
                // User can modify checkboxes between cancellation and second
                // trigger, so we make sure to clear out the previous workspace
                backupWorkspace?.close()
                backupWorkspace = null
                isBackupInProgress = false
                lastBackupNotice = BackupRestorePolicy.noticeForBackupDocumentResult(result)
                lastBackupErrorMessage = null
                return@rememberLauncherForActivityResult
            }
            runCatching {
                context.contentResolver.writeFromFile(uri, backupWorkspace!!.zipFile)
                backupWorkspace!!.close()
            }.onSuccess {
                backupWorkspace = null
                isBackupInProgress = false
                lastBackupNotice = BackupRestorePolicy.noticeForBackupDocumentResult(BackupDocumentResult.Success)
                lastBackupErrorMessage = null
                scope.launch {
                    context.showLongToast(R.string.backup_and_restore__back_up__success)
                    navController.popBackStack()
                }
            }.onFailure { error ->
                flogError { error.stackTraceToString() }
                scope.launch {
                    context.showLongToast(
                        R.string.backup_and_restore__back_up__failure,
                        "error_message" to error.message,
                    )
                }
                backupWorkspace?.close()
                backupWorkspace = null
                isBackupInProgress = false
                lastBackupNotice = BackupRestorePolicy.noticeForBackupDocumentResult(BackupDocumentResult.Failure)
                lastBackupErrorMessage = error.message
            }
        },
    )

    suspend fun prepareBackupWorkspace() {
        val workspace = cacheManager.backupAndRestore.new()
        try {
            if (backupFilesSelector.jetprefDatastore) {
                val fileBasedStorage = workspace.inputDir
                    .subDir(AndroidAppDataStorage.JETPREF_DIR_NAME)
                    .subFile("${FlorisPreferenceModel.NAME}.${AndroidAppDataStorage.JETPREF_FILE_EXT}")
                    .let { FileBasedStorage(it.path) }
                FlorisPreferenceStore.export(fileBasedStorage).getOrThrow()
            }
            val workspaceFilesDir = workspace.inputDir.subDir("files")
            if (backupFilesSelector.imeKeyboard) {
                context.filesDir.subDir(ExtensionManager.IME_KEYBOARD_PATH).let { dir ->
                    dir.copyRecursively(workspaceFilesDir.subDir(ExtensionManager.IME_KEYBOARD_PATH))
                }
            }
            if (backupFilesSelector.imeTheme) {
                context.filesDir.subDir(ExtensionManager.IME_THEME_PATH).let { dir ->
                    dir.copyRecursively(workspaceFilesDir.subDir(ExtensionManager.IME_THEME_PATH))
                }
            }

            if (backupFilesSelector.provideClipboardItems()) {
                val clipboardManager by context.clipboardManager()
                // Drop clipboard items the source app flagged as sensitive
                // (`ClipDescription.EXTRA_IS_SENSITIVE`, API 33+, also v1.8.105's
                // primary-clip gate now refuses to insert these — but legacy
                // history rows from before that fix can still carry the flag).
                // Backups are user-portable artifacts that move via Syncthing /
                // USB / cloud sync at the user's choice; passwords / OTPs / 2FA
                // codes that landed in history must not be serialised into the
                // backup zip in plaintext. The personal-dictionary backup is
                // passphrase-encrypted (v1.8.65); clipboard history is not, so
                // the only safe path is to exclude sensitive rows.
                val clipboardHistory = clipboardManager.currentHistory.all
                    .filterNot { it.isSensitive }
                val clipboardFilesDir = workspace.inputDir.subDir("clipboard")
                clipboardFilesDir.mkdir()
                if (backupFilesSelector.clipboardTextItems) {
                    clipboardFilesDir.subFile(Backup.CLIPBOARD_TEXT_ITEMS_JSON_NAME)
                        .writeJson(clipboardHistory.filter { it.type == ItemType.TEXT })
                }
                if (backupFilesSelector.clipboardImageItems) {
                    clipboardFilesDir.subFile(Backup.CLIPBOARD_IMAGES_JSON_NAME)
                        .writeJson(clipboardHistory.filter { it.type == ItemType.IMAGE })
                    for (item in clipboardHistory.filter { it.type == ItemType.IMAGE }) {
                        val id = ContentUris.parseId(item.uri!!)
                        ClipboardFileStorage.getFileForId(context, id).copyTo(
                            clipboardFilesDir.subFile("${ClipboardFileStorage.CLIPBOARD_FILES_PATH}/$id")
                        )
                    }
                }
                if (backupFilesSelector.clipboardVideoItems) {
                    clipboardFilesDir.subFile(Backup.CLIPBOARD_VIDEO_JSON_NAME)
                        .writeJson(clipboardHistory.filter { it.type == ItemType.VIDEO })
                    for (item in clipboardHistory.filter { it.type == ItemType.VIDEO }) {
                        val id = ContentUris.parseId(item.uri!!)
                        ClipboardFileStorage.getFileForId(context, id).copyTo(
                            clipboardFilesDir.subFile("${ClipboardFileStorage.CLIPBOARD_FILES_PATH}/$id")
                        )
                    }
                }
            }
            workspace.metadata = Backup.Metadata(
                packageName = BuildConfig.APPLICATION_ID,
                versionCode = BuildConfig.VERSION_CODE,
                versionName = BuildConfig.VERSION_NAME,
                timestamp = System.currentTimeMillis(),
            )
            workspace.inputDir.subFile(Backup.METADATA_JSON_NAME).writeJson(workspace.metadata)
            workspace.zipFile = workspace.outputDir.subFile(Backup.defaultFileName(workspace.metadata))
            ZipUtils.zip(workspace.inputDir, workspace.zipFile)
            backupWorkspace = workspace
        } catch (error: Throwable) {
            workspace.close()
            throw error
        }
    }

    suspend fun prepareAndPerformBackup() {
        if (isBackupInProgress) {
            return
        }
        isBackupInProgress = true
        lastBackupNotice = null
        lastBackupErrorMessage = null
        runCatching {
            if (backupWorkspace == null || backupWorkspace!!.isClosed()) {
                prepareBackupWorkspace()
            }
            when (backupDestination) {
                Backup.Destination.FILE_SYS -> {
                    backUpToFileSystemLauncher.launch(backupWorkspace!!.zipFile.name)
                }

                Backup.Destination.SHARE_INTENT -> {
                    val uri =
                        FileProvider.getUriForFile(context, Backup.FILE_PROVIDER_AUTHORITY, backupWorkspace!!.zipFile)
                    val shareIntent = ShareCompat.IntentBuilder(context)
                        .setStream(uri)
                        .setType(FileRegistry.BackupArchive.mediaType)
                        .createChooserIntent()
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(shareIntent)
                    isBackupInProgress = false
                    lastBackupNotice = BackupFlowNotice.ShareSheetOpened
                }
            }
        }.onFailure { error ->
            flogError { error.stackTraceToString() }
            context.showLongToast(R.string.backup_and_restore__back_up__failure, "error_message" to error.message)
            backupWorkspace?.close()
            backupWorkspace = null
            isBackupInProgress = false
            lastBackupNotice = BackupFlowNotice.Failure
            lastBackupErrorMessage = error.message
        }
    }

    bottomBar {
        FlorisButtonBar {
            ButtonBarSpacer()
            ButtonBarTextButton(
                onClick = {
                    backupWorkspace?.close()
                    navController.popBackStack()
                },
                text = stringRes(R.string.action__cancel),
            )
            ButtonBarButton(
                onClick = {
                    scope.launch { prepareAndPerformBackup() }
                },
                text = if (isBackupInProgress) {
                    stringRes(R.string.backup_and_restore__back_up__in_progress)
                } else {
                    stringRes(R.string.action__back_up)
                },
                enabled = BackupRestorePolicy.canStartBackup(
                    hasSelectedFiles = backupFilesSelector.atLeastOneSelected(),
                    isBackupInProgress = isBackupInProgress,
                ),
            )
        }
    }

    content {
        when (BackupRestorePolicy.resolveBackupFlowNotice(
            isBackupInProgress = isBackupInProgress,
            clipboardItemsSelected = backupFilesSelector.provideClipboardItems(),
            lastTerminalNotice = lastBackupNotice,
        )) {
            BackupFlowNotice.InProgress -> FlorisProgressCard(
                modifier = Modifier.padding(8.dp),
                text = stringRes(R.string.backup_and_restore__back_up__in_progress),
                secondaryText = stringRes(R.string.backup_and_restore__back_up__in_progress_summary),
            )
            BackupFlowNotice.ClipboardPrivacyWarning -> FlorisWarningCard(
                modifier = Modifier.padding(8.dp),
                text = stringRes(R.string.backup_and_restore__back_up__clipboard_privacy_warning_title),
                secondaryText = stringRes(R.string.backup_and_restore__back_up__clipboard_privacy_warning_summary),
            )
            BackupFlowNotice.Cancelled -> FlorisNeutralCard(
                modifier = Modifier.padding(8.dp),
                text = stringRes(R.string.backup_and_restore__back_up__cancelled),
                secondaryText = stringRes(R.string.backup_and_restore__back_up__cancelled_summary),
            )
            BackupFlowNotice.Failure -> FlorisErrorCard(
                modifier = Modifier.padding(8.dp),
                text = stringRes(R.string.backup_and_restore__back_up__failure_title),
                secondaryText = stringRes(
                    R.string.backup_and_restore__back_up__failure_recovery,
                    "error_message" to (lastBackupErrorMessage ?: stringRes(
                        R.string.backup_and_restore__back_up__unknown_error,
                    )),
                ),
            )
            BackupFlowNotice.ShareSheetOpened -> FlorisProgressCard(
                modifier = Modifier.padding(8.dp),
                text = stringRes(R.string.backup_and_restore__back_up__share_sheet_opened),
                secondaryText = stringRes(R.string.backup_and_restore__back_up__share_sheet_opened_summary),
            )
            BackupFlowNotice.Success,
            BackupFlowNotice.None,
            -> Unit
        }
        FlorisOutlinedBox(
            modifier = Modifier.defaultFlorisOutlinedBox(),
            title = stringRes(R.string.backup_and_restore__back_up__destination),
        ) {
            RadioListItem(
                onClick = {
                    backupDestination = Backup.Destination.FILE_SYS
                },
                selected = backupDestination == Backup.Destination.FILE_SYS,
                enabled = !isBackupInProgress,
                text = stringRes(R.string.backup_and_restore__back_up__destination_file_sys),
                secondaryText = stringRes(R.string.backup_and_restore__back_up__destination_file_sys_summary),
            )
            RadioListItem(
                onClick = {
                    backupDestination = Backup.Destination.SHARE_INTENT
                },
                selected = backupDestination == Backup.Destination.SHARE_INTENT,
                enabled = !isBackupInProgress,
                text = stringRes(R.string.backup_and_restore__back_up__destination_share_intent),
                secondaryText = stringRes(R.string.backup_and_restore__back_up__destination_share_intent_summary),
            )
        }
        BackupFilesSelector(
            filesSelector = backupFilesSelector,
            title = stringRes(R.string.backup_and_restore__back_up__files),
            enabled = !isBackupInProgress,
        )
    }
}

@Composable
internal fun BackupFilesSelector(
    modifier: Modifier = Modifier,
    filesSelector: Backup.FilesSelector,
    title: String,
    enabled: Boolean = true,
) {
    FlorisOutlinedBox(
        modifier = modifier.defaultFlorisOutlinedBox(),
        title = title,
    ) {
        CheckboxListItem(
            onClick = { filesSelector.jetprefDatastore = !filesSelector.jetprefDatastore },
            checked = filesSelector.jetprefDatastore,
            text = stringRes(R.string.backup_and_restore__back_up__files_jetpref_datastore),
            secondaryText = stringRes(R.string.backup_and_restore__back_up__files_jetpref_datastore_summary),
            enabled = enabled,
        )
        CheckboxListItem(
            onClick = { filesSelector.imeKeyboard = !filesSelector.imeKeyboard },
            checked = filesSelector.imeKeyboard,
            text = stringRes(R.string.backup_and_restore__back_up__files_ime_keyboard),
            secondaryText = stringRes(R.string.backup_and_restore__back_up__files_ime_keyboard_summary),
            enabled = enabled,
        )
        CheckboxListItem(
            onClick = { filesSelector.imeTheme = !filesSelector.imeTheme },
            checked = filesSelector.imeTheme,
            text = stringRes(R.string.backup_and_restore__back_up__files_ime_theme),
            secondaryText = stringRes(R.string.backup_and_restore__back_up__files_ime_theme_summary),
            enabled = enabled,
        )

        TriStateCheckboxListItem(
            onClick = {
                if (
                    filesSelector.clipboardData.value == ToggleableState.Off ||
                    filesSelector.clipboardData.value == ToggleableState.Indeterminate
                ) {
                    filesSelector.clipboardImageItems = true
                    filesSelector.clipboardVideoItems = true
                    filesSelector.clipboardTextItems = true
                } else {
                    filesSelector.clipboardImageItems = false
                    filesSelector.clipboardVideoItems = false
                    filesSelector.clipboardTextItems = false
                }
                filesSelector.updateCheckboxState()
            },
            state = filesSelector.clipboardData.value,
            text = stringRes(R.string.backup_and_restore__back_up__files_clipboard_history),
            secondaryText = stringRes(R.string.backup_and_restore__back_up__files_clipboard_history_summary),
            enabled = enabled,
        )


        CheckboxListItem(
            onClick = {
                filesSelector.clipboardTextItems = !filesSelector.clipboardTextItems
                filesSelector.updateCheckboxState()
            },
            checked = filesSelector.clipboardTextItems,
            text = stringRes(R.string.backup_and_restore__back_up__files_clipboard_history__clipboard_text_items),
            isSecondaryListItem = true,
            enabled = enabled,
        )
        CheckboxListItem(
            onClick = {
                filesSelector.clipboardImageItems = !filesSelector.clipboardImageItems
                filesSelector.updateCheckboxState()
            },
            checked = filesSelector.clipboardImageItems,
            text = stringRes(R.string.backup_and_restore__back_up__files_clipboard_history__clipboard_image_items),
            isSecondaryListItem = true,
            enabled = enabled,
        )
        CheckboxListItem(
            onClick = {
                filesSelector.clipboardVideoItems = !filesSelector.clipboardVideoItems
                filesSelector.updateCheckboxState()
            },
            checked = filesSelector.clipboardVideoItems,
            text = stringRes(R.string.backup_and_restore__back_up__files_clipboard_history__clipboard_video_items),
            isSecondaryListItem = true,
            enabled = enabled,
        )

    }
}

@Composable
internal fun CheckboxListItem(
    onClick: () -> Unit,
    checked: Boolean,
    text: String,
    secondaryText: String? = null,
    isSecondaryListItem: Boolean = false,
    enabled: Boolean = true,
) {
    JetPrefListItem(
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.56f)
            .rippleClickable(enabled = enabled, role = Role.Checkbox, onClick = onClick),
        icon = {
            Row {
                if (isSecondaryListItem) {
                    Spacer(modifier = Modifier.width(40.dp))
                }
                Checkbox(
                    checked = checked,
                    onCheckedChange = null,
                    enabled = enabled,
                )
            }
        },
        text = text,
        secondaryText = secondaryText,
    )
}

@Composable
internal fun TriStateCheckboxListItem(
    onClick: () -> Unit,
    state: ToggleableState,
    text: String,
    secondaryText: String? = null,
    isSecondaryListItem: Boolean = false,
    enabled: Boolean = true,
) {
    JetPrefListItem(
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.56f)
            .rippleClickable(enabled = enabled, role = Role.Checkbox, onClick = onClick),
        icon = {
            Row {
                if (isSecondaryListItem) {
                    Spacer(modifier = Modifier.width(40.dp))
                }
                TriStateCheckbox(
                    state = state,
                    onClick = null,
                    enabled = enabled,
                )
            }
        },
        text = text,
        secondaryText = secondaryText,
    )
}

@Composable
internal fun RadioListItem(
    onClick: () -> Unit,
    selected: Boolean,
    text: String,
    secondaryText: String? = null,
    enabled: Boolean = true,
) {
    JetPrefListItem(
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.56f)
            .rippleClickable(enabled = enabled, role = Role.RadioButton, onClick = onClick),
        icon = {
            RadioButton(
                selected = selected,
                onClick = null,
                enabled = enabled,
            )
        },
        text = text,
        secondaryText = secondaryText,
    )
}
