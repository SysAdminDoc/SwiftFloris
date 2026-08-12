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

import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.mcp.McpDispatchRouter
import dev.patrickgold.florisboard.ime.mcp.McpClient
import dev.patrickgold.florisboard.ime.mcp.McpClientRegistry
import dev.patrickgold.florisboard.ime.mcp.DaemonKey
import dev.patrickgold.florisboard.ime.mcp.DisabledDaemonSet
import dev.patrickgold.florisboard.ime.mcp.DisabledToolSet
import dev.patrickgold.florisboard.ime.translate.TranslationRouter
import dev.patrickgold.florisboard.ime.translate.InlineTranslator
import dev.patrickgold.florisboard.ime.translate.InlineTranslatorRegistry
import dev.patrickgold.florisboard.ime.translate.LanguagePairDescriptor
import dev.patrickgold.florisboard.ime.translate.TranslationResult

/**
 * ROADMAP §10.5 N7.7 — unified opt-in addon hub.
 *
 * Single-shot façade for the NlpManager + smartbar UI to call into.
 * Owns the three Routers (smart-compose, translation, MCP) and
 * threads every invocation through [AddonInvocationAudit].
 *
 * The hub is intentionally **stateless across surfaces** — each
 * Router owns its own cache / breaker; the hub just dispatches.
 * That keeps the wire-up shallow: production code constructs one
 * hub per IME session, the NlpManager calls into it from three
 * places (ghost-text overlay, translate quick-action, MCP-driven
 * quick-action), and Settings → Privacy reads the audit log to
 * render the invocation list.
 *
 * Audit ties together with the routers' structured responses:
 *
 *  - `Response.Completed` / `Response.Translated` / `Result.Suggestion`
 *    → `Outcome.ACCEPTED` (no reason recorded).
 *  - `Response.Suppressed(reason)` → `Outcome.SUPPRESSED(reason)`.
 *  - `Response.Failed` / `Result.NoSuggestion` (after the router
 *    ran the addon and got nothing back) →
 *    `Outcome.FAILED(reason)`.
 */
