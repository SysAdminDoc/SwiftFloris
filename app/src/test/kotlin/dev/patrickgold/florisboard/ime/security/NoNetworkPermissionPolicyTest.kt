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

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Every permission literal in this file is hard-coded on purpose.
 *
 * The enrollment tests that already exist iterate
 * [NoNetworkPermissionPolicy.DeniedPermissions] to build their fixtures, so
 * they assert that the policy rejects what the policy already knows about —
 * they cannot fail when something is missing from it. These cases name the
 * permissions independently so an omission is detectable.
 */
class NoNetworkPermissionPolicyTest : FunSpec({

    /** Permissions that carry data off the device or to another app without
     *  needing `INTERNET`. None of these was screened by the old denylist. */
    val exfiltrationCapablePermissions = listOf(
        "android.permission.SEND_SMS",
        "android.permission.BLUETOOTH_CONNECT",
        "android.permission.BLUETOOTH_ADVERTISE",
        "android.permission.BLUETOOTH_SCAN",
        "android.permission.NEARBY_WIFI_DEVICES",
        "android.permission.ACCESS_LOCAL_NETWORK",
        "android.permission.NFC",
        "android.permission.CHANGE_WIFI_MULTICAST_STATE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.MANAGE_EXTERNAL_STORAGE",
        "android.permission.CALL_PHONE",
        "android.permission.QUERY_ALL_PACKAGES",
    )

    test("rejects every exfiltration-capable permission, INTERNET or not") {
        for (permission in exfiltrationCapablePermissions) {
            withClue(permission) {
                NoNetworkPermissionPolicy.isAllowed(permission) shouldBe false
                NoNetworkPermissionPolicy.firstDisallowed(
                    arrayOf(permission),
                ) shouldBe permission
            }
        }
    }

    test("rejects the named network permissions with network-specific wording") {
        NoNetworkPermissionPolicy.rejectionReason("android.permission.INTERNET") shouldBe
            "declares denied network permission android.permission.INTERNET"
    }

    test("rejects other disallowed permissions with generic wording") {
        NoNetworkPermissionPolicy.rejectionReason("android.permission.SEND_SMS") shouldBe
            "declares disallowed permission android.permission.SEND_SMS"
    }

    test("rejects a permission that did not exist when the allowlist was written") {
        // Fail-closed is the whole point: an unknown permission is not a safe one.
        val invented = "android.permission.SOME_FUTURE_TRANSPORT"
        NoNetworkPermissionPolicy.isAllowed(invented) shouldBe false
        NoNetworkPermissionPolicy.firstDisallowed(listOf(invented)) shouldBe invented
    }

    test("allows SwiftFloris's own signature permissions") {
        for (
            permission in listOf(
                "io.github.sysadmindoc.swiftfloris.permission.REGISTER_ADDON",
                "io.github.sysadmindoc.swiftfloris.permission.BIND_MCP",
            )
        ) {
            withClue(permission) {
                NoNetworkPermissionPolicy.isAllowed(permission) shouldBe true
            }
        }
    }

    test("does not allow a package to spoof the signature namespace by prefix") {
        // A lookalike package ID must not inherit the namespace exemption.
        NoNetworkPermissionPolicy.isAllowed(
            "io.github.sysadmindoc.swiftfloris.evil.permission.REGISTER_ADDON",
        ) shouldBe false
    }

    test("every denied network permission is also outside the allowlist") {
        for (permission in NoNetworkPermissionPolicy.DeniedPermissions) {
            withClue(permission) {
                NoNetworkPermissionPolicy.isAllowed(permission) shouldBe false
            }
        }
    }

    test("the allowlist stays small and contains no dangerous transport permission") {
        NoNetworkPermissionPolicy.AllowedPermissions shouldBe setOf(
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.QUERY_ADVANCED_PROTECTION_MODE",
            "android.permission.READ_CALENDAR",
            "android.permission.RECEIVE_BOOT_COMPLETED",
            "android.permission.VIBRATE",
            "android.permission.WAKE_LOCK",
        )
    }

    test("screens permissions positionally, reporting the first offender") {
        NoNetworkPermissionPolicy.firstDisallowed(
            arrayOf(
                "android.permission.VIBRATE",
                "android.permission.SEND_SMS",
                "android.permission.INTERNET",
            ),
        ) shouldBe "android.permission.SEND_SMS"
    }

    test("treats a null or empty request list as clean") {
        NoNetworkPermissionPolicy.firstDisallowed(null) shouldBe null
        NoNetworkPermissionPolicy.firstDisallowed(emptyArray()) shouldBe null
        NoNetworkPermissionPolicy.firstDisallowed(arrayOf("   ")) shouldBe null
    }

    test("rejectionReason refuses to describe an allowed permission") {
        runCatching {
            NoNetworkPermissionPolicy.rejectionReason("android.permission.VIBRATE")
        }.exceptionOrNull() shouldNotBe null
    }
})
