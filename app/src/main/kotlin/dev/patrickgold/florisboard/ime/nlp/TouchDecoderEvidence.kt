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
        if (candidate.length != typed.length || candidate == typed || candidate.length != samples.size) {
            return 0.0
        }

        var changedPositions = 0
        var score = 0.0
        for (index in candidate.indices) {
            if (candidate[index] == typed[index]) continue
            val alternative = samples[index].alternatives.firstOrNull { alternative ->
                alternative.text.normalizedSpatialWord() == candidate[index].toString()
            } ?: return 0.0
            changedPositions += 1
            score += alternative.confidence
        }
        return if (changedPositions == 0) {
            0.0
        } else {
            score / changedPositions.toDouble()
        }
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
