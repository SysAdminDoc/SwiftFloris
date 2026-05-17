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

/**
 * ROADMAP §7 Next-10.3c — pure startup reconciliation for addon enrolment.
 *
 * Production startup supplies the PackageManager snapshot from [AddonEnumerator]
 * and the persisted `prefs.addon.signingCertPins` string. This reconciler
 * builds the live [AddonRegistry], reports whether the persisted pin string
 * changed, and normalises corrupt preference lines out of the stored value.
 */
object AddonRegistryStartup {
    fun reconcile(
        discovered: List<AddonManifest>,
        persistedSigningPinsRaw: String,
    ): Result {
        val persistedPins = AddonSigningPinSet.parse(persistedSigningPinsRaw)
        val registry = AddonRegistry.fromPinnedSigningPinSet(persistedPins)
        val snapshot = registry.refresh(discovered)
        val encodedPins = registry.pinnedSigningPinSet().encode()
        val normalizedExistingPins = persistedSigningPinsRaw.trim()
        return Result(
            registry = registry,
            snapshot = snapshot,
            encodedSigningPins = encodedPins,
            signingPinsChanged = encodedPins != normalizedExistingPins,
        )
    }

    data class Result(
        val registry: AddonRegistry,
        val snapshot: AddonRegistry.Snapshot,
        val encodedSigningPins: String,
        val signingPinsChanged: Boolean,
    )
}

/**
 * Process-wide view of the latest addon registry. Settings and runtime
 * consumers should read this snapshot rather than re-running PackageManager
 * scans on hot paths.
 */
object AddonRegistryStore {
    @Volatile
    private var activeRegistry: AddonRegistry = AddonRegistry()

    fun active(): AddonRegistry = activeRegistry

    fun setActive(registry: AddonRegistry) {
        activeRegistry = registry
    }

    fun reset() {
        activeRegistry = AddonRegistry()
    }
}
