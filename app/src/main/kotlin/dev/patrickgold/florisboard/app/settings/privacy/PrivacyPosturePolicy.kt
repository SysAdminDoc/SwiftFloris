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

data class ProfilePreferenceValues(
    val suggestionsEnabled: Boolean,
    val nextWordPredictionEnabled: Boolean,
    val clipboardSuggestionEnabled: Boolean,
    val clipboardHistoryEnabled: Boolean,
    val emojiSuggestionEnabled: Boolean,
    val emojiHistoryEnabled: Boolean,
    val inputFeedbackAudioEnabled: Boolean,
    val inputFeedbackHapticEnabled: Boolean,
    val glideShowTrail: Boolean,
    val glideShowPreview: Boolean,
    val smartbarSharedActionsExpanded: Boolean,
    val smartbarExtendedActionsExpanded: Boolean,
    val smartbarSharedActionsExpandWithAnimation: Boolean,
    val smartComposeConsent: AddonConsentState,
    val translationConsent: AddonConsentState,
    val mcpConsent: AddonConsentState,
)

object PrivacyPosturePolicy {
    val simpleModeValues = ProfilePreferenceValues(
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

    val powerSavingValues = ProfilePreferenceValues(
        suggestionsEnabled = true,
        nextWordPredictionEnabled = false,
        clipboardSuggestionEnabled = false,
        clipboardHistoryEnabled = false,
        emojiSuggestionEnabled = false,
        emojiHistoryEnabled = true,
        inputFeedbackAudioEnabled = false,
        inputFeedbackHapticEnabled = false,
        glideShowTrail = false,
        glideShowPreview = false,
        smartbarSharedActionsExpanded = false,
        smartbarExtendedActionsExpanded = false,
        smartbarSharedActionsExpandWithAnimation = false,
        smartComposeConsent = AddonConsentState.NEEDS_PROMPT,
        translationConsent = AddonConsentState.NEEDS_PROMPT,
        mcpConsent = AddonConsentState.NEEDS_PROMPT,
    )

    val focusModeValues = ProfilePreferenceValues(
        suggestionsEnabled = true,
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
        smartComposeConsent = AddonConsentState.NEEDS_PROMPT,
        translationConsent = AddonConsentState.NEEDS_PROMPT,
        mcpConsent = AddonConsentState.NEEDS_PROMPT,
    )

    val fullModeValues = ProfilePreferenceValues(
        suggestionsEnabled = true,
        nextWordPredictionEnabled = true,
        clipboardSuggestionEnabled = true,
        clipboardHistoryEnabled = false,
        emojiSuggestionEnabled = true,
        emojiHistoryEnabled = true,
        inputFeedbackAudioEnabled = true,
        inputFeedbackHapticEnabled = true,
        glideShowTrail = true,
        glideShowPreview = true,
        smartbarSharedActionsExpanded = false,
        smartbarExtendedActionsExpanded = false,
        smartbarSharedActionsExpandWithAnimation = true,
        smartComposeConsent = AddonConsentState.NEEDS_PROMPT,
        translationConsent = AddonConsentState.NEEDS_PROMPT,
        mcpConsent = AddonConsentState.NEEDS_PROMPT,
    )

    fun isSimpleModeActive(values: ProfilePreferenceValues): Boolean = values == simpleModeValues

    fun isPowerSavingActive(values: ProfilePreferenceValues): Boolean = values == powerSavingValues

    fun isFocusModeActive(values: ProfilePreferenceValues): Boolean = values == focusModeValues

    fun enabledCount(vararg values: Boolean): Int = values.count { it }

    fun grantedAddonSurfaceCount(vararg values: AddonConsentState): Int =
        values.count { it == AddonConsentState.GRANTED }
}
