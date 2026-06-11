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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith

private const val EXAMPLE_FINGERPRINT =
    "AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89:" +
        "AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89"

class AddonManifestTest : FunSpec({

    test("AddonType.fromMetadata maps known values") {
        AddonType.fromMetadata("language-pack") shouldBe AddonType.LANGUAGE_PACK
        AddonType.fromMetadata("theme-pack") shouldBe AddonType.THEME_PACK
        AddonType.fromMetadata("dictionary-pack") shouldBe AddonType.DICTIONARY_PACK
        AddonType.fromMetadata("layout-pack") shouldBe AddonType.LAYOUT_PACK
        AddonType.fromMetadata("popup-mapping-pack") shouldBe AddonType.POPUP_MAPPING_PACK
    }

    test("AddonType.fromMetadata returns null for unknown values") {
        // ROADMAP §7 Next-10.1 — forward-compat: an addon shipping a newer
        // addon-type than this IME knows must not crash; it just fails to
        // enrol. Unknown values returning null is how the enumerator
        // signals "skip silently".
        AddonType.fromMetadata("future-type-from-a-newer-addon") shouldBe null
        AddonType.fromMetadata(null) shouldBe null
        AddonType.fromMetadata("") shouldBe null
    }

    test("AddonManifest stableId carries the addon: namespace prefix") {
        val manifest = AddonManifest(
            packageName = "org.swiftfloris.langpack.polish",
            type = AddonType.LANGUAGE_PACK,
            version = 1L,
            displayName = "Polish",
            descriptorResourceId = 12345,
            licenseSpdxId = "Apache-2.0",
            signingCertSha256 = EXAMPLE_FINGERPRINT,
            bundleSizeBytes = 4096L,
        )
        manifest.stableId shouldStartWith AddonContract.ADDON_ID_PREFIX
        manifest.stableId shouldBe "addon:org.swiftfloris.langpack.polish"
    }

    test("AddonManifest rejects negative version") {
        shouldThrow<IllegalArgumentException> {
            AddonManifest(
                packageName = "org.swiftfloris.bad",
                type = AddonType.THEME_PACK,
                version = -1L,
                displayName = "Bad",
                descriptorResourceId = 1,
                licenseSpdxId = "Apache-2.0",
                signingCertSha256 = EXAMPLE_FINGERPRINT,
                bundleSizeBytes = 4096L,
            )
        }
    }

    test("AddonManifest rejects oversized bundle") {
        shouldThrow<IllegalArgumentException> {
            AddonManifest(
                packageName = "org.swiftfloris.huge",
                type = AddonType.DICTIONARY_PACK,
                version = 1L,
                displayName = "Huge",
                descriptorResourceId = 1,
                licenseSpdxId = "Apache-2.0",
                signingCertSha256 = EXAMPLE_FINGERPRINT,
                bundleSizeBytes = AddonContract.ADDON_MAX_BUNDLE_BYTES + 1,
            )
        }
    }

    test("AddonManifest rejects malformed signing fingerprint") {
        // The fingerprint must match `apksigner --print-certs` shape exactly:
        // 32 colon-separated pairs of uppercase hex. Anything else is a sign
        // the enumerator failed to extract a real signature.
        shouldThrow<IllegalArgumentException> {
            AddonManifest(
                packageName = "org.swiftfloris.malformed",
                type = AddonType.LAYOUT_PACK,
                version = 1L,
                displayName = "Malformed",
                descriptorResourceId = 1,
                licenseSpdxId = "Apache-2.0",
                signingCertSha256 = "not a real fingerprint",
                bundleSizeBytes = 4096L,
            )
        }
    }

    test("ADDON_SIGNATURE_PERMISSION namespace matches the manifest declaration") {
        AddonContract.ADDON_SIGNATURE_PERMISSION shouldBe
            "io.github.sysadmindoc.swiftfloris.permission.REGISTER_ADDON"
    }

    test("REGISTER_ADDON action namespace matches the manifest queries block") {
        AddonContract.Action.REGISTER shouldBe
            "io.github.sysadmindoc.swiftfloris.action.REGISTER_ADDON"
    }

    test("AddonEnumerator default banned permission set blocks all network surfaces") {
        // ROADMAP §1 — addons must never become a back-door network channel.
        // Reject the entire suite — not just INTERNET, because state-of-network
        // probes leak typed-text-rate metadata that's already enough to fingerprint
        // a session.
        AddonEnumerator.DefaultNetworkPermissions shouldBe setOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.ACCESS_WIFI_STATE",
            "android.permission.CHANGE_NETWORK_STATE",
            "android.permission.CHANGE_WIFI_STATE",
        )
    }

    test("AddonEnumerator permission screening treats a null requestedPermissions array as safe") {
        AddonEnumerator.firstRejectedNetworkPermission(
            requestedPermissions = null,
            networkPermissionsRejected = AddonEnumerator.DefaultNetworkPermissions,
        ) shouldBe null
        AddonEnumerator.firstRejectedNetworkPermission(
            requestedPermissions = arrayOf("android.permission.INTERNET"),
            networkPermissionsRejected = AddonEnumerator.DefaultNetworkPermissions,
        ) shouldBe "android.permission.INTERNET"
    }
})
