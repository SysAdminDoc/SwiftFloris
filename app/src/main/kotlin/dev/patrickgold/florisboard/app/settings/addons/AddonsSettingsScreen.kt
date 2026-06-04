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

package dev.patrickgold.florisboard.app.settings.addons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.settings.about.SigningFingerprint
import dev.patrickgold.florisboard.ime.addon.AddonEnumerator
import dev.patrickgold.florisboard.ime.addon.AddonManifest
import dev.patrickgold.florisboard.ime.addon.AddonRegistry
import dev.patrickgold.florisboard.ime.addon.AddonRegistryStartup
import dev.patrickgold.florisboard.ime.addon.AddonRegistryStore
import dev.patrickgold.florisboard.ime.addon.AddonSigningPinSet
import dev.patrickgold.florisboard.ime.addon.DictionaryPackCatalog
import dev.patrickgold.florisboard.ime.addon.DictionaryPackCatalogReader
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.compose.FlorisProgressCard
import org.florisboard.lib.compose.defaultFlorisOutlinedBox
import org.florisboard.lib.compose.stringRes
import java.text.NumberFormat
import java.util.Locale

/**
 * ROADMAP §7 Next-10.3d — read-only Settings surface for installed addon APKs.
 *
 * This screen deliberately reuses [AddonRegistryStartup] for manual rescans so
 * Settings and IME startup share the exact same signing-pin and package-hijack
 * rules.
 */
