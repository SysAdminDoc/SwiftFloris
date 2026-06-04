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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * ROADMAP matrix #36 — dictionary-pack (and general addon) provenance and signing report.
 *
 * SwiftFloris's addon model lets external APKs supply dictionary packs, theme packs, layout packs, popup mappings,
 * and language packs. Those packs are user-installed and may originate from any of: F-Droid, Play, Aurora,
 * IzzyOnDroid, Obtainium, the maintainer's own GitHub Releases, or a sideloaded APK someone got off Telegram.
 * Once a user clicks "enable" on an addon in Settings → Addons, they have effectively granted that addon read
 * access to keyboard state on every text field — *modulo* the SensitiveFieldGuard short-circuit.
 *
 * This data structure is the minimum-correct "show me what I just turned on" surface: it bundles every field a
 * privacy-curious user needs to make an informed enrolment decision, plus the no-network attestation that the
 * enumerator already enforced at scan time. The renderer turns it into a stable plain-text and JSON form so users
 * (and reviewers) can export the report, paste it into an audit log, or hand it to someone else for a quick
 * sniff-test.
 *
 * - `noNetworkAttested` is always `true` for an enrolled addon — [AddonEnumerator] rejects any package declaring
 *   INTERNET / ACCESS_NETWORK_STATE / ACCESS_WIFI_STATE / CHANGE_NETWORK_STATE / CHANGE_WIFI_STATE. The field is
 *   present in this report so the user-facing surface can show it explicitly rather than letting users infer it
 *   from the absence of a warning.
 * - `datasetLicenseSpdxId` and `datasetSource` are populated for `DICTIONARY_PACK` addons (from the
 *   [DictionaryPackDescriptor]) but null for other types. The addon-level [AddonManifest.licenseSpdxId] always
 *   carries the *APK code* license (Apache-2.0 / GPL-3.0-only / etc.), which may differ from the *dataset* license
 *   for a dictionary pack — e.g. a GPL-licensed APK shipping a CC-BY-SA word list.
 * - `signingCertSha256` is the upper-cased colon-separated SHA-256 fingerprint of the addon's signing certificate.
 *   Co-signed addons can load automatically; non-co-signed addons are shown in Settings until users explicitly
 *   pin this fingerprint. Users can compare it against a fingerprint published by the addon maintainer.
 */
data class AddonProvenanceReport(
    val packageName: String,
    val displayName: String,
    val addonType: AddonType,
    val addonVersion: Long,
    val apkLicenseSpdxId: String,
    val signingCertSha256: String,
    val bundleSizeBytes: Long,
    val datasetLicenseSpdxId: String? = null,
    val datasetSource: String? = null,
    val noNetworkAttested: Boolean = true,
) {

    /** Render the report as a plain-text, line-per-field block suitable for Settings → Addons → Details. */
    fun toPlainText(): String = buildString {
        appendLine("Addon: $displayName")
        appendLine("  Package:           $packageName")
        appendLine("  Type:              ${addonType.metadataValue}")
        appendLine("  Version:           $addonVersion")
        appendLine("  APK license:       $apkLicenseSpdxId")
        if (datasetLicenseSpdxId != null) {
            appendLine("  Dataset license:   $datasetLicenseSpdxId")
        }
        if (datasetSource != null) {
            appendLine("  Dataset source:    $datasetSource")
        }
        appendLine("  Bundle size:       ${formatBytes(bundleSizeBytes)}")
        appendLine("  Signing fp SHA-256: $signingCertSha256")
        append("  No-network attest: ")
        appendLine(if (noNetworkAttested) "yes (no INTERNET / ACCESS_NETWORK_* permissions)" else "FAILED")
    }

    /** Render the report as a stable, sorted JSON object suitable for export. */
    fun toJson(): String {
        val obj = JsonObject(buildMap {
            put("packageName", JsonPrimitive(packageName))
            put("displayName", JsonPrimitive(displayName))
            put("addonType", JsonPrimitive(addonType.metadataValue))
            put("addonVersion", JsonPrimitive(addonVersion))
            put("apkLicenseSpdxId", JsonPrimitive(apkLicenseSpdxId))
            put("bundleSizeBytes", JsonPrimitive(bundleSizeBytes))
            put("signingCertSha256", JsonPrimitive(signingCertSha256))
            if (datasetLicenseSpdxId != null) {
                put("datasetLicenseSpdxId", JsonPrimitive(datasetLicenseSpdxId))
            }
            if (datasetSource != null) {
                put("datasetSource", JsonPrimitive(datasetSource))
            }
            put("noNetworkAttested", JsonPrimitive(noNetworkAttested))
        })
        return Json.encodeToString(JsonObject.serializer(), obj)
    }

    companion object {
        /**
         * Build a report from an [AddonManifest] alone. Use this overload for non-dictionary addon types or for
         * dictionary addons whose descriptor failed to parse — the dataset-level fields stay null so callers can
         * still surface the APK-level provenance.
         */
        fun from(manifest: AddonManifest): AddonProvenanceReport {
            return AddonProvenanceReport(
                packageName = manifest.packageName,
                displayName = manifest.displayName,
                addonType = manifest.type,
                addonVersion = manifest.version,
                apkLicenseSpdxId = manifest.licenseSpdxId,
                signingCertSha256 = manifest.signingCertSha256,
                bundleSizeBytes = manifest.bundleSizeBytes,
            )
        }

        /**
         * Build a report from a [AddonManifest] of type [AddonType.DICTIONARY_PACK] plus its loaded
         * [DictionaryPackDescriptor]. The dataset-level fields are populated from the descriptor.
         */
        fun fromDictionaryPack(
            manifest: AddonManifest,
            descriptor: DictionaryPackDescriptor,
        ): AddonProvenanceReport {
            require(manifest.type == AddonType.DICTIONARY_PACK) {
                "fromDictionaryPack called with non-dictionary addon type ${manifest.type}"
            }
            return AddonProvenanceReport(
                packageName = manifest.packageName,
                displayName = manifest.displayName,
                addonType = manifest.type,
                addonVersion = manifest.version,
                apkLicenseSpdxId = manifest.licenseSpdxId,
                signingCertSha256 = manifest.signingCertSha256,
                bundleSizeBytes = manifest.bundleSizeBytes,
                datasetLicenseSpdxId = descriptor.license,
                datasetSource = descriptor.source,
            )
        }

        private fun formatBytes(bytes: Long): String {
            val kib = 1024L
            val mib = kib * 1024
            return when {
                bytes >= mib -> "%.2f MiB".format(bytes.toDouble() / mib)
                bytes >= kib -> "%.2f KiB".format(bytes.toDouble() / kib)
                else -> "$bytes B"
            }
        }
    }
}

/**
 * Bundle several reports into a single ordered list. Stable sort by (addonType, packageName) so the output is
 * deterministic across enumerations.
 */
fun List<AddonProvenanceReport>.sortedForDisplay(): List<AddonProvenanceReport> =
    sortedWith(compareBy({ it.addonType.ordinal }, { it.packageName }))
