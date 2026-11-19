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
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe

/** Words the user typed after the previous words, offered while the next word is half typed. */
class PersonalCompletionTest : FunSpec({
    val frequencies = mapOf(
        "to" to 255,
        "today" to 250,
        "told" to 245,
        "together" to 240,
        "top" to 240,
        "total" to 235,
        "touch" to 230,
        "tomorrow" to 150,
        "soon" to 230,
    )
    val dictionary = LatinDictionarySnapshot(
        frequencies = frequencies,
        sortedWords = frequencies.keys.sorted(),
    )

    fun suggest(word: String, continuations: List<String>) = LatinDictionarySuggester.suggest(
        rawWord = word,
        dictionary = dictionary,
        maxCandidateCount = 4,
        personalContinuations = continuations,
    )

    test("a remembered continuation leads the completions") {
        suggest("to", emptyList()).map { it.text } shouldNotContain "tomorrow"

        val suggestions = suggest("to", listOf("tomorrow"))

        suggestions.first().text shouldBe "tomorrow"
        suggestions.first().isEligibleForAutoCommit shouldBe false
    }

    test("one typed letter is enough for a remembered continuation") {
        suggest("t", emptyList()).shouldBeEmpty()

        suggest("t", listOf("soon", "tomorrow")).map { it.text } shouldBe listOf("tomorrow")
    }

    test("continuations that don't complete the typed word are left out") {
        suggest("to", listOf("soon")).map { it.text } shouldNotContain "soon"
        suggest("to", listOf("to")).count { it.text == "to" } shouldBe 0
    }

    test("a continuation takes the case the word was typed in") {
        suggest("To", listOf("tomorrow")).first().text shouldBe "Tomorrow"
    }
})
