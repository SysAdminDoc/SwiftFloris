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
 * ROADMAP §7 Next-10.3a — typed view of enrolled dictionary-pack addons.
 *
 * The Android resource-read path will supply descriptor JSON by package name
 * once Settings → Addons is wired. Keeping the catalog builder pure lets the
 * enrolment rules, compatibility checks, provenance report handoff, and
 * language lookup semantics ship before the Compose UI and APK asset mounting
 * slice.
 */
data class DictionaryPackCatalog(
    val entries: List<Entry>,
    val rejected: List<RejectedDescriptor>,
) {
    fun forLanguage(language: String): List<Entry> {
        val normalized = language.lowercase(Locale.ROOT)
        return entries.filter { it.descriptor.language == normalized }
    }

    data class Entry(
        val manifest: AddonManifest,
        val descriptor: DictionaryPackDescriptor,
        val provenanceReport: AddonProvenanceReport,
    ) {
        val packageName: String get() = manifest.packageName
        val stableId: String get() = manifest.stableId
        val language: String get() = descriptor.language
        val displayName: String get() = descriptor.displayName
    }

    data class RejectedDescriptor(
        val packageName: String,
        val displayName: String,
        val reason: String,
    )

    companion object {
        fun build(
            manifests: List<AddonManifest>,
            descriptorJsonFor: (AddonManifest) -> String?,
        ): DictionaryPackCatalog {
            val entries = mutableListOf<Entry>()
            val rejected = mutableListOf<RejectedDescriptor>()

            for (manifest in manifests.sortedWith(AddonRegistry.DisplayOrder)) {
                if (manifest.type != AddonType.DICTIONARY_PACK) continue
                val rawDescriptor = descriptorJsonFor(manifest)
                if (rawDescriptor.isNullOrBlank()) {
                    rejected += manifest.rejection("missing descriptor JSON")
                    continue
                }
                val descriptor = DictionaryPackDescriptor.parse(rawDescriptor)
                if (descriptor == null) {
                    rejected += manifest.rejection("invalid descriptor JSON")
                    continue
                }
                if (!descriptor.isCompatibleWithIme()) {
                    rejected += manifest.rejection(
                        "descriptor schema ${descriptor.schema} is incompatible with IME schema " +
                            DictionaryPackDescriptor.SUPPORTED_SCHEMA,
                    )
                    continue
                }
                entries += Entry(
                    manifest = manifest,
                    descriptor = descriptor,
                    provenanceReport = AddonProvenanceReport.fromDictionaryPack(manifest, descriptor),
                )
            }

            return DictionaryPackCatalog(
                entries = entries.sortedWith(
                    compareBy<Entry> { it.language }
                        .thenBy { it.displayName.lowercase(Locale.ROOT) }
                        .thenBy { it.packageName },
                ),
                rejected = rejected.sortedWith(
                    compareBy<RejectedDescriptor> { it.packageName }.thenBy { it.reason },
                ),
            )
        }

        fun build(
            manifests: List<AddonManifest>,
            descriptorJsonByPackageName: Map<String, String>,
        ): DictionaryPackCatalog =
            build(manifests) { manifest -> descriptorJsonByPackageName[manifest.packageName] }

        private fun AddonManifest.rejection(reason: String): RejectedDescriptor =
            RejectedDescriptor(
                packageName = packageName,
                displayName = displayName,
                reason = reason,
            )
    }
}
