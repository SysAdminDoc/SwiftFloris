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

class McpDaemonDiscovererTest : FunSpec({

    val validJson = """
        {"tools":[{"name":"calendar.next","description":"Next event","parameterSchema":"{\"type\":\"object\"}"}]}
    """.trimIndent()

    test("happy path discovers a single daemon") {
        val cand = DiscoveryCandidate(
            packageName = "com.example.mcp",
            daemonClassName = "com.example.mcp.Daemon",
            protocolVersion = 1,
            hasBindPermission = true,
            toolCatalogJson = validJson,
        )
        val out = McpDaemonDiscoverer.discover(listOf(cand))
        out.size shouldBe 1
        out.values.single().tools.single().name shouldBe "calendar.next"
    }

    test("rejects candidate missing the BIND permission") {
        val cand = DiscoveryCandidate(
            packageName = "com.example.mcp",
            daemonClassName = "com.example.mcp.Daemon",
            protocolVersion = 1,
            hasBindPermission = false,
            toolCatalogJson = validJson,
        )
        McpDaemonDiscoverer.discover(listOf(cand)).size shouldBe 0
    }

    test("rejects candidate with protocol-version above SUPPORTED_PROTOCOL_VERSION") {
        val cand = DiscoveryCandidate(
            packageName = "com.example.mcp",
            daemonClassName = "com.example.mcp.Daemon",
            protocolVersion = McpBridgeContract.SUPPORTED_PROTOCOL_VERSION + 1,
            hasBindPermission = true,
            toolCatalogJson = validJson,
        )
        McpDaemonDiscoverer.discover(listOf(cand)).size shouldBe 0
    }

    test("rejects candidate with malformed catalog JSON") {
        val cand = DiscoveryCandidate(
            packageName = "com.example.mcp",
            daemonClassName = "com.example.mcp.Daemon",
            protocolVersion = 1,
            hasBindPermission = true,
            toolCatalogJson = "not-json-{{",
        )
        McpDaemonDiscoverer.discover(listOf(cand)).size shouldBe 0
    }

    test("rejects empty tools array") {
        val cand = DiscoveryCandidate(
            packageName = "com.example.mcp",
            daemonClassName = "com.example.mcp.Daemon",
            protocolVersion = 1,
            hasBindPermission = true,
            toolCatalogJson = """{"tools":[]}""",
        )
        McpDaemonDiscoverer.discover(listOf(cand)).size shouldBe 0
    }

    test("skips tool entries with blank names but keeps the rest") {
        val cand = DiscoveryCandidate(
            packageName = "com.example.mcp",
            daemonClassName = "com.example.mcp.Daemon",
            protocolVersion = 1,
            hasBindPermission = true,
            toolCatalogJson = """
                {"tools":[
                  {"name":"","description":"blank-name"},
                  {"name":"valid","description":"keeps this one"}
                ]}
            """.trimIndent(),
        )
        val out = McpDaemonDiscoverer.discover(listOf(cand))
        out.size shouldBe 1
        out.values.single().tools.single().name shouldBe "valid"
    }

    test("supplies safe placeholder fields when description / schema are missing") {
        val cand = DiscoveryCandidate(
            packageName = "com.example.mcp",
            daemonClassName = "com.example.mcp.Daemon",
            protocolVersion = 1,
            hasBindPermission = true,
            toolCatalogJson = """{"tools":[{"name":"thing"}]}""",
        )
        val tool = McpDaemonDiscoverer.discover(listOf(cand)).values.single().tools.single()
        tool.description.isNotBlank() shouldBe true
        tool.parameterSchemaJson.isNotBlank() shouldBe true
    }

    test("preserves insertion order across multiple daemons") {
        val candA = DiscoveryCandidate(
            packageName = "com.example.a", daemonClassName = "com.example.a.Daemon",
            protocolVersion = 1, hasBindPermission = true, toolCatalogJson = validJson,
        )
        val candB = DiscoveryCandidate(
            packageName = "com.example.b", daemonClassName = "com.example.b.Daemon",
            protocolVersion = 1, hasBindPermission = true, toolCatalogJson = validJson,
        )
        val out = McpDaemonDiscoverer.discover(listOf(candA, candB))
        out.keys.map { it.packageName } shouldBe listOf("com.example.a", "com.example.b")
    }
})
