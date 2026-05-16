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

class SentenceTokenizerTest : FunSpec({

    test("split: empty input yields empty list") {
        SentenceTokenizer.split("") shouldBe emptyList()
    }

    test("split: input with no terminator yields one-entry list") {
        SentenceTokenizer.split("hello world") shouldBe listOf("hello world")
    }

    test("split: two English sentences with trailing whitespace") {
        SentenceTokenizer.split("Hello. World!") shouldBe listOf("Hello. ", "World!")
    }

    test("split: stitching round-trip preserves the original text") {
        val text = "First sentence. Second one! Third? Fourth."
        val parts = SentenceTokenizer.split(text)
        parts.joinToString("") shouldBe text
    }

    test("split: consecutive terminators collapse into one boundary") {
        SentenceTokenizer.split("Wait!? Really.") shouldBe listOf("Wait!? ", "Really.")
    }

    test("split: CJK ideographic full stop terminates") {
        val text = "你好。世界"
        SentenceTokenizer.split(text) shouldBe listOf("你好。", "世界")
    }

    test("split: Devanagari danda terminates") {
        val text = "नमस्ते। दुनिया"
        SentenceTokenizer.split(text) shouldBe listOf("नमस्ते। ", "दुनिया")
    }

    test("split: Arabic question mark terminates") {
        val text = "كيف حالك؟ أنا بخير"
        SentenceTokenizer.split(text) shouldBe listOf("كيف حالك؟ ", "أنا بخير")
    }

    test("hasMultipleSentences: true for two-sentence input") {
        SentenceTokenizer.hasMultipleSentences("Hello. World!") shouldBe true
    }

    test("hasMultipleSentences: false for single-sentence input") {
        SentenceTokenizer.hasMultipleSentences("Hello world") shouldBe false
        SentenceTokenizer.hasMultipleSentences("Hello world.") shouldBe false
    }
})
