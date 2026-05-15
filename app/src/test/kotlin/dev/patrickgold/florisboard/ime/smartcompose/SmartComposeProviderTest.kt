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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class SmartComposeProviderTest : FunSpec({

    afterEach { SmartComposeProviderRegistry.reset() }

    test("Default provider returns NoSuggestion") {
        val ctx = SmartComposeContext(
            precedingText = "Hello world",
            composingPrefix = "wor",
            locale = "en-US",
        )
        SmartComposeProvider.Default.predictNextTokens(ctx) shouldBe SmartComposeResult.NoSuggestion
        SmartComposeProvider.Default.isReady("en-US") shouldBe false
        SmartComposeProvider.Default.activeModel shouldBe null
        SmartComposeProvider.Default.supportedLocales shouldBe emptySet()
    }

    test("Registry starts at Default and accepts replacement") {
        SmartComposeProviderRegistry.active shouldBe SmartComposeProvider.Default
        val fake = object : SmartComposeProvider {
            override fun predictNextTokens(context: SmartComposeContext, maxCandidates: Int) =
                SmartComposeResult.Suggestion(listOf(SmartComposeCandidate("ld", 0.9f)))
            override fun isReady(locale: String) = locale == "en-US"
            override val activeModel = LiteRtModelDescriptor(
                name = "Gemma 3 1B",
                modelId = "gemma-3-1b-it-q4_k_m",
                preferredBackend = "auto",
                supportedLocales = listOf("en-US"),
                sizeBytes = 800_000_000L,
                quantization = "int4",
            )
            override val supportedLocales = setOf("en-US")
        }
        SmartComposeProviderRegistry.setActive(fake)
        SmartComposeProviderRegistry.active shouldBe fake
        SmartComposeProviderRegistry.active.isReady("en-US") shouldBe true
    }

    test("LiteRtModelDescriptor enforces backend whitelist") {
        shouldThrow<IllegalArgumentException> {
            LiteRtModelDescriptor(
                name = "x", modelId = "x", preferredBackend = "cuda",
                supportedLocales = listOf("en"), sizeBytes = 1L, quantization = "int4",
            )
        }
    }

    test("LiteRtModelDescriptor enforces quantization whitelist") {
        shouldThrow<IllegalArgumentException> {
            LiteRtModelDescriptor(
                name = "x", modelId = "x", preferredBackend = "cpu",
                supportedLocales = listOf("en"), sizeBytes = 1L, quantization = "int1",
            )
        }
    }

    test("SmartComposeContext rejects out-of-range maxTokens") {
        shouldThrow<IllegalArgumentException> {
            SmartComposeContext("hi", "", "en-US", maxTokens = 0)
        }
        shouldThrow<IllegalArgumentException> {
            SmartComposeContext("hi", "", "en-US", maxTokens = 100)
        }
    }

    test("SmartComposeCandidate auto-derives tokenCount from space-split text") {
        val candidate = SmartComposeCandidate("see you soon", 0.8f)
        candidate.tokenCount shouldBe 3
    }

    test("Suggestion result preserves candidate ordering") {
        val ctx = SmartComposeContext("hello", "", "en-US")
        val fake = object : SmartComposeProvider {
            override fun predictNextTokens(context: SmartComposeContext, maxCandidates: Int) =
                SmartComposeResult.Suggestion(
                    listOf(
                        SmartComposeCandidate("world", 0.95f),
                        SmartComposeCandidate("there", 0.45f),
                    ),
                )
            override fun isReady(locale: String) = true
            override val activeModel = null
            override val supportedLocales = setOf("en-US")
        }
        val result = fake.predictNextTokens(ctx).shouldBeInstanceOf<SmartComposeResult.Suggestion>()
        result.candidates.first().text shouldBe "world"
    }
})
