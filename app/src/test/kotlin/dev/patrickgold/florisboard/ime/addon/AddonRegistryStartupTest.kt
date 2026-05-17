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

private const val STARTUP_SHA_A =
    "01:01:01:01:01:01:01:01:01:01:01:01:01:01:01:01:" +
        "01:01:01:01:01:01:01:01:01:01:01:01:01:01:01:01"
private const val STARTUP_SHA_B =
    "02:02:02:02:02:02:02:02:02:02:02:02:02:02:02:02:" +
        "02:02:02:02:02:02:02:02:02:02:02:02:02:02:02:02"

private fun startupManifest(
    packageName: String,
    signingCertSha256: String = STARTUP_SHA_A,
): AddonManifest = AddonManifest(
    packageName = packageName,
    type = AddonType.DICTIONARY_PACK,
    version = 1L,
    displayName = packageName,
    descriptorResourceId = 1234,
    licenseSpdxId = "Apache-2.0",
    signingCertSha256 = signingCertSha256,
    bundleSizeBytes = 4096L,
)

class AddonRegistryStartupTest : FunSpec({
    test("reconcile accepts new addons and emits updated persisted pins") {
        val result = AddonRegistryStartup.reconcile(
            discovered = listOf(startupManifest("org.swiftfloris.dict.pl")),
            persistedSigningPinsRaw = "",
        )

        result.snapshot.accepted.map { it.packageName } shouldContainExactly listOf(
            "org.swiftfloris.dict.pl",
        )
        result.encodedSigningPins shouldBe "org.swiftfloris.dict.pl=$STARTUP_SHA_A"
        result.signingPinsChanged shouldBe true
        result.registry.dictionaryPacks().map { it.packageName } shouldContainExactly listOf(
            "org.swiftfloris.dict.pl",
        )
    }

    test("reconcile rejects changed-certificate addon and preserves old pin") {
        val result = AddonRegistryStartup.reconcile(
            discovered = listOf(startupManifest("org.swiftfloris.dict.pl", STARTUP_SHA_B)),
            persistedSigningPinsRaw = "org.swiftfloris.dict.pl=$STARTUP_SHA_A",
        )

        result.snapshot.accepted shouldBe emptyList()
        result.snapshot.rejected.single().reason shouldBe "signing certificate changed"
        result.encodedSigningPins shouldBe "org.swiftfloris.dict.pl=$STARTUP_SHA_A"
        result.signingPinsChanged shouldBe false
    }

    test("reconcile cleans corrupt stored pin lines") {
        val result = AddonRegistryStartup.reconcile(
            discovered = emptyList(),
            persistedSigningPinsRaw = """
                org.swiftfloris.dict.bad=not-a-fingerprint
                not-a-package=$STARTUP_SHA_A
            """.trimIndent(),
        )

        result.snapshot.accepted shouldBe emptyList()
        result.encodedSigningPins shouldBe ""
        result.signingPinsChanged shouldBe true
    }

    test("registry store publishes and resets the active registry") {
        val result = AddonRegistryStartup.reconcile(
            discovered = listOf(startupManifest("org.swiftfloris.dict.pl")),
            persistedSigningPinsRaw = "",
        )

        AddonRegistryStore.setActive(result.registry)
        AddonRegistryStore.active().dictionaryPacks().map { it.packageName } shouldContainExactly listOf(
            "org.swiftfloris.dict.pl",
        )

        AddonRegistryStore.reset()
        AddonRegistryStore.active().snapshot() shouldBe emptyList()
    }
})
