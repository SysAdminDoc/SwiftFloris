/*
 * Copyright (C) 2026 SwiftFloris Contributors
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

package dev.patrickgold.florisboard.app.settings.sync

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.semantics.Role
import org.florisboard.lib.compose.rippleClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.settings.dictionary.UserDictionaryScreenAction
import dev.patrickgold.florisboard.app.settings.dictionary.UserDictionaryType
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.ime.dictionary.DictionaryManager
import dev.patrickgold.florisboard.ime.sync.PairedSyncDevice
import dev.patrickgold.florisboard.ime.sync.PairedSyncDeviceList
import dev.patrickgold.florisboard.ime.sync.PairingPayload
import dev.patrickgold.florisboard.ime.sync.PairingPayloadGenerator
import dev.patrickgold.florisboard.ime.sync.PairingPayloadReceiver
import dev.patrickgold.florisboard.ime.sync.PersonalDictionarySync
import dev.patrickgold.florisboard.ime.sync.PersonalDictionarySyncDaoApplier
import dev.patrickgold.florisboard.ime.sync.SyncChannel
import dev.patrickgold.florisboard.ime.sync.SyncIdentityStore
import dev.patrickgold.florisboard.ime.sync.SyncJsonTransferPolicy
import dev.patrickgold.florisboard.ime.sync.SyncQrCode
import dev.patrickgold.florisboard.ime.sync.SyncQrCodeMatrix
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.jetpref.material.ui.JetPrefListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.compose.FlorisErrorCard
import org.florisboard.lib.compose.FlorisInfoCard
import org.florisboard.lib.compose.FlorisProgressCard
import org.florisboard.lib.compose.FlorisSuccessCard
import org.florisboard.lib.compose.defaultFlorisOutlinedBox
import org.florisboard.lib.compose.stringRes
import java.io.FileNotFoundException
import java.util.UUID

@Composable
fun SyncSettingsScreen() = FlorisScreen {
    title = stringRes(R.string.settings__sync__title)
    previewFieldVisible = false

    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val dictionaryManager = remember { DictionaryManager.default() }
    val compatibility = SyncCompatibilityPolicy.stateForSdk(Build.VERSION.SDK_INT)

    val channelId by prefs.sync.channelId.collectAsState()
    val clusterId by prefs.sync.clusterId.collectAsState()
    val deviceId by prefs.sync.deviceId.collectAsState()
    val pairedDevicesJson by prefs.sync.pairedDevicesJson.collectAsState()
    val manualExportTargetUri by prefs.sync.manualExportTargetUri.collectAsState()
    val pairedDevices = remember(pairedDevicesJson) { PairedSyncDeviceList.parse(pairedDevicesJson) }
    val activeChannel = remember(channelId) { SyncChannel.parse(channelId) }

    var syncthingDialogVisible by rememberSaveable { mutableStateOf(false) }
    var scannedPayloadDialogVisible by rememberSaveable { mutableStateOf(false) }
    var generatedPayload by rememberSaveable { mutableStateOf<String?>(null) }
    var syncTransferNotice by rememberSaveable { mutableStateOf(SyncTransferNotice.None) }
    var syncTransferError by rememberSaveable { mutableStateOf<String?>(null) }
    var syncExportedCount by rememberSaveable { mutableStateOf<Int?>(null) }
    var syncImportedInsertCount by rememberSaveable { mutableStateOf<Int?>(null) }
    var syncImportedUpdateCount by rememberSaveable { mutableStateOf<Int?>(null) }
    var syncImportedDeleteCount by rememberSaveable { mutableStateOf<Int?>(null) }

    val folderPickedText = stringRes(R.string.settings__sync__folder_selected)
    val manualExportPickedText = stringRes(R.string.settings__sync__manual_export_target_selected)
    val pairingReceivedText = stringRes(R.string.settings__sync__pairing_received)
    val pairingInvalidText = stringRes(R.string.settings__sync__pairing_invalid)
    val pairingClusterMismatchText = stringRes(R.string.settings__sync__pairing_cluster_mismatch)
    val scannerMissingText = stringRes(R.string.settings__sync__scanner_missing)
    val pairingUnsupportedText = stringRes(R.string.settings__sync__pairing_requires_android_13)
    val manualExportDefaultSummary = stringRes(R.string.settings__sync__channel_manual_export_summary)
    val syncExportedText = stringRes(R.string.settings__sync__export_success_toast)
    val syncImportedText = stringRes(R.string.settings__sync__import_success_toast)
    val syncNoChangesText = stringRes(R.string.settings__sync__import_no_changes_toast)
    val syncMissingTargetText = stringRes(R.string.settings__sync__missing_target_toast)
    val syncNeedsPairingText = stringRes(R.string.settings__sync__needs_pairing_toast)
    val syncOpenFailedText = stringRes(R.string.settings__sync__open_failed_toast)
    val syncUnsupportedText = stringRes(R.string.settings__sync__unsupported_toast)
    val syncUnknownError = stringRes(R.string.settings__sync__unknown_error)

    fun setChannel(channel: SyncChannel) {
        scope.launch { prefs.sync.channelId.set(channel.channelId) }
    }

    fun clearTransferNotice() {
        syncTransferNotice = SyncTransferNotice.None
        syncTransferError = null
        syncExportedCount = null
        syncImportedInsertCount = null
        syncImportedUpdateCount = null
        syncImportedDeleteCount = null
    }

    fun failTransfer(message: String, error: Throwable? = null) {
        syncTransferNotice = SyncTransferNotice.Failure
        syncTransferError = error?.localizedMessage ?: error?.message ?: message
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    fun receivePayload(rawPayload: String) {
        val payload = PairingPayload.parse(rawPayload)
        if (payload == null) {
            Toast.makeText(context, pairingInvalidText, Toast.LENGTH_LONG).show()
            return
        }
        scope.launch {
            val plan = withContext(Dispatchers.IO) {
                PairingPayloadReceiver.plan(
                    payload = payload,
                    localClusterId = clusterId,
                    localDeviceId = deviceId,
                    pairedDevicesJson = pairedDevicesJson,
                    previousLocalState = SyncIdentityStore.loadLocalState(context),
                    pairedAtMillis = System.currentTimeMillis(),
                    newDeviceId = { UUID.randomUUID().toString() },
                )
            }
            when (plan) {
                is PairingPayloadReceiver.Plan.Accepted -> {
                    val stateSaved = withContext(Dispatchers.IO) {
                        SyncIdentityStore.saveLocalState(context, plan.foldedLocalState)
                    }
                    if (!stateSaved) {
                        failTransfer(syncOpenFailedText)
                        return@launch
                    }
                    prefs.sync.pairedDevicesJson.set(plan.pairedDevicesJson)
                    prefs.sync.channelId.set(payload.syncChannelId)
                    prefs.sync.clusterId.set(plan.clusterId)
                    prefs.sync.deviceId.set(plan.deviceId)
                    Toast.makeText(context, pairingReceivedText, Toast.LENGTH_SHORT).show()
                }
                is PairingPayloadReceiver.Plan.ClusterMismatch -> {
                    Toast.makeText(context, pairingClusterMismatchText, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun resolveLocalFolderSyncFileUri(channel: SyncChannel.LocalFolder, create: Boolean): Uri? {
        val treeUri = Uri.parse(channel.absolutePath)
        val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocumentId)
        val projection = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
        )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndex(Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndex(Document.COLUMN_DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndex(Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                val name = cursor.getStringOrNull(nameCol)
                val mime = cursor.getStringOrNull(mimeCol)
                if (name == SYNC_FILE_NAME && mime != Document.MIME_TYPE_DIR) {
                    val documentId = cursor.getStringOrNull(idCol) ?: return null
                    return DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                }
            }
        }
        if (!create) return null
        return DocumentsContract.createDocument(
            context.contentResolver,
            DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocumentId),
            SYNC_FILE_MIME_TYPE,
            SYNC_FILE_NAME,
        )
    }

    fun writeSyncJsonToUri(uri: Uri, json: String) {
        val stream = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw FileNotFoundException(uri.toString())
        stream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
    }

    fun readSyncJsonFromUri(uri: Uri): String {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw FileNotFoundException(uri.toString())
        return stream.use(SyncJsonTransferPolicy::readJsonTextLimited)
    }

    suspend fun exportSyncSnapshot(targetUri: Uri) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            failTransfer(syncUnsupportedText)
            return
        }
        if (pairedDevices.isEmpty()) {
            failTransfer(syncNeedsPairingText)
            return
        }
        val dao = dictionaryManager.florisUserDictionaryDao()
        if (dao == null) {
            failTransfer(syncOpenFailedText)
            return
        }
        val resolvedClusterId = clusterId.ifBlank { UUID.randomUUID().toString() }
        val resolvedDeviceId = deviceId.ifBlank { UUID.randomUUID().toString() }
        val identity = SyncIdentityStore.getOrCreate(context, resolvedDeviceId)
        if (identity == null) {
            failTransfer(syncUnsupportedText)
            return
        }
        syncTransferNotice = SyncTransferNotice.Exporting
        syncTransferError = null
        syncExportedCount = null
        runCatching {
            val exportedCount = withContext(Dispatchers.IO) {
                val words = PersonalDictionarySyncDaoApplier.snapshot(dao)
                val state = PersonalDictionarySync.reconcileLocalState(
                    previous = SyncIdentityStore.loadLocalState(context),
                    words = words,
                    deviceId = resolvedDeviceId,
                    nowMillis = System.currentTimeMillis(),
                )
                val file = PersonalDictionarySync.sealEnvelopes(
                    state = state,
                    clusterId = resolvedClusterId,
                    recipients = pairedDevices.filterNot { it.deviceId == resolvedDeviceId },
                    senderKeyPair = identity.keyPair,
                    nowMillis = System.currentTimeMillis(),
                )
                if (file.envelopes.isEmpty()) {
                    throw IllegalStateException(syncNeedsPairingText)
                }
                writeSyncJsonToUri(targetUri, file.serializeToString())
                check(SyncIdentityStore.saveLocalState(context, state)) { syncOpenFailedText }
                words.size
            }
            if (clusterId.isBlank()) prefs.sync.clusterId.set(resolvedClusterId)
            if (deviceId.isBlank()) prefs.sync.deviceId.set(resolvedDeviceId)
            syncTransferNotice = SyncTransferNotice.ExportSuccess
            syncExportedCount = exportedCount
            Toast.makeText(context, syncExportedText, Toast.LENGTH_SHORT).show()
        }.onFailure { error ->
            failTransfer(error.localizedMessage ?: syncOpenFailedText, error)
        }
    }

    suspend fun importSyncSnapshot(sourceUri: Uri) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            failTransfer(syncUnsupportedText)
            return
        }
        val resolvedClusterId = clusterId.ifBlank {
            failTransfer(syncNeedsPairingText)
            return
        }
        val resolvedDeviceId = deviceId.ifBlank {
            failTransfer(syncNeedsPairingText)
            return
        }
        val dao = dictionaryManager.florisUserDictionaryDao()
        if (dao == null) {
            failTransfer(syncOpenFailedText)
            return
        }
        val identity = SyncIdentityStore.getOrCreate(context, resolvedDeviceId)
        if (identity == null) {
            failTransfer(syncUnsupportedText)
            return
        }
        syncTransferNotice = SyncTransferNotice.Importing
        syncTransferError = null
        syncImportedInsertCount = null
        syncImportedUpdateCount = null
        syncImportedDeleteCount = null
        runCatching {
            val result = withContext(Dispatchers.IO) {
                val rawJson = readSyncJsonFromUri(sourceUri)
                val imported = PersonalDictionarySync.openEnvelopeFor(
                    rawFileJson = rawJson,
                    myDeviceId = resolvedDeviceId,
                    expectedClusterId = resolvedClusterId,
                    recipientKeyPair = identity.keyPair,
                    trustedSenders = pairedDevices.filterNot { it.deviceId == resolvedDeviceId },
                ) ?: throw IllegalArgumentException(syncOpenFailedText)
                val words = PersonalDictionarySyncDaoApplier.snapshot(dao)
                val localState = PersonalDictionarySync.reconcileLocalState(
                    previous = SyncIdentityStore.loadLocalState(context),
                    words = words,
                    deviceId = resolvedDeviceId,
                    nowMillis = System.currentTimeMillis(),
                )
                val plan = PersonalDictionarySync.planImport(
                    localState = localState,
                    imported = imported,
                    currentWords = words,
                )
                val applyResult = PersonalDictionarySyncDaoApplier.apply(plan, dao)
                check(SyncIdentityStore.saveLocalState(context, plan.newState)) { syncOpenFailedText }
                applyResult
            }
            syncTransferNotice = SyncTransferNotice.ImportSuccess
            syncImportedInsertCount = result.insertedCount
            syncImportedUpdateCount = result.updatedCount
            syncImportedDeleteCount = result.deletedCount
            Toast.makeText(
                context,
                if (result.isNoOp) syncNoChangesText else syncImportedText,
                Toast.LENGTH_SHORT,
            ).show()
        }.onFailure { error ->
            failTransfer(error.localizedMessage ?: syncOpenFailedText, error)
        }
    }

    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            // Release the previous folder grant before taking the new one, otherwise
            // each re-pick orphans a persisted grant and slowly exhausts Android's
            // per-app persisted-URI-permission cap (the stale grants survive reboots).
            val previousFolderUri = (activeChannel as? SyncChannel.LocalFolder)?.absolutePath
            if (!previousFolderUri.isNullOrBlank() && previousFolderUri != uri.toString()) {
                runCatching {
                    context.contentResolver.releasePersistableUriPermission(
                        Uri.parse(previousFolderUri),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }
            }
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            setChannel(SyncChannel.LocalFolder(uri.toString(), uri.lastPathSegment ?: "Local folder"))
            Toast.makeText(context, folderPickedText, Toast.LENGTH_SHORT).show()
        }
    }

    val manualExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            // Release the previous manual-export grant before taking the new one (see
            // folderLauncher) so re-picking a target doesn't leak persisted grants.
            val previousExportUri = manualExportTargetUri
            if (previousExportUri.isNotBlank() && previousExportUri != uri.toString()) {
                runCatching {
                    context.contentResolver.releasePersistableUriPermission(
                        Uri.parse(previousExportUri),
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }
            }
            // Persist the write grant, otherwise the transient ActivityResult
            // permission is lost on process death and every later export to this
            // saved target fails with SecurityException (the folder channel above
            // already does this).
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            scope.launch {
                prefs.sync.manualExportTargetUri.set(uri.toString())
                prefs.sync.channelId.set(SyncChannel.ManualExport.channelId)
            }
            Toast.makeText(context, manualExportPickedText, Toast.LENGTH_SHORT).show()
        }
    }

    val manualImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch { importSyncSnapshot(uri) }
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val raw = result.data?.getStringExtra(ZXING_SCAN_RESULT).orEmpty()
            if (raw.isNotBlank()) {
                receivePayload(raw)
            }
        }
    }

    fun startReceivePairing() {
        val scanIntent = Intent(ZXING_SCAN_ACTION).apply {
            putExtra(ZXING_SCAN_MODE, ZXING_QR_CODE_MODE)
        }
        try {
            if (scanIntent.resolveActivity(context.packageManager) == null) {
                Toast.makeText(context, scannerMissingText, Toast.LENGTH_LONG).show()
                scannedPayloadDialogVisible = true
            } else {
                scanLauncher.launch(scanIntent)
            }
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, scannerMissingText, Toast.LENGTH_LONG).show()
            scannedPayloadDialogVisible = true
        }
    }

    fun generatePairingPayload() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, pairingUnsupportedText, Toast.LENGTH_LONG).show()
            return
        }
        val resolvedClusterId = clusterId.ifBlank { UUID.randomUUID().toString() }
        val resolvedDeviceId = deviceId.ifBlank { UUID.randomUUID().toString() }
        scope.launch {
            if (clusterId.isBlank()) prefs.sync.clusterId.set(resolvedClusterId)
            if (deviceId.isBlank()) prefs.sync.deviceId.set(resolvedDeviceId)
        }
        // The advertised public key MUST be the persisted identity's — the
        // generator's default argument mints a throwaway keypair whose
        // private half nothing could ever use to open an envelope.
        val identity = SyncIdentityStore.getOrCreate(context, resolvedDeviceId)
        if (identity == null) {
            Toast.makeText(context, pairingUnsupportedText, Toast.LENGTH_LONG).show()
            return
        }
        generatedPayload = PairingPayloadGenerator.generate(
            displayName = Build.MODEL ?: "Android device",
            syncChannelId = activeChannel.channelId,
            clusterId = resolvedClusterId,
            deviceId = resolvedDeviceId,
            keyPair = identity.keyPair,
        ).serializeToString()
    }

    content {
        FlorisInfoCard(
            modifier = Modifier.defaultFlorisOutlinedBox(),
            text = stringRes(R.string.settings__sync__intro_title),
            secondaryText = stringRes(R.string.settings__sync__intro_summary),
        )

        SyncTransferStatusCard(
            notice = syncTransferNotice,
            exportedCount = syncExportedCount,
            importedInsertCount = syncImportedInsertCount,
            importedUpdateCount = syncImportedUpdateCount,
            importedDeleteCount = syncImportedDeleteCount,
            errorMessage = syncTransferError ?: syncUnknownError,
            onDismiss = { clearTransferNotice() },
        )

        if (compatibility.usesPassphraseDictionaryMigration) {
            FlorisInfoCard(
                modifier = Modifier.defaultFlorisOutlinedBox(),
                text = stringRes(R.string.settings__sync__legacy_fallback_title),
                secondaryText = stringRes(R.string.settings__sync__legacy_fallback_summary),
            )
            PreferenceGroup(title = stringRes(R.string.settings__sync__legacy_group)) {
                Preference(
                    icon = Icons.Outlined.FileUpload,
                    title = stringRes(R.string.settings__sync__legacy_export_encrypted),
                    summary = stringRes(R.string.settings__sync__legacy_export_encrypted_summary),
                    onClick = {
                        navController.navigate(
                            Routes.Settings.UserDictionary(
                                type = UserDictionaryType.FLORIS,
                                action = UserDictionaryScreenAction.EXPORT_ENCRYPTED,
                            ),
                        )
                    },
                )
                Preference(
                    icon = Icons.Outlined.FileDownload,
                    title = stringRes(R.string.settings__sync__legacy_import_encrypted),
                    summary = stringRes(R.string.settings__sync__legacy_import_encrypted_summary),
                    onClick = {
                        navController.navigate(
                            Routes.Settings.UserDictionary(
                                type = UserDictionaryType.FLORIS,
                                action = UserDictionaryScreenAction.IMPORT,
                            ),
                        )
                    },
                )
            }
        } else {
            PreferenceGroup(title = stringRes(R.string.settings__sync__group_actions)) {
                Preference(
                    icon = Icons.Outlined.FileUpload,
                    title = stringRes(R.string.settings__sync__export_now),
                    summary = stringRes(R.string.settings__sync__export_now_summary),
                    onClick = {
                        val target = when (val channel = activeChannel) {
                            is SyncChannel.LocalFolder -> runCatching {
                                resolveLocalFolderSyncFileUri(channel, create = true)
                            }.getOrNull()
                            SyncChannel.ManualExport -> manualExportTargetUri
                                .takeIf { it.isNotBlank() }
                                ?.let { Uri.parse(it) }
                            else -> null
                        }
                        if (target == null) {
                            failTransfer(syncMissingTargetText)
                        } else {
                            scope.launch { exportSyncSnapshot(target) }
                        }
                    },
                )
                Preference(
                    icon = Icons.Outlined.FileDownload,
                    title = stringRes(R.string.settings__sync__import_now),
                    summary = stringRes(R.string.settings__sync__import_now_summary),
                    onClick = {
                        when (val channel = activeChannel) {
                            is SyncChannel.LocalFolder -> {
                                val source = runCatching {
                                    resolveLocalFolderSyncFileUri(channel, create = false)
                                }.getOrNull()
                                if (source == null) {
                                    failTransfer(syncMissingTargetText)
                                } else {
                                    scope.launch { importSyncSnapshot(source) }
                                }
                            }
                            else -> manualImportLauncher.launch(
                                arrayOf(SYNC_FILE_MIME_TYPE, "application/json", "text/json"),
                            )
                        }
                    },
                )
                Preference(
                    icon = Icons.Default.Sync,
                    title = stringRes(R.string.settings__sync__choose_manual_export_target),
                    summary = manualExportTargetUri.ifBlank { manualExportDefaultSummary },
                    onClick = { manualExportLauncher.launch(SYNC_FILE_NAME) },
                )
            }

            PreferenceGroup(title = stringRes(R.string.settings__sync__group_channel)) {
                SyncChannelPreference(
                    selected = activeChannel is SyncChannel.Syncthing,
                    title = stringRes(R.string.settings__sync__channel_syncthing),
                    summary = stringRes(R.string.settings__sync__channel_syncthing_summary),
                    onClick = { syncthingDialogVisible = true },
                )
                SyncChannelPreference(
                    selected = activeChannel is SyncChannel.LocalFolder,
                    title = stringRes(R.string.settings__sync__channel_local_folder),
                    summary = stringRes(R.string.settings__sync__channel_local_folder_summary),
                    onClick = { folderLauncher.launch(null) },
                )
                SyncChannelPreference(
                    selected = activeChannel is SyncChannel.ManualExport,
                    title = stringRes(R.string.settings__sync__channel_manual_export),
                    summary = manualExportTargetUri.ifBlank { manualExportDefaultSummary },
                    onClick = { manualExportLauncher.launch(SYNC_FILE_NAME) },
                )
                SyncChannelPreference(
                    selected = activeChannel is SyncChannel.Disabled,
                    title = stringRes(R.string.settings__sync__channel_disabled),
                    summary = stringRes(R.string.settings__sync__channel_disabled_summary),
                    onClick = { setChannel(SyncChannel.Disabled) },
                )
            }

            PreferenceGroup(title = stringRes(R.string.settings__sync__group_pairing)) {
                Preference(
                    icon = Icons.Default.QrCode2,
                    title = stringRes(R.string.settings__sync__pair_new_device),
                    summary = stringRes(R.string.settings__sync__pair_new_device_summary),
                    onClick = { generatePairingPayload() },
                )
                Preference(
                    icon = Icons.Default.ContentPaste,
                    title = stringRes(R.string.settings__sync__receive_pairing),
                    summary = stringRes(R.string.settings__sync__receive_pairing_summary),
                    onClick = { startReceivePairing() },
                )
                generatedPayload?.let { raw ->
                    SyncQrPayloadCard(raw)
                }
            }

            PreferenceGroup(title = stringRes(R.string.settings__sync__group_devices)) {
                if (pairedDevices.isEmpty()) {
                    Text(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        text = stringRes(R.string.settings__sync__no_paired_devices),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                    ) {
                        items(pairedDevices, key = { it.deviceId }) { device ->
                            PairedDeviceRow(device)
                        }
                    }
                }
            }
        }
    }

    if (syncthingDialogVisible) {
        TextInputDialog(
            title = stringRes(R.string.settings__sync__syncthing_dialog_title),
            label = stringRes(R.string.settings__sync__syncthing_folder_label),
            initialValue = (activeChannel as? SyncChannel.Syncthing)?.folderName ?: "swiftfloris-sync",
            onDismiss = { syncthingDialogVisible = false },
            onConfirm = { folderName ->
                syncthingDialogVisible = false
                setChannel(SyncChannel.Syncthing(folderName.ifBlank { "swiftfloris-sync" }))
            },
        )
    }

    if (scannedPayloadDialogVisible) {
        TextInputDialog(
            title = stringRes(R.string.settings__sync__paste_pairing_payload_title),
            label = stringRes(R.string.settings__sync__paste_pairing_payload_label),
            initialValue = "",
            singleLine = false,
            onDismiss = { scannedPayloadDialogVisible = false },
            onConfirm = { raw ->
                scannedPayloadDialogVisible = false
                receivePayload(raw)
            },
        )
    }
}

@Composable
private fun SyncTransferStatusCard(
    notice: SyncTransferNotice,
    exportedCount: Int?,
    importedInsertCount: Int?,
    importedUpdateCount: Int?,
    importedDeleteCount: Int?,
    errorMessage: String,
    onDismiss: () -> Unit,
) {
    when (notice) {
        SyncTransferNotice.None -> Unit
        SyncTransferNotice.Exporting -> FlorisProgressCard(
            modifier = Modifier.defaultFlorisOutlinedBox(),
            text = stringRes(R.string.settings__sync__export_in_progress),
            secondaryText = stringRes(R.string.settings__sync__export_in_progress_summary),
        )
        SyncTransferNotice.Importing -> FlorisProgressCard(
            modifier = Modifier.defaultFlorisOutlinedBox(),
            text = stringRes(R.string.settings__sync__import_in_progress),
            secondaryText = stringRes(R.string.settings__sync__import_in_progress_summary),
        )
        SyncTransferNotice.ExportSuccess -> FlorisSuccessCard(
            modifier = Modifier.defaultFlorisOutlinedBox(),
            text = stringRes(R.string.settings__sync__export_success),
            secondaryText = exportedCount?.let { count ->
                stringRes(R.string.settings__sync__export_success_summary, "count" to count)
            } ?: stringRes(R.string.settings__sync__export_success_summary_fallback),
            actionLabel = stringRes(R.string.action__ok),
            onClick = onDismiss,
        )
        SyncTransferNotice.ImportSuccess -> FlorisSuccessCard(
            modifier = Modifier.defaultFlorisOutlinedBox(),
            text = stringRes(R.string.settings__sync__import_success),
            secondaryText = if (
                importedInsertCount != null &&
                importedUpdateCount != null &&
                importedDeleteCount != null
            ) {
                stringRes(
                    R.string.settings__sync__import_success_summary,
                    "inserted" to importedInsertCount,
                    "updated" to importedUpdateCount,
                    "deleted" to importedDeleteCount,
                )
            } else {
                stringRes(R.string.settings__sync__import_success_summary_fallback)
            },
            actionLabel = stringRes(R.string.action__ok),
            onClick = onDismiss,
        )
        SyncTransferNotice.Failure -> FlorisErrorCard(
            modifier = Modifier.defaultFlorisOutlinedBox(),
            text = stringRes(R.string.settings__sync__transfer_failure),
            secondaryText = stringRes(
                R.string.settings__sync__transfer_failure_summary,
                "error_message" to errorMessage,
            ),
            actionLabel = stringRes(R.string.action__ok),
            onClick = onDismiss,
        )
    }
}

@Composable
private fun SyncChannelPreference(
    selected: Boolean,
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    JetPrefListItem(
        // One activation target with a RadioButton role (mirrors
        // BackupScreen.RadioListItem): the whole row is the radio control, and
        // the inner RadioButton is non-interactive (onClick = null) so screen
        // readers announce a single selectable radio option, not two targets.
        modifier = Modifier.rippleClickable(role = Role.RadioButton, onClick = onClick),
        text = title,
        secondaryText = summary,
        trailing = {
            RadioButton(
                selected = selected,
                onClick = null,
            )
        },
    )
}

@Composable
private fun SyncQrPayloadCard(rawPayload: String) {
    val context = LocalContext.current
    val clipboardManager by context.clipboardManager()
    val matrix = remember(rawPayload) { SyncQrCode.encode(rawPayload) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringRes(R.string.settings__sync__qr_ready),
            style = MaterialTheme.typography.titleSmall,
        )
        SyncQrCodeCanvas(matrix)
        Text(
            text = stringRes(R.string.settings__sync__qr_ready_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = rawPayload,
            onValueChange = {},
            label = { Text(text = stringRes(R.string.settings__sync__paste_pairing_payload_label)) },
            readOnly = true,
            minLines = 3,
            maxLines = 5,
        )
        TextButton(
            onClick = {
                clipboardManager.addNewPlaintext(rawPayload)
                Toast.makeText(context, R.string.settings__sync__pairing_payload_copied, Toast.LENGTH_SHORT).show()
            },
        ) {
            Text(text = stringRes(R.string.settings__sync__copy_pairing_payload))
        }
    }
}

@Composable
private fun SyncQrCodeCanvas(matrix: SyncQrCodeMatrix) {
    Box(
        modifier = Modifier
            .size(220.dp)
            .background(Color.White, RoundedCornerShape(4.dp))
            .padding(8.dp),
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val cellSize = size.minDimension / matrix.size
            for (y in 0 until matrix.size) {
                for (x in 0 until matrix.size) {
                    if (matrix[x, y]) {
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset(x * cellSize, y * cellSize),
                            size = Size(cellSize, cellSize),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PairedDeviceRow(device: PairedSyncDevice) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Smartphone,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.displayName,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = device.syncChannelId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    label: String,
    initialValue: String,
    singleLine: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by rememberSaveable { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(8.dp),
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = singleLine,
                minLines = if (singleLine) 1 else 4,
                maxLines = if (singleLine) 1 else 8,
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(value.trim()) },
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(stringRes(R.string.action__apply))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(stringRes(R.string.action__cancel))
            }
        },
    )
}

private const val ZXING_SCAN_ACTION = "com.google.zxing.client.android.SCAN"
private const val ZXING_SCAN_MODE = "SCAN_MODE"
private const val ZXING_QR_CODE_MODE = "QR_CODE_MODE"
private const val ZXING_SCAN_RESULT = "SCAN_RESULT"
private const val SYNC_FILE_NAME = "swiftfloris-dictionary-sync.json"
private const val SYNC_FILE_MIME_TYPE = "application/json"

private enum class SyncTransferNotice {
    None,
    Exporting,
    Importing,
    ExportSuccess,
    ImportSuccess,
    Failure,
}

private fun android.database.Cursor.getStringOrNull(index: Int): String? {
    return if (index >= 0 && !isNull(index)) getString(index) else null
}
