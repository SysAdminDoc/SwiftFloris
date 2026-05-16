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

private class CountingMcp(
    private val response: McpToolCallResponse,
) : McpClient {
    var calls: Int = 0
        private set
    override fun callTool(
        daemonKey: DaemonKey,
        toolName: String,
        parameterJson: String,
        timeoutMillis: Long,
    ): McpToolCallResponse {
        calls++
        return response
    }
    override fun nextCorrelationId(): String = "stub-${calls + 1}"
}

private class FakeRegistry(
    private val table: Map<String, ResolvedTool>,
) : McpDispatchRouter.RegistryView {
    override fun findTool(toolName: String): ResolvedTool? = table[toolName]
}

class McpDispatchRouterTest : FunSpec({

    val daemon = DaemonKey("com.example.mcp", "com.example.mcp.Daemon")
    val tool = McpToolDescriptor(
        name = "calendar.next",
        description = "next event",
        parameterSchemaJson = """{"type":"object"}""",
    )
    val resolved = ResolvedTool(daemon, tool)

    fun successCall() = McpToolCallResponse(
        correlationId = "ok-1",
        toolName = "calendar.next",
        payloadJson = """{"events":[]}""",
        errorCode = McpErrorCode.OK,
    )

    fun errorCall() = McpToolCallResponse(
        correlationId = "err-1",
        toolName = "calendar.next",
        errorMessage = "tool internal failure",
        errorCode = McpErrorCode.TOOL_INTERNAL_ERROR,
    )

    test("password field short-circuits to Suppressed before touching the registry") {
        val mcp = CountingMcp(successCall())
        val router = McpDispatchRouter(mcp, FakeRegistry(mapOf("calendar.next" to resolved)))
        val resp = router.dispatch(McpDispatchRouter.Request(
            toolName = "calendar.next",
            parameterJson = "{}",
            inputType = 0x81,
        ))
        (resp is McpDispatchRouter.Response.Suppressed) shouldBe true
        mcp.calls shouldBe 0
    }

    test("blank tool name returns Suppressed") {
        val router = McpDispatchRouter(CountingMcp(successCall()), FakeRegistry(emptyMap()))
        val resp = router.dispatch(McpDispatchRouter.Request(
            toolName = "",
            parameterJson = "{}",
        ))
        (resp is McpDispatchRouter.Response.Suppressed) shouldBe true
    }

    test("tool not in registry returns Suppressed with the lookup reason") {
        val router = McpDispatchRouter(CountingMcp(successCall()), FakeRegistry(emptyMap()))
        val resp = router.dispatch(McpDispatchRouter.Request(
            toolName = "ghost.tool",
            parameterJson = "{}",
        ))
        val sup = resp as McpDispatchRouter.Response.Suppressed
        sup.reason shouldBe "tool ghost.tool not registered"
    }

    test("oversized parameterJson returns Suppressed with size reason") {
        val router = McpDispatchRouter(
            CountingMcp(successCall()),
            FakeRegistry(mapOf("calendar.next" to resolved)),
        )
        val oversized = "x".repeat((McpBridgeContract.MAX_PAYLOAD_BYTES + 1).toInt())
        val resp = router.dispatch(McpDispatchRouter.Request(
            toolName = "calendar.next",
            parameterJson = oversized,
        ))
        (resp is McpDispatchRouter.Response.Suppressed) shouldBe true
    }

    test("happy path returns Completed with the daemon + the OK response") {
        val mcp = CountingMcp(successCall())
        val router = McpDispatchRouter(mcp, FakeRegistry(mapOf("calendar.next" to resolved)))
        val resp = router.dispatch(McpDispatchRouter.Request(
            toolName = "calendar.next",
            parameterJson = "{}",
        ))
        val completed = resp as McpDispatchRouter.Response.Completed
        completed.daemon shouldBe daemon
        completed.callResponse.errorCode shouldBe McpErrorCode.OK
        mcp.calls shouldBe 1
    }

    test("delegate error response is wrapped in Failed") {
        val mcp = CountingMcp(errorCall())
        val router = McpDispatchRouter(mcp, FakeRegistry(mapOf("calendar.next" to resolved)))
        val resp = router.dispatch(McpDispatchRouter.Request(
            toolName = "calendar.next",
            parameterJson = "{}",
        ))
        val failed = resp as McpDispatchRouter.Response.Failed
        failed.callResponse.errorCode shouldBe McpErrorCode.TOOL_INTERNAL_ERROR
        mcp.calls shouldBe 1
    }

    test("IME_FLAG_NO_PERSONALIZED_LEARNING suppresses dispatch") {
        val mcp = CountingMcp(successCall())
        val router = McpDispatchRouter(mcp, FakeRegistry(mapOf("calendar.next" to resolved)))
        val resp = router.dispatch(McpDispatchRouter.Request(
            toolName = "calendar.next",
            parameterJson = "{}",
            imeOptions = 0x01000000,
        ))
        (resp is McpDispatchRouter.Response.Suppressed) shouldBe true
        mcp.calls shouldBe 0
    }
})
