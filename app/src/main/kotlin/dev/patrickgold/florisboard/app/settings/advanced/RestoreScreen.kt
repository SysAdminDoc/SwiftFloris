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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
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
import dev.patrickgold.florisboard.app.findActivity
import dev.patrickgold.florisboard.cacheManager
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.ime.clipboard.ClipboardRestoredFileInfo
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardFileStorage
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardFileInfo
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardFilesDatabase
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardMediaProvider
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import dev.patrickgold.florisboard.ime.media.emoji.CustomEmojiTagStore
import dev.patrickgold.florisboard.ime.media.emoji.EmojiPinGroupStore
import dev.patrickgold.florisboard.ime.media.sticker.LocalStickerPackRepository
import dev.patrickgold.florisboard.ime.media.sticker.evictStickerBitmapCache
import dev.patrickgold.florisboard.ime.input.KeypressSoundStore
import dev.patrickgold.florisboard.snippetManager
import dev.patrickgold.florisboard.lib.cache.CacheManager
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.devtools.flogError
import dev.patrickgold.florisboard.lib.ext.ExtensionManager
import dev.patrickgold.florisboard.lib.io.ZipUtils
import dev.patrickgold.jetpref.datastore.runtime.AndroidAppDataStorage
import dev.patrickgold.jetpref.datastore.runtime.FileBasedStorage
import dev.patrickgold.jetpref.datastore.runtime.ImportStrategy
import dev.patrickgold.florisboard.app.settings.search.Preference
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import java.io.FileNotFoundException
import java.text.DateFormat
import java.util.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
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
import org.florisboard.lib.compose.pluralsRes
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
    const val BACKUP_ARCHIVE_FILE_NAME = "backup.archive"
    const val DECRYPTED_ARCHIVE_FILE_NAME = "authenticated-backup.zip"
    const val MAX_ARCHIVE_BYTES = 256L * 1024L * 1024L
    const val MAX_PORTABLE_ARCHIVE_BYTES =
        MAX_ARCHIVE_BYTES + PortableBackupEnvelope.HeaderBytes + PortableBackupEnvelope.GcmTagBytes
}

private val RestoreOperationSummarySaver = Saver<RestoreOperationSummary?, ArrayList<String>>(
    save = { summary ->
        if (summary == null) {
            arrayListOf()
        } else {
            arrayListOf(
                summary.selectedSections.toString(),
                summary.restoredSections.toString(),
                summary.missingSections.toString(),
                summary.failedSections.toString(),
                summary.firstFailureMessage.orEmpty(),
            )
        }
    },
    restore = { values ->
        if (values.size == 5) {
            RestoreOperationSummary(
                selectedSections = values[0].toIntOrNull() ?: 0,
                restoredSections = values[1].toIntOrNull() ?: 0,
                missingSections = values[2].toIntOrNull() ?: 0,
                failedSections = values[3].toIntOrNull() ?: 0,
                firstFailureMessage = values[4].takeIf { it.isNotBlank() },
            )
        } else {
            null
        }
    },
)

