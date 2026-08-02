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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Observable binding state of one discovered MCP daemon.
 *
 * Discovery acceptance used to be reported as "bound", which is a different fact entirely: a
 * daemon can be accepted by the trust policy and still never connect because `bindService`
 * returned false, the binding is pending, the daemon returned a null binder, or the binder died.
 */
enum class McpDaemonConnectionState {
    /** Bind request accepted; the binder has not arrived yet. */
    Pending,

    /** A live binder is available. The only state that may receive tool calls. */
    Connected,

    /** The bind request was refused, threw, or the daemon returned a null binding. */
    Failed,

    /** The binding died and the bounded rebind budget is exhausted. Retry is manual. */
    Dead,

    /** Bridge consent withdrawn or this daemon switched off by the user. */
    Disabled,
    ;

    /** Only a live binder may be dispatched to. */
    val acceptsCalls: Boolean
        get() = this == Connected

    /** Whether an explicit user retry can plausibly change this state. */
    val isRetryable: Boolean
        get() = this == Failed || this == Dead
}

/**
 * Process-local, observable connection state per daemon. Settings collects it so a bind failure,
 * a disconnect, a death, or a recovery is reflected without restarting the IME or the app.
 *
 * Keys are component identities only; nothing here carries selected or surrounding text.
 */
object McpConnectionStateStore {
    private val _states = MutableStateFlow<Map<DaemonKey, McpDaemonConnectionState>>(emptyMap())

    val states: StateFlow<Map<DaemonKey, McpDaemonConnectionState>> = _states.asStateFlow()

    fun active(): Map<DaemonKey, McpDaemonConnectionState> = _states.value

    fun update(daemonKey: DaemonKey, state: McpDaemonConnectionState) {
        _states.update { current ->
            if (current[daemonKey] == state) current else current + (daemonKey to state)
        }
    }

    fun forget(daemonKey: DaemonKey) {
        _states.update { current -> if (daemonKey in current) current - daemonKey else current }
    }

    /** Drops states for daemons that are no longer discovered. */
    fun retainOnly(daemonKeys: Set<DaemonKey>) {
        _states.update { current ->
            val retained = current.filterKeys { it in daemonKeys }
            if (retained.size == current.size) current else retained
        }
    }

    fun reset() {
        _states.value = emptyMap()
    }
}

/**
 * Resolves what Settings should show for a daemon. Pure so every combination of consent,
 * per-daemon switch and live binding state is unit-testable.
 */
object McpDaemonStatePolicy {
    fun resolve(
        daemonKey: DaemonKey,
        bridgeEnabled: Boolean,
        disabledPackages: Set<String>,
        connectionStates: Map<DaemonKey, McpDaemonConnectionState>,
    ): McpDaemonConnectionState {
        if (!bridgeEnabled || daemonKey.packageName in disabledPackages) {
            return McpDaemonConnectionState.Disabled
        }
        // A daemon that was accepted by discovery but has no recorded transition yet is still
        // waiting on its bind callback — never report it as connected.
        return connectionStates[daemonKey] ?: McpDaemonConnectionState.Pending
    }

    /** Number of daemons actually dispatchable right now. */
    fun connectedCount(
        daemonKeys: Collection<DaemonKey>,
        bridgeEnabled: Boolean,
        disabledPackages: Set<String>,
        connectionStates: Map<DaemonKey, McpDaemonConnectionState>,
    ): Int {
        return daemonKeys.count { key ->
            resolve(key, bridgeEnabled, disabledPackages, connectionStates).acceptsCalls
        }
    }
}
