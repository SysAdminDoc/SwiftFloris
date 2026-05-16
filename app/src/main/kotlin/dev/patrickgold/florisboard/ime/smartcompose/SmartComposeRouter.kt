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
 * ROADMAP §10.5 L1.1f — smart-compose end-to-end router.
 *
 * Single composition point that the NlpManager's smart-compose path
 * calls into. Layers the pieces shipped across v1.8.x in the order
 * they need to run:
 *
 *   1. [SensitiveFieldGuard] — short-circuit to `NoSuggestion`
 *      when the field is sensitive (password / PIN / no-learn).
 *   2. [SmartComposeContextWindow] — truncate `precedingText` to a
 *      sentence-aware window before dispatch.
 *   3. [SmartComposeCache] — LRU cache check on the truncated
 *      context.
 *   4. Underlying provider — usually
 *      `SmartComposeProviderRegistry.active` in production.
 *   5. [SmartComposeResultFilter] — drop low-confidence / blank /
 *      duplicate candidates, normalise whitespace, sort by
 *      confidence, clamp to `maxCandidates`.
 *
 * The router takes its dependencies as constructor arguments so
 * production wires the registry, tests inject fakes. The
 * `SmartComposeCache` is created internally with default capacity
 * since it's per-router state; pass `bypassCache = true` (e.g. for
 * benchmarks) to skip it.
 */
class SmartComposeRouter(
    private val provider: SmartComposeProvider,
    private val maxContextChars: Int = SmartComposeContextWindow.DEFAULT_MAX_CHARS,
    private val minConfidence: Float = SmartComposeResultFilter.DEFAULT_MIN_CONFIDENCE,
    private val bypassCache: Boolean = false,
    cacheCapacity: Int = SmartComposeCache.DEFAULT_CAPACITY,
    private val isConsentGranted: () -> Boolean = { true },
) {
    private val cache: SmartComposeCache? = if (bypassCache) null else SmartComposeCache(
        delegate = provider,
        capacity = cacheCapacity,
    )

    /**
     * End-to-end predict path: consent → guard → truncate → cache → provider
     * → filter. Returns whatever the dispatcher should hand to the
     * ghost-text overlay.
     */
    fun predict(
        context: SmartComposeContext,
        inputType: Int,
        imeOptions: Int,
        maxCandidates: Int = 3,
    ): SmartComposeResult {
        // Matrix #37 — consent gate. NEEDS_PROMPT / DENIED short-circuit to NoSuggestion; the IME's UI layer
        // owns the consent-dialog flow that flips the pref to GRANTED on user accept.
        if (!isConsentGranted()) {
            return SmartComposeResult.NoSuggestion
        }
        if (SensitiveFieldGuard.isSensitive(inputType, imeOptions)) {
            return SmartComposeResult.NoSuggestion
        }
        val truncated = SmartComposeContextWindow.truncate(context, maxContextChars)
        val raw = if (cache != null) {
            cache.predictNextTokens(truncated, maxCandidates)
        } else {
            provider.predictNextTokens(truncated, maxCandidates)
        }
        return SmartComposeResultFilter.filter(
            input = raw,
            minConfidence = minConfidence,
            maxCandidates = maxCandidates,
        )
    }

    /** Drop the LRU cache (e.g. on language switch). */
    fun clearCache() {
        cache?.clear()
    }
}
