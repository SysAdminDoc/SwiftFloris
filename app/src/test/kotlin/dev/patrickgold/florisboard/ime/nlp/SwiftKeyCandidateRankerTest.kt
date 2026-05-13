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

class SwiftKeyCandidateRankerTest : FunSpec({
    test("rank keeps the typed literal visible before autocorrect candidates") {
        val ranked = SwiftKeyCandidateRanker.rank(
            context = decoderContext("teh"),
            preferred = emptyList(),
            fallback = listOf(candidate("the", confidence = 0.98, autoCommit = true)),
        )

        ranked.map { it.text.toString() } shouldBe listOf("teh", "the")
        ranked[1].isEligibleForAutoCommit shouldBe true
    }

    test("rank keeps personal suggestions ahead of equivalent fallback suggestions") {
        val ranked = SwiftKeyCandidateRanker.rank(
            context = decoderContext("sw"),
            preferred = listOf(candidate("SwiftFloris", confidence = 0.42)),
            fallback = listOf(candidate("swift", confidence = 0.95), candidate("SwiftFloris", confidence = 0.99)),
        )

        ranked.map { it.text.toString() } shouldBe listOf("sw", "SwiftFloris", "swift")
    }

    test("rank deduplicates case-insensitively and respects the candidate cap") {
        val ranked = SwiftKeyCandidateRanker.rank(
            context = decoderContext("op", maxCandidateCount = 3),
            preferred = listOf(candidate("OpenAI"), candidate("openai")),
            fallback = listOf(candidate("open"), candidate("offline")),
        )

        ranked.map { it.text.toString() } shouldBe listOf("op", "OpenAI", "open")
    }

    test("rank does not inject a literal for blank or non-word input") {
        SwiftKeyCandidateRanker.rank(
            context = decoderContext("123"),
            preferred = emptyList(),
            fallback = listOf(candidate("one")),
        ).map { it.text.toString() } shouldBe listOf("one")

        SwiftKeyCandidateRanker.rank(
            context = decoderContext(""),
            preferred = emptyList(),
            fallback = listOf(candidate("next")),
        ).map { it.text.toString() } shouldBe listOf("next")
    }
})

private fun decoderContext(
    currentWord: String,
    maxCandidateCount: Int = 8,
): SwiftKeyDecoderContext {
    return SwiftKeyDecoderContext(
        currentWord = currentWord,
        maxCandidateCount = maxCandidateCount,
    )
}

private fun candidate(
    text: String,
    confidence: Double = 0.5,
    autoCommit: Boolean = false,
): SuggestionCandidate {
    return WordSuggestionCandidate(
        text = text,
        confidence = confidence,
        isEligibleForAutoCommit = autoCommit,
    )
}
