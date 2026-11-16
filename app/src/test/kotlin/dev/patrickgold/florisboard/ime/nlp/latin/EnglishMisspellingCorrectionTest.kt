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

import dev.patrickgold.florisboard.ime.nlp.AutoCommitSuppression
import dev.patrickgold.florisboard.ime.nlp.CandidateAutoCommitPolicy
import dev.patrickgold.florisboard.ime.nlp.ImmediateAutocorrect
import dev.patrickgold.florisboard.ime.nlp.SuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.SwiftKeyCandidateRanker
import dev.patrickgold.florisboard.ime.nlp.SwiftKeyDecoderContext
import dev.patrickgold.florisboard.ime.nlp.WordSuggestionCandidate
import dev.patrickgold.florisboard.ime.text.key.KeyVariation
import dev.patrickgold.florisboard.lib.FlorisLocale
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.File

class EnglishMisspellingCorrectionTest : FunSpec({
    val shipped by lazy {
        runBlocking {
            LatinDictionaryStore(readAsset = LatinDictionaryAssetReader { path -> bundledAsset(path)?.readText() })
                .dictionaryForLanguage("en")
        }
    }

    test("shipped English word lists leave out every listed misspelling") {
        val table = LatinDictionaryStore.parseMisspellings(
            bundledAsset(LatinDictionaryStore.EnglishMisspellingsPath)!!.readText(),
        )
        val dataWords = jsonKeys("ime/dict/data.json")
        val supplementalWords = jsonKeys("ime/dict/en_supplemental.json")
        val plainWords = bundledAsset("dictionaries/en.txt")!!.readLines().toSet()

        (table.size >= 700) shouldBe true
        table["teh"] shouldBe "the"
        table["recieve"] shouldBe "receive"
        table["untill"] shouldBe "until"
        table["thier"] shouldBe "their"
        table.keys.filter { it in dataWords } shouldBe emptyList()
        table.keys.filter { it in supplementalWords } shouldBe emptyList()
        table.keys.filter { it in plainWords } shouldBe emptyList()
        table.keys.filter { shipped.contains(it) } shouldBe emptyList()
        // Real words that are also common misspellings stay recognized and off the list.
        listOf("wether", "calender").forEach { word ->
            table.containsKey(word) shouldBe false
            shipped.contains(word) shouldBe true
        }
        shipped.misspellings shouldBe table
    }

    test("typed key by key, listed misspellings commit their fix on space") {
        mapOf(
            "teh" to "the",
            "recieve" to "receive",
            "untill" to "until",
            "thier" to "their",
            "Teh" to "The",
            "wich" to "which",
        ).forEach { (typed, expected) ->
            typeThenSpace(typed, shipped) shouldBe expected
        }
    }

    test("typed key by key, real words that look like misspellings stay as typed") {
        listOf("wether", "calender").forEach { typed ->
            typeThenSpace(typed, shipped) shouldBe typed
        }
    }

    test("a misspelling the user taught the keyboard stays as typed") {
        val overlay = mapOf("teh" to 200)

        LatinDictionarySuggester.englishMisspellingCorrection("teh", shipped, "en", overlay) shouldBe null
        typeThenSpace("teh", shipped, overlay) shouldBe "teh"
    }

    test("misspelling fix keeps the typed case, skips ALL CAPS and other languages") {
        val dictionary = LatinDictionarySnapshot.from(mapOf("the" to 255, "lot" to 230))
            .withMisspellings(mapOf("teh" to "the", "alot" to "a lot"))

        LatinDictionarySuggester.englishMisspellingCorrection("teh", dictionary, "en")?.text shouldBe "the"
        LatinDictionarySuggester.englishMisspellingCorrection("Teh", dictionary, "en-US")?.text shouldBe "The"
        LatinDictionarySuggester.englishMisspellingCorrection("Alot", dictionary, "en")?.text shouldBe "A lot"
        LatinDictionarySuggester.englishMisspellingCorrection("TEH", dictionary, "en") shouldBe null
        LatinDictionarySuggester.englishMisspellingCorrection("teh", dictionary, "fr") shouldBe null
        LatinDictionarySuggester.englishMisspellingCorrection("tea", dictionary, "en") shouldBe null
        LatinDictionarySuggester.suggest("teh", dictionary, maxCandidateCount = 4).map { it.text } shouldBe
            listOf("the")
        LatinDictionarySuggester.suggest("teh", dictionary, maxCandidateCount = 4).single()
            .isEligibleForAutoCommit shouldBe true
    }

    test("a dictionary that lists the word itself wins over the misspelling table") {
        val dictionary = LatinDictionarySnapshot.from(mapOf("the" to 255, "teh" to 120))
            .withMisspellings(mapOf("teh" to "the"))

        LatinDictionarySuggester.englishMisspellingCorrection("teh", dictionary, "en") shouldBe null
    }

    test("en-UK and en-AU keyboards get the British fix, en-US the US one, and en-CA Canadian spelling") {
        // The region comes from the bundled presets' tags the way the provider reads it (primaryLocale.country).
        val (uk, au, us, ca) = listOf("en-UK", "en-AU", "en-US", "en-CA").map { FlorisLocale.fromTag(it).country }

        typeThenSpace("jewelery", shipped, region = uk) shouldBe "jewellery"
        typeThenSpace("nieghbour", shipped, region = uk) shouldBe "neighbour"
        typeThenSpace("nieghbor", shipped, region = au) shouldBe "neighbour"
        typeThenSpace("Favorate", shipped, region = "ZA") shouldBe "Favourite"
        typeThenSpace("relize", shipped, region = "GB") shouldBe "realise"
        typeThenSpace("fullfill", shipped, region = uk) shouldBe "fulfil"
        typeThenSpace("jewelery", shipped, region = us) shouldBe "jewelry"
        typeThenSpace("nieghbor", shipped, region = us) shouldBe "neighbor"
        typeThenSpace("jewelery", shipped, region = "MH") shouldBe "jewelry"
        typeThenSpace("jewelery", shipped) shouldBe "jewelry"
        // Canada writes -our, -re and -ence like Britain but keeps the US -ize words and fulfill.
        typeThenSpace("jewelery", shipped, region = ca) shouldBe "jewellery"
        typeThenSpace("nieghbor", shipped, region = ca) shouldBe "neighbour"
        typeThenSpace("favorate", shipped, region = ca) shouldBe "favourite"
        typeThenSpace("manuever", shipped, region = ca) shouldBe "manoeuvre"
        typeThenSpace("liscence", shipped, region = ca) shouldBe "licence"
        typeThenSpace("relize", shipped, region = ca) shouldBe "realize"
        typeThenSpace("recogise", shipped, region = ca) shouldBe "recognize"
        typeThenSpace("fullfill", shipped, region = ca) shouldBe "fulfill"
        // A typo of either spelling gets the keyboard's own, unless only British spelling makes it.
        typeThenSpace("recogise", shipped, region = us) shouldBe "recognize"
        typeThenSpace("critisise", shipped, region = us) shouldBe "criticize"
        typeThenSpace("liscence", shipped, region = us) shouldBe "license"
        typeThenSpace("recogise", shipped, region = uk) shouldBe "recognise"
        typeThenSpace("nieghbour", shipped, region = us) shouldBe "neighbour"
        typeThenSpace("teh", shipped, region = uk) shouldBe "the"
    }

    test("Canada keeps the US spelling only for -ize words and a doubled final l") {
        LatinDictionarySuggester.canadianKeepsUsSpelling("realize", "realise") shouldBe true
        LatinDictionarySuggester.canadianKeepsUsSpelling("organizing", "organising") shouldBe true
        LatinDictionarySuggester.canadianKeepsUsSpelling("organization", "organisation") shouldBe true
        LatinDictionarySuggester.canadianKeepsUsSpelling("fulfill", "fulfil") shouldBe true
        LatinDictionarySuggester.canadianKeepsUsSpelling("jewelry", "jewellery") shouldBe false
        LatinDictionarySuggester.canadianKeepsUsSpelling("license", "licence") shouldBe false
        LatinDictionarySuggester.canadianKeepsUsSpelling("maneuver", "manoeuvre") shouldBe false
        LatinDictionarySuggester.canadianKeepsUsSpelling("pizza", "pissa") shouldBe false
    }

    test("the misspelling list's third column is the Commonwealth fix") {
        val raw = "# comment\njewelery\tjewelry\tjewellery\nteh\tthe\nbroken\t\tbroken\nhalf\tfix\t\n"

        LatinDictionaryStore.parseMisspellings(raw) shouldBe
            mapOf("jewelery" to "jewelry", "teh" to "the", "half" to "fix")
        LatinDictionaryStore.parseCommonwealthMisspellings(raw) shouldBe mapOf("jewelery" to "jewellery")
        shipped.commonwealthMisspellings.size shouldBe 17
        shipped.commonwealthMisspellings.keys.filter { it !in shipped.misspellings } shouldBe emptyList()
        shipped.commonwealthMisspellings.values.filter { !shipped.contains(it) } shouldBe emptyList()
    }

    test("only the English dictionary loads the misspelling table") {
        val assets = mapOf(
            "ime/dict/data.json" to """{"the":255,"their":215}""",
            "ime/dict/de.json" to """{"der":255,"die":250}""",
            LatinDictionaryStore.EnglishMisspellingsPath to "# comment\n\nthier\ttheir\nbroken line\nTEH\tthe\n",
        )
        val store = LatinDictionaryStore(readAsset = LatinDictionaryAssetReader { path -> assets[path] })

        val english = runBlocking { store.dictionaryForLanguage("en-US") }
        val german = runBlocking { store.dictionaryForLanguage("de") }

        english.misspellings shouldBe mapOf("thier" to "their", "teh" to "the")
        german.misspellings shouldBe emptyMap()
    }
})

