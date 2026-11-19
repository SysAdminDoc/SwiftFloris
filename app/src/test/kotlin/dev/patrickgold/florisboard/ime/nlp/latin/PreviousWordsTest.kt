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
import io.kotest.matchers.shouldBe

class PreviousWordsTest : FunSpec({
    test("reads the words before the cursor") {
        PreviousWords.inSentence("see you ") shouldBe "you"
        PreviousWords.inSentence("see you ", depth = 2) shouldBe "see"
        PreviousWords.inSentence("I don't ") shouldBe "don't"
        PreviousWords.inSentence("a well-known ", depth = 1) shouldBe "well-known"
    }

    test("commas and other in-sentence punctuation are skipped") {
        PreviousWords.inSentence("yes, please ", depth = 2) shouldBe "yes"
        PreviousWords.inSentence("(soon) ") shouldBe "soon"
    }

    test("a sentence end or line break means there's no previous word") {
        PreviousWords.inSentence("Thanks. ") shouldBe null
        PreviousWords.inSentence("Great! ") shouldBe null
        PreviousWords.inSentence("Really? ") shouldBe null
        PreviousWords.inSentence("Wait… ") shouldBe null
        PreviousWords.inSentence("Thanks. See ") shouldBe "See"
        PreviousWords.inSentence("Thanks. See ", depth = 2) shouldBe null
        PreviousWords.inSentence("first line\n") shouldBe null
        PreviousWords.inSentence("first line\nnext ", depth = 2) shouldBe null
    }

    test("empty or wordless text has no previous word") {
        PreviousWords.inSentence("") shouldBe null
        PreviousWords.inSentence("   ") shouldBe null
        PreviousWords.inSentence("42 ") shouldBe null
        PreviousWords.inSentence("one ", depth = 2) shouldBe null
        PreviousWords.inSentence("one ", depth = 0) shouldBe null
    }
})
