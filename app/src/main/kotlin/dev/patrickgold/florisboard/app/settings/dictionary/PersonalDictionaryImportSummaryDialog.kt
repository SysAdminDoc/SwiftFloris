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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.dictionary.DictionaryImportFormat
import dev.patrickgold.florisboard.ime.dictionary.PersonalDictionaryImportResult
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import org.florisboard.lib.compose.stringRes

/**
 * docs/archive/SWIFTKEY_PARITY_ROADMAP_2026-05-17 §A2 — post-import confirmation
 * dialog. Surfaces after a successful modular `DictionaryImporter` run
 * and shows what landed in the personal dictionary, with an explicit
 * Undo action backed by `PersonalDictionaryImportBatch.rollback`.
 *
 * The Undo affordance is the load-bearing UX promise: it must do nothing
 * when there's nothing to undo (an import that only updated existing
 * entries leaves no rollback-eligible rows). When `result.isRollbackable`
 * is false we hide the Undo button entirely so users don't tap it and
 * see "Removed 0 imported words" — which would read as a bug.
 */
@Composable
fun PersonalDictionaryImportSummaryDialog(
    result: PersonalDictionaryImportResult,
    onKeep: () -> Unit,
    onRollback: () -> Unit,
) {
    JetPrefAlertDialog(
        title = stringRes(R.string.settings__udm__import_summary__title),
        confirmLabel = stringRes(R.string.settings__udm__import_summary__keep),
        onConfirm = onKeep,
        dismissLabel = if (result.isRollbackable) {
            stringRes(R.string.settings__udm__import_summary__rollback)
        } else {
            null
        },
        onDismiss = if (result.isRollbackable) {
            onRollback
        } else {
            onKeep
        },
    ) {
        Column(
            modifier = Modifier.padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (result.noChanges) {
                Text(text = stringRes(R.string.settings__udm__import_summary__no_changes))
            } else {
                if (result.insertedCount > 0) {
                    Text(
                        text = stringRes(
                            R.string.settings__udm__import_summary__inserted,
                            "count" to result.insertedCount,
                        ),
                    )
                }
                if (result.updatedExistingCount > 0) {
                    Text(
                        text = stringRes(
                            R.string.settings__udm__import_summary__updated,
                            "count" to result.updatedExistingCount,
                        ),
                    )
                    // An in-place update replaces freq and shortcut outright and
                    // the undo action only removes inserted rows, so say so
                    // rather than letting "Updated N existing words" read as
                    // additive.
                    Text(
                        text = stringRes(R.string.settings__udm__import_summary__updated_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (result.skippedCount > 0) {
                Text(
                    text = stringRes(
                        R.string.settings__udm__import_summary__skipped,
                        "count" to result.skippedCount,
                    ),
                )
            }
            if (result.excludedCount > 0) {
                Text(
                    text = stringRes(
                        R.string.settings__udm__import_summary__excluded,
                        "count" to result.excludedCount,
                    ),
                )
            }
            result.format?.let { format ->
                Text(
                    text = stringRes(
                        R.string.settings__udm__import_summary__source,
                        "format" to stringRes(format.labelRes()),
                    ),
                )
            }
        }
    }
}

private fun DictionaryImportFormat.labelRes(): Int {
    return when (this) {
        DictionaryImportFormat.JSON -> R.string.settings__udm__import_summary__format__json
        DictionaryImportFormat.XML -> R.string.settings__udm__import_summary__format__xml
        DictionaryImportFormat.CSV -> R.string.settings__udm__import_summary__format__csv
        DictionaryImportFormat.ZIP -> R.string.settings__udm__import_summary__format__zip
        DictionaryImportFormat.FLORIS -> R.string.settings__udm__import_summary__format__floris
        DictionaryImportFormat.UNKNOWN -> R.string.settings__udm__import_summary__format__unknown
    }
}
