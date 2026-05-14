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

import kotlin.math.ln

internal data class SwiftKeyDecoderContext(
    val currentWord: String,
    val maxCandidateCount: Int,
    val typedWordKnown: Boolean = false,
    val touchEvidence: TouchDecoderEvidence? = null,
    val candidateSignals: Map<String, SwiftKeyCandidateSignals> = emptyMap(),
)

internal data class SwiftKeyCandidateSignals(
    val dictionaryFrequency: Double = 0.0,
    val contextProbability: Double = 0.0,
    val languageConfidence: Double = 1.0,
    val acceptedCorrectionConfidence: Double = 0.0,
    val rejectionPenalty: Double = 0.0,
)

internal data class SwiftKeyScoredCandidate(
    val candidate: SuggestionCandidate,
    val originalIndex: Int,
    val source: SwiftKeyCandidateSource,
    val score: SwiftKeyCandidateScore,
)

internal data class SwiftKeyCandidateTuning(
    val roleWeight: Double = 22.0,
    val spatialWeight: Double = 28.0,
    val sourceWeight: Double = 14.0,
    val confidenceWeight: Double = 8.0,
    val dictionaryWeight: Double = 12.0,
    val contextWeight: Double = 20.0,
    val languageWeight: Double = 8.0,
    val acceptedCorrectionWeight: Double = 7.0,
    val editWeight: Double = 5.0,
    val completionWeight: Double = 3.0,
    val rejectionWeight: Double = 32.0,
    val spatialCorrectionScoreThreshold: Double = 0.28,
    val acceptedCorrectionSpatialBoost: Double = 0.46,
    val rejectedCorrectionSpatialPenalty: Double = 0.58,
) {
    companion object {
        val Default = SwiftKeyCandidateTuning()
    }
}

internal data class SwiftKeyCandidateScore(
    val role: SwiftKeyCandidateRole,
    val rolePriority: Double,
    val spatialLikelihood: Double,
    val providerConfidence: Double,
    val sourceAffinity: Double,
    val editProximity: Double,
    val completionAffinity: Double,
    val dictionaryFrequency: Double,
    val contextProbability: Double,
    val languageConfidence: Double,
    val acceptedCorrectionConfidence: Double,
    val rejectionPenalty: Double,
    val lengthPenalty: Double,
    val tuning: SwiftKeyCandidateTuning = SwiftKeyCandidateTuning.Default,
) {
    val total: Double =
        evidence(rolePriority / MaxRolePriority) * tuning.roleWeight +
            evidence(spatialLikelihood) * tuning.spatialWeight +
            sourceAffinity * tuning.sourceWeight +
            evidence(providerConfidence) * tuning.confidenceWeight +
            evidence(dictionaryFrequency) * tuning.dictionaryWeight +
            evidence(contextProbability) * tuning.contextWeight +
            evidence(languageConfidence) * tuning.languageWeight +
            evidence(acceptedCorrectionConfidence) * tuning.acceptedCorrectionWeight +
            evidence(editProximity) * tuning.editWeight +
            evidence(completionAffinity) * tuning.completionWeight -
            rejectionPenalty.coerceIn(0.0, 1.0) * tuning.rejectionWeight -
            lengthPenalty

    private companion object {
        const val MaxRolePriority = 5.0
        val LogDenominator: Double = ln(10.0)

        fun evidence(value: Double): Double {
            val normalized = value.coerceIn(0.0, 1.0)
            if (normalized <= 0.0) return 0.0
            return ln(1.0 + 9.0 * normalized) / LogDenominator
        }
    }
}

internal enum class SwiftKeyCandidateSource(val affinity: Double) {
    Preferred(1.0),
    Fallback(0.5),
}

internal enum class SwiftKeyCandidateRole(val priority: Double) {
    TypedLiteral(5.0),
    SpatialCorrection(4.5),
    AutoCorrection(4.0),
    Completion(3.0),
    Other(1.0),
}

