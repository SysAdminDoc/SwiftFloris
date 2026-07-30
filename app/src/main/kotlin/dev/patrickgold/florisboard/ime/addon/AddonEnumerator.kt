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

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import dev.patrickgold.florisboard.app.settings.about.SigningFingerprint
import dev.patrickgold.florisboard.ime.security.NoNetworkPermissionPolicy
import dev.patrickgold.florisboard.lib.devtools.flogError
import dev.patrickgold.florisboard.lib.devtools.flogInfo
import java.io.File

/**
 * ROADMAP §7 Next-10.2 — enumerator that discovers installed addon packages
 * at IME startup, validates them against the privacy invariants declared in
 * [AddonContract], and returns the subset safe to enrol.
 *
 * Inputs: a `Context` whose `packageManager` can query the install base.
 * Outputs: a list of [AddonManifest] entries, one per accepted addon. Rejected
 * addons are logged via `flogInfo` / `flogError` with the rejection reason so
 * a future Settings → Addons → "Why was X rejected?" surface can surface the
 * audit trail without re-running the scan.
 *
 * Hot-path constraint: this is intentionally a *snapshot* scan — typical run
 * time is single-digit milliseconds across <500 installed packages on a
 * Pixel-class device. Callers should run it once on cold IME boot (or on
 * explicit user-initiated refresh from Settings), not per-keystroke. The
 * companion class [AddonRegistry] holds the live state for the running IME.
 */
