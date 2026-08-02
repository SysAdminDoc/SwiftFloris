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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
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
import dev.patrickgold.florisboard.ime.media.sticker.LocalStickerPackRepository
import dev.patrickgold.florisboard.lib.cache.CacheManager
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.devtools.flogError
import dev.patrickgold.florisboard.lib.ext.ExtensionManager
import dev.patrickgold.florisboard.lib.io.FileRegistry
import dev.patrickgold.florisboard.lib.io.ZipUtils
import dev.patrickgold.jetpref.datastore.runtime.AndroidAppDataStorage
import dev.patrickgold.jetpref.datastore.runtime.FileBasedStorage
import dev.patrickgold.jetpref.material.ui.JetPrefListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.florisboard.lib.android.showLongToast
import org.florisboard.lib.android.writeFromFile
import org.florisboard.lib.compose.FlorisButtonBar
import org.florisboard.lib.compose.FlorisErrorCard
import org.florisboard.lib.compose.FlorisInfoCard
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
    const val LEGACY_ARCHIVE_FORMAT_VERSION = 1
    const val CURRENT_ARCHIVE_FORMAT_VERSION = 2

    fun defaultFileName(
        metadata: Metadata,
        encrypted: Boolean = false,
    ): String {
        val extension = if (encrypted) {
            FileRegistry.EncryptedBackupArchive.fileExt
        } else {
            FileRegistry.BackupArchive.fileExt
        }
        return "backup_${metadata.packageName}_${metadata.versionCode}_${metadata.timestamp}.$extension"
    }

    enum class Destination {
        FILE_SYS,
        SHARE_INTENT;
    }

    class FilesSelector {
        var jetprefDatastore by mutableStateOf(true)
        var imeKeyboard by mutableStateOf(true)
        var imeTheme by mutableStateOf(true)
        var localStickerPacks by mutableStateOf(true)
        var snippets by mutableStateOf(true)
        var hardwareKeyboardLayouts by mutableStateOf(true)
        var customEmojiTags by mutableStateOf(true)
        var emojiPinGroups by mutableStateOf(true)
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
            return jetprefDatastore ||
                imeKeyboard ||
                imeTheme ||
                localStickerPacks ||
                snippets ||
                hardwareKeyboardLayouts ||
                customEmojiTags ||
                emojiPinGroups ||
                clipboardTextItems ||
                clipboardImageItems ||
                clipboardVideoItems
        }

        fun snapshot(): FilesSelection = FilesSelection(
            jetprefDatastore = jetprefDatastore,
            imeKeyboard = imeKeyboard,
            imeTheme = imeTheme,
            localStickerPacks = localStickerPacks,
            snippets = snippets,
            hardwareKeyboardLayouts = hardwareKeyboardLayouts,
            customEmojiTags = customEmojiTags,
            emojiPinGroups = emojiPinGroups,
            clipboardTextItems = clipboardTextItems,
            clipboardImageItems = clipboardImageItems,
            clipboardVideoItems = clipboardVideoItems,
        )

        /**
         * Selects every backup section — preferences, keyboard layouts, themes,
         * and all local clipboard items — so a single "Full backup" action
         * produces a complete archive without the user ticking each box. The
         * clipboard sections (off by default for privacy) are deliberately
         * included; the screen still surfaces the clipboard-privacy warning
         * once they are on.
         */
        fun selectAll() {
            jetprefDatastore = true
            imeKeyboard = true
            imeTheme = true
            localStickerPacks = true
            snippets = true
            hardwareKeyboardLayouts = true
            customEmojiTags = true
            emojiPinGroups = true
            clipboardTextItems = true
            clipboardImageItems = true
            clipboardVideoItems = true
            updateCheckboxState()
        }

        fun selectAvailableAdditionalStores(
            snippetsAvailable: Boolean,
            hardwareKeyboardLayoutsAvailable: Boolean,
            customEmojiTagsAvailable: Boolean,
            emojiPinGroupsAvailable: Boolean,
        ) {
            snippets = snippetsAvailable
            hardwareKeyboardLayouts = hardwareKeyboardLayoutsAvailable
            customEmojiTags = customEmojiTagsAvailable
            emojiPinGroups = emojiPinGroupsAvailable
        }

        companion object {
            val Saver = Saver<FilesSelector, ArrayList<Boolean>>(
                save = { selector ->
                    arrayListOf(
                        selector.jetprefDatastore,
                        selector.imeKeyboard,
                        selector.imeTheme,
                        selector.localStickerPacks,
                        selector.snippets,
                        selector.hardwareKeyboardLayouts,
                        selector.customEmojiTags,
                        selector.emojiPinGroups,
                        selector.clipboardTextItems,
                        selector.clipboardImageItems,
                        selector.clipboardVideoItems,
                    )
                },
                restore = { values ->
                    FilesSelector().apply {
                        if (values.size >= 7) {
                            jetprefDatastore = values[0]
                            imeKeyboard = values[1]
                            imeTheme = values[2]
                            localStickerPacks = values[3]
                            if (values.size >= 11) {
                                snippets = values[4]
                                hardwareKeyboardLayouts = values[5]
                                customEmojiTags = values[6]
                                emojiPinGroups = values[7]
                                clipboardTextItems = values[8]
                                clipboardImageItems = values[9]
                                clipboardVideoItems = values[10]
                            } else {
                                // State saved before the four portable stores
                                // were selectable. Keep their new safe defaults.
                                clipboardTextItems = values[4]
                                clipboardImageItems = values[5]
                                clipboardVideoItems = values[6]
                            }
                            updateCheckboxState()
                        }
                    }
                },
            )
        }
    }

    data class FilesSelection(
        val jetprefDatastore: Boolean,
        val imeKeyboard: Boolean,
        val imeTheme: Boolean,
        val localStickerPacks: Boolean,
        val snippets: Boolean,
        val hardwareKeyboardLayouts: Boolean,
        val customEmojiTags: Boolean,
        val emojiPinGroups: Boolean,
        val clipboardTextItems: Boolean,
        val clipboardImageItems: Boolean,
        val clipboardVideoItems: Boolean,
    ) {
        val containsClipboard: Boolean
            get() = clipboardTextItems || clipboardImageItems || clipboardVideoItems
    }

    @Serializable
    data class Metadata(
        @SerialName("package")
        val packageName: String,
        val versionCode: Int,
        val versionName: String,
        val timestamp: Long,
        /**
         * Portable archive schema version. A missing field is a v1 archive
         * written before the additional user-owned stores were carried.
         */
        val archiveVersion: Int = LEGACY_ARCHIVE_FORMAT_VERSION,
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
    var backupWorkspaceUuid by rememberSaveable { mutableStateOf<String?>(null) }
    var backupWorkspace by remember {
        mutableStateOf(
            backupWorkspaceUuid?.let(cacheManager.backupAndRestore::getWorkspaceByUuid),
        )
    }
    var isBackupInProgress by remember { mutableStateOf(backupWorkspace != null) }
    var lastBackupNotice by remember { mutableStateOf<BackupFlowNotice?>(null) }
    var lastBackupErrorMessage by remember { mutableStateOf<String?>(null) }
    var showEncryptionPassphraseDialog by remember { mutableStateOf(false) }

    fun setBackupWorkspace(workspace: CacheManager.BackupAndRestoreWorkspace?) {
        backupWorkspace = workspace
        backupWorkspaceUuid = workspace?.uuid
    }

    fun closeBackupWorkspace() {
        backupWorkspace?.close()
        setBackupWorkspace(null)
    }

    // Close screen-owned plaintext/ciphertext workspaces on every exit. Shared
    // artifacts transfer to CacheManager's bounded grant lease and are nulled
    // here, so onDispose never races a receiving app.
    val currentBackupWorkspace by rememberUpdatedState(backupWorkspace)
    val currentIsBackupInProgress by rememberUpdatedState(isBackupInProgress)
    DisposableEffect(Unit) {
        onDispose {
            if (!currentIsBackupInProgress) currentBackupWorkspace?.close()
        }
    }

    val backUpToFileSystemLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = { uri ->
            if (uri == null) {
                val result = BackupRestorePolicy.classifyBackupDocumentResult(
                    uriSelected = false,
                    writeSucceeded = false,
                )
                if (result != BackupDocumentResult.Cancelled) return@rememberLauncherForActivityResult
                // User can modify checkboxes between cancellation and second
                // trigger, so we make sure to clear out the previous workspace
                closeBackupWorkspace()
                isBackupInProgress = false
                lastBackupNotice = BackupRestorePolicy.noticeForBackupDocumentResult(result)
                lastBackupErrorMessage = null
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                val workspace = backupWorkspace
                runCatching {
                    checkNotNull(workspace) {
                        "Prepared backup workspace is no longer available."
                    }
                    withContext(Dispatchers.IO) {
                        context.contentResolver.writeFromFile(uri, workspace.archiveFile)
                    }
                    workspace.close()
                }.onSuccess {
                    setBackupWorkspace(null)
                    isBackupInProgress = false
                    lastBackupNotice =
                        BackupRestorePolicy.noticeForBackupDocumentResult(BackupDocumentResult.Success)
                    lastBackupErrorMessage = null
                    context.showLongToast(R.string.backup_and_restore__back_up__success)
                    navController.popBackStack()
                }.onFailure { error ->
                    flogError { error.stackTraceToString() }
                    context.showLongToast(
                        R.string.backup_and_restore__back_up__failure,
                        "error_message" to error.message,
                    )
                    closeBackupWorkspace()
                    isBackupInProgress = false
                    lastBackupNotice =
                        BackupRestorePolicy.noticeForBackupDocumentResult(BackupDocumentResult.Failure)
                    lastBackupErrorMessage = error.message
                }
            }
        },
    )

    suspend fun prepareBackupWorkspace(
        passphrase: CharArray?,
    ): CacheManager.BackupAndRestoreWorkspace {
        val selection = backupFilesSelector.snapshot()
        val clipboardHistory = if (selection.containsClipboard) {
            context.clipboardManager().value.snapshotHistoryForRestore()
                .filterNot { it.isSensitive }
        } else {
            emptyList()
        }
        val workspace = cacheManager.backupAndRestore.new()
        return try {
            withContext(Dispatchers.IO) {
                if (selection.jetprefDatastore) {
                    val fileBasedStorage = workspace.inputDir
                        .subDir(AndroidAppDataStorage.JETPREF_DIR_NAME)
                        .subFile("${FlorisPreferenceModel.NAME}.${AndroidAppDataStorage.JETPREF_FILE_EXT}")
                        .let { FileBasedStorage(it.path) }
                    FlorisPreferenceStore.export(fileBasedStorage).getOrThrow()
                }
                val workspaceFilesDir = workspace.inputDir.subDir("files")
                if (selection.imeKeyboard) {
                    context.filesDir.subDir(ExtensionManager.IME_KEYBOARD_PATH).let { dir ->
                        dir.copyRecursively(workspaceFilesDir.subDir(ExtensionManager.IME_KEYBOARD_PATH))
                    }
                }
                if (selection.imeTheme) {
                    context.filesDir.subDir(ExtensionManager.IME_THEME_PATH).let { dir ->
                        dir.copyRecursively(workspaceFilesDir.subDir(ExtensionManager.IME_THEME_PATH))
                    }
                }
                if (selection.localStickerPacks) {
                    val stickerDir = LocalStickerPackRepository.storageDir(context)
                    if (stickerDir.exists()) {
                        stickerDir.copyRecursively(
                            workspaceFilesDir.subDir(LocalStickerPackRepository.StorageDirName),
                            overwrite = true,
                        )
                    }
                }
                if (selection.snippets) {
                    val snippetsDir = context.filesDir.subDir(BackupArchiveStores.SnippetsDirName)
                    if (snippetsDir.exists()) {
                        BackupArchiveStores.copyDirectory(
                            snippetsDir,
                            workspaceFilesDir.subDir(BackupArchiveStores.SnippetsDirName),
                        )
                    }
                }
                if (selection.hardwareKeyboardLayouts) {
                    val layoutFile = context.filesDir.subFile(
                        BackupArchiveStores.HardwareKeyboardLayoutFileName,
                    )
                    if (layoutFile.isFile) {
                        BackupArchiveStores.copyFile(
                            layoutFile,
                            workspaceFilesDir.subFile(
                                BackupArchiveStores.HardwareKeyboardLayoutFileName,
                            ),
                        )
                    }
                }
                if (selection.customEmojiTags) {
                    val tagFile = context.filesDir.subFile(
                        BackupArchiveStores.CustomEmojiTagsFileName,
                    )
                    if (tagFile.isFile) {
                        BackupArchiveStores.copyFile(
                            tagFile,
                            workspaceFilesDir.subFile(
                                BackupArchiveStores.CustomEmojiTagsFileName,
                            ),
                        )
                    }
                }
                if (selection.emojiPinGroups) {
                    val pinGroupFile = context.filesDir.subFile(
                        BackupArchiveStores.EmojiPinGroupsFileName,
                    )
                    if (pinGroupFile.isFile) {
                        BackupArchiveStores.copyFile(
                            pinGroupFile,
                            workspaceFilesDir.subFile(
                                BackupArchiveStores.EmojiPinGroupsFileName,
                            ),
                        )
                    }
                }

                if (BackupRestorePolicy.requiresPortableEncryption(selection.containsClipboard)) {
                    // Sensitive rows are excluded before the app-private ZIP is
                    // built. Every remaining clipboard byte is then sealed by
                    // PortableBackupEnvelope before any SAF/share exposure.
                    val clipboardFilesDir = workspace.inputDir.subDir("clipboard")
                    clipboardFilesDir.mkdir()
                    if (selection.clipboardTextItems) {
                        clipboardFilesDir.subFile(Backup.CLIPBOARD_TEXT_ITEMS_JSON_NAME)
                            .writeJson(clipboardHistory.filter { it.type == ItemType.TEXT })
                    }
                    if (selection.clipboardImageItems) {
                        clipboardFilesDir.subFile(Backup.CLIPBOARD_IMAGES_JSON_NAME)
                            .writeJson(clipboardHistory.filter { it.type == ItemType.IMAGE })
                        for (item in clipboardHistory.filter { it.type == ItemType.IMAGE }) {
                            val uri = item.uri ?: continue
                            val id = ContentUris.parseId(uri)
                            ClipboardFileStorage.copyDecryptedTo(
                                context = context,
                                id = id,
                                target = clipboardFilesDir.subFile(
                                    "${ClipboardFileStorage.CLIPBOARD_FILES_PATH}/$id",
                                ),
                                mediaKind = ClipboardFileStorage.MediaKind.IMAGE,
                            )
                        }
                    }
                    if (selection.clipboardVideoItems) {
                        clipboardFilesDir.subFile(Backup.CLIPBOARD_VIDEO_JSON_NAME)
                            .writeJson(clipboardHistory.filter { it.type == ItemType.VIDEO })
                        for (item in clipboardHistory.filter { it.type == ItemType.VIDEO }) {
                            val uri = item.uri ?: continue
                            val id = ContentUris.parseId(uri)
                            ClipboardFileStorage.copyDecryptedTo(
                                context = context,
                                id = id,
                                target = clipboardFilesDir.subFile(
                                    "${ClipboardFileStorage.CLIPBOARD_FILES_PATH}/$id",
                                ),
                                mediaKind = ClipboardFileStorage.MediaKind.VIDEO,
                            )
                        }
                    }
                }
                workspace.metadata = Backup.Metadata(
                    packageName = BuildConfig.APPLICATION_ID,
                    versionCode = BuildConfig.VERSION_CODE,
                    versionName = BuildConfig.VERSION_NAME,
                    timestamp = System.currentTimeMillis(),
                    archiveVersion = Backup.CURRENT_ARCHIVE_FORMAT_VERSION,
                )
                workspace.inputDir.subFile(Backup.METADATA_JSON_NAME).writeJson(workspace.metadata)
                val plaintextZip = workspace.outputDir.subFile(
                    Backup.defaultFileName(workspace.metadata),
                )
                ZipUtils.zip(workspace.inputDir, plaintextZip)
                if (selection.containsClipboard) {
                    requireNotNull(passphrase) {
                        "Clipboard-bearing backups require a passphrase."
                    }
                    val encryptedArchive = workspace.outputDir.subFile(
                        Backup.defaultFileName(workspace.metadata, encrypted = true),
                    )
                    PortableBackupEnvelope.encrypt(
                        plaintextZip = plaintextZip,
                        encryptedTarget = encryptedArchive,
                        passphrase = passphrase,
                        containsClipboard = true,
                    )
                    check(plaintextZip.delete()) {
                        "Could not remove the app-private plaintext backup ZIP."
                    }
                    check(workspace.inputDir.deleteRecursively()) {
                        "Could not remove the app-private plaintext backup workspace."
                    }
                    workspace.archiveFile = encryptedArchive
                } else {
                    workspace.archiveFile = plaintextZip
                }
                workspace
            }
        } catch (error: Throwable) {
            // withContext has prompt cancellation: ownership may fail to
            // transfer even after its IO block returned successfully.
            workspace.close()
            throw error
        }
    }

    suspend fun prepareAndPerformBackup(passphrase: CharArray? = null) {
        if (isBackupInProgress) {
            passphrase?.fill('\u0000')
            return
        }
        isBackupInProgress = true
        lastBackupNotice = null
        lastBackupErrorMessage = null
        try {
            runCatching {
                if (backupWorkspace == null || backupWorkspace!!.isClosed()) {
                    setBackupWorkspace(prepareBackupWorkspace(passphrase))
                }
                when (backupDestination) {
                    Backup.Destination.FILE_SYS -> {
                        backUpToFileSystemLauncher.launch(backupWorkspace!!.archiveFile.name)
                    }

                    Backup.Destination.SHARE_INTENT -> {
                        val workspace = backupWorkspace!!
                        val uri = FileProvider.getUriForFile(
                            context,
                            Backup.FILE_PROVIDER_AUTHORITY,
                            workspace.archiveFile,
                        )
                        val shareIntent = ShareCompat.IntentBuilder(context)
                            .setStream(uri)
                            .setType(
                                if (PortableBackupEnvelope.isEncryptedEnvelope(workspace.archiveFile)) {
                                    FileRegistry.EncryptedBackupArchive.mediaType
                                } else {
                                    FileRegistry.BackupArchive.mediaType
                                },
                            )
                            .createChooserIntent()
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(shareIntent)
                        cacheManager.leaseSharedBackupArtifact(workspace, uri)
                        setBackupWorkspace(null)
                        isBackupInProgress = false
                        lastBackupNotice = BackupFlowNotice.ShareSheetOpened
                    }
                }
            }.onFailure { error ->
                flogError { error.stackTraceToString() }
                context.showLongToast(
                    R.string.backup_and_restore__back_up__failure,
                    "error_message" to error.message,
                )
                closeBackupWorkspace()
                isBackupInProgress = false
                lastBackupNotice = BackupFlowNotice.Failure
                lastBackupErrorMessage = error.message
            }
        } finally {
            passphrase?.fill('\u0000')
        }
    }

    fun requestBackup() {
        if (BackupRestorePolicy.requiresPortableEncryption(
                backupFilesSelector.provideClipboardItems(),
            )
        ) {
            showEncryptionPassphraseDialog = true
        } else {
            scope.launch { prepareAndPerformBackup() }
        }
    }

    if (showEncryptionPassphraseDialog) {
        BackupPassphraseDialog(
            title = stringRes(R.string.backup_and_restore__back_up__encryption_dialog_title),
            message = stringRes(R.string.backup_and_restore__back_up__encryption_dialog_message),
            confirmLabel = stringRes(R.string.action__back_up),
            requireConfirmation = true,
            onDismiss = {
                showEncryptionPassphraseDialog = false
                lastBackupNotice = BackupFlowNotice.Cancelled
                lastBackupErrorMessage = null
            },
            onConfirm = { passphrase ->
                showEncryptionPassphraseDialog = false
                scope.launch { prepareAndPerformBackup(passphrase) }
            },
        )
    }

    bottomBar {
        FlorisButtonBar {
            ButtonBarSpacer()
            ButtonBarTextButton(
                // Disabled while a backup is running: close() deletes the workspace
                // directory, which would otherwise be torn down from under the
                // in-flight zip/copy coroutine (mirrors RestoreScreen's guard).
                enabled = !isBackupInProgress,
                onClick = {
                    closeBackupWorkspace()
                    navController.popBackStack()
                },
                text = stringRes(R.string.action__cancel),
            )
            ButtonBarTextButton(
                // One-tap full backup: tick every section then run the standard
                // file-system backup. Discard any partially-prepared workspace
                // first so prepareAndPerformBackup() rebuilds it against the
                // now-complete selection (it only rebuilds when null/closed).
                enabled = !isBackupInProgress,
                onClick = {
                    closeBackupWorkspace()
                    backupFilesSelector.selectAll()
                    backupDestination = Backup.Destination.FILE_SYS
                    requestBackup()
                },
                text = stringRes(R.string.backup_and_restore__back_up__full_backup),
            )
            ButtonBarButton(
                onClick = {
                    requestBackup()
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
        FlorisInfoCard(
            modifier = Modifier.padding(8.dp),
            text = stringRes(R.string.backup_and_restore__back_up__privacy_title),
            secondaryText = stringRes(R.string.backup_and_restore__back_up__privacy_summary),
        )
        // An archive is not a full device image. Name what it leaves behind, from the one
        // inventory that also drives Android's own backup rules, rather than implying coverage.
        FlorisInfoCard(
            modifier = Modifier.padding(8.dp),
            text = stringRes(R.string.backup_and_restore__back_up__coverage_title),
            secondaryText = stringRes(R.string.backup_and_restore__back_up__coverage_summary),
        )
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
        CheckboxListItem(
            onClick = { filesSelector.localStickerPacks = !filesSelector.localStickerPacks },
            checked = filesSelector.localStickerPacks,
            text = stringRes(R.string.backup_and_restore__back_up__files_local_stickers),
            secondaryText = stringRes(R.string.backup_and_restore__back_up__files_local_stickers_summary),
            enabled = enabled,
        )
        CheckboxListItem(
            onClick = { filesSelector.snippets = !filesSelector.snippets },
            checked = filesSelector.snippets,
            text = stringRes(R.string.backup_and_restore__back_up__files_snippets),
            secondaryText = stringRes(R.string.backup_and_restore__back_up__files_snippets_summary),
            enabled = enabled,
        )
        CheckboxListItem(
            onClick = { filesSelector.hardwareKeyboardLayouts = !filesSelector.hardwareKeyboardLayouts },
            checked = filesSelector.hardwareKeyboardLayouts,
            text = stringRes(R.string.backup_and_restore__back_up__files_hardware_keyboard_layouts),
            secondaryText = stringRes(
                R.string.backup_and_restore__back_up__files_hardware_keyboard_layouts_summary,
            ),
            enabled = enabled,
        )
        CheckboxListItem(
            onClick = { filesSelector.customEmojiTags = !filesSelector.customEmojiTags },
            checked = filesSelector.customEmojiTags,
            text = stringRes(R.string.backup_and_restore__back_up__files_custom_emoji_tags),
            secondaryText = stringRes(
                R.string.backup_and_restore__back_up__files_custom_emoji_tags_summary,
            ),
            enabled = enabled,
        )
        CheckboxListItem(
            onClick = { filesSelector.emojiPinGroups = !filesSelector.emojiPinGroups },
            checked = filesSelector.emojiPinGroups,
            text = stringRes(R.string.backup_and_restore__back_up__files_emoji_pin_groups),
            secondaryText = stringRes(
                R.string.backup_and_restore__back_up__files_emoji_pin_groups_summary,
            ),
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