internal object SwiftKeyCandidateRanker {
    fun rank(
        context: SwiftKeyDecoderContext,
        preferred: List<SuggestionCandidate>,
        fallback: List<SuggestionCandidate>,
        reranker: NeuralCandidateReranker = NeuralCandidateReranker.Disabled,
        tuning: SwiftKeyCandidateTuning = SwiftKeyCandidateTuning.Default,
    ): List<SuggestionCandidate> {
        if (context.maxCandidateCount <= 0) {
            return emptyList()
        }

        val currentWord = context.currentWord.trim()
        val typedWordKey = currentWord.normalizedCandidateKey()
        val canShowTypedLiteral = currentWord.isWordLike()
        val rankedSuggestions = rankedCandidates(
            currentWord = currentWord,
            touchEvidence = context.touchEvidence,
            signals = context.candidateSignals,
            preferred = preferred,
            fallback = fallback,
            reranker = reranker,
            tuning = tuning,
        )

        val typedLiteral = WordSuggestionCandidate(
            text = currentWord,
            confidence = TypedLiteralConfidence,
            isEligibleForAutoCommit = false,
            isEligibleForUserRemoval = false,
        )

        val seen = mutableSetOf<String>()
        return buildList {
            if (canShowTypedLiteral && context.typedWordKnown) {
                if (context.maxCandidateCount >= 2) {
                    rankedSuggestions.firstOrNull { candidate ->
                        val key = candidate.text.toString().normalizedCandidateKey()
                        key.isNotBlank() && key != typedWordKey
                    }?.let { leadingCandidate ->
                        add(leadingCandidate)
                        seen.add(leadingCandidate.text.toString().normalizedCandidateKey())
                    }
                }
                add(typedLiteral)
                seen.add(typedWordKey)
            } else if (canShowTypedLiteral) {
                add(typedLiteral)
                seen.add(typedWordKey)
            }

            for (candidate in rankedSuggestions) {
                val key = candidate.text.toString().normalizedCandidateKey()
                if (key.isNotBlank() && seen.add(key)) {
                    add(candidate)
                    if (size >= context.maxCandidateCount) {
                        break
                    }
                }
            }
        }.take(context.maxCandidateCount)
    }

    fun scoreCandidates(
        context: SwiftKeyDecoderContext,
        preferred: List<SuggestionCandidate>,
        fallback: List<SuggestionCandidate>,
        reranker: NeuralCandidateReranker = NeuralCandidateReranker.Disabled,
        tuning: SwiftKeyCandidateTuning = SwiftKeyCandidateTuning.Default,
    ): List<SwiftKeyScoredCandidate> {
        val currentWord = context.currentWord.trim()
        val typedWordKey = currentWord.normalizedCandidateKey()
        val heuristicRanking = (preferred.mapIndexed { index, candidate ->
            scoredCandidate(
                candidate = candidate,
                originalIndex = index,
                source = SwiftKeyCandidateSource.Preferred,
                typedWordKey = typedWordKey,
                currentWord = currentWord,
                touchEvidence = context.touchEvidence,
                signals = context.candidateSignals,
                tuning = tuning,
            )
        } + fallback.mapIndexed { index, candidate ->
            scoredCandidate(
                candidate = candidate,
                originalIndex = index,
                source = SwiftKeyCandidateSource.Fallback,
                typedWordKey = typedWordKey,
                currentWord = currentWord,
                touchEvidence = context.touchEvidence,
                signals = context.candidateSignals,
                tuning = tuning,
            )
        }).sortedWith(ScoredCandidateComparator)
        return safeRerank(context, heuristicRanking, reranker)
    }

    fun selectSpacebarCandidate(
        currentWord: String,
        candidates: List<SuggestionCandidate>,
        quickPredictionInsert: Boolean = false,
        candidateSignals: Map<String, SwiftKeyCandidateSignals> = emptyMap(),
    ): SuggestionCandidate? {
        val typedWordKey = currentWord.trim().normalizedCandidateKey()
        if (!currentWord.trim().isWordLike() || typedWordKey.isBlank()) {
            return if (quickPredictionInsert) {
                nextWordSpacebarCandidate(candidates)
            } else {
                null
            }
        }

        val middleCandidate = candidates.getOrNull(1)
        val middleCandidateKey = middleCandidate?.text?.toString()?.normalizedCandidateKey()
        if (middleCandidateKey == typedWordKey) {
            return null
        }
        if (middleCandidate is WordSuggestionCandidate &&
            middleCandidateKey != null &&
            middleCandidateKey != typedWordKey &&
            languageConfidenceAllowsAutoCommit(middleCandidate, candidateSignals)
        ) {
            return middleCandidate
        }

        return candidates.firstOrNull { candidate ->
            candidate.isEligibleForAutoCommit &&
                candidate.text.toString().normalizedCandidateKey() != typedWordKey &&
                languageConfidenceAllowsAutoCommit(candidate, candidateSignals)
        }
    }

