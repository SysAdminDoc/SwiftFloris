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
import io.kotest.matchers.shouldBe

private val CurlyApostrophe = Char(0x2019).toString()

class TypedWordKnowledgeTest : FunSpec({
    test("a word the last pass knew reads as known, whatever its case or trailing punctuation") {
        val knowledge = TypedWordKnowledge()
        knowledge.record("Swift", "en-US", known = true)

        knowledge.knownState("swift", "en-US") shouldBe true
        knowledge.knownState("SWIFT,", "en-US") shouldBe true
    }

    test("a word the last pass didn't know reads as unknown") {
        val knowledge = TypedWordKnowledge()
        knowledge.record("thw", "en-US", known = false)

        knowledge.knownState("thw", "en-US") shouldBe false
    }

    test("a pass for another word says nothing about this one") {
        val knowledge = TypedWordKnowledge()
        // The space came before the pass for the finished word ran.
        knowledge.record("swif", "en-US", known = true)

        knowledge.knownState("swift", "en-US") shouldBe null
    }

    test("a pass in another language says nothing about this one") {
        val knowledge = TypedWordKnowledge()
        knowledge.record("hund", "de-DE", known = true)

        knowledge.knownState("hund", "en-US") shouldBe null
    }

    test("no pass yet, or a cleared strip, says nothing") {
        val knowledge = TypedWordKnowledge()
        knowledge.knownState("swift", "en-US") shouldBe null

        knowledge.record("swift", "en-US", known = true)
        knowledge.clear()
        knowledge.knownState("swift", "en-US") shouldBe null
    }

    test("a pass that didn't look, with suggestions off, records nothing") {
        val knowledge = TypedWordKnowledge()
        knowledge.record("swift", "en-US", known = true)
        knowledge.record("swift", "en-US", known = null)

        knowledge.knownState("swift", "en-US") shouldBe null
    }

    test("a word whose learned form isn't the looked-up one records nothing") {
        val knowledge = TypedWordKnowledge()
        // The lookup drops the trailing hyphen and finds "well", but the space would learn "well-".
        knowledge.record("well-", "en-US", known = true)

        knowledge.knownState("well-", "en-US") shouldBe null
    }

    test("a word too short to learn says nothing") {
        val knowledge = TypedWordKnowledge()
        knowledge.record("an", "en-US", known = true)

        knowledge.knownState("an", "en-US") shouldBe null
    }

    test("the key keeps apostrophes, straight or curly, and drops other punctuation") {
        candidateSignalKeyOf("  Don't! ") shouldBe "don't"
        candidateSignalKeyOf("don${CurlyApostrophe}t") shouldBe "don${CurlyApostrophe}t"
        candidateSignalKeyOf("123") shouldBe null
        candidateSignalKeyOf("  ") shouldBe null
    }
})
