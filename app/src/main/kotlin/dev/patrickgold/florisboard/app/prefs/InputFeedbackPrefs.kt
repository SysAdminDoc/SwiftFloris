/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.app.prefs

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import dev.patrickgold.florisboard.app.settings.theme.ColorPreferenceSerializer
import dev.patrickgold.florisboard.app.settings.theme.DisplayKbdAfterDialogs
import dev.patrickgold.florisboard.app.settings.theme.SnyggLevel
import dev.patrickgold.florisboard.app.setup.NotificationPermissionState
import dev.patrickgold.florisboard.ime.clipboard.CLIPBOARD_HISTORY_NUM_GRID_COLUMNS_AUTO
import dev.patrickgold.florisboard.ime.clipboard.ClipboardSyncBehavior
import dev.patrickgold.florisboard.ime.core.DisplayLanguageNamesIn
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.input.CapitalizationBehavior
import dev.patrickgold.florisboard.ime.input.HapticVibrationMode
import dev.patrickgold.florisboard.ime.input.InputFeedbackActivationMode
import dev.patrickgold.florisboard.ime.keyboard.IncognitoMode
import dev.patrickgold.florisboard.ime.keyboard.SpaceBarMode
import dev.patrickgold.florisboard.ime.landscapeinput.LandscapeInputUiMode
import dev.patrickgold.florisboard.ime.media.emoji.EmojiHairStyle
import dev.patrickgold.florisboard.ime.media.emoji.EmojiHistory
import dev.patrickgold.florisboard.ime.media.emoji.EmojiSkinTone
import dev.patrickgold.florisboard.ime.smartcompose.AddonConsentState
import dev.patrickgold.florisboard.ime.media.emoji.EmojiSuggestionType
import dev.patrickgold.florisboard.ime.nlp.SpellingLanguageMode
import dev.patrickgold.florisboard.ime.smartbar.CandidatesDisplayMode
import dev.patrickgold.florisboard.ime.smartbar.ExtendedActionsPlacement
import dev.patrickgold.florisboard.ime.smartbar.IncognitoDisplayMode
import dev.patrickgold.florisboard.ime.smartbar.SmartbarLayout
import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickAction
import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickActionArrangement
import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickActionJsonConfig
import dev.patrickgold.florisboard.ime.text.gestures.GlideTrailTheme
import dev.patrickgold.florisboard.ime.text.gestures.SwipeAction
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.key.KeyHintConfiguration
import dev.patrickgold.florisboard.ime.text.key.KeyHintMode
import dev.patrickgold.florisboard.ime.text.key.UtilityKeyAction
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.ime.theme.ThemeMode
import dev.patrickgold.florisboard.ime.theme.extCoreTheme
import dev.patrickgold.florisboard.ime.voice.VoiceCommandCustomCommands
import dev.patrickgold.florisboard.ime.voice.VoiceModelPreference
import dev.patrickgold.florisboard.ime.voice.VoiceRecognitionEnginePreference
import dev.patrickgold.florisboard.ime.window.ImeWindowConfig
import dev.patrickgold.florisboard.lib.ext.ExtensionComponentName
import dev.patrickgold.florisboard.lib.util.VersionName
import dev.patrickgold.jetpref.datastore.model.LocalTime
import dev.patrickgold.jetpref.datastore.model.PreferenceData
import dev.patrickgold.jetpref.datastore.model.PreferenceModel
import org.florisboard.lib.android.isOrientationPortrait
import dev.patrickgold.florisboard.app.AppTheme
import dev.patrickgold.jetpref.material.ui.ColorRepresentation

open class InputFeedbackPrefs : PreferenceModel() {
    override val declaredPreferenceEntries = emptyMap<PreferenceModel.TypedKey, PreferenceData<*>>()

    val audioEnabled = boolean(
        key = "input_feedback__audio_enabled",
        default = true,
    )
    val audioActivationMode = enum(
        key = "input_feedback__audio_activation_mode",
        default = InputFeedbackActivationMode.RESPECT_SYSTEM_SETTINGS,
    )
    val audioVolume = int(
        key = "input_feedback__audio_volume",
        default = 50,
    )
    val audioFeatKeyPress = boolean(
        key = "input_feedback__audio_feat_key_press",
        default = true,
    )
    val audioFeatKeyLongPress = boolean(
        key = "input_feedback__audio_feat_key_long_press",
        default = false,
    )
    val audioFeatKeyRepeatedAction = boolean(
        key = "input_feedback__audio_feat_key_repeated_action",
        default = false,
    )
    val audioFeatGestureSwipe = boolean(
        key = "input_feedback__audio_feat_gesture_swipe",
        default = false,
    )
    val audioFeatGestureMovingSwipe = boolean(
        key = "input_feedback__audio_feat_gesture_moving_swipe",
        default = false,
    )

    val hapticEnabled = boolean(
        key = "input_feedback__haptic_enabled",
        default = true,
    )
    val hapticActivationMode = enum(
        key = "input_feedback__haptic_activation_mode",
        default = InputFeedbackActivationMode.RESPECT_SYSTEM_SETTINGS,
    )
    val hapticVibrationMode = enum(
        key = "input_feedback__haptic_vibration_mode",
        default = HapticVibrationMode.USE_VIBRATOR_DIRECTLY,
    )
    // ROADMAP §6 N3.3 — SwiftKey-aligned haptic profile defaults: ~20ms duration,
    // amplitude 153/255 ≈ 60%. The default keyboard-tap haptic on FlorisBoard
    // upstream (65ms / 70%) feels heavier than SwiftKey/Gboard. Existing users
    // who have customized these prefs are unaffected (jetpref fall-back-only-when-unset).
    val hapticVibrationDuration = int(
        key = "input_feedback__haptic_vibration_duration",
        default = 20,
    )
    val hapticVibrationStrength = int(
        key = "input_feedback__haptic_vibration_strength",
        default = 60,
    )
    val hapticFeatKeyPress = boolean(
        key = "input_feedback__haptic_feat_key_press",
        default = true,
    )
    val hapticFeatKeyLongPress = boolean(
        key = "input_feedback__haptic_feat_key_long_press",
        default = false,
    )
    val hapticFeatKeyRepeatedAction = boolean(
        key = "input_feedback__haptic_feat_key_repeated_action",
        default = true,
    )
    val hapticFeatGestureSwipe = boolean(
        key = "input_feedback__haptic_feat_gesture_swipe",
        default = false,
    )
    val hapticFeatGestureMovingSwipe = boolean(
        key = "input_feedback__haptic_feat_gesture_moving_swipe",
        default = true,
    )
}
