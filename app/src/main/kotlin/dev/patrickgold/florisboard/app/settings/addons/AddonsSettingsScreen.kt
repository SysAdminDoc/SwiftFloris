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

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.annotations.RoboPreviewInclude
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.preview.SwiftFlorisPreviewFrame
import dev.patrickgold.florisboard.app.settings.about.SigningFingerprint
import android.content.ClipData
import android.content.Context
import android.widget.Toast
import dev.patrickgold.florisboard.ime.addon.AddonEnumerator
import dev.patrickgold.florisboard.ime.addon.AddonManifest
import dev.patrickgold.florisboard.ime.addon.AddonProvenanceReport
import dev.patrickgold.florisboard.ime.addon.AddonRegistry
import dev.patrickgold.florisboard.ime.addon.AddonRegistryStartup
import dev.patrickgold.florisboard.ime.addon.AddonRegistryStore
import dev.patrickgold.florisboard.ime.addon.AddonSigningPinSet
import dev.patrickgold.florisboard.ime.addon.AddonType
import dev.patrickgold.florisboard.ime.addon.DictionaryPackCatalog
import dev.patrickgold.florisboard.ime.addon.DictionaryPackCatalogReader
import dev.patrickgold.florisboard.ime.addon.DictionaryPackDescriptor
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.compose.FlorisEmptyState
import org.florisboard.lib.compose.FlorisErrorCard
import org.florisboard.lib.compose.FlorisProgressCard
import org.florisboard.lib.compose.FlorisSuccessCard
import org.florisboard.lib.compose.FlorisWarningCard
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
    val registryInitialized by AddonRegistryStore.initialized.collectAsState()
    var snapshot by remember { mutableStateOf(AddonRegistryStore.snapshot()) }
    var registryGeneration by remember { mutableStateOf(AddonRegistryStore.generation()) }
    var dictionaryCatalog by remember {
        mutableStateOf(DictionaryPackCatalog(entries = emptyList(), rejected = emptyList()))
    }
    var dictionaryCatalogLoaded by remember { mutableStateOf(false) }
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
        AddonRegistryStore.setActive(result.registry, result.snapshot)
        snapshot = result.snapshot
        activePinnedPackageNames = result.registry.pinnedSigningCertificates().keys
        registryGeneration = AddonRegistryStore.generation()
    }

    fun rescanInstalledAddons(persistedPinsOverride: String? = null) {
        if (scanInProgress) return
        scanInProgress = true
        dictionaryCatalogLoaded = false
        scanError = null
        scope.launch {
            try {
                val persistedPins = persistedPinsOverride ?: prefs.addon.signingCertPins.get()
                if (persistedPinsOverride != null) {
                    prefs.addon.signingCertPins.set(persistedPinsOverride)
                }
                val result = withContext(Dispatchers.Default) {
                    val enumeration = AddonEnumerator(context.applicationContext).scan()
                    AddonRegistryStartup.reconcile(
                        discovered = enumeration.accepted,
                        persistedSigningPinsRaw = persistedPins,
                        trustedRootSigningCertSha256 = trustedRootSigningCertSha256,
                        packageRejections = enumeration.rejected,
                    )
                }
                publishReconcileResult(result)
                if (result.signingPinsChanged) {
                    prefs.addon.signingCertPins.set(result.encodedSigningPins)
                }
            } catch (e: Exception) {
                scanError = e.message ?: e::class.simpleName
                dictionaryCatalogLoaded = true
            } finally {
                scanInProgress = false
            }
        }
    }

    fun resetTrustDecisions() {
        if (scanInProgress) return
        scanInProgress = true
        dictionaryCatalogLoaded = false
        scanError = null
        scope.launch {
            try {
                prefs.addon.signingCertPins.set("")
                AddonRegistryStore.reset()
                snapshot = AddonRegistryStore.snapshot()
                activePinnedPackageNames = emptySet()
                registryGeneration = AddonRegistryStore.generation()
            } catch (e: Exception) {
                scanError = e.message ?: e::class.simpleName
                dictionaryCatalogLoaded = true
            } finally {
                scanInProgress = false
            }
        }
    }

    LaunchedEffect(registryGeneration) {
        dictionaryCatalog = dictionaryCatalogReader.build(
            AddonRegistryStore.active().dictionaryPacks(),
        )
        dictionaryCatalogLoaded = true
    }

    LaunchedEffect(Unit) {
        if (!AddonRegistryStore.initialized.value) {
            rescanInstalledAddons()
        }
    }

    content {
        val addonStatusSummary = stringRes(R.string.settings__addons__status_summary)
            .replace("{accepted}", snapshot.accepted.size.toString())
            .replace("{rejected}", snapshot.rejected.size.toString())
            .replace("{pinned}", pinnedCount.toString())

        val isLoading = scanInProgress || (!registryInitialized && scanError == null) ||
            !dictionaryCatalogLoaded
        if (isLoading) {
            FlorisProgressCard(
                modifier = Modifier.defaultFlorisOutlinedBox(),
                text = stringRes(R.string.settings__addons__rescan_running),
                secondaryText = stringRes(R.string.settings__addons__rescan_running_summary),
            )
        } else {
            PreferenceGroup(title = stringRes(R.string.settings__addons__group_status)) {
                if (scanError != null) {
                    FlorisErrorCard(
                        modifier = Modifier.padding(8.dp),
                        text = stringRes(R.string.settings__addons__rescan_failed),
                        secondaryText = scanError,
                        actionLabel = if (scanInProgress) null else stringRes(R.string.settings__addons__rescan),
                        onClick = if (scanInProgress) null else ({ rescanInstalledAddons() }),
                    )
                } else if (snapshot.rejected.isNotEmpty() || dictionaryCatalog.rejected.isNotEmpty()) {
                    FlorisWarningCard(
                        modifier = Modifier.padding(8.dp),
                        text = stringRes(R.string.settings__addons__status_title),
                        secondaryText = addonStatusSummary,
                        actionLabel = if (scanInProgress) null else stringRes(R.string.settings__addons__rescan),
                        onClick = if (scanInProgress) null else ({ rescanInstalledAddons() }),
                    )
                } else {
                    FlorisSuccessCard(
                        modifier = Modifier.padding(8.dp),
                        text = stringRes(R.string.settings__addons__status_title),
                        secondaryText = addonStatusSummary,
                        actionLabel = if (scanInProgress) null else stringRes(R.string.settings__addons__rescan),
                        onClick = if (scanInProgress) null else ({ rescanInstalledAddons() }),
                    )
                }
                if (pinnedCount > 0) {
                    Preference(
                        icon = Icons.Default.Delete,
                        title = stringRes(R.string.settings__addons__reset_trust),
                        summary = stringRes(R.string.settings__addons__reset_trust_summary),
                        enabledIf = { !scanInProgress },
                        onClick = { pendingPinAction = SigningPinAction.ResetAll },
                    )
                }
            }

            PreferenceGroup(title = stringRes(R.string.settings__addons__group_installed)) {
                if (snapshot.accepted.isEmpty()) {
                    FlorisEmptyState(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        icon = Icons.Default.Extension,
                        title = stringRes(R.string.settings__addons__none_installed),
                        message = stringRes(R.string.settings__addons__none_installed_summary),
                        actionLabel = if (scanInProgress) null else stringRes(R.string.settings__addons__rescan),
                        onAction = if (scanInProgress) null else ({ rescanInstalledAddons() }),
                    )
                } else {
                    for (manifest in snapshot.accepted) {
                        InstalledAddonRow(manifest = manifest)
                    }
                }
            }

            PreferenceGroup(title = stringRes(R.string.settings__addons__group_dictionary_packs)) {
                if (dictionaryCatalog.entries.isEmpty()) {
                    FlorisEmptyState(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        icon = Icons.Default.Extension,
                        title = stringRes(R.string.settings__addons__none_dictionary_packs),
                        message = stringRes(R.string.settings__addons__none_dictionary_packs_summary),
                        actionLabel = if (scanInProgress) null else stringRes(R.string.settings__addons__rescan),
                        onAction = if (scanInProgress) null else ({ rescanInstalledAddons() }),
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
                        val signingCertSha256 = rejected.signingCertSha256
                        if (
                            rejected.reason == AddonRegistry.ReasonExplicitTrustRequired &&
                            signingCertSha256 != null
                        ) {
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
                                        signingCertSha256 = signingCertSha256,
                                    )
                                },
                            )
                        } else if (rejected.packageName in pinnedPackageNames && signingCertSha256 != null) {
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
                                        signingCertSha256 = signingCertSha256,
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
    val context = LocalContext.current
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
        onClick = {
            val report = AddonProvenanceReport.from(manifest)
            val clip = ClipData.newPlainText("SwiftFloris addon provenance", report.toJson())
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(clip)
            Toast.makeText(context, R.string.settings__addons__provenance_copied, Toast.LENGTH_SHORT).show()
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

@RoboPreviewInclude
@Preview(showBackground = true)
@Composable
private fun PreviewInstalledAddonRow() {
    SwiftFlorisPreviewFrame {
        InstalledAddonRow(
            manifest = previewAddonManifest(
                type = AddonType.THEME_PACK,
                displayName = "Midnight Contrast Theme Pack",
                packageName = "io.github.sysadmindoc.swiftfloris.addons.theme.midnight",
            ),
        )
    }
}

@RoboPreviewInclude
@Preview(showBackground = true)
@Composable
private fun PreviewDictionaryPackRow() {
    val manifest = previewAddonManifest(
        type = AddonType.DICTIONARY_PACK,
        displayName = "Esperanto Sample Dictionary",
        packageName = "io.github.sysadmindoc.swiftfloris.addons.dictionary.eo",
    )
    val descriptor = DictionaryPackDescriptor(
        schema = DictionaryPackDescriptor.SUPPORTED_SCHEMA,
        language = "eo",
        displayName = "Esperanto Sample Dictionary",
        wordCount = 12500,
        fldicAssetPath = "ime/dict/eo.fldic",
        zipfAssetPath = "freq/eo.tsv",
        source = "Sample fixture corpus",
        license = "CC0-1.0",
    )
    SwiftFlorisPreviewFrame {
        DictionaryPackRow(
            entry = DictionaryPackCatalog.Entry(
                manifest = manifest,
                descriptor = descriptor,
                provenanceReport = AddonProvenanceReport.fromDictionaryPack(manifest, descriptor),
            ),
        )
    }
}

private fun previewAddonManifest(
    type: AddonType,
    displayName: String,
    packageName: String,
): AddonManifest = AddonManifest(
    packageName = packageName,
    type = type,
    version = 3L,
    displayName = displayName,
    descriptorResourceId = 1,
    licenseSpdxId = "Apache-2.0",
    signingCertSha256 = PREVIEW_SIGNING_CERT_SHA256,
    bundleSizeBytes = 1_048_576L,
)

private const val PREVIEW_SIGNING_CERT_SHA256 =
    "AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99"

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
