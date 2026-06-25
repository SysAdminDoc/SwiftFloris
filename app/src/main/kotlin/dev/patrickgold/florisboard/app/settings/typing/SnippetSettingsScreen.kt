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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.snippet.EspansoMatchParser
import dev.patrickgold.florisboard.ime.snippet.SnippetManager
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.snippetManager
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.jetpref.material.ui.JetPrefListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.compose.FlorisEmptyState
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
    val files = remember(snippets) { snippetManager.listFiles() }

    val importSuccessTemplate = stringRes(R.string.settings__snippet__import_success__toast)
    val importFailedText = stringRes(R.string.settings__snippet__import_failed__toast)
    val importReadErrorText = stringRes(R.string.settings__snippet__import_read_error__toast)
    val fileRemovedText = stringRes(R.string.settings__snippet__file_removed__toast)
    val clearedText = stringRes(R.string.settings__snippet__clear_all__toast)

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = runCatching {
                val contentResolver = context.contentResolver
                val bytes = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } ?: error("null stream")
                val yamlContent = bytes.decodeToString()
                val filename = uri.lastPathSegment.orEmpty().ifBlank { "import.yml" }
                snippetManager.importYaml(yamlContent, filename)
            }
            result.onSuccess { count ->
                if (count > 0) {
                    Toast.makeText(
                        context,
                        importSuccessTemplate.replace("{count}", count.toString()),
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    Toast.makeText(context, importFailedText, Toast.LENGTH_LONG).show()
                }
            }.onFailure {
                Toast.makeText(context, importReadErrorText, Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        snippetManager.loadAll()
    }

    content {
        if (files.isEmpty() && snippets.isEmpty()) {
            FlorisEmptyState(
                modifier = Modifier.padding(16.dp),
                icon = Icons.AutoMirrored.Filled.TextSnippet,
                title = stringRes(R.string.settings__snippet__empty_title),
                message = stringRes(R.string.settings__snippet__empty_message),
            )
        }

        PreferenceGroup(title = stringRes(R.string.settings__snippet__group_files)) {
            Preference(
                icon = Icons.Default.FileOpen,
                title = stringRes(R.string.settings__snippet__import_yaml),
                summary = stringRes(R.string.settings__snippet__import_yaml__summary),
                onClick = {
                    filePickerLauncher.launch(
                        arrayOf(
                            "application/x-yaml",
                            "text/yaml",
                            "text/x-yaml",
                            "text/plain",
                            "*/*",
                        ),
                    )
                },
            )
            for (filename in files) {
                val triggerCount = remember(filename, snippets) {
                    runCatching {
                        val file = java.io.File(context.filesDir, "snippets/$filename")
                        if (file.exists()) EspansoMatchParser.parse(file.readText()).size else 0
                    }.getOrDefault(0)
                }
                JetPrefListItem(
                    text = filename,
                    secondaryText = stringRes(
                        R.string.settings__snippet__file_triggers,
                        "count" to triggerCount,
                    ),
                    trailing = {
                        IconButton(onClick = {
                            snippetManager.removeFile(filename)
                            Toast.makeText(context, fileRemovedText, Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringRes(R.string.settings__snippet__file_removed__toast),
                            )
                        }
                    },
                )
            }
            if (files.isNotEmpty()) {
                Preference(
                    icon = Icons.Default.DeleteSweep,
                    title = stringRes(R.string.settings__snippet__clear_all),
                    summary = stringRes(R.string.settings__snippet__clear_all__summary),
                    onClick = {
                        snippetManager.clearAll()
                        Toast.makeText(context, clearedText, Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }

        if (snippets.isNotEmpty()) {
            PreferenceGroup(title = stringRes(R.string.settings__snippet__group_preview)) {
                for (match in snippets) {
                    val preview = if (match.replace.length > 80) {
                        match.replace.take(80) + "…"
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
}