    fun languageConfidenceAllowsAutoCommit(
        candidate: SuggestionCandidate,
        candidateSignals: Map<String, SwiftKeyCandidateSignals>,
    ): Boolean {
        val key = candidate.text.toString().normalizedCandidateKey()
        if (key.isBlank()) return true
        return (candidateSignals[key]?.languageConfidence ?: 1.0) >= MinAutoCommitLanguageConfidence
    }

    private fun nextWordSpacebarCandidate(candidates: List<SuggestionCandidate>): SuggestionCandidate? {
        val middleCandidate = candidates.getOrNull(1)
        if (middleCandidate is WordSuggestionCandidate) {
            return middleCandidate
        }
        return candidates.firstOrNull { it is WordSuggestionCandidate }
    }

    private fun rankedCandidates(
        currentWord: String,
        touchEvidence: TouchDecoderEvidence?,
        signals: Map<String, SwiftKeyCandidateSignals>,
        preferred: List<SuggestionCandidate>,
        fallback: List<SuggestionCandidate>,
        reranker: NeuralCandidateReranker,
        tuning: SwiftKeyCandidateTuning,
    ): List<SuggestionCandidate> {
        return scoreCandidates(
            context = SwiftKeyDecoderContext(
                currentWord = currentWord,
                maxCandidateCount = Int.MAX_VALUE,
                touchEvidence = touchEvidence,
                candidateSignals = signals,
            ),
            preferred = preferred,
            fallback = fallback,
            reranker = reranker,
            tuning = tuning,
        ).map { it.candidate }
    }

    private fun safeRerank(
        context: SwiftKeyDecoderContext,
        heuristicRanking: List<SwiftKeyScoredCandidate>,
        reranker: NeuralCandidateReranker,
    ): List<SwiftKeyScoredCandidate> {
        if (heuristicRanking.isEmpty()) return heuristicRanking
        val reranked = runCatching {
            reranker.rerank(context, heuristicRanking)
        }.getOrDefault(heuristicRanking)
        if (reranked === heuristicRanking) return heuristicRanking

        val heuristicByKey = heuristicRanking.associateBy { it.rerankKey() }
        val seen = HashSet<String>(heuristicRanking.size)
        return buildList {
            for (candidate in reranked) {
                val key = candidate.rerankKey()
                val original = heuristicByKey[key] ?: continue
                if (seen.add(key)) {
                    add(original)
                }
            }
            for (candidate in heuristicRanking) {
                if (seen.add(candidate.rerankKey())) {
                    add(candidate)
                }
            }
        }
    }

    private fun SwiftKeyScoredCandidate.rerankKey(): String {
        return "${source.name}:$originalIndex:${candidate.text.toString().normalizedCandidateKey()}"
    }

    private fun scoredCandidate(
        candidate: SuggestionCandidate,
        originalIndex: Int,
        source: SwiftKeyCandidateSource,
        typedWordKey: String,
        currentWord: String,
        touchEvidence: TouchDecoderEvidence?,
        signals: Map<String, SwiftKeyCandidateSignals>,
        tuning: SwiftKeyCandidateTuning,
    ): SwiftKeyScoredCandidate {
        val candidateKey = candidate.text.toString().normalizedCandidateKey()
        val signal = signals[candidateKey] ?: SwiftKeyCandidateSignals()
        val rawSpatialLikelihood = touchEvidence?.spatialReplacementScore(candidate.text, currentWord)
            ?.coerceIn(0.0, 1.0)
            ?: 0.0
        val spatialLikelihood = outcomeAdjustedSpatialLikelihood(rawSpatialLikelihood, signal, tuning)
        val role = candidate.role(typedWordKey, spatialLikelihood, tuning)
        val score = SwiftKeyCandidateScore(
            role = role,
            rolePriority = role.priority,
            spatialLikelihood = spatialLikelihood,
            providerConfidence = candidate.confidence.coerceIn(0.0, 1.0),
            sourceAffinity = source.affinity,
            editProximity = editProximity(typedWordKey, candidateKey),
            completionAffinity = completionAffinity(typedWordKey, candidateKey),
            dictionaryFrequency = signal.dictionaryFrequency,
            contextProbability = signal.contextProbability,
            languageConfidence = signal.languageConfidence,
            acceptedCorrectionConfidence = signal.acceptedCorrectionConfidence,
            rejectionPenalty = signal.rejectionPenalty,
            lengthPenalty = lengthPenalty(candidate.text.length),
            tuning = tuning,
        )
        return SwiftKeyScoredCandidate(
            candidate = candidate,
            originalIndex = originalIndex,
            source = source,
            score = score,
        )
    }

