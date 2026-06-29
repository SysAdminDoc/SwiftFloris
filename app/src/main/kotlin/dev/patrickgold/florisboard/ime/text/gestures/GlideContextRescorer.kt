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

package dev.patrickgold.florisboard.ime.text.gestures

internal data class GlideContextTuning(
    val maxRecoverableWordLength: Int = 4,
    val maxCandidatesToRescore: Int = 4,
    val rankStepPenalty: Double = 0.12,
    val contextWeight: Double = 0.55,
    val minContextScore: Double = 0.35,
    val minSwitchMargin: Double = 0.10,
) {
    companion object {
        val Default = GlideContextTuning()
    }
}

internal object GlideContextRescorer {
    fun chooseReplacement(
        committedWord: String,
        candidateWords: List<String>,
        nextWord: String,
        contextScores: Map<String, Double>,
        tuning: GlideContextTuning = GlideContextTuning.Default,
    ): String? {
        val committed = normalizeGlideWordForContext(committedWord) ?: return null
        normalizeGlideWordForContext(nextWord) ?: return null
        if (committed.length > tuning.maxRecoverableWordLength) return null

        val candidates = candidateWords
            .mapNotNull { word ->
                normalizeGlideWordForContext(word)?.let { normalized -> normalized to word }
            }
            .distinctBy { it.first }
            .take(tuning.maxCandidatesToRescore)
        if (candidates.size < 2 || candidates.none { it.first == committed }) return null

        val current = candidates.first { it.first == committed }
        val currentScore = gestureRankPrior(candidates.indexOf(current), tuning) +
            contextScore(current.first, contextScores) * tuning.contextWeight
        val best = candidates
            .filter { it.first != committed && endpointsPlausible(committed, it.first) }
            .map { candidate ->
                val score = gestureRankPrior(candidates.indexOf(candidate), tuning) +
                    contextScore(candidate.first, contextScores) * tuning.contextWeight
                candidate to score
            }
            .maxByOrNull { it.second }
            ?: return null
        val bestContext = contextScore(best.first.first, contextScores)
        return if (bestContext >= tuning.minContextScore && best.second >= currentScore + tuning.minSwitchMargin) {
            best.first.second
        } else {
            null
        }
    }

    fun normalizeGlideWordForContext(word: String): String? {
        val normalized = word.trim()
            .trim { char -> !char.isLetter() && char != '\'' && char != '\u2019' }
            .lowercase()
        if (normalized.isBlank() || normalized.none { it.isLetter() }) return null
        if (normalized.any { char -> !char.isLetter() && char != '\'' && char != '\u2019' }) return null
        return normalized
    }

    private fun endpointsPlausible(committed: String, candidate: String): Boolean {
        val committedLetters = committed.filter { it.isLetter() }
        val candidateLetters = candidate.filter { it.isLetter() }
        if (committedLetters.isBlank() || candidateLetters.isBlank()) return false
        return endpointPlausible(committedLetters.first(), candidateLetters.first()) &&
            endpointPlausible(committedLetters.last(), candidateLetters.last())
    }

    private fun endpointPlausible(left: Char, right: Char): Boolean {
        if (left == right) return true
        return EndpointNeighbors[left]?.contains(right) == true
    }

    private fun gestureRankPrior(index: Int, tuning: GlideContextTuning): Double {
        return (1.0 - index.coerceAtLeast(0) * tuning.rankStepPenalty).coerceAtLeast(0.0)
    }

    private fun contextScore(word: String, contextScores: Map<String, Double>): Double {
        return contextScores[word]?.coerceIn(0.0, 1.0) ?: 0.0
    }

    private val EndpointNeighbors = mapOf(
        'q' to setOf('w'),
        'w' to setOf('q', 'e'),
        'e' to setOf('w', 'r'),
        'r' to setOf('e', 't'),
        't' to setOf('r', 'y'),
        'y' to setOf('t', 'u'),
        'u' to setOf('y', 'i'),
        'i' to setOf('u', 'o'),
        'o' to setOf('i', 'p'),
        'p' to setOf('o'),
        'a' to setOf('s'),
        's' to setOf('a', 'd'),
        'd' to setOf('s', 'f'),
        'f' to setOf('d', 'g'),
        'g' to setOf('f', 'h'),
        'h' to setOf('g', 'j'),
        'j' to setOf('h', 'k'),
        'k' to setOf('j', 'l'),
        'l' to setOf('k'),
        'z' to setOf('x'),
        'x' to setOf('z', 'c'),
        'c' to setOf('x', 'v'),
        'v' to setOf('c', 'b'),
        'b' to setOf('v', 'n'),
        'n' to setOf('b', 'm'),
        'm' to setOf('n'),
    )
}
