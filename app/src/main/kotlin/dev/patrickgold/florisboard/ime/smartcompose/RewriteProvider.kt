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

/**
 * ROADMAP matrix #20 — Samsung-style rewrite / tone actions via a local provider.
 *
 * Commercial keyboards (Galaxy AI Writing Assist, Gboard AI rewrite, SwiftKey rewrite-with-Copilot) ship
 * "Rewrite as: Formal / Casual / Shorter / Longer / Summarize" as a flagship surface. SwiftFloris exposes the
 * same surface as a smartbar quick action, but routes the rewrite call through a **local-only** provider so
 * the user's selected text never leaves the device. The provider plug-in target is the same LiteRT-LM /
 * Gemma-3 / Phi-4 stack that the smart-compose provider targets (L1.1a); until that addon ships,
 * [NoOpRewriteProvider] returns [RewriteResult.Unavailable] and the quick action falls back to a "no rewriter
 * installed — install the SwiftFloris Compose addon" affordance.
 *
 * The contract here is intentionally similar in shape to [SmartComposeProvider] so addon implementations can
 * reuse infrastructure. The router (`RewriteRouter`) layers consent + sensitive-field guard + cache on top.
 */
interface RewriteProvider {

    /**
     * @return `true` when this provider can rewrite into [tone] for the given source language.
     *  An addon that supports the full LiteRT-LM tone set returns true for every [RewriteTone] / locale
     *  combination it has weights loaded for; the [NoOpRewriteProvider] always returns false.
     */
    fun isReady(tone: RewriteTone, sourceLanguageTag: String): Boolean

    /**
     * Rewrite [request].sourceText in the requested [request].tone. The provider is expected to be safe to
     * call from any thread; the router serialises calls per-request to keep dispatch deterministic.
     */
    fun rewrite(request: RewriteRequest): RewriteResult
}

/** Tone presets the IME exposes as quick-action variants. Mirrors the Samsung Galaxy AI Writing Assist set. */
enum class RewriteTone(val displayKey: String) {
    /** Tighten language, drop colloquialisms, fix spelling-by-ear ("kinda" → "somewhat"). */
    FORMAL("formal"),
    /** Loosen language, conversational tone ("we appreciate" → "thanks"). */
    CASUAL("casual"),
    /** Shorten while preserving meaning. */
    SHORTER("shorter"),
    /** Expand with more detail / context. */
    LONGER("longer"),
    /** Single-sentence summary of the source. */
    SUMMARIZE("summarize"),
}

/**
 * Caller-facing request payload. The router populates [inputType] / [imeOptions] from the active editor so
 * sensitive fields are short-circuited before this request reaches the provider.
 */
data class RewriteRequest(
    val sourceText: String,
    val tone: RewriteTone,
    val sourceLanguageTag: String = "en",
    val inputType: Int = 0x01,
    val imeOptions: Int = 0,
    val maxRewrittenChars: Int = 4096,
    /**
     * What the editor said about generative text replacement, from
     * `EditorInfo.isWritingToolsEnabled()`.
     *
     * Android 16 lets an editor forbid this outright, and an editor that says
     * no has said no to a rewrite regardless of what the user consented to for
     * the keyboard as a whole. Defaults to `true` so callers below API 36, and
     * tests that do not care, keep the previous behaviour.
     */
    val isWritingToolsEnabled: Boolean = true,
)

/** Caller-facing result. */
sealed class RewriteResult {
    /** Provider rewrote the source text. [rewrittenText] is what the IME commits in place of the selection. */
    data class Rewritten(val rewrittenText: String, val tone: RewriteTone) : RewriteResult()
    /** Provider cannot serve this request (no model loaded, unsupported tone, unsupported language, etc.). */
    data class Unavailable(val reason: String) : RewriteResult()
    /** Provider attempted the rewrite and reported an error. */
    data class Failed(val reason: String) : RewriteResult()
}

/**
 * Default no-op provider. Always returns [RewriteResult.Unavailable] so the smartbar quick action surfaces the
 * "install the SwiftFloris Compose addon" Toast on tap until a real provider is enrolled via
 * [RewriteProviderRegistry.setActive].
 */
object NoOpRewriteProvider : RewriteProvider {
    override fun isReady(tone: RewriteTone, sourceLanguageTag: String): Boolean = false

    override fun rewrite(request: RewriteRequest): RewriteResult {
        return RewriteResult.Unavailable(reason = "no rewrite provider installed")
    }
}

/**
 * Process-wide registry, mirrors [SmartComposeProviderRegistry]. The addon enumerator installs the active
 * provider on enrolment; until a rewrite addon is installed, [active] is [NoOpRewriteProvider].
 */
object RewriteProviderRegistry {

    @Volatile
    private var current: RewriteProvider = NoOpRewriteProvider

    val active: RewriteProvider
        get() = current

    fun setActive(provider: RewriteProvider) {
        current = provider
    }

    fun reset() {
        current = NoOpRewriteProvider
    }
}
