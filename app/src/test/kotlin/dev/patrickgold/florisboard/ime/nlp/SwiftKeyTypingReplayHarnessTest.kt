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

class SwiftKeyTypingReplayHarnessTest : FunSpec({
    test("replay adjacent-key taps promote the intended correction") {
        val result = replayRankerCase(
            ReplayRankerCase(
                currentWord = "gello",
                touchSamples = listOf(
                    touchSample("g", "h" to 0.78),
                    touchSample("e"),
                    touchSample("l"),
                    touchSample("l"),
                    touchSample("o"),
                ),
                fallback = listOf(
                    replayCandidate("fello", confidence = 0.99),
                    replayCandidate("hello", confidence = 0.42),
                ),
                expectedRankedText = listOf("gello", "hello", "fello"),
                expectedSpacebarText = "hello",
            )
        )

        result.scored.first { it.candidate.text == "hello" }.score.role shouldBe
            SwiftKeyCandidateRole.SpatialCorrection
    }

    test("replay known-word spatial evidence keeps spacebar from replacing the literal") {
        replayRankerCase(
            ReplayRankerCase(
                currentWord = "jello",
                typedWordKnown = true,
                touchSamples = listOf(
                    touchSample("j", "h" to 0.82),
                    touchSample("e"),
                    touchSample("l"),
                    touchSample("l"),
                    touchSample("o"),
                ),
                fallback = listOf(replayCandidate("hello", confidence = 0.91, autoCommit = true)),
                expectedRankedText = listOf("hello", "jello"),
                expectedSpacebarText = null,
            )
        )
    }

    test("replay quick prediction mode uses the middle word candidate as the action") {
        val candidates = listOf(
            replayCandidate("I'm", confidence = 0.92),
            replayCandidate("I", confidence = 0.88),
            replayCandidate("it's", confidence = 0.74),
        )

        SwiftKeyCandidateRanker.selectSpacebarCandidate(
            currentWord = "",
            candidates = candidates,
            quickPredictionInsert = true,
        )?.text shouldBe "I"
    }
})

private data class ReplayRankerCase(
    val currentWord: String,
    val typedWordKnown: Boolean = false,
    val touchSamples: List<TouchDecoderSample> = emptyList(),
    val preferred: List<SuggestionCandidate> = emptyList(),
    val fallback: List<SuggestionCandidate> = emptyList(),
    val expectedRankedText: List<String>,
    val expectedSpacebarText: String?,
)

private data class ReplayResult(
    val ranked: List<SuggestionCandidate>,
    val scored: List<SwiftKeyScoredCandidate>,
)

private fun replayRankerCase(case: ReplayRankerCase): ReplayResult {
    val touchEvidence = replayTouchEvidence(
        currentWord = case.currentWord,
        samples = case.touchSamples,
    )
    val context = SwiftKeyDecoderContext(
        currentWord = case.currentWord,
        maxCandidateCount = 8,
        typedWordKnown = case.typedWordKnown,
        touchEvidence = touchEvidence,
    )
    val ranked = SwiftKeyCandidateRanker.rank(
        context = context,
        preferred = case.preferred,
        fallback = case.fallback,
    )
    ranked.map { it.text.toString() } shouldBe case.expectedRankedText
    SwiftKeyCandidateRanker.selectSpacebarCandidate(
        currentWord = case.currentWord,
        candidates = ranked,
    )?.text?.toString() shouldBe case.expectedSpacebarText
    return ReplayResult(
        ranked = ranked,
        scored = SwiftKeyCandidateRanker.scoreCandidates(
            context = context,
            preferred = case.preferred,
            fallback = case.fallback,
        ),
    )
}

private fun replayTouchEvidence(
    currentWord: String,
    samples: List<TouchDecoderSample>,
): TouchDecoderEvidence? {
    val buffer = TouchDecoderEvidenceBuffer()
    for (sample in samples) {
        buffer.record(sample)
    }
    return buffer.evidenceFor(currentWord)
}

private fun touchSample(primaryText: String, vararg alternatives: Pair<String, Double>): TouchDecoderSample {
    return TouchDecoderSample(
        primaryText = primaryText,
        alternatives = alternatives.map { alternative ->
            TouchDecoderCandidate(text = alternative.first, confidence = alternative.second)
        },
    )
}

private fun replayCandidate(
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
