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

internal data class TouchDecoderCandidate(
    val text: String,
    val confidence: Double,
)

internal data class TouchDecoderSample(
    val primaryText: String,
    val alternatives: List<TouchDecoderCandidate>,
)

internal data class TouchDecoderEvidence(
    val samples: List<TouchDecoderSample>,
) {
    fun spatialReplacementScore(candidateText: CharSequence, currentWord: String): Double {
        val candidate = candidateText.toString().normalizedSpatialWord() ?: return 0.0
        val typed = currentWord.normalizedSpatialWord() ?: return 0.0
        if (candidate == typed || typed.length != samples.size) {
            return 0.0
        }
        if (kotlin.math.abs(candidate.length - typed.length) > MaxSpatialEditDistance) {
            return 0.0
        }

        adjacentTranspositionScore(candidate, typed)?.let { return it }
        spatialEditAlignmentScore(candidate, typed)?.let { return it }

        return 0.0
    }

    private fun spatialEditAlignmentScore(candidate: String, typed: String): Double? {
        val best = Array(typed.length + 1) {
            Array(candidate.length + 1) {
                Array<SpatialEditPath?>(MaxSpatialEditDistance + 1) { null }
            }
        }
        best[0][0][0] = SpatialEditPath(editCount = 0, confidenceSum = 0.0)
        for (typedIndex in 0..typed.length) {
            for (candidateIndex in 0..candidate.length) {
                for (editCount in 0..MaxSpatialEditDistance) {
                    val path = best[typedIndex][candidateIndex][editCount] ?: continue
                    if (typedIndex < typed.length && candidateIndex < candidate.length) {
                        if (typed[typedIndex] == candidate[candidateIndex]) {
                            best.updateBest(
                                typedIndex = typedIndex + 1,
                                candidateIndex = candidateIndex + 1,
                                editCount = editCount,
                                candidate = path,
                            )
                        } else if (editCount < MaxSpatialEditDistance) {
                            val substitutionConfidence = substitutionConfidence(
                                sampleIndex = typedIndex,
                                candidateChar = candidate[candidateIndex],
                            )
                            if (substitutionConfidence != null) {
                                best.updateBest(
                                    typedIndex = typedIndex + 1,
                                    candidateIndex = candidateIndex + 1,
                                    editCount = editCount + 1,
                                    candidate = path.withEdit(substitutionConfidence),
                                )
                            }
                        }
                    }
                    if (editCount < MaxSpatialEditDistance && candidateIndex < candidate.length) {
                        missingCandidateCharConfidence(
                            candidateIndex = candidateIndex,
                            candidateLength = candidate.length,
                        )?.let { missingConfidence ->
                            best.updateBest(
                                typedIndex = typedIndex,
                                candidateIndex = candidateIndex + 1,
                                editCount = editCount + 1,
                                candidate = path.withEdit(missingConfidence),
                            )
                        }
                    }
                    if (editCount < MaxSpatialEditDistance && typedIndex < typed.length) {
                        val extraConfidence = extraTypedCharConfidence(
                            typedIndex = typedIndex,
                            candidate = candidate,
                            candidateIndex = candidateIndex,
                        )
                        best.updateBest(
                            typedIndex = typedIndex + 1,
                            candidateIndex = candidateIndex,
                            editCount = editCount + 1,
                            candidate = path.withEdit(extraConfidence),
                        )
                    }
                }
            }
        }

        return best[typed.length][candidate.length]
            .asSequence()
            .filterNotNull()
            .filter { it.editCount > 0 }
            .maxByOrNull { it.averageConfidence }
            ?.averageConfidence
    }

    private fun substitutionConfidence(sampleIndex: Int, candidateChar: Char): Double? {
        return samples[sampleIndex].alternatives.firstOrNull { alternative ->
            alternative.text.normalizedSpatialWord() == candidateChar.toString()
        }?.confidence
    }

    private fun missingCandidateCharConfidence(candidateIndex: Int, candidateLength: Int): Double? {
        if (candidateIndex == 0 || candidateIndex == candidateLength - 1) {
            return null
        }
        return MissingLetterSpatialConfidence
    }

    private fun extraTypedCharConfidence(typedIndex: Int, candidate: String, candidateIndex: Int): Double {
        val typedChar = samples[typedIndex].primaryText.singleOrNull()
            ?: return UnsupportedExtraLetterSpatialConfidence
        val previousCandidateChar = candidate.getOrNull(candidateIndex - 1)
        val nextCandidateChar = candidate.getOrNull(candidateIndex)
        if (typedChar == previousCandidateChar || typedChar == nextCandidateChar) {
            return DoubleLetterSpatialConfidence
        }
        val alternatives = samples[typedIndex].alternatives.asSequence()
            .mapNotNull { alternative ->
                alternative.text.normalizedSpatialWord()?.singleOrNull()?.let { char ->
                    char to alternative.confidence
                }
            }
            .toMap()
        val neighborConfidence = maxOf(
            previousCandidateChar?.let { alternatives[it] } ?: 0.0,
            nextCandidateChar?.let { alternatives[it] } ?: 0.0,
        )
        return if (neighborConfidence > 0.0) {
            neighborConfidence * ExtraLetterNeighborConfidenceScale
        } else {
            UnsupportedExtraLetterSpatialConfidence
        }
    }

    private fun Array<Array<Array<SpatialEditPath?>>>.updateBest(
        typedIndex: Int,
        candidateIndex: Int,
        editCount: Int,
        candidate: SpatialEditPath,
    ) {
        val current = this[typedIndex][candidateIndex][editCount]
        if (current == null || candidate.confidenceSum > current.confidenceSum) {
            this[typedIndex][candidateIndex][editCount] = candidate
        }
    }

    private fun adjacentTranspositionScore(candidate: String, typed: String): Double? {
        if (candidate.length != typed.length) return null
        var firstDifference = -1
        var secondDifference = -1
        for (index in candidate.indices) {
            if (candidate[index] == typed[index]) continue
            when {
                firstDifference < 0 -> firstDifference = index
                secondDifference < 0 -> secondDifference = index
                else -> return null
            }
        }
        if (firstDifference < 0 || secondDifference != firstDifference + 1) return null
        return if (
            candidate[firstDifference] == typed[secondDifference] &&
            candidate[secondDifference] == typed[firstDifference]
        ) {
            TranspositionSpatialConfidence
        } else {
            null
        }
    }

    private data class SpatialEditPath(
        val editCount: Int,
        val confidenceSum: Double,
    ) {
        val averageConfidence: Double
            get() = if (editCount == 0) 0.0 else confidenceSum / editCount.toDouble()

        fun withEdit(confidence: Double): SpatialEditPath {
            return copy(
                editCount = editCount + 1,
                confidenceSum = confidenceSum + confidence,
            )
        }
    }

    private companion object {
        const val MaxSpatialEditDistance = 2
        const val TranspositionSpatialConfidence = 0.38
        const val MissingLetterSpatialConfidence = 0.34
        const val DoubleLetterSpatialConfidence = 0.46
        const val ExtraLetterNeighborConfidenceScale = 0.72
        const val UnsupportedExtraLetterSpatialConfidence = 0.18
    }
}

