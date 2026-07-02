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

internal object CandidateCommitSideEffectPolicy {
    fun shouldNotifyAcceptedProvider(
        commitSucceeded: Boolean,
        hasSourceProvider: Boolean,
    ): Boolean {
        return commitSucceeded && hasSourceProvider
    }

    fun shouldLearnCommittedCandidate(
        commitSucceeded: Boolean,
        isClipboardCandidate: Boolean,
    ): Boolean {
        return commitSucceeded && !isClipboardCandidate
    }

    fun shouldCommitPlainSpaceAfterSpacebar(
        candidate: SuggestionCandidate?,
        suppressPlainSpaceForPrediction: Boolean,
        supportsAutoSpace: Boolean,
    ): Boolean {
        if (suppressPlainSpaceForPrediction) return false
        return when (candidate?.trailingSpacePolicy) {
            null,
            CandidateTrailingSpacePolicy.ALWAYS -> true
            CandidateTrailingSpacePolicy.AUTO_SPACE_LOCALE -> supportsAutoSpace
            CandidateTrailingSpacePolicy.NEVER -> false
        }
    }
}
