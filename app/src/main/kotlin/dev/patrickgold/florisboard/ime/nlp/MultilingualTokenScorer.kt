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

internal data class TokenLocaleEvidence(
    val typedFrequency: Double,
    val candidateFrequency: Double,
    val contextFrequency: Double = 0.0,
)

internal data class MultilingualTokenSignal(
    val typedWordKnown: Boolean,
    val dictionaryFrequency: Double,
    val languageConfidence: Double,
)

internal object MultilingualTokenScorer {
    fun score(
        localeEvidence: List<TokenLocaleEvidence>,
        typedWordKnownByUserDictionary: Boolean,
        candidateMatchesTypedWord: Boolean,
        candidateIsEligibleForAutoCommit: Boolean,
        candidateCompletesTypedWord: Boolean = false,
        candidateConflictsWithTypedPrefix: Boolean = false,
        contextLocaleHasTypedPrefixCandidate: Boolean = false,
    ): MultilingualTokenSignal {
        val typedKnownByDictionary = localeEvidence.any { it.typedFrequency > 0.0 }
        val typedWordKnown = typedWordKnownByUserDictionary || typedKnownByDictionary
        val typedKnownLocaleCount = localeEvidence.count { it.typedFrequency > 0.0 }
        val dictionaryFrequency = localeEvidence.maxOfOrNull { it.candidateFrequency.coerceIn(0.0, 1.0) } ?: 0.0
        val candidateKnown = dictionaryFrequency > 0.0
        val sameLocaleAsTypedWord = localeEvidence.any { evidence ->
            evidence.typedFrequency > 0.0 && evidence.candidateFrequency > 0.0
        }
        val contextKnown = localeEvidence.any { it.contextFrequency > 0.0 }
        val sameLocaleAsContext = localeEvidence.any { evidence ->
            evidence.contextFrequency > 0.0 && evidence.candidateFrequency > 0.0
        }
        val languageConfidence = when {
            localeEvidence.size <= 1 -> 1.0
            candidateMatchesTypedWord && typedWordKnown -> 1.0
            typedKnownLocaleCount > 1 && typedWordKnown && candidateKnown -> 0.52
            typedWordKnown && candidateKnown && sameLocaleAsTypedWord -> 0.92
            typedWordKnown && candidateKnown -> 0.32
            typedWordKnown && candidateIsEligibleForAutoCommit -> 0.20
            typedWordKnown -> 0.38
            contextKnown && candidateKnown && sameLocaleAsContext && candidateConflictsWithTypedPrefix -> 0.50
            contextKnown && candidateKnown && sameLocaleAsContext -> 0.98
            contextKnown && candidateKnown && candidateCompletesTypedWord && !contextLocaleHasTypedPrefixCandidate -> 0.74
            contextKnown && candidateKnown -> 0.12
            contextKnown && candidateIsEligibleForAutoCommit -> 0.08
            contextKnown -> 0.44
            candidateKnown -> 0.88
            else -> 0.62
        }
        return MultilingualTokenSignal(
            typedWordKnown = typedWordKnown,
            dictionaryFrequency = dictionaryFrequency,
            languageConfidence = languageConfidence,
        )
    }
}
