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

import dev.patrickgold.florisboard.ime.smartcompose.SensitiveFieldGuard

/**
 * ROADMAP §10.5 L7.6 — MCP dispatch end-to-end router.
 *
 * Third sibling of `SmartComposeRouter` + `TranslationRouter`.
 * Layers every v1.8.x MCP building block into one entry point that
 * the IME's quick-action surface calls when dispatching a tool by
 * name:
 *
 *   1. [SensitiveFieldGuard] — short-circuit on sensitive fields.
 *   2. [McpDaemonRegistry.findTool] — resolve the tool name to
 *      `(daemon, tool)` across every active daemon.
 *   3. Underlying [McpClient] — usually
 *      [McpClientRegistry.active], which itself may be wrapped by
 *      [McpTimeoutClient] in production.
 *
 * Unlike the other two routers this one doesn't own caches — MCP
 * tool calls are by definition side-effecting (calendar lookups,
 * contact searches, clipboard manipulation) so caching is
 * semantically wrong.  The timeout budget breaker
 * ([McpTimeoutClient]) is the analogous "don't run forever" guard.
 *
 * API mirrors the other routers' `Request` / `Response` shape so
 * the NlpManager dispatch surface treats all three identically.
 */
class McpDispatchRouter(
    private val client: McpClient,
    private val registryView: RegistryView = RegistryView.from(),
    private val isDaemonDisabled: (DaemonKey) -> Boolean = { false },
    private val isToolDisabled: (DaemonKey, String) -> Boolean = { _, _ -> false },
    private val isConsentGranted: () -> Boolean = { true },
) {

    fun dispatch(request: Request): Response {
        // Matrix #37 — consent gate. NEEDS_PROMPT / DENIED short-circuits with the "consent required" reason
        // so the IME's UI layer can drive the consent-dialog flow before any tool ever fires.
        if (!isConsentGranted()) {
            return Response.Suppressed(reason = "consent required")
        }
        if (SensitiveFieldGuard.isSensitive(request.inputType, request.imeOptions)) {
            return Response.Suppressed(reason = "sensitive field")
        }
        if (request.toolName.isBlank()) {
            return Response.Suppressed(reason = "blank tool name")
        }
        if (request.parameterJson.length.toLong() > McpBridgeContract.MAX_PAYLOAD_BYTES) {
            return Response.Suppressed(reason = "parameterJson exceeds MAX_PAYLOAD_BYTES")
        }
        val resolved = registryView.findTool(request.toolName)
            ?: return Response.Suppressed(reason = "tool ${request.toolName} not registered")
        if (isDaemonDisabled(resolved.daemon)) {
            return Response.Suppressed(
                reason = "daemon ${resolved.daemon.packageName} disabled by user",
            )
        }
        if (isToolDisabled(resolved.daemon, request.toolName)) {
            return Response.Suppressed(
                reason = "tool ${request.toolName} on daemon ${resolved.daemon.packageName} disabled by user",
            )
        }
        val callResp = client.callTool(
            daemonKey = resolved.daemon,
            toolName = request.toolName,
            parameterJson = request.parameterJson,
            timeoutMillis = request.timeoutMillis,
        )
        return if (callResp.isError) {
            Response.Failed(callResp)
        } else {
            Response.Completed(callResp, daemon = resolved.daemon)
        }
    }

    /** Caller-facing input. */
    data class Request(
        val toolName: String,
        val parameterJson: String,
        val inputType: Int = 0x01,
        val imeOptions: Int = 0,
        val timeoutMillis: Long = McpClient.DEFAULT_TIMEOUT_MILLIS,
    )

    /** Caller-facing output. */
    sealed class Response {
        data class Completed(
            val callResponse: McpToolCallResponse,
            val daemon: DaemonKey,
        ) : Response()

        data class Failed(val callResponse: McpToolCallResponse) : Response()
        data class Suppressed(val reason: String) : Response()
    }

    /** View over [McpDaemonRegistry] for test injection. */
    interface RegistryView {
        fun findTool(toolName: String): ResolvedTool?
        companion object {
            fun from(): RegistryView = object : RegistryView {
                override fun findTool(toolName: String): ResolvedTool? =
                    McpDaemonRegistry.findTool(toolName)
            }
        }
    }
}