    private fun outcomeAdjustedSpatialLikelihood(
        rawSpatialLikelihood: Double,
        signal: SwiftKeyCandidateSignals,
        tuning: SwiftKeyCandidateTuning,
    ): Double {
        val acceptedBoost = signal.acceptedCorrectionConfidence.coerceIn(0.0, 1.0) *
            tuning.acceptedCorrectionSpatialBoost
        val rejectedPenalty = signal.rejectionPenalty.coerceIn(0.0, 1.0) *
            tuning.rejectedCorrectionSpatialPenalty
        return (rawSpatialLikelihood + acceptedBoost - rejectedPenalty).coerceIn(0.0, 1.0)
    }

    private fun SuggestionCandidate.role(
        typedWordKey: String,
        touchScore: Double,
        tuning: SwiftKeyCandidateTuning,
    ): SwiftKeyCandidateRole {
        val key = text.toString().normalizedCandidateKey()
        return when {
            key.isBlank() -> SwiftKeyCandidateRole.Other
            typedWordKey.isNotBlank() && key == typedWordKey -> SwiftKeyCandidateRole.TypedLiteral
            touchScore >= tuning.spatialCorrectionScoreThreshold -> SwiftKeyCandidateRole.SpatialCorrection
            isEligibleForAutoCommit -> SwiftKeyCandidateRole.AutoCorrection
            typedWordKey.isNotBlank() && key.startsWith(typedWordKey) -> SwiftKeyCandidateRole.Completion
            else -> SwiftKeyCandidateRole.Other
        }
    }

    private fun editProximity(typedWordKey: String, candidateKey: String): Double {
        if (typedWordKey.isBlank() || candidateKey.isBlank()) return 0.0
        if (typedWordKey == candidateKey) return 1.0
        val maxLength = maxOf(typedWordKey.length, candidateKey.length)
        if (maxLength == 0) return 0.0
        val distance = boundedEditDistance(
            left = typedWordKey,
            right = candidateKey,
            maxDistance = minOf(3, maxLength),
        ) ?: return 0.0
        return (1.0 - distance.toDouble() / maxLength.toDouble()).coerceIn(0.0, 1.0)
    }

    private fun completionAffinity(typedWordKey: String, candidateKey: String): Double {
        if (typedWordKey.isBlank() || candidateKey.isBlank()) return 0.0
        if (!candidateKey.startsWith(typedWordKey) || candidateKey == typedWordKey) return 0.0
        val extraLength = candidateKey.length - typedWordKey.length
        return (1.0 - extraLength.toDouble() / candidateKey.length.toDouble()).coerceIn(0.0, 1.0)
    }

    private fun lengthPenalty(length: Int): Double {
        return length.coerceAtLeast(0) * 0.001
    }

    private fun boundedEditDistance(left: String, right: String, maxDistance: Int): Int? {
        if (kotlin.math.abs(left.length - right.length) > maxDistance) return null
        var previous = IntArray(right.length + 1) { it }
        var current = IntArray(right.length + 1)
        for (i in 1..left.length) {
            current[0] = i
            var rowMin = current[0]
            for (j in 1..right.length) {
                val cost = if (left[i - 1] == right[j - 1]) 0 else 1
                current[j] = minOf(
                    previous[j] + 1,
                    current[j - 1] + 1,
                    previous[j - 1] + cost,
                )
                rowMin = minOf(rowMin, current[j])
            }
            if (rowMin > maxDistance) return null
            val swap = previous
            previous = current
            current = swap
        }
        return previous[right.length].takeIf { it <= maxDistance }
    }

    private fun String.normalizedCandidateKey(): String = trim().lowercase()

    private fun String.isWordLike(): Boolean {
        if (isBlank() || none { it.isLetter() }) {
            return false
        }
        return all { it.isLetter() || it == '\'' }
    }

    private const val TypedLiteralConfidence = 0.62
    private const val MinAutoCommitLanguageConfidence = 0.40

    private val ScoredCandidateComparator = compareByDescending<SwiftKeyScoredCandidate> { it.score.total }
        .thenByDescending { it.score.rolePriority }
        .thenByDescending { it.score.spatialLikelihood }
        .thenByDescending { it.score.contextProbability }
        .thenByDescending { it.score.dictionaryFrequency }
        .thenByDescending { it.score.languageConfidence }
        .thenByDescending { it.score.sourceAffinity }
        .thenByDescending { it.score.providerConfidence }
        .thenBy { it.candidate.text.length }
        .thenBy { it.originalIndex }
}
