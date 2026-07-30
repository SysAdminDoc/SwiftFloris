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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private const val PIN_SHA_A =
    "AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:" +
        "AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA"
private const val PIN_SHA_B =
    "BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:" +
        "BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB"

class McpSigningPinSetTest : FunSpec({
    test("parse decodes newline-separated daemon fingerprints") {
        val pins = McpSigningPinSet.parse(
            """
            org.swiftfloris.mcp.calendar=$PIN_SHA_A
            org.swiftfloris.mcp.translate=$PIN_SHA_B
            """.trimIndent(),
        )

        pins.asMap() shouldBe mapOf(
            "org.swiftfloris.mcp.calendar" to PIN_SHA_A,
            "org.swiftfloris.mcp.translate" to PIN_SHA_B,
        )
    }

    test("parse ignores malformed package and fingerprint lines") {
        val pins = McpSigningPinSet.parse(
            """
            no-dot=$PIN_SHA_A
            org.swiftfloris.mcp.bad=not-a-fingerprint
            org.swiftfloris.mcp.ok=$PIN_SHA_B
            """.trimIndent(),
        )

        pins.asMap() shouldBe mapOf("org.swiftfloris.mcp.ok" to PIN_SHA_B)
    }

    test("encode sorts pins and filters invalid entries") {
        McpSigningPinSet.encode(
            mapOf(
                "org.swiftfloris.mcp.z" to PIN_SHA_B,
                "bad" to PIN_SHA_A,
                "org.swiftfloris.mcp.a" to PIN_SHA_A,
            ),
        ) shouldBe "org.swiftfloris.mcp.a=$PIN_SHA_A\norg.swiftfloris.mcp.z=$PIN_SHA_B"
    }

    test("withPinnedCertificate updates one package without mutating invalid input") {
        val pins = McpSigningPinSet.parse("org.swiftfloris.mcp.a=$PIN_SHA_A")

        pins.withPinnedCertificate("org.swiftfloris.mcp.a", PIN_SHA_B).asMap() shouldBe
            mapOf("org.swiftfloris.mcp.a" to PIN_SHA_B)
        pins.withPinnedCertificate("bad", PIN_SHA_B).asMap() shouldBe
            mapOf("org.swiftfloris.mcp.a" to PIN_SHA_A)
    }

    test("withoutPackage removes only the requested pin") {
        val pins = McpSigningPinSet.parse(
            """
            org.swiftfloris.mcp.a=$PIN_SHA_A
            org.swiftfloris.mcp.b=$PIN_SHA_B
            """.trimIndent(),
        )

        pins.withoutPackage("org.swiftfloris.mcp.a").asMap() shouldBe
            mapOf("org.swiftfloris.mcp.b" to PIN_SHA_B)
    }

    test("proposed pin persists only after a fresh scan accepts the exact daemon") {
        val key = DaemonKey(
            packageName = "org.swiftfloris.mcp.a",
            daemonClassName = "org.swiftfloris.mcp.a.Daemon",
        )
        val entry = DaemonEntry(
            key = key,
            protocolVersion = 1,
            tools = emptyList(),
        )
        val accepted = McpDiscoverySnapshot(
            accepted = mapOf(key to entry),
            rejected = emptyList(),
        )
        val rejectedAfterPackageUpdate = McpDiscoverySnapshot(
            accepted = emptyMap(),
            rejected = listOf(
                RejectedMcpDaemon(
                    packageName = key.packageName,
                    daemonClassName = key.daemonClassName,
                    signingCertSha256 = null,
                    reason = "declares denied network permission android.permission.INTERNET",
                ),
            ),
        )

        McpSigningPinPersistencePolicy.shouldPersistProposedPin(accepted, key) shouldBe true
        McpSigningPinPersistencePolicy.shouldPersistProposedPin(
            rejectedAfterPackageUpdate,
            key,
        ) shouldBe false
        McpSigningPinPersistencePolicy.shouldPersistProposedPin(
            accepted,
            key.copy(daemonClassName = "org.swiftfloris.mcp.a.OtherDaemon"),
        ) shouldBe false
    }
})
