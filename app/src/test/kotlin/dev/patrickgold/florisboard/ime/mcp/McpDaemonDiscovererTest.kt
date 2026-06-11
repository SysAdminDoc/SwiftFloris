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

private const val MCP_SHA_A =
    "AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:" +
        "AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA"
private const val MCP_SHA_B =
    "BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:" +
        "BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB"
private const val MCP_SHA_C =
    "CC:CC:CC:CC:CC:CC:CC:CC:CC:CC:CC:CC:CC:CC:CC:CC:" +
        "CC:CC:CC:CC:CC:CC:CC:CC:CC:CC:CC:CC:CC:CC:CC:CC"

class McpDaemonDiscovererTest : FunSpec({

    val validJson = """
        {"tools":[{"name":"calendar.next","description":"Next event","parameterSchema":"{\"type\":\"object\"}"}]}
    """.trimIndent()
    val trustedPolicy = McpDaemonTrustPolicy(trustedRootSigningCertSha256 = MCP_SHA_A)

    fun candidate(
        packageName: String = "com.example.mcp",
        daemonClassName: String = "com.example.mcp.Daemon",
        protocolVersion: Int = 1,
        hasBindPermission: Boolean = true,
        signingCertSha256: String? = MCP_SHA_A,
        toolCatalogJson: String = validJson,
    ) = DiscoveryCandidate(
        packageName = packageName,
        daemonClassName = daemonClassName,
        protocolVersion = protocolVersion,
        hasBindPermission = hasBindPermission,
        signingCertSha256 = signingCertSha256,
        toolCatalogJson = toolCatalogJson,
    )

    test("happy path discovers a single daemon") {
        val cand = candidate()
        val out = McpDaemonDiscoverer.discover(listOf(cand), trustedPolicy)
        out.size shouldBe 1
        out.values.single().tools.single().name shouldBe "calendar.next"
    }

    test("rejects candidate missing the BIND permission") {
        val cand = candidate(hasBindPermission = false)
        McpDaemonDiscoverer.discover(listOf(cand), trustedPolicy).size shouldBe 0
    }

    test("rejects candidate with unreadable signing certificate before catalog parsing") {
        val cand = candidate(signingCertSha256 = null, toolCatalogJson = "not-json-{{")
        val snapshot = McpDaemonDiscoverer.discoverSnapshot(listOf(cand), trustedPolicy)

        snapshot.accepted.size shouldBe 0
        snapshot.rejected.single().reason shouldBe McpDaemonTrustPolicy.ReasonMissingSigningCertificate
    }

    test("rejects first-seen non co-signed daemon until explicitly trusted") {
        val cand = candidate(signingCertSha256 = MCP_SHA_B)
        val snapshot = McpDaemonDiscoverer.discoverSnapshot(listOf(cand), trustedPolicy)

        snapshot.accepted.size shouldBe 0
        snapshot.rejected.single().reason shouldBe McpDaemonTrustPolicy.ReasonExplicitTrustRequired
        snapshot.rejected.single().signingCertSha256 shouldBe MCP_SHA_B
    }

    test("accepts explicitly pinned non co-signed daemon") {
        val cand = candidate(signingCertSha256 = MCP_SHA_B)
        val policy = McpDaemonTrustPolicy(
            pinnedSigningCertificates = mapOf("com.example.mcp" to MCP_SHA_B),
            trustedRootSigningCertSha256 = MCP_SHA_A,
        )

        val out = McpDaemonDiscoverer.discover(listOf(cand), policy)

        out.size shouldBe 1
        out.values.single().key.packageName shouldBe "com.example.mcp"
    }

    test("normalizes root and pinned certificate fingerprints before comparison") {
        val rootPolicy = McpDaemonTrustPolicy(trustedRootSigningCertSha256 = " ${MCP_SHA_A.lowercase()} ")
        val pinnedPolicy = McpDaemonTrustPolicy(
            pinnedSigningCertificates = mapOf("com.example.mcp" to " ${MCP_SHA_B.lowercase()} "),
            trustedRootSigningCertSha256 = MCP_SHA_A,
        )

        McpDaemonDiscoverer.discover(listOf(candidate()), rootPolicy).size shouldBe 1
        McpDaemonDiscoverer.discover(
            listOf(candidate(signingCertSha256 = MCP_SHA_B)),
            pinnedPolicy,
        ).size shouldBe 1
    }

    test("rejects pinned daemon when signing certificate changes") {
        val cand = candidate(signingCertSha256 = MCP_SHA_C)
        val policy = McpDaemonTrustPolicy(
            pinnedSigningCertificates = mapOf("com.example.mcp" to MCP_SHA_B),
            trustedRootSigningCertSha256 = MCP_SHA_A,
        )

        val snapshot = McpDaemonDiscoverer.discoverSnapshot(listOf(cand), policy)

        snapshot.accepted.size shouldBe 0
        snapshot.rejected.single().reason shouldBe McpDaemonTrustPolicy.ReasonSigningCertificateChanged
        snapshot.rejected.single().signingCertSha256 shouldBe MCP_SHA_C
    }

    test("rejects candidate with protocol-version above SUPPORTED_PROTOCOL_VERSION") {
        val cand = candidate(protocolVersion = McpBridgeContract.SUPPORTED_PROTOCOL_VERSION + 1)
        McpDaemonDiscoverer.discover(listOf(cand), trustedPolicy).size shouldBe 0
    }

    test("rejects candidate with malformed catalog JSON") {
        val cand = candidate(toolCatalogJson = "not-json-{{")
        McpDaemonDiscoverer.discover(listOf(cand), trustedPolicy).size shouldBe 0
    }

    test("rejects empty tools array") {
        val cand = candidate(toolCatalogJson = """{"tools":[]}""")
        McpDaemonDiscoverer.discover(listOf(cand), trustedPolicy).size shouldBe 0
    }

    test("skips tool entries with blank names but keeps the rest") {
        val cand = candidate(
            toolCatalogJson = """
                {"tools":[
                  {"name":"","description":"blank-name"},
                  {"name":"valid","description":"keeps this one"}
                ]}
            """.trimIndent(),
        )
        val out = McpDaemonDiscoverer.discover(listOf(cand), trustedPolicy)
        out.size shouldBe 1
        out.values.single().tools.single().name shouldBe "valid"
    }

    test("supplies safe placeholder fields when description / schema are missing") {
        val cand = candidate(toolCatalogJson = """{"tools":[{"name":"thing"}]}""")
        val tool = McpDaemonDiscoverer.discover(listOf(cand), trustedPolicy).values.single().tools.single()
        tool.description.isNotBlank() shouldBe true
        tool.parameterSchemaJson.isNotBlank() shouldBe true
    }

    test("preserves insertion order across multiple daemons") {
        val candA = candidate(
            packageName = "com.example.a", daemonClassName = "com.example.a.Daemon",
        )
        val candB = candidate(
            packageName = "com.example.b", daemonClassName = "com.example.b.Daemon",
        )
        val out = McpDaemonDiscoverer.discover(listOf(candA, candB), trustedPolicy)
        out.keys.map { it.packageName } shouldBe listOf("com.example.a", "com.example.b")
    }
})
