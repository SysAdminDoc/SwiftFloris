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

package dev.patrickgold.florisboard.ime.translate

/**
 * ROADMAP §10.5 L2.1b — LRU cache wrapping an [InlineTranslator].
 *
 * Bergamot inference for short conversation strings is fast (~10 ms
 * per token on a Pixel 6) but not free, and the IME translator
 * surface re-asks for the same phrases constantly — "thank you",
 * "see you tomorrow", "where is the …" — as the user retypes or
 * tabs between drafts. A small per-pair LRU swallows the repeat
 * traffic without touching the model.
 *
 * Cache key is the `(sourceText, sourceLocale, targetLocale)` triple
 * — different target locales for the same source must store
 * separately, and different source locales for the same source
 * string can produce different translations (e.g. "no" in Catalan
 * versus Spanish).
 */
class TranslationCache(
    private val delegate: InlineTranslator,
    val capacity: Int = DEFAULT_CAPACITY,
) : InlineTranslator {

    init {
        require(capacity >= 1) { "capacity must be ≥ 1 (was $capacity)" }
    }

    private val cache: LinkedHashMap<String, TranslationResult> =
        object : LinkedHashMap<String, TranslationResult>(16, 0.75f, /* accessOrder= */ true) {
            override fun removeEldestEntry(
                eldest: Map.Entry<String, TranslationResult>,
            ): Boolean = size > capacity
        }

    private val cacheLock = Any()

    @Volatile
    var hits: Long = 0L
        private set

    @Volatile
    var misses: Long = 0L
        private set

    override fun translate(
        sourceText: String,
        sourceLocale: String,
        targetLocale: String,
    ): TranslationResult {
        val key = buildKey(sourceText, sourceLocale, targetLocale)
        synchronized(cacheLock) {
            cache[key]?.let {
                hits++
                return it
            }
        }
        val computed = delegate.translate(sourceText, sourceLocale, targetLocale)
        synchronized(cacheLock) {
            // Don't cache `Unavailable` — the delegate may flip to a
            // bound state any moment when an addon installs.
            if (computed is TranslationResult.Translated) {
                cache[key] = computed
            }
            misses++
        }
        return computed
    }

    override fun isLanguagePairReady(sourceLocale: String, targetLocale: String): Boolean =
        delegate.isLanguagePairReady(sourceLocale, targetLocale)

    override val installedPairs: Set<LanguagePairDescriptor>
        get() = delegate.installedPairs

    /** Drop all cached translations — used when the addon swap fires. */
    fun clear() = synchronized(cacheLock) {
        cache.clear()
        hits = 0L
        misses = 0L
    }

    fun size(): Int = synchronized(cacheLock) { cache.size }

    private fun buildKey(sourceText: String, sourceLocale: String, targetLocale: String): String =
        "$sourceLocale\u001E$targetLocale\u001F$sourceText"

    companion object {
        const val DEFAULT_CAPACITY: Int = 2_048
    }
}
