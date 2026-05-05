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

class SuggestionCandidateMergerTest : FunSpec({
    test("mergePreferred keeps preferred suggestions ahead of fallback suggestions") {
        val merged = SuggestionCandidateMerger.mergePreferred(
            preferred = listOf(candidate("SwiftFloris")),
            fallback = listOf(candidate("swift"), candidate("keyboard")),
            maxCandidateCount = 3,
        )

        merged.map { it.text.toString() } shouldBe listOf("SwiftFloris", "swift", "keyboard")
    }

    test("mergePreferred removes fallback duplicates case-insensitively") {
        val merged = SuggestionCandidateMerger.mergePreferred(
            preferred = listOf(candidate("OpenAI")),
            fallback = listOf(candidate("openai"), candidate("offline")),
            maxCandidateCount = 3,
        )

        merged.map { it.text.toString() } shouldBe listOf("OpenAI", "offline")
    }

    test("mergePreferred respects the max candidate count") {
        val merged = SuggestionCandidateMerger.mergePreferred(
            preferred = listOf(candidate("one"), candidate("two")),
            fallback = listOf(candidate("three"), candidate("four")),
            maxCandidateCount = 3,
        )

        merged.map { it.text.toString() } shouldBe listOf("one", "two", "three")
    }
})

private fun candidate(text: String): SuggestionCandidate {
    return WordSuggestionCandidate(text = text)
}
