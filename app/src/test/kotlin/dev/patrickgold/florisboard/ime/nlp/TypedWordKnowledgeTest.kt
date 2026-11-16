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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe

private val CurlyApostrophe = Char(0x2019).toString()

class TypedWordKnowledgeTest : FunSpec({
    test("a word the last pass knew reads as known, whatever its case or trailing punctuation") {
        val knowledge = TypedWordKnowledge()
        knowledge.record("Swift", known = true)

        knowledge.wasKnown("swift").shouldBeTrue()
        knowledge.wasKnown("SWIFT,").shouldBeTrue()
    }

    test("a word the last pass didn't know reads as unknown") {
        val knowledge = TypedWordKnowledge()
        knowledge.record("thw", known = false)

        knowledge.wasKnown("thw").shouldBeFalse()
    }

    test("a pass for another word says nothing about this one") {
        val knowledge = TypedWordKnowledge()
        // The space came before the pass for the finished word ran.
        knowledge.record("swif", known = true)

        knowledge.wasKnown("swift").shouldBeFalse()
    }

    test("no pass yet, or a cleared strip, reads as unknown") {
        val knowledge = TypedWordKnowledge()
        knowledge.wasKnown("swift").shouldBeFalse()

        knowledge.record("swift", known = true)
        knowledge.clear()
        knowledge.wasKnown("swift").shouldBeFalse()
    }

    test("the key keeps apostrophes, straight or curly, and drops other punctuation") {
        candidateSignalKeyOf("  Don't! ") shouldBe "don't"
        candidateSignalKeyOf("don${CurlyApostrophe}t") shouldBe "don${CurlyApostrophe}t"
        candidateSignalKeyOf("123") shouldBe null
        candidateSignalKeyOf("  ") shouldBe null
    }
})
