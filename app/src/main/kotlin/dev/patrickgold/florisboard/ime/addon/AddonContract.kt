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

import dev.patrickgold.florisboard.AppPackageContract

/**
 * ROADMAP §7 Next-10.1 — addon-package manifest schema and intent-action
 * constants for SwiftFloris's plugin / addon ecosystem.
 *
 * Strategic intent: long-tail languages, themes, and dictionaries can ship as
 * separate APKs (Play / F-Droid / Aurora / Obtainium) that self-register with
 * the running SwiftFloris IME via a broadcast / package-manager metadata
 * handshake — the fcitx5 / AnySoftKeyboard pattern, scaled. Base APK stays
 * small; long-tail surface area lives in user-installed companion packages.
 *
 * Contract (the *spec* lives here; the *enumerator* — Next-10.2 — discovers
 * matching packages and feeds the registry):
 *
 *  1. An addon APK declares a single `<meta-data android:name>` entry on its
 *     `<application>` tag, equal to one of [AddonContract.MetadataKey]. The
 *     value is a string resource id pointing to an XML descriptor file that
 *     conforms to the schema below.
 *  2. An addon APK **MUST** declare a broadcast `<receiver>` whose
 *     `<intent-filter>` matches one of the [AddonContract.Action] register
 *     actions (REGISTER_ADDON or one of the type-specific aliases).
 *
 *     This is a *visibility* requirement, not a feature requirement —
 *     Android 11+ enforces package-visibility restrictions; the IME's
 *     `<queries>` block in `AndroidManifest.xml` declares intent-filter
 *     queries for these action names, and an addon's package is invisible
 *     to `PackageManager.getInstalledPackages()` unless at least one of its
 *     manifest components carries a matching intent-filter. The receiver
 *     can be a no-op (it does not need to handle the broadcast); the
 *     intent-filter alone satisfies the visibility query.
 *
 *     The receiver *should* also respond to [AddonContract.Action.REGISTER]
 *     so it can self-announce changes (version bumps, new language assets)
 *     without forcing the IME to poll. Receivers must be exported with a
 *     signature-permission protection so only signature-matching packages
 *     can request a register-callback.
 *  3. The addon's signing certificate is captured at first-enrolment and
 *     pinned. Subsequent installs that change the signing cert are quietly
 *     ignored — supply-chain protection equivalent to the user-dictionary
 *     SQLCipher passphrase pin (N7.4).
 *
 * **Privacy invariants every addon must satisfy.** The enumerator (Next-10.2)
 * rejects any addon whose own manifest declares `INTERNET` / `ACCESS_NETWORK_*`
 * permissions, so an addon cannot become a back-door network channel that
 * bypasses the keyboard's no-`INTERNET` posture. This is a hard reject, not a
 * warning. The check is verified at addon enrolment AND at every reboot, so a
 * malicious addon update cannot escalate after the user opts in.
 */
object AddonContract {

    /** Stable, externally-public intent actions a third-party addon can fire. */
    object Action {
        /**
         * Broadcast a third-party addon can send when it becomes installed,
         * updated, or wants to advertise a new asset bundle. The IME's
         * [dev.patrickgold.florisboard.ime.addon.AddonEnumerator] (Next-10.2)
         * listens for this action with the addon's own signature-permission
         * gate; the broadcast is best-effort — addons must also rely on the
         * IME's startup-time enumeration for the initial enrolment.
         */
        const val REGISTER = AppPackageContract.ACTION_PREFIX + "REGISTER_ADDON"

        /** Convenience aliases scoped per addon-type. Implementations may dispatch
         *  on the addon-type metadata key declared in the addon's manifest. */
        const val REGISTER_LANGUAGE_PACK = AppPackageContract.ACTION_PREFIX + "REGISTER_LANGUAGE_PACK"
        const val REGISTER_THEME_PACK = AppPackageContract.ACTION_PREFIX + "REGISTER_THEME_PACK"
        const val REGISTER_DICTIONARY_PACK = AppPackageContract.ACTION_PREFIX + "REGISTER_DICTIONARY_PACK"
        const val REGISTER_LAYOUT_PACK = AppPackageContract.ACTION_PREFIX + "REGISTER_LAYOUT_PACK"
        const val REGISTER_POPUP_MAPPING_PACK = AppPackageContract.ACTION_PREFIX + "REGISTER_POPUP_MAPPING_PACK"

        /** Reverse direction: the IME pushes this broadcast to a single addon
         *  package to ask it to surface its current state (e.g. after an
         *  in-IME configuration screen prompts "rescan installed addons"). */
        const val INVALIDATE = AppPackageContract.ACTION_PREFIX + "INVALIDATE_ADDON"
    }

