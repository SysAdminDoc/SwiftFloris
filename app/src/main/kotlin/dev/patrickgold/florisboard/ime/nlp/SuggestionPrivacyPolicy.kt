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

import dev.patrickgold.florisboard.ime.keyboard.IncognitoMode
import dev.patrickgold.florisboard.ime.text.key.KeyVariation

internal object SuggestionPrivacyPolicy {
    fun snapshotSuggestionRequest(
        emojiSuggestionEnabled: Boolean,
        emojiMaxCandidateCount: Int,
        wordSuggestionEnabled: Boolean,
        blockPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
        isEditorSensitive: Boolean,
    ): SuggestionRequestPrivacySnapshot {
        return SuggestionRequestPrivacySnapshot(
            emojiSuggestionEnabled = emojiSuggestionEnabled,
            emojiMaxCandidateCount = emojiMaxCandidateCount,
            wordSuggestionEnabled = wordSuggestionEnabled,
            allowPossiblyOffensive = !blockPossiblyOffensive,
            isPrivateSession = isPrivateSession,
            isEditorSensitive = isEditorSensitive,
        )
    }

    fun resolveIncognitoMode(
        appDeclaredNoPersonalizedLearning: Boolean,
        preference: IncognitoMode,
        isDynamicIncognitoForced: Boolean,
    ): Boolean {
        if (appDeclaredNoPersonalizedLearning) return true
        return when (preference) {
            IncognitoMode.FORCE_OFF -> false
            IncognitoMode.FORCE_ON -> true
            IncognitoMode.DYNAMIC_ON_OFF -> isDynamicIncognitoForced
        }
    }

    fun canToggleIncognitoMode(
        preference: IncognitoMode,
        appDeclaredNoPersonalizedLearning: Boolean,
    ): Boolean {
        return preference == IncognitoMode.DYNAMIC_ON_OFF && !appDeclaredNoPersonalizedLearning
    }

    fun shouldLearnCommittedWord(
        rawWord: String,
        isIncognitoMode: Boolean,
        keyVariation: KeyVariation,
    ): Boolean {
        return rawWord.isNotBlank() &&
            !isIncognitoMode &&
            keyVariation != KeyVariation.PASSWORD
    }

    fun shouldRecordTouchDecoderSample(
        isAutoCorrectEnabled: Boolean,
        isIncognitoMode: Boolean,
        keyVariation: KeyVariation,
    ): Boolean {
        return isAutoCorrectEnabled &&
            !isIncognitoMode &&
            keyVariation != KeyVariation.PASSWORD
    }
}

internal data class SuggestionRequestPrivacySnapshot(
    val emojiSuggestionEnabled: Boolean,
    val emojiMaxCandidateCount: Int,
    val wordSuggestionEnabled: Boolean,
    val allowPossiblyOffensive: Boolean,
    val isPrivateSession: Boolean,
    val isEditorSensitive: Boolean,
)
