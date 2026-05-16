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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.mcp.DaemonEntry
import dev.patrickgold.florisboard.ime.mcp.DisabledDaemonSet
import dev.patrickgold.florisboard.ime.mcp.McpDaemonRegistry
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.observeAsState
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

/**
 * ROADMAP §10.5 L7.6 + L7.6b — Settings → MCP daemon bridge.
 *
 * Reads `McpDaemonRegistry.active()` once on entry — the registry is
 * rebuilt at IME service startup, so the snapshot is stable for the
 * life of the screen. Per-daemon enable / disable writes back to
 * [dev.patrickgold.florisboard.app.AppPrefs.Mcp.disabledDaemonPackages]
 * via the [DisabledDaemonSet] codec. The router consults the same
 * pref before forwarding any `callTool` request so disabled daemons
 * stay bound but receive no traffic.
 *
 * Runtime re-scan + NlpManager smart-compose wire-up ride as
 * L7.6c / L7.7 in subsequent slices.
 */
@Composable
fun McpSettingsScreen() = FlorisScreen {
    title = stringRes(R.string.settings__mcp__title)
    previewFieldVisible = false

    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()

    val activeDaemons = McpDaemonRegistry.active()
    val disabledSerialized by prefs.mcp.disabledDaemonPackages.observeAsState()
    val disabledSet = remember(disabledSerialized) {
        DisabledDaemonSet.parse(disabledSerialized)
    }

    content {
        PreferenceGroup(title = stringRes(R.string.settings__mcp__group_status)) {
            if (activeDaemons.isEmpty()) {
                Preference(
                    icon = Icons.Default.Extension,
                    title = stringRes(R.string.settings__mcp__status_no_daemons),
                    summary = stringRes(R.string.settings__mcp__status_no_daemons_summary),
                )
            } else {
                val activeCount = activeDaemons.keys.count { it.packageName !in disabledSet }
                Preference(
                    icon = Icons.Default.Extension,
                    title = stringRes(R.string.settings__mcp__status_bound_title),
                    summary = stringRes(R.string.settings__mcp__status_bound_summary)
                        .replace("{count}", "$activeCount/${activeDaemons.size}"),
                )
            }
        }

        if (activeDaemons.isNotEmpty()) {
            PreferenceGroup(title = stringRes(R.string.settings__mcp__group_daemons)) {
                for ((_, entry) in activeDaemons) {
                    DaemonRow(
                        entry = entry,
                        isEnabled = entry.key.packageName !in disabledSet,
                        onEnabledChange = { enabled ->
                            scope.launch {
                                val current = prefs.mcp.disabledDaemonPackages.get()
                                val next = if (enabled) {
                                    DisabledDaemonSet.remove(current, entry.key.packageName)
                                } else {
                                    DisabledDaemonSet.add(current, entry.key.packageName)
                                }
                                prefs.mcp.disabledDaemonPackages.set(next)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DaemonRow(
    entry: DaemonEntry,
    isEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    Preference(
        icon = if (isEnabled) Icons.Default.PlayCircleOutline else Icons.Default.Block,
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
        trailing = {
            Switch(checked = isEnabled, onCheckedChange = onEnabledChange)
        },
    )
}
