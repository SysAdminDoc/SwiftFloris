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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private class CountingTranslator(
    private val table: Map<Triple<String, String, String>, TranslationResult>,
) : InlineTranslator {
    var calls: Int = 0
        private set
    override fun translate(
        sourceText: String,
        sourceLocale: String,
        targetLocale: String,
    ): TranslationResult {
        calls++
        return table[Triple(sourceText, sourceLocale, targetLocale)]
            ?: TranslationResult.Unavailable
    }
    override fun isLanguagePairReady(sourceLocale: String, targetLocale: String) = true
    override val installedPairs: Set<LanguagePairDescriptor> = emptySet()
}

class TranslationCacheTest : FunSpec({

    test("repeat translations hit the cache") {
        val under = CountingTranslator(
            mapOf(
                Triple("hello", "en", "es") to
                    TranslationResult.Translated("hola", 0.95f),
            ),
        )
        val cache = TranslationCache(under)
        cache.translate("hello", "en", "es")
        cache.translate("hello", "en", "es")
        cache.translate("hello", "en", "es")
        under.calls shouldBe 1
        cache.hits shouldBe 2
        cache.misses shouldBe 1
    }

    test("different source-target locale pairs cache separately") {
        val under = CountingTranslator(
            mapOf(
                Triple("no", "ca", "en") to TranslationResult.Translated("not", 0.9f),
                Triple("no", "es", "en") to TranslationResult.Translated("no", 0.9f),
            ),
        )
        val cache = TranslationCache(under)
        cache.translate("no", "ca", "en")
        cache.translate("no", "es", "en")
        under.calls shouldBe 2
        cache.size() shouldBe 2
    }

    test("Unavailable results are NOT cached") {
        val under = CountingTranslator(emptyMap())
        val cache = TranslationCache(under)
        cache.translate("ghost", "en", "es")
        cache.translate("ghost", "en", "es")
        // Both calls reach the delegate because Unavailable isn't cached.
        under.calls shouldBe 2
        cache.size() shouldBe 0
    }

    test("clear resets cache + counters") {
        val under = CountingTranslator(
            mapOf(
                Triple("a", "en", "es") to TranslationResult.Translated("x", 0.5f),
            ),
        )
        val cache = TranslationCache(under)
        cache.translate("a", "en", "es")
        cache.size() shouldBe 1
        cache.clear()
        cache.size() shouldBe 0
        cache.hits shouldBe 0
        cache.misses shouldBe 0
    }

    test("eviction kicks in at capacity") {
        val under = CountingTranslator(
            (0..9).associate { i ->
                Triple("text-$i", "en", "es") to TranslationResult.Translated("t-$i", 0.5f)
            },
        )
        val cache = TranslationCache(under, capacity = 3)
        repeat(5) { i ->
            cache.translate("text-$i", "en", "es")
        }
        cache.size() shouldBe 3
    }

    test("capacity must be ≥ 1") {
        var caught = false
        try {
            TranslationCache(CountingTranslator(emptyMap()), capacity = 0)
        } catch (_: IllegalArgumentException) {
            caught = true
        }
        caught shouldBe true
    }
})