@Composable
fun AddonsSettingsScreen() = FlorisScreen {
    title = stringRes(R.string.settings__addons__title)
    previewFieldVisible = false
    iconSpaceReserved = true

    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dictionaryCatalogReader = remember(context) {
        DictionaryPackCatalogReader(context.applicationContext)
    }
    val trustedRootSigningCertSha256 = remember(context) {
        SigningFingerprint.sha256(context.applicationContext)
    }
    val persistedPinsRaw by prefs.addon.signingCertPins.collectAsState()
    var snapshot by remember { mutableStateOf(AddonRegistryStore.active().lastRefresh()) }
    var registryGeneration by remember { mutableStateOf(AddonRegistryStore.generation()) }
    var dictionaryCatalog by remember {
        mutableStateOf(DictionaryPackCatalog(entries = emptyList(), rejected = emptyList()))
    }
    var activePinnedPackageNames by remember {
        mutableStateOf(AddonRegistryStore.active().pinnedSigningCertificates().keys)
    }
    var scanInProgress by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var pendingPinAction by remember { mutableStateOf<SigningPinAction?>(null) }
    val persistedPinSet = remember(persistedPinsRaw) {
        AddonSigningPinSet.parse(persistedPinsRaw)
    }
    val pinnedPackageNames = remember(persistedPinSet, activePinnedPackageNames) {
        persistedPinSet.asMap().keys + activePinnedPackageNames
    }
    val pinnedCount = pinnedPackageNames.size

    fun publishReconcileResult(result: AddonRegistryStartup.Result) {
        AddonRegistryStore.setActive(result.registry)
        snapshot = result.snapshot
        activePinnedPackageNames = result.registry.pinnedSigningCertificates().keys
        registryGeneration = AddonRegistryStore.generation()
    }

    fun rescanInstalledAddons(persistedPinsOverride: String? = null) {
        if (scanInProgress) return
        scanInProgress = true
        scanError = null
        scope.launch {
            try {
                val persistedPins = persistedPinsOverride ?: prefs.addon.signingCertPins.get()
                if (persistedPinsOverride != null) {
                    prefs.addon.signingCertPins.set(persistedPinsOverride)
                }
                val result = withContext(Dispatchers.Default) {
                    val discovered = AddonEnumerator(context.applicationContext).snapshot()
                    AddonRegistryStartup.reconcile(
                        discovered = discovered,
                        persistedSigningPinsRaw = persistedPins,
                        trustedRootSigningCertSha256 = trustedRootSigningCertSha256,
                    )
                }
                publishReconcileResult(result)
                if (result.signingPinsChanged) {
                    prefs.addon.signingCertPins.set(result.encodedSigningPins)
                }
            } catch (e: Exception) {
                scanError = e.message ?: e::class.simpleName
            } finally {
                scanInProgress = false
            }
        }
    }

    fun resetTrustDecisions() {
        if (scanInProgress) return
        scanInProgress = true
        scanError = null
        scope.launch {
            try {
                prefs.addon.signingCertPins.set("")
                AddonRegistryStore.reset()
                snapshot = AddonRegistryStore.active().lastRefresh()
                activePinnedPackageNames = emptySet()
                registryGeneration = AddonRegistryStore.generation()
            } catch (e: Exception) {
                scanError = e.message ?: e::class.simpleName
            } finally {
                scanInProgress = false
            }
        }
    }

    LaunchedEffect(registryGeneration) {
        dictionaryCatalog = dictionaryCatalogReader.build(
            AddonRegistryStore.active().dictionaryPacks(),
        )
    }

    content {
        if (scanInProgress) {
            FlorisProgressCard(
                modifier = Modifier.defaultFlorisOutlinedBox(),
                text = stringRes(R.string.settings__addons__rescan_running),
                secondaryText = stringRes(R.string.settings__addons__rescan_running_summary),
            )
        }

        PreferenceGroup(title = stringRes(R.string.settings__addons__group_status)) {
            Preference(
                icon = Icons.Default.Extension,
                title = stringRes(R.string.settings__addons__status_title),
                summary = stringRes(R.string.settings__addons__status_summary)
                    .replace("{accepted}", snapshot.accepted.size.toString())
                    .replace("{rejected}", snapshot.rejected.size.toString())
                    .replace("{pinned}", pinnedCount.toString()),
            )
            Preference(
                icon = Icons.Default.Extension,
                title = if (scanInProgress) {
                    stringRes(R.string.settings__addons__rescan_running)
                } else {
                    stringRes(R.string.settings__addons__rescan)
                },
                summary = stringRes(R.string.settings__addons__rescan_summary),
                enabledIf = { !scanInProgress },
                onClick = { rescanInstalledAddons() },
            )
            if (pinnedCount > 0) {
                Preference(
                    icon = Icons.Default.Delete,
                    title = stringRes(R.string.settings__addons__reset_trust),
                    summary = stringRes(R.string.settings__addons__reset_trust_summary),
                    enabledIf = { !scanInProgress },
                    onClick = { pendingPinAction = SigningPinAction.ResetAll },
                )
            }
            scanError?.let { error ->
                Preference(
                    icon = Icons.Default.Block,
                    title = stringRes(R.string.settings__addons__rescan_failed),
                    summary = error,
                )
            }
        }

        PreferenceGroup(title = stringRes(R.string.settings__addons__group_installed)) {
            if (snapshot.accepted.isEmpty()) {
                Preference(
                    icon = Icons.Default.Extension,
                    title = stringRes(R.string.settings__addons__none_installed),
                    summary = stringRes(R.string.settings__addons__none_installed_summary),
                )
            } else {
                for (manifest in snapshot.accepted) {
                    InstalledAddonRow(manifest = manifest)
                }
            }
        }

        PreferenceGroup(title = stringRes(R.string.settings__addons__group_dictionary_packs)) {
            if (dictionaryCatalog.entries.isEmpty()) {
                Preference(
                    icon = Icons.Default.Extension,
                    title = stringRes(R.string.settings__addons__none_dictionary_packs),
                    summary = stringRes(R.string.settings__addons__none_dictionary_packs_summary),
                )
            } else {
                for (entry in dictionaryCatalog.entries) {
                    DictionaryPackRow(entry = entry)
                }
            }
            for (rejectedDescriptor in dictionaryCatalog.rejected) {
                Preference(
                    icon = Icons.Default.Block,
                    title = stringRes(R.string.settings__addons__dictionary_pack_rejected),
                    summary = stringRes(R.string.settings__addons__dictionary_pack_rejected_summary)
                        .replace("{package}", rejectedDescriptor.packageName)
                        .replace("{reason}", rejectedDescriptor.reason),
                )
            }
        }

        if (snapshot.rejected.isNotEmpty()) {
            PreferenceGroup(title = stringRes(R.string.settings__addons__group_rejected)) {
                for (rejected in snapshot.rejected) {
                    Preference(
                        icon = Icons.Default.Block,
                        title = rejected.displayName ?: rejected.packageName,
                        summary = stringRes(R.string.settings__addons__rejected_summary)
                            .replace("{package}", rejected.packageName)
                            .replace("{reason}", rejected.reason),
                    )
                    if (rejected.reason == AddonRegistry.ReasonExplicitTrustRequired) {
                        Preference(
                            icon = Icons.Default.Refresh,
                            title = stringRes(R.string.settings__addons__trust_new_certificate),
                            summary = stringRes(R.string.settings__addons__trust_new_certificate_summary)
                                .replace("{package}", rejected.packageName),
                            enabledIf = { !scanInProgress },
                            onClick = {
                                pendingPinAction = SigningPinAction.TrustNewCertificate(
                                    packageName = rejected.packageName,
                                    displayName = rejected.displayName,
                                    signingCertSha256 = rejected.signingCertSha256,
                                )
                            },
                        )
                    } else if (rejected.packageName in pinnedPackageNames) {
                        Preference(
                            icon = Icons.Default.Refresh,
                            title = stringRes(R.string.settings__addons__trust_changed_certificate),
                            summary = stringRes(R.string.settings__addons__trust_changed_certificate_summary)
                                .replace("{package}", rejected.packageName),
                            enabledIf = { !scanInProgress },
                            onClick = {
                                pendingPinAction = SigningPinAction.TrustChangedCertificate(
                                    packageName = rejected.packageName,
                                    displayName = rejected.displayName,
                                    signingCertSha256 = rejected.signingCertSha256,
                                )
                            },
                        )
                    }
                }
            }
        }

        PreferenceGroup(title = stringRes(R.string.settings__addons__group_install)) {
            Preference(
                icon = Icons.Default.Extension,
                title = stringRes(R.string.settings__addons__install_title),
                summary = stringRes(R.string.settings__addons__install_summary),
            )
        }

        pendingPinAction?.let { action ->
            SigningPinActionDialog(
                action = action,
                onConfirm = {
                    pendingPinAction = null
                    when (action) {
                        SigningPinAction.ResetAll -> resetTrustDecisions()
                        is SigningPinAction.TrustNewCertificate -> {
                            val nextPins = AddonSigningPinSet.parse(prefs.addon.signingCertPins.get())
                                .withPinnedCertificate(action.packageName, action.signingCertSha256)
                                .encode()
                            rescanInstalledAddons(persistedPinsOverride = nextPins)
                        }
                        is SigningPinAction.TrustChangedCertificate -> {
                            val nextPins = AddonSigningPinSet.parse(prefs.addon.signingCertPins.get())
                                .withPinnedCertificate(action.packageName, action.signingCertSha256)
                                .encode()
                            rescanInstalledAddons(persistedPinsOverride = nextPins)
                        }
                    }
                },
                onDismiss = { pendingPinAction = null },
            )
        }
    }
}