class NlpAddonHub(
    private val smartCompose: SmartComposeRouter,
    private val translate: TranslationRouter,
    private val mcp: McpDispatchRouter,
    private val isSmartComposeConsentGranted: () -> Boolean,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    fun predict(
        context: SmartComposeContext,
        inputType: Int,
        imeOptions: Int,
        maxCandidates: Int = 3,
    ): SmartComposeResult {
        val result = smartCompose.predict(context, inputType, imeOptions, maxCandidates)
        recordSmartCompose(result, inputType, imeOptions)
        return result
    }

    suspend fun predictAsync(
        context: SmartComposeContext,
        inputType: Int,
        imeOptions: Int,
        maxCandidates: Int = 3,
    ): SmartComposeResult {
        val result = smartCompose.predictAsync(context, inputType, imeOptions, maxCandidates)
        recordSmartCompose(result, inputType, imeOptions)
        return result
    }

    fun translate(request: TranslationRouter.Request): TranslationRouter.Response {
        val response = translate.translate(request)
        recordTranslation(response)
        return response
    }

    fun callMcpTool(request: McpDispatchRouter.Request): McpDispatchRouter.Response {
        val response = mcp.dispatch(request)
        recordMcp(response)
        return response
    }

    private fun recordSmartCompose(
        result: SmartComposeResult,
        inputType: Int,
        imeOptions: Int,
    ) {
        val now = clock()
        when (result) {
            is SmartComposeResult.Suggestion -> AddonInvocationAudit.record(
                surface = AddonInvocationAudit.Surface.SMART_COMPOSE,
                outcome = AddonInvocationAudit.Outcome.ACCEPTED,
                timestampMillis = now,
            )
            is SmartComposeResult.NoSuggestion -> AddonInvocationAudit.record(
                surface = AddonInvocationAudit.Surface.SMART_COMPOSE,
                outcome = if (!isSmartComposeConsentGranted() || SensitiveFieldGuard.isSensitive(inputType, imeOptions)) {
                    AddonInvocationAudit.Outcome.SUPPRESSED
                } else {
                    AddonInvocationAudit.Outcome.FAILED
                },
                reason = when {
                    !isSmartComposeConsentGranted() -> "consent required"
                    else -> SensitiveFieldGuard.reasonFor(inputType, imeOptions)
                        ?: "no candidate above confidence threshold"
                },
                timestampMillis = now,
            )
        }
    }

    private fun recordTranslation(response: TranslationRouter.Response) {
        val now = clock()
        when (response) {
            is TranslationRouter.Response.Translated -> AddonInvocationAudit.record(
                surface = AddonInvocationAudit.Surface.TRANSLATION,
                outcome = AddonInvocationAudit.Outcome.ACCEPTED,
                timestampMillis = now,
            )
            is TranslationRouter.Response.Suppressed -> AddonInvocationAudit.record(
                surface = AddonInvocationAudit.Surface.TRANSLATION,
                outcome = AddonInvocationAudit.Outcome.SUPPRESSED,
                reason = response.reason.auditReason,
                timestampMillis = now,
            )
        }
    }

    private fun recordMcp(response: McpDispatchRouter.Response) {
        val now = clock()
        when (response) {
            is McpDispatchRouter.Response.Completed -> AddonInvocationAudit.record(
                surface = AddonInvocationAudit.Surface.MCP,
                outcome = AddonInvocationAudit.Outcome.ACCEPTED,
                timestampMillis = now,
            )
            is McpDispatchRouter.Response.Failed -> AddonInvocationAudit.record(
                surface = AddonInvocationAudit.Surface.MCP,
                outcome = AddonInvocationAudit.Outcome.FAILED,
                reason = response.callResponse.errorCode.name,
                timestampMillis = now,
            )
            is McpDispatchRouter.Response.Suppressed -> AddonInvocationAudit.record(
                surface = AddonInvocationAudit.Surface.MCP,
                outcome = AddonInvocationAudit.Outcome.SUPPRESSED,
                reason = response.reason,
                timestampMillis = now,
            )
        }
    }

    companion object {
        /**
         * Build the single production hub used by the NLP worker and quick
         * actions. Registry adapters are deliberately dynamic: addon
         * enrollment or lifecycle teardown can replace an active provider
         * without leaving a router pointed at a stale instance.
         */
        fun production(): NlpAddonHub {
            val prefs by FlorisPreferenceStore
            val smartComposeProvider = object : SmartComposeProvider {
                private val active: SmartComposeProvider
                    get() = SmartComposeProviderRegistry.active

                override fun predictNextTokens(
                    context: SmartComposeContext,
                    maxCandidates: Int,
                ): SmartComposeResult = active.predictNextTokens(context, maxCandidates)

                override suspend fun predictNextTokensAsync(
                    context: SmartComposeContext,
                    maxCandidates: Int,
                ): SmartComposeResult = active.predictNextTokensAsync(context, maxCandidates)

                override fun isReady(locale: String): Boolean = active.isReady(locale)
                override val activeModel: LiteRtModelDescriptor?
                    get() = active.activeModel
                override val supportedLocales: Set<String>
                    get() = active.supportedLocales
            }
            val translator = object : InlineTranslator {
                private val active: InlineTranslator
                    get() = InlineTranslatorRegistry.active

                override fun translate(
                    sourceText: String,
                    sourceLocale: String,
                    targetLocale: String,
                ): TranslationResult = active.translate(sourceText, sourceLocale, targetLocale)

                override fun isLanguagePairReady(sourceLocale: String, targetLocale: String): Boolean =
                    active.isLanguagePairReady(sourceLocale, targetLocale)

                override val installedPairs: Set<LanguagePairDescriptor>
                    get() = active.installedPairs
            }
            val mcpClient = object : McpClient {
                private val active: McpClient
                    get() = McpClientRegistry.active()

                override fun callTool(
                    daemonKey: DaemonKey,
                    toolName: String,
                    parameterJson: String,
                    timeoutMillis: Long,
                ) = active.callTool(daemonKey, toolName, parameterJson, timeoutMillis)

                override fun nextCorrelationId(): String = active.nextCorrelationId()
            }
            val smartComposeConsent = {
                prefs.privacy.smartComposeConsent.get().allowsInvocation()
            }
            return NlpAddonHub(
                smartCompose = SmartComposeRouter(
                    provider = smartComposeProvider,
                    isConsentGranted = smartComposeConsent,
                ),
                translate = TranslationRouter(
                    translator = translator,
                    packManager = TranslationRouter.PackManagerView.from(),
                    isConsentGranted = {
                        prefs.privacy.translationConsent.get().allowsInvocation()
                    },
                ),
                mcp = McpDispatchRouter(
                    client = mcpClient,
                    registryView = McpDispatchRouter.RegistryView.from(),
                    isDaemonDisabled = { daemon ->
                        DisabledDaemonSet.contains(
                            prefs.mcp.disabledDaemonPackages.get(),
                            daemon.packageName,
                        )
                    },
                    isToolDisabled = { daemon, tool ->
                        DisabledToolSet.contains(
                            prefs.mcp.disabledTools.get(),
                            daemon.packageName,
                            tool,
                        )
                    },
                    isConsentGranted = {
                        prefs.privacy.mcpConsent.get().allowsInvocation()
                    },
                ),
                isSmartComposeConsentGranted = smartComposeConsent,
            )
        }
    }
}
