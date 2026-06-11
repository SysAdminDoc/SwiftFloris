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

import dev.patrickgold.florisboard.ime.text.key.KeyVariation

internal object CandidateAutoCommitPolicy {
    fun selectAutoCommitCandidate(
        autoCorrectEnabled: Boolean,
        autoCorrectCommitMode: AutoCorrectCommitMode = AutoCorrectCommitMode.NORMAL,
        keyVariation: KeyVariation,
        currentWord: String,
        currentWordStart: Int?,
        candidates: List<SuggestionCandidate>,
        candidateSignals: Map<String, SwiftKeyCandidateSignals>,
        rejectionPolicy: AutoCommitSuppression,
        userDictionaryShortcutCandidate: SuggestionCandidate?,
        immediatePhraseRepairCandidate: SuggestionCandidate?,
        immediateAutoCommitCandidate: SuggestionCandidate?,
    ): SuggestionCandidate? {
        if (!autoCorrectEnabled) return null
        // Never auto-commit into a password / PIN field. Composing is disabled
        // there, so a commit would append after the typed text instead of
        // replacing it — corrupting masked input the user cannot see.
        if (keyVariation == KeyVariation.PASSWORD) return null
        if (rejectionPolicy.shouldKeepTypedLiteral(currentWord, currentWordStart)) return null

        firstAllowedCandidate(
            currentWord = currentWord,
            currentWordStart = currentWordStart,
            rejectionPolicy = rejectionPolicy,
            candidates = listOfNotNull(userDictionaryShortcutCandidate),
        )?.let { return it }

        firstAllowedCandidate(
            currentWord = currentWord,
            currentWordStart = currentWordStart,
            rejectionPolicy = rejectionPolicy,
            candidates = listOfNotNull(immediatePhraseRepairCandidate),
            autoCorrectCommitMode = autoCorrectCommitMode,
            candidateSignals = candidateSignals,
        )?.let { return it }

        candidates.firstOrNull { candidate ->
            candidate.isEligibleForAutoCommit &&
                SwiftKeyCandidateRanker.autoCommitConfidenceAllowsMode(
                    candidate = candidate,
                    candidateSignals = candidateSignals,
                    mode = autoCorrectCommitMode,
                ) &&
                rejectionPolicy.allowsCandidate(currentWord, candidate, currentWordStart)
        }?.let { return it }

        return firstAllowedCandidate(
            currentWord = currentWord,
            currentWordStart = currentWordStart,
            rejectionPolicy = rejectionPolicy,
            candidates = listOfNotNull(immediateAutoCommitCandidate),
            autoCorrectCommitMode = autoCorrectCommitMode,
            candidateSignals = candidateSignals,
        )
    }

    fun selectSpacebarCandidate(
        autoCorrectEnabled: Boolean,
        autoCorrectCommitMode: AutoCorrectCommitMode = AutoCorrectCommitMode.NORMAL,
        quickPredictionInsertEnabled: Boolean,
        keyVariation: KeyVariation,
        currentWord: String,
        currentWordStart: Int?,
        textBeforeCursor: String,
        candidates: List<SuggestionCandidate>,
        candidateSignals: Map<String, SwiftKeyCandidateSignals>,
        rejectionPolicy: AutoCommitSuppression,
        userDictionaryShortcutCandidate: SuggestionCandidate?,
        immediatePhraseRepairCandidate: SuggestionCandidate?,
        immediateAutoCommitCandidate: SuggestionCandidate?,
    ): SuggestionCandidate? {
        if (!autoCorrectEnabled && !quickPredictionInsertEnabled) return null
        // Same password gate as selectAutoCommitCandidate: spacebar
        // auto-commit must never rewrite masked input.
        if (keyVariation == KeyVariation.PASSWORD) return null
        if (rejectionPolicy.shouldKeepTypedLiteral(currentWord, currentWordStart)) return null

        if (autoCorrectEnabled) {
            firstAllowedCandidate(
                currentWord = currentWord,
                currentWordStart = currentWordStart,
                rejectionPolicy = rejectionPolicy,
                candidates = listOfNotNull(userDictionaryShortcutCandidate),
            )?.let { return it }

            firstAllowedCandidate(
                currentWord = currentWord,
                currentWordStart = currentWordStart,
                rejectionPolicy = rejectionPolicy,
                candidates = listOfNotNull(immediatePhraseRepairCandidate),
                autoCorrectCommitMode = autoCorrectCommitMode,
                candidateSignals = candidateSignals,
            )?.let { return it }
        }

        val candidate = SwiftKeyCandidateRanker.selectSpacebarCandidate(
            currentWord = currentWord,
            candidates = candidates,
            quickPredictionInsert = quickPredictionInsertEnabled,
            textBeforeCursor = textBeforeCursor,
            candidateSignals = candidateSignals,
            autoCorrectCommitMode = autoCorrectCommitMode,
        ) ?: return if (autoCorrectEnabled) {
            firstAllowedCandidate(
                currentWord = currentWord,
                currentWordStart = currentWordStart,
                rejectionPolicy = rejectionPolicy,
                candidates = listOfNotNull(immediateAutoCommitCandidate),
                autoCorrectCommitMode = autoCorrectCommitMode,
                candidateSignals = candidateSignals,
            )
        } else {
            null
        }

        return candidate.takeIf {
            rejectionPolicy.allowsCandidate(currentWord, it, currentWordStart)
        }
    }

    fun shouldSuppressPlainSpaceForPrediction(
        quickPredictionInsertEnabled: Boolean,
        currentWord: String,
        textBeforeCursor: String,
        candidates: List<SuggestionCandidate>,
        candidateSignals: Map<String, SwiftKeyCandidateSignals>,
    ): Boolean {
        if (!quickPredictionInsertEnabled) return false
        if (currentWord.isNotBlank()) return false
        return SwiftKeyCandidateRanker.selectSpacebarCandidate(
            currentWord = "",
            candidates = candidates,
            quickPredictionInsert = true,
            textBeforeCursor = textBeforeCursor,
            candidateSignals = candidateSignals,
        ) != null
    }

    private fun firstAllowedCandidate(
        currentWord: String,
        currentWordStart: Int?,
        rejectionPolicy: AutoCommitSuppression,
        candidates: List<SuggestionCandidate>,
        autoCorrectCommitMode: AutoCorrectCommitMode? = null,
        candidateSignals: Map<String, SwiftKeyCandidateSignals> = emptyMap(),
    ): SuggestionCandidate? {
        return candidates.firstOrNull { candidate ->
            candidate.isEligibleForAutoCommit &&
                (
                    autoCorrectCommitMode == null ||
                        SwiftKeyCandidateRanker.autoCommitConfidenceAllowsMode(
                            candidate = candidate,
                            candidateSignals = candidateSignals,
                            mode = autoCorrectCommitMode,
                        )
                ) &&
                rejectionPolicy.allowsCandidate(currentWord, candidate, currentWordStart)
        }
    }

    private fun AutoCommitSuppression.allowsCandidate(
        currentWord: String,
        candidate: SuggestionCandidate,
        currentWordStart: Int?,
    ): Boolean {
        return !shouldSuppress(
            currentWord = currentWord,
            candidateText = candidate.text,
            currentWordStart = currentWordStart,
        )
    }
}
