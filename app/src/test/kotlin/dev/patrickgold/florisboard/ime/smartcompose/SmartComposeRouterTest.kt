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

private class FakeProvider(
    private val response: SmartComposeResult,
) : SmartComposeProvider {
    var calls: Int = 0
        private set
    var lastContextChars: Int = 0
        private set
    override fun predictNextTokens(
        context: SmartComposeContext,
        maxCandidates: Int,
    ): SmartComposeResult {
        calls++
        lastContextChars = context.precedingText.length
        return response
    }
    override fun isReady(locale: String) = true
    override val activeModel: LiteRtModelDescriptor? = null
    override val supportedLocales: Set<String> = setOf("en")
}

class SmartComposeRouterTest : FunSpec({

    fun ctx(preceding: String = "hello ") = SmartComposeContext(
        precedingText = preceding,
        composingPrefix = "",
        locale = "en",
    )

    test("password field short-circuits to NoSuggestion before touching the provider") {
        val provider = FakeProvider(SmartComposeResult.Suggestion(
            listOf(SmartComposeCandidate("world", 0.95f, 1)),
        ))
        val router = SmartComposeRouter(provider)
        val result = router.predict(
            context = ctx(),
            inputType = 0x81,   // TEXT password
            imeOptions = 0,
        )
        result shouldBe SmartComposeResult.NoSuggestion
        provider.calls shouldBe 0
    }

    test("plain TEXT field returns filtered suggestions from the provider") {
        val provider = FakeProvider(SmartComposeResult.Suggestion(
            listOf(
                SmartComposeCandidate("noise", 0.1f, 1),
                SmartComposeCandidate("world", 0.9f, 1),
            ),
        ))
        val router = SmartComposeRouter(provider)
        val result = router.predict(
            context = ctx(),
            inputType = 0x01,
            imeOptions = 0,
        )
        val suggestion = result as SmartComposeResult.Suggestion
        suggestion.candidates.map { it.text } shouldBe listOf("world")
        provider.calls shouldBe 1
    }

    test("router truncates precedingText to maxContextChars before dispatch") {
        val provider = FakeProvider(SmartComposeResult.NoSuggestion)
        val router = SmartComposeRouter(provider, maxContextChars = 32)
        val longContext = "x".repeat(500)
        router.predict(
            context = ctx(longContext),
            inputType = 0x01,
            imeOptions = 0,
        )
        // Provider should see at most 32 chars (no boundary in the
        // window, so falls back to the hard cap).
        provider.lastContextChars shouldBe 32
    }

    test("cache deduplicates repeat predictions for the same context") {
        val provider = FakeProvider(SmartComposeResult.Suggestion(
            listOf(SmartComposeCandidate("world", 0.9f, 1)),
        ))
        val router = SmartComposeRouter(provider)
        router.predict(ctx(), inputType = 0x01, imeOptions = 0)
        router.predict(ctx(), inputType = 0x01, imeOptions = 0)
        router.predict(ctx(), inputType = 0x01, imeOptions = 0)
        provider.calls shouldBe 1
    }

    test("bypassCache=true skips the LRU and re-asks the provider every call") {
        val provider = FakeProvider(SmartComposeResult.Suggestion(
            listOf(SmartComposeCandidate("world", 0.9f, 1)),
        ))
        val router = SmartComposeRouter(provider, bypassCache = true)
        router.predict(ctx(), inputType = 0x01, imeOptions = 0)
        router.predict(ctx(), inputType = 0x01, imeOptions = 0)
        provider.calls shouldBe 2
    }

    test("IME_FLAG_NO_PERSONALIZED_LEARNING also short-circuits") {
        val provider = FakeProvider(SmartComposeResult.Suggestion(
            listOf(SmartComposeCandidate("world", 0.95f, 1)),
        ))
        val router = SmartComposeRouter(provider)
        val result = router.predict(
            context = ctx(),
            inputType = 0x01,
            imeOptions = 0x01000000,
        )
        result shouldBe SmartComposeResult.NoSuggestion
        provider.calls shouldBe 0
    }
})
