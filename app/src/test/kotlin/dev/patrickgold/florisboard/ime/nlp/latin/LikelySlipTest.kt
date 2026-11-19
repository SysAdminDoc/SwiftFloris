/*
 * Copyright (C) 2026 SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.nlp.latin

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.io.File

/** A slip that lands on a rare dictionary entry ("thos" for "this") is corrected like an unknown word. */
class LikelySlipTest : FunSpec({
    val frequencies = mapOf(
        "this" to 236,
        "those" to 218,
        "thus" to 203,
        "thos" to 131,
        "snide" to 129,
        "slide" to 220,
        "the" to 252,
        "thy" to 150,
        "thwart" to 157,
        "data" to 235,
        "date" to 225,
        "day" to 240,
    )
    val dictionary = LatinDictionarySnapshot(
        frequencies = frequencies,
        sortedWords = frequencies.keys.sorted(),
        unvettedWords = setOf("thos"),
    )

    fun fixes(
        word: String,
        overlay: Map<String, Int> = emptyMap(),
        contextFit: ((String) -> Double?)? = null,
    ) = LatinDictionarySuggester.likelySlipFixes(word, dictionary, overlay, contextFit)

    fun firstAutoCommit(word: String) = LatinDictionarySuggester.suggest(
        rawWord = word,
        dictionary = dictionary,
        maxCandidateCount = 6,
    ).firstOrNull { it.isEligibleForAutoCommit }?.text

    test("an unvetted rare word gets the far more common words one edit away, most common first") {
        fixes("thos") shouldBe listOf("this", "those", "thus")
        firstAutoCommit("thos") shouldBe "this"
    }

    test("a rare word a vetted list backs stays as typed") {
        fixes("snide").shouldBeEmpty()
        firstAutoCommit("snide") shouldBe null
    }

    test("a word the user has typed before is never a slip") {
        fixes("thos", overlay = mapOf("thos" to 246)).shouldBeEmpty()
    }

    test("a word that fits the words before it is never a slip") {
        fixes("thos", contextFit = { word -> if (word == "thos") 0.6 else 0.1 }).shouldBeEmpty()
    }

    test("a fix has to fit the words before at least as well as the typed word") {
        val fit = mapOf("thos" to 0.2, "this" to 0.1, "those" to 0.0, "thus" to 0.3)
        fixes("thos", contextFit = { word -> fit[word] }) shouldBe listOf("thus")
    }

    test("a short word's correction beats rare completions but not common ones") {
        // "the" is far more common than "thwart"; "day" is barely more common than "data".
        firstAutoCommit("thw") shouldBe "the"
        firstAutoCommit("dat") shouldBe null
    }

    test("the word list parser skips comments and blank lines and lowercases") {
        LatinDictionaryStore.parseWordList("# header\n\nThos\nsnd\n  \n") shouldBe setOf("thos", "snd")
    }

    test("the bundled list has the web-corpus slips but no real word, chat term or acronym") {
        val file = listOf(File("src/main/assets"), File("app/src/main/assets"))
            .map { it.resolve(LatinDictionaryStore.EnglishUnvettedWordsPath) }
            .first { it.isFile }
        val unvetted = LatinDictionaryStore.parseWordList(file.readText())

        for (slip in listOf("thos", "snd", "rhe", "yoy", "lile", "thd")) {
            (slip in unvetted) shouldBe true
        }
        for (word in listOf("snide", "plop", "wager", "dyeing", "kites", "tine", "mow", "idk", "lol", "atm", "usa")) {
            (word in unvetted) shouldBe false
        }
    }
})