internal class TouchDecoderEvidenceBuffer {
    private val samples = ArrayDeque<TouchDecoderSample>()

    @Synchronized
    fun record(sample: TouchDecoderSample) {
        val primary = sample.primaryText.normalizedSpatialWord() ?: return
        if (primary.length != 1) return
        val alternatives = sample.alternatives
            .asSequence()
            .mapNotNull { candidate ->
                val text = candidate.text.normalizedSpatialWord() ?: return@mapNotNull null
                if (text.length == 1 && text != primary) {
                    TouchDecoderCandidate(text = text, confidence = candidate.confidence.coerceIn(0.0, 1.0))
                } else {
                    null
                }
            }
            .distinctBy { it.text }
            .sortedByDescending { it.confidence }
            .take(MaxAlternativesPerSample)
            .toList()
        samples.addLast(TouchDecoderSample(primaryText = primary, alternatives = alternatives))
        while (samples.size > MaxSamples) {
            samples.removeFirst()
        }
    }

    @Synchronized
    fun evidenceFor(currentWord: String): TouchDecoderEvidence? {
        val normalizedWord = currentWord.normalizedSpatialWord() ?: return null
        if (normalizedWord.length > samples.size) return null
        val tail = samples.takeLast(normalizedWord.length)
        val primaryText = tail.joinToString(separator = "") { it.primaryText }
        return if (primaryText == normalizedWord) {
            TouchDecoderEvidence(tail)
        } else {
            null
        }
    }

    @Synchronized
    fun clear() {
        samples.clear()
    }

    private companion object {
        const val MaxSamples = 64
        const val MaxAlternativesPerSample = 4
    }
}

private fun String.normalizedSpatialWord(): String? {
    val normalized = trim()
        .trim { char -> !char.isLetter() && char != '\'' && char != '\u2019' }
        .lowercase()
    if (normalized.isBlank() || normalized.none { it.isLetter() }) return null
    if (normalized.any { char -> !char.isLetter() && char != '\'' && char != '\u2019' }) return null
    return normalized
}
