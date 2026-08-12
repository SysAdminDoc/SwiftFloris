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
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SuggestionPrivacyPolicyTest : FunSpec({
    test("app-declared no-personalized-learning forces incognito despite user force-off") {
        SuggestionPrivacyPolicy.resolveIncognitoMode(
            appDeclaredNoPersonalizedLearning = true,
            preference = IncognitoMode.FORCE_OFF,
            isDynamicIncognitoForced = false,
        ) shouldBe true
    }

    test("fixed incognito preferences resolve directly when the app does not force privacy") {
        SuggestionPrivacyPolicy.resolveIncognitoMode(
            appDeclaredNoPersonalizedLearning = false,
            preference = IncognitoMode.FORCE_OFF,
            isDynamicIncognitoForced = true,
        ) shouldBe false
        SuggestionPrivacyPolicy.resolveIncognitoMode(
            appDeclaredNoPersonalizedLearning = false,
            preference = IncognitoMode.FORCE_ON,
            isDynamicIncognitoForced = false,
        ) shouldBe true
    }

    test("dynamic incognito mode follows the user toggle") {
        SuggestionPrivacyPolicy.resolveIncognitoMode(
            appDeclaredNoPersonalizedLearning = false,
            preference = IncognitoMode.DYNAMIC_ON_OFF,
            isDynamicIncognitoForced = false,
        ) shouldBe false
        SuggestionPrivacyPolicy.resolveIncognitoMode(
            appDeclaredNoPersonalizedLearning = false,
            preference = IncognitoMode.DYNAMIC_ON_OFF,
            isDynamicIncognitoForced = true,
        ) shouldBe true
    }

    test("incognito toggle is only available for dynamic mode outside app-private fields") {
        SuggestionPrivacyPolicy.canToggleIncognitoMode(
            preference = IncognitoMode.DYNAMIC_ON_OFF,
            appDeclaredNoPersonalizedLearning = false,
        ) shouldBe true
        SuggestionPrivacyPolicy.canToggleIncognitoMode(
            preference = IncognitoMode.DYNAMIC_ON_OFF,
            appDeclaredNoPersonalizedLearning = true,
        ) shouldBe false
        SuggestionPrivacyPolicy.canToggleIncognitoMode(
            preference = IncognitoMode.FORCE_ON,
            appDeclaredNoPersonalizedLearning = false,
        ) shouldBe false
    }

    test("committed words are learned only in non-private normal text fields") {
        SuggestionPrivacyPolicy.shouldLearnCommittedWord(
            rawWord = "hello",
            isIncognitoMode = false,
            keyVariation = KeyVariation.NORMAL,
        ) shouldBe true
        SuggestionPrivacyPolicy.shouldLearnCommittedWord(
            rawWord = "hello",
            isIncognitoMode = true,
            keyVariation = KeyVariation.NORMAL,
        ) shouldBe false
        SuggestionPrivacyPolicy.shouldLearnCommittedWord(
            rawWord = "hello",
            isIncognitoMode = false,
            keyVariation = KeyVariation.PASSWORD,
        ) shouldBe false
        SuggestionPrivacyPolicy.shouldLearnCommittedWord(
            rawWord = " ",
            isIncognitoMode = false,
            keyVariation = KeyVariation.NORMAL,
        ) shouldBe false
    }

    test("touch decoder evidence is recorded only when autocorrect can learn safely") {
        SuggestionPrivacyPolicy.shouldRecordTouchDecoderSample(
            isAutoCorrectEnabled = true,
            isIncognitoMode = false,
            keyVariation = KeyVariation.NORMAL,
        ) shouldBe true
        SuggestionPrivacyPolicy.shouldRecordTouchDecoderSample(
            isAutoCorrectEnabled = false,
            isIncognitoMode = false,
            keyVariation = KeyVariation.NORMAL,
        ) shouldBe false
        SuggestionPrivacyPolicy.shouldRecordTouchDecoderSample(
            isAutoCorrectEnabled = true,
            isIncognitoMode = true,
            keyVariation = KeyVariation.NORMAL,
        ) shouldBe false
        SuggestionPrivacyPolicy.shouldRecordTouchDecoderSample(
            isAutoCorrectEnabled = true,
            isIncognitoMode = false,
            keyVariation = KeyVariation.PASSWORD,
        ) shouldBe false
    }

    test("incognito suppresses ghost text even in a non-sensitive editor") {
        SuggestionPrivacyPolicy.allowsGhostText(
            isPrivateSession = true,
            isEditorSensitive = false,
        ) shouldBe false
        SuggestionPrivacyPolicy.allowsGhostText(
            isPrivateSession = false,
            isEditorSensitive = true,
        ) shouldBe false
        SuggestionPrivacyPolicy.allowsGhostText(
            isPrivateSession = false,
            isEditorSensitive = false,
        ) shouldBe true
    }
})
