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
 * ROADMAP matrix #20 — end-to-end router for the local rewrite / tone surface.
 *
 * Sibling of [SmartComposeRouter] / `TranslationRouter` / `McpDispatchRouter`. Pipeline:
 *
 *  1. **Consent gate** (matrix #37). NEEDS_PROMPT / DENIED short-circuit to [Response.Suppressed] with the
 *     standard `"consent required"` reason. Consent is shared with the SmartCompose surface because both
 *     ride the same LiteRT-LM / Gemma addon — a user who consents to smart-compose should not have to
 *     consent to rewrite separately.
 *  2. **Sensitive-field guard** ([SensitiveFieldGuard]). Password / web-password / numeric-PIN fields + any
 *     field with `IME_FLAG_NO_PERSONALIZED_LEARNING` short-circuit with `"sensitive field"`.
 *  3. **Blank-input** check. A rewrite request with no source text returns [Response.Suppressed].
 *  4. **Provider readiness**. `provider.isReady(tone, sourceLanguageTag)` must return true.
 *  5. **Cache** (optional, on by default). Keyed by `(tone, languageTag, sourceText)` triple — same source +
 *     same tone request inside the cache window returns the prior rewrite.
 *  6. **Provider call**. The provider rewrites and returns one of [RewriteResult].
 *  7. **Translate provider result** into the router's [Response] envelope.
 *
 * The router is pure-Kotlin; production wires `RewriteProviderRegistry.active` and the consent lambda from
 * `prefs.privacy.smartComposeConsent`; tests inject fakes.
 */
class RewriteRouter(
    private val provider: RewriteProvider,
    private val bypassCache: Boolean = false,
    cacheCapacity: Int = DEFAULT_CACHE_CAPACITY,
    private val isConsentGranted: () -> Boolean = { true },
) {

    private val cache: LinkedHashMap<CacheKey, RewriteResult.Rewritten>? = if (bypassCache) null else {
        object : LinkedHashMap<CacheKey, RewriteResult.Rewritten>(cacheCapacity, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<CacheKey, RewriteResult.Rewritten>?): Boolean {
                return size > cacheCapacity
            }
        }
    }

    // accessOrder=true means get() structurally mutates the map (re-links to tail), so
    // concurrent get()/put()/clear() can corrupt the list or throw CME. Guard every
    // access, mirroring SmartComposeCache.
    private val cacheLock = Any()

    fun rewrite(request: RewriteRequest): Response {
        if (!isConsentGranted()) {
            return Response.Suppressed(reason = "consent required")
        }
        if (SensitiveFieldGuard.isSensitive(request.inputType, request.imeOptions)) {
            return Response.Suppressed(reason = "sensitive field")
        }
        // Ahead of the cache, not just ahead of the provider: a rewrite this
        // editor forbids must not be served from a hit an editor that allowed
        // it put there.
        if (!request.isWritingToolsEnabled) {
            return Response.Suppressed(reason = "editor disallows writing tools")
        }
        if (request.sourceText.isBlank()) {
            return Response.Suppressed(reason = "blank input")
        }
        if (!provider.isReady(request.tone, request.sourceLanguageTag)) {
            return Response.Suppressed(reason = "provider not ready for ${request.tone}/${request.sourceLanguageTag}")
        }

        val key = CacheKey(request.tone, request.sourceLanguageTag, request.sourceText)
        synchronized(cacheLock) { cache?.get(key) }?.let { hit ->
            return Response.Rewritten(hit.rewrittenText, hit.tone, fromCache = true)
        }

        return when (val result = provider.rewrite(request)) {
            is RewriteResult.Rewritten -> {
                synchronized(cacheLock) { cache?.put(key, result) }
                Response.Rewritten(result.rewrittenText, result.tone, fromCache = false)
            }
            is RewriteResult.Unavailable -> Response.Suppressed(reason = result.reason)
            is RewriteResult.Failed -> Response.Failed(reason = result.reason)
        }
    }

    /** Drop the LRU cache (e.g. on language switch). */
    fun clearCache() {
        synchronized(cacheLock) { cache?.clear() }
    }

    /** Caller-facing result envelope. */
    sealed class Response {
        data class Rewritten(
            val rewrittenText: String,
            val tone: RewriteTone,
            val fromCache: Boolean,
        ) : Response()

        data class Suppressed(val reason: String) : Response()
        data class Failed(val reason: String) : Response()
    }

    private data class CacheKey(
        val tone: RewriteTone,
        val sourceLanguageTag: String,
        val sourceText: String,
    )

    companion object {
        const val DEFAULT_CACHE_CAPACITY: Int = 64
    }
}
