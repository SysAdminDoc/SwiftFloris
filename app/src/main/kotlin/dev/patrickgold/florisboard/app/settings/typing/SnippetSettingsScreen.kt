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

package dev.patrickgold.florisboard.app.settings.typing

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.settings.copyImportDiagnosticsToClipboard
import dev.patrickgold.florisboard.ime.importing.ImportDiagnostics
import dev.patrickgold.florisboard.ime.snippet.SnippetImportPolicy
import dev.patrickgold.florisboard.lib.compose.FlorisConfirmDeleteDialog
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.snippetManager
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import dev.patrickgold.jetpref.material.ui.JetPrefListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.compose.FlorisEmptyState
import org.florisboard.lib.compose.FlorisErrorCard
import org.florisboard.lib.compose.FlorisIconButton
import org.florisboard.lib.compose.FlorisSuccessCard
import org.florisboard.lib.compose.FlorisWarningCard
import org.florisboard.lib.compose.stringRes

@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun SnippetSettingsScreen() = FlorisScreen {
    title = stringRes(R.string.settings__snippet__title)
    previewFieldVisible = false

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snippetManager by context.snippetManager()
    val snippets by snippetManager.snippets.collectAsState()
    val fileStates by snippetManager.fileStates.collectAsState()
    val loadReport by snippetManager.loadReport.collectAsState()

    val importSuccessTemplate = stringRes(R.string.settings__snippet__import_success__toast)
    val importFailedText = stringRes(R.string.settings__snippet__import_failed__toast)
    val importReadErrorText = stringRes(R.string.settings__snippet__import_read_error__toast)
    val fileRemovedText = stringRes(R.string.settings__snippet__file_removed__toast)
    val fileRemoveFailedText = stringRes(R.string.settings__snippet__file_remove_failed__toast)
    val clearedText = stringRes(R.string.settings__snippet__clear_all__toast)
    val clearFailedText = stringRes(R.string.settings__snippet__clear_failed__toast)
    val acceptedSnippetMimeTypes = remember {
        arrayOf(
            "application/x-yaml",
            "text/yaml",
            "text/x-yaml",
            "text/plain",
            "*/*",
        )
    }
    var deleteCandidate by remember { mutableStateOf<String?>(null) }
    var showClearAllConfirmation by remember { mutableStateOf(false) }
    var importNotice by remember { mutableStateOf<SnippetImportNotice?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = runCatching {
                val contentResolver = context.contentResolver
                val yamlContent = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use(SnippetImportPolicy::readYamlTextLimited)
                } ?: error("null stream")
                val filename = uri.lastPathSegment.orEmpty().ifBlank { "import.yml" }
                snippetManager.importYaml(yamlContent, filename)
            }
            result.onSuccess { importResult ->
                if (importResult.importedCount > 0) {
                    importNotice = SnippetImportNotice.Imported(
                        importedCount = importResult.importedCount,
                        diagnostics = importResult.diagnostics,
                    )
                    Toast.makeText(
                        context,
                        importSuccessTemplate.replace("{count}", importResult.importedCount.toString()),
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    importNotice = SnippetImportNotice.NoValidTriggers(importResult.diagnostics)
                    Toast.makeText(context, importFailedText, Toast.LENGTH_LONG).show()
                }
            }.onFailure {
                importNotice = SnippetImportNotice.ReadFailure
                Toast.makeText(context, importReadErrorText, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun launchImportPicker() {
        filePickerLauncher.launch(acceptedSnippetMimeTypes)
    }

    LaunchedEffect(Unit) {
        snippetManager.loadAll()
    }

    content {
        if (fileStates.isEmpty() && snippets.isEmpty()) {
            FlorisEmptyState(
                modifier = Modifier.padding(16.dp),
                icon = Icons.AutoMirrored.Filled.TextSnippet,
                title = stringRes(R.string.settings__snippet__empty_title),
                message = stringRes(R.string.settings__snippet__empty_message),
                actionLabel = stringRes(R.string.settings__snippet__import_yaml),
                onAction = { launchImportPicker() },
            )
        }

        PreferenceGroup(title = stringRes(R.string.settings__snippet__group_files)) {
            Preference(
                icon = Icons.Default.FileOpen,
                title = stringRes(R.string.settings__snippet__import_yaml),
                summary = stringRes(R.string.settings__snippet__import_yaml__summary),
                onClick = { launchImportPicker() },
            )
            importNotice?.let { notice ->
                SnippetImportNoticeCard(
                    notice = notice,
                    modifier = Modifier.padding(8.dp),
                )
            }
            if (loadReport.skippedFileCount > 0) {
                FlorisWarningCard(
                    modifier = Modifier.padding(8.dp),
                    text = stringRes(R.string.settings__snippet__load_warning_title),
                    secondaryText = stringRes(
                        R.string.settings__snippet__load_warning_summary,
                        "count" to loadReport.skippedFileCount,
                    ),
                )
            }
            for (fileState in fileStates) {
                JetPrefListItem(
                    text = fileState.filename,
                    secondaryText = stringRes(
                        R.string.settings__snippet__file_triggers,
                        "count" to fileState.triggerCount,
                    ),
                    trailing = {
                        FlorisIconButton(
                            onClick = { deleteCandidate = fileState.filename },
                            icon = Icons.Default.Delete,
                            contentDescription = stringRes(
                                R.string.settings__snippet__delete_file_a11y,
                                "filename" to fileState.filename,
                            ),
                        )
                    },
                )
            }
            if (fileStates.isNotEmpty()) {
                Preference(
                    icon = Icons.Default.DeleteSweep,
                    title = stringRes(R.string.settings__snippet__clear_all),
                    summary = stringRes(R.string.settings__snippet__clear_all__summary),
                    onClick = { showClearAllConfirmation = true },
                )
            }
        }

        if (snippets.isNotEmpty()) {
            PreferenceGroup(title = stringRes(R.string.settings__snippet__group_preview)) {
                for (match in snippets) {
                    val preview = if (match.replace.length > 80) {
                        match.replace.take(80) + "..."
                    } else {
                        match.replace
                    }
                    JetPrefListItem(
                        text = match.trigger,
                        secondaryText = stringRes(R.string.settings__snippet__trigger_arrow) + " " + preview,
                    )
                }
            }
        }
    }

    deleteCandidate?.let { filename ->
        FlorisConfirmDeleteDialog(
            what = filename,
            onConfirm = {
                scope.launch {
                    val removed = snippetManager.removeFile(filename)
                    deleteCandidate = null
                    importNotice = null
                    Toast.makeText(
                        context,
                        if (removed) fileRemovedText else fileRemoveFailedText,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            onDismiss = { deleteCandidate = null },
        )
    }

    if (showClearAllConfirmation) {
        JetPrefAlertDialog(
            title = stringRes(R.string.settings__snippet__clear_all_confirm_title),
            confirmLabel = stringRes(R.string.settings__snippet__clear_all),
            onConfirm = {
                scope.launch {
                    val cleared = snippetManager.clearAll()
                    showClearAllConfirmation = false
                    importNotice = null
                    Toast.makeText(
                        context,
                        if (cleared) clearedText else clearFailedText,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            dismissLabel = stringRes(R.string.action__cancel),
            onDismiss = { showClearAllConfirmation = false },
        ) {
            Text(text = stringRes(R.string.settings__snippet__clear_all_confirm_message))
        }
    }
}

private sealed interface SnippetImportNotice {
    data class Imported(
        val importedCount: Int,
        val diagnostics: ImportDiagnostics,
    ) : SnippetImportNotice

    data class NoValidTriggers(val diagnostics: ImportDiagnostics) : SnippetImportNotice

    data object ReadFailure : SnippetImportNotice
}

@Composable
private fun SnippetImportNoticeCard(
    notice: SnippetImportNotice,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    when (notice) {
        is SnippetImportNotice.Imported -> {
            val diagnosticSummary = notice.diagnostics.summary()
            if (notice.diagnostics.hasSkipped && diagnosticSummary.isNotBlank()) {
                FlorisWarningCard(
                    modifier = modifier,
                    text = stringRes(R.string.settings__snippet__import_warning_title),
                    secondaryText = stringRes(
                        R.string.settings__snippet__import_warning_summary,
                        "count" to notice.importedCount,
                        "details" to diagnosticSummary,
                    ),
                    actionLabel = stringRes(R.string.import_diagnostics__copy_details),
                    onClick = { copyImportDiagnosticsToClipboard(context, diagnosticSummary) },
                )
            } else {
                FlorisSuccessCard(
                    modifier = modifier,
                    text = stringRes(R.string.settings__snippet__import_success_title),
                    secondaryText = stringRes(
                        R.string.settings__snippet__import_success_summary,
                        "count" to notice.importedCount,
                    ),
                )
            }
        }
        is SnippetImportNotice.NoValidTriggers -> {
            val diagnosticSummary = notice.diagnostics.summary()
            FlorisErrorCard(
                modifier = modifier,
                text = stringRes(R.string.settings__snippet__import_failed_title),
                secondaryText = if (diagnosticSummary.isNotBlank()) {
                    stringRes(
                        R.string.settings__snippet__import_failed_with_details,
                        "details" to diagnosticSummary,
                    )
                } else {
                    stringRes(R.string.settings__snippet__import_failed_summary)
                },
                actionLabel = if (diagnosticSummary.isNotBlank()) {
                    stringRes(R.string.import_diagnostics__copy_details)
                } else {
                    null
                },
                onClick = if (diagnosticSummary.isNotBlank()) {
                    { copyImportDiagnosticsToClipboard(context, diagnosticSummary) }
                } else {
                    null
                },
            )
        }
        SnippetImportNotice.ReadFailure -> FlorisErrorCard(
            modifier = modifier,
            text = stringRes(R.string.settings__snippet__import_read_error_title),
            secondaryText = stringRes(R.string.settings__snippet__import_read_error_summary),
        )
    }
}
