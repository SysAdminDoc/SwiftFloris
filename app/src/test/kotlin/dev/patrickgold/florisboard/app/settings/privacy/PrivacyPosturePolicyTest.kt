/*
 * Copyright (C) 2026 SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.app.settings.privacy

import dev.patrickgold.florisboard.ime.smartcompose.AddonConsentState
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PrivacyPosturePolicyTest : FunSpec({
    test("simple mode disables optional learning, history, and addon surfaces") {
        PrivacyPosturePolicy.simpleModeValues shouldBe ProfilePreferenceValues(
            suggestionsEnabled = false,
            nextWordPredictionEnabled = false,
            clipboardSuggestionEnabled = false,
            clipboardHistoryEnabled = false,
            emojiSuggestionEnabled = false,
            emojiHistoryEnabled = false,
            inputFeedbackAudioEnabled = true,
            inputFeedbackHapticEnabled = true,
            glideShowTrail = true,
            glideShowPreview = true,
            smartbarSharedActionsExpanded = false,
            smartbarExtendedActionsExpanded = false,
            smartbarSharedActionsExpandWithAnimation = true,
            smartComposeConsent = AddonConsentState.DENIED,
            translationConsent = AddonConsentState.DENIED,
            mcpConsent = AddonConsentState.DENIED,
        )
    }

    test("power saving disables feedback, animated chrome, and predictive extras") {
        PrivacyPosturePolicy.powerSavingValues.inputFeedbackAudioEnabled shouldBe false
        PrivacyPosturePolicy.powerSavingValues.inputFeedbackHapticEnabled shouldBe false
        PrivacyPosturePolicy.powerSavingValues.glideShowTrail shouldBe false
        PrivacyPosturePolicy.powerSavingValues.glideShowPreview shouldBe false
        PrivacyPosturePolicy.powerSavingValues.smartbarSharedActionsExpandWithAnimation shouldBe false
        PrivacyPosturePolicy.powerSavingValues.nextWordPredictionEnabled shouldBe false
        PrivacyPosturePolicy.powerSavingValues.clipboardSuggestionEnabled shouldBe false
        PrivacyPosturePolicy.powerSavingValues.emojiSuggestionEnabled shouldBe false
        PrivacyPosturePolicy.isPowerSavingActive(PrivacyPosturePolicy.powerSavingValues) shouldBe true
    }

    test("focus mode hides retention and expanded action surfaces without disabling feedback") {
        PrivacyPosturePolicy.focusModeValues.clipboardHistoryEnabled shouldBe false
        PrivacyPosturePolicy.focusModeValues.clipboardSuggestionEnabled shouldBe false
        PrivacyPosturePolicy.focusModeValues.emojiHistoryEnabled shouldBe false
        PrivacyPosturePolicy.focusModeValues.emojiSuggestionEnabled shouldBe false
        PrivacyPosturePolicy.focusModeValues.smartbarSharedActionsExpanded shouldBe false
        PrivacyPosturePolicy.focusModeValues.smartbarExtendedActionsExpanded shouldBe false
        PrivacyPosturePolicy.focusModeValues.inputFeedbackAudioEnabled shouldBe true
        PrivacyPosturePolicy.focusModeValues.inputFeedbackHapticEnabled shouldBe true
        PrivacyPosturePolicy.isFocusModeActive(PrivacyPosturePolicy.focusModeValues) shouldBe true
    }

    test("restore profile re-enables defaults without granting addon consent") {
        PrivacyPosturePolicy.fullModeValues.smartComposeConsent shouldBe AddonConsentState.NEEDS_PROMPT
        PrivacyPosturePolicy.fullModeValues.translationConsent shouldBe AddonConsentState.NEEDS_PROMPT
        PrivacyPosturePolicy.fullModeValues.mcpConsent shouldBe AddonConsentState.NEEDS_PROMPT
        PrivacyPosturePolicy.fullModeValues.suggestionsEnabled shouldBe true
        PrivacyPosturePolicy.fullModeValues.emojiHistoryEnabled shouldBe true
        PrivacyPosturePolicy.fullModeValues.inputFeedbackAudioEnabled shouldBe true
        PrivacyPosturePolicy.fullModeValues.inputFeedbackHapticEnabled shouldBe true
    }

    test("simple mode active requires the exact simple profile") {
        PrivacyPosturePolicy.isSimpleModeActive(PrivacyPosturePolicy.simpleModeValues) shouldBe true
        PrivacyPosturePolicy.isSimpleModeActive(
            PrivacyPosturePolicy.simpleModeValues.copy(emojiHistoryEnabled = true),
        ) shouldBe false
    }
})