/**
 * Feeds [typed] one letter at a time through the provider's suggester and the ranker, the way
 * NlpManager publishes candidates after each key, then asks the spacebar policy what a space
 * would commit. The typed word counts as known the same way NlpManager.isKnownTypedWord does.
 */
private fun typeThenSpace(
    typed: String,
    dictionary: LatinDictionarySnapshot,
    overlay: Map<String, Int> = emptyMap(),
    region: String = "",
): String {
    var ranked: List<SuggestionCandidate> = emptyList()
    for (end in 1..typed.length) {
        val word = typed.take(end)
        val key = word.lowercase()
        val suggestions = LatinDictionarySuggester.suggest(
            rawWord = word,
            dictionary = dictionary,
            maxCandidateCount = 8,
            languageCode = "en",
            userOverlay = overlay,
            region = region,
        ).map { suggestion ->
            WordSuggestionCandidate(
                text = suggestion.text,
                confidence = suggestion.confidence,
                isEligibleForAutoCommit = suggestion.isEligibleForAutoCommit,
            )
        }
        ranked = SwiftKeyCandidateRanker.rank(
            context = SwiftKeyDecoderContext(
                currentWord = word,
                maxCandidateCount = 8,
                typedWordKnown = overlay.containsKey(key) || dictionary.frequencyFor(key) > 0.0,
            ),
            preferred = emptyList(),
            fallback = suggestions,
        )
    }
    val committed = CandidateAutoCommitPolicy.selectSpacebarCandidate(
        autoCorrectEnabled = true,
        quickPredictionInsertEnabled = false,
        keyVariation = KeyVariation.NORMAL,
        currentWord = typed,
        currentWordStart = 0,
        textBeforeCursor = typed,
        candidates = ranked,
        candidateSignals = emptyMap(),
        rejectionPolicy = AutoCommitSuppression(),
        userDictionaryShortcutCandidate = null,
        immediatePhraseRepairCandidate = ImmediateAutocorrect.englishPhraseRepairCandidate(typed, "en"),
        immediateAutoCommitCandidate = ImmediateAutocorrect.englishContractionCandidate(typed, "en"),
    )
    return committed?.text?.toString() ?: typed
}

private fun jsonKeys(path: String): Set<String> {
    return Json.parseToJsonElement(bundledAsset(path)!!.readText()).jsonObject.keys
}

private fun bundledAsset(path: String): File? {
    return listOf(
        File("src/main/assets/$path"),
        File("app/src/main/assets/$path"),
    ).firstOrNull { it.isFile }
}
