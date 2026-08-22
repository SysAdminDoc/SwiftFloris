/*
 * Copyright (C) 2026 SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.editor

import dev.patrickgold.florisboard.ime.keyboard.IncognitoMode
import dev.patrickgold.florisboard.ime.nlp.SuggestionPrivacyPolicy
import dev.patrickgold.florisboard.ime.text.key.KeyVariation
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class EditorInfoSensitiveFieldReplayTest : FunSpec({

    test("PASSWORD variation suppresses word suggestions") {
        val snapshot = SuggestionPrivacyPolicy.snapshotSuggestionRequest(
            emojiSuggestionEnabled = true,
            emojiMaxCandidateCount = 5,
            wordSuggestionEnabled = true,
            blockPossiblyOffensive = false,
            isPrivateSession = false,
            isEditorSensitive = false,
            keyVariation = KeyVariation.PASSWORD,
        )
        snapshot.wordSuggestionEnabled shouldBe false
        snapshot.emojiSuggestionEnabled shouldBe false
        snapshot.isPasswordEditor shouldBe true
    }

    test("PASSWORD variation suppresses dictionary learning") {
        SuggestionPrivacyPolicy.shouldLearnCommittedWord(
            rawWord = "secretword",
            isIncognitoMode = false,
            keyVariation = KeyVariation.PASSWORD,
        ) shouldBe false
    }

    test("NORMAL variation permits word suggestions") {
        val snapshot = SuggestionPrivacyPolicy.snapshotSuggestionRequest(
            emojiSuggestionEnabled = true,
            emojiMaxCandidateCount = 5,
            wordSuggestionEnabled = true,
            blockPossiblyOffensive = false,
            isPrivateSession = false,
            isEditorSensitive = false,
            keyVariation = KeyVariation.NORMAL,
        )
        snapshot.wordSuggestionEnabled shouldBe true
        snapshot.isPasswordEditor shouldBe false
    }

    test("NORMAL variation permits dictionary learning") {
        SuggestionPrivacyPolicy.shouldLearnCommittedWord(
            rawWord = "normalword",
            isIncognitoMode = false,
            keyVariation = KeyVariation.NORMAL,
        ) shouldBe true
    }

    test("app-declared NO_PERSONALIZED_LEARNING forces incognito regardless of user preference") {
        SuggestionPrivacyPolicy.resolveIncognitoMode(
            appDeclaredNoPersonalizedLearning = true,
            preference = IncognitoMode.FORCE_OFF,
            isDynamicIncognitoForced = false,
        ) shouldBe true
    }

    test("app-declared NO_PERSONALIZED_LEARNING blocks incognito toggle") {
        SuggestionPrivacyPolicy.canToggleIncognitoMode(
            preference = IncognitoMode.DYNAMIC_ON_OFF,
            appDeclaredNoPersonalizedLearning = true,
        ) shouldBe false
    }

    test("incognito mode suppresses dictionary learning even on normal fields") {
        SuggestionPrivacyPolicy.shouldLearnCommittedWord(
            rawWord = "incognitoword",
            isIncognitoMode = true,
            keyVariation = KeyVariation.NORMAL,
        ) shouldBe false
    }

    test("blank words are never learned regardless of field type") {
        SuggestionPrivacyPolicy.shouldLearnCommittedWord(
            rawWord = "  ",
            isIncognitoMode = false,
            keyVariation = KeyVariation.NORMAL,
        ) shouldBe false
    }

    test("incognito mode suppresses touch decoder evidence collection") {
        SuggestionPrivacyPolicy.shouldRecordTouchDecoderSample(
            isAutoCorrectEnabled = true,
            isIncognitoMode = true,
            keyVariation = KeyVariation.NORMAL,
        ) shouldBe false
    }

    test("PASSWORD variation suppresses touch decoder evidence collection") {
        SuggestionPrivacyPolicy.shouldRecordTouchDecoderSample(
            isAutoCorrectEnabled = true,
            isIncognitoMode = false,
            keyVariation = KeyVariation.PASSWORD,
        ) shouldBe false
    }

    test("normal fields permit adaptive touch samples") {
        SuggestionPrivacyPolicy.shouldRecordAdaptiveTouchSample(
            isAdaptiveTouchEnabled = true,
            isIncognitoMode = false,
            keyVariation = KeyVariation.NORMAL,
        ) shouldBe true
    }

    test("PASSWORD variation suppresses adaptive touch samples") {
        SuggestionPrivacyPolicy.shouldRecordAdaptiveTouchSample(
            isAdaptiveTouchEnabled = true,
            isIncognitoMode = false,
            keyVariation = KeyVariation.PASSWORD,
        ) shouldBe false
    }

    test("incognito mode suppresses adaptive touch samples") {
        SuggestionPrivacyPolicy.shouldRecordAdaptiveTouchSample(
            isAdaptiveTouchEnabled = true,
            isIncognitoMode = true,
            keyVariation = KeyVariation.NORMAL,
        ) shouldBe false
    }

    test("app-declared NO_PERSONALIZED_LEARNING suppresses adaptive touch samples") {
        val appDeclaredPrivate = SuggestionPrivacyPolicy.resolveIncognitoMode(
            appDeclaredNoPersonalizedLearning = true,
            preference = IncognitoMode.FORCE_OFF,
            isDynamicIncognitoForced = false,
        )

        SuggestionPrivacyPolicy.shouldRecordAdaptiveTouchSample(
            isAdaptiveTouchEnabled = true,
            isIncognitoMode = appDeclaredPrivate,
            keyVariation = KeyVariation.NORMAL,
        ) shouldBe false
    }

    test("EMAIL_ADDRESS variation permits suggestions and learning") {
        val snapshot = SuggestionPrivacyPolicy.snapshotSuggestionRequest(
            emojiSuggestionEnabled = true,
            emojiMaxCandidateCount = 5,
            wordSuggestionEnabled = true,
            blockPossiblyOffensive = false,
            isPrivateSession = false,
            isEditorSensitive = false,
            keyVariation = KeyVariation.EMAIL_ADDRESS,
        )
        snapshot.wordSuggestionEnabled shouldBe true
        snapshot.isPasswordEditor shouldBe false

        SuggestionPrivacyPolicy.shouldLearnCommittedWord(
            rawWord = "user@example",
            isIncognitoMode = false,
            keyVariation = KeyVariation.EMAIL_ADDRESS,
        ) shouldBe true
    }

    test("URI variation permits suggestions and learning") {
        val snapshot = SuggestionPrivacyPolicy.snapshotSuggestionRequest(
            emojiSuggestionEnabled = true,
            emojiMaxCandidateCount = 5,
            wordSuggestionEnabled = true,
            blockPossiblyOffensive = false,
            isPrivateSession = false,
            isEditorSensitive = false,
            keyVariation = KeyVariation.URI,
        )
        snapshot.wordSuggestionEnabled shouldBe true
        snapshot.isPasswordEditor shouldBe false
    }

    test("DYNAMIC_ON_OFF incognito follows the dynamic flag when no app override") {
        SuggestionPrivacyPolicy.resolveIncognitoMode(
            appDeclaredNoPersonalizedLearning = false,
            preference = IncognitoMode.DYNAMIC_ON_OFF,
            isDynamicIncognitoForced = true,
        ) shouldBe true

        SuggestionPrivacyPolicy.resolveIncognitoMode(
            appDeclaredNoPersonalizedLearning = false,
            preference = IncognitoMode.DYNAMIC_ON_OFF,
            isDynamicIncognitoForced = false,
        ) shouldBe false
    }

    test("FORCE_ON incognito is always active") {
        SuggestionPrivacyPolicy.resolveIncognitoMode(
            appDeclaredNoPersonalizedLearning = false,
            preference = IncognitoMode.FORCE_ON,
            isDynamicIncognitoForced = false,
        ) shouldBe true
    }

    test("FORCE_OFF incognito is never active unless app overrides") {
        SuggestionPrivacyPolicy.resolveIncognitoMode(
            appDeclaredNoPersonalizedLearning = false,
            preference = IncognitoMode.FORCE_OFF,
            isDynamicIncognitoForced = true,
        ) shouldBe false
    }

    test("sensitive editor flag propagates through snapshot") {
        val snapshot = SuggestionPrivacyPolicy.snapshotSuggestionRequest(
            emojiSuggestionEnabled = true,
            emojiMaxCandidateCount = 5,
            wordSuggestionEnabled = true,
            blockPossiblyOffensive = false,
            isPrivateSession = false,
            isEditorSensitive = true,
            keyVariation = KeyVariation.NORMAL,
        )
        snapshot.isEditorSensitive shouldBe true
        snapshot.wordSuggestionEnabled shouldBe true
    }

    test("private session flag propagates through snapshot") {
        val snapshot = SuggestionPrivacyPolicy.snapshotSuggestionRequest(
            emojiSuggestionEnabled = true,
            emojiMaxCandidateCount = 5,
            wordSuggestionEnabled = true,
            blockPossiblyOffensive = false,
            isPrivateSession = true,
            isEditorSensitive = false,
            keyVariation = KeyVariation.NORMAL,
        )
        snapshot.isPrivateSession shouldBe true
    }
})
