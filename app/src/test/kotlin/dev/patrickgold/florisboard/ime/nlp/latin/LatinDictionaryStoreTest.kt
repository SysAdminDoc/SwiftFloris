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

package dev.patrickgold.florisboard.ime.nlp.latin

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.math.roundToInt

class LatinDictionaryStoreTest : FunSpec({
    test("normalizes locale tags to language asset paths") {
        LatinDictionaryStore.normalizeLanguageCode("pt-BR") shouldBe "pt"
        LatinDictionaryStore.normalizeLanguageCode("de_DE") shouldBe "de"
        LatinDictionaryStore.normalizeLanguageCode("") shouldBe "en"

        LatinDictionaryStore.assetPathsForLanguage("pt-BR") shouldBe listOf(
            "ime/dict/pt.json",
            "ime/dict/pt.fldic",
        )
        LatinDictionaryStore.assetPathsForLanguage("en-US") shouldBe listOf(
            "ime/dict/en.json",
            "ime/dict/en.fldic",
            "ime/dict/data.json",
        )
    }

    test("loads locale-specific dictionary before English fallback") {
        val store = latinDictionaryStore(
            "ime/dict/en.json" to dictionaryJson("hello" to 210, "test" to 180),
            "ime/dict/de.json" to dictionaryJson("hallo" to 220, "danke" to 190),
        )

        val dictionary = runBlocking { store.dictionaryForLanguage("de-DE") }

        dictionary.sortedWords shouldBe listOf("danke", "hallo")
        dictionary.frequencyFor("hallo") shouldBe 220 / 255.0
        dictionary.frequencyFor("hello") shouldBe 0.0
    }

    test("falls back to legacy English dictionary when locale asset is unavailable") {
        val store = latinDictionaryStore(
            "ime/dict/data.json" to dictionaryJson("hello" to 210, "test" to 180),
        )

        val dictionary = runBlocking { store.dictionaryForLanguage("fr") }

        dictionary.sortedWords shouldBe listOf("hello", "test")
        dictionary.frequencyFor("hello") shouldBe 210 / 255.0
    }

    test("merges addon dictionary assets ahead of bundled language assets") {
        val addonDictionaryPath = AddonDictionaryAssetMounts.addonAssetPath(
            packageName = "org.swiftfloris.dict.pl",
            assetPath = "ime/dict/pl.fldic",
        )
        val store = LatinDictionaryStore(
            readAsset = LatinDictionaryAssetReader { path ->
                mapOf(
                    addonDictionaryPath to fldic("addonword" to 1000, "shared" to 1000),
                    "ime/dict/pl.fldic" to fldic("baseword" to 900, "shared" to 100),
                )[path]
            },
            assetPlanner = LatinDictionaryAssetPlanner {
                LatinDictionaryAssetPlan(
                    generation = 1L,
                    dictionaryPaths = listOf(addonDictionaryPath, "ime/dict/pl.fldic"),
                    zipfPaths = emptyList(),
                )
            },
        )

        val dictionary = runBlocking { store.dictionaryForLanguage("pl") }

        dictionary.sortedWords shouldBe listOf("addonword", "baseword", "shared")
        dictionary.frequencyFor("addonword") shouldBe 1.0
        dictionary.frequencyFor("baseword") shouldBe 1.0
        dictionary.frequencyFor("shared") shouldBe 1.0
    }

    test("reloads cached dictionaries when the asset plan generation changes") {
        val addonDictionaryPath = AddonDictionaryAssetMounts.addonAssetPath(
            packageName = "org.swiftfloris.dict.pl",
            assetPath = "ime/dict/pl.fldic",
        )
        val assets = mutableMapOf(
            "ime/dict/pl.fldic" to fldic("baseword" to 900),
        )
        var generation = 1L
        var dictionaryPaths = listOf("ime/dict/pl.fldic")
        val store = LatinDictionaryStore(
            readAsset = LatinDictionaryAssetReader { path -> assets[path] },
            assetPlanner = LatinDictionaryAssetPlanner {
                LatinDictionaryAssetPlan(
                    generation = generation,
                    dictionaryPaths = dictionaryPaths,
                    zipfPaths = emptyList(),
                )
            },
        )

        val first = runBlocking { store.dictionaryForLanguage("pl") }
        assets[addonDictionaryPath] = fldic("addonword" to 1000)
        dictionaryPaths = listOf(addonDictionaryPath, "ime/dict/pl.fldic")
        generation = 2L
        val second = runBlocking { store.dictionaryForLanguage("pl") }

        first.sortedWords shouldBe listOf("baseword")
        second.sortedWords shouldBe listOf("addonword", "baseword")
    }

    test("merges bundled English supplemental dictionary without lowering base frequencies") {
        val store = latinDictionaryStore(
            "ime/dict/data.json" to dictionaryJson("hello" to 210, "test" to 180),
            "ime/dict/en_supplemental.json" to dictionaryJson("hello" to 48, "swiftfloris" to 96),
        )

        val dictionary = runBlocking { store.dictionaryForLanguage("en-US") }

        dictionary.sortedWords shouldBe listOf("hello", "swiftfloris", "test")
        dictionary.frequencyFor("hello") shouldBe 210 / 255.0
        dictionary.frequencyFor("swiftfloris") shouldBe 96 / 255.0
    }

    test("glide vocabulary keeps common words and filters long-tail recognition words") {
        val dictionary = LatinDictionarySnapshot.from(
            mapOf(
                "hello" to 210,
                "swiftfloris" to 96,
                "zyzzyvas" to 59,
                "a" to 255,
                "averyveryveryveryverylongword" to 255,
            ),
        )

        dictionary.sortedWords.contains("zyzzyvas") shouldBe true
        dictionary.sortedWords.contains("a") shouldBe true
        dictionary.glideWords shouldBe listOf("hello", "swiftfloris")
    }

    test("loads fldic word scores and ignores non-word sections") {
        val store = latinDictionaryStore(
            "ime/dict/es.fldic" to """
                #~schema: https://schemas.florisboard.org/nlp/v0~draft1/fldic.txt
                #~encoding: utf-8

                [words]
                hola	1000
                árbol	500
                hola	900
                buen-día	800
                oculto	100	h

                [ngrams]
                1,2	500
            """.trimIndent(),
            "ime/dict/data.json" to dictionaryJson("hello" to 210),
        )

        val dictionary = runBlocking { store.dictionaryForLanguage("es") }

        dictionary.sortedWords shouldBe listOf("hola", "oculto", "árbol")
        dictionary.frequencyFor("hola") shouldBe 1.0
        dictionary.frequencyFor("árbol") shouldBe (kotlin.math.ln(501.0) / kotlin.math.ln(1001.0) * 255.0)
            .roundToInt() / 255.0
        dictionary.frequencyFor("hello") shouldBe 0.0
    }

    test("loads bundled imported fldic dictionaries") {
        val store = LatinDictionaryStore(readAsset = LatinDictionaryAssetReader { path ->
            bundledAsset(path)?.readText()
        })
        val expectedMinimumWordCounts = mapOf(
            "de" to 200_000,
            "es" to 350_000,
            "fr" to 200_000,
            "it" to 300_000,
            "pt" to 100_000,
        )

        expectedMinimumWordCounts.forEach { (language, minimumWordCount) ->
            val dictionary = runBlocking { store.dictionaryForLanguage(language) }

            (dictionary.sortedWords.size >= minimumWordCount) shouldBe true
            (dictionary.glideWords.size in 50_000..120_000) shouldBe true
            dictionary.isLoaded shouldBe true
        }
    }

    test("loads bundled expanded English supplemental dictionary") {
        val store = LatinDictionaryStore(readAsset = LatinDictionaryAssetReader { path ->
            bundledAsset(path)?.readText()
        })

        val dictionary = runBlocking { store.dictionaryForLanguage("en") }

        (dictionary.sortedWords.size >= 515_000) shouldBe true
        dictionary.contains("kubernetes") shouldBe true
        dictionary.contains("chatgpt") shouldBe true
        dictionary.contains("telehealth") shouldBe true
        dictionary.contains("swiftfloris") shouldBe true
        dictionary.contains("qwerty") shouldBe true
        dictionary.contains("zyzzyvas") shouldBe true
        dictionary.frequencyFor("kubernetes") shouldBe 96 / 255.0
        dictionary.frequencyFor("zyzzyvas") shouldBe 59 / 255.0
        (dictionary.glideWords.size in 100_000..120_000) shouldBe true
        dictionary.glideWords.contains("swiftkey") shouldBe true
        dictionary.glideWords.contains("zyzzyvas") shouldBe false
        (dictionary.correctionWords.size in 90_000..96_000) shouldBe true
        dictionary.correctionWords.contains("swiftkey") shouldBe true
        dictionary.correctionWords.contains("zyzzyvas") shouldBe false
    }
})

private fun latinDictionaryStore(vararg assets: Pair<String, String>): LatinDictionaryStore {
    val assetMap = assets.toMap()
    return LatinDictionaryStore(readAsset = LatinDictionaryAssetReader { path -> assetMap[path] })
}

private fun dictionaryJson(vararg words: Pair<String, Int>): String {
    return words.joinToString(prefix = "{", postfix = "}") { (word, frequency) ->
        """"$word":$frequency"""
    }
}

private fun fldic(vararg words: Pair<String, Int>): String {
    return words.joinToString(
        prefix = """
            #~schema: https://schemas.florisboard.org/nlp/v0~draft1/fldic.txt
            #~encoding: utf-8

            [words]
        """.trimIndent() + "\n",
        separator = "\n",
    ) { (word, score) -> "$word\t$score" }
}

private fun bundledAsset(path: String): File? {
    return listOf(
        File("src/main/assets/$path"),
        File("app/src/main/assets/$path"),
    ).firstOrNull { it.isFile }
}
