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

internal object GlideContextRescorer {
    fun chooseReplacement(
        committedWord: String,
        candidateWords: List<String>,
        nextWord: String,
        contextScores: Map<String, Double>,
    ): String? {
        val committed = normalizeGlideWordForContext(committedWord) ?: return null
        normalizeGlideWordForContext(nextWord) ?: return null
        if (committed.length > MaxRecoverableWordLength) return null

        val candidates = candidateWords
            .mapNotNull { word ->
                normalizeGlideWordForContext(word)?.let { normalized -> normalized to word }
            }
            .distinctBy { it.first }
            .take(MaxCandidatesToRescore)
        if (candidates.size < 2 || candidates.none { it.first == committed }) return null

        val current = candidates.first { it.first == committed }
        val currentScore = gestureRankPrior(candidates.indexOf(current)) +
            contextScore(current.first, contextScores) * ContextWeight
        val best = candidates
            .filter { it.first != committed }
            .map { candidate ->
                val score = gestureRankPrior(candidates.indexOf(candidate)) +
                    contextScore(candidate.first, contextScores) * ContextWeight
                candidate to score
            }
            .maxByOrNull { it.second }
            ?: return null
        val bestContext = contextScore(best.first.first, contextScores)
        return if (bestContext >= MinContextScore && best.second >= currentScore + MinSwitchMargin) {
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

    private fun gestureRankPrior(index: Int): Double {
        return (1.0 - index.coerceAtLeast(0) * RankStepPenalty).coerceAtLeast(0.0)
    }

    private fun contextScore(word: String, contextScores: Map<String, Double>): Double {
        return contextScores[word]?.coerceIn(0.0, 1.0) ?: 0.0
    }

    private const val MaxRecoverableWordLength = 4
    private const val MaxCandidatesToRescore = 4
    private const val RankStepPenalty = 0.12
    private const val ContextWeight = 0.55
    private const val MinContextScore = 0.35
    private const val MinSwitchMargin = 0.10
}