    /** `<meta-data android:name="…">` keys the IME scans for on every
     *  installed `<application>` element. The first one present wins; an
     *  addon with multiple addon-type meta-data entries is rejected because
     *  the enrolment is intentionally per-type. */
    object MetadataKey {
        const val ADDON_DESCRIPTOR = AppPackageContract.ADDON_METADATA_PREFIX + "descriptor"
        const val ADDON_TYPE = AppPackageContract.ADDON_METADATA_PREFIX + "type"
        const val ADDON_VERSION = AppPackageContract.ADDON_METADATA_PREFIX + "version"
        const val ADDON_LICENSE = AppPackageContract.ADDON_METADATA_PREFIX + "license"
    }

    /** Signature-permission name every addon-aware broadcast receiver must
     *  request, so only packages signed with a matching certificate can fire
     *  REGISTER. The base IME declares this permission with
     *  `android:protectionLevel="signature"`; addons must be co-signed (Play /
     *  F-Droid) by the same key to register, OR the user must explicitly opt
     *  in to an unsigned addon via Settings → Addons. */
    const val ADDON_SIGNATURE_PERMISSION = AppPackageContract.PERMISSION_PREFIX + "REGISTER_ADDON"

    /** The directory inside an enrolled addon's assets where the IME looks
     *  for the addon's resource bundle. Convention; not enforced at the
     *  package-manager level. */
    const val ADDON_ASSETS_ROOT = "addon"

    /** Maximum bundle size accepted per addon, in bytes. Prevents a malicious
     *  addon from trying to ship a 500 MB neural model that would OOM the
     *  IME process on enrolment. Large addons should split into multiple
     *  packages. */
    const val ADDON_MAX_BUNDLE_BYTES: Long = 64L * 1024 * 1024  // 64 MB

    /** Reserved namespace prefix for addon-supplied keyboard / language /
     *  theme identifiers. Anything an addon registers must be prefixed with
     *  `addon:` so it can never collide with a built-in id. */
    const val ADDON_ID_PREFIX = "addon:"
}

/** The set of first-class addon types the IME understands. The enrolment
 *  surface (Next-10.2) dispatches on this value to validate the addon's
 *  declared assets against the expected schema for that type. */
enum class AddonType(val metadataValue: String, val intentAction: String) {
    LANGUAGE_PACK("language-pack", AddonContract.Action.REGISTER_LANGUAGE_PACK),
    THEME_PACK("theme-pack", AddonContract.Action.REGISTER_THEME_PACK),
    DICTIONARY_PACK("dictionary-pack", AddonContract.Action.REGISTER_DICTIONARY_PACK),
    LAYOUT_PACK("layout-pack", AddonContract.Action.REGISTER_LAYOUT_PACK),
    POPUP_MAPPING_PACK("popup-mapping-pack", AddonContract.Action.REGISTER_POPUP_MAPPING_PACK),
    ;

    companion object {
        /** Parse a metadata string back into a typed [AddonType], or null when
         *  the value is unrecognised. Unknown values are intentionally not
         *  fatal so future addons declaring a newer addon-type don't crash
         *  older IME installs — they just don't enrol. */
        fun fromMetadata(value: String?): AddonType? =
            value?.let { v -> entries.firstOrNull { it.metadataValue == v } }
    }
}

/**
 * Parsed addon manifest. The [packageName] uniquely identifies the addon
 * inside Android's package manager (and serves as the stable cross-restart
 * key the enrolment registry uses). [signingCertSha256] is captured at first
 * enrolment and pinned on every subsequent enumeration so a malicious actor
 * who hijacks the package name with a different signing certificate gets
 * quietly ignored.
 */
data class AddonManifest(
    val packageName: String,
    val type: AddonType,
    val version: Long,
    val displayName: String,
    val descriptorResourceId: Int,
    val licenseSpdxId: String,
    val signingCertSha256: String,
    val bundleSizeBytes: Long,
) {
    init {
        require(version >= 0) { "Addon version must be non-negative; was $version" }
        require(bundleSizeBytes >= 0) {
            "Addon bundle size must be non-negative; was $bundleSizeBytes"
        }
        require(bundleSizeBytes <= AddonContract.ADDON_MAX_BUNDLE_BYTES) {
            "Addon bundle exceeds ${AddonContract.ADDON_MAX_BUNDLE_BYTES} bytes: $bundleSizeBytes"
        }
        require(signingCertSha256.matches(Regex("^[0-9A-F:]{95}$"))) {
            "Signing certificate fingerprint must be 32 SHA-256 bytes formatted as AB:CD:..."
        }
    }

    /** Stable, namespaced id used by the IME's internal registries to refer
     *  to assets supplied by this addon. */
    val stableId: String get() = AddonContract.ADDON_ID_PREFIX + packageName
}
