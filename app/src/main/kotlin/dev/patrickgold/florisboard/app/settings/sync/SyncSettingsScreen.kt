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
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.QrCode2
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
import dev.patrickgold.florisboard.ime.sync.PairedSyncDevice
import dev.patrickgold.florisboard.ime.sync.PairedSyncDeviceList
import dev.patrickgold.florisboard.ime.sync.PairingPayload
import dev.patrickgold.florisboard.ime.sync.PairingPayloadGenerator
import dev.patrickgold.florisboard.ime.sync.SyncChannel
import dev.patrickgold.florisboard.ime.sync.SyncQrCode
import dev.patrickgold.florisboard.ime.sync.SyncQrCodeMatrix
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.jetpref.material.ui.JetPrefListItem
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes
import java.util.UUID

@Composable
fun SyncSettingsScreen() = FlorisScreen {
    title = stringRes(R.string.settings__sync__title)
    previewFieldVisible = false

    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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

    val folderPickedText = stringRes(R.string.settings__sync__folder_selected)
    val manualExportPickedText = stringRes(R.string.settings__sync__manual_export_target_selected)
    val pairingReceivedText = stringRes(R.string.settings__sync__pairing_received)
    val pairingInvalidText = stringRes(R.string.settings__sync__pairing_invalid)
    val scannerMissingText = stringRes(R.string.settings__sync__scanner_missing)
    val pairingUnsupportedText = stringRes(R.string.settings__sync__pairing_requires_android_13)
    val manualExportDefaultSummary = stringRes(R.string.settings__sync__channel_manual_export_summary)

    fun setChannel(channel: SyncChannel) {
        scope.launch { prefs.sync.channelId.set(channel.channelId) }
    }

    fun receivePayload(rawPayload: String) {
        val payload = PairingPayload.parse(rawPayload)
        if (payload == null) {
            Toast.makeText(context, pairingInvalidText, Toast.LENGTH_LONG).show()
            return
        }
        val device = PairedSyncDevice.fromPayload(payload, pairedAtMillis = System.currentTimeMillis())
        scope.launch {
            prefs.sync.pairedDevicesJson.set(PairedSyncDeviceList.upsert(pairedDevicesJson, device))
            prefs.sync.channelId.set(payload.syncChannelId)
        }
        Toast.makeText(context, pairingReceivedText, Toast.LENGTH_SHORT).show()
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
        generatedPayload = PairingPayloadGenerator.generate(
            displayName = Build.MODEL ?: "Android device",
            syncChannelId = activeChannel.channelId,
            clusterId = resolvedClusterId,
            deviceId = resolvedDeviceId,
        ).serializeToString()
    }

    content {
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
                onClick = { manualExportLauncher.launch("swiftfloris-sync.json") },
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
