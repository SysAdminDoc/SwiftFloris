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

class TranslationLanguagePackManagerTest : FunSpec({

    afterEach {
        TranslationLanguagePackManager.resetForTest()
    }

    fun pair(src: String, tgt: String, tier: String = "tiny") = LanguagePairDescriptor(
        sourceLocale = src,
        targetLocale = tgt,
        bundleAssetPath = "models/$src-$tgt-$tier.bin",
        bundleSizeBytes = 17_000_000L,
        qualityTier = tier,
    )

    test("empty manager exposes empty installed + available + null preferred locale") {
        TranslationLanguagePackManager.installedPairs() shouldBe emptyList()
        TranslationLanguagePackManager.availablePairs() shouldBe emptyList()
        TranslationLanguagePackManager.preferredTargetLocale() shouldBe null
    }

    test("setInstalled de-dupes by pairKey") {
        val p1 = pair("en", "es", "tiny")
        val p2 = pair("en", "es", "base")  // same pairKey "en-es", different tier
        TranslationLanguagePackManager.setInstalled(listOf(p1, p2))
        TranslationLanguagePackManager.installedPairs().size shouldBe 1
    }

    test("downloadablePairs returns available minus installed by pairKey") {
        val enEs = pair("en", "es")
        val enFr = pair("en", "fr")
        val enDe = pair("en", "de")
        TranslationLanguagePackManager.setAvailable(listOf(enEs, enFr, enDe))
        TranslationLanguagePackManager.setInstalled(listOf(enEs))
        val downloadable = TranslationLanguagePackManager.downloadablePairs()
        downloadable.map { it.pairKey } shouldBe listOf("en-fr", "en-de")
    }

    test("defaultPairFor honours preferredTargetLocale when installed") {
        val enEs = pair("en", "es")
        val enFr = pair("en", "fr")
        TranslationLanguagePackManager.setInstalled(listOf(enEs, enFr))
        TranslationLanguagePackManager.setPreferredTargetLocale("fr")
        TranslationLanguagePackManager.defaultPairFor("en") shouldBe enFr
    }

    test("defaultPairFor falls back to first installed match when preferred is null") {
        val enEs = pair("en", "es")
        val enFr = pair("en", "fr")
        TranslationLanguagePackManager.setInstalled(listOf(enEs, enFr))
        TranslationLanguagePackManager.defaultPairFor("en") shouldBe enEs
    }

    test("defaultPairFor returns null when no installed pair matches the source") {
        TranslationLanguagePackManager.setInstalled(listOf(pair("en", "es")))
        TranslationLanguagePackManager.defaultPairFor("ja") shouldBe null
    }

    test("setPreferredTargetLocale rejects blank or uppercase locales") {
        var caught = false
        try {
            TranslationLanguagePackManager.setPreferredTargetLocale("EN")
        } catch (_: IllegalArgumentException) {
            caught = true
        }
        caught shouldBe true

        var caught2 = false
        try {
            TranslationLanguagePackManager.setPreferredTargetLocale("  ")
        } catch (_: IllegalArgumentException) {
            caught2 = true
        }
        caught2 shouldBe true
    }
})
