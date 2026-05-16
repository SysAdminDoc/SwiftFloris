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
import dev.patrickgold.florisboard.ime.translate.LanguagePairDescriptor
import dev.patrickgold.florisboard.ime.translate.TranslationResult
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private class FixedSmartCompose(
    private val response: SmartComposeResult,
) : SmartComposeProvider {
    var calls: Int = 0
        private set
    override fun predictNextTokens(
        context: SmartComposeContext,
        maxCandidates: Int,
    ): SmartComposeResult {
        calls++
        return response
    }
    override fun isReady(locale: String) = true
    override val activeModel: LiteRtModelDescriptor? = null
    override val supportedLocales: Set<String> = setOf("en")
}

private class FixedTranslator(
    private val response: TranslationResult,
) : InlineTranslator {
    var calls: Int = 0
        private set
    override fun translate(
        sourceText: String,
        sourceLocale: String,
        targetLocale: String,
    ): TranslationResult {
        calls++
        return response
    }
    override fun isLanguagePairReady(sourceLocale: String, targetLocale: String) = true
    override val installedPairs: Set<LanguagePairDescriptor> = emptySet()
}

private class CountingMcp : McpClient {
    var calls: Int = 0
        private set
    override fun callTool(
        daemonKey: DaemonKey,
        toolName: String,
        parameterJson: String,
        timeoutMillis: Long,
    ): McpToolCallResponse {
        calls++
        return McpToolCallResponse(
            correlationId = "stub-$calls",
            toolName = toolName,
            payloadJson = """{"ok":true}""",
            errorCode = McpErrorCode.OK,
        )
    }
    override fun nextCorrelationId(): String = "stub-${calls + 1}"
}

class OptInAddonDispatcherTest : FunSpec({

    val ctx = SmartComposeContext(
        precedingText = "hello",
        composingPrefix = "",
        locale = "en",
    )
    val daemon = DaemonKey("com.example.mcp", "com.example.mcp.Daemon")
    // Plain TEXT class field (inputType = 0x01), no special flags.
    val plainInputType = 0x01
    val plainImeOptions = 0
    // TEXT password (inputType = 0x81).
    val passwordInputType = 0x81

    test("smart-compose suppressed on a password field, NoSuggestion returned") {
        val sc = FixedSmartCompose(SmartComposeResult.Suggestion(
            listOf(SmartComposeCandidate("world", 0.9f, 1)),
        ))
        val dispatcher = OptInAddonDispatcher(sc, FixedTranslator(TranslationResult.Unavailable), CountingMcp())
        val result = dispatcher.predictNextTokens(
            context = ctx,
            inputType = passwordInputType,
            imeOptions = plainImeOptions,
        )
        result shouldBe SmartComposeResult.NoSuggestion
        sc.calls shouldBe 0
    }

    test("smart-compose forwarded on a plain TEXT field") {
        val sc = FixedSmartCompose(SmartComposeResult.Suggestion(
            listOf(SmartComposeCandidate("world", 0.9f, 1)),
        ))
        val dispatcher = OptInAddonDispatcher(sc, FixedTranslator(TranslationResult.Unavailable), CountingMcp())
        dispatcher.predictNextTokens(
            context = ctx,
            inputType = plainInputType,
            imeOptions = plainImeOptions,
        )
        sc.calls shouldBe 1
    }

    test("translation suppressed on a password field, Unavailable returned") {
        val tr = FixedTranslator(TranslationResult.Translated("hola", 0.9f))
        val dispatcher = OptInAddonDispatcher(FixedSmartCompose(SmartComposeResult.NoSuggestion), tr, CountingMcp())
        val result = dispatcher.translate(
            sourceText = "hello",
            sourceLocale = "en",
            targetLocale = "es",
            inputType = passwordInputType,
            imeOptions = plainImeOptions,
        )
        result shouldBe TranslationResult.Unavailable
        tr.calls shouldBe 0
    }

    test("translation forwarded on a plain TEXT field") {
        val tr = FixedTranslator(TranslationResult.Translated("hola", 0.9f))
        val dispatcher = OptInAddonDispatcher(FixedSmartCompose(SmartComposeResult.NoSuggestion), tr, CountingMcp())
        val result = dispatcher.translate(
            sourceText = "hello",
            sourceLocale = "en",
            targetLocale = "es",
            inputType = plainInputType,
            imeOptions = plainImeOptions,
        )
        result shouldBe TranslationResult.Translated("hola", 0.9f)
        tr.calls shouldBe 1
    }

    test("MCP tool call suppressed on a password field, PERMISSION_DENIED returned") {
        val mcp = CountingMcp()
        val dispatcher = OptInAddonDispatcher(
            FixedSmartCompose(SmartComposeResult.NoSuggestion),
            FixedTranslator(TranslationResult.Unavailable),
            mcp,
        )
        val response = dispatcher.callMcpTool(
            daemonKey = daemon,
            toolName = "tool",
            parameterJson = "{}",
            inputType = passwordInputType,
            imeOptions = plainImeOptions,
        )
        response.errorCode shouldBe McpErrorCode.PERMISSION_DENIED
        mcp.calls shouldBe 0
    }

    test("IME_FLAG_NO_PERSONALIZED_LEARNING suppresses everything") {
        val sc = FixedSmartCompose(SmartComposeResult.Suggestion(
            listOf(SmartComposeCandidate("world", 0.9f, 1)),
        ))
        val tr = FixedTranslator(TranslationResult.Translated("x", 0.5f))
        val mcp = CountingMcp()
        val dispatcher = OptInAddonDispatcher(sc, tr, mcp)
        dispatcher.predictNextTokens(ctx, plainInputType, imeOptions = 0x01000000)
        dispatcher.translate("a", "en", "es", plainInputType, imeOptions = 0x01000000)
        dispatcher.callMcpTool(daemon, "t", "{}", plainInputType, imeOptions = 0x01000000)
        sc.calls shouldBe 0
        tr.calls shouldBe 0
        mcp.calls shouldBe 0
    }
})
