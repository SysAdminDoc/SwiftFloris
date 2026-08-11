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

package dev.patrickgold.florisboard.ime.security

import dev.patrickgold.florisboard.AppPackageContract

/**
 * Shared enrollment policy for separately installed packages that must retain
 * SwiftFloris's no-network posture.
 *
 * Requested permissions are screened whether or not Android has granted them:
 * an ungranted permission can be granted after enrollment without another
 * SwiftFloris trust decision.
 *
 * ## Why this is an allowlist
 *
 * This screening used to be a five-entry network denylist. A denylist certifies
 * everything it does not name, and `INTERNET` is not the only way a package can
 * move the selected text the MCP bridge hands it off the device: `SEND_SMS`,
 * `BLUETOOTH_CONNECT`, `NEARBY_WIFI_DEVICES` (Wi-Fi Direct / Aware), `NFC`,
 * `MANAGE_EXTERNAL_STORAGE` and Android 17's `ACCESS_LOCAL_NETWORK` all carry
 * data across a process or device boundary without requiring `INTERNET`. Any
 * future platform release can add another one.
 *
 * So enrollment now accepts a package only when *every* permission it requests
 * is either in [AllowedPermissions] or inside SwiftFloris's own signature
 * permission namespace ([AppPackageContract.PERMISSION_PREFIX]) — which is how
 * an addon holds `REGISTER_ADDON` and an MCP daemon holds `BIND_MCP`. A
 * permission nobody has thought about yet fails closed.
 *
 * [DeniedPermissions] is retained because the network permissions are the
 * documented, user-visible case and deserve their own rejection wording in
 * `docs/PRIVACY_AND_AI.md` and the Settings → Addons rejection list. It is a
 * messaging aid, not the gate.
 */
object NoNetworkPermissionPolicy {
    /**
     * The network permissions the privacy posture calls out by name. Every one
     * of these is also absent from [AllowedPermissions]; this set only selects
     * the more specific rejection wording.
     */
    val DeniedPermissions: Set<String> = setOf(
        "android.permission.INTERNET",
        "android.permission.ACCESS_NETWORK_STATE",
        "android.permission.ACCESS_WIFI_STATE",
        "android.permission.CHANGE_NETWORK_STATE",
        "android.permission.CHANGE_WIFI_STATE",
    )

    /**
     * Every platform permission an enrolled addon or MCP daemon may request.
     *
     * The bar for adding one: it must not be able to carry data off the device
     * or to another app, and a plausible local addon must actually need it.
     * `READ_CALENDAR` is here because the documented calendar MCP daemon reads
     * the local agenda; it widens what the *daemon* can see, never what it can
     * send.
     */
    val AllowedPermissions: Set<String> = setOf(
        "android.permission.FOREGROUND_SERVICE",
        "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
        "android.permission.POST_NOTIFICATIONS",
        "android.permission.READ_CALENDAR",
        "android.permission.RECEIVE_BOOT_COMPLETED",
        "android.permission.VIBRATE",
        "android.permission.WAKE_LOCK",
    )

    /**
     * True when [permission] may appear in an enrolled package's manifest —
     * either explicitly allowlisted, or one of SwiftFloris's own
     * signature-protected permissions.
     */
    fun isAllowed(permission: String): Boolean {
        val normalized = permission.trim()
        if (normalized.isEmpty()) return false
        return normalized in AllowedPermissions ||
            normalized.startsWith(AppPackageContract.PERMISSION_PREFIX)
    }

    fun firstDisallowed(
        requestedPermissions: Iterable<String>,
        allowedPermissions: Set<String> = AllowedPermissions,
    ): String? = requestedPermissions.firstOrNull { permission ->
        val normalized = permission.trim()
        normalized.isNotEmpty() &&
            normalized !in allowedPermissions &&
            !normalized.startsWith(AppPackageContract.PERMISSION_PREFIX)
    }

    fun firstDisallowed(
        requestedPermissions: Array<String>?,
        allowedPermissions: Set<String> = AllowedPermissions,
    ): String? = firstDisallowed(requestedPermissions.orEmpty().asIterable(), allowedPermissions)

    fun rejectionReason(permission: String): String {
        require(!isAllowed(permission)) { "permission is allowed for enrollment" }
        return if (permission in DeniedPermissions) {
            "declares denied network permission $permission"
        } else {
            "declares disallowed permission $permission"
        }
    }
}
