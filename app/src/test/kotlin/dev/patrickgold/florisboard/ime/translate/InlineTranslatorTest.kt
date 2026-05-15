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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class InlineTranslatorTest : FunSpec({
    afterEach { InlineTranslatorRegistry.reset() }

    test("Default translator returns Unavailable") {
        InlineTranslator.Default.translate("hi", "en", "es") shouldBe TranslationResult.Unavailable
        InlineTranslator.Default.isLanguagePairReady("en", "es") shouldBe false
        InlineTranslator.Default.installedPairs shouldBe emptySet()
    }

    test("Registry default + replace + reset works") {
        InlineTranslatorRegistry.active shouldBe InlineTranslator.Default
        val pair = LanguagePairDescriptor("en", "es", "models/en-es-tiny.bin", 17_000_000L, "tiny")
        val fake = object : InlineTranslator {
            override fun translate(sourceText: String, sourceLocale: String, targetLocale: String) =
                TranslationResult.Translated("hola", 0.95f)
            override fun isLanguagePairReady(sourceLocale: String, targetLocale: String) = true
            override val installedPairs = setOf(pair)
        }
        InlineTranslatorRegistry.setActive(fake)
        val result = InlineTranslatorRegistry.active.translate("hi", "en", "es")
            .shouldBeInstanceOf<TranslationResult.Translated>()
        result.translatedText shouldBe "hola"
        InlineTranslatorRegistry.reset()
        InlineTranslatorRegistry.active shouldBe InlineTranslator.Default
    }

    test("LanguagePairDescriptor enforces lowercase locales and distinct source/target") {
        shouldThrow<IllegalArgumentException> {
            LanguagePairDescriptor("EN", "es", "bundle.bin", 1L, "tiny")
        }
        shouldThrow<IllegalArgumentException> {
            LanguagePairDescriptor("en", "en", "bundle.bin", 1L, "tiny")
        }
        shouldThrow<IllegalArgumentException> {
            LanguagePairDescriptor("en", "es", "bundle.bin", 1L, "ultra")
        }
    }

    test("LanguagePairDescriptor.pairKey composes the canonical id") {
        LanguagePairDescriptor("en", "es", "bundle.bin", 1L, "tiny").pairKey shouldBe "en-es"
    }

    test("Translated result validates confidence range") {
        shouldThrow<IllegalArgumentException> {
            TranslationResult.Translated("x", 1.5f)
        }
        shouldThrow<IllegalArgumentException> {
            TranslationResult.Translated("", 0.5f)
        }
    }
})
