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
import dev.patrickgold.florisboard.ime.mcp.McpDispatchRouter
import dev.patrickgold.florisboard.ime.mcp.McpErrorCode
import dev.patrickgold.florisboard.ime.mcp.McpToolCallResponse
import dev.patrickgold.florisboard.ime.mcp.ResolvedTool
import dev.patrickgold.florisboard.ime.translate.InlineTranslator
import dev.patrickgold.florisboard.ime.translate.TranslationRouter
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking

private class HubSmartComposeProvider(
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

    override fun isReady(locale: String): Boolean = true
    override val activeModel: LiteRtModelDescriptor? = null
    override val supportedLocales: Set<String> = setOf("en")
}

private object EmptyHubMcpClient : McpClient {
    override fun callTool(
        daemonKey: DaemonKey,
        toolName: String,
        parameterJson: String,
        timeoutMillis: Long,
    ) = McpToolCallResponse(
        correlationId = "hub-test",
        toolName = toolName,
        errorMessage = "not used",
        errorCode = McpErrorCode.TOOL_NOT_FOUND,
    )

    override fun nextCorrelationId(): String = "hub-test"
}

private object EmptyHubRegistry : McpDispatchRouter.RegistryView {
    override fun findTool(daemonKey: DaemonKey, toolName: String): ResolvedTool? = null
    override fun findToolMatches(toolName: String): List<ResolvedTool> = emptyList()
}

class NlpAddonHubTest : FunSpec({

    fun context() = SmartComposeContext(
        precedingText = "hello ",
        composingPrefix = "",
        locale = "en",
    )

    fun hub(
        provider: SmartComposeProvider,
        isSmartComposeConsentGranted: () -> Boolean,
        isMcpConsentGranted: () -> Boolean = { true },
    ) = NlpAddonHub(
        smartCompose = SmartComposeRouter(
            provider = provider,
            isConsentGranted = isSmartComposeConsentGranted,
        ),
        translate = TranslationRouter(
            translator = InlineTranslator.Default,
            packManager = object : TranslationRouter.PackManagerView {
                override fun installedPairs() = emptyList<dev.patrickgold.florisboard.ime.translate.LanguagePairDescriptor>()
                override fun preferredTargetLocale(): String? = null
            },
            isConsentGranted = { true },
        ),
        mcp = McpDispatchRouter(
            client = EmptyHubMcpClient,
            registryView = EmptyHubRegistry,
            isDaemonDisabled = { false },
            isToolDisabled = { _, _ -> false },
            isConsentGranted = isMcpConsentGranted,
        ),
        isSmartComposeConsentGranted = isSmartComposeConsentGranted,
        clock = { 42L },
    )

    test("async production suggestion path records one audited result") {
        AddonInvocationAudit.resetForTest()
        val provider = HubSmartComposeProvider(
            SmartComposeResult.Suggestion(
                listOf(SmartComposeCandidate("world", 0.9f, 1)),
            ),
        )
        val hub = hub(provider, isSmartComposeConsentGranted = { true })

        val result = runBlocking {
            hub.predictAsync(context(), inputType = 0x01, imeOptions = 0, maxCandidates = 1)
        }

        (result is SmartComposeResult.Suggestion) shouldBe true
        provider.calls shouldBe 1
        val record = AddonInvocationAudit.snapshot().single()
        record.surface shouldBe AddonInvocationAudit.Surface.SMART_COMPOSE
        record.outcome shouldBe AddonInvocationAudit.Outcome.ACCEPTED
        record.timestampMillis shouldBe 42L
    }

    test("consent denial suppresses the provider and still records the denial") {
        AddonInvocationAudit.resetForTest()
        val provider = HubSmartComposeProvider(SmartComposeResult.NoSuggestion)
        val hub = hub(provider, isSmartComposeConsentGranted = { false })

        hub.predict(context(), inputType = 0x01, imeOptions = 0)

        provider.calls shouldBe 0
        val record = AddonInvocationAudit.snapshot().single()
        record.outcome shouldBe AddonInvocationAudit.Outcome.SUPPRESSED
        record.reason shouldBe "consent required"
    }

    test("MCP consent denial is audited before registry or client access") {
        AddonInvocationAudit.resetForTest()
        val hub = hub(
            provider = HubSmartComposeProvider(SmartComposeResult.NoSuggestion),
            isSmartComposeConsentGranted = { true },
            isMcpConsentGranted = { false },
        )

        hub.callMcpTool(
            McpDispatchRouter.Request(
                toolName = "calendar.next",
                parameterJson = "{}",
            ),
        )

        val record = AddonInvocationAudit.snapshot().single()
        record.surface shouldBe AddonInvocationAudit.Surface.MCP
        record.outcome shouldBe AddonInvocationAudit.Outcome.SUPPRESSED
        record.reason shouldBe "consent required"
    }
})
