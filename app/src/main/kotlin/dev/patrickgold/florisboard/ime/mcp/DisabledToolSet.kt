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

/**
 * ROADMAP matrix #38 — codec for the `prefs.mcp.disabledTools` preference. Persists as a newline-separated string of
 * `<daemonPackageName>::<toolName>` entries.
 *
 * Sits next to [DisabledDaemonSet] as the finer-grained sibling: where the daemon-level set lets the user pause an
 * entire bound daemon, this tool-level set lets the user disable individual tools advertised by a still-bound daemon.
 * The two are consulted independently by [McpDispatchRouter]:
 *
 * 1. If the daemon's package is in the disabled-daemon set, the call short-circuits before the tool gate ever runs.
 * 2. Otherwise, if the resolved `(daemonPackage, toolName)` is in the disabled-tool set, the call still
 *    short-circuits — but for a per-tool reason that the audit log can surface separately.
 *
 * Per-tool entries are encoded with a `::` separator that cannot occur in a valid Android package name (`.` is
 * required between labels, and `:` is reserved in `name` attribute paths) or in a standard MCP tool name (tool
 * names follow the model-context-protocol spec's `[A-Za-z][A-Za-z0-9_-]*` shape). Using `::` avoids the parsing
 * fragility that would come from a single-colon separator.
 *
 * The codec deliberately mirrors [DisabledDaemonSet]'s API surface so a future Settings UI can reuse the same
 * Compose patterns for both lists. Storage shape matches the JetPref `string` slot for the same reason
 * [DisabledDaemonSet] uses one — the version in use here does not ship a `Set<String>` type.
 */
object DisabledToolSet {

    private const val SEPARATOR = "::"

    /** Parse the persisted string back into a set of `(daemonPackage, toolName)` entries. */
    fun parse(serialized: String): Set<ToolKey> {
        if (serialized.isBlank()) return emptySet()
        return serialized.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull(::parseEntry)
            .toSet()
    }

    /** Encode a set of `(daemonPackage, toolName)` entries into the persisted form. */
    fun encode(entries: Collection<ToolKey>): String {
        return entries.asSequence()
            .map { ToolKey(it.daemonPackage.trim(), it.toolName.trim()) }
            .filter { it.daemonPackage.isNotEmpty() && it.toolName.isNotEmpty() }
            .distinct()
            .sortedWith(compareBy({ it.daemonPackage }, { it.toolName }))
            .joinToString(separator = "\n") { "${it.daemonPackage}$SEPARATOR${it.toolName}" }
    }

    /** Add a `(daemonPackage, toolName)` entry, returning the updated serialised form. */
    fun add(serialized: String, daemonPackage: String, toolName: String): String {
        val key = sanitize(daemonPackage, toolName) ?: return serialized
        val current = parse(serialized).toMutableSet()
        current.add(key)
        return encode(current)
    }

    /** Remove a `(daemonPackage, toolName)` entry, returning the updated serialised form. */
    fun remove(serialized: String, daemonPackage: String, toolName: String): String {
        val key = sanitize(daemonPackage, toolName) ?: return serialized
        val current = parse(serialized).toMutableSet()
        current.remove(key)
        return encode(current)
    }

    /** True when the given `(daemonPackage, toolName)` entry is in the disabled set. */
    fun contains(serialized: String, daemonPackage: String, toolName: String): Boolean {
        val key = sanitize(daemonPackage, toolName) ?: return false
        return parse(serialized).contains(key)
    }

    /** Convenience filter — every tool entry under [daemonPackage]. Useful for the Settings UI. */
    fun toolsFor(serialized: String, daemonPackage: String): Set<String> {
        val pkg = daemonPackage.trim()
        if (pkg.isEmpty()) return emptySet()
        return parse(serialized).asSequence()
            .filter { it.daemonPackage == pkg }
            .map { it.toolName }
            .toSet()
    }

    /** Stable identifier for a `(daemonPackage, toolName)` pair. */
    data class ToolKey(val daemonPackage: String, val toolName: String)

    private fun parseEntry(line: String): ToolKey? {
        val index = line.indexOf(SEPARATOR)
        if (index <= 0 || index >= line.length - SEPARATOR.length) return null
        val pkg = line.substring(0, index).trim()
        val tool = line.substring(index + SEPARATOR.length).trim()
        if (pkg.isEmpty() || tool.isEmpty()) return null
        return ToolKey(pkg, tool)
    }

    private fun sanitize(daemonPackage: String, toolName: String): ToolKey? {
        val pkg = daemonPackage.trim()
        val tool = toolName.trim()
        if (pkg.isEmpty() || tool.isEmpty()) return null
        return ToolKey(pkg, tool)
    }
}