class AddonEnumerator(
    private val context: Context,
    private val networkPermissionsRejected: Set<String> = NoNetworkPermissionPolicy.DeniedPermissions,
) {

    companion object {
        /** Permissions that are an automatic reject for an addon, because they
         *  open a network exfil channel that bypasses the keyboard's
         *  no-INTERNET posture (ROADMAP §1, STD-NO-INTERNET). An addon that
         *  needs *any* of these is fundamentally incompatible with the
         *  privacy stance, regardless of intent. */
        val DefaultNetworkPermissions: Set<String> = NoNetworkPermissionPolicy.DeniedPermissions

        /** Convenience: the IME's own package name, used to skip self-scan. */
        private const val SCAN_FLAGS_BASE =
            PackageManager.GET_META_DATA or PackageManager.GET_PERMISSIONS

        internal fun firstRejectedNetworkPermission(
            requestedPermissions: Array<String>?,
            networkPermissionsRejected: Set<String>,
        ): String? {
            return NoNetworkPermissionPolicy.firstDenied(
                requestedPermissions,
                networkPermissionsRejected,
            )
        }

        internal fun packageBundleSizeBytes(app: ApplicationInfo): Long? {
            val paths = buildList {
                app.sourceDir?.takeIf { it.isNotBlank() }?.let(::add)
                app.splitSourceDirs.orEmpty()
                    .filter { it.isNotBlank() }
                    .forEach(::add)
            }
            if (paths.isEmpty()) return null

            var total = 0L
            for (path in paths) {
                val size = try {
                    val file = File(path)
                    if (!file.isFile) return null
                    file.length()
                } catch (_: SecurityException) {
                    return null
                }
                if (size < 0 || total > Long.MAX_VALUE - size) return null
                total += size
            }
            return total
        }

        internal fun bundleSizeRejectionReason(bundleSizeBytes: Long?): String? {
            if (bundleSizeBytes == null) return "cannot determine addon bundle size"
            return if (bundleSizeBytes > AddonContract.ADDON_MAX_BUNDLE_BYTES) {
                "bundle size $bundleSizeBytes exceeds ${AddonContract.ADDON_MAX_BUNDLE_BYTES} bytes"
            } else {
                null
            }
        }

        internal fun unknownAddonTypeRejectionReason(typeRaw: String): String {
            val normalized = typeRaw.lowercase()
            return if ("runtime" in normalized || "engine" in normalized) {
                "unsupported runtime addon capability: $typeRaw"
            } else {
                "unknown addon-type=$typeRaw"
            }
        }
    }

    /**
     * Snapshot all currently-installed addons. Order is unspecified — callers
     * that want a deterministic order should sort by [AddonManifest.stableId].
     */
    fun snapshot(): List<AddonManifest> = scan().accepted

    fun scan(): Snapshot {
        val pm = context.packageManager
        val self = context.packageName
        val allPackages: List<PackageInfo> = try {
            queryInstalledPackages(pm)
        } catch (t: Throwable) {
            flogError { "AddonEnumerator: packageManager scan threw: ${t.message}" }
            return Snapshot(emptyList(), emptyList())
        }
        val accepted = ArrayList<AddonManifest>(8)
        val rejected = ArrayList<RejectedPackage>(4)
        for (info in allPackages) {
            if (info.packageName == self) continue
            val verdict = evaluate(info, pm)
            when (verdict) {
                is AddonVerdict.Accepted -> accepted += verdict.manifest
                is AddonVerdict.Rejected -> {
                    rejected += RejectedPackage(
                        packageName = info.packageName,
                        displayName = displayNameFor(info.applicationInfo, info, pm),
                        reason = verdict.reason,
                    )
                    flogInfo {
                        "AddonEnumerator: rejected ${info.packageName} (${verdict.reason})"
                    }
                }
                AddonVerdict.NotAnAddon -> { /* common case — ignore */ }
            }
        }
        flogInfo { "AddonEnumerator: scan complete, ${accepted.size} accepted, ${rejected.size} rejected" }
        return Snapshot(accepted = accepted, rejected = rejected)
    }

    @Suppress("DEPRECATION")
    internal fun queryInstalledPackages(pm: PackageManager): List<PackageInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(SCAN_FLAGS_BASE.toLong()),
            )
        } else {
            pm.getInstalledPackages(SCAN_FLAGS_BASE)
        }
    }

    /**
     * Evaluate a single installed package and return whether it qualifies as
     * an enrolled addon. The decision tree:
     *
     *  1. Skip if missing addon-type metadata — common case, not an error.
     *  2. Reject if addon-type metadata is present but unrecognised
     *     (forward-compat: silent so old IME with new addon doesn't crash).
     *  3. Reject if the addon requests any denied network permission.
     *  4. Reject if the addon manifest is missing the descriptor / version /
     *     license metadata keys.
     *  5. Reject if the signing-cert fingerprint can't be read (Android 8
     *     dropped legacy v1-only signers, so any current addon should expose
     *     this trivially).
     *  6. Accept and return the parsed [AddonManifest].
     */
    internal fun evaluate(info: PackageInfo, pm: PackageManager): AddonVerdict {
        val app = info.applicationInfo ?: return AddonVerdict.NotAnAddon
        val meta = app.metaData ?: return AddonVerdict.NotAnAddon
        val typeRaw = meta.getString(AddonContract.MetadataKey.ADDON_TYPE)
            ?: return AddonVerdict.NotAnAddon
        val type = AddonType.fromMetadata(typeRaw)
            ?: return AddonVerdict.Rejected(unknownAddonTypeRejectionReason(typeRaw))
        val banned = firstRejectedNetworkPermission(info.requestedPermissions)
        if (banned != null) {
            return AddonVerdict.Rejected(NoNetworkPermissionPolicy.rejectionReason(banned))
        }
        val descriptorRes = meta.getInt(AddonContract.MetadataKey.ADDON_DESCRIPTOR, 0)
        if (descriptorRes == 0) {
            return AddonVerdict.Rejected("missing descriptor resource id")
        }
        val version = meta.getInt(AddonContract.MetadataKey.ADDON_VERSION, -1)
        if (version < 0) {
            return AddonVerdict.Rejected("missing or negative addon-version")
        }
        val license = meta.getString(AddonContract.MetadataKey.ADDON_LICENSE)
        if (license.isNullOrBlank()) {
            return AddonVerdict.Rejected("missing addon-license SPDX id")
        }
        val signingCert = readSigningFingerprint(info.packageName)
            ?: return AddonVerdict.Rejected("cannot read signing certificate")
        val measuredBundleSize = packageBundleSizeBytes(app)
        bundleSizeRejectionReason(measuredBundleSize)?.let { reason ->
            return AddonVerdict.Rejected(reason)
        }
        val bundleSize = measuredBundleSize ?: return AddonVerdict.Rejected("cannot determine addon bundle size")
        val displayName = displayNameFor(app, info, pm) ?: info.packageName
        return try {
            AddonVerdict.Accepted(
                AddonManifest(
                    packageName = info.packageName,
                    type = type,
                    version = version.toLong(),
                    displayName = displayName,
                    descriptorResourceId = descriptorRes,
                    licenseSpdxId = license,
                    signingCertSha256 = signingCert,
                    bundleSizeBytes = bundleSize,
                ),
            )
        } catch (e: IllegalArgumentException) {
            AddonVerdict.Rejected(e.message ?: "invalid manifest")
        }
    }

    private fun readSigningFingerprint(packageName: String): String? {
        return try {
            // Reuse the IME's own SigningFingerprint helper, which gracefully
            // falls back from API 28+ GET_SIGNING_CERTIFICATES to API 26/27
            // GET_SIGNATURES. The helper is package-private to the IME's
            // security package, so we route through its public read of the
            // *caller* identity by querying for a different package.
            SigningFingerprint.sha256OfPackage(context, packageName)
        } catch (_: Throwable) {
            null
        }
    }

    private fun displayNameFor(app: ApplicationInfo?, info: PackageInfo, pm: PackageManager): String? {
        return try {
            app?.loadLabel(pm)?.toString()
        } catch (_: Throwable) {
            null
        } ?: info.packageName
    }

    internal fun firstRejectedNetworkPermission(requestedPermissions: Array<String>?): String? {
        return AddonEnumerator.firstRejectedNetworkPermission(requestedPermissions, networkPermissionsRejected)
    }

    data class Snapshot(
        val accepted: List<AddonManifest>,
        val rejected: List<RejectedPackage>,
    )

    data class RejectedPackage(
        val packageName: String,
        val displayName: String?,
        val reason: String,
    )
}

/** Result of [AddonEnumerator.evaluate] for a single installed package. */
internal sealed interface AddonVerdict {
    data class Accepted(val manifest: AddonManifest) : AddonVerdict
    data class Rejected(val reason: String) : AddonVerdict
    object NotAnAddon : AddonVerdict
}
