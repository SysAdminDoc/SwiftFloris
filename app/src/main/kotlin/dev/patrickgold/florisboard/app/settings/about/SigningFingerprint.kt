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

package dev.patrickgold.florisboard.app.settings.about

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.content.pm.SigningInfo
import android.os.Build
import dev.patrickgold.florisboard.lib.devtools.flogError
import java.security.MessageDigest

/**
 * ROADMAP §6 N7.5 — Computes the SHA-256 fingerprint of the running APK's signing
 * certificate so users can compare it against the value pinned in README and detect
 * supply-chain swaps. Pure read of installed package metadata — no network.
 *
 * Format: 32 hex byte pairs separated by colons (`AB:CD:…`), matching the convention
 * used by `apksigner verify --print-certs` and the F-Droid metadata pages.
 */
object SigningFingerprint {

    private const val HEX_DIGITS = "0123456789ABCDEF"

    /**
     * Returns the colon-formatted SHA-256 fingerprint of the package's primary signer,
     * or null if the platform refuses to surface signing info on this build (extremely
     * rare; not expected on Android 8.0+ where PackageInfo.signingInfo is supported).
     */
    fun sha256(context: Context): String? = sha256OfPackage(context, context.packageName)

    /**
     * ROADMAP §7 Next-10.2 — same SHA-256 routine as [sha256] but for an
     * arbitrary installed package, used by the addon enumerator to pin each
     * enrolled addon's signing certificate on first contact. Returns null if
     * the package isn't installed or signing info is unavailable.
     */
    fun sha256OfPackage(context: Context, packageName: String): String? {
        val signatures = readSignatures(context, packageName) ?: return null
        if (signatures.isEmpty()) return null
        val primary = signatures.first().toByteArray()
        return try {
            val digest = MessageDigest.getInstance("SHA-256").digest(primary)
            buildString(digest.size * 3) {
                digest.forEachIndexed { index, byte ->
                    val unsigned = byte.toInt() and 0xff
                    if (index > 0) append(':')
                    append(HEX_DIGITS[unsigned ushr 4])
                    append(HEX_DIGITS[unsigned and 0x0f])
                }
            }
        } catch (e: Throwable) {
            flogError { "Failed to compute APK signing fingerprint: $e" }
            null
        }
    }

    private fun readSignatures(context: Context, packageName: String): List<Signature>? {
        val pm = context.packageManager
        return try {
            // PackageManager.GET_SIGNING_CERTIFICATES is Android 9+ (API 28). minSdk = 26
            // includes Android 8.0/8.1 where SIGNING_CERTIFICATES is unavailable; on those
            // we fall back to the deprecated GET_SIGNATURES API.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                @Suppress("DEPRECATION")
                val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val signingInfo: SigningInfo = info.signingInfo ?: return null
                if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners.toList()
                } else {
                    signingInfo.signingCertificateHistory.toList()
                }
            } else {
                @Suppress("DEPRECATION")
                val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                info.signatures?.toList()
            }
        } catch (e: Throwable) {
            flogError { "Failed to read signing info for $packageName: $e" }
            null
        }
    }
}
