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

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.settings.about.SigningFingerprint
import dev.patrickgold.florisboard.ime.mcp.DaemonEntry
import dev.patrickgold.florisboard.ime.mcp.DaemonKey
import dev.patrickgold.florisboard.ime.mcp.DisabledDaemonSet
import dev.patrickgold.florisboard.ime.mcp.DisabledToolSet
import dev.patrickgold.florisboard.ime.mcp.McpAndroidDiscoverer
import dev.patrickgold.florisboard.ime.mcp.McpBindingPolicy
import dev.patrickgold.florisboard.ime.mcp.McpConnectionStateStore
import dev.patrickgold.florisboard.ime.mcp.McpDaemonConnectionState
import dev.patrickgold.florisboard.ime.mcp.McpDaemonDiscoveryStore
import dev.patrickgold.florisboard.ime.mcp.McpDaemonRegistry
import dev.patrickgold.florisboard.ime.mcp.McpDaemonStatePolicy
import dev.patrickgold.florisboard.ime.mcp.McpDaemonTrustPolicy
import dev.patrickgold.florisboard.ime.mcp.McpServiceLifecycle
import dev.patrickgold.florisboard.ime.mcp.McpSigningPinPersistencePolicy
import dev.patrickgold.florisboard.ime.mcp.McpSigningPinSet
import dev.patrickgold.florisboard.ime.mcp.RejectedMcpDaemon
import dev.patrickgold.florisboard.ime.smartcompose.AddonConsentState
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.compose.FlorisInfoCard
import org.florisboard.lib.compose.FlorisSuccessCard
import org.florisboard.lib.compose.FlorisWarningCard
import org.florisboard.lib.compose.pluralsRes
import org.florisboard.lib.compose.stringRes

/**
 * ROADMAP §10.5 L7.6 + L7.6b — Settings → MCP daemon bridge.
 *
 * Seeds its daemon view from `McpDaemonRegistry.active()` and replaces
 * it after each explicit discovery rescan so revoked packages disappear
 * from both dispatch and Settings immediately. Per-daemon enable / disable writes back to
 * [dev.patrickgold.florisboard.app.AppPrefs.Mcp.disabledDaemonPackages]
 * via the [DisabledDaemonSet] codec. The router consults the same
 * pref before forwarding any `callTool` request so disabled daemons
 * stay bound but receive no traffic.
 *
 * Runtime discovery remains available for trust review, but production daemon
 * binding is paused until an audited keyboard dispatch action exists.
 */
