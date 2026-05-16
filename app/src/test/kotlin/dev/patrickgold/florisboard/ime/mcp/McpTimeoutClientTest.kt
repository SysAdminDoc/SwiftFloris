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
import java.util.concurrent.atomic.AtomicLong

/**
 * Slow-but-not-hung delegate that pretends to take [millisPerCall]
 * milliseconds for each `callTool`. Drives the wrapper's elapsed-time
 * accumulator using the injected clock.
 */
private class FakeDelegate(
    val clock: AtomicLong,
    val millisPerCall: Long,
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
        // Advance the fake clock to simulate dispatch duration.
        clock.addAndGet(millisPerCall)
        return McpToolCallResponse(
            correlationId = "fake-$calls",
            toolName = toolName,
            payloadJson = """{"ok":true}""",
            errorCode = McpErrorCode.OK,
        )
    }
    override fun nextCorrelationId(): String = "fake-${calls + 1}"
}

class McpTimeoutClientTest : FunSpec({

    val daemon = DaemonKey("com.example.mcp", "com.example.mcp.Daemon")

    test("calls within budget pass through to the delegate") {
        val clock = AtomicLong(0L)
        val delegate = FakeDelegate(clock, millisPerCall = 100L)
        val wrapped = McpTimeoutClient(
            delegate = delegate,
            budgetMillis = 1_000L,
            windowMillis = 60_000L,
            clock = McpTimeoutClient.Clock { clock.get() },
        )
        val response = wrapped.callTool(daemon, "tool", "{}", 5_000L)
        response.errorCode shouldBe McpErrorCode.OK
        delegate.calls shouldBe 1
    }

    test("breaker trips when budget is exhausted within the window") {
        val clock = AtomicLong(0L)
        val delegate = FakeDelegate(clock, millisPerCall = 400L)
        val wrapped = McpTimeoutClient(
            delegate = delegate,
            budgetMillis = 1_000L,
            windowMillis = 60_000L,
            clock = McpTimeoutClient.Clock { clock.get() },
        )
        // Three calls × 400 ms = 1200 ms > 1000 ms budget.
        wrapped.callTool(daemon, "t", "{}", 5_000L)
        wrapped.callTool(daemon, "t", "{}", 5_000L)
        wrapped.callTool(daemon, "t", "{}", 5_000L)
        val fourth = wrapped.callTool(daemon, "t", "{}", 5_000L)
        // Three delegate calls landed; the 4th should short-circuit.
        delegate.calls shouldBe 3
        fourth.errorCode shouldBe McpErrorCode.TIMEOUT
        wrapped.breakerTrips shouldBe 1L
    }

    test("budget resets when the window rolls forward") {
        val clock = AtomicLong(0L)
        val delegate = FakeDelegate(clock, millisPerCall = 600L)
        val wrapped = McpTimeoutClient(
            delegate = delegate,
            budgetMillis = 1_000L,
            windowMillis = 10_000L,
            clock = McpTimeoutClient.Clock { clock.get() },
        )
        // Two calls × 600 ms = 1200 ms — second one trips.
        wrapped.callTool(daemon, "t", "{}", 5_000L)
        wrapped.callTool(daemon, "t", "{}", 5_000L)
        val third = wrapped.callTool(daemon, "t", "{}", 5_000L)
        third.errorCode shouldBe McpErrorCode.TIMEOUT
        // Roll the clock past the window.
        clock.addAndGet(15_000L)
        val fourth = wrapped.callTool(daemon, "t", "{}", 5_000L)
        fourth.errorCode shouldBe McpErrorCode.OK
    }

    test("totalDispatchMillis accumulates across rollovers") {
        val clock = AtomicLong(0L)
        val delegate = FakeDelegate(clock, millisPerCall = 200L)
        val wrapped = McpTimeoutClient(
            delegate = delegate,
            budgetMillis = 500L,
            windowMillis = 1_000L,
            clock = McpTimeoutClient.Clock { clock.get() },
        )
        wrapped.callTool(daemon, "t", "{}", 5_000L)
        wrapped.callTool(daemon, "t", "{}", 5_000L)
        // Roll forward, fire another.
        clock.addAndGet(5_000L)
        wrapped.callTool(daemon, "t", "{}", 5_000L)
        wrapped.totalDispatchMillis shouldBe 600L
    }

    test("budgetMillis must be < windowMillis (otherwise breaker never trips)") {
        var caught = false
        try {
            McpTimeoutClient(
                delegate = FakeDelegate(AtomicLong(0L), 100L),
                budgetMillis = 60_000L,
                windowMillis = 60_000L,
            )
        } catch (_: IllegalArgumentException) {
            caught = true
        }
        caught shouldBe true
    }
})
