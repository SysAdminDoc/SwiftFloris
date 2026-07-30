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

import kotlinx.serialization.Serializable

/**
 * ROADMAP §7 L1 — on-device LLM smart-compose facade (Gboard Smart
 * Compose / Apple QuickType inline ghost-text pattern).
 *
 * SwiftFloris' `:app` module deliberately does *not* take a direct
 * dependency on Google's LiteRT-LM runtime (the orchestration layer
 * Gemini Nano uses on Chrome / Pixel Watch). LiteRT-LM brings a
 * multi-MB native runtime + GPU/NPU/CPU backend dispatch + KV-cache
 * management — too large to embed in the base APK. No LiteRT-LM
 * runtime addon currently ships. Production registers the opt-in local
 * [HeuristicSmartComposeProvider]; this interface preserves a narrow
 * boundary for a future model-backed implementation without claiming
 * that one is delivered.
 *
 * The facade exposes the minimal surface the keyboard needs:
 *  - [predictNextTokens]  — given the typing context, return ranked
 *    next-token continuations (ghost-text candidates).
 *  - [isReady]            — whether a model is loaded for [locale].
 *  - [activeModel]        — descriptor of the bound model (UI surface).
 *  - [supportedLocales]   — locales the bound runtime handles.
 *
 * Default behaviour: [Default] returns `NoSuggestion` so the IME falls
 * back to the existing N12.x bigram/trigram chain and the Next-3.x
 * Zipf-overlay ranker; nothing in the typing pipeline breaks when no
 * smart-compose addon is installed.
 */
interface SmartComposeProvider {
    fun predictNextTokens(
        context: SmartComposeContext,
        maxCandidates: Int = 3,
    ): SmartComposeResult

    /**
     * Suspend-aware prediction path for providers backed by local storage or
     * addon IPC. The synchronous method stays as the compatibility surface for
     * existing tests/addons; production coroutine callers should prefer this
     * method so they do not block an IME worker thread.
     */
    suspend fun predictNextTokensAsync(
        context: SmartComposeContext,
        maxCandidates: Int = 3,
    ): SmartComposeResult = predictNextTokens(context, maxCandidates)

    fun isReady(locale: String): Boolean
    val activeModel: LiteRtModelDescriptor?
    val supportedLocales: Set<String>

    object Default : SmartComposeProvider {
        override fun predictNextTokens(context: SmartComposeContext, maxCandidates: Int) =
            SmartComposeResult.NoSuggestion

        override fun isReady(locale: String): Boolean = false
        override val activeModel: LiteRtModelDescriptor? = null
        override val supportedLocales: Set<String> = emptySet()
    }
}

/**
 * Typing context handed to the smart-compose provider on every keystroke.
 * Carries the **preceding** text (already-committed words on the line) and
 * the **composing** prefix (partial word currently in the buffer), plus
 * the package name of the focused editor so a per-app LoRA hot-swap
 * (L1.3) can pick the right adapter.
 */
data class SmartComposeContext(
    val precedingText: String,
    val composingPrefix: String,
    val locale: String,
    val editorPackageName: String? = null,
    val maxTokens: Int = 8,
) {
    init {
        require(locale.isNotBlank()) { "locale must not be blank" }
        require(maxTokens in 1..64) { "maxTokens must be in 1..64; was $maxTokens" }
    }
}

/**
 * Descriptor contract for a potential loaded model. A future
 * model-backed provider can expose name, quantisation, and size without
 * making the IME parse its binary format.
 */
@Serializable
data class LiteRtModelDescriptor(
    val name: String,
    /** e.g. "gemma-3-1b-it-q4_k_m". */
    val modelId: String,
    /** Preferred backend at load time: "cpu" | "gpu" | "npu" | "auto". */
    val preferredBackend: String,
    /** ISO 639-1 locales the model is competent in. */
    val supportedLocales: List<String>,
    /** On-disk size of the model bundle in bytes. */
    val sizeBytes: Long,
    /** Quantisation tag: "int4" | "int8" | "fp16" | "bf16". */
    val quantization: String,
    /** Whether this descriptor declares LoRA hot-swap support (L1.3). */
    val supportsLora: Boolean = false,
) {
    init {
        require(name.isNotBlank()) { "name must not be blank" }
        require(modelId.isNotBlank()) { "modelId must not be blank" }
        require(preferredBackend in setOf("cpu", "gpu", "npu", "auto")) {
            "preferredBackend must be cpu / gpu / npu / auto; was $preferredBackend"
        }
        require(supportedLocales.isNotEmpty()) {
            "supportedLocales must list at least one locale"
        }
        require(sizeBytes >= 0) { "sizeBytes must be non-negative" }
        require(quantization in setOf("int4", "int8", "fp16", "bf16")) {
            "quantization must be int4 / int8 / fp16 / bf16; was $quantization"
        }
    }
}

/**
 * Smart-compose result. [Suggestion] carries ranked ghost-text
 * continuations; [NoSuggestion] is the "I'm not confident enough" / "no
 * model loaded" answer — distinct from `Suggestion(emptyList())` because
 * the IME treats them differently (no-suggestion suppresses the gray
 * ghost-text overlay entirely, while empty candidates blanks the
 * overlay but keeps the strip active).
 */
sealed class SmartComposeResult {
    object NoSuggestion : SmartComposeResult()
    data class Suggestion(val candidates: List<SmartComposeCandidate>) : SmartComposeResult()
}

data class SmartComposeCandidate(
    val text: String,
    val confidence: Float,
    /** Tokens this candidate represents (for tap-to-accept granularity). */
    val tokenCount: Int = text.split(' ').size,
) {
    init {
        require(text.isNotEmpty()) { "smart-compose candidate text must not be empty" }
        require(confidence in 0f..1f) { "confidence must be in [0, 1]; was $confidence" }
        require(tokenCount in 1..32) { "tokenCount must be in 1..32; was $tokenCount" }
    }
}

/**
 * Process-wide provider registry. It starts with
 * [SmartComposeProvider.Default]; application startup replaces that
 * with the opt-in heuristic provider. No model-backed provider ships.
 */
object SmartComposeProviderRegistry {

    @Volatile
    private var current: SmartComposeProvider = SmartComposeProvider.Default

    val active: SmartComposeProvider
        get() = current

    fun setActive(provider: SmartComposeProvider) {
        current = provider
    }

    fun reset() {
        current = SmartComposeProvider.Default
    }
}
