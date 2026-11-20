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

import android.content.Intent
import android.provider.DocumentsContract
import android.text.format.Formatter
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import dev.patrickgold.florisboard.BuildConfig
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.findActivity
import dev.patrickgold.florisboard.cacheManager
import dev.patrickgold.florisboard.lib.cache.CacheManager
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.devtools.flogError
import dev.patrickgold.florisboard.lib.io.FileRegistry
import dev.patrickgold.florisboard.app.settings.search.SettingsListItem
import java.text.NumberFormat
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
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
import org.florisboard.lib.compose.FlorisSuccessCard
import org.florisboard.lib.compose.FlorisWarningCard
import org.florisboard.lib.compose.defaultFlorisOutlinedBox
import org.florisboard.lib.compose.rippleClickable
import org.florisboard.lib.compose.stringRes

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

    /**
     * Scheduled archives carry the non-sensitive canonical stores. Clipboard
     * history remains an explicit manual-backup choice because recurring
     * retention of copied secrets would be surprising even when encrypted.
     */
    fun scheduledSelection() = FilesSelection(
        jetprefDatastore = true,
        imeKeyboard = true,
        keypressSounds = true,
        imeTheme = true,
        localStickerPacks = true,
        snippets = true,
        hardwareKeyboardLayouts = true,
        customEmojiTags = true,
        emojiPinGroups = true,
        clipboardTextItems = false,
        clipboardImageItems = false,
        clipboardVideoItems = false,
    )

    enum class Destination {
        FILE_SYS,
        SHARE_INTENT;
    }

    class FilesSelector {
        var jetprefDatastore by mutableStateOf(true)
        var imeKeyboard by mutableStateOf(true)
        var keypressSounds by mutableStateOf(true)
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
                keypressSounds ||
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
            keypressSounds = keypressSounds,
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
            keypressSounds = true
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
            keypressSoundsAvailable: Boolean,
            snippetsAvailable: Boolean,
            hardwareKeyboardLayoutsAvailable: Boolean,
            customEmojiTagsAvailable: Boolean,
            emojiPinGroupsAvailable: Boolean,
        ) {
            keypressSounds = keypressSoundsAvailable
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
                        selector.keypressSounds,
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
                        if (values.size >= 12) {
                            jetprefDatastore = values[0]
                            imeKeyboard = values[1]
                            keypressSounds = values[2]
                            imeTheme = values[3]
                            localStickerPacks = values[4]
                            snippets = values[5]
                            hardwareKeyboardLayouts = values[6]
                            customEmojiTags = values[7]
                            emojiPinGroups = values[8]
                            clipboardTextItems = values[9]
                            clipboardImageItems = values[10]
                            clipboardVideoItems = values[11]
                            updateCheckboxState()
                        } else if (values.size >= 11) {
                            // State saved before custom keypress sounds became a
                            // selectable portable store.
                            jetprefDatastore = values[0]
                            imeKeyboard = values[1]
                            keypressSounds = true
                            imeTheme = values[2]
                            localStickerPacks = values[3]
                            snippets = values[4]
                            hardwareKeyboardLayouts = values[5]
                            customEmojiTags = values[6]
                            emojiPinGroups = values[7]
                            clipboardTextItems = values[8]
                            clipboardImageItems = values[9]
                            clipboardVideoItems = values[10]
                            updateCheckboxState()
                        } else if (values.size >= 7) {
                            jetprefDatastore = values[0]
                            imeKeyboard = values[1]
                            keypressSounds = true
                            imeTheme = values[2]
                            localStickerPacks = values[3]
                            // State saved before the four portable stores
                            // were selectable. Keep their new safe defaults.
                            clipboardTextItems = values[4]
                            clipboardImageItems = values[5]
                            clipboardVideoItems = values[6]
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
        val keypressSounds: Boolean,
        val imeTheme: Boolean,
        val localStickerPacks: Boolean,
        val snippets: Boolean,
        val hardwareKeyboardLayouts: Boolean,
        val customEmojiTags: Boolean,
        val emojiPinGroups: Boolean,
        val clipboardTextItems: Boolean,
        val clipboardImageItems: Boolean,
        val clipboardVideoItems: Boolean,
    ) : java.io.Serializable {
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
    val unknownBackupError = stringRes(R.string.backup_and_restore__back_up__unknown_error)
    val context = LocalContext.current
    val cacheManager by context.cacheManager()
    val scope = rememberCoroutineScope()

    // Saved across rotation: a kept archive is offered again only for the sections it was built
    // from, so a selector reset by turning the phone would quietly build a different archive.
    var backupDestination by rememberSaveable { mutableStateOf(Backup.Destination.FILE_SYS) }
    val backupFilesSelector = rememberSaveable(saver = Backup.FilesSelector.Saver) { Backup.FilesSelector() }
    var backupWorkspaceUuid by rememberSaveable { mutableStateOf<String?>(null) }
    var backupWorkspace by remember {
        mutableStateOf(
            // Closing a workspace deletes its folder but leaves it listed, so only an open one
            // means a save is still running.
            backupWorkspaceUuid?.let(cacheManager.backupAndRestore::getWorkspaceByUuid)?.takeIf { it.isOpen() },
        )
    }
    // Kept across rotation, since an open workspace can also be a kept archive waiting to be saved
    // again. A workspace that's gone (the app was restarted) means no save is running.
    var isBackupInProgress by rememberSaveable(
        stateSaver = Saver<Boolean, Boolean>(save = { it }, restore = { it && backupWorkspace != null }),
    ) { mutableStateOf(false) }
    var lastBackupNotice by rememberSaveable { mutableStateOf<BackupFlowNotice?>(null) }
    var lastBackupErrorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    // A failed check already says what to do, so the generic storage advice isn't added to it.
    var lastBackupFailureHasOwnAdvice by rememberSaveable { mutableStateOf(false) }
    // Saved with the notice, so a success notice that survives rotation still has its receipt.
    var lastBackupReceipt by rememberSaveable { mutableStateOf<BackupReceipt?>(null) }
    var lastBackupSelection by rememberSaveable { mutableStateOf<Backup.FilesSelection?>(null) }
    var showEncryptionPassphraseDialog by remember { mutableStateOf(false) }

    fun setBackupWorkspace(workspace: CacheManager.BackupAndRestoreWorkspace?) {
        backupWorkspace = workspace
        backupWorkspaceUuid = workspace?.uuid
    }

    fun closeBackupWorkspace() {
        backupWorkspace?.close()
        setBackupWorkspace(null)
    }

    // A checked archive kept after a save that didn't verify is saved again as it is, unless
    // the chosen sections changed since it was built.
    fun hasReusableBackupWorkspace(): Boolean {
        val workspace = backupWorkspace ?: return false
        val receipt = workspace.backupReceipt ?: return false
        return !workspace.isClosed() &&
            receipt.stillDescribes(workspace.archiveFile) &&
            workspace.backupSelection == backupFilesSelector.snapshot()
    }

    // Close screen-owned plaintext/ciphertext workspaces on every exit. Shared
    // artifacts transfer to CacheManager's bounded grant lease and are nulled
    // here, so onDispose never races a receiving app.
    // Turning the phone keeps the workspace, since the recreated screen picks it up by its UUID.
    // The disposers read the state itself, not a copy updated on recomposition: an outcome taken
    // just before the screen goes for good hasn't recomposed yet.
    val activity = context.findActivity()
    val currentActivity by rememberUpdatedState(activity)
    DisposableEffect(Unit) {
        onDispose {
            val isConfigurationChange = currentActivity?.isChangingConfigurations == true
            if (!isConfigurationChange && !isBackupInProgress) backupWorkspace?.close()
        }
    }

    // A save outlives the screen (see the launcher), so it reports to ManualBackupSaveOutcomes.
    // Holding the workspace tells the save a screen will offer its archive again, and the
    // outcome lands here even when this screen replaced the one that started the save.
    val heldWorkspaceUuid = backupWorkspaceUuid
    DisposableEffect(heldWorkspaceUuid) {
        heldWorkspaceUuid?.let(ManualBackupSaveOutcomes::hold)
        onDispose {
            val uuid = heldWorkspaceUuid ?: return@onDispose
            val dropped = ManualBackupSaveOutcomes.screenGone(
                workspaceUuid = uuid,
                turning = currentActivity?.isChangingConfigurations == true,
                stillShown = backupWorkspaceUuid == uuid,
                saveRunning = isBackupInProgress,
            )
            // An archive the dropped outcome kept to save again would stay in the cache with
            // nobody to offer it.
            if ((dropped as? ManualBackupSaveOutcomes.Outcome.Failed)?.keptToSaveAgain == true) {
                cacheManager.backupAndRestore.getWorkspaceByUuid(uuid)?.close()
            }
        }
    }
    LaunchedEffect(heldWorkspaceUuid) {
        val uuid = heldWorkspaceUuid ?: return@LaunchedEffect
        // A kept archive is saved again under the same workspace, so every save's outcome is taken.
        while (true) {
            when (val outcome = ManualBackupSaveOutcomes.take(uuid)) {
                is ManualBackupSaveOutcomes.Outcome.Saved -> {
                    isBackupInProgress = false
                    setBackupWorkspace(null)
                    lastBackupNotice = BackupRestorePolicy.noticeForBackupDocumentResult(BackupDocumentResult.Success)
                    lastBackupErrorMessage = null
                    lastBackupReceipt = outcome.receipt
                    lastBackupSelection = outcome.selection
                }
                is ManualBackupSaveOutcomes.Outcome.Failed -> {
                    isBackupInProgress = false
                    lastBackupErrorMessage = outcome.message
                    lastBackupFailureHasOwnAdvice = outcome.hasOwnAdvice
                    if (outcome.keptToSaveAgain) {
                        lastBackupNotice = BackupFlowNotice.SaveNotVerified
                    } else {
                        setBackupWorkspace(null)
                        lastBackupNotice = BackupRestorePolicy.noticeForBackupDocumentResult(BackupDocumentResult.Failure)
                    }
                }
            }
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
            val workspace = backupWorkspace
            if (workspace == null) {
                // The archive went with a screen that was closed, so there's nothing to save.
                isBackupInProgress = false
                lastBackupNotice = BackupRestorePolicy.noticeForBackupDocumentResult(BackupDocumentResult.Failure)
                lastBackupErrorMessage = null
                return@rememberLauncherForActivityResult
            }
            // A write that started can't be called back, so leaving or turning the screen doesn't
            // cancel the save: it runs to the end and reports what really happened, not a failure.
            // ATOMIC, since a screen gone before the save was first dispatched would otherwise
            // drop it unrun and leave the next screen waiting for an outcome forever.
            scope.launch(start = CoroutineStart.ATOMIC) { withContext(NonCancellable) {
                val outcome = runCatching {
                    val receipt = checkNotNull(workspace.backupReceipt) {
                        "Prepared backup was not checked."
                    }
                    withContext(Dispatchers.IO) {
                        // A finished write is not a backup until the copy reads back the same.
                        ManualBackupSave.save(
                            archive = workspace.archiveFile,
                            receipt = receipt,
                            open = { context.contentResolver.openInputStream(uri) },
                            write = { file -> context.contentResolver.writeFromFile(uri, file) },
                            delete = { DocumentsContract.deleteDocument(context.contentResolver, uri) },
                        )
                    }
                    receipt
                }.fold(
                    onSuccess = { receipt ->
                        // The copy is checked, so a workspace that won't close is logged, not
                        // reported as a failed backup.
                        runCatching { workspace.close() }.onFailure { flogError { it.stackTraceToString() } }
                        context.showLongToast(R.string.backup_and_restore__back_up__success)
                        ManualBackupSaveOutcomes.Outcome.Saved(receipt, workspace.backupSelection)
                    },
                    onFailure = { error ->
                        val errorMessage = error.backupFailureMessage(context, unknownBackupError)
                        // The checked archive stays while a screen holds it, so Back up can save
                        // it again as it is.
                        val keep = error.leavesArchiveToSaveAgain() &&
                            !workspace.isClosed() &&
                            workspace.backupReceipt?.stillDescribes(workspace.archiveFile) == true &&
                            ManualBackupSaveOutcomes.isHeld(workspace.uuid)
                        if (keep) {
                            context.showLongToast(
                                R.string.backup_and_restore__back_up__not_verified_toast,
                                "error_message" to errorMessage,
                            )
                        } else {
                            context.showLongToast(
                                R.string.backup_and_restore__back_up__failure,
                                "error_message" to errorMessage,
                            )
                            runCatching { workspace.close() }.onFailure { flogError { it.stackTraceToString() } }
                        }
                        ManualBackupSaveOutcomes.Outcome.Failed(errorMessage, error.hasOwnBackupAdvice(), keep)
                    },
                )
                ManualBackupSaveOutcomes.report(workspace.uuid, outcome)
            } }
        },
    )

    suspend fun prepareBackupWorkspace(
        passphrase: CharArray?,
    ): CacheManager.BackupAndRestoreWorkspace {
        val selection = backupFilesSelector.snapshot()
        val workspace = cacheManager.backupAndRestore.new()
        return try {
            val built = BackupArchiveBuilder.build(
                context = context,
                inputDir = workspace.inputDir,
                outputDir = workspace.outputDir,
                selection = selection,
                passphrase = passphrase,
            )
            workspace.metadata = built.metadata
            workspace.archiveFile = built.archiveFile
            workspace.archiveWasEncrypted = built.encrypted
            // Open the archive the way restore will while the passphrase is still here, so a
            // copy that later reads back byte for byte the same is known to restore.
            workspace.backupReceipt = withContext(Dispatchers.IO) {
                BackupPublicationVerifier.inspect(built.archiveFile, passphrase, workspace.dir)
            }
            workspace.backupSelection = selection
            workspace
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
        lastBackupReceipt = null
        lastBackupSelection = null
        try {
            runCatching {
                if (!hasReusableBackupWorkspace()) {
                    closeBackupWorkspace()
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
                if (error is CancellationException) {
                    // Leaving the screen while the archive is built or checked saves nothing,
                    // and that isn't a failure worth a toast.
                    closeBackupWorkspace()
                    isBackupInProgress = false
                    throw error
                }
                val errorMessage = error.backupFailureMessage(context, unknownBackupError)
                context.showLongToast(
                    R.string.backup_and_restore__back_up__failure,
                    "error_message" to errorMessage,
                )
                closeBackupWorkspace()
                isBackupInProgress = false
                lastBackupNotice = BackupFlowNotice.Failure
                lastBackupErrorMessage = errorMessage
                lastBackupFailureHasOwnAdvice = error.hasOwnBackupAdvice()
            }
        } finally {
            passphrase?.fill('\u0000')
        }
    }

    fun requestBackup() {
        if (!hasReusableBackupWorkspace() &&
            BackupRestorePolicy.requiresPortableEncryption(
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
                    if (lastBackupFailureHasOwnAdvice) {
                        R.string.backup_and_restore__back_up__failure_with_advice
                    } else {
                        R.string.backup_and_restore__back_up__failure_recovery
                    },
                    "error_message" to (lastBackupErrorMessage ?: stringRes(
                        R.string.backup_and_restore__back_up__unknown_error,
                    )),
                ),
            )
            BackupFlowNotice.SaveNotVerified -> FlorisErrorCard(
                modifier = Modifier.padding(8.dp),
                text = stringRes(R.string.backup_and_restore__back_up__not_verified_title),
                secondaryText = stringRes(
                    R.string.backup_and_restore__back_up__not_verified_summary,
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
            BackupFlowNotice.Success -> lastBackupReceipt?.let { receipt ->
                BackupReceiptCard(receipt, lastBackupSelection)
            }
            BackupFlowNotice.None -> Unit
        }
        FlorisInfoCard(
            modifier = Modifier.padding(8.dp),
            text = stringRes(R.string.backup_and_restore__back_up__privacy_title),
            secondaryText = stringRes(R.string.backup_and_restore__back_up__privacy_summary),
        )
        FlorisInfoCard(
            modifier = Modifier.padding(8.dp),
            text = stringRes(R.string.backup_and_restore__android_managed__title),
            secondaryText = stringRes(R.string.backup_and_restore__android_managed__summary),
        )
        // An archive is not a full device image. Name what it leaves behind, from the one
        // inventory that also drives Android's own backup rules, rather than implying coverage.
        // Rendered from the inventory rather than a hand-written sentence, so a
        // newly excluded store shows up here instead of quietly going unlisted.
        val omittedStores = BackupDataInventory.archiveOmissions()
            .mapNotNull { it.omissionLabel }
            .distinct()
            .map { labelRes -> stringRes(labelRes) }
            .joinToString(separator = ", ")
        FlorisInfoCard(
            modifier = Modifier.padding(8.dp),
            text = stringRes(R.string.backup_and_restore__back_up__coverage_title),
            secondaryText = stringRes(
                R.string.backup_and_restore__back_up__coverage_summary,
                "stores" to omittedStores,
            ),
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
        ScheduledBackupPanel(context)
    }
}

/** Each archive section with its label from the section picker. */
internal fun Backup.FilesSelection.labelledSections(): List<Pair<Boolean, Int>> = listOf(
    jetprefDatastore to R.string.backup_and_restore__back_up__files_jetpref_datastore,
    imeKeyboard to R.string.backup_and_restore__back_up__files_ime_keyboard,
    keypressSounds to R.string.backup_and_restore__back_up__files_keypress_sounds,
    imeTheme to R.string.backup_and_restore__back_up__files_ime_theme,
    localStickerPacks to R.string.backup_and_restore__back_up__files_local_stickers,
    snippets to R.string.backup_and_restore__back_up__files_snippets,
    hardwareKeyboardLayouts to R.string.backup_and_restore__back_up__files_hardware_keyboard_layouts,
    customEmojiTags to R.string.backup_and_restore__back_up__files_custom_emoji_tags,
    emojiPinGroups to R.string.backup_and_restore__back_up__files_emoji_pin_groups,
    clipboardTextItems to R.string.backup_and_restore__back_up__receipt_clipboard_text,
    clipboardImageItems to R.string.backup_and_restore__back_up__receipt_clipboard_images,
    clipboardVideoItems to R.string.backup_and_restore__back_up__receipt_clipboard_videos,
)

@Composable
private fun BackupReceiptCard(receipt: BackupReceipt, selection: Backup.FilesSelection?) {
    val context = LocalContext.current
    val none = stringRes(R.string.backup_and_restore__back_up__receipt_none)
    val carried = receipt.sections.labelledSections()
    val chosen = (selection ?: receipt.sections).labelledSections()
    val included = carried.filter { it.first }.map { stringRes(it.second) }
    // A ticked section with nothing in it yet is named apart from one left unticked, so the
    // receipt never reads as if a choice was ignored.
    val selectedButEmpty = carried.zip(chosen)
        .filter { (inArchive, picked) -> picked.first && !inArchive.first }
        .map { (inArchive, _) -> stringRes(inArchive.second) }
    // Unticked sections, then the stores no archive carries, so nothing reads as backed up
    // that isn't.
    val omitted = carried.zip(chosen)
        .filter { (inArchive, picked) -> !picked.first && !inArchive.first }
        .map { (inArchive, _) -> stringRes(inArchive.second) } +
        BackupDataInventory.archiveOmissions()
            .mapNotNull { it.omissionLabel }
            .distinct()
            .map { stringRes(it) }
    val lines = listOfNotNull(
        stringRes(R.string.backup_and_restore__back_up__receipt_summary),
        stringRes(R.string.backup_and_restore__back_up__receipt_version, "version" to receipt.archiveVersion),
        stringRes(
            R.string.backup_and_restore__back_up__receipt_encryption,
            "encryption" to stringRes(
                if (receipt.encrypted) {
                    R.string.backup_and_restore__back_up__receipt_encrypted
                } else {
                    R.string.backup_and_restore__back_up__receipt_not_encrypted
                },
            ),
        ),
        stringRes(
            R.string.backup_and_restore__back_up__receipt_sections,
            "sections" to included.joinToString(separator = ", ").ifEmpty { none },
        ),
        selectedButEmpty.takeIf { it.isNotEmpty() }?.let { empty ->
            stringRes(
                R.string.backup_and_restore__back_up__receipt_empty_sections,
                "sections" to empty.joinToString(separator = ", "),
            )
        },
        stringRes(
            R.string.backup_and_restore__back_up__receipt_omissions,
            "omissions" to omitted.joinToString(separator = ", ").ifEmpty { none },
        ),
        stringRes(
            R.string.backup_and_restore__back_up__receipt_size,
            "size" to Formatter.formatShortFileSize(context, receipt.byteSize),
            "bytes" to NumberFormat.getIntegerInstance().format(receipt.byteSize),
        ),
        stringRes(R.string.backup_and_restore__back_up__receipt_checksum, "checksum" to receipt.sha256),
    )
    FlorisSuccessCard(
        modifier = Modifier.padding(8.dp),
        text = stringRes(R.string.backup_and_restore__back_up__receipt_title),
        secondaryText = lines.joinToString(separator = "\n"),
    )
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
            onClick = { filesSelector.keypressSounds = !filesSelector.keypressSounds },
            checked = filesSelector.keypressSounds,
            text = stringRes(R.string.backup_and_restore__back_up__files_keypress_sounds),
            secondaryText = stringRes(R.string.backup_and_restore__back_up__files_keypress_sounds_summary),
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
    SettingsListItem(
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
    SettingsListItem(
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
    SettingsListItem(
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.56f)
            .rippleClickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            // The radio button inside takes no clicks, so the row says which one is picked.
            .semantics { this.selected = selected },
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
