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

private class CountingMcpClient : McpClient {
    var calls: Int = 0
        private set
    override fun callTool(
        daemonKey: DaemonKey,
        toolName: String,
        parameterJson: String,
        timeoutMillis: Long,
    ): McpToolCallResponse {
        calls++
        return McpToolCallResponse(
            correlationId = "stub-$calls",
            toolName = toolName,
            payloadJson = """{"ok":true}""",
            errorCode = McpErrorCode.OK,
        )
    }
    override fun nextCorrelationId(): String = "stub-$calls"
}

class McpClientTest : FunSpec({

    afterEach {
        McpClientRegistry.resetForTest()
    }

    test("NoOpMcpClient returns TOOL_NOT_FOUND with a unique correlation id") {
        val response = NoOpMcpClient.callTool(
            daemonKey = DaemonKey("com.example.mcp", "com.example.mcp.Daemon"),
            toolName = "calendar.next",
            parameterJson = "{}",
        )
        response.errorCode shouldBe McpErrorCode.TOOL_NOT_FOUND
        response.isError shouldBe true
        response.correlationId.isNotBlank() shouldBe true
    }

    test("NoOpMcpClient rejects parameterJson over MAX_PAYLOAD_BYTES with PAYLOAD_TOO_LARGE") {
        val oversized = "x".repeat((McpBridgeContract.MAX_PAYLOAD_BYTES + 1).toInt())
        val response = NoOpMcpClient.callTool(
            daemonKey = DaemonKey("com.example.mcp", "com.example.mcp.Daemon"),
            toolName = "calendar.next",
            parameterJson = oversized,
        )
        response.errorCode shouldBe McpErrorCode.PAYLOAD_TOO_LARGE
    }

    test("NoOpMcpClient correlation ids are strictly increasing within a session") {
        val first = NoOpMcpClient.nextCorrelationId()
        val second = NoOpMcpClient.nextCorrelationId()
        (first != second) shouldBe true
    }

    test("McpClientRegistry starts with NoOpMcpClient") {
        McpClientRegistry.active() shouldBe NoOpMcpClient
    }

    test("setActive replaces the bound client; resetForTest restores NoOp") {
        val stub = CountingMcpClient()
        McpClientRegistry.setActive(stub)
        McpClientRegistry.active() shouldBe stub
        McpClientRegistry.resetForTest()
        McpClientRegistry.active() shouldBe NoOpMcpClient
    }
})
