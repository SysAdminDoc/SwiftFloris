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

package dev.patrickgold.florisboard.ime.addon

import java.util.Locale

/**
 * ROADMAP §7 Next-10.3a — process-local live state for addon enrolment.
 *
 * [AddonEnumerator] performs the Android PackageManager scan. This registry
 * owns the next step: reconcile that snapshot with the signing-certificate
 * pins captured after explicit user trust, expose deterministic lookup lists
 * for UI and runtime consumers, and keep stale pins even when an addon is
 * temporarily uninstalled. Startup persistence is handled by
 * [AddonRegistryStartup]; the reconciliation rules stay pure and unit-testable
 * here.
 */
class AddonRegistry(
    initialPinnedSigningCertificates: Map<String, String> = emptyMap(),
    private val trustedRootSigningCertSha256: String? = null,
) {
    private val pinnedSigningCertificates = initialPinnedSigningCertificates.toMutableMap()
    private val liveManifests = linkedMapOf<String, AddonManifest>()
    private var lastSnapshot = Snapshot(emptyList(), emptyList())

    /**
     * Reconcile a fresh PackageManager scan into live addon state.
     *
     * A package whose signing certificate changes after explicit trust is
     * rejected even if the new APK otherwise satisfies the addon manifest
     * contract. The old pin stays in place so uninstall/reinstall hijacks do
     * not clear trust silently. First-seen packages are accepted only when
     * co-signed with the base IME; every other package remains rejected until
     * Settings writes an explicit pin.
     */
    @Synchronized
    fun refresh(discovered: List<AddonManifest>): Snapshot {
        val accepted = mutableListOf<AddonManifest>()
        val rejected = mutableListOf<RejectedAddon>()
        val newestByPackage = discovered
            .groupBy { it.packageName }
            .mapValues { (_, manifests) -> manifests.maxWith(DisplayOrderWithNewestVersion) }

        for ((packageName, manifest) in newestByPackage.toSortedMap()) {
            val pinnedFingerprint = pinnedSigningCertificates[packageName]
            when {
                pinnedFingerprint == null &&
                    manifest.signingCertSha256 == trustedRootSigningCertSha256 -> {
                    accepted += manifest
                }
                pinnedFingerprint == manifest.signingCertSha256 -> {
                    accepted += manifest
                }
                pinnedFingerprint == null -> {
                    rejected += RejectedAddon(
                        packageName = packageName,
                        displayName = manifest.displayName,
                        signingCertSha256 = manifest.signingCertSha256,
                        reason = ReasonExplicitTrustRequired,
                    )
                }
                else -> {
                    rejected += RejectedAddon(
                        packageName = packageName,
                        displayName = manifest.displayName,
                        signingCertSha256 = manifest.signingCertSha256,
                        reason = ReasonSigningCertificateChanged,
                    )
                }
            }
        }

        val sortedAccepted = accepted.sortedWith(DisplayOrder)
        liveManifests.clear()
        sortedAccepted.associateByTo(liveManifests) { it.packageName }
        lastSnapshot = Snapshot(
            accepted = sortedAccepted,
            rejected = rejected.sortedWith(RejectedDisplayOrder),
        )
        return lastSnapshot
    }

    @Synchronized
    fun snapshot(): List<AddonManifest> = liveManifests.values.toList()

    @Synchronized
    fun lastRefresh(): Snapshot = lastSnapshot

    @Synchronized
    fun byType(type: AddonType): List<AddonManifest> =
        liveManifests.values.filter { it.type == type }

    @Synchronized
    fun dictionaryPacks(): List<AddonManifest> = byType(AddonType.DICTIONARY_PACK)

    @Synchronized
    fun runtimeEngineAddons(): List<AddonManifest> =
        liveManifests.values.filter { it.type.isRuntimeEngine }

    @Synchronized
    fun runtimeEngineAddonsFor(type: AddonType): List<AddonManifest> {
        require(type.isRuntimeEngine) {
            "runtimeEngineAddonsFor requires a runtime-engine addon type; was $type"
        }
        return byType(type)
    }

    @Synchronized
    fun manifestForPackage(packageName: String): AddonManifest? = liveManifests[packageName]

    @Synchronized
    fun manifestForStableId(stableId: String): AddonManifest? =
        liveManifests.values.firstOrNull { it.stableId == stableId }

    @Synchronized
    fun pinnedSigningCertificates(): Map<String, String> = pinnedSigningCertificates.toMap()

    @Synchronized
    fun pinnedSigningPinSet(): AddonSigningPinSet =
        AddonSigningPinSet(pinnedSigningCertificates)

    /**
     * Clears process-live addon state without clearing signing pins. Used when
     * the IME wants a clean rescan after package changes.
     */
    @Synchronized
    fun clearRuntimeState() {
        liveManifests.clear()
        lastSnapshot = Snapshot(emptyList(), emptyList())
    }

    data class Snapshot(
        val accepted: List<AddonManifest>,
        val rejected: List<RejectedAddon>,
    )

    data class RejectedAddon(
        val packageName: String,
        val displayName: String?,
        val signingCertSha256: String?,
        val reason: String,
    )

    companion object {
        const val ReasonExplicitTrustRequired = "explicit trust required"
        const val ReasonSigningCertificateChanged = "signing certificate changed"

        fun fromPinnedSigningPinSet(
            pinSet: AddonSigningPinSet,
            trustedRootSigningCertSha256: String? = null,
        ): AddonRegistry =
            AddonRegistry(
                initialPinnedSigningCertificates = pinSet.asMap(),
                trustedRootSigningCertSha256 = trustedRootSigningCertSha256,
            )

        val DisplayOrder: Comparator<AddonManifest> =
            compareBy<AddonManifest> { it.type.metadataValue }
                .thenBy { it.displayName.lowercase(Locale.ROOT) }
                .thenBy { it.packageName }

        val RejectedDisplayOrder: Comparator<RejectedAddon> =
            compareBy<RejectedAddon> { it.packageName }.thenBy { it.reason }

        private val DisplayOrderWithNewestVersion: Comparator<AddonManifest> =
            compareBy<AddonManifest> { it.version }
                .thenBy { it.type.metadataValue }
                .thenBy { it.displayName.lowercase(Locale.ROOT) }
                .thenBy { it.packageName }
    }
}
