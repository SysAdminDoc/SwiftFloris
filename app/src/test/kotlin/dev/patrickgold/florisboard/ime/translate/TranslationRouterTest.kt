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

private class FakeTranslator(
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

private class FakePackManager(
    val installed: List<LanguagePairDescriptor>,
    val preferred: String?,
) : TranslationRouter.PackManagerView {
    override fun installedPairs() = installed
    override fun preferredTargetLocale() = preferred
}

class TranslationRouterTest : FunSpec({

    fun pair(src: String, tgt: String) = LanguagePairDescriptor(
        sourceLocale = src,
        targetLocale = tgt,
        bundleAssetPath = "models/$src-$tgt.bin",
        bundleSizeBytes = 17_000_000L,
        qualityTier = "tiny",
    )

    test("password field short-circuits to Suppressed") {
        val tr = FakeTranslator(emptyMap())
        val pm = FakePackManager(listOf(pair("en", "es")), preferred = "es")
        val router = TranslationRouter(tr, pm)
        val resp = router.translate(TranslationRouter.Request(
            sourceText = "hello",
            sourceLocale = "en",
            targetLocale = "es",
            inputType = 0x81,
        ))
        (resp is TranslationRouter.Response.Suppressed) shouldBe true
        tr.calls shouldBe 0
    }

    test("blank input is suppressed") {
        val tr = FakeTranslator(emptyMap())
        val pm = FakePackManager(listOf(pair("en", "es")), preferred = "es")
        val router = TranslationRouter(tr, pm)
        val resp = router.translate(TranslationRouter.Request(sourceText = "   "))
        (resp is TranslationRouter.Response.Suppressed) shouldBe true
    }

    test("happy path: explicit src+tgt with installed pair returns Translated") {
        val tr = FakeTranslator(mapOf(
            Triple("hello", "en", "es") to TranslationResult.Translated("hola", 0.95f),
        ))
        val pm = FakePackManager(listOf(pair("en", "es")), preferred = null)
        val router = TranslationRouter(tr, pm)
        val resp = router.translate(TranslationRouter.Request(
            sourceText = "hello",
            sourceLocale = "en",
            targetLocale = "es",
        ))
        val translated = resp as TranslationRouter.Response.Translated
        translated.translatedText shouldBe "hola"
        translated.resolvedSourceLocale shouldBe "en"
        translated.resolvedTargetLocale shouldBe "es"
    }

    test("auto-detect: Latin text without explicit source resolves to en") {
        val tr = FakeTranslator(mapOf(
            Triple("hello world", "en", "es") to TranslationResult.Translated("hola mundo", 0.9f),
        ))
        val pm = FakePackManager(listOf(pair("en", "es")), preferred = "es")
        val router = TranslationRouter(tr, pm)
        val resp = router.translate(TranslationRouter.Request(sourceText = "hello world"))
        val translated = resp as TranslationRouter.Response.Translated
        translated.resolvedSourceLocale shouldBe "en"
        translated.resolvedTargetLocale shouldBe "es"
    }

    test("source == target collapses to Suppressed") {
        val pm = FakePackManager(listOf(pair("en", "es")), preferred = "en")
        val router = TranslationRouter(FakeTranslator(emptyMap()), pm)
        val resp = router.translate(TranslationRouter.Request(
            sourceText = "hello",
            sourceLocale = "en",
            targetLocale = "en",
        ))
        (resp is TranslationRouter.Response.Suppressed) shouldBe true
    }

    test("missing target without preferred is suppressed") {
        val pm = FakePackManager(listOf(pair("en", "es")), preferred = null)
        val router = TranslationRouter(FakeTranslator(emptyMap()), pm)
        val resp = router.translate(TranslationRouter.Request(
            sourceText = "hello",
            sourceLocale = "en",
        ))
        (resp is TranslationRouter.Response.Suppressed) shouldBe true
    }

    test("paragraph: each sentence dispatched separately and stitched") {
        val tr = FakeTranslator(mapOf(
            Triple("Hello. ", "en", "es") to TranslationResult.Translated("Hola. ", 0.9f),
            Triple("World!", "en", "es") to TranslationResult.Translated("¡Mundo!", 0.9f),
        ))
        val pm = FakePackManager(listOf(pair("en", "es")), preferred = "es")
        val router = TranslationRouter(tr, pm)
        val resp = router.translate(TranslationRouter.Request(
            sourceText = "Hello. World!",
            sourceLocale = "en",
            targetLocale = "es",
        ))
        val translated = resp as TranslationRouter.Response.Translated
        translated.translatedText shouldBe "Hola. ¡Mundo!"
        tr.calls shouldBe 2
    }

    test("cache deduplicates repeat translations") {
        val tr = FakeTranslator(mapOf(
            Triple("hello", "en", "es") to TranslationResult.Translated("hola", 0.95f),
        ))
        val pm = FakePackManager(listOf(pair("en", "es")), preferred = "es")
        val router = TranslationRouter(tr, pm)
        router.translate(TranslationRouter.Request("hello", "en", "es"))
        router.translate(TranslationRouter.Request("hello", "en", "es"))
        router.translate(TranslationRouter.Request("hello", "en", "es"))
        tr.calls shouldBe 1
    }

    test("no installed pair for src→tgt returns Suppressed") {
        val pm = FakePackManager(emptyList(), preferred = "es")
        val router = TranslationRouter(FakeTranslator(emptyMap()), pm)
        val resp = router.translate(TranslationRouter.Request(
            sourceText = "hello",
            sourceLocale = "en",
            targetLocale = "es",
        ))
        (resp is TranslationRouter.Response.Suppressed) shouldBe true
    }

    test("translator returns Unavailable → router suppresses (per-sentence fallback to source)") {
        val tr = FakeTranslator(emptyMap())  // returns Unavailable for everything
        val pm = FakePackManager(listOf(pair("en", "es")), preferred = "es")
        val router = TranslationRouter(tr, pm)
        val resp = router.translate(TranslationRouter.Request(
            sourceText = "hello",
            sourceLocale = "en",
            targetLocale = "es",
        ))
        (resp is TranslationRouter.Response.Suppressed) shouldBe true
    }
})
