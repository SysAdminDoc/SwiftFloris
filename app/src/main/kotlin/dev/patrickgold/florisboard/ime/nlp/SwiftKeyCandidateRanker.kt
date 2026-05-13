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
            typedWordKey = typedWordKey,
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

    fun selectSpacebarCandidate(
        currentWord: String,
        candidates: List<SuggestionCandidate>,
    ): SuggestionCandidate? {
        val typedWordKey = currentWord.trim().normalizedCandidateKey()
        if (!currentWord.trim().isWordLike() || typedWordKey.isBlank()) {
            return null
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

    private fun rankedCandidates(
        typedWordKey: String,
        currentWord: String,
        touchEvidence: TouchDecoderEvidence?,
        preferred: List<SuggestionCandidate>,
        fallback: List<SuggestionCandidate>,
    ): List<SuggestionCandidate> {
        return (preferred.mapIndexed { index, candidate ->
            val touchScore = touchEvidence?.spatialReplacementScore(candidate.text, currentWord) ?: 0.0
            RankedCandidate(
                candidate = candidate,
                originalIndex = index,
                sourcePriority = PreferredSourcePriority,
                role = candidate.role(typedWordKey, touchScore),
                touchScore = touchScore,
            )
        } + fallback.mapIndexed { index, candidate ->
            val touchScore = touchEvidence?.spatialReplacementScore(candidate.text, currentWord) ?: 0.0
            RankedCandidate(
                candidate = candidate,
                originalIndex = index,
                sourcePriority = FallbackSourcePriority,
                role = candidate.role(typedWordKey, touchScore),
                touchScore = touchScore,
            )
        }).sortedWith(
            compareByDescending<RankedCandidate> { it.role.priority }
                .thenByDescending { it.touchScore }
                .thenByDescending { it.sourcePriority }
                .thenByDescending { it.candidate.confidence }
                .thenBy { it.candidate.text.length }
                .thenBy { it.originalIndex }
        ).map { it.candidate }
    }

    private fun SuggestionCandidate.role(typedWordKey: String, touchScore: Double): CandidateRole {
        val key = text.toString().normalizedCandidateKey()
        return when {
            key.isBlank() -> CandidateRole.Other
            typedWordKey.isNotBlank() && key == typedWordKey -> CandidateRole.TypedLiteral
            touchScore >= SpatialCorrectionScoreThreshold -> CandidateRole.SpatialCorrection
            isEligibleForAutoCommit -> CandidateRole.AutoCorrection
            typedWordKey.isNotBlank() && key.startsWith(typedWordKey) -> CandidateRole.Completion
            else -> CandidateRole.Other
        }
    }

    private data class RankedCandidate(
        val candidate: SuggestionCandidate,
        val originalIndex: Int,
        val sourcePriority: Int,
        val role: CandidateRole,
        val touchScore: Double,
    )

    private enum class CandidateRole(val priority: Int) {
        TypedLiteral(50),
        SpatialCorrection(45),
        AutoCorrection(40),
        Completion(30),
        Other(10),
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
    private const val PreferredSourcePriority = 2
    private const val FallbackSourcePriority = 1
}