@Composable
private fun DictionaryPackRow(entry: DictionaryPackCatalog.Entry) {
    Preference(
        icon = Icons.Default.CheckCircle,
        title = entry.displayName,
        summary = stringRes(R.string.settings__addons__dictionary_pack_summary)
            .replace("{language}", entry.language)
            .replace("{words}", formatWordCount(entry.descriptor.wordCount))
            .replace("{license}", entry.descriptor.license)
            .replace("{source}", entry.descriptor.source),
    )
}

@Composable
private fun InstalledAddonRow(manifest: AddonManifest) {
    Preference(
        icon = Icons.Default.CheckCircle,
        title = manifest.displayName,
        summary = buildString {
            append(manifest.packageName)
            append("\n")
            append(
                stringRes(R.string.settings__addons__installed_summary)
                    .replace("{type}", manifest.type.metadataValue)
                    .replace("{version}", manifest.version.toString())
                    .replace("{license}", manifest.licenseSpdxId)
                    .replace("{size}", formatBundleSize(manifest.bundleSizeBytes)),
            )
            append("\n")
            append(
                stringRes(R.string.settings__addons__fingerprint_summary)
                    .replace("{fingerprint}", manifest.signingCertSha256),
            )
        },
    )
}

private fun formatWordCount(words: Long): String =
    NumberFormat.getIntegerInstance(Locale.getDefault()).format(words)

private fun formatBundleSize(bytes: Long): String {
    val kib = 1024L
    val mib = kib * 1024L
    return when {
        bytes >= mib -> "%.2f MiB".format(bytes.toDouble() / mib)
        bytes >= kib -> "%.2f KiB".format(bytes.toDouble() / kib)
        else -> "$bytes B"
    }
}

private sealed interface SigningPinAction {
    data object ResetAll : SigningPinAction

    data class TrustNewCertificate(
        val packageName: String,
        val displayName: String?,
        val signingCertSha256: String,
    ) : SigningPinAction

    data class TrustChangedCertificate(
        val packageName: String,
        val displayName: String?,
        val signingCertSha256: String,
    ) : SigningPinAction
}

@Composable
private fun SigningPinActionDialog(
    action: SigningPinAction,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (action) {
        SigningPinAction.ResetAll -> {
            JetPrefAlertDialog(
                title = stringRes(R.string.settings__addons__reset_trust_confirm_title),
                confirmLabel = stringRes(R.string.action__reset),
                onConfirm = onConfirm,
                dismissLabel = stringRes(R.string.action__cancel),
                onDismiss = onDismiss,
            ) {
                Text(text = stringRes(R.string.settings__addons__reset_trust_confirm_message))
            }
        }
        is SigningPinAction.TrustNewCertificate -> {
            JetPrefAlertDialog(
                title = stringRes(R.string.settings__addons__trust_new_certificate_confirm_title),
                confirmLabel = stringRes(R.string.settings__addons__trust_new_certificate_confirm),
                onConfirm = onConfirm,
                dismissLabel = stringRes(R.string.action__cancel),
                onDismiss = onDismiss,
            ) {
                Text(
                    text = stringRes(
                        R.string.settings__addons__trust_new_certificate_confirm_message,
                        "package" to action.packageName,
                        "name" to (action.displayName ?: action.packageName),
                        "fingerprint" to action.signingCertSha256,
                    ),
                )
            }
        }
        is SigningPinAction.TrustChangedCertificate -> {
            JetPrefAlertDialog(
                title = stringRes(R.string.settings__addons__trust_changed_certificate_confirm_title),
                confirmLabel = stringRes(R.string.settings__addons__trust_changed_certificate_confirm),
                onConfirm = onConfirm,
                dismissLabel = stringRes(R.string.action__cancel),
                onDismiss = onDismiss,
            ) {
                Text(
                    text = stringRes(
                        R.string.settings__addons__trust_changed_certificate_confirm_message,
                        "package" to action.packageName,
                        "name" to (action.displayName ?: action.packageName),
                        "fingerprint" to action.signingCertSha256,
                    ),
                )
            }
        }
    }
}
