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
 * ROADMAP §10.5 L7.6b — codec for the `prefs.mcp.disabledDaemonPackages`
 * preference. Persists as a newline-separated string of package names.
 *
 * The pref holds a `String` because the JetPref version in use here
 * doesn't ship a `Set<String>` type. The codec round-trips a
 * `Set<String>` view onto that string so callers (the Settings UI +
 * `McpDispatchRouter`'s `isDaemonDisabled` check) can think in
 * set-of-packages terms.
 */
object DisabledDaemonSet {

    /** Parse the persisted string back into a set of package names. */
    fun parse(serialized: String): Set<String> {
        if (serialized.isBlank()) return emptySet()
        return serialized.split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    /** Encode a set of package names into the persisted form. */
    fun encode(packages: Collection<String>): String {
        return packages.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()  // Deterministic ordering so the prefs file diff is stable.
            .joinToString(separator = "\n")
    }

    /** Add [packageName] to the set, returning the updated serialised form. */
    fun add(serialized: String, packageName: String): String {
        if (packageName.isBlank()) return serialized
        val current = parse(serialized).toMutableSet()
        current.add(packageName.trim())
        return encode(current)
    }

    /** Remove [packageName] from the set, returning the updated serialised form. */
    fun remove(serialized: String, packageName: String): String {
        if (packageName.isBlank()) return serialized
        val current = parse(serialized).toMutableSet()
        current.remove(packageName.trim())
        return encode(current)
    }

    /** True when [packageName] is present in the serialised set. */
    fun contains(serialized: String, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return parse(serialized).contains(packageName.trim())
    }
}
