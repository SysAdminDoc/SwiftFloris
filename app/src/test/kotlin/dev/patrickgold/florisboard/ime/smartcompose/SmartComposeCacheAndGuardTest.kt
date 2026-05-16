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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private class CountingProvider(
    private val table: Map<String, SmartComposeResult>,
) : SmartComposeProvider {
    var calls: Int = 0
        private set
    override fun predictNextTokens(
        context: SmartComposeContext,
        maxCandidates: Int,
    ): SmartComposeResult {
        calls++
        return table[context.precedingText + "|" + context.composingPrefix]
            ?: SmartComposeResult.NoSuggestion
    }
    override fun isReady(locale: String) = true
    override val activeModel: LiteRtModelDescriptor? = null
    override val supportedLocales: Set<String> = setOf("en")
}

class SmartComposeCacheAndGuardTest : FunSpec({

    fun ctx(preceding: String, composing: String = "") = SmartComposeContext(
        precedingText = preceding,
        composingPrefix = composing,
        locale = "en",
    )

    test("SmartComposeCache: repeat predictions hit the cache") {
        val suggestion = SmartComposeResult.Suggestion(
            listOf(SmartComposeCandidate("world", 0.9f, 1)),
        )
        val under = CountingProvider(mapOf("hello |" to suggestion))
        val cache = SmartComposeCache(under)
        cache.predictNextTokens(ctx("hello "))
        cache.predictNextTokens(ctx("hello "))
        cache.predictNextTokens(ctx("hello "))
        under.calls shouldBe 1
        cache.hits shouldBe 2
        cache.misses shouldBe 1
    }

    test("SmartComposeCache: NoSuggestion is NOT cached so addon-flip stays live") {
        val under = CountingProvider(emptyMap())
        val cache = SmartComposeCache(under)
        cache.predictNextTokens(ctx("nothing here"))
        cache.predictNextTokens(ctx("nothing here"))
        // Both calls hit the delegate because NoSuggestion is not cached.
        under.calls shouldBe 2
        cache.size() shouldBe 0
    }

    test("SmartComposeCache: different locales cache separately") {
        val under = CountingProvider(
            mapOf("hi|" to SmartComposeResult.Suggestion(
                listOf(SmartComposeCandidate("there", 0.5f, 1)),
            )),
        )
        val cache = SmartComposeCache(under)
        val enCtx = SmartComposeContext("hi", "", "en")
        val esCtx = SmartComposeContext("hi", "", "es")
        cache.predictNextTokens(enCtx)
        cache.predictNextTokens(esCtx)
        // Different locale keys → both calls reach the delegate.
        // CountingProvider is locale-blind so both return the same Suggestion;
        // the cache stores them under separate keys → size 2.
        under.calls shouldBe 2
        cache.size() shouldBe 2
    }

    test("SmartComposeCache: clear resets cache + counters") {
        val under = CountingProvider(
            mapOf("a|" to SmartComposeResult.Suggestion(
                listOf(SmartComposeCandidate("b", 0.7f, 1)),
            )),
        )
        val cache = SmartComposeCache(under)
        cache.predictNextTokens(ctx("a"))
        cache.size() shouldBe 1
        cache.clear()
        cache.size() shouldBe 0
        cache.hits shouldBe 0
        cache.misses shouldBe 0
    }

    test("SensitiveFieldGuard: TEXT password field is sensitive") {
        // InputType for TYPE_CLASS_TEXT | TYPE_TEXT_VARIATION_PASSWORD = 0x81
        SensitiveFieldGuard.isSensitive(inputType = 0x81, imeOptions = 0) shouldBe true
        SensitiveFieldGuard.reasonFor(inputType = 0x81, imeOptions = 0) shouldBe
            "TEXT password field"
    }

    test("SensitiveFieldGuard: visible-password + web-password + numeric-PIN flagged") {
        SensitiveFieldGuard.isSensitive(inputType = 0x91, imeOptions = 0) shouldBe true   // visible
        SensitiveFieldGuard.isSensitive(inputType = 0xE1, imeOptions = 0) shouldBe true   // web
        SensitiveFieldGuard.isSensitive(inputType = 0x12, imeOptions = 0) shouldBe true   // num PIN
    }

    test("SensitiveFieldGuard: IME_FLAG_NO_PERSONALIZED_LEARNING always wins") {
        SensitiveFieldGuard.isSensitive(
            inputType = 0x01,  // plain text — not normally sensitive
            imeOptions = 0x01000000,
        ) shouldBe true
        SensitiveFieldGuard.reasonFor(
            inputType = 0x01,
            imeOptions = 0x01000000,
        ) shouldBe "IME_FLAG_NO_PERSONALIZED_LEARNING set"
    }

    test("SensitiveFieldGuard: plain text field is NOT sensitive") {
        SensitiveFieldGuard.isSensitive(inputType = 0x01, imeOptions = 0) shouldBe false
        SensitiveFieldGuard.reasonFor(inputType = 0x01, imeOptions = 0) shouldBe null
    }

    test("SensitiveFieldGuard: number field (non-password) is NOT sensitive") {
        SensitiveFieldGuard.isSensitive(inputType = 0x02, imeOptions = 0) shouldBe false
    }
})
