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

package dev.patrickgold.florisboard.ime.nlp.advanced

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AdvancedPredictionEngineTest : FunSpec({
    val dictionary = setOf(
        "there",
        "their",
        "theory",
        "toast",
        "swift",
    )
    val frequencies = mapOf(
        "there" to 0.94,
        "their" to 0.81,
        "theory" to 0.64,
        "toast" to 0.52,
        "swift" to 0.76,
    )
    val bigrams = mapOf(
        "hello" to listOf(
            "there" to 0.91,
            "swift" to 0.82,
            "toast" to 0.43,
        ),
    )

    test("suggest returns empty results for blank short or zero-count input") {
        predict("", dictionary, bigrams, frequencies, maxCandidateCount = 3) shouldBe emptyList()
        predict("a", dictionary, bigrams, frequencies, maxCandidateCount = 3) shouldBe emptyList()
        predict("th", dictionary, bigrams, frequencies, maxCandidateCount = 0) shouldBe emptyList()
    }

    test("suggest returns deterministic frequency-ranked completions") {
        val suggestions = predict("th", dictionary, bigrams, frequencies, maxCandidateCount = 3)

        suggestions.map { it.text } shouldBe listOf("there", "their", "theory")
        suggestions.map { it.isEligibleForAutoCommit } shouldBe listOf(true, true, false)
    }

    test("suggest appends context predictions without duplicating completions") {
        val suggestions = predict("hello th", dictionary, bigrams, frequencies, maxCandidateCount = 4)

        suggestions.map { it.text } shouldBe listOf("there", "their", "theory", "swift")
        suggestions.last().confidence shouldBe 0.82
        suggestions.last().isEligibleForAutoCommit shouldBe true
    }

    test("suggest trims trailing whitespace before extracting context") {
        val suggestions = predict("hello th   ", dictionary, bigrams, frequencies, maxCandidateCount = 2)

        suggestions.map { it.text } shouldBe listOf("there", "their")
    }
})

private fun predict(
    textBeforeSelection: String,
    dictionary: Set<String>,
    bigramPredictions: Map<String, List<Pair<String, Double>>>,
    frequencies: Map<String, Double>,
    maxCandidateCount: Int,
): List<AdvancedPredictionSuggestion> {
    return AdvancedPredictionEngine.suggest(
        textBeforeSelection = textBeforeSelection,
        dictionary = dictionary,
        bigramPredictions = bigramPredictions,
        maxCandidateCount = maxCandidateCount,
        frequencyForWord = { word -> frequencies.getValue(word) },
    )
}
