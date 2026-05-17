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

import android.os.DeadObjectException
import android.os.IBinder
import android.os.RemoteException
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * AIDL `Stub` is an abstract Binder; the unit test bypasses the
 * Binder transport by feeding a custom IBinder back through
 * [AndroidMcpClient]'s `binderLookup`. The custom binder's
 * `queryLocalInterface` returns a fake [IMcpDaemon] implementation
 * that exercises the success, decode-failure, blank-response,
 * RemoteException, and DeadObjectException branches.
 */
class AndroidMcpClientTest : FunSpec({

    val daemonKey = DaemonKey(
        packageName = "com.example.daemon",
        daemonClassName = "com.example.daemon.McpService",
    )

    fun binderReturning(daemon: IMcpDaemon): IBinder = object : IBinder by NoOpBinder {
        override fun queryLocalInterface(descriptor: String): android.os.IInterface = daemon
    }

    test("callTool returns PAYLOAD_TOO_LARGE before binder lookup when parameterJson exceeds cap") {
        val client = AndroidMcpClient(binderLookup = { error("must not be called") })
        val oversized = "x".repeat((McpBridgeContract.MAX_PAYLOAD_BYTES + 1).toInt())

        val response = client.callTool(daemonKey, "tools/echo", oversized)

        response.errorCode shouldBe McpErrorCode.PAYLOAD_TOO_LARGE
        response.payloadJson shouldBe null
    }

    test("callTool returns TOOL_NOT_FOUND when binderLookup returns null") {
        val client = AndroidMcpClient(binderLookup = { null })

        val response = client.callTool(daemonKey, "tools/echo", "{}")

        response.errorCode shouldBe McpErrorCode.TOOL_NOT_FOUND
        response.errorMessage shouldContain "no daemon bound"
    }

    test("callTool round-trips an OK envelope through the daemon") {
        val daemon = FakeMcpDaemon { request ->
            McpToolCallResponse(
                correlationId = request.correlationId,
                toolName = request.toolName,
                payloadJson = """{"echo":"hi"}""",
                errorCode = McpErrorCode.OK,
            )
        }
        val client = AndroidMcpClient(binderLookup = { binderReturning(daemon) })

        val response = client.callTool(daemonKey, "tools/echo", """{"in":"hi"}""")

        response.errorCode shouldBe McpErrorCode.OK
        response.payloadJson shouldBe """{"echo":"hi"}"""
        response.toolName shouldBe "tools/echo"
        daemon.lastRequest?.toolName shouldBe "tools/echo"
        daemon.lastRequest?.parameterJson shouldBe """{"in":"hi"}"""
    }

    test("callTool translates a daemon DeadObjectException to TOOL_INTERNAL_ERROR") {
        val daemon = FakeMcpDaemon { throw DeadObjectException("binder died") }
        val client = AndroidMcpClient(binderLookup = { binderReturning(daemon) })

        val response = client.callTool(daemonKey, "tools/echo", "{}")

        response.errorCode shouldBe McpErrorCode.TOOL_INTERNAL_ERROR
        response.errorMessage shouldContain "binder died"
    }

    test("callTool translates a daemon RemoteException to TOOL_INTERNAL_ERROR") {
        val daemon = FakeMcpDaemon { throw RemoteException("boom") }
        val client = AndroidMcpClient(binderLookup = { binderReturning(daemon) })

        val response = client.callTool(daemonKey, "tools/echo", "{}")

        response.errorCode shouldBe McpErrorCode.TOOL_INTERNAL_ERROR
        response.errorMessage shouldContain "RemoteException"
    }

    test("callTool returns TOOL_INTERNAL_ERROR when daemon returns blank response") {
        val daemon = FakeMcpDaemon { _ -> null }  // null response
        val client = AndroidMcpClient(binderLookup = { binderReturning(daemon) })

        val response = client.callTool(daemonKey, "tools/echo", "{}")

        response.errorCode shouldBe McpErrorCode.TOOL_INTERNAL_ERROR
        response.errorMessage shouldContain "null/blank"
    }

    test("callTool returns TOOL_INTERNAL_ERROR when daemon returns non-JSON response") {
        val daemon = FakeMcpDaemon { _ -> "not a json envelope" }
        val client = AndroidMcpClient(binderLookup = { binderReturning(daemon) })

        val response = client.callTool(daemonKey, "tools/echo", "{}")

        response.errorCode shouldBe McpErrorCode.TOOL_INTERNAL_ERROR
        response.errorMessage shouldContain "failed to decode response"
    }

    test("callTool propagates daemon-emitted error envelopes verbatim") {
        val daemon = FakeMcpDaemon { request ->
            McpToolCallResponse(
                correlationId = request.correlationId,
                toolName = request.toolName,
                errorMessage = "parameter 'x' missing",
                errorCode = McpErrorCode.INVALID_PARAMETERS,
            )
        }
        val client = AndroidMcpClient(binderLookup = { binderReturning(daemon) })

        val response = client.callTool(daemonKey, "tools/echo", """{}""")

        response.errorCode shouldBe McpErrorCode.INVALID_PARAMETERS
        response.errorMessage shouldBe "parameter 'x' missing"
        response.isError shouldBe true
    }

    test("nextCorrelationId is unique across consecutive calls") {
        val client = AndroidMcpClient(binderLookup = { null })
        val a = client.nextCorrelationId()
        val b = client.nextCorrelationId()
        (a == b) shouldBe false
        a.startsWith("mcp-android-") shouldBe true
    }

    // Hardening: a daemon that echoes a different correlation id than the
    // one the client issued has either reused a stale response or is
    // actively trying to confuse the IME. Treat the response as untrusted.
    test("callTool rejects a daemon response with a mismatched correlationId") {
        val daemon = FakeMcpDaemon { request ->
            McpToolCallResponse(
                correlationId = request.correlationId + "-spoofed",
                toolName = request.toolName,
                payloadJson = """{"echo":"hi"}""",
                errorCode = McpErrorCode.OK,
            )
        }
        val client = AndroidMcpClient(binderLookup = { binderReturning(daemon) })

        val response = client.callTool(daemonKey, "tools/echo", """{"in":"hi"}""")

        response.errorCode shouldBe McpErrorCode.TOOL_INTERNAL_ERROR
        response.errorMessage shouldContain "correlationId mismatch"
    }

    test("callTool rejects an oversized response from the daemon") {
        val giant = "x".repeat((McpBridgeContract.MAX_PAYLOAD_BYTES + 1).toInt())
        val daemon = FakeMcpDaemon { _ -> giant }
        val client = AndroidMcpClient(binderLookup = { binderReturning(daemon) })

        val response = client.callTool(daemonKey, "tools/echo", "{}")

        response.errorCode shouldBe McpErrorCode.PAYLOAD_TOO_LARGE
    }

    test("callTool counts payload size in UTF-8 bytes, not char length") {
        // A 4-byte UTF-8 emoji has length 2 in UTF-16. Exploiting this to
        // smuggle a payload past the cap was previously possible because
        // the gate compared char length to byte cap.
        val singleEmoji = "😀"  // 😀 — 4 UTF-8 bytes, 2 UTF-16 chars
        val justOverByteCap = singleEmoji.repeat(
            (McpBridgeContract.MAX_PAYLOAD_BYTES / 4 + 1).toInt(),
        )
        // Sanity-check the test fixture: char length must be ≤ cap while
        // UTF-8 byte length must be > cap so the bug-and-fix demarcation
        // actually exercises this code path.
        (justOverByteCap.length.toLong() < McpBridgeContract.MAX_PAYLOAD_BYTES) shouldBe true
        val client = AndroidMcpClient(binderLookup = { error("must not be called") })

        val response = client.callTool(daemonKey, "tools/echo", justOverByteCap)

        response.errorCode shouldBe McpErrorCode.PAYLOAD_TOO_LARGE
    }
})

