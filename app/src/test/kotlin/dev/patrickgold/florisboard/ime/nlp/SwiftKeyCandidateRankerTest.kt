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

import dev.patrickgold.florisboard.ime.media.emoji.Emoji
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

    test("rank places known typed words in the middle slot") {
        val ranked = SwiftKeyCandidateRanker.rank(
            context = decoderContext("I", typedWordKnown = true),
            preferred = emptyList(),
            fallback = listOf(candidate("I'm", confidence = 0.92), candidate("it's", confidence = 0.75)),
        )

        ranked.map { it.text.toString() } shouldBe listOf("I'm", "I", "it's")
    }

    test("spacebar candidate follows the middle prediction when it replaces the current word") {
        val candidates = SwiftKeyCandidateRanker.rank(
            context = decoderContext("neces"),
            preferred = emptyList(),
            fallback = listOf(candidate("necessary", confidence = 0.90)),
        )

        SwiftKeyCandidateRanker.selectSpacebarCandidate("neces", candidates)?.text shouldBe "necessary"
    }

    test("spacebar candidate keeps known middle literal unchanged") {
        val candidates = SwiftKeyCandidateRanker.rank(
            context = decoderContext("the", typedWordKnown = true),
            preferred = emptyList(),
            fallback = listOf(
                candidate("they", confidence = 0.80, autoCommit = true),
                candidate("then", confidence = 0.70),
            ),
        )

        SwiftKeyCandidateRanker.selectSpacebarCandidate("the", candidates) shouldBe null
    }

    test("rank promotes a spatial correction from touch evidence") {
        val ranked = SwiftKeyCandidateRanker.rank(
            context = decoderContext(
                currentWord = "gello",
                touchEvidence = touchEvidence(
                    sample("g", "h" to 0.74),
                    sample("e"),
                    sample("l"),
                    sample("l"),
                    sample("o"),
                ),
            ),
            preferred = emptyList(),
            fallback = listOf(
                candidate("fello", confidence = 0.99),
                candidate("hello", confidence = 0.40),
            ),
        )

        ranked.map { it.text.toString() } shouldBe listOf("gello", "hello", "fello")
    }

    test("spatial evidence does not make spacebar replace a known typed word") {
        val candidates = SwiftKeyCandidateRanker.rank(
            context = decoderContext(
                currentWord = "jello",
                typedWordKnown = true,
                touchEvidence = touchEvidence(
                    sample("j", "h" to 0.82),
                    sample("e"),
                    sample("l"),
                    sample("l"),
                    sample("o"),
                ),
            ),
            preferred = emptyList(),
            fallback = listOf(candidate("hello", confidence = 0.90, autoCommit = true)),
        )

        candidates.map { it.text.toString() } shouldBe listOf("hello", "jello")
        SwiftKeyCandidateRanker.selectSpacebarCandidate("jello", candidates) shouldBe null
    }

    test("spacebar candidate ignores emoji suggestions after a known literal") {
        val candidates = listOf(
            candidate("love"),
            EmojiSuggestionCandidate(Emoji("<3", "heart", emptyList()), showName = false),
        )

        SwiftKeyCandidateRanker.selectSpacebarCandidate("love", candidates) shouldBe null
    }

    test("quick prediction insert uses the middle next-word prediction") {
        val candidates = listOf(
            candidate("I'm"),
            candidate("I"),
            candidate("it's"),
        )

        SwiftKeyCandidateRanker.selectSpacebarCandidate("", candidates) shouldBe null
        SwiftKeyCandidateRanker.selectSpacebarCandidate(
            currentWord = "",
            candidates = candidates,
            quickPredictionInsert = true,
        )?.text shouldBe "I"
    }

    test("quick prediction insert skips non-word candidates") {
        val candidates = listOf(
            EmojiSuggestionCandidate(Emoji("<3", "heart", emptyList()), showName = false),
            candidate("hello"),
        )

        SwiftKeyCandidateRanker.selectSpacebarCandidate(
            currentWord = "",
            candidates = candidates,
            quickPredictionInsert = true,
        )?.text shouldBe "hello"
    }
})

private fun decoderContext(
    currentWord: String,
    maxCandidateCount: Int = 8,
    typedWordKnown: Boolean = false,
    touchEvidence: TouchDecoderEvidence? = null,
): SwiftKeyDecoderContext {
    return SwiftKeyDecoderContext(
        currentWord = currentWord,
        maxCandidateCount = maxCandidateCount,
        typedWordKnown = typedWordKnown,
        touchEvidence = touchEvidence,
    )
}

private fun touchEvidence(vararg samples: TouchDecoderSample): TouchDecoderEvidence {
    return TouchDecoderEvidence(samples.toList())
}

private fun sample(primaryText: String, vararg alternatives: Pair<String, Double>): TouchDecoderSample {
    return TouchDecoderSample(
        primaryText = primaryText,
        alternatives = alternatives.map { alternative ->
            TouchDecoderCandidate(
                text = alternative.first,
                confidence = alternative.second,
            )
        },
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
