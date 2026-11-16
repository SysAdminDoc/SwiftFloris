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

package dev.patrickgold.florisboard.ime.nlp

import dev.patrickgold.florisboard.lib.FlorisLocale
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LearnedWordForgetOfferTest : FunSpec({
    val chip = LearnedWordForgetSuggestionCandidate(word = "kvazq", locale = FlorisLocale.from("en", "US"))

    // The offer is made while request 5 is the newest, so request 6 on is a pass that saw it.
    val offeredAt = 5L
    val newer = 6L

    fun word(text: String) = WordSuggestionCandidate(text = text, confidence = 0.5)

    fun offered() = LearnedWordForgetOffer().apply { offer(chip, latestRequestId = offeredAt) }

    test("the chip shows on the pass after the space that learned the word") {
        val offer = offered()

        offer.chipFor(currentWord = "", textBeforeSelection = "hello kvazq ", requestId = newer) shouldBe chip
        // Any later pass with the cursor still there shows it again.
        offer.chipFor(currentWord = "", textBeforeSelection = "hello kvazq ", requestId = newer + 1) shouldBe chip
    }

    test("a late pass for the learned word, or part of it, keeps the offer") {
        val offer = offered()

        offer.chipFor(currentWord = "kvazq", textBeforeSelection = "hello kvazq", requestId = newer) shouldBe null
        offer.chipFor(currentWord = "kvaz", textBeforeSelection = "hello kvaz", requestId = newer) shouldBe null
        offer.chipFor(currentWord = "", textBeforeSelection = "hello kvazq ", requestId = newer) shouldBe chip
    }

    test("typing the next word ends the offer") {
        val offer = offered()

        offer.chipFor(currentWord = "a", textBeforeSelection = "hello kvazq a", requestId = newer) shouldBe null
        offer.chipFor(currentWord = "", textBeforeSelection = "hello kvazq ", requestId = newer + 1) shouldBe null
    }

    test("moving the cursor away from the word ends the offer") {
        val offer = offered()

        offer.chipFor(currentWord = "", textBeforeSelection = "hello ", requestId = newer) shouldBe null
        offer.chipFor(currentWord = "", textBeforeSelection = "hello kvazq ", requestId = newer + 1) shouldBe null
    }

    test("a pass from before the offer can't end it") {
        val offer = offered()

        // Passes started before the word was learned rank older text. They finish late, after the
        // offer, and must not end it.
        offer.chipFor(currentWord = "a", textBeforeSelection = "hello kvazq a", requestId = offeredAt) shouldBe null
        offer.chipFor(currentWord = "", textBeforeSelection = "hello ", requestId = offeredAt - 1) shouldBe null
        offer.chipFor(currentWord = "", textBeforeSelection = "hello kvazq ", requestId = newer) shouldBe chip
    }

    test("a line break after the word ends the offer") {
        val offer = offered()

        offer.chipFor(currentWord = "", textBeforeSelection = "hello kvazq\n", requestId = newer) shouldBe null
        offer.chipFor(currentWord = "", textBeforeSelection = "hello kvazq ", requestId = newer + 1) shouldBe null
    }

    test("the word has to stand alone before the cursor") {
        val offer = offered()

        offer.chipFor(currentWord = "", textBeforeSelection = "xkvazq ", requestId = newer) shouldBe null
    }

    test("a cleared offer shows nothing") {
        val offer = offered()
        offer.clear()

        offer.chipFor(currentWord = "", textBeforeSelection = "hello kvazq ", requestId = newer) shouldBe null
        offer.chipAfter("hello kvazq ") shouldBe null
    }

    test("chipAfter shows the chip right after the word and never ends the offer") {
        val offer = offered()

        offer.chipAfter("hello kvazq ") shouldBe chip
        offer.chipAfter("hello kvazq a") shouldBe null
        offer.chipAfter("hello ") shouldBe null
        offer.chipAfter("hello kvazq ") shouldBe chip
    }

    test("the chip takes the left slot and leaves the middle one the space reads") {
        val candidates = listOf(word("the"), word("a"), word("to"))

        val placed = LearnedWordForgetOffer.place(chip, candidates)

        placed shouldBe listOf(chip, word("a"), word("to"))
        LearnedWordForgetOffer.place(chip, listOf(word("the"))) shouldBe listOf(chip, word("the"))
        LearnedWordForgetOffer.place(chip, emptyList()) shouldBe listOf(chip)
    }
})