/**
 * Fake daemon implementing [IMcpDaemon] directly. The lambda either
 * - returns an `McpToolCallResponse` (success path — encoded back to
 *   JSON via the same codec the wire uses),
 * - returns a non-envelope `String` to simulate a malformed response,
 * - returns `null` to simulate a blank reply, or
 * - throws to simulate the AIDL transport failures.
 */
private class FakeMcpDaemon(
    private val onInvoke: (McpToolCallRequest) -> Any?,
) : IMcpDaemon.Stub() {

    var lastRequest: McpToolCallRequest? = null

    override fun listToolNames(): Array<String> = arrayOf("tools/echo")

    override fun invoke(requestJson: String): String? {
        val request = McpEnvelopeCodec.decodeRequest(requestJson)
        lastRequest = request
        return when (val result = onInvoke(request)) {
            is McpToolCallResponse -> McpEnvelopeCodec.encodeResponse(result)
            is String -> result
            null -> null
            else -> error("unexpected fake-daemon return type: ${result::class}")
        }
    }
}

/** Minimum-viable IBinder for the binderReturning() helper. */
private object NoOpBinder : IBinder {
    override fun getInterfaceDescriptor(): String? = null
    override fun pingBinder(): Boolean = true
    override fun isBinderAlive(): Boolean = true
    override fun queryLocalInterface(descriptor: String): android.os.IInterface? = null
    override fun dump(fd: java.io.FileDescriptor, args: Array<out String>?) {}
    override fun dumpAsync(fd: java.io.FileDescriptor, args: Array<out String>?) {}
    override fun transact(code: Int, data: android.os.Parcel, reply: android.os.Parcel?, flags: Int): Boolean = false
    override fun linkToDeath(recipient: IBinder.DeathRecipient, flags: Int) {}
    override fun unlinkToDeath(recipient: IBinder.DeathRecipient, flags: Int): Boolean = true
}
