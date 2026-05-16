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

import java.util.concurrent.atomic.AtomicReference

/**
 * ROADMAP §10.5 L7.1 — registry for active MCP daemons.
 *
 * The IME enumerates installed MCP daemons via PackageManager
 * (`queryIntentServices`) at startup, filtering on
 * [McpBridgeContract.ACTION_BIND_MCP_DAEMON] + the signature-
 * protected [McpBridgeContract.PERMISSION_BIND_MCP].  The discovery
 * pipeline lands in L7.2.  This registry owns the live "who's
 * currently bound and what tools do they expose" snapshot so the
 * NlpManager smart-compose path can ask "is there a calendar tool"
 * without re-driving the PackageManager scan.
 *
 * The contract is intentionally a tiny in-memory key-value:
 *
 *  - One [DaemonEntry] per `<package, daemon-class>` pair.
 *  - Atomic snapshot reads — concurrent IME-thread access never
 *    sees a half-replaced map.
 *  - `setActive(Map)` replaces the entire registry in one step (the
 *    discovery pipeline rebuilds the snapshot on every Settings →
 *    Addons refresh).
 *
 * Mirrors the existing `SmartComposeRegistry` / `InlineTranslatorRegistry`
 * `setActive()` pattern so the addon lifecycle remains uniform across
 * the four heavy-runtime surfaces.
 */
object McpDaemonRegistry {

    private val snapshot = AtomicReference<Map<DaemonKey, DaemonEntry>>(emptyMap())

    /**
     * Replace the current registry with [entries], indexed by
     * `(packageName, daemonClassName)`.  Passing an empty map clears
     * the registry — used when the user disables every MCP daemon
     * from Settings → Addons.
     */
    fun setActive(entries: Map<DaemonKey, DaemonEntry>) {
        snapshot.set(entries.toMap())
    }

    /** Atomic read of the active daemon entries. */
    fun active(): Map<DaemonKey, DaemonEntry> = snapshot.get()

    /** Number of active daemons. */
    fun size(): Int = snapshot.get().size

    /** Find a daemon by [DaemonKey], or null when not registered. */
    fun get(key: DaemonKey): DaemonEntry? = snapshot.get()[key]

    /**
     * Return every tool the registry currently advertises, flattened
     * across all daemons. Stable insertion order — daemon order
     * follows `setActive` insertion, tool order follows
     * [DaemonEntry.tools] order.
     */
    fun listAllTools(): List<ResolvedTool> {
        val out = ArrayList<ResolvedTool>(snapshot.get().size * 4)
        for ((key, entry) in snapshot.get()) {
            for (tool in entry.tools) {
                out.add(ResolvedTool(daemon = key, tool = tool))
            }
        }
        return out
    }

    /** Find a tool by name across every active daemon. Null on miss. */
    fun findTool(toolName: String): ResolvedTool? {
        require(toolName.isNotBlank()) { "toolName must not be blank" }
        for ((key, entry) in snapshot.get()) {
            val tool = entry.tools.firstOrNull { it.name == toolName }
            if (tool != null) return ResolvedTool(daemon = key, tool = tool)
        }
        return null
    }

    /** Test-only — wipe the registry. */
    internal fun resetForTest() {
        snapshot.set(emptyMap())
    }
}

/** Stable identifier for one registered daemon. */
data class DaemonKey(val packageName: String, val daemonClassName: String) {
    init {
        require(packageName.isNotBlank()) { "packageName must not be blank" }
        require(daemonClassName.isNotBlank()) { "daemonClassName must not be blank" }
    }
}

/** One row in the registry — what tools a daemon exposes + version metadata. */
data class DaemonEntry(
    val key: DaemonKey,
    val protocolVersion: Int,
    val tools: List<McpToolDescriptor>,
) {
    init {
        require(protocolVersion >= 1) {
            "protocolVersion must be ≥ 1 (was $protocolVersion)"
        }
        require(protocolVersion <= McpBridgeContract.SUPPORTED_PROTOCOL_VERSION) {
            "protocolVersion $protocolVersion exceeds supported " +
                "${McpBridgeContract.SUPPORTED_PROTOCOL_VERSION}"
        }
    }
}

/** A tool resolved back to its owning daemon — returned by [McpDaemonRegistry.findTool]. */
data class ResolvedTool(val daemon: DaemonKey, val tool: McpToolDescriptor)
