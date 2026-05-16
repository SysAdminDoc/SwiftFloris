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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import java.util.concurrent.ConcurrentHashMap

/**
 * ROADMAP §10.5 L7.4b — per-daemon `bindService` lifecycle owner.
 *
 * `AndroidMcpClient` consumes a `binderLookup` lambda; this class
 * provides the production-side lambda by tracking the live
 * `IBinder` per `DaemonKey` across `ServiceConnection` callbacks.
 *
 * **Lifecycle:**
 *  - `bind(daemonKey)` — issues `Context.bindService` with
 *    `BIND_AUTO_CREATE` and stashes the resulting
 *    [ServiceConnection]. If the connection is already live, no-ops.
 *  - `onServiceConnected` — fires when the daemon's `onBind` returns;
 *    stores the [IBinder] under the daemon key.
 *  - `onServiceDisconnected` / `onBindingDied` — clears the binder
 *    reference so `binderFor` returns null while the binding is dead.
 *    `onBindingDied` additionally unbinds + rebinds (Android's
 *    recommended pattern when a service binding becomes unusable).
 *  - `unbind(daemonKey)` — calls `Context.unbindService` and drops
 *    the entry. Safe to call when not bound.
 *  - `shutdown()` — unbinds everything; called from
 *    `FlorisImeService.onDestroy`.
 *
 * **State separation:** the in-memory [BindingTable] is split out so
 * pure-JVM tests can exercise the connection-state transitions
 * without a real `Context`. The Android-bound bind/unbind glue lives
 * in [bind] / [unbind] and isn't pure-JVM testable; that surface is
 * deliberately thin (one `bindService` call, one `unbindService` call).
 */
class McpServiceConnectionManager(
    private val appContext: Context,
    private val table: BindingTable = BindingTable(),
) {

    /**
     * Returns the current live binder for [daemonKey], or null when no
     * connection is bound. Pass this method reference straight to
     * [AndroidMcpClient]'s constructor.
     */
    fun binderFor(daemonKey: DaemonKey): IBinder? = table.binderFor(daemonKey)

    /**
     * Bind to the daemon identified by [daemonKey]. Returns true if
     * the bind request was accepted by the system (the binder may
     * not be live yet — callers receive it through subsequent
     * [binderFor] calls). Returns false if a bind is already in
     * progress or live.
     */
    fun bind(daemonKey: DaemonKey): Boolean {
        if (table.hasBinding(daemonKey)) return false
        val connection = LifecycleConnection(daemonKey)
        val intent = Intent(McpBridgeContract.ACTION_BIND_MCP_DAEMON).apply {
            component = ComponentName(daemonKey.packageName, daemonKey.daemonClassName)
        }
        val accepted = appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        if (accepted) {
            table.registerPending(daemonKey, connection)
        }
        return accepted
    }

    /** Unbind from the daemon. Safe to call when not bound. */
    fun unbind(daemonKey: DaemonKey) {
        val connection = table.removeBinding(daemonKey) ?: return
        runCatching { appContext.unbindService(connection) }
    }

    /** Tear down every live binding — called from `FlorisImeService.onDestroy`. */
    fun shutdown() {
        for (key in table.activeKeys()) {
            unbind(key)
        }
    }

    /**
     * Per-daemon [ServiceConnection] that drives the state machine in
     * [BindingTable] off the system's lifecycle callbacks. Held by
     * reference inside the table so [unbindService] receives the same
     * instance that [bindService] consumed.
     */
    private inner class LifecycleConnection(
        private val daemonKey: DaemonKey,
    ) : ServiceConnection {

        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            table.onConnected(daemonKey, binder)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            table.onDisconnected(daemonKey)
        }

        override fun onBindingDied(name: ComponentName) {
            table.onDisconnected(daemonKey)
            // Android contract: rebind after onBindingDied.
            unbind(daemonKey)
            bind(daemonKey)
        }

        override fun onNullBinding(name: ComponentName) {
            // Daemon's onBind returned null — treat as a hard refusal.
            table.onDisconnected(daemonKey)
        }
    }

    /**
     * In-memory binding state. Split out from the Android-bound
     * connection manager so the state-machine transitions can be
     * exercised in pure-JVM tests by driving the public methods
     * directly.
     */
    class BindingTable {

        private val entries = ConcurrentHashMap<DaemonKey, Entry>()

        fun binderFor(daemonKey: DaemonKey): IBinder? = entries[daemonKey]?.binder

        fun hasBinding(daemonKey: DaemonKey): Boolean = entries.containsKey(daemonKey)

        fun activeKeys(): Set<DaemonKey> = entries.keys.toSet()

        fun registerPending(daemonKey: DaemonKey, connection: ServiceConnection) {
            entries[daemonKey] = Entry(connection = connection, binder = null)
        }

        fun removeBinding(daemonKey: DaemonKey): ServiceConnection? {
            return entries.remove(daemonKey)?.connection
        }

        fun onConnected(daemonKey: DaemonKey, binder: IBinder) {
            val entry = entries[daemonKey] ?: return
            entries[daemonKey] = entry.copy(binder = binder)
        }

        fun onDisconnected(daemonKey: DaemonKey) {
            val entry = entries[daemonKey] ?: return
            entries[daemonKey] = entry.copy(binder = null)
        }

        private data class Entry(
            val connection: ServiceConnection,
            val binder: IBinder?,
        )
    }
}
