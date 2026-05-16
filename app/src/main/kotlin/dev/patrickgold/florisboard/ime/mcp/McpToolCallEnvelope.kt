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

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * ROADMAP §10.5 L7.3 — MCP tool-call request / response envelope.
 *
 * Sits one level above the AIDL transport (which lands in L7.4). The
 * envelope is the wire format the IME's `McpClient` serializes onto
 * the daemon-bound IPC stream:
 *
 *  - **`McpToolCallRequest`** — single tool invocation. Carries the
 *    daemon-scoped tool name, a JSON parameter object (validated by
 *    the receiving daemon against its declared
 *    `McpToolDescriptor.parameterSchemaJson`), and a client-generated
 *    correlation id so async responses can be matched back to the
 *    in-flight request.
 *  - **`McpToolCallResponse`** — the corresponding response envelope.
 *    Either `payloadJson` (success) or `errorMessage` + `errorCode`
 *    (failure), with the same correlation id echoed back.
 *
 * Both types are `@Serializable` so the IME can use the same JSON
 * codec on the wire that it uses for `McpToolDescriptor`.
 *
 * Hard size cap mirrors [McpBridgeContract.MAX_PAYLOAD_BYTES] — the
 * client enforces this before send so a runaway prompt never reaches
 * the daemon, and the daemon should enforce on its side before
 * unmarshalling.
 */
@Serializable
data class McpToolCallRequest(
    val correlationId: String,
    val toolName: String,
    val parameterJson: String,
) {
    init {
        require(correlationId.isNotBlank()) { "correlationId must not be blank" }
        require(toolName.isNotBlank()) { "toolName must not be blank" }
        require(parameterJson.isNotBlank()) { "parameterJson must not be blank" }
        require(parameterJson.length.toLong() <= McpBridgeContract.MAX_PAYLOAD_BYTES) {
            "parameterJson exceeds MAX_PAYLOAD_BYTES"
        }
    }
}

@Serializable
data class McpToolCallResponse(
    val correlationId: String,
    val toolName: String,
    val payloadJson: String? = null,
    val errorMessage: String? = null,
    val errorCode: McpErrorCode = McpErrorCode.OK,
) {
    val isError: Boolean get() = errorCode != McpErrorCode.OK

    init {
        require(correlationId.isNotBlank()) { "correlationId must not be blank" }
        require(toolName.isNotBlank()) { "toolName must not be blank" }
        if (errorCode == McpErrorCode.OK) {
            require(payloadJson != null) { "payloadJson must be set when errorCode is OK" }
        } else {
            require(!errorMessage.isNullOrBlank()) {
                "errorMessage must be set when errorCode is not OK"
            }
        }
    }
}

/**
 * Stable error codes for [McpToolCallResponse]. Numeric values are
 * stable on the wire — only append, never renumber, when the
 * protocol gains new failure modes.
 */
@Serializable
enum class McpErrorCode(val wireValue: Int) {
    OK(0),
    TOOL_NOT_FOUND(1),
    INVALID_PARAMETERS(2),
    TOOL_INTERNAL_ERROR(3),
    TIMEOUT(4),
    PAYLOAD_TOO_LARGE(5),
    PERMISSION_DENIED(6),
    UNKNOWN(99);

    companion object {
        fun fromWireValue(value: Int): McpErrorCode =
            entries.firstOrNull { it.wireValue == value } ?: UNKNOWN
    }
}

/** Shared JSON codec for envelope round-tripping. */
object McpEnvelopeCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encodeRequest(request: McpToolCallRequest): String =
        json.encodeToString(McpToolCallRequest.serializer(), request)

    fun decodeRequest(payload: String): McpToolCallRequest =
        json.decodeFromString(McpToolCallRequest.serializer(), payload)

    fun encodeResponse(response: McpToolCallResponse): String =
        json.encodeToString(McpToolCallResponse.serializer(), response)

    fun decodeResponse(payload: String): McpToolCallResponse =
        json.decodeFromString(McpToolCallResponse.serializer(), payload)
}
