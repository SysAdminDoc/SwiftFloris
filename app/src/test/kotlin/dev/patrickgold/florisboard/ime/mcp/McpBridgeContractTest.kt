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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class McpBridgeContractTest : FunSpec({
    test("constants follow the dev.patrickgold.florisboard.* namespace") {
        McpBridgeContract.ACTION_BIND_MCP_DAEMON shouldBe
            "dev.patrickgold.florisboard.action.BIND_MCP_DAEMON"
        McpBridgeContract.PERMISSION_BIND_MCP shouldBe
            "dev.patrickgold.florisboard.permission.BIND_MCP"
        McpBridgeContract.METADATA_TOOL_CATALOG shouldBe
            "dev.patrickgold.florisboard.mcp.tool_catalog"
        McpBridgeContract.METADATA_PROTOCOL_VERSION shouldBe
            "dev.patrickgold.florisboard.mcp.protocol_version"
    }

    test("payload cap is 4 MB") {
        McpBridgeContract.MAX_PAYLOAD_BYTES shouldBe (4L * 1024 * 1024)
    }

    test("McpToolDescriptor validates required fields") {
        val descriptor = McpToolDescriptor(
            name = "calendar.next_event",
            description = "Get the next event from the user's local calendar.",
            parameterSchemaJson = """{"type":"object","properties":{}}""",
        )
        descriptor.name shouldBe "calendar.next_event"

        shouldThrow<IllegalArgumentException> {
            McpToolDescriptor(name = "", description = "x", parameterSchemaJson = "{}")
        }
        shouldThrow<IllegalArgumentException> {
            McpToolDescriptor(name = "x", description = "", parameterSchemaJson = "{}")
        }
        shouldThrow<IllegalArgumentException> {
            McpToolDescriptor(name = "x", description = "x", parameterSchemaJson = "")
        }
    }

    test("McpToolResult success path requires payload") {
        val ok = McpToolResult(
            toolName = "calendar.next_event",
            payloadJson = """{"start":"2026-05-15T10:00:00Z"}""",
            isError = false,
        )
        ok.payloadJson shouldBe """{"start":"2026-05-15T10:00:00Z"}"""

        shouldThrow<IllegalArgumentException> {
            McpToolResult(toolName = "x", payloadJson = null, isError = false)
        }
    }

    test("McpToolResult error path requires errorMessage") {
        val err = McpToolResult(
            toolName = "calendar.next_event",
            errorMessage = "Calendar permission denied",
            isError = true,
        )
        err.errorMessage shouldBe "Calendar permission denied"

        shouldThrow<IllegalArgumentException> {
            McpToolResult(toolName = "x", errorMessage = null, isError = true)
        }
    }
})
