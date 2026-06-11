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
    var lastDaemonKey: DaemonKey? = null
        private set
    override fun callTool(
        daemonKey: DaemonKey,
        toolName: String,
        parameterJson: String,
        timeoutMillis: Long,
    ): McpToolCallResponse {
        calls++
        lastDaemonKey = daemonKey
        return response
    }
    override fun nextCorrelationId(): String = "stub-${calls + 1}"
}

private class FakeRegistry(
    private val entries: List<ResolvedTool>,
) : McpDispatchRouter.RegistryView {
    override fun findTool(daemonKey: DaemonKey, toolName: String): ResolvedTool? =
        entries.firstOrNull { it.daemon == daemonKey && it.tool.name == toolName }
    override fun findToolMatches(toolName: String): List<ResolvedTool> =
        entries.filter { it.tool.name == toolName }
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
        val router = McpDispatchRouter(mcp, FakeRegistry(listOf(resolved)))
        val resp = router.dispatch(McpDispatchRouter.Request(
            toolName = "calendar.next",
            parameterJson = "{}",
            inputType = 0x81,
        ))
        (resp is McpDispatchRouter.Response.Suppressed) shouldBe true
        mcp.calls shouldBe 0
    }

    test("blank tool name returns Suppressed") {
        val router = McpDispatchRouter(CountingMcp(successCall()), FakeRegistry(emptyList()))
        val resp = router.dispatch(McpDispatchRouter.Request(
            toolName = "",
            parameterJson = "{}",
        ))
        (resp is McpDispatchRouter.Response.Suppressed) shouldBe true
    }

    test("tool not in registry returns Suppressed with the lookup reason") {
        val router = McpDispatchRouter(CountingMcp(successCall()), FakeRegistry(emptyList()))
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
            FakeRegistry(listOf(resolved)),
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
        val router = McpDispatchRouter(mcp, FakeRegistry(listOf(resolved)))
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
        val router = McpDispatchRouter(mcp, FakeRegistry(listOf(resolved)))
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
        val router = McpDispatchRouter(mcp, FakeRegistry(listOf(resolved)))
        val resp = router.dispatch(McpDispatchRouter.Request(
            toolName = "calendar.next",
            parameterJson = "{}",
            imeOptions = 0x01000000,
        ))
        (resp is McpDispatchRouter.Response.Suppressed) shouldBe true
        mcp.calls shouldBe 0
    }

    test("isDaemonDisabled=true short-circuits to Suppressed before the client is invoked") {
        val mcp = CountingMcp(successCall())
        val router = McpDispatchRouter(
            client = mcp,
            registryView = FakeRegistry(listOf(resolved)),
            isDaemonDisabled = { it == daemon },
        )
        val resp = router.dispatch(McpDispatchRouter.Request(
            toolName = "calendar.next",
            parameterJson = "{}",
        ))
        val suppressed = resp as McpDispatchRouter.Response.Suppressed
        suppressed.reason shouldBe "daemon com.example.mcp disabled by user"
        mcp.calls shouldBe 0
    }

    test("isDaemonDisabled lambda is consulted only after the tool resolves") {
        val mcp = CountingMcp(successCall())
        var disabledQueryCount = 0
        val router = McpDispatchRouter(
            client = mcp,
            registryView = FakeRegistry(emptyList()),  // No tool registered.
            isDaemonDisabled = { disabledQueryCount++; true },
        )
        router.dispatch(McpDispatchRouter.Request(
            toolName = "calendar.next",
            parameterJson = "{}",
        ))
        disabledQueryCount shouldBe 0  // Never reached the disabled-check.
        mcp.calls shouldBe 0
    }

    test("isToolDisabled=true short-circuits to Suppressed with the per-tool reason (matrix #38)") {
        val mcp = CountingMcp(successCall())
        val router = McpDispatchRouter(
            client = mcp,
            registryView = FakeRegistry(listOf(resolved)),
            isToolDisabled = { d, t -> d == daemon && t == "calendar.next" },
        )
        val resp = router.dispatch(McpDispatchRouter.Request(
            toolName = "calendar.next",
            parameterJson = "{}",
        ))
        val suppressed = resp as McpDispatchRouter.Response.Suppressed
        suppressed.reason shouldBe "tool calendar.next on daemon com.example.mcp disabled by user"
        mcp.calls shouldBe 0
    }

    test("isToolDisabled lambda is not consulted when isDaemonDisabled already short-circuits") {
        val mcp = CountingMcp(successCall())
        var toolGateCount = 0
        val router = McpDispatchRouter(
            client = mcp,
            registryView = FakeRegistry(listOf(resolved)),
            isDaemonDisabled = { true },
            isToolDisabled = { _, _ -> toolGateCount++; true },
        )
        router.dispatch(McpDispatchRouter.Request(
            toolName = "calendar.next",
            parameterJson = "{}",
        ))
        toolGateCount shouldBe 0
        mcp.calls shouldBe 0
    }

    test("isToolDisabled lambda lets non-matching (daemon,tool) pairs through to the client") {
        val mcp = CountingMcp(successCall())
        val router = McpDispatchRouter(
            client = mcp,
            registryView = FakeRegistry(listOf(resolved)),
            isToolDisabled = { _, t -> t == "other.tool" },
        )
        val resp = router.dispatch(McpDispatchRouter.Request(
            toolName = "calendar.next",
            parameterJson = "{}",
        ))
        (resp is McpDispatchRouter.Response.Completed) shouldBe true
        mcp.calls shouldBe 1
    }

    test("missing consent short-circuits before any other check (matrix #37)") {
        val mcp = CountingMcp(successCall())
        val router = McpDispatchRouter(
            client = mcp,
            registryView = FakeRegistry(listOf(resolved)),
            isConsentGranted = { false },
        )
        val resp = router.dispatch(McpDispatchRouter.Request(
            toolName = "calendar.next",
            parameterJson = "{}",
        ))
        val suppressed = resp as McpDispatchRouter.Response.Suppressed
        suppressed.reason shouldBe "consent required"
        mcp.calls shouldBe 0
    }

    test("consent gate beats sensitive-field gate — consent reason wins on a password field too") {
        val mcp = CountingMcp(successCall())
        val router = McpDispatchRouter(
            client = mcp,
            registryView = FakeRegistry(listOf(resolved)),
            isConsentGranted = { false },
        )
        val resp = router.dispatch(McpDispatchRouter.Request(
            toolName = "calendar.next",
            parameterJson = "{}",
            inputType = 0x81,
        ))
        (resp as McpDispatchRouter.Response.Suppressed).reason shouldBe "consent required"
        mcp.calls shouldBe 0
    }

    test("granted consent lets requests pass through to the existing gates and client") {
        val mcp = CountingMcp(successCall())
        val router = McpDispatchRouter(
            client = mcp,
            registryView = FakeRegistry(listOf(resolved)),
            isConsentGranted = { true },
        )
        val resp = router.dispatch(McpDispatchRouter.Request(
            toolName = "calendar.next",
            parameterJson = "{}",
        ))
        (resp is McpDispatchRouter.Response.Completed) shouldBe true
        mcp.calls shouldBe 1
    }

    test("daemonKey-scoped dispatch reaches the named daemon and never its shadow") {
        val daemonB = DaemonKey("com.example.shadow", "com.example.shadow.Daemon")
        val shadowed = FakeRegistry(listOf(resolved, ResolvedTool(daemonB, tool)))
        val mcp = CountingMcp(successCall())
        val router = McpDispatchRouter(mcp, shadowed)
        val respA = router.dispatch(McpDispatchRouter.Request(
            toolName = "calendar.next",
            parameterJson = "{}",
            daemonKey = daemon,
        ))
        (respA as McpDispatchRouter.Response.Completed).daemon shouldBe daemon
        mcp.lastDaemonKey shouldBe daemon
        val respB = router.dispatch(McpDispatchRouter.Request(
            toolName = "calendar.next",
            parameterJson = "{}",
            daemonKey = daemonB,
        ))
        (respB as McpDispatchRouter.Response.Completed).daemon shouldBe daemonB
        mcp.lastDaemonKey shouldBe daemonB
        mcp.calls shouldBe 2
    }

    test("flat-name dispatch of a shadowed tool is suppressed instead of silently picking a winner") {
        val daemonB = DaemonKey("com.example.shadow", "com.example.shadow.Daemon")
        val shadowed = FakeRegistry(listOf(resolved, ResolvedTool(daemonB, tool)))
        val mcp = CountingMcp(successCall())
        val router = McpDispatchRouter(mcp, shadowed)
        val resp = router.dispatch(McpDispatchRouter.Request(
            toolName = "calendar.next",
            parameterJson = "{}",
        ))
        val suppressed = resp as McpDispatchRouter.Response.Suppressed
        suppressed.reason shouldBe
            "tool calendar.next is ambiguous across 2 daemons — request must name a daemon"
        mcp.calls shouldBe 0
        mcp.lastDaemonKey shouldBe null
    }

    test("daemonKey-scoped dispatch is suppressed when the named daemon does not advertise the tool") {
        val daemonB = DaemonKey("com.example.shadow", "com.example.shadow.Daemon")
        val mcp = CountingMcp(successCall())
        val router = McpDispatchRouter(mcp, FakeRegistry(listOf(resolved)))
        val resp = router.dispatch(McpDispatchRouter.Request(
            toolName = "calendar.next",
            parameterJson = "{}",
            daemonKey = daemonB,
        ))
        val suppressed = resp as McpDispatchRouter.Response.Suppressed
        suppressed.reason shouldBe "tool calendar.next not registered on daemon com.example.shadow"
        mcp.calls shouldBe 0
    }
})
