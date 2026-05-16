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

/**
 * ROADMAP §10.5 L7.5 — timeout budget wrapper around [McpClient].
 *
 * Tools the IME calls into shouldn't be able to wedge the keyboard
 * by hanging in a long-running daemon. This wrapper enforces a
 * **global per-process deadline** on top of the per-call timeout
 * each `callTool(...)` accepts:
 *
 *  - The wrapper has a [windowMillis] sliding budget (default
 *    10,000 ms over a 60 s window). Once the budget is exhausted
 *    further calls short-circuit with `errorCode = TIMEOUT` until
 *    the window rolls forward.
 *  - Each individual call still goes through the underlying
 *    client with its own `timeoutMillis`; this wrapper measures
 *    actual elapsed time and accumulates against the budget.
 *
 * Use case: a misbehaving MCP tool that's slow but not strictly
 * hung shouldn't degrade typing performance. After the budget
 * trips, smart-compose / quick-action surfaces gate the tool off
 * automatically until the user explicitly re-enables it.
 *
 * Single source of `System.nanoTime()` for testability. Tests
 * inject a `Clock` so the budget can be advanced without sleep.
 */
class McpTimeoutClient(
    private val delegate: McpClient,
    val budgetMillis: Long = DEFAULT_BUDGET_MILLIS,
    val windowMillis: Long = DEFAULT_WINDOW_MILLIS,
    private val clock: Clock = Clock.System,
) : McpClient {

    init {
        require(budgetMillis > 0) { "budgetMillis must be positive (was $budgetMillis)" }
        require(windowMillis > 0) { "windowMillis must be positive (was $windowMillis)" }
        require(budgetMillis < windowMillis) {
            "budgetMillis must be < windowMillis (otherwise the breaker never trips)"
        }
    }

    /** Per-process clock. Tests inject a deterministic fake. */
    fun interface Clock {
        fun nowMillis(): Long
        object System : Clock {
            override fun nowMillis(): Long = java.lang.System.currentTimeMillis()
        }
    }

    private val lock = Any()
    private var windowStart: Long = clock.nowMillis()
    private var elapsedInWindow: Long = 0

    /** Total ms spent dispatching MCP calls since registration. */
    @Volatile
    var totalDispatchMillis: Long = 0
        private set

    /** Number of times the breaker has tripped. */
    @Volatile
    var breakerTrips: Long = 0
        private set

    override fun callTool(
        daemonKey: DaemonKey,
        toolName: String,
        parameterJson: String,
        timeoutMillis: Long,
    ): McpToolCallResponse {
        synchronized(lock) {
            rolloverWindowLocked()
            if (elapsedInWindow >= budgetMillis) {
                breakerTrips++
                return McpToolCallResponse(
                    correlationId = nextCorrelationId(),
                    toolName = toolName,
                    errorMessage = "MCP budget exhausted: ${elapsedInWindow}ms of ${budgetMillis}ms in current window",
                    errorCode = McpErrorCode.TIMEOUT,
                )
            }
        }
        val startNs = clock.nowMillis()
        val response = delegate.callTool(daemonKey, toolName, parameterJson, timeoutMillis)
        val durationMs = clock.nowMillis() - startNs
        synchronized(lock) {
            elapsedInWindow += durationMs
            totalDispatchMillis += durationMs
        }
        return response
    }

    override fun nextCorrelationId(): String = delegate.nextCorrelationId()

    private fun rolloverWindowLocked() {
        val now = clock.nowMillis()
        if (now - windowStart >= windowMillis) {
            windowStart = now
            elapsedInWindow = 0
        }
    }

    /** Test-only — reset the breaker state. */
    internal fun resetForTest() = synchronized(lock) {
        windowStart = clock.nowMillis()
        elapsedInWindow = 0
        totalDispatchMillis = 0
        breakerTrips = 0
    }

    companion object {
        const val DEFAULT_BUDGET_MILLIS: Long = 10_000L
        const val DEFAULT_WINDOW_MILLIS: Long = 60_000L
    }
}
