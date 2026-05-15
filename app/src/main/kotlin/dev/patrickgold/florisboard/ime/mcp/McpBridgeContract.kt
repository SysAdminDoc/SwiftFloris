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

/**
 * ROADMAP §7 L7 — local-only MCP (Model Context Protocol) bridge contract.
 *
 * Deskdrop's [STD-DESKDROP] pattern of letting IME-driven tools call a
 * local MCP daemon is increasingly the way smart-compose engines reach
 * structured tools (calendar, contacts, clipboard) without sending text
 * to the cloud. SwiftFloris carries the §1 no-network promise, so any
 * MCP server we talk to must be hosted *on-device* (a sibling app the
 * user opted into installing) and reached via Android's
 * `bindService` + AIDL transport — never a network socket.
 *
 * This scaffold defines:
 *  - [McpBridgeContract] — the Intent + AIDL action constants that an
 *    on-device MCP daemon must advertise to be discoverable by the IME.
 *  - [McpToolDescriptor] — the serialized shape of one tool exposed by
 *    a daemon (name, description, parameter schema).
 *  - [McpToolResult] — the cross-package result envelope.
 *
 * The actual AIDL interface, the discovery (via PackageManager + addon
 * enumerator), and the IME-side `McpClient` ship in L7.1–L7.4. This
 * commit lands the contract so the daemon side can be built and tested
 * independently from the keyboard.
 */
object McpBridgeContract {

    /** Intent action a local MCP daemon must declare in its
     *  AndroidManifest `<intent-filter>` to be discoverable. */
    const val ACTION_BIND_MCP_DAEMON: String =
        "dev.patrickgold.florisboard.action.BIND_MCP_DAEMON"

    /** Permission the IME holds; daemons should require it so random
     *  apps can't bind. Signature-protected like the existing
     *  REGISTER_ADDON permission. */
    const val PERMISSION_BIND_MCP: String =
        "dev.patrickgold.florisboard.permission.BIND_MCP"

    /** AndroidManifest `<meta-data>` key carrying the JSON descriptor
     *  resource id (an `R.raw.<name>` pointing to the tool catalog). */
    const val METADATA_TOOL_CATALOG: String =
        "dev.patrickgold.florisboard.mcp.tool_catalog"

    /** AndroidManifest `<meta-data>` key carrying the daemon's MCP
     *  protocol version (currently fixed at "1"). */
    const val METADATA_PROTOCOL_VERSION: String =
        "dev.patrickgold.florisboard.mcp.protocol_version"

    /** Highest protocol version this IME understands. */
    const val SUPPORTED_PROTOCOL_VERSION: Int = 1

    /** Hard cap on JSON payload size in either direction (4 MB). The
     *  daemon and the IME both enforce this on their side so a runaway
     *  tool doesn't blow up either process. */
    const val MAX_PAYLOAD_BYTES: Long = 4L * 1024 * 1024
}

/**
 * One tool exposed by an MCP daemon. Mirrors the upstream MCP spec
 * (https://modelcontextprotocol.io) for `tools/list` payload entries.
 *
 *  - [name] — stable identifier (e.g. `"calendar.next_event"`)
 *  - [description] — single sentence shown to the user
 *  - [parameterSchemaJson] — JSON Schema for the parameter object
 */
@Serializable
data class McpToolDescriptor(
    val name: String,
    val description: String,
    val parameterSchemaJson: String,
) {
    init {
        require(name.isNotBlank()) { "tool name must not be blank" }
        require(description.isNotBlank()) { "tool description must not be blank" }
        require(parameterSchemaJson.isNotBlank()) { "parameter schema must not be blank" }
    }
}

/**
 * Envelope returned by the daemon for a [McpClient] call. Either
 * `payload` (success) or `errorMessage` (failure) is non-null — not
 * both.
 */
@Serializable
data class McpToolResult(
    val toolName: String,
    val payloadJson: String? = null,
    val errorMessage: String? = null,
    val isError: Boolean = false,
) {
    init {
        require(toolName.isNotBlank()) { "toolName must not be blank" }
        if (isError) {
            require(!errorMessage.isNullOrBlank()) {
                "errorMessage must be set when isError is true"
            }
        } else {
            require(payloadJson != null) { "payloadJson must be set on success" }
        }
    }
}
