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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

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
    test("reconcile accepts co-signed addons without saving explicit pins") {
        val result = AddonRegistryStartup.reconcile(
            discovered = listOf(startupManifest("org.swiftfloris.dict.pl")),
            persistedSigningPinsRaw = "",
            trustedRootSigningCertSha256 = STARTUP_SHA_A,
        )

        result.snapshot.accepted.map { it.packageName } shouldContainExactly listOf(
            "org.swiftfloris.dict.pl",
        )
        result.encodedSigningPins shouldBe ""
        result.signingPinsChanged shouldBe false
        result.registry.dictionaryPacks().map { it.packageName } shouldContainExactly listOf(
            "org.swiftfloris.dict.pl",
        )
    }

    test("reconcile keeps first-seen external addons pending until a pin exists") {
        val result = AddonRegistryStartup.reconcile(
            discovered = listOf(startupManifest("org.swiftfloris.dict.pl", STARTUP_SHA_B)),
            persistedSigningPinsRaw = "",
            trustedRootSigningCertSha256 = STARTUP_SHA_A,
        )

        result.snapshot.accepted shouldBe emptyList()
        result.snapshot.rejected.single().reason shouldBe AddonRegistry.ReasonExplicitTrustRequired
        result.snapshot.rejected.single().signingCertSha256 shouldBe STARTUP_SHA_B
        result.encodedSigningPins shouldBe ""
        result.signingPinsChanged shouldBe false
    }

    test("reconcile surfaces package-level enumerator rejections without certificate trust actions") {
        val result = AddonRegistryStartup.reconcile(
            discovered = emptyList(),
            persistedSigningPinsRaw = "",
            packageRejections = listOf(
                AddonEnumerator.RejectedPackage(
                    packageName = "org.swiftfloris.dict.huge",
                    displayName = "Huge Dictionary",
                    reason = "bundle size 67108865 exceeds 67108864 bytes",
                ),
            ),
        )

        result.snapshot.accepted shouldBe emptyList()
        result.snapshot.rejected.single().packageName shouldBe "org.swiftfloris.dict.huge"
        result.snapshot.rejected.single().displayName shouldBe "Huge Dictionary"
        result.snapshot.rejected.single().signingCertSha256 shouldBe null
        result.snapshot.rejected.single().reason shouldBe "bundle size 67108865 exceeds 67108864 bytes"

        AddonRegistryStore.setActive(result.registry, result.snapshot)
        AddonRegistryStore.snapshot().rejected.single().packageName shouldBe "org.swiftfloris.dict.huge"
    }

    test("reconcile accepts explicitly pinned external addons") {
        val result = AddonRegistryStartup.reconcile(
            discovered = listOf(startupManifest("org.swiftfloris.dict.pl", STARTUP_SHA_B)),
            persistedSigningPinsRaw = "org.swiftfloris.dict.pl=$STARTUP_SHA_B",
            trustedRootSigningCertSha256 = STARTUP_SHA_A,
        )

        result.snapshot.accepted.map { it.packageName } shouldContainExactly listOf(
            "org.swiftfloris.dict.pl",
        )
        result.snapshot.rejected shouldBe emptyList()
        result.encodedSigningPins shouldBe "org.swiftfloris.dict.pl=$STARTUP_SHA_B"
        result.signingPinsChanged shouldBe false
    }

    test("reconcile rejects changed-certificate addon and preserves old pin") {
        val result = AddonRegistryStartup.reconcile(
            discovered = listOf(startupManifest("org.swiftfloris.dict.pl", STARTUP_SHA_B)),
            persistedSigningPinsRaw = "org.swiftfloris.dict.pl=$STARTUP_SHA_A",
        )

        result.snapshot.accepted shouldBe emptyList()
        result.snapshot.rejected.single().reason shouldBe AddonRegistry.ReasonSigningCertificateChanged
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
            trustedRootSigningCertSha256 = STARTUP_SHA_A,
        )

        AddonRegistryStore.setActive(result.registry)
        AddonRegistryStore.initialized.value shouldBe true
        AddonRegistryStore.active().dictionaryPacks().map { it.packageName } shouldContainExactly listOf(
            "org.swiftfloris.dict.pl",
        )

        AddonRegistryStore.reset()
        AddonRegistryStore.initialized.value shouldBe true
        AddonRegistryStore.active().snapshot() shouldBe emptyList()
    }

    test("registry store generation changes when active registry changes") {
        val before = AddonRegistryStore.generation()

        AddonRegistryStore.setActive(AddonRegistry())
        val afterSet = AddonRegistryStore.generation()
        AddonRegistryStore.reset()
        val afterReset = AddonRegistryStore.generation()

        (afterSet > before) shouldBe true
        (afterReset > afterSet) shouldBe true
    }

    test("a startup scan that started before a trust reset isn't published") {
        // The scan read the stored pins before the reset, so publishing it would bring back
        // the trust the reset removed.
        val scanStarted = AddonRegistryStore.generation()
        AddonRegistryStore.reset()
        val staleRegistry = AddonRegistryStartup.reconcile(
            discovered = listOf(startupManifest("org.swiftfloris.dict.pl")),
            persistedSigningPinsRaw = "",
            trustedRootSigningCertSha256 = STARTUP_SHA_A,
        ).registry

        AddonRegistryStore.setActiveIfGeneration(scanStarted, staleRegistry) shouldBe false
        AddonRegistryStore.active().snapshot() shouldBe emptyList()

        AddonRegistryStore.setActiveIfGeneration(AddonRegistryStore.generation(), staleRegistry) shouldBe true
        AddonRegistryStore.active().dictionaryPacks().map { it.packageName } shouldContainExactly listOf(
            "org.swiftfloris.dict.pl",
        )
        AddonRegistryStore.reset()
    }

    test("a trust reset waits for a startup publish to save its pins, then clears them") {
        // If the reset could land between startup publishing and saving its pins, the pins
        // would be written back after the clear and the trust would return on the next start.
        var storedPins = "pins-before-reset"
        val saving = CompletableDeferred<Unit>()
        val finishSave = CompletableDeferred<Unit>()
        val registry = AddonRegistryStartup.reconcile(
            discovered = listOf(startupManifest("org.swiftfloris.dict.pl")),
            persistedSigningPinsRaw = "",
            trustedRootSigningCertSha256 = STARTUP_SHA_A,
        ).registry
        withTimeout(5_000L) {
            coroutineScope {
                val startup = launch {
                    AddonRegistryStore.setActiveIfGeneration(AddonRegistryStore.generation(), registry) {
                        saving.complete(Unit)
                        finishSave.await()
                        storedPins = "startup-pins"
                    }
                }
                saving.await()

                // Undispatched, so the reset runs here until it has to wait: without the lock it
                // would clear the pins and finish before launch returns.
                val reset = launch(start = CoroutineStart.UNDISPATCHED) {
                    AddonRegistryStore.reset { storedPins = "" }
                }
                reset.isCompleted shouldBe false
                storedPins shouldBe "pins-before-reset"

                finishSave.complete(Unit)
                startup.join()
                reset.join()
            }
        }

        storedPins shouldBe ""
        AddonRegistryStore.active().snapshot() shouldBe emptyList()
    }

    test("a confirmed trust override is saved at once and a startup scan from before can't undo it") {
        // Saved before Settings rescans, so leaving mid-scan or a scan that fails keeps it.
        val registry = AddonRegistryStartup.reconcile(
            discovered = listOf(startupManifest("org.swiftfloris.dict.pl")),
            persistedSigningPinsRaw = "",
            trustedRootSigningCertSha256 = STARTUP_SHA_A,
        ).registry
        AddonRegistryStore.setActive(registry)
        var storedPins = "older-pins"
        val scanStarted = AddonRegistryStore.generation()

        AddonRegistryStore.savePins { storedPins = "override-pins" }

        storedPins shouldBe "override-pins"
        // The registry on screen stays until the rescan publishes.
        AddonRegistryStore.active().dictionaryPacks().map { it.packageName } shouldContainExactly listOf(
            "org.swiftfloris.dict.pl",
        )
        val stalePublished = AddonRegistryStore.setActiveIfGeneration(scanStarted, AddonRegistry()) {
            storedPins = "older-pins"
        }
        stalePublished shouldBe false
        storedPins shouldBe "override-pins"
        AddonRegistryStore.reset()
    }

    test("a startup scan overtaken by a confirmed trust scans again with it and publishes") {
        // Settings' own rescan may fail or be left, so startup is what publishes the override.
        var storedPins = "older-pins"
        val scannedWith = mutableListOf<String>()
        val pl = startupManifest("org.swiftfloris.dict.pl")

        val result = AddonRegistryStartup.publishAtStartup(
            readPins = { storedPins },
            scan = { pins ->
                scannedWith += pins
                if (scannedWith.size == 1) AddonRegistryStore.savePins { storedPins = "override-pins" }
                val reconciled = AddonRegistryStartup.reconcile(
                    discovered = listOf(pl),
                    persistedSigningPinsRaw = "",
                    trustedRootSigningCertSha256 = STARTUP_SHA_A,
                )
                reconciled.copy(encodedSigningPins = "saved-from-$pins", signingPinsChanged = true)
            },
            savePins = { storedPins = it },
        )

        scannedWith shouldContainExactly listOf("older-pins", "override-pins")
        result?.encodedSigningPins shouldBe "saved-from-override-pins"
        storedPins shouldBe "saved-from-override-pins"
        AddonRegistryStore.active().dictionaryPacks().map { it.packageName } shouldContainExactly listOf(
            "org.swiftfloris.dict.pl",
        )
        AddonRegistryStore.reset()
    }

    test("a startup scan overtaken by a registry Settings published is dropped, not run again") {
        var storedPins = "older-pins"
        var scans = 0

        val result = AddonRegistryStartup.publishAtStartup(
            readPins = { storedPins },
            scan = { pins ->
                scans++
                // Only the first scan is overtaken, so one run again would publish over Settings.
                if (scans == 1) AddonRegistryStore.setActive(AddonRegistry()) { storedPins = "settings-pins" }
                AddonRegistryStartup.reconcile(discovered = emptyList(), persistedSigningPinsRaw = pins)
                    .copy(encodedSigningPins = "startup-pins", signingPinsChanged = true)
            },
            savePins = { storedPins = it },
        )

        result shouldBe null
        scans shouldBe 1
        storedPins shouldBe "settings-pins"
        AddonRegistryStore.reset()
    }
})
