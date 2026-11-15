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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference

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
        trustedRootSigningCertSha256: String? = null,
        packageRejections: List<AddonEnumerator.RejectedPackage> = emptyList(),
    ): Result {
        val persistedPins = AddonSigningPinSet.parse(persistedSigningPinsRaw)
        val registry = AddonRegistry.fromPinnedSigningPinSet(
            pinSet = persistedPins,
            trustedRootSigningCertSha256 = trustedRootSigningCertSha256,
        )
        val registrySnapshot = registry.refresh(discovered)
        val packageRejectedAddons = packageRejections.map { rejected ->
            AddonRegistry.RejectedAddon(
                packageName = rejected.packageName,
                displayName = rejected.displayName,
                signingCertSha256 = null,
                reason = rejected.reason,
            )
        }
        val snapshot = registrySnapshot.copy(
            rejected = (registrySnapshot.rejected + packageRejectedAddons)
                .sortedWith(AddonRegistry.RejectedDisplayOrder),
        )
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

    /** How often a startup scan runs again after Settings saved new pins under it. */
    private const val MaxStartupScans = 3

    /**
     * Scans with the stored pins and publishes the result, unless Settings changed the trust state
     * meanwhile. A registry Settings published or reset wins and this result is dropped. If Settings
     * only saved pins (a trust confirmed before its own rescan), the scan runs again with them, since
     * that rescan may never publish. Returns the published result, or null if it was dropped.
     *
     * A failed scan empties only what this startup would have published, and rethrows.
     */
    suspend fun publishAtStartup(
        readPins: suspend () -> String,
        scan: suspend (persistedPins: String) -> Result,
        savePins: suspend (encodedPins: String) -> Unit,
    ): Result? {
        repeat(MaxStartupScans) {
            // Taken before the stored pins are read, so a change made while this scans is noticed.
            val startGeneration = AddonRegistryStore.generation()
            val result = try {
                scan(readPins())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AddonRegistryStore.setActiveIfGeneration(startGeneration, AddonRegistry())
                throw e
            }
            val published = AddonRegistryStore.setActiveIfGeneration(
                expectedGeneration = startGeneration,
                registry = result.registry,
                snapshot = result.snapshot,
            ) {
                if (result.signingPinsChanged) savePins(result.encodedSigningPins)
            }
            if (published) return result
            if (AddonRegistryStore.publishedSince(startGeneration)) return null
        }
        return null
    }
}

/**
 * Process-wide view of the latest addon registry. Settings and runtime
 * consumers should read this snapshot rather than re-running PackageManager
 * scans on hot paths.
 */
object AddonRegistryStore {
    /**
     * One value, so a reader never pairs one registry with another's snapshot or generation.
     * [publishedGeneration] is the generation [registry] was published or reset at; a pin save
     * moves [generation] on without it.
     */
    private class Active(
        val registry: AddonRegistry,
        val snapshot: AddonRegistry.Snapshot,
        val generation: Long,
        val publishedGeneration: Long = generation,
    )

    private val published = AtomicReference(AddonRegistry().let { Active(it, it.lastRefresh(), 0L) })
    private val initializedFlow = MutableStateFlow(false)

    val initialized: StateFlow<Boolean> = initializedFlow.asStateFlow()

    fun active(): AddonRegistry = published.get().registry

    fun snapshot(): AddonRegistry.Snapshot = published.get().snapshot

    fun generation(): Long = published.get().generation

    /** Whether a registry was published or reset after [generation], not only pins saved. */
    fun publishedSince(generation: Long): Boolean = published.get().publishedGeneration > generation

    /**
     * Held while a registry is published or reset together with the pin write that goes with it,
     * so a reset can't land between a publish and its pins being saved. A Mutex, since the pin
     * write is a suspending preference write, and a caller on the main thread waits without
     * blocking it.
     */
    private val trustLock = Mutex()

    /** Publishes [registry] and runs [savePins] as one step against any reset. */
    suspend fun setActive(
        registry: AddonRegistry,
        snapshot: AddonRegistry.Snapshot = registry.lastRefresh(),
        savePins: suspend () -> Unit = {},
    ) = trustLock.withLock {
        published.updateAndGet { Active(registry, snapshot, it.generation + 1L) }
        savePins()
        initializedFlow.value = true
    }

    /**
     * Publishes [registry] and runs [savePins] only if nothing was published or reset since
     * [expectedGeneration]. A scan that read the stored pins before a "Reset trust" would otherwise
     * bring back the trust the reset removed. Returns whether it was published.
     */
    suspend fun setActiveIfGeneration(
        expectedGeneration: Long,
        registry: AddonRegistry,
        snapshot: AddonRegistry.Snapshot = registry.lastRefresh(),
        savePins: suspend () -> Unit = {},
    ): Boolean = trustLock.withLock {
        if (published.get().generation != expectedGeneration) return false
        published.set(Active(registry, snapshot, expectedGeneration + 1L))
        savePins()
        initializedFlow.value = true
        true
    }

    /**
     * Runs [savePins] as a step of its own, keeping the published registry. It counts as a change,
     * so a startup scan that read the stored pins before it can't publish them or save them back;
     * [AddonRegistryStartup.publishAtStartup] scans again with the saved pins instead.
     */
    suspend fun savePins(savePins: suspend () -> Unit) {
        trustLock.withLock {
            savePins()
            published.updateAndGet { Active(it.registry, it.snapshot, it.generation + 1L, it.publishedGeneration) }
        }
    }

    /** Empties the registry and runs [clearPins] as one step against any publish. */
    suspend fun reset(clearPins: suspend () -> Unit = {}) = trustLock.withLock {
        clearPins()
        val registry = AddonRegistry()
        val snapshot = registry.lastRefresh()
        published.updateAndGet { Active(registry, snapshot, it.generation + 1L) }
        initializedFlow.value = true
    }
}
