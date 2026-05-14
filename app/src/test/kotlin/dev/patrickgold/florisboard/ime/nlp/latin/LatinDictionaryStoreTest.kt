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

private fun bundledAsset(path: String): File? {
    return listOf(
        File("src/main/assets/$path"),
        File("app/src/main/assets/$path"),
    ).firstOrNull { it.isFile }
}
