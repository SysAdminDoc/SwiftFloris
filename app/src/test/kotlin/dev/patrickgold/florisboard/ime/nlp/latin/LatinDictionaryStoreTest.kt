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

class LatinDictionaryStoreTest : FunSpec({
    test("normalizes locale tags to language asset paths") {
        LatinDictionaryStore.normalizeLanguageCode("pt-BR") shouldBe "pt"
        LatinDictionaryStore.normalizeLanguageCode("de_DE") shouldBe "de"
        LatinDictionaryStore.normalizeLanguageCode("") shouldBe "en"

        LatinDictionaryStore.assetPathsForLanguage("pt-BR") shouldBe listOf("ime/dict/pt.json")
        LatinDictionaryStore.assetPathsForLanguage("en-US") shouldBe listOf(
            "ime/dict/en.json",
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
