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

internal data class SwiftKeyDecoderContext(
    val currentWord: String,
    val maxCandidateCount: Int,
    val typedWordKnown: Boolean = false,
    val touchEvidence: TouchDecoderEvidence? = null,
)

internal data class SwiftKeyScoredCandidate(
    val candidate: SuggestionCandidate,
    val originalIndex: Int,
    val source: SwiftKeyCandidateSource,
    val score: SwiftKeyCandidateScore,
)

internal data class SwiftKeyCandidateScore(
    val role: SwiftKeyCandidateRole,
    val rolePriority: Double,
    val spatialLikelihood: Double,
    val providerConfidence: Double,
    val sourceAffinity: Double,
    val editProximity: Double,
    val completionAffinity: Double,
    val lengthPenalty: Double,
) {
    val total: Double =
        rolePriority * RoleWeight +
            spatialLikelihood * SpatialWeight +
            sourceAffinity * SourceWeight +
            providerConfidence * ConfidenceWeight +
            editProximity * EditWeight +
            completionAffinity * CompletionWeight -
            lengthPenalty

    private companion object {
        const val RoleWeight = 100.0
        const val SpatialWeight = 24.0
        const val SourceWeight = 18.0
        const val ConfidenceWeight = 6.0
        const val EditWeight = 4.0
        const val CompletionWeight = 3.0
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
            preferred = preferred,
            fallback = fallback,
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
    ): List<SwiftKeyScoredCandidate> {
        val currentWord = context.currentWord.trim()
        val typedWordKey = currentWord.normalizedCandidateKey()
        return (preferred.mapIndexed { index, candidate ->
            scoredCandidate(
                candidate = candidate,
                originalIndex = index,
                source = SwiftKeyCandidateSource.Preferred,
                typedWordKey = typedWordKey,
                currentWord = currentWord,
                touchEvidence = context.touchEvidence,
            )
        } + fallback.mapIndexed { index, candidate ->
            scoredCandidate(
                candidate = candidate,
                originalIndex = index,
                source = SwiftKeyCandidateSource.Fallback,
                typedWordKey = typedWordKey,
                currentWord = currentWord,
                touchEvidence = context.touchEvidence,
            )
        }).sortedWith(ScoredCandidateComparator)
    }

    fun selectSpacebarCandidate(
        currentWord: String,
        candidates: List<SuggestionCandidate>,
        quickPredictionInsert: Boolean = false,
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
            middleCandidateKey != typedWordKey
        ) {
            return middleCandidate
        }

        return candidates.firstOrNull { candidate ->
            candidate.isEligibleForAutoCommit &&
                candidate.text.toString().normalizedCandidateKey() != typedWordKey
        }
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
        preferred: List<SuggestionCandidate>,
        fallback: List<SuggestionCandidate>,
    ): List<SuggestionCandidate> {
        return scoreCandidates(
            context = SwiftKeyDecoderContext(
                currentWord = currentWord,
                maxCandidateCount = Int.MAX_VALUE,
                touchEvidence = touchEvidence,
            ),
            preferred = preferred,
            fallback = fallback,
        ).map { it.candidate }
    }

    private fun scoredCandidate(
        candidate: SuggestionCandidate,
        originalIndex: Int,
        source: SwiftKeyCandidateSource,
        typedWordKey: String,
        currentWord: String,
        touchEvidence: TouchDecoderEvidence?,
    ): SwiftKeyScoredCandidate {
        val spatialLikelihood = touchEvidence?.spatialReplacementScore(candidate.text, currentWord)
            ?.coerceIn(0.0, 1.0)
            ?: 0.0
        val role = candidate.role(typedWordKey, spatialLikelihood)
        val candidateKey = candidate.text.toString().normalizedCandidateKey()
        val score = SwiftKeyCandidateScore(
            role = role,
            rolePriority = role.priority,
            spatialLikelihood = spatialLikelihood,
            providerConfidence = candidate.confidence.coerceIn(0.0, 1.0),
            sourceAffinity = source.affinity,
            editProximity = editProximity(typedWordKey, candidateKey),
            completionAffinity = completionAffinity(typedWordKey, candidateKey),
            lengthPenalty = lengthPenalty(candidate.text.length),
        )
        return SwiftKeyScoredCandidate(
            candidate = candidate,
            originalIndex = originalIndex,
            source = source,
            score = score,
        )
    }

    private fun SuggestionCandidate.role(typedWordKey: String, touchScore: Double): SwiftKeyCandidateRole {
        val key = text.toString().normalizedCandidateKey()
        return when {
            key.isBlank() -> SwiftKeyCandidateRole.Other
            typedWordKey.isNotBlank() && key == typedWordKey -> SwiftKeyCandidateRole.TypedLiteral
            touchScore >= SpatialCorrectionScoreThreshold -> SwiftKeyCandidateRole.SpatialCorrection
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
    private const val SpatialCorrectionScoreThreshold = 0.28

    private val ScoredCandidateComparator = compareByDescending<SwiftKeyScoredCandidate> { it.score.total }
        .thenByDescending { it.score.rolePriority }
        .thenByDescending { it.score.spatialLikelihood }
        .thenByDescending { it.score.sourceAffinity }
        .thenByDescending { it.score.providerConfidence }
        .thenBy { it.candidate.text.length }
        .thenBy { it.originalIndex }
}
