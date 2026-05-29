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

package dev.patrickgold.florisboard.app.settings.privacy

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.ime.smartcompose.AddonAuditExport
import dev.patrickgold.florisboard.ime.smartcompose.AddonInvocationAudit
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import org.florisboard.lib.compose.stringRes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MAX_DISPLAYED_RECORDS = 100

/**
 * RESEARCH_FEATURE_PLAN.md F7 — Settings → Privacy → "Local audit log".
 *
 * Surfaces the existing [AddonInvocationAudit] ring (smart-compose / translation /
 * MCP cross-process calls SwiftFloris made on the user's behalf). This is a
 * **display-only** trust surface: it reads the in-memory ring, never collects
 * anything new, and every record is PII-safe by construction (categorical
 * subjects only — package name / language-pair / daemon::tool, never typed text).
 * "Copy log as JSON" exports the bundle to the clipboard (on-device; the user
 * decides where it goes); "Clear log" drops the ring.
 */
@Composable
fun PrivacyAuditScreen() = FlorisScreen {
    title = stringRes(R.string.settings__privacy_audit__title)
    previewFieldVisible = false

    val context = LocalContext.current
    val clipboardManager by context.clipboardManager()

    var refreshTick by remember { mutableLongStateOf(0L) }

    content {
        // refreshTick is read so re-snapshots recompose the list after Clear.
        @Suppress("UNUSED_EXPRESSION") refreshTick
        val records = remember(refreshTick) { AddonInvocationAudit.snapshot() }
        val summaryLine = remember(refreshTick) { AddonAuditExport.summaryLine(records) }

        PreferenceGroup(title = stringRes(R.string.settings__privacy_audit__group_summary)) {
            Text(
                text = stringRes(R.string.settings__privacy_audit__intro),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Preference(
                icon = Icons.Default.Shield,
                title = stringRes(R.string.settings__privacy_audit__summary_title),
                summary = summaryLine,
            )
        }

        PreferenceGroup(title = stringRes(R.string.settings__privacy_audit__group_actions)) {
            Preference(
                icon = Icons.Default.ContentCopy,
                title = stringRes(R.string.settings__privacy_audit__export),
                summary = stringRes(R.string.settings__privacy_audit__export__summary),
                enabledIf = { records.isNotEmpty() },
                onClick = {
                    runCatching {
                        clipboardManager.addNewPlaintext(AddonAuditExport.toJsonString(records = records))
                    }
                    Toast.makeText(context, R.string.settings__privacy_audit__export_toast, Toast.LENGTH_LONG).show()
                },
            )
            Preference(
                icon = Icons.Default.DeleteSweep,
                title = stringRes(R.string.settings__privacy_audit__clear),
                summary = stringRes(R.string.settings__privacy_audit__clear__summary),
                enabledIf = { records.isNotEmpty() },
                onClick = {
                    AddonInvocationAudit.clear()
                    refreshTick = refreshTick + 1L
                    Toast.makeText(context, R.string.settings__privacy_audit__clear_toast, Toast.LENGTH_LONG).show()
                },
            )
        }

        PreferenceGroup(title = stringRes(R.string.settings__privacy_audit__group_records)) {
            if (records.isEmpty()) {
                Text(
                    text = stringRes(R.string.settings__privacy_audit__empty),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            } else {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    for (record in records.asReversed().take(MAX_DISPLAYED_RECORDS)) {
                        Preference(
                            title = "${prettyEnum(record.surface.name)} · ${prettyEnum(record.outcome.name)}",
                            summary = buildString {
                                append(formatTimestamp(record.timestampMillis))
                                record.subject?.let { append("  ·  ").append(it) }
                                record.reason?.let { append("\n").append(it) }
                            },
                        )
                    }
                }
            }
        }
    }
}

private val TIMESTAMP_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

private fun formatTimestamp(millis: Long): String = TIMESTAMP_FORMAT.format(Date(millis))

/** "SMART_COMPOSE" → "Smart compose". Categorical enum names only; no localization churn. */
private fun prettyEnum(raw: String): String =
    raw.lowercase(Locale.US).replace('_', ' ').replaceFirstChar { it.titlecase(Locale.US) }
