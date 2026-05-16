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

class McpToolCallEnvelopeTest : FunSpec({

    test("request round-trips through the codec") {
        val req = McpToolCallRequest(
            correlationId = "abc-123",
            toolName = "calendar.next",
            parameterJson = "{\"limit\":3}",
        )
        val encoded = McpEnvelopeCodec.encodeRequest(req)
        val decoded = McpEnvelopeCodec.decodeRequest(encoded)
        decoded shouldBe req
    }

    test("success response round-trips through the codec") {
        val resp = McpToolCallResponse(
            correlationId = "abc-123",
            toolName = "calendar.next",
            payloadJson = "{\"events\":[]}",
            errorCode = McpErrorCode.OK,
        )
        val encoded = McpEnvelopeCodec.encodeResponse(resp)
        val decoded = McpEnvelopeCodec.decodeResponse(encoded)
        decoded shouldBe resp
        decoded.isError shouldBe false
    }

    test("error response round-trips and isError flips") {
        val resp = McpToolCallResponse(
            correlationId = "abc-456",
            toolName = "calendar.next",
            errorMessage = "no calendar access",
            errorCode = McpErrorCode.PERMISSION_DENIED,
        )
        val encoded = McpEnvelopeCodec.encodeResponse(resp)
        val decoded = McpEnvelopeCodec.decodeResponse(encoded)
        decoded shouldBe resp
        decoded.isError shouldBe true
    }

    test("error response requires a non-blank errorMessage") {
        var caught = false
        try {
            McpToolCallResponse(
                correlationId = "abc",
                toolName = "t",
                errorMessage = null,
                errorCode = McpErrorCode.TOOL_NOT_FOUND,
            )
        } catch (_: IllegalArgumentException) {
            caught = true
        }
        caught shouldBe true
    }

    test("success response requires payloadJson") {
        var caught = false
        try {
            McpToolCallResponse(
                correlationId = "abc",
                toolName = "t",
                payloadJson = null,
                errorCode = McpErrorCode.OK,
            )
        } catch (_: IllegalArgumentException) {
            caught = true
        }
        caught shouldBe true
    }

    test("request rejects blank correlationId") {
        var caught = false
        try {
            McpToolCallRequest(
                correlationId = "",
                toolName = "t",
                parameterJson = "{}",
            )
        } catch (_: IllegalArgumentException) {
            caught = true
        }
        caught shouldBe true
    }

    test("error code wire values are stable") {
        McpErrorCode.OK.wireValue shouldBe 0
        McpErrorCode.TOOL_NOT_FOUND.wireValue shouldBe 1
        McpErrorCode.PERMISSION_DENIED.wireValue shouldBe 6
        McpErrorCode.fromWireValue(1) shouldBe McpErrorCode.TOOL_NOT_FOUND
        McpErrorCode.fromWireValue(99) shouldBe McpErrorCode.UNKNOWN
        McpErrorCode.fromWireValue(404) shouldBe McpErrorCode.UNKNOWN
    }
})