@Composable
fun McpSettingsScreen() = FlorisScreen {
    title = stringRes(R.string.settings__mcp__title)
    previewFieldVisible = false

    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val trustedRootSigningCertSha256 = remember(context) {
        SigningFingerprint.sha256(context.applicationContext)
    }

    var activeDaemons by remember {
        mutableStateOf(McpDaemonRegistry.active())
    }
    val disabledSerialized by prefs.mcp.disabledDaemonPackages.collectAsState()
    val disabledSet = remember(disabledSerialized) {
        DisabledDaemonSet.parse(disabledSerialized)
    }
    val disabledToolsSerialized by prefs.mcp.disabledTools.collectAsState()
    val signingPinsRaw by prefs.mcp.signingCertPins.collectAsState()
    val mcpConsent by prefs.privacy.mcpConsent.collectAsState()
    val bridgeEnabled = mcpConsent.allowsInvocation()
    val signingPinSet = remember(signingPinsRaw) {
        McpSigningPinSet.parse(signingPinsRaw)
    }
    var discoverySnapshot by remember {
        mutableStateOf(McpDaemonDiscoveryStore.active())
    }
    val connectionStates by McpConnectionStateStore.states.collectAsState()
    var retryUnavailable by remember { mutableStateOf(false) }
    var scanInProgress by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var pendingTrustAction by remember { mutableStateOf<McpTrustAction?>(null) }

    fun rescanDaemonTrust(
        persistedPinsOverride: String? = null,
        trustTarget: DaemonKey? = null,
    ) {
        if (scanInProgress) return
        scanInProgress = true
        scanError = null
        scope.launch {
            try {
                val currentPins = prefs.mcp.signingCertPins.get()
                val proposedPins = persistedPinsOverride ?: currentPins
                var snapshot = withContext(Dispatchers.Default) {
                    McpAndroidDiscoverer.runDiscoverySnapshot(
                        context = context.applicationContext,
                        persistedSigningPinsRaw = proposedPins,
                        trustedRootSigningCertSha256 = trustedRootSigningCertSha256,
                    )
                }
                if (persistedPinsOverride != null) {
                    val mayPersist = trustTarget == null ||
                        McpSigningPinPersistencePolicy.shouldPersistProposedPin(
                            snapshot = snapshot,
                            daemonKey = trustTarget,
                        )
                    if (mayPersist) {
                        prefs.mcp.signingCertPins.set(persistedPinsOverride)
                    } else {
                        // The package changed after confirmation. Re-run under
                        // the still-durable pins so Settings never presents a
                        // snapshot based on trust that was correctly refused.
                        snapshot = withContext(Dispatchers.Default) {
                            McpAndroidDiscoverer.runDiscoverySnapshot(
                                context = context.applicationContext,
                                persistedSigningPinsRaw = currentPins,
                                trustedRootSigningCertSha256 = trustedRootSigningCertSha256,
                            )
                        }
                    }
                }
                McpDaemonDiscoveryStore.setActive(snapshot)
                McpServiceLifecycle.reconcileActiveDaemons(snapshot.accepted)
                activeDaemons = snapshot.accepted
                discoverySnapshot = snapshot
            } catch (e: Exception) {
                scanError = e.message ?: e::class.simpleName
            } finally {
                scanInProgress = false
            }
        }
    }

    content {
        // Acceptance by the trust policy is not a connection: only daemons the binder actually
        // handed back count as bound.
        val connectedCount = McpDaemonStatePolicy.connectedCount(
            daemonKeys = activeDaemons.keys,
            bridgeEnabled = bridgeEnabled,
            disabledPackages = disabledSet,
            connectionStates = connectionStates,
        )
        // Sits above every other status card and does not depend on the bridge toggle: while
        // binding is parked, the toggle and the per-daemon switches below govern a no-op, and
        // saying so is the only thing that makes the rest of the screen honest.
        if (McpBindingPolicy.showsParkedNotice()) {
            FlorisWarningCard(
                modifier = Modifier.padding(8.dp),
                text = stringRes(R.string.settings__mcp__status_parked),
                secondaryText = stringRes(R.string.settings__mcp__status_parked_summary),
            )
        }
        if (!bridgeEnabled) {
            FlorisInfoCard(
                modifier = Modifier.padding(8.dp),
                text = stringRes(R.string.settings__mcp__status_disabled),
                secondaryText = stringRes(R.string.settings__mcp__status_disabled_summary),
            )
        } else if (activeDaemons.isEmpty()) {
            FlorisInfoCard(
                modifier = Modifier.padding(8.dp),
                text = stringRes(R.string.settings__mcp__status_no_daemons),
                secondaryText = stringRes(R.string.settings__mcp__status_no_daemons_summary),
            )
        } else if (connectedCount == 0) {
            FlorisWarningCard(
                modifier = Modifier.padding(8.dp),
                text = stringRes(R.string.settings__mcp__status_not_connected_title),
                secondaryText = pluralsRes(
                    R.plurals.settings__mcp__status_bound_summary,
                    activeDaemons.size,
                    "count" to "$connectedCount/${activeDaemons.size}",
                ),
            )
        } else {
            FlorisSuccessCard(
                modifier = Modifier.padding(8.dp),
                text = stringRes(R.string.settings__mcp__status_bound_title),
                secondaryText = pluralsRes(
                    R.plurals.settings__mcp__status_bound_summary,
                    activeDaemons.size,
                    "count" to "$connectedCount/${activeDaemons.size}",
                ),
            )
        }

        if (retryUnavailable) {
            FlorisWarningCard(
                modifier = Modifier.padding(8.dp),
                text = stringRes(R.string.settings__mcp__retry_unavailable_title),
                secondaryText = stringRes(R.string.settings__mcp__retry_unavailable_summary),
                actionLabel = stringRes(R.string.action__ok),
                onClick = { retryUnavailable = false },
            )
        }

        PreferenceGroup(title = stringRes(R.string.settings__mcp__group_status)) {
            Preference(
                icon = if (bridgeEnabled) Icons.Default.PlayCircleOutline else Icons.Default.Block,
                title = stringRes(R.string.settings__mcp__bridge_enabled),
                summary = if (bridgeEnabled) {
                    stringRes(R.string.settings__mcp__bridge_enabled_summary)
                } else {
                    stringRes(R.string.settings__mcp__bridge_disabled_summary)
                },
                trailing = {
                    Switch(
                        checked = bridgeEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                prefs.privacy.mcpConsent.set(
                                    if (enabled) AddonConsentState.GRANTED else AddonConsentState.DENIED,
                                )
                            }
                        },
                    )
                },
            )
            Preference(
                icon = Icons.Default.Refresh,
                title = if (scanInProgress) {
                    stringRes(R.string.settings__mcp__rescan_running)
                } else {
                    stringRes(R.string.settings__mcp__rescan)
                },
                summary = stringRes(R.string.settings__mcp__rescan_summary),
                enabledIf = { !scanInProgress },
                onClick = { rescanDaemonTrust() },
            )
            if (signingPinSet.asMap().isNotEmpty()) {
                Preference(
                    icon = Icons.Default.Delete,
                    title = stringRes(R.string.settings__mcp__reset_trust),
                    summary = stringRes(R.string.settings__mcp__reset_trust_summary),
                    enabledIf = { !scanInProgress },
                    onClick = { pendingTrustAction = McpTrustAction.ResetAll },
                )
            }
            scanError?.let { error ->
                Preference(
                    icon = Icons.Default.Block,
                    title = stringRes(R.string.settings__mcp__rescan_failed),
                    summary = error,
                )
            }
        }

        if (bridgeEnabled && activeDaemons.isNotEmpty()) {
            PreferenceGroup(title = stringRes(R.string.settings__mcp__group_daemons)) {
                for ((_, entry) in activeDaemons) {
                    val daemonEnabled = entry.key.packageName !in disabledSet
                    val daemonState = McpDaemonStatePolicy.resolve(
                        daemonKey = entry.key,
                        bridgeEnabled = bridgeEnabled,
                        disabledPackages = disabledSet,
                        connectionStates = connectionStates,
                    )
                    DaemonRow(
                        entry = entry,
                        isEnabled = daemonEnabled,
                        connectionState = daemonState,
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
                    if (daemonState.isRetryable) {
                        // Rebinding is bounded and never automatic past the budget, so recovery is
                        // an explicit action rather than a background loop.
                        Preference(
                            icon = Icons.Default.Refresh,
                            title = stringRes(R.string.settings__mcp__daemon_retry),
                            summary = stringRes(R.string.settings__mcp__daemon_retry_summary),
                            onClick = {
                                retryUnavailable = !McpServiceLifecycle.retryActiveDaemon(entry.key)
                            },
                        )
                    }
                    // Matrix #38 follow-up — per-tool toggle row under each daemon. Per-tool switches are
                    // greyed out while the parent daemon is disabled (the daemon switch wins), but the
                    // per-tool persisted state is preserved across re-enables so users do not lose their
                    // per-tool decisions when they momentarily mute a daemon.
                    for (tool in entry.tools) {
                        val toolEnabled = !DisabledToolSet.contains(
                            disabledToolsSerialized,
                            entry.key.packageName,
                            tool.name,
                        )
                        ToolRow(
                            daemonPackage = entry.key.packageName,
                            toolName = tool.name,
                            toolDescription = tool.description,
                            // Show the tool's TRUE persisted state; interactivity (not the
                            // checked state) is gated on the parent daemon. Otherwise a
                            // muted daemon renders every enabled tool as OFF, contradicting
                            // the preserved per-tool state and misleading TalkBack.
                            isEnabled = toolEnabled,
                            isInteractive = daemonEnabled,
                            onEnabledChange = { enabled ->
                                scope.launch {
                                    val current = prefs.mcp.disabledTools.get()
                                    val next = if (enabled) {
                                        DisabledToolSet.remove(current, entry.key.packageName, tool.name)
                                    } else {
                                        DisabledToolSet.add(current, entry.key.packageName, tool.name)
                                    }
                                    prefs.mcp.disabledTools.set(next)
                                }
                            },
                        )
                    }
                }
            }
        }

        if (discoverySnapshot.rejected.isNotEmpty()) {
            PreferenceGroup(title = stringRes(R.string.settings__mcp__group_trust)) {
                for (rejected in discoverySnapshot.rejected) {
                    RejectedDaemonRow(rejected = rejected)
                    if (rejected.signingCertSha256 != null &&
                        (rejected.reason == McpDaemonTrustPolicy.ReasonExplicitTrustRequired ||
                            rejected.reason == McpDaemonTrustPolicy.ReasonSigningCertificateChanged)
                    ) {
                        Preference(
                            icon = Icons.Default.Refresh,
                            title = if (rejected.reason == McpDaemonTrustPolicy.ReasonSigningCertificateChanged) {
                                stringRes(R.string.settings__mcp__trust_changed_certificate)
                            } else {
                                stringRes(R.string.settings__mcp__trust_new_certificate)
                            },
                            summary = stringRes(R.string.settings__mcp__trust_certificate_summary)
                                .replace("{package}", rejected.packageName),
                            enabledIf = { !scanInProgress },
                            onClick = {
                                pendingTrustAction = McpTrustAction.TrustCertificate(
                                    packageName = rejected.packageName,
                                    daemonClassName = rejected.daemonClassName,
                                    signingCertSha256 = rejected.signingCertSha256,
                                    changedCertificate = rejected.reason ==
                                        McpDaemonTrustPolicy.ReasonSigningCertificateChanged,
                                )
                            },
                        )
                    }
                }
            }
        }

        pendingTrustAction?.let { action ->
            McpTrustActionDialog(
                action = action,
                onConfirm = {
                    pendingTrustAction = null
                    when (action) {
                        McpTrustAction.ResetAll -> {
                            scope.launch {
                                prefs.mcp.signingCertPins.set("")
                                rescanDaemonTrust(persistedPinsOverride = "")
                            }
                        }
                        is McpTrustAction.TrustCertificate -> {
                            val nextPins = McpSigningPinSet.parse(prefs.mcp.signingCertPins.get())
                                .withPinnedCertificate(action.packageName, action.signingCertSha256)
                                .encode()
                            rescanDaemonTrust(
                                persistedPinsOverride = nextPins,
                                trustTarget = DaemonKey(
                                    packageName = action.packageName,
                                    daemonClassName = action.daemonClassName,
                                ),
                            )
                        }
                    }
                },
                onDismiss = { pendingTrustAction = null },
            )
        }
    }
}

