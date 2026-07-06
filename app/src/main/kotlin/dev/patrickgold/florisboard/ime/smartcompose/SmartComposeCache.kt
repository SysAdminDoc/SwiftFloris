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
 * ROADMAP §10.5 L1.1c — LRU cache wrapping a [SmartComposeProvider].
 *
 * Smart-compose ghost-text rendering re-runs on every keystroke, but
 * the user often pauses for a beat before accepting/rejecting the
 * suggestion. During that pause the IME's redraw loop re-asks the
 * provider for the same context — same `(precedingText,
 * composingPrefix, locale)` tuple. A small cache absorbs the repeat
 * traffic without touching the underlying LLM.
 *
 * Default capacity 512 — smart-compose contexts are larger than
 * KenLM n-gram histories (one preceding-text key can hold an entire
 * sentence) so we keep the cache tighter than `TranslationCache`'s
 * 2,048.
 *
 * Cache key includes locale + editor package because the same
 * context routed through a different per-app LoRA (L1.3) can
 * produce different continuations.
 */
class SmartComposeCache(
    private val delegate: SmartComposeProvider,
    val capacity: Int = DEFAULT_CAPACITY,
) : SmartComposeProvider {

    init {
        require(capacity >= 1) { "capacity must be ≥ 1 (was $capacity)" }
    }

    private val cache: LinkedHashMap<String, SmartComposeResult> =
        object : LinkedHashMap<String, SmartComposeResult>(16, 0.75f, /* accessOrder= */ true) {
            override fun removeEldestEntry(
                eldest: Map.Entry<String, SmartComposeResult>,
            ): Boolean = size > capacity
        }

    private val cacheLock = Any()

    @Volatile
    var hits: Long = 0L
        private set

    @Volatile
    var misses: Long = 0L
        private set

    override fun predictNextTokens(
        context: SmartComposeContext,
        maxCandidates: Int,
    ): SmartComposeResult {
        val key = buildKey(context, maxCandidates)
        synchronized(cacheLock) {
            cache[key]?.let {
                hits++
                return it
            }
        }
        val computed = delegate.predictNextTokens(context, maxCandidates)
        synchronized(cacheLock) {
            // Don't cache NoSuggestion — the underlying provider may
            // flip ready mid-session.
            if (computed is SmartComposeResult.Suggestion) {
                cache[key] = computed
            }
            misses++
        }
        return computed
    }

    override suspend fun predictNextTokensAsync(
        context: SmartComposeContext,
        maxCandidates: Int,
    ): SmartComposeResult {
        val key = buildKey(context, maxCandidates)
        synchronized(cacheLock) {
            cache[key]?.let {
                hits++
                return it
            }
        }
        val computed = delegate.predictNextTokensAsync(context, maxCandidates)
        synchronized(cacheLock) {
            // Don't cache NoSuggestion - the underlying provider may
            // flip ready mid-session.
            if (computed is SmartComposeResult.Suggestion) {
                cache[key] = computed
            }
            misses++
        }
        return computed
    }

    override fun isReady(locale: String): Boolean = delegate.isReady(locale)
    override val activeModel: LiteRtModelDescriptor? get() = delegate.activeModel
    override val supportedLocales: Set<String> get() = delegate.supportedLocales

    /** Drop all cached suggestions. */
    fun clear() = synchronized(cacheLock) {
        cache.clear()
        hits = 0L
        misses = 0L
    }

    fun size(): Int = synchronized(cacheLock) { cache.size }

    private fun buildKey(context: SmartComposeContext, maxCandidates: Int): String =
        "${context.locale}\u001E${context.editorPackageName ?: ""}\u001E$maxCandidates" +
            "\u001F${context.precedingText}\u001F${context.composingPrefix}"

    companion object {
        const val DEFAULT_CAPACITY: Int = 512
    }
}
