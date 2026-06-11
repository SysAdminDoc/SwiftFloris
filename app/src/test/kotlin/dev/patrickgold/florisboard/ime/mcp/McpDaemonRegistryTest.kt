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

class McpDaemonRegistryTest : FunSpec({

    afterEach {
        McpDaemonRegistry.resetForTest()
    }

    fun dummyTool(name: String) = McpToolDescriptor(
        name = name,
        description = "demo tool $name",
        parameterSchemaJson = "{\"type\":\"object\"}",
    )

    test("empty registry returns no daemons + no tools") {
        McpDaemonRegistry.resetForTest()
        McpDaemonRegistry.size() shouldBe 0
        McpDaemonRegistry.active() shouldBe emptyMap()
        McpDaemonRegistry.listAllTools() shouldBe emptyList()
    }

    test("setActive populates the registry and atomic active() returns the new map") {
        val key = DaemonKey("com.example.mcp", "com.example.mcp.Daemon")
        val entry = DaemonEntry(
            key = key,
            protocolVersion = 1,
            tools = listOf(dummyTool("calendar.next"), dummyTool("contacts.search")),
        )
        McpDaemonRegistry.setActive(mapOf(key to entry))
        McpDaemonRegistry.size() shouldBe 1
        McpDaemonRegistry.get(key)?.tools?.size shouldBe 2
    }

    test("findTool scans across multiple daemons") {
        val keyA = DaemonKey("com.example.a", "com.example.a.Daemon")
        val keyB = DaemonKey("com.example.b", "com.example.b.Daemon")
        McpDaemonRegistry.setActive(
            mapOf(
                keyA to DaemonEntry(
                    key = keyA, protocolVersion = 1,
                    tools = listOf(dummyTool("a.thing")),
                ),
                keyB to DaemonEntry(
                    key = keyB, protocolVersion = 1,
                    tools = listOf(dummyTool("b.thing")),
                ),
            ),
        )
        McpDaemonRegistry.findTool("a.thing")?.daemon shouldBe keyA
        McpDaemonRegistry.findTool("b.thing")?.daemon shouldBe keyB
        McpDaemonRegistry.findTool("nope") shouldBe null
    }

    test("flat findTool fails on a tool name shadowed across daemons instead of picking the first") {
        val keyA = DaemonKey("com.example.a", "com.example.a.Daemon")
        val keyB = DaemonKey("com.example.b", "com.example.b.Daemon")
        McpDaemonRegistry.setActive(
            linkedMapOf(
                keyA to DaemonEntry(
                    key = keyA, protocolVersion = 1,
                    tools = listOf(dummyTool("shared.tool")),
                ),
                keyB to DaemonEntry(
                    key = keyB, protocolVersion = 1,
                    tools = listOf(dummyTool("shared.tool")),
                ),
            ),
        )
        McpDaemonRegistry.findTool("shared.tool") shouldBe null
    }

    test("findToolMatches surfaces every (daemon, tool) entry for a shadowed name") {
        val keyA = DaemonKey("com.example.a", "com.example.a.Daemon")
        val keyB = DaemonKey("com.example.b", "com.example.b.Daemon")
        McpDaemonRegistry.setActive(
            linkedMapOf(
                keyA to DaemonEntry(
                    key = keyA, protocolVersion = 1,
                    tools = listOf(dummyTool("shared.tool")),
                ),
                keyB to DaemonEntry(
                    key = keyB, protocolVersion = 1,
                    tools = listOf(dummyTool("shared.tool"), dummyTool("b.only")),
                ),
            ),
        )
        val matches = McpDaemonRegistry.findToolMatches("shared.tool")
        matches.size shouldBe 2
        matches.map { it.daemon } shouldBe listOf(keyA, keyB)
        McpDaemonRegistry.findToolMatches("b.only").map { it.daemon } shouldBe listOf(keyB)
        McpDaemonRegistry.findToolMatches("nope") shouldBe emptyList()
    }

    test("exact (daemonKey, toolName) lookup resolves each daemon even when names collide") {
        val keyA = DaemonKey("com.example.a", "com.example.a.Daemon")
        val keyB = DaemonKey("com.example.b", "com.example.b.Daemon")
        McpDaemonRegistry.setActive(
            linkedMapOf(
                keyA to DaemonEntry(
                    key = keyA, protocolVersion = 1,
                    tools = listOf(dummyTool("shared.tool")),
                ),
                keyB to DaemonEntry(
                    key = keyB, protocolVersion = 1,
                    tools = listOf(dummyTool("shared.tool")),
                ),
            ),
        )
        McpDaemonRegistry.findTool(keyA, "shared.tool")?.daemon shouldBe keyA
        McpDaemonRegistry.findTool(keyB, "shared.tool")?.daemon shouldBe keyB
        val keyC = DaemonKey("com.example.c", "com.example.c.Daemon")
        McpDaemonRegistry.findTool(keyC, "shared.tool") shouldBe null
        McpDaemonRegistry.findTool(keyA, "not.advertised") shouldBe null
    }

    test("listAllTools flattens tools across every daemon") {
        val keyA = DaemonKey("com.example.a", "com.example.a.Daemon")
        val keyB = DaemonKey("com.example.b", "com.example.b.Daemon")
        McpDaemonRegistry.setActive(
            linkedMapOf(
                keyA to DaemonEntry(
                    key = keyA, protocolVersion = 1,
                    tools = listOf(dummyTool("a.x"), dummyTool("a.y")),
                ),
                keyB to DaemonEntry(
                    key = keyB, protocolVersion = 1,
                    tools = listOf(dummyTool("b.z")),
                ),
            ),
        )
        val tools = McpDaemonRegistry.listAllTools()
        tools.size shouldBe 3
        tools.map { it.tool.name } shouldBe listOf("a.x", "a.y", "b.z")
    }

    test("DaemonEntry rejects unsupported protocol versions") {
        val key = DaemonKey("com.example.mcp", "com.example.mcp.Daemon")
        var caught = false
        try {
            DaemonEntry(
                key = key,
                protocolVersion = McpBridgeContract.SUPPORTED_PROTOCOL_VERSION + 1,
                tools = emptyList(),
            )
        } catch (_: IllegalArgumentException) {
            caught = true
        }
        caught shouldBe true
    }

    test("DaemonKey rejects blank components") {
        var caught = false
        try {
            DaemonKey("", "com.example.Daemon")
        } catch (_: IllegalArgumentException) {
            caught = true
        }
        caught shouldBe true
    }
})
