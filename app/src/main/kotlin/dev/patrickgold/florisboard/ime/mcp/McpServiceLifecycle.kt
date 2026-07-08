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

    /** Tear down the bridge. Idempotent. */
    fun stop() {
        if (!started) return
        started = false
        for (key in McpDaemonRegistry.active().keys) {
            unbindCallback(key)
        }
        shutdownCallback()
        McpDaemonRegistry.setActive(emptyMap())
        McpClientRegistry.setActive(NoOpMcpClient)
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
            )
            if (!bridgeEnabled) {
                McpDaemonDiscoveryStore.reset()
                McpDaemonRegistry.setActive(emptyMap())
                McpClientRegistry.setActive(NoOpMcpClient)
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
            return lifecycle
        }
    }
}
