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
import java.util.concurrent.atomic.AtomicLong

/**
 * ROADMAP §10.5 L7.4 — Android-backed [McpClient] that dispatches
 * [McpToolCallRequest] envelopes across the AIDL surface defined in
 * `IMcpDaemon.aidl`.
 *
 * **Responsibilities:**
 *  - Look up the bound [IMcpDaemon] for a given [DaemonKey] via the
 *    supplied [binderLookup] (the IME's `McpServiceConnectionManager`
 *    holds the per-daemon `ServiceConnection` + provides the current
 *    binder on demand — that piece lands as L7.4b).
 *  - Encode the request envelope (`McpEnvelopeCodec.encodeRequest`),
 *    invoke the daemon's `invoke(String)` AIDL method, decode the
 *    response.
 *  - Translate the four AIDL-layer failure modes — no binder bound,
 *    `DeadObjectException`, `RemoteException`, and decode failures —
 *    into the same [McpToolCallResponse] failure shape callers
 *    already handle from [NoOpMcpClient].
 *
 * **Not handled here** (deliberately): timeouts. The
 * [McpTimeoutClient] decorator already wraps any [McpClient] in a
 * cancellation guard at the IME side, so this class doesn't
 * double-implement that logic.
 *
 * Constructed with a [binderLookup] lambda rather than a stored
 * `ServiceConnection` so the binding lifecycle lives one layer up
 * (the service-connection manager re-binds on rebind events without
 * needing to mutate this client).
 */
class AndroidMcpClient(
    private val binderLookup: (DaemonKey) -> IBinder?,
) : McpClient {

    override fun callTool(
        daemonKey: DaemonKey,
        toolName: String,
        parameterJson: String,
        timeoutMillis: Long,
    ): McpToolCallResponse {
        if (parameterJson.length.toLong() > McpBridgeContract.MAX_PAYLOAD_BYTES) {
            return McpToolCallResponse(
                correlationId = nextCorrelationId(),
                toolName = toolName,
                errorMessage = "parameterJson exceeds MAX_PAYLOAD_BYTES",
                errorCode = McpErrorCode.PAYLOAD_TOO_LARGE,
            )
        }
        val correlationId = nextCorrelationId()
        val request = McpToolCallRequest(
            correlationId = correlationId,
            toolName = toolName,
            parameterJson = parameterJson,
        )
        val binder = binderLookup(daemonKey) ?: return McpToolCallResponse(
            correlationId = correlationId,
            toolName = toolName,
            errorMessage = "no daemon bound for ${daemonKey.packageName}",
            errorCode = McpErrorCode.TOOL_NOT_FOUND,
        )
        val daemon = IMcpDaemon.Stub.asInterface(binder) ?: return McpToolCallResponse(
            correlationId = correlationId,
            toolName = toolName,
            errorMessage = "binder for ${daemonKey.packageName} did not return IMcpDaemon",
            errorCode = McpErrorCode.TOOL_INTERNAL_ERROR,
        )
        val requestJson = try {
            McpEnvelopeCodec.encodeRequest(request)
        } catch (e: Throwable) {
            return McpToolCallResponse(
                correlationId = correlationId,
                toolName = toolName,
                errorMessage = "failed to encode request: ${e.message}",
                errorCode = McpErrorCode.TOOL_INTERNAL_ERROR,
            )
        }
        val responseJson = try {
            daemon.invoke(requestJson)
        } catch (e: DeadObjectException) {
            return McpToolCallResponse(
                correlationId = correlationId,
                toolName = toolName,
                errorMessage = "daemon ${daemonKey.packageName} binder died",
                errorCode = McpErrorCode.TOOL_INTERNAL_ERROR,
            )
        } catch (e: RemoteException) {
            return McpToolCallResponse(
                correlationId = correlationId,
                toolName = toolName,
                errorMessage = "RemoteException invoking ${daemonKey.packageName}: ${e.message}",
                errorCode = McpErrorCode.TOOL_INTERNAL_ERROR,
            )
        }
        if (responseJson.isNullOrBlank()) {
            return McpToolCallResponse(
                correlationId = correlationId,
                toolName = toolName,
                errorMessage = "daemon returned null/blank response",
                errorCode = McpErrorCode.TOOL_INTERNAL_ERROR,
            )
        }
        return try {
            McpEnvelopeCodec.decodeResponse(responseJson)
        } catch (e: Throwable) {
            McpToolCallResponse(
                correlationId = correlationId,
                toolName = toolName,
                errorMessage = "failed to decode response: ${e.message}",
                errorCode = McpErrorCode.TOOL_INTERNAL_ERROR,
            )
        }
    }

    override fun nextCorrelationId(): String = "mcp-android-${counter.getAndIncrement()}"

    companion object {
        private val counter = AtomicLong(1L)
    }
}
