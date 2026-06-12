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

data class SimpleModePreferenceValues(
    val suggestionsEnabled: Boolean,
    val nextWordPredictionEnabled: Boolean,
    val clipboardSuggestionEnabled: Boolean,
    val clipboardHistoryEnabled: Boolean,
    val emojiSuggestionEnabled: Boolean,
    val emojiHistoryEnabled: Boolean,
    val smartComposeConsent: AddonConsentState,
    val translationConsent: AddonConsentState,
    val mcpConsent: AddonConsentState,
)

object PrivacyPosturePolicy {
    val simpleModeValues = SimpleModePreferenceValues(
        suggestionsEnabled = false,
        nextWordPredictionEnabled = false,
        clipboardSuggestionEnabled = false,
        clipboardHistoryEnabled = false,
        emojiSuggestionEnabled = false,
        emojiHistoryEnabled = false,
        smartComposeConsent = AddonConsentState.DENIED,
        translationConsent = AddonConsentState.DENIED,
        mcpConsent = AddonConsentState.DENIED,
    )

    val fullModeValues = SimpleModePreferenceValues(
        suggestionsEnabled = true,
        nextWordPredictionEnabled = true,
        clipboardSuggestionEnabled = true,
        clipboardHistoryEnabled = false,
        emojiSuggestionEnabled = true,
        emojiHistoryEnabled = true,
        smartComposeConsent = AddonConsentState.NEEDS_PROMPT,
        translationConsent = AddonConsentState.NEEDS_PROMPT,
        mcpConsent = AddonConsentState.NEEDS_PROMPT,
    )

    fun isSimpleModeActive(values: SimpleModePreferenceValues): Boolean = values == simpleModeValues

    fun enabledCount(vararg values: Boolean): Int = values.count { it }

    fun grantedAddonSurfaceCount(vararg values: AddonConsentState): Int =
        values.count { it == AddonConsentState.GRANTED }
}
