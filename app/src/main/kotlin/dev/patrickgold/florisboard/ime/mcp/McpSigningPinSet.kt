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
 * Persistable signing-certificate pin codec for MCP daemon packages.
 *
 * Storage mirrors addon signing pins: one newline-separated
 * `<packageName>=<SHA-256 fingerprint>` entry per explicitly trusted package.
 */
data class McpSigningPinSet(
    private val pinsByPackageName: Map<String, String>,
) {
    fun asMap(): Map<String, String> = pinsByPackageName.toSortedMap()

    fun fingerprintFor(packageName: String): String? = pinsByPackageName[packageName]

    fun withPinnedCertificate(packageName: String, fingerprint: String): McpSigningPinSet {
        val pkg = packageName.trim()
        val fp = fingerprint.trim().uppercase()
        if (!isValidPackageName(pkg) || !isValidFingerprint(fp)) return this
        return McpSigningPinSet(pinsByPackageName + (pkg to fp))
    }

    fun withoutPackage(packageName: String): McpSigningPinSet =
        McpSigningPinSet(pinsByPackageName - packageName.trim())

    fun encode(): String = encode(pinsByPackageName)

    companion object {
        private val PackagePattern = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
        private val FingerprintPattern = Regex("^([0-9A-F]{2}:){31}[0-9A-F]{2}$")

        fun parse(serialized: String): McpSigningPinSet {
            if (serialized.isBlank()) return McpSigningPinSet(emptyMap())
            val pins = linkedMapOf<String, String>()
            for (line in serialized.lineSequence()) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                val separatorIndex = trimmed.indexOf('=')
                if (separatorIndex <= 0 || separatorIndex >= trimmed.lastIndex) continue
                val packageName = trimmed.substring(0, separatorIndex).trim()
                val fingerprint = trimmed.substring(separatorIndex + 1).trim().uppercase()
                if (!isValidPackageName(packageName) || !isValidFingerprint(fingerprint)) continue
                pins.putIfAbsent(packageName, fingerprint)
            }
            return McpSigningPinSet(pins)
        }

        fun encode(pinsByPackageName: Map<String, String>): String =
            pinsByPackageName
                .filter { (packageName, fingerprint) ->
                    isValidPackageName(packageName) && isValidFingerprint(fingerprint)
                }
                .toSortedMap()
                .entries
                .joinToString(separator = "\n") { (packageName, fingerprint) ->
                    "$packageName=${fingerprint.uppercase()}"
                }

        fun isValidPackageName(packageName: String): Boolean =
            PackagePattern.matches(packageName)

        fun isValidFingerprint(fingerprint: String): Boolean =
            FingerprintPattern.matches(fingerprint.uppercase())
    }
}
