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

/**
 * Shared enrollment policy for separately installed packages that must retain
 * SwiftFloris's no-network posture.
 *
 * Requested permissions are screened whether or not Android has granted them:
 * an ungranted permission can be granted after enrollment without another
 * SwiftFloris trust decision.
 */
object NoNetworkPermissionPolicy {
    val DeniedPermissions: Set<String> = setOf(
        "android.permission.INTERNET",
        "android.permission.ACCESS_NETWORK_STATE",
        "android.permission.ACCESS_WIFI_STATE",
        "android.permission.CHANGE_NETWORK_STATE",
        "android.permission.CHANGE_WIFI_STATE",
    )

    fun firstDenied(
        requestedPermissions: Iterable<String>,
        deniedPermissions: Set<String> = DeniedPermissions,
    ): String? = requestedPermissions.firstOrNull { it in deniedPermissions }

    fun firstDenied(
        requestedPermissions: Array<String>?,
        deniedPermissions: Set<String> = DeniedPermissions,
    ): String? = firstDenied(requestedPermissions.orEmpty().asIterable(), deniedPermissions)

    fun rejectionReason(permission: String): String {
        require(permission in DeniedPermissions) { "permission is not in the no-network denylist" }
        return "declares denied network permission $permission"
    }
}
