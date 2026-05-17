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
    val languageIdConfidence: Double = 0.0,
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
        // SWIFTKEY_PARITY_ROADMAP_2026-05-17 §B3 — shared-spelling
        // bilingual handling. When the typed word is recognised in
        // multiple active locales (e.g. "no" in EN+ES), a candidate
        // that's *only* known in ONE locale is a single-language
        // overwrite of a shared literal — dangerous, must be
        // suppressed harder than the generic shared-typed-word
        // dampening. We measure the candidate's locale spread the
        // same way we measure the typed word's.
        val candidateKnownLocaleCount = localeEvidence.count { it.candidateFrequency > 0.0 }
        val sameLocaleAsTypedWord = localeEvidence.any { evidence ->
            evidence.typedFrequency > 0.0 && evidence.candidateFrequency > 0.0
        }
        val contextKnown = localeEvidence.any { it.contextFrequency > 0.0 }
        val sameLocaleAsContext = localeEvidence.any { evidence ->
            evidence.contextFrequency > 0.0 && evidence.candidateFrequency > 0.0
        }
        val languageIdKnown = localeEvidence.any { it.languageIdConfidence >= LanguageIdActiveThreshold }
        val sameLocaleAsLanguageId = localeEvidence.any { evidence ->
            evidence.languageIdConfidence >= LanguageIdActiveThreshold && evidence.candidateFrequency > 0.0
        }
        val languageConfidence = when {
            localeEvidence.size <= 1 -> 1.0
            candidateMatchesTypedWord && typedWordKnown -> 1.0
            // SWIFTKEY_PARITY_ROADMAP §B3 — typed word is shared across
            // multiple locales AND the candidate is recognised in only
            // one. This is the dangerous single-language overwrite case
            // ("no" + EN candidate `on`). Push below the
            // SwiftKeyCandidateRanker autocommit floor (0.40) so
            // spacebar can never replace a shared-spelling literal with
            // a one-language guess.
            typedKnownLocaleCount > 1 && candidateKnown && candidateKnownLocaleCount == 1 -> SharedSpellingOneLocaleCandidateConfidence
            // Both typed AND candidate are shared across locales — the
            // candidate could plausibly be valid in either side of the
            // bilingual sentence. Keep the existing moderate dampening.
            typedKnownLocaleCount > 1 && typedWordKnown && candidateKnown -> 0.52
            typedWordKnown && candidateKnown && sameLocaleAsTypedWord -> 0.92
            typedWordKnown && candidateKnown -> 0.32
            typedWordKnown && candidateIsEligibleForAutoCommit -> 0.20
            typedWordKnown -> 0.38
            languageIdKnown && candidateKnown && sameLocaleAsLanguageId && candidateConflictsWithTypedPrefix -> 0.58
            languageIdKnown && candidateKnown && sameLocaleAsLanguageId -> 0.94
            languageIdKnown && candidateKnown && candidateCompletesTypedWord && !contextLocaleHasTypedPrefixCandidate -> 0.70
            languageIdKnown && candidateKnown -> 0.16
            languageIdKnown && candidateIsEligibleForAutoCommit -> 0.10
            languageIdKnown -> 0.46
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

    private const val LanguageIdActiveThreshold = 0.80

    /**
     * SWIFTKEY_PARITY_ROADMAP §B3 — sub-floor (< 0.40, the
     * SwiftKeyCandidateRanker `MinAutoCommitLanguageConfidence`)
     * applied to a one-locale-only candidate that would otherwise
     * overwrite a shared-spelling typed word at the spacebar slot.
     * Internal visibility so tests can pin the exact value.
     */
    internal const val SharedSpellingOneLocaleCandidateConfidence: Double = 0.30
}
