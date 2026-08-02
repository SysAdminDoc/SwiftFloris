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

package dev.patrickgold.florisboard.ime.mcp

import android.content.Context
import dev.patrickgold.florisboard.lib.devtools.flogInfo

/**
 * ROADMAP §10.5 L7.5b — top-level orchestration that walks the
 * end-to-end MCP bridge for the IME-side process.
 *
 * Lifecycle owned by [FlorisImeService]:
 *
 *  - [start] is called from `onCreate` after the user has explicitly
 *    enabled the MCP bridge. It runs a single discovery pass, publishes
 *    the daemon list into [McpDaemonRegistry], binds every daemon via
 *    [McpServiceConnectionManager], and installs an [AndroidMcpClient]
 *    backed by the manager's `binderFor` into [McpClientRegistry].
 *  - [stop] is called from `onDestroy`. It unbinds every daemon and
 *    resets both registries to their startup defaults.
 *
 * The class is testable around its observable state: tests inject a
 * pre-built `Map<DaemonKey, DaemonEntry>` (via [startWithDaemons])
 * and a fake `bind`/`unbind` lambda, avoiding the `Context` /
 * `bindService` glue.
 */
class McpServiceLifecycle(
    private val bindCallback: (DaemonKey) -> Boolean,
    private val unbindCallback: (DaemonKey) -> Unit,
    private val shutdownCallback: () -> Unit,
    private val binderLookup: (DaemonKey) -> android.os.IBinder?,
    private val isBridgeEnabled: () -> Boolean = { true },
    private val retryCallback: (DaemonKey) -> Boolean = { false },
) {

    private var started: Boolean = false

    /**
     * Start the bridge with a pre-discovered daemon map. Used directly
     * by tests + indirectly by the production [start] glue below.
     */
    fun startWithDaemons(daemons: Map<DaemonKey, DaemonEntry>) {
        check(!started) { "McpServiceLifecycle already started" }
        if (!isBridgeEnabled()) {
            McpDaemonRegistry.setActive(emptyMap())
            McpClientRegistry.setActive(NoOpMcpClient)
            McpConnectionStateStore.reset()
            flogInfo { "MCP bridge: disabled until user consent is granted" }
            return
        }
        started = true
        McpDaemonRegistry.setActive(daemons)
        for (key in daemons.keys) {
            bindCallback(key)
        }
        McpClientRegistry.setActive(AndroidMcpClient(binderLookup))
        flogInfo { "MCP bridge: bound ${daemons.size} daemon(s)" }
    }

    /**
     * Replace the live eligible-daemon set after an explicit PackageManager
     * rescan. The registry is narrowed before removed services are unbound so
     * dispatch cannot race a package that just became ineligible.
     */
    @Synchronized
    fun replaceDaemons(daemons: Map<DaemonKey, DaemonEntry>) {
        if (!started) return
        val previousKeys = McpDaemonRegistry.active().keys
        val nextKeys = daemons.keys
        McpDaemonRegistry.setActive(daemons)
        for (key in previousKeys - nextKeys) {
            unbindCallback(key)
        }
        for (key in nextKeys - previousKeys) {
            bindCallback(key)
        }
        // Drop reported states for daemons that discovery no longer accepts, so Settings cannot
        // keep showing a connection for a package that was revoked or uninstalled.
        McpConnectionStateStore.retainOnly(nextKeys)
        flogInfo {
            "MCP bridge: discovery rescan accepted ${daemons.size} daemon(s)"
        }
    }

    /**
     * Re-attempts a binding the user asked to recover. Returns whether a running bridge received
     * the request; the daemon's observable state carries the bind outcome itself.
     */
    fun retryDaemon(daemonKey: DaemonKey): Boolean {
        if (!started) return false
        retryCallback(daemonKey)
        return true
    }

    /** Tear down the bridge. Idempotent. */
    fun stop() {
        if (!started) return
        started = false
        try {
            for (key in McpDaemonRegistry.active().keys) {
                unbindCallback(key)
            }
            shutdownCallback()
        } finally {
            McpDaemonRegistry.setActive(emptyMap())
            McpClientRegistry.setActive(NoOpMcpClient)
            McpConnectionStateStore.reset()
            unregisterActiveLifecycle(this)
        }
    }

    val isStarted: Boolean get() = started

    companion object {
        /**
         * Production-side factory. Wires the lifecycle into a real
         * [McpServiceConnectionManager] backed by [appContext], discovering
         * daemons via [McpAndroidDiscoverer.runDiscovery]. Returns the
         * started lifecycle for the caller to retain.
         */
        fun start(
            appContext: Context,
            persistedSigningPinsRaw: String = "",
            trustedRootSigningCertSha256: String? = null,
            bridgeEnabled: Boolean = true,
        ): McpServiceLifecycle {
            val manager = McpServiceConnectionManager(appContext)
            val lifecycle = McpServiceLifecycle(
                bindCallback = manager::bind,
                unbindCallback = manager::unbind,
                shutdownCallback = manager::shutdown,
                binderLookup = manager::binderFor,
                isBridgeEnabled = { bridgeEnabled },
                retryCallback = manager::retry,
            )
            if (!bridgeEnabled) {
                McpDaemonDiscoveryStore.reset()
                McpDaemonRegistry.setActive(emptyMap())
                McpClientRegistry.setActive(NoOpMcpClient)
                McpConnectionStateStore.reset()
                flogInfo { "MCP bridge: startup skipped until user consent is granted" }
                return lifecycle
            }
            val snapshot = runCatching {
                McpAndroidDiscoverer.runDiscoverySnapshot(
                    context = appContext,
                    persistedSigningPinsRaw = persistedSigningPinsRaw,
                    trustedRootSigningCertSha256 = trustedRootSigningCertSha256,
                )
            }.getOrDefault(McpDiscoverySnapshot.Empty)
            McpDaemonDiscoveryStore.setActive(snapshot)
            lifecycle.startWithDaemons(snapshot.accepted)
            registerActiveLifecycle(lifecycle)
            return lifecycle
        }

        /**
         * Apply a Settings-triggered trust/package rescan to the running IME
         * lifecycle, if one exists in this process.
         */
        fun reconcileActiveDaemons(daemons: Map<DaemonKey, DaemonEntry>): Boolean {
            val lifecycle = synchronized(ActiveLifecycleLock) { activeLifecycle }
                ?: return false
            lifecycle.replaceDaemons(daemons)
            return true
        }

        /**
         * Settings-triggered retry for a daemon whose binding failed or died. Returns false when
         * no bridge is running in this process, which Settings reports rather than pretending the
         * daemon reconnected.
         */
        fun retryActiveDaemon(daemonKey: DaemonKey): Boolean {
            val lifecycle = synchronized(ActiveLifecycleLock) { activeLifecycle }
                ?: return false
            return lifecycle.retryDaemon(daemonKey)
        }

        private val ActiveLifecycleLock = Any()
        private var activeLifecycle: McpServiceLifecycle? = null

        private fun registerActiveLifecycle(lifecycle: McpServiceLifecycle) {
            synchronized(ActiveLifecycleLock) {
                activeLifecycle = lifecycle
            }
        }

        private fun unregisterActiveLifecycle(lifecycle: McpServiceLifecycle) {
            synchronized(ActiveLifecycleLock) {
                if (activeLifecycle === lifecycle) {
                    activeLifecycle = null
                }
            }
        }
    }
}
