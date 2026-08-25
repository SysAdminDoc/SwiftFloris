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

package dev.patrickgold.florisboard.app.settings.advanced

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.core.net.toUri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.patrickgold.florisboard.R
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.compose.FlorisErrorCard
import org.florisboard.lib.compose.FlorisInfoCard
import org.florisboard.lib.compose.FlorisOutlinedBox
import org.florisboard.lib.compose.defaultFlorisOutlinedBox
import org.florisboard.lib.compose.rippleClickable
import dev.patrickgold.jetpref.material.ui.JetPrefListItem
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
internal fun ScheduledBackupPanel(context: Context) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(ScheduledBackupStore.load(context)) }
    var showPassphraseDialog by remember { mutableStateOf(false) }

    fun refresh() {
        settings = ScheduledBackupStore.load(context)
    }

    fun reconcile() {
        ScheduledBackupScheduler.reconcile(context)
        refresh()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val grantFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val granted = runCatching {
            context.contentResolver.takePersistableUriPermission(uri, grantFlags)
        }.isSuccess
        if (!granted) {
            showScheduledBackupToast(context, R.string.scheduled_backup__folder_permission_failed)
            return@rememberLauncherForActivityResult
        }
        val previousUri = settings.treeUri
        if (previousUri.isNotBlank() && previousUri != uri.toString()) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    previousUri.toUri(),
                    grantFlags,
                )
            }
        }
        ScheduledBackupStore.setTreeUri(context, uri.toString())
        showScheduledBackupToast(context, R.string.scheduled_backup__folder_selected)
        if (settings.enabled) reconcile() else refresh()
    }

    FlorisInfoCard(
        modifier = Modifier.padding(8.dp),
        text = stringRes(R.string.scheduled_backup__summary),
        secondaryText = stringRes(R.string.scheduled_backup__summary_detail),
    )
    FlorisOutlinedBox(
        modifier = Modifier.defaultFlorisOutlinedBox(),
        title = stringRes(R.string.scheduled_backup__title),
    ) {
        CheckboxListItem(
            onClick = {
                if (settings.enabled) {
                    ScheduledBackupStore.setEnabled(context, false)
                    ScheduledBackupScheduler.cancel(context)
                    refresh()
                } else if (settings.treeUri.isBlank()) {
                    showScheduledBackupToast(context, R.string.scheduled_backup__choose_folder_first)
                } else if (!settings.hasPassphrase) {
                    showScheduledBackupToast(context, R.string.scheduled_backup__set_passphrase_first)
                } else {
                    ScheduledBackupStore.setEnabled(context, true)
                    ScheduledBackupScheduler.schedule(context, settings.cadence)
                    showScheduledBackupToast(context, R.string.scheduled_backup__enabled)
                    refresh()
                }
            },
            checked = settings.enabled,
            text = stringRes(R.string.scheduled_backup__enabled_title),
            secondaryText = stringRes(R.string.scheduled_backup__enabled_summary),
        )
        JetPrefListItem(
            modifier = Modifier.rippleClickable(
                role = Role.Button,
                onClick = { folderLauncher.launch(null) },
            ),
            text = stringRes(R.string.scheduled_backup__folder_title),
            secondaryText = if (settings.treeUri.isBlank()) {
                stringRes(R.string.scheduled_backup__folder_not_selected)
            } else {
                context.getString(
                    R.string.scheduled_backup__folder_selected_summary,
                    settings.treeUri.substringAfterLast('/').ifBlank { settings.treeUri },
                )
            },
        )
        JetPrefListItem(
            modifier = Modifier.rippleClickable(
                role = Role.Button,
                onClick = { showPassphraseDialog = true },
            ),
            text = stringRes(R.string.scheduled_backup__passphrase_title),
            secondaryText = if (settings.hasPassphrase) {
                stringRes(R.string.scheduled_backup__passphrase_configured)
            } else {
                stringRes(R.string.scheduled_backup__passphrase_not_configured)
            },
        )
        RadioListItem(
            onClick = {
                ScheduledBackupStore.setCadence(context, ScheduledBackupCadence.DAILY)
                if (settings.enabled) reconcile() else refresh()
            },
            selected = settings.cadence == ScheduledBackupCadence.DAILY,
            text = stringRes(R.string.scheduled_backup__daily),
            secondaryText = stringRes(R.string.scheduled_backup__daily_summary),
        )
        RadioListItem(
            onClick = {
                ScheduledBackupStore.setCadence(context, ScheduledBackupCadence.WEEKLY)
                if (settings.enabled) reconcile() else refresh()
            },
            selected = settings.cadence == ScheduledBackupCadence.WEEKLY,
            text = stringRes(R.string.scheduled_backup__weekly),
            secondaryText = stringRes(R.string.scheduled_backup__weekly_summary),
        )
        for (retention in ScheduledBackupPolicy.RetentionOptions) {
            RadioListItem(
                onClick = {
                    ScheduledBackupStore.setRetention(context, retention)
                    refresh()
                },
                selected = settings.retentionCount == retention,
                text = context.resources.getQuantityString(
                    R.plurals.scheduled_backup__retention_option,
                    retention,
                    retention,
                ),
                secondaryText = if (retention == ScheduledBackupPolicy.DefaultRetentionCount) {
                    stringRes(R.string.scheduled_backup__retention_default_summary)
                } else {
                    null
                },
            )
        }
    }

    when {
        settings.lastFailureAt > settings.lastSuccessAt -> FlorisErrorCard(
            modifier = Modifier.padding(8.dp),
            text = stringRes(R.string.scheduled_backup__last_failure_title),
            secondaryText = context.getString(
                R.string.scheduled_backup__last_failure_summary,
                formatScheduledBackupTime(settings.lastFailureAt),
                settings.lastFailureMessage.ifBlank {
                    context.getString(R.string.scheduled_backup__unknown_failure)
                },
            ),
        )
        settings.lastSuccessAt > 0L -> FlorisInfoCard(
            modifier = Modifier.padding(8.dp),
            text = stringRes(R.string.scheduled_backup__last_success_title),
            secondaryText = context.getString(
                R.string.scheduled_backup__last_success_summary,
                formatScheduledBackupTime(settings.lastSuccessAt),
                settings.lastArchiveName,
            ),
        )
    }

    if (showPassphraseDialog) {
        BackupPassphraseDialog(
            title = stringRes(R.string.scheduled_backup__passphrase_dialog_title),
            message = stringRes(R.string.scheduled_backup__passphrase_dialog_message),
            confirmLabel = stringRes(R.string.scheduled_backup__passphrase_save),
            requireConfirmation = true,
            onDismiss = { showPassphraseDialog = false },
            onConfirm = { passphrase ->
                showPassphraseDialog = false
                scope.launch {
                    val saved = runCatching {
                        ScheduledBackupStore.savePassphrase(context, passphrase)
                    }.getOrDefault(false)
                    passphrase.fill('\u0000')
                    if (!saved) {
                        showScheduledBackupToast(context, R.string.scheduled_backup__passphrase_failed)
                    } else {
                        showScheduledBackupToast(context, R.string.scheduled_backup__passphrase_saved)
                        if (settings.enabled) reconcile() else refresh()
                    }
                }
            },
        )
    }
}

private fun formatScheduledBackupTime(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))

private fun showScheduledBackupToast(context: Context, messageId: Int) {
    Toast.makeText(context, messageId, Toast.LENGTH_LONG).show()
}
