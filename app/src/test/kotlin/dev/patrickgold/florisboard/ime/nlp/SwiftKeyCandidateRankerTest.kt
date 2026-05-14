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

    test("spacebar candidate prefers high confidence typo correction over prefix completion") {
        val candidates = SwiftKeyCandidateRanker.rank(
            context = decoderContext("Thos"),
            preferred = emptyList(),
            fallback = listOf(
                candidate("This", confidence = 0.94, autoCommit = true),
                candidate("Those", confidence = 0.78),
            ),
        )

        candidates.map { it.text.toString() } shouldBe listOf("Thos", "This", "Those")
        SwiftKeyCandidateRanker.selectSpacebarCandidate("Thos", candidates)?.text shouldBe "This"
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

    test("scoreCandidates exposes the reason a spatial correction wins") {
        val scored = SwiftKeyCandidateRanker.scoreCandidates(
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

        val hello = scored.first { it.candidate.text == "hello" }
        val fello = scored.first { it.candidate.text == "fello" }
        hello.score.role shouldBe SwiftKeyCandidateRole.SpatialCorrection
        hello.score.spatialLikelihood shouldBe 0.74
        (hello.score.total > fello.score.total) shouldBe true
    }

    test("rank promotes an interior missing-letter correction from touch alignment") {
        val ranked = SwiftKeyCandidateRanker.rank(
            context = decoderContext(
                currentWord = "ths",
                touchEvidence = touchEvidence(
                    sample("t"),
                    sample("h"),
                    sample("s"),
                ),
                signals = mapOf(
                    "this" to SwiftKeyCandidateSignals(dictionaryFrequency = 1.0),
                    "thus" to SwiftKeyCandidateSignals(dictionaryFrequency = 0.18),
                ),
            ),
            preferred = emptyList(),
            fallback = listOf(
                candidate("thus", confidence = 0.92),
                candidate("this", confidence = 0.42),
            ),
        )

        ranked.map { it.text.toString() } shouldBe listOf("ths", "this", "thus")
        SwiftKeyCandidateRanker.selectSpacebarCandidate("ths", ranked)?.text shouldBe "this"
    }

    test("rank promotes an extra-letter correction when nearby-key evidence supports deletion") {
        val ranked = SwiftKeyCandidateRanker.rank(
            context = decoderContext(
                currentWord = "thuis",
                touchEvidence = touchEvidence(
                    sample("t"),
                    sample("h"),
                    sample("u", "i" to 0.70),
                    sample("i"),
                    sample("s"),
                ),
                signals = mapOf(
                    "this" to SwiftKeyCandidateSignals(dictionaryFrequency = 1.0),
                    "thugs" to SwiftKeyCandidateSignals(dictionaryFrequency = 0.20),
                ),
            ),
            preferred = emptyList(),
            fallback = listOf(
                candidate("thugs", confidence = 0.95),
                candidate("this", confidence = 0.45),
            ),
        )

        ranked.map { it.text.toString() } shouldBe listOf("thuis", "this", "thugs")
    }

    test("rank treats accidental double letters as spatial corrections") {
        val scored = SwiftKeyCandidateRanker.scoreCandidates(
            context = decoderContext(
                currentWord = "thiis",
                touchEvidence = touchEvidence(
                    sample("t"),
                    sample("h"),
                    sample("i"),
                    sample("i"),
                    sample("s"),
                ),
                signals = mapOf(
                    "this" to SwiftKeyCandidateSignals(dictionaryFrequency = 1.0),
                    "thins" to SwiftKeyCandidateSignals(dictionaryFrequency = 0.25),
                ),
            ),
            preferred = emptyList(),
            fallback = listOf(
                candidate("thins", confidence = 0.96),
                candidate("this", confidence = 0.40),
            ),
        )

        val thisCandidate = scored.first { it.candidate.text == "this" }
        thisCandidate.score.role shouldBe SwiftKeyCandidateRole.SpatialCorrection
        thisCandidate.score.spatialLikelihood shouldBe 0.46
        scored.map { it.candidate.text.toString() } shouldBe listOf("this", "thins")
    }

    test("scoreCandidates uses dictionary frequency as a lexical prior") {
        val ranked = SwiftKeyCandidateRanker.rank(
            context = decoderContext(
                currentWord = "th",
                signals = mapOf(
                    "there" to SwiftKeyCandidateSignals(dictionaryFrequency = 0.92),
                    "thrum" to SwiftKeyCandidateSignals(dictionaryFrequency = 0.10),
                ),
            ),
            preferred = emptyList(),
            fallback = listOf(
                candidate("thrum", confidence = 0.94),
                candidate("there", confidence = 0.48),
            ),
        )

        ranked.map { it.text.toString() } shouldBe listOf("th", "there", "thrum")
    }

    test("scoreCandidates lets personal phrase context beat generic confidence") {
        val ranked = SwiftKeyCandidateRanker.rank(
            context = decoderContext(
                currentWord = "",
                signals = mapOf(
                    "brown" to SwiftKeyCandidateSignals(contextProbability = 1.0),
                    "bring" to SwiftKeyCandidateSignals(contextProbability = 0.0),
                ),
            ),
            preferred = emptyList(),
            fallback = listOf(
                candidate("bring", confidence = 0.95),
                candidate("brown", confidence = 0.40),
            ),
        )

        ranked.map { it.text.toString() } shouldBe listOf("brown", "bring")
    }

    test("scoreCandidates demotes a previously rejected autocorrect pair") {
        val ranked = SwiftKeyCandidateRanker.rank(
            context = decoderContext(
                currentWord = "teh",
                signals = mapOf(
                    "the" to SwiftKeyCandidateSignals(rejectionPenalty = 1.0),
                    "ten" to SwiftKeyCandidateSignals(rejectionPenalty = 0.0),
                ),
            ),
            preferred = emptyList(),
            fallback = listOf(
                candidate("the", confidence = 0.98, autoCommit = true),
                candidate("ten", confidence = 0.52, autoCommit = true),
            ),
        )

        ranked.map { it.text.toString() } shouldBe listOf("teh", "ten", "the")
    }

    test("accepted correction priors can promote a learned typed-corrected pair") {
        val ranked = SwiftKeyCandidateRanker.rank(
            context = decoderContext(
                currentWord = "gello",
                signals = mapOf(
                    "hello" to SwiftKeyCandidateSignals(acceptedCorrectionConfidence = 0.67),
                ),
            ),
            preferred = emptyList(),
            fallback = listOf(
                candidate("fello", confidence = 0.99),
                candidate("hello", confidence = 0.42),
            ),
        )

        ranked.map { it.text.toString() } shouldBe listOf("gello", "hello", "fello")
    }

    test("rejected correction priors lower spatial confidence before role selection") {
        val scored = SwiftKeyCandidateRanker.scoreCandidates(
            context = decoderContext(
                currentWord = "gello",
                touchEvidence = touchEvidence(
                    sample("g", "h" to 0.78),
                    sample("e"),
                    sample("l"),
                    sample("l"),
                    sample("o"),
                ),
                signals = mapOf(
                    "hello" to SwiftKeyCandidateSignals(rejectionPenalty = 1.0),
                ),
            ),
            preferred = emptyList(),
            fallback = listOf(
                candidate("fello", confidence = 0.90),
                candidate("hello", confidence = 0.80),
            ),
        )

        val hello = scored.first { it.candidate.text == "hello" }
        hello.score.role shouldBe SwiftKeyCandidateRole.Other
        (hello.score.spatialLikelihood < 0.28) shouldBe true
    }

    test("candidate tuning can evaluate spatial threshold changes") {
        val context = decoderContext(
            currentWord = "gello",
            touchEvidence = touchEvidence(
                sample("g", "h" to 0.74),
                sample("e"),
                sample("l"),
                sample("l"),
                sample("o"),
            ),
        )
        val fallback = listOf(
            candidate("fello", confidence = 0.99),
            candidate("hello", confidence = 0.40),
        )

        val defaultScored = SwiftKeyCandidateRanker.scoreCandidates(
            context = context,
            preferred = emptyList(),
            fallback = fallback,
        )
        val conservativeScored = SwiftKeyCandidateRanker.scoreCandidates(
            context = context,
            preferred = emptyList(),
            fallback = fallback,
            tuning = SwiftKeyCandidateTuning(spatialCorrectionScoreThreshold = 0.80),
        )

        defaultScored.first { it.candidate.text == "hello" }.score.role shouldBe
            SwiftKeyCandidateRole.SpatialCorrection
        conservativeScored.first { it.candidate.text == "hello" }.score.role shouldBe
            SwiftKeyCandidateRole.Other
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

    test("disabled neural reranker preserves heuristic order") {
        val scored = SwiftKeyCandidateRanker.scoreCandidates(
            context = decoderContext(
                currentWord = "",
                signals = mapOf(
                    "brown" to SwiftKeyCandidateSignals(contextProbability = 1.0),
                    "bring" to SwiftKeyCandidateSignals(contextProbability = 0.0),
                ),
            ),
            preferred = emptyList(),
            fallback = listOf(
                candidate("bring", confidence = 0.95),
                candidate("brown", confidence = 0.40),
            ),
        )

        scored.map { it.candidate.text.toString() } shouldBe listOf("brown", "bring")
    }

    test("neural reranker boundary can reorder scored candidates with heuristic backfill") {
        val ranked = SwiftKeyCandidateRanker.rank(
            context = decoderContext("th"),
            preferred = emptyList(),
            fallback = listOf(
                candidate("there", confidence = 0.91),
                candidate("this", confidence = 0.84),
                candidate("the", confidence = 0.80),
            ),
            reranker = NeuralCandidateReranker { _, scoredCandidates ->
                scoredCandidates
                    .filter { it.candidate.text == "this" }
                    .plus(scoredCandidates.first { it.candidate.text == "there" })
            },
        )

        ranked.map { it.text.toString() } shouldBe listOf("th", "this", "there", "the")
    }
})

private fun decoderContext(
    currentWord: String,
    maxCandidateCount: Int = 8,
    typedWordKnown: Boolean = false,
    touchEvidence: TouchDecoderEvidence? = null,
    signals: Map<String, SwiftKeyCandidateSignals> = emptyMap(),
): SwiftKeyDecoderContext {
    return SwiftKeyDecoderContext(
        currentWord = currentWord,
        maxCandidateCount = maxCandidateCount,
        typedWordKnown = typedWordKnown,
        touchEvidence = touchEvidence,
        candidateSignals = signals,
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
