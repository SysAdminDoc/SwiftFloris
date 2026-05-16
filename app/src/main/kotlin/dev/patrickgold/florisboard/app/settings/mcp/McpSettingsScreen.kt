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

package dev.patrickgold.florisboard.app.settings.mcp

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.runtime.Composable
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.mcp.DaemonEntry
import dev.patrickgold.florisboard.ime.mcp.McpDaemonRegistry
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import org.florisboard.lib.compose.stringRes
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup

/**
 * ROADMAP §10.5 L7.6 — Settings → Privacy → MCP daemon bridge.
 *
 * Read-only listing of every MCP daemon the IME bound to at startup
 * (via [dev.patrickgold.florisboard.ime.mcp.McpServiceLifecycle.start]),
 * plus the tools each daemon advertises. Per-daemon enable / disable
 * + a runtime re-scan rides as the L7.6b sub-slice.
 *
 * The screen reads `McpDaemonRegistry.active()` once on entry — the
 * registry is rebuilt at IME service startup, so the snapshot is
 * stable for the life of the screen.
 */
@Composable
fun McpSettingsScreen() = FlorisScreen {
    title = stringRes(R.string.settings__mcp__title)
    previewFieldVisible = false

    val activeDaemons = McpDaemonRegistry.active()

    content {
        PreferenceGroup(title = stringRes(R.string.settings__mcp__group_status)) {
            if (activeDaemons.isEmpty()) {
                Preference(
                    icon = Icons.Default.Extension,
                    title = stringRes(R.string.settings__mcp__status_no_daemons),
                    summary = stringRes(R.string.settings__mcp__status_no_daemons_summary),
                )
            } else {
                Preference(
                    icon = Icons.Default.Extension,
                    title = stringRes(R.string.settings__mcp__status_bound_title),
                    summary = stringRes(R.string.settings__mcp__status_bound_summary)
                        .replace("{count}", activeDaemons.size.toString()),
                )
            }
        }

        if (activeDaemons.isNotEmpty()) {
            PreferenceGroup(title = stringRes(R.string.settings__mcp__group_daemons)) {
                for ((_, entry) in activeDaemons) {
                    DaemonRow(entry)
                }
            }
        }
    }
}

@Composable
private fun DaemonRow(entry: DaemonEntry) {
    Preference(
        icon = Icons.Default.PlayCircleOutline,
        title = entry.key.packageName,
        summary = buildString {
            append(stringRes(R.string.settings__mcp__daemon_protocol).replace(
                "{version}",
                entry.protocolVersion.toString(),
            ))
            append(" · ")
            append(stringRes(R.string.settings__mcp__daemon_tools_count).replace(
                "{count}",
                entry.tools.size.toString(),
            ))
            if (entry.tools.isNotEmpty()) {
                append("\n")
                append(entry.tools.joinToString(separator = ", ") { it.name })
            }
        },
    )
}