@Composable
private fun RejectedDaemonRow(rejected: RejectedMcpDaemon) {
    Preference(
        icon = Icons.Default.Block,
        title = rejected.packageName,
        summary = stringRes(R.string.settings__mcp__daemon_rejected_summary)
            .replace("{class}", rejected.daemonClassName)
            .replace("{reason}", rejected.reason)
            .replace("{fingerprint}", rejected.signingCertSha256 ?: "unreadable"),
    )
}

@Composable
private fun ToolRow(
    daemonPackage: String,
    toolName: String,
    toolDescription: String,
    isEnabled: Boolean,
    isInteractive: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    Preference(
        title = toolName,
        summary = if (toolDescription.isBlank()) {
            daemonPackage
        } else {
            "$toolDescription\n$daemonPackage"
        },
        trailing = {
            Switch(
                checked = isEnabled,
                onCheckedChange = onEnabledChange,
                enabled = isInteractive,
            )
        },
    )
}

@Composable
private fun DaemonRow(
    entry: DaemonEntry,
    isEnabled: Boolean,
    connectionState: McpDaemonConnectionState,
    onEnabledChange: (Boolean) -> Unit,
) {
    Preference(
        icon = if (isEnabled && connectionState.acceptsCalls) {
            Icons.Default.PlayCircleOutline
        } else {
            Icons.Default.Block
        },
        title = entry.key.packageName,
        summary = buildString {
            append(stringRes(connectionState.labelRes))
            append(" · ")
            append(stringRes(
                R.string.settings__mcp__daemon_protocol,
                "version" to entry.protocolVersion,
            ))
            append(" · ")
            append(pluralsRes(
                R.plurals.settings__mcp__daemon_tools_count,
                entry.tools.size,
                "count" to entry.tools.size,
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

/** Localized label for each observable binding state. */
private val McpDaemonConnectionState.labelRes: Int
    get() = when (this) {
        McpDaemonConnectionState.Pending -> R.string.settings__mcp__daemon_state_pending
        McpDaemonConnectionState.Connected -> R.string.settings__mcp__daemon_state_connected
        McpDaemonConnectionState.Failed -> R.string.settings__mcp__daemon_state_failed
        McpDaemonConnectionState.Dead -> R.string.settings__mcp__daemon_state_dead
        McpDaemonConnectionState.Disabled -> R.string.settings__mcp__daemon_state_disabled
    }

private sealed interface McpTrustAction {
    data object ResetAll : McpTrustAction

    data class TrustCertificate(
        val packageName: String,
        val daemonClassName: String,
        val signingCertSha256: String,
        val changedCertificate: Boolean,
    ) : McpTrustAction
}

@Composable
private fun McpTrustActionDialog(
    action: McpTrustAction,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (action) {
        McpTrustAction.ResetAll -> {
            JetPrefAlertDialog(
                title = stringRes(R.string.settings__mcp__reset_trust_confirm_title),
                confirmLabel = stringRes(R.string.action__reset),
                onConfirm = onConfirm,
                dismissLabel = stringRes(R.string.action__cancel),
                onDismiss = onDismiss,
            ) {
                Text(text = stringRes(R.string.settings__mcp__reset_trust_confirm_message))
            }
        }
        is McpTrustAction.TrustCertificate -> {
            JetPrefAlertDialog(
                title = if (action.changedCertificate) {
                    stringRes(R.string.settings__mcp__trust_changed_certificate_confirm_title)
                } else {
                    stringRes(R.string.settings__mcp__trust_new_certificate_confirm_title)
                },
                confirmLabel = stringRes(R.string.settings__mcp__trust_certificate_confirm),
                onConfirm = onConfirm,
                dismissLabel = stringRes(R.string.action__cancel),
                onDismiss = onDismiss,
            ) {
                Text(
                    text = stringRes(
                        R.string.settings__mcp__trust_certificate_confirm_message,
                        "package" to action.packageName,
                        "class" to action.daemonClassName,
                        "fingerprint" to action.signingCertSha256,
                    ),
                )
            }
        }
    }
}