@Composable
fun RestoreScreen() = FlorisScreen {
    title = stringRes(R.string.backup_and_restore__restore__title)
    previewFieldVisible = false

    val navController = LocalNavController.current
    val context = LocalContext.current
    val activity = context.findActivity()
    val cacheManager by context.cacheManager()

    val restoreFilesSelector = rememberSaveable(saver = Backup.FilesSelector.Saver) {
        Backup.FilesSelector()
    }
    var importStrategy by rememberSaveable { mutableStateOf(ImportStrategy.Merge) }
    val restoreScope = rememberCoroutineScope()
    var isRestoreInProgress by remember { mutableStateOf(false) }
    var restoreWorkspaceUuid by rememberSaveable { mutableStateOf<String?>(null) }
    var restoreWorkspace by remember {
        mutableStateOf(
            restoreWorkspaceUuid?.let { uuid ->
                cacheManager.backupAndRestore.getWorkspaceByUuid(uuid)
            },
        )
    }
    var pendingEncryptedWorkspace by remember {
        mutableStateOf<CacheManager.BackupAndRestoreWorkspace?>(null)
    }
    var showRestorePassphraseDialog by remember { mutableStateOf(false) }
    var showEraseRestoreConfirmation by rememberSaveable { mutableStateOf(false) }
    var lastRestoreNotice by rememberSaveable { mutableStateOf<RestoreFlowNotice?>(null) }
    var lastRestoreErrorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var lastRestoreRolledBack by rememberSaveable { mutableStateOf(false) }
    var lastRestoreSummary by rememberSaveable(stateSaver = RestoreOperationSummarySaver) {
        mutableStateOf<RestoreOperationSummary?>(null)
    }

    fun setRestoreWorkspace(workspace: CacheManager.BackupAndRestoreWorkspace?) {
        restoreWorkspace = workspace
        restoreWorkspaceUuid = workspace?.uuid
    }

    fun closeRestoreWorkspace() {
        restoreWorkspace?.close()
        pendingEncryptedWorkspace?.close()
        pendingEncryptedWorkspace = null
        showRestorePassphraseDialog = false
        setRestoreWorkspace(null)
    }
    val unknownRestoreError = stringRes(R.string.backup_and_restore__restore__unknown_error)
    val badBackupPassphrase = stringRes(R.string.backup_and_restore__restore__bad_passphrase)
    val unsupportedBackupEnvelope = stringRes(
        R.string.backup_and_restore__restore__unsupported_envelope,
    )
    val rollbackFailureError = stringRes(
        R.string.backup_and_restore__restore__rollback_failure,
    )

    fun restoreArchiveErrorMessage(error: Throwable): String {
        val envelopeError = error as? PortableBackupEnvelopeException
        return when (envelopeError?.reason) {
            PortableBackupEnvelope.FailureReason.BadPassphraseOrTampered,
            PortableBackupEnvelope.FailureReason.CorruptHeader,
            PortableBackupEnvelope.FailureReason.NotAnEnvelope,
            PortableBackupEnvelope.FailureReason.Oversized,
            PortableBackupEnvelope.FailureReason.Truncated,
            -> badBackupPassphrase
            PortableBackupEnvelope.FailureReason.UnsupportedVersion -> unsupportedBackupEnvelope
            null -> BackupRestorePolicy.restoreErrorMessage(error, unknownRestoreError)
        }
    }

    // Close the workspace when the screen leaves composition (system-back / nav-up),
    // not only via the Cancel button. prepareRestoreWorkspace extracts the archive —
    // including clipboard plaintext and the decrypted jetpref datastore — into the
    // cache dir; without this, leaving any other way leaves that plaintext on disk.
    // rememberUpdatedState so onDispose sees the latest workspace/flag. Rotation
    // keeps the workspace alive for UUID re-attachment; real exits still delete
    // it, and we skip closing mid-restore so we don't pull the dir out from
    // under an in-flight copy.
    val currentRestoreWorkspace by rememberUpdatedState(restoreWorkspace)
    val currentPendingEncryptedWorkspace by rememberUpdatedState(pendingEncryptedWorkspace)
    val currentIsRestoreInProgress by rememberUpdatedState(isRestoreInProgress)
    val currentActivity by rememberUpdatedState(activity)
    DisposableEffect(activity) {
        onDispose {
            val isConfigurationChange = currentActivity?.isChangingConfigurations == true
            if (!isConfigurationChange && !currentIsRestoreInProgress) {
                currentRestoreWorkspace?.close()
            }
            // The passphrase is deliberately not saveable, so an encrypted
            // archive awaiting it cannot survive recreation safely.
            currentPendingEncryptedWorkspace?.close()
        }
    }

    suspend fun copyRestoreArchive(uri: Uri): CacheManager.BackupAndRestoreWorkspace {
        val workspace = cacheManager.backupAndRestore.new()
        return try {
            withContext(Dispatchers.IO) {
                workspace.archiveFile = workspace.inputDir.subFile(Restore.BACKUP_ARCHIVE_FILE_NAME)
                context.contentResolver.readToFile(
                    uri,
                    workspace.archiveFile,
                    Restore.MAX_PORTABLE_ARCHIVE_BYTES,
                )
            }
            workspace
        } catch (error: Throwable) {
            // Keep ownership outside withContext so prompt cancellation cannot
            // strand a copied archive after its IO block returns.
            workspace.close()
            throw error
        }
    }

    suspend fun prepareRestoreWorkspace(
        workspace: CacheManager.BackupAndRestoreWorkspace,
        passphrase: CharArray? = null,
    ): CacheManager.BackupAndRestoreWorkspace = withContext(Dispatchers.IO) {
        try {
            val encrypted = PortableBackupEnvelope.isEncryptedEnvelope(workspace.archiveFile)
            workspace.archiveWasEncrypted = encrypted
            workspace.archiveWasLegacyPlaintext = !encrypted
            val zipFile = if (encrypted) {
                requireNotNull(passphrase) { "Encrypted backup requires a passphrase." }
                workspace.inputDir.subFile(Restore.DECRYPTED_ARCHIVE_FILE_NAME).also { decrypted ->
                    PortableBackupEnvelope.decrypt(
                        encryptedSource = workspace.archiveFile,
                        plaintextTarget = decrypted,
                        passphrase = passphrase,
                    )
                }
            } else {
                workspace.archiveFile
            }
            try {
                ZipUtils.unzip(zipFile, workspace.outputDir)
            } finally {
                if (encrypted) {
                    check(zipFile.delete()) {
                        "Could not remove the authenticated plaintext backup ZIP."
                    }
                }
            }
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
                    hasKeypressSounds = workspaceFilesDir
                        .subDir(BackupArchiveStores.KeypressSoundsDirName)
                        .exists(),
                    hasImeTheme = workspaceFilesDir.subDir(ExtensionManager.IME_THEME_PATH).exists(),
                    hasLocalStickerPacks = workspaceFilesDir
                        .subDir(LocalStickerPackRepository.StorageDirName)
                        .exists(),
                    hasSnippets = workspaceFilesDir
                        .subDir(BackupArchiveStores.SnippetsDirName)
                        .exists(),
                    hasHardwareKeyboardLayouts = workspaceFilesDir
                        .subFile(BackupArchiveStores.HardwareKeyboardLayoutFileName)
                        .isFile,
                    hasCustomEmojiTags = workspaceFilesDir
                        .subFile(BackupArchiveStores.CustomEmojiTagsFileName)
                        .isFile,
                    hasEmojiPinGroups = workspaceFilesDir
                        .subFile(BackupArchiveStores.EmojiPinGroupsFileName)
                        .isFile,
                    hasClipboardTextItems = clipboardFilesDir.subFile(Backup.CLIPBOARD_TEXT_ITEMS_JSON_NAME).exists(),
                    hasClipboardImageItems = clipboardFilesDir.subFile(Backup.CLIPBOARD_IMAGES_JSON_NAME).exists(),
                    hasClipboardVideoItems = clipboardFilesDir.subFile(Backup.CLIPBOARD_VIDEO_JSON_NAME).exists(),
                ),
            )
            workspace.restoreWarningId = validation.warningId ?: if (workspace.archiveWasLegacyPlaintext) {
                R.string.backup_and_restore__restore__metadata_warn_legacy_plaintext
            } else {
                null
            }
            workspace.restoreErrorId = validation.errorId
            workspace
        } catch (error: Throwable) {
            workspace.close()
            throw error
        }
    }

    fun syncAdditionalRestoreSelection(
        workspace: CacheManager.BackupAndRestoreWorkspace,
    ) {
        val workspaceFilesDir = workspace.outputDir.subDir("files")
        restoreFilesSelector.selectAvailableAdditionalStores(
            keypressSoundsAvailable = workspaceFilesDir
                .subDir(BackupArchiveStores.KeypressSoundsDirName)
                .exists(),
            snippetsAvailable = workspaceFilesDir
                .subDir(BackupArchiveStores.SnippetsDirName)
                .exists(),
            hardwareKeyboardLayoutsAvailable = workspaceFilesDir
                .subFile(BackupArchiveStores.HardwareKeyboardLayoutFileName)
                .isFile,
            customEmojiTagsAvailable = workspaceFilesDir
                .subFile(BackupArchiveStores.CustomEmojiTagsFileName)
                .isFile,
            emojiPinGroupsAvailable = workspaceFilesDir
                .subFile(BackupArchiveStores.EmojiPinGroupsFileName)
                .isFile,
        )
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
                var copiedWorkspace: CacheManager.BackupAndRestoreWorkspace? = null
                try {
                    closeRestoreWorkspace()
                    copiedWorkspace = copyRestoreArchive(uri)
                    if (PortableBackupEnvelope.isEncryptedEnvelope(copiedWorkspace.archiveFile)) {
                        // Reject malformed/future headers before asking for a
                        // credential, but do not decrypt or mutate live state.
                        PortableBackupEnvelope.inspect(copiedWorkspace.archiveFile)
                        pendingEncryptedWorkspace = copiedWorkspace
                        copiedWorkspace = null
                        showRestorePassphraseDialog = true
                    } else {
                        val readyWorkspace = prepareRestoreWorkspace(copiedWorkspace)
                        copiedWorkspace = null
                        syncAdditionalRestoreSelection(readyWorkspace)
                        setRestoreWorkspace(readyWorkspace)
                    }
                } catch (error: Throwable) {
                    copiedWorkspace?.close()
                    flogError { error.stackTraceToString() }
                    val errorMessage = restoreArchiveErrorMessage(error)
                    context.showLongToast(
                        R.string.backup_and_restore__restore__failure,
                        "error_message" to errorMessage,
                    )
                    lastRestoreNotice = RestoreFlowNotice.Failure
                    lastRestoreErrorMessage = errorMessage
                } finally {
                    isRestoreInProgress = false
                }
            }
        },
    )

    if (showRestorePassphraseDialog) {
        BackupPassphraseDialog(
            title = stringRes(R.string.backup_and_restore__restore__encryption_dialog_title),
            message = stringRes(R.string.backup_and_restore__restore__encryption_dialog_message),
            confirmLabel = stringRes(R.string.action__restore),
            requireConfirmation = false,
            onDismiss = {
                showRestorePassphraseDialog = false
                pendingEncryptedWorkspace?.close()
                pendingEncryptedWorkspace = null
                lastRestoreNotice = RestoreFlowNotice.Cancelled
                lastRestoreErrorMessage = null
            },
            onConfirm = { passphrase ->
                showRestorePassphraseDialog = false
                val encryptedWorkspace = pendingEncryptedWorkspace
                pendingEncryptedWorkspace = null
                if (encryptedWorkspace == null) {
                    passphrase.fill('\u0000')
                    lastRestoreNotice = RestoreFlowNotice.Failure
                    lastRestoreErrorMessage = unknownRestoreError
                } else {
                    restoreScope.launch {
                        if (isRestoreInProgress) {
                            passphrase.fill('\u0000')
                            encryptedWorkspace.close()
                            return@launch
                        }
                        isRestoreInProgress = true
                        lastRestoreNotice = null
                        lastRestoreErrorMessage = null
                        try {
                            val readyWorkspace = prepareRestoreWorkspace(
                                workspace = encryptedWorkspace,
                                passphrase = passphrase,
                            )
                            syncAdditionalRestoreSelection(readyWorkspace)
                            setRestoreWorkspace(readyWorkspace)
                        } catch (error: Throwable) {
                            flogError { error.stackTraceToString() }
                            encryptedWorkspace.close()
                            val errorMessage = restoreArchiveErrorMessage(error)
                            lastRestoreNotice = RestoreFlowNotice.Failure
                            lastRestoreErrorMessage = errorMessage
                            context.showLongToast(
                                R.string.backup_and_restore__restore__failure,
                                "error_message" to errorMessage,
                            )
                        } finally {
                            passphrase.fill('\u0000')
                            isRestoreInProgress = false
                        }
                    }
                }
            },
        )
    }

    suspend fun performRestore(
        selection: Backup.FilesSelection,
        strategy: ImportStrategy,
    ): RestoreOperationSummary {
        val workspace = restoreWorkspace!!
        val shouldReset = strategy == ImportStrategy.Erase
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
            try {
                block()
                markRestored()
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
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

        if (selection.jetprefDatastore) {
            val file = workspace.outputDir
                .subDir(AndroidAppDataStorage.JETPREF_DIR_NAME)
                .subFile("${FlorisPreferenceModel.NAME}.${AndroidAppDataStorage.JETPREF_FILE_EXT}")
            restoreSelectedSection(sourceExists = file.exists()) {
                val fileBasedStorage = FileBasedStorage(file.path)
                FlorisPreferenceStore.import(strategy, fileBasedStorage).getOrThrow()
            }
        }
        val workspaceFilesDir = workspace.outputDir.subDir("files")
        if (selection.imeKeyboard) {
            val srcDir = workspaceFilesDir.subDir(ExtensionManager.IME_KEYBOARD_PATH)
            val dstDir = context.filesDir.subDir(ExtensionManager.IME_KEYBOARD_PATH)
            restoreSelectedSection(sourceExists = srcDir.exists()) {
                if (shouldReset) {
                    dstDir.deleteContentsRecursively()
                }
                srcDir.copyRecursively(dstDir, overwrite = true)
            }
        }
        if (selection.keypressSounds) {
            val srcDir = workspaceFilesDir.subDir(BackupArchiveStores.KeypressSoundsDirName)
            val dstDir = context.filesDir.subDir(BackupArchiveStores.KeypressSoundsDirName)
            restoreSelectedSection(sourceExists = srcDir.exists()) {
                if (shouldReset) {
                    dstDir.deleteRecursively()
                }
                BackupArchiveStores.copyDirectory(srcDir, dstDir)
                // This writes the sound directory behind KeypressSoundStore's
                // back, and a running keyboard holds each sample in a SoundPool
                // by handle. Without this it would keep playing the samples the
                // restore just replaced.
                KeypressSoundStore.revision.incrementAndGet()
            }
        }
        if (selection.imeTheme) {
            val srcDir = workspaceFilesDir.subDir(ExtensionManager.IME_THEME_PATH)
            val dstDir = context.filesDir.subDir(ExtensionManager.IME_THEME_PATH)
            restoreSelectedSection(sourceExists = srcDir.exists()) {
                if (shouldReset) {
                    dstDir.deleteContentsRecursively()
                }
                srcDir.copyRecursively(dstDir, overwrite = true)
            }
        }
        if (selection.localStickerPacks) {
            val srcDir = workspaceFilesDir.subDir(LocalStickerPackRepository.StorageDirName)
            val dstDir = LocalStickerPackRepository.storageDir(context)
            restoreSelectedSection(sourceExists = srcDir.exists()) {
                if (shouldReset) {
                    dstDir.deleteRecursively()
                }
                srcDir.copyRecursively(dstDir, overwrite = true)
                evictStickerBitmapCache()
            }
        }
        if (selection.snippets) {
            val srcDir = workspaceFilesDir.subDir(BackupArchiveStores.SnippetsDirName)
            val dstDir = context.filesDir.subDir(BackupArchiveStores.SnippetsDirName)
            restoreSelectedSection(sourceExists = srcDir.exists()) {
                if (shouldReset) {
                    dstDir.deleteRecursively()
                }
                BackupArchiveStores.copyDirectory(srcDir, dstDir)
                context.snippetManager().value.loadAll()
            }
        }
        if (selection.hardwareKeyboardLayouts) {
            val srcFile = workspaceFilesDir.subFile(BackupArchiveStores.HardwareKeyboardLayoutFileName)
            val dstFile = context.filesDir.subFile(BackupArchiveStores.HardwareKeyboardLayoutFileName)
            restoreSelectedSection(sourceExists = srcFile.isFile) {
                if (shouldReset) {
                    dstFile.delete()
                }
                BackupArchiveStores.copyFile(srcFile, dstFile)
            }
        }
        if (selection.customEmojiTags) {
            val srcFile = workspaceFilesDir.subFile(BackupArchiveStores.CustomEmojiTagsFileName)
            val dstFile = context.filesDir.subFile(BackupArchiveStores.CustomEmojiTagsFileName)
            restoreSelectedSection(sourceExists = srcFile.isFile) {
                if (shouldReset) {
                    dstFile.delete()
                }
                BackupArchiveStores.copyFile(srcFile, dstFile)
                CustomEmojiTagStore.get(context).reload()
            }
        }
        if (selection.emojiPinGroups) {
            val srcFile = workspaceFilesDir.subFile(BackupArchiveStores.EmojiPinGroupsFileName)
            val dstFile = context.filesDir.subFile(BackupArchiveStores.EmojiPinGroupsFileName)
            restoreSelectedSection(sourceExists = srcFile.isFile) {
                if (shouldReset) {
                    dstFile.delete()
                }
                BackupArchiveStores.copyFile(srcFile, dstFile)
                EmojiPinGroupStore.get(context).reload()
            }
        }
        val clipboardManager = context.clipboardManager().value
        var clipboardWasReset = false

        suspend fun ensureClipboardReset() {
            if (shouldReset && !clipboardWasReset) {
                clipboardManager.clearFullHistoryForRestore()
                ClipboardFileStorage.resetClipboardFileStorage(context)
                val clipboardFilesDb = ClipboardFilesDatabase.new(context)
                try {
                    clipboardFilesDb.clipboardFilesDao().deleteAll()
                } finally {
                    clipboardFilesDb.close()
                }
                clipboardWasReset = true
            }
        }

        if (selection.containsClipboard) {
            val clipboardFilesDir = workspace.outputDir.subDir("clipboard")

            if (selection.clipboardTextItems) {
                val clipboardItems = clipboardFilesDir.subFile(Backup.CLIPBOARD_TEXT_ITEMS_JSON_NAME)
                restoreSelectedSection(sourceExists = clipboardItems.exists()) {
                    ensureClipboardReset()
                    val clipboardItemsList = clipboardItems.readJson<List<ClipboardItem>>()
                    clipboardManager.restoreHistoryAndAwait(
                        items = clipboardItemsList.filter { it.type == ItemType.TEXT },
                    )
                }
            }
            if (selection.clipboardImageItems) {
                val clipboardItems = clipboardFilesDir.subFile(Backup.CLIPBOARD_IMAGES_JSON_NAME)
                restoreSelectedSection(sourceExists = clipboardItems.exists()) {
                    ensureClipboardReset()
                    val restoredFileInfos = mutableListOf<ClipboardFileInfo>()
                    val clipboardItemsList = clipboardItems.readJson<List<ClipboardItem>>()
                    val restoredItems = clipboardItemsList
                        .filter { it.type == ItemType.IMAGE }
                        .map { item ->
                            val archivedFileId = checkNotNull(
                                item.uri?.lastPathSegment?.toLongOrNull(),
                            ) {
                                "Clipboard image backup contains an invalid provider id."
                            }
                            val restoredFile = ClipboardFileStorage.insertFileFromBackup(
                                context = context,
                                source = clipboardFilesDir.subFile(
                                    relPath = "${ClipboardFileStorage.CLIPBOARD_FILES_PATH}/$archivedFileId",
                                ),
                                mediaKind = ClipboardFileStorage.MediaKind.IMAGE,
                            )
                            val restoredItem = item.copy(
                                uri = ContentUris.withAppendedId(
                                    ClipboardMediaProvider.IMAGE_CLIPS_URI,
                                    restoredFile.id,
                                ),
                            )
                            ClipboardRestoredFileInfo.create(
                                restoredItem,
                                restoredFile.id,
                                restoredFile.plaintextSize,
                            )?.let(restoredFileInfos::add)
                            restoredItem
                        }
                    insertRestoredClipboardFileInfos(restoredFileInfos)
                    clipboardManager.restoreHistoryAndAwait(items = restoredItems)
                }
            }
            if (selection.clipboardVideoItems) {
                val clipboardItems = clipboardFilesDir.subFile(Backup.CLIPBOARD_VIDEO_JSON_NAME)
                restoreSelectedSection(sourceExists = clipboardItems.exists()) {
                    ensureClipboardReset()
                    val restoredFileInfos = mutableListOf<ClipboardFileInfo>()
                    val clipboardItemsList = clipboardItems.readJson<List<ClipboardItem>>()
                    val restoredItems = clipboardItemsList
                        .filter { it.type == ItemType.VIDEO }
                        .map { item ->
                            val archivedFileId = checkNotNull(
                                item.uri?.lastPathSegment?.toLongOrNull(),
                            ) {
                                "Clipboard video backup contains an invalid provider id."
                            }
                            val restoredFile = ClipboardFileStorage.insertFileFromBackup(
                                context = context,
                                source = clipboardFilesDir.subFile(
                                    relPath = "${ClipboardFileStorage.CLIPBOARD_FILES_PATH}/$archivedFileId",
                                ),
                                mediaKind = ClipboardFileStorage.MediaKind.VIDEO,
                            )
                            val restoredItem = item.copy(
                                uri = ContentUris.withAppendedId(
                                    ClipboardMediaProvider.VIDEO_CLIPS_URI,
                                    restoredFile.id,
                                ),
                            )
                            ClipboardRestoredFileInfo.create(
                                restoredItem,
                                restoredFile.id,
                                restoredFile.plaintextSize,
                            )?.let(restoredFileInfos::add)
                            restoredItem
                        }
                    insertRestoredClipboardFileInfos(restoredFileInfos)
                    clipboardManager.restoreHistoryAndAwait(items = restoredItems)
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
            lastRestoreRolledBack = false
            lastRestoreSummary = null
            var rollbackSnapshot: RestoreRollbackSnapshot? = null
            var liveMutationStarted = false
            var rollbackCompleted = false

            suspend fun rollbackIfNeeded() {
                val snapshot = rollbackSnapshot
                if (snapshot != null && liveMutationStarted && !rollbackCompleted) {
                    withContext(NonCancellable) {
                        snapshot.restore()
                    }
                    rollbackCompleted = true
                    lastRestoreRolledBack = true
                }
            }

            try {
                val selection = restoreFilesSelector.snapshot()
                val strategy = importStrategy
                rollbackSnapshot = RestoreRollbackSnapshot.capture(
                    context = context,
                    cacheManager = cacheManager,
                    selection = selection,
                )
                liveMutationStarted = true
                val summary = withContext(Dispatchers.IO) {
                    performRestore(selection, strategy)
                }
                val result = summary.result
                if (result != RestoreOperationResult.Success) {
                    rollbackIfNeeded()
                }
                lastRestoreSummary = summary
                lastRestoreErrorMessage = summary.firstFailureMessage
                lastRestoreNotice = BackupRestorePolicy.noticeForRestoreOperationResult(result)
                when (result) {
                    RestoreOperationResult.Success -> {
                        closeRestoreWorkspace()
                        context.showLongToast(R.string.backup_and_restore__restore__success)
                        navController.navigateUp()
                    }
                    RestoreOperationResult.PartialFailure -> {
                        closeRestoreWorkspace()
                        context.showLongToast(R.string.backup_and_restore__restore__partial_failure_toast)
                    }
                    RestoreOperationResult.Failure -> {
                        closeRestoreWorkspace()
                        context.showLongToast(
                            R.string.backup_and_restore__restore__failure,
                            "error_message" to (lastRestoreErrorMessage ?: unknownRestoreError),
                        )
                    }
                    RestoreOperationResult.Cancelled -> {
                        closeRestoreWorkspace()
                        context.showLongToast(R.string.backup_and_restore__restore__cancelled)
                    }
                }
            } catch (e: Throwable) {
                var reportError = e
                try {
                    rollbackIfNeeded()
                } catch (rollbackError: Throwable) {
                    rollbackError.addSuppressed(e)
                    reportError = rollbackError
                }
                flogError { reportError.stackTraceToString() }
                closeRestoreWorkspace()
                if (e is CancellationException) {
                    throw e
                }
                val errorMessage = if (reportError !== e) {
                    rollbackFailureError
                } else {
                    BackupRestorePolicy.restoreErrorMessage(e, unknownRestoreError)
                }
                lastRestoreNotice = RestoreFlowNotice.Failure
                lastRestoreErrorMessage = errorMessage
                context.showLongToast(
                    R.string.backup_and_restore__restore__failure,
                    "error_message" to errorMessage,
                )
            } finally {
                withContext(NonCancellable) {
                    rollbackSnapshot?.close()
                }
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
                    closeRestoreWorkspace()
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
                secondaryText = if (lastRestoreRolledBack) {
                    stringRes(
                        R.string.backup_and_restore__restore__rollback_complete,
                        "error_message" to (lastRestoreErrorMessage ?: unknownRestoreError),
                    )
                } else {
                    stringRes(
                        R.string.backup_and_restore__restore__failure_recovery,
                        "error_message" to (lastRestoreErrorMessage ?: unknownRestoreError),
                    )
                },
            )
            RestoreFlowNotice.PartialFailure -> {
                val summary = lastRestoreSummary
                FlorisWarningCard(
                    modifier = Modifier.padding(8.dp),
                    text = stringRes(R.string.backup_and_restore__restore__partial_failure_title),
                    secondaryText = if (lastRestoreRolledBack) {
                        stringRes(
                            R.string.backup_and_restore__restore__rollback_complete,
                            "error_message" to (
                                lastRestoreErrorMessage ?: stringRes(
                                    R.string.backup_and_restore__restore__partial_failure_recovery,
                                )
                            ),
                        )
                    } else buildString {
                        val restored = summary?.restoredSections ?: 0
                        val problems = summary?.problemSections ?: 0
                        append(pluralsRes(
                            R.plurals.backup_and_restore__restore__partial_restored_count,
                            restored,
                            "restored_count" to restored,
                        ))
                        append(". ")
                        append(pluralsRes(
                            R.plurals.backup_and_restore__restore__partial_problem_count,
                            problems,
                            "problem_count" to problems,
                        ))
                        append(". ")
                        append(stringRes(R.string.backup_and_restore__restore__partial_failure_recovery))
                    },
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
                    icon = Icons.Default.Archive,
                    title = if (workspace.archiveWasEncrypted) {
                        stringRes(R.string.backup_and_restore__restore__metadata_encrypted)
                    } else {
                        stringRes(R.string.backup_and_restore__restore__metadata_plaintext)
                    },
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
