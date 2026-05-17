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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

private const val REGISTRY_SHA_A =
    "AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:" +
        "AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA"
private const val REGISTRY_SHA_B =
    "BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:" +
        "BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB"

private fun registryManifest(
    packageName: String,
    type: AddonType = AddonType.DICTIONARY_PACK,
    version: Long = 1L,
    displayName: String = packageName,
    signingCertSha256: String = REGISTRY_SHA_A,
): AddonManifest = AddonManifest(
    packageName = packageName,
    type = type,
    version = version,
    displayName = displayName,
    descriptorResourceId = 1234,
    licenseSpdxId = "Apache-2.0",
    signingCertSha256 = signingCertSha256,
    bundleSizeBytes = 1024L,
)

class AddonRegistryTest : FunSpec({
    test("refresh pins first-seen signing certificates and exposes deterministic state") {
        val registry = AddonRegistry()
        val dictionary = registryManifest(
            packageName = "org.swiftfloris.dict.pl",
            displayName = "Polish",
        )
        val theme = registryManifest(
            packageName = "org.swiftfloris.theme.dark",
            type = AddonType.THEME_PACK,
            displayName = "Dark Theme",
        )

        val snapshot = registry.refresh(listOf(theme, dictionary))

        snapshot.accepted.map { it.packageName } shouldContainExactly listOf(
            "org.swiftfloris.dict.pl",
            "org.swiftfloris.theme.dark",
        )
        snapshot.rejected shouldBe emptyList()
        registry.dictionaryPacks() shouldContainExactly listOf(dictionary)
        registry.manifestForPackage("org.swiftfloris.theme.dark") shouldBe theme
        registry.manifestForStableId("addon:org.swiftfloris.dict.pl") shouldBe dictionary
        registry.pinnedSigningCertificates() shouldBe mapOf(
            "org.swiftfloris.dict.pl" to REGISTRY_SHA_A,
            "org.swiftfloris.theme.dark" to REGISTRY_SHA_A,
        )
    }

    test("refresh rejects package-name hijack with changed signing certificate") {
        val registry = AddonRegistry(
            initialPinnedSigningCertificates = mapOf("org.swiftfloris.dict.pl" to REGISTRY_SHA_A),
        )
        val hijacked = registryManifest(
            packageName = "org.swiftfloris.dict.pl",
            signingCertSha256 = REGISTRY_SHA_B,
        )

        val snapshot = registry.refresh(listOf(hijacked))

        snapshot.accepted shouldBe emptyList()
        snapshot.rejected.single().packageName shouldBe "org.swiftfloris.dict.pl"
        snapshot.rejected.single().reason shouldBe "signing certificate changed"
        registry.snapshot() shouldBe emptyList()
        registry.pinnedSigningCertificates() shouldBe mapOf(
            "org.swiftfloris.dict.pl" to REGISTRY_SHA_A,
        )
    }

    test("refresh keeps stale pins when addons disappear") {
        val registry = AddonRegistry()
        registry.refresh(listOf(registryManifest("org.swiftfloris.dict.pl")))

        registry.refresh(emptyList())

        registry.snapshot() shouldBe emptyList()
        registry.pinnedSigningCertificates() shouldBe mapOf(
            "org.swiftfloris.dict.pl" to REGISTRY_SHA_A,
        )
    }

    test("duplicate package entries collapse to newest version") {
        val registry = AddonRegistry()
        val old = registryManifest("org.swiftfloris.dict.pl", version = 1L)
        val newest = registryManifest("org.swiftfloris.dict.pl", version = 3L)

        registry.refresh(listOf(old, newest))

        registry.manifestForPackage("org.swiftfloris.dict.pl")?.version shouldBe 3L
    }

    test("clearRuntimeState does not clear signing pins") {
        val registry = AddonRegistry()
        registry.refresh(listOf(registryManifest("org.swiftfloris.dict.pl")))

        registry.clearRuntimeState()

        registry.snapshot() shouldBe emptyList()
        registry.lastRefresh() shouldBe AddonRegistry.Snapshot(emptyList(), emptyList())
        registry.pinnedSigningCertificates() shouldBe mapOf(
            "org.swiftfloris.dict.pl" to REGISTRY_SHA_A,
        )
    }
})
