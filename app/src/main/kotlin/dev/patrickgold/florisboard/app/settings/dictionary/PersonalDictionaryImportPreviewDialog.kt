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

package dev.patrickgold.florisboard.app.settings.dictionary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.dictionary.DictionaryImportFormat
import dev.patrickgold.florisboard.ime.dictionary.PersonalDictionaryEntry
import org.florisboard.lib.compose.rippleClickable
import org.florisboard.lib.compose.stringRes

private const val ImportPreviewRowLimit = 50

data class PersonalDictionaryImportPreview(
    val entries: List<PersonalDictionaryEntry>,
    val format: DictionaryImportFormat?,
)

@Composable
fun PersonalDictionaryImportPreviewDialog(
    preview: PersonalDictionaryImportPreview,
    onImport: (excludedEntryIndexes: Set<Int>, skipFuturePreview: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var excludedEntryIndexes by remember(preview) { mutableStateOf(emptySet<Int>()) }
    var skipFuturePreview by remember(preview) { mutableStateOf(false) }
    val rows = remember(preview) {
        preview.entries.take(ImportPreviewRowLimit).mapIndexed { index, entry ->
            IndexedPreviewEntry(index, entry)
        }
    }
    val selectedCount = preview.entries.size - excludedEntryIndexes.size

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringRes(R.string.settings__udm__import_preview__title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringRes(
                        R.string.settings__udm__import_preview__summary,
                        "count" to preview.entries.size,
                    ),
                )
                Text(
                    text = stringRes(
                        R.string.settings__udm__import_preview__selected_count,
                        "selected_count" to selectedCount,
                        "total_count" to preview.entries.size,
                    ),
                )
                if (preview.entries.size > rows.size) {
                    Text(
                        text = stringRes(
                            R.string.settings__udm__import_preview__first_rows,
                            "shown_count" to rows.size,
                            "total_count" to preview.entries.size,
                        ),
                    )
                }
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                ) {
                    items(rows, key = { it.index }) { row ->
                        val checked = row.index !in excludedEntryIndexes
                        ImportPreviewRow(
                            row = row,
                            checked = checked,
                            onCheckedChange = { isChecked ->
                                excludedEntryIndexes = if (isChecked) {
                                    excludedEntryIndexes - row.index
                                } else {
                                    excludedEntryIndexes + row.index
                                }
                            },
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .rippleClickable(role = Role.Checkbox) {
                            skipFuturePreview = !skipFuturePreview
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = skipFuturePreview,
                        onCheckedChange = null,
                    )
                    Text(text = stringRes(R.string.settings__udm__import_preview__skip_next_time))
                }
            }
        },
        confirmButton = {
            Button(
                enabled = selectedCount > 0,
                onClick = { onImport(excludedEntryIndexes, skipFuturePreview) },
            ) {
                Text(text = stringRes(R.string.settings__udm__import_preview__import_selected))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringRes(R.string.action__cancel))
            }
        },
    )
}

@Composable
private fun ImportPreviewRow(
    row: IndexedPreviewEntry,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .rippleClickable(role = Role.Checkbox) { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.entry.word.ifBlank { " " },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = row.entry.detailLine(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PersonalDictionaryEntry.detailLine(): String {
    val frequencyText = stringRes(R.string.settings__udm__import_preview__entry_frequency, "freq" to frequency)
    val localeText = locale?.takeIf { it.isNotBlank() }?.let {
        stringRes(R.string.settings__udm__import_preview__entry_language, "locale" to it)
    }
    val shortcutText = shortcut?.takeIf { it.isNotBlank() }?.let {
        stringRes(R.string.settings__udm__import_preview__entry_shortcut, "shortcut" to it)
    }
    return listOfNotNull(frequencyText, localeText, shortcutText).joinToString(separator = " | ")
}

private data class IndexedPreviewEntry(
    val index: Int,
    val entry: PersonalDictionaryEntry,
)
