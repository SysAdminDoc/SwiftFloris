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
 * ROADMAP §7 Next-10.3b — persistable signing-certificate pin codec.
 *
 * JetPref in this tree does not expose a `Map<String, String>` primitive, so
 * addon signing pins are stored as one newline-separated string under
 * `prefs.addon.signingCertPins`. Each non-blank line is
 * `<packageName>=<SHA-256 fingerprint>`. Invalid lines are ignored on parse
 * so a corrupted preference disables affected addons instead of crashing the
 * IME.
 */
data class AddonSigningPinSet(
    private val pinsByPackageName: Map<String, String>,
) {
    fun asMap(): Map<String, String> = pinsByPackageName.toSortedMap()

    fun fingerprintFor(packageName: String): String? = pinsByPackageName[packageName]

    fun contains(packageName: String): Boolean = packageName in pinsByPackageName

    fun withoutPackage(packageName: String): AddonSigningPinSet {
        if (!contains(packageName)) return this
        return AddonSigningPinSet(pinsByPackageName - packageName)
    }

    fun withFirstSeen(manifest: AddonManifest): AddonSigningPinSet {
        if (contains(manifest.packageName)) return this
        return AddonSigningPinSet(
            pinsByPackageName + (manifest.packageName to manifest.signingCertSha256),
        )
    }

    fun withFirstSeen(manifests: Iterable<AddonManifest>): AddonSigningPinSet {
        var next = this
        for (manifest in manifests) {
            next = next.withFirstSeen(manifest)
        }
        return next
    }

    fun encode(): String = encode(pinsByPackageName)

    companion object {
        private val PackageNamePattern = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
        private val FingerprintPattern = Regex("^[0-9A-F]{2}(:[0-9A-F]{2}){31}$")

        val Empty = AddonSigningPinSet(emptyMap())

        fun parse(raw: String): AddonSigningPinSet {
            if (raw.isBlank()) return Empty
            val pins = linkedMapOf<String, String>()
            for (line in raw.lineSequence()) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                val separatorIndex = trimmed.indexOf('=')
                if (separatorIndex <= 0 || separatorIndex == trimmed.lastIndex) continue
                val packageName = trimmed.substring(0, separatorIndex).trim()
                val fingerprint = trimmed.substring(separatorIndex + 1).trim()
                if (!isValidPackageName(packageName) || !isValidFingerprint(fingerprint)) continue
                pins.putIfAbsent(packageName, fingerprint)
            }
            return AddonSigningPinSet(pins)
        }

        fun encode(pinsByPackageName: Map<String, String>): String {
            return pinsByPackageName
                .filter { (packageName, fingerprint) ->
                    isValidPackageName(packageName) && isValidFingerprint(fingerprint)
                }
                .toSortedMap()
                .entries
                .joinToString(separator = "\n") { (packageName, fingerprint) ->
                    "$packageName=$fingerprint"
                }
        }

        fun isValidPackageName(packageName: String): Boolean =
            PackageNamePattern.matches(packageName)

        fun isValidFingerprint(fingerprint: String): Boolean =
            FingerprintPattern.matches(fingerprint)
    }
}
