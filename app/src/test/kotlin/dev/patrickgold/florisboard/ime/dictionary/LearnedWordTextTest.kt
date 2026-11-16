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

package dev.patrickgold.florisboard.ime.dictionary

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LearnedWordTextTest : FunSpec({
    test("a word is lowercased and loses the punctuation around it") {
        LearnedWordText.normalize("  Hello, ") shouldBe "hello"
        LearnedWordText.normalize("\"Zorblat!\"") shouldBe "zorblat"
    }

    test("apostrophes and hyphens stay, inside the word or at its ends") {
        LearnedWordText.normalize("Don't") shouldBe "don't"
        LearnedWordText.normalize("well-known") shouldBe "well-known"
        LearnedWordText.normalize("well-") shouldBe "well-"
    }

    test("words outside the learnable length are refused") {
        LearnedWordText.normalize("an") shouldBe null
        LearnedWordText.normalize("abc") shouldBe "abc"
        LearnedWordText.normalize("a".repeat(LearnedWordText.MAX_LENGTH)) shouldBe "a".repeat(LearnedWordText.MAX_LENGTH)
        LearnedWordText.normalize("a".repeat(LearnedWordText.MAX_LENGTH + 1)) shouldBe null
    }

    test("digits and symbols inside a token refuse it, at its ends they're trimmed") {
        LearnedWordText.normalize("ab1cd") shouldBe null
        LearnedWordText.normalize("user@example") shouldBe null
        LearnedWordText.normalize("abc123") shouldBe "abc"
    }

    test("tokens with no letters are refused") {
        LearnedWordText.normalize("'--'") shouldBe null
        LearnedWordText.normalize("   ") shouldBe null
    }
})
