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

class TypingContextExtractorTest : FunSpec({
    test("strips the active current word from the context prefix") {
        TypingContextExtractor.prefixBeforeCurrentWord(
            textBeforeSelection = "Let me kn",
            currentWord = "kn",
        ) shouldBe "Let me "
    }

    test("extracts previous words from the current sentence only") {
        TypingContextExtractor.previousWordsBeforeCurrentWord(
            textBeforeSelection = "Hola amigo. Let me kn",
            currentWord = "kn",
        ) shouldBe PreviousWordContext(prev2 = "Let", prev1 = "me")
    }

    test("extracts up to four trailing words for language context") {
        TypingContextExtractor.previousWordListBeforeCurrentWord(
            textBeforeSelection = "Please let me know if grac",
            currentWord = "grac",
            maxDepth = 4,
        ) shouldBe listOf("let", "me", "know", "if")
    }

    test("does not carry locale context across newlines") {
        TypingContextExtractor.previousWordsBeforeCurrentWord(
            textBeforeSelection = "hola amigo\nThanks fo",
            currentWord = "fo",
        ) shouldBe PreviousWordContext(prev2 = null, prev1 = "Thanks")
    }

    test("keeps apostrophe and hyphen words intact inside the active sentence") {
        TypingContextExtractor.previousWordsBeforeCurrentWord(
            textBeforeSelection = "We're cross-checking th",
            currentWord = "th",
        ) shouldBe PreviousWordContext(prev2 = "We're", prev1 = "cross-checking")
    }
})
