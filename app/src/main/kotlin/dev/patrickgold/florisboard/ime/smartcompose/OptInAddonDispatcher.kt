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

package dev.patrickgold.florisboard.ime.smartcompose

import dev.patrickgold.florisboard.ime.mcp.DaemonKey
import dev.patrickgold.florisboard.ime.mcp.McpClient
import dev.patrickgold.florisboard.ime.mcp.McpErrorCode
import dev.patrickgold.florisboard.ime.mcp.McpToolCallResponse
import dev.patrickgold.florisboard.ime.translate.InlineTranslator
import dev.patrickgold.florisboard.ime.translate.TranslationResult

/**
 * ROADMAP §7 N7 + L1/L2/L7 — privacy-aware opt-in addon dispatcher.
 *
 * Single chokepoint the IME's typing pipeline calls into to invoke
 * any of the three opt-in addon surfaces (smart-compose, inline
 * translation, MCP tool call). Every entry point runs through
 * [SensitiveFieldGuard] first and short-circuits to a safe "no
 * result" answer when the field is sensitive.
 *
 * The dispatcher itself doesn't own the underlying providers — it
 * takes them as constructor arguments so production code can plug
 * in `SmartComposeProviderRegistry.active`, `InlineTranslatorRegistry.active`,
 * and `McpClientRegistry.active`, while tests can drive synthetic
 * providers without touching the registries.
 *
 * This is the load-bearing privacy-enforcement seam: smart-compose
 * suggestions, translations, and MCP tool calls **never** fire from
 * a password / PIN / no-learn field, regardless of what the
 * underlying provider would have returned.
 */
class OptInAddonDispatcher(
    private val smartCompose: SmartComposeProvider,
    private val translator: InlineTranslator,
    private val mcpClient: McpClient,
) {

    /** Smart-compose predict path, with field-guard suppression. */
    fun predictNextTokens(
        context: SmartComposeContext,
        inputType: Int,
        imeOptions: Int,
        maxCandidates: Int = 3,
    ): SmartComposeResult {
        if (SensitiveFieldGuard.isSensitive(inputType, imeOptions)) {
            return SmartComposeResult.NoSuggestion
        }
        return smartCompose.predictNextTokens(context, maxCandidates)
    }

    /** Inline-translation path, with field-guard suppression. */
    fun translate(
        sourceText: String,
        sourceLocale: String,
        targetLocale: String,
        inputType: Int,
        imeOptions: Int,
    ): TranslationResult {
        if (SensitiveFieldGuard.isSensitive(inputType, imeOptions)) {
            return TranslationResult.Unavailable
        }
        return translator.translate(sourceText, sourceLocale, targetLocale)
    }

    /** MCP tool-call path, with field-guard suppression. */
    fun callMcpTool(
        daemonKey: DaemonKey,
        toolName: String,
        parameterJson: String,
        inputType: Int,
        imeOptions: Int,
        timeoutMillis: Long = McpClient.DEFAULT_TIMEOUT_MILLIS,
    ): McpToolCallResponse {
        if (SensitiveFieldGuard.isSensitive(inputType, imeOptions)) {
            return McpToolCallResponse(
                correlationId = mcpClient.nextCorrelationId(),
                toolName = toolName,
                errorMessage = "field is sensitive — opt-in surface suppressed",
                errorCode = McpErrorCode.PERMISSION_DENIED,
            )
        }
        return mcpClient.callTool(daemonKey, toolName, parameterJson, timeoutMillis)
    }
}
