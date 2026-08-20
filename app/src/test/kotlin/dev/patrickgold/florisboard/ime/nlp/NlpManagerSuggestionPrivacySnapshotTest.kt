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
import org.junit.Test
import kotlin.test.assertEquals

class NlpManagerSuggestionPrivacySnapshotTest {
    @Test
    fun suggestionRequestSnapshotFreezesEnabledPrivacyAndSensitivityInputs() {
        assertEquals(
            SuggestionRequestPrivacySnapshot(
                emojiSuggestionEnabled = true,
                emojiMaxCandidateCount = 5,
                wordSuggestionEnabled = false,
                allowPossiblyOffensive = false,
                isPrivateSession = true,
                isEditorSensitive = true,
                isPasswordEditor = false,
            ),
            SuggestionPrivacyPolicy.snapshotSuggestionRequest(
                emojiSuggestionEnabled = true,
                emojiMaxCandidateCount = 5,
                wordSuggestionEnabled = false,
                blockPossiblyOffensive = true,
                isPrivateSession = true,
                isEditorSensitive = true,
                keyVariation = KeyVariation.NORMAL,
            ),
        )
    }

    @Test
    fun suggestionRequestSnapshotSuppressesWordAndEmojiCandidatesForPasswordFields() {
        assertEquals(
            SuggestionRequestPrivacySnapshot(
                emojiSuggestionEnabled = false,
                emojiMaxCandidateCount = 5,
                wordSuggestionEnabled = false,
                allowPossiblyOffensive = false,
                isPrivateSession = false,
                isEditorSensitive = true,
                isPasswordEditor = true,
            ),
            SuggestionPrivacyPolicy.snapshotSuggestionRequest(
                emojiSuggestionEnabled = true,
                emojiMaxCandidateCount = 5,
                wordSuggestionEnabled = true,
                blockPossiblyOffensive = true,
                isPrivateSession = false,
                isEditorSensitive = true,
                keyVariation = KeyVariation.PASSWORD,
            ),
        )
    }
}
