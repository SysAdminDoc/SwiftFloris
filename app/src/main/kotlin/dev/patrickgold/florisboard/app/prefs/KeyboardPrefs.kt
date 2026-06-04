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

open class KeyboardPrefs : PreferenceModel() {
    override val declaredPreferenceEntries = emptyMap<PreferenceModel.TypedKey, PreferenceData<*>>()

    val windowConfig = custom(
        key = "keyboard__window_config",
        default = emptyMap(),
        serializer = ImeWindowConfig.ByTypeSerializer,
    )
    val numberRow = boolean(
        key = "keyboard__number_row",
        default = true,
    )
    val hintedNumberRowEnabled = boolean(
        key = "keyboard__hinted_number_row_enabled",
        default = false,
    )
    val hintedNumberRowMode = enum(
        key = "keyboard__hinted_number_row_mode",
        default = KeyHintMode.SMART_PRIORITY,
    )
    val hintedSymbolsEnabled = boolean(
        key = "keyboard__hinted_symbols_enabled",
        default = false,
    )
    val hintedSymbolsMode = enum(
        key = "keyboard__hinted_symbols_mode",
        default = KeyHintMode.SMART_PRIORITY,
    )
    val bottomRowPresetJson = string(
        key = "keyboard__bottom_row_preset_json",
        default = "automatic",
    )
    val utilityKeyEnabled = boolean(
        key = "keyboard__utility_key_enabled",
        default = true,
    )
    val utilityKeyAction = enum(
        key = "keyboard__utility_key_action",
        default = UtilityKeyAction.SWITCH_TO_EMOJIS,
    )
    val spaceBarMode = enum(
        key = "keyboard__space_bar_display_mode",
        default = SpaceBarMode.NOTHING,
    )
    val capitalizationBehavior = enum(
        key = "keyboard__capitalization_behavior",
        default = CapitalizationBehavior.CAPSLOCK_BY_DOUBLE_TAP,
    )
    val fontSizeMultiplierPortrait = int(
        key = "keyboard__font_size_multiplier_portrait",
        default = 100,
    )
    val fontSizeMultiplierLandscape = int(
        key = "keyboard__font_size_multiplier_landscape",
        default = 100,
    )
    // ROADMAP §6 N5.3 — Scalable keyboard height (HeliBoard #1342, ASK #1775).
    // Percentages applied to the form-factor-derived defKeyboardHeight, then
    // re-constrained to [minKeyboardHeight, maxKeyboardHeight] in
    // ImeWindowController.doComputeWindowSpec.
    val keyboardHeightMultiplierPortrait = int(
        key = "keyboard__keyboard_height_multiplier_portrait",
        default = 100,
    )
    val keyboardHeightMultiplierLandscape = int(
        key = "keyboard__keyboard_height_multiplier_landscape",
        default = 100,
    )
    /** ROADMAP §7 Next-4.3 — stylus handwriting per-subtype toggle.
     *  When false the IME does not request the system stylus-handwriting
     *  delegate even on Android 14+ devices with a compatible stylus; the
     *  field falls back to standard touch input. Recogniser plumbing is
     *  Next-4.2's slot, so the toggle currently only gates Next-4.1's
     *  `onStartStylusHandwriting()` callback. */
    val stylusHandwritingEnabled = boolean(
        key = "keyboard__stylus_handwriting_enabled",
        default = false,
    )
    /** ROADMAP §7 Next-7.1 — floating-mode default. When true the IME
     *  starts in floating mode on first show; when false it starts in
     *  the existing fixed-mode default. The user can still toggle at
     *  runtime via the existing TOGGLE_COMPACT_LAYOUT / window-mode
     *  controls. The actual `ImeWindowController` initial-state read
     *  happens in `ImeWindowController.onWindowShown` and falls back to
     *  the stored `ImeWindowConfig` when this preference is false (the
     *  pre-existing behaviour). */
    val startInFloatingMode = boolean(
        key = "keyboard__start_in_floating_mode",
        default = false,
    )
    /** ROADMAP §10 Next-7.1a — one-shot floating-window onboarding.
     *  False means the next floating-mode entry shows the drag/resize
     *  tooltip and immediately marks this flag true. Settings exposes
     *  a reset action for QA and user rediscovery. */
    val floatingOnboardingShown = boolean(
        key = "keyboard__floating_onboarding_shown",
        default = false,
    )
    /** ROADMAP §7 Next-7.2 — split-keyboard layout-mode foundation.
     *  When true and the current form factor is wide enough (see
     *  [ImeWindowConstraints.Fixed.Split.minTabletWidthDp]), the IME
     *  routes the fixed-mode renderer through the `Fixed.SPLIT`
     *  sub-mode instead of `Fixed.NORMAL`. Narrow phones simply
     *  ignore the preference because the split is not viable there.
     *  `TextKeyboardSplitLayout` + `SplitGutterPostPass` perform the
     *  actual key-rect distribution. */
    val splitKeyboardEnabled = boolean(
        key = "keyboard__split_keyboard_enabled",
        default = false,
    )
    val landscapeInputUiMode = enum(
        key = "keyboard__landscape_input_ui_mode",
        default = LandscapeInputUiMode.DYNAMICALLY_SHOW,
    )
    val keySpacingVertical = int(
        key = "keyboard__key_spacing_vertical",
        default = 100,
    )
    val keySpacingHorizontal = int(
        key = "keyboard__key_spacing_horizontal",
        default = 100,
    )
    val popupEnabled = boolean(
        key = "keyboard__popup_enabled",
        default = true,
    )
    val mergeHintPopupsEnabled = boolean(
        key = "keyboard__merge_hint_popups_enabled",
        default = false,
    )
    val longPressDelay = int(
        key = "keyboard__long_press_delay",
        default = 300,
    )
    val spaceBarSwitchesToCharacters = boolean(
        key = "keyboard__space_bar_switches_to_characters",
        default = true,
    )
    val autoReturnAfterApostrophe = boolean(
        key = "keyboard__auto_return_after_apostrophe",
        default = true,
    )
    val quoteAutoCloseEnabled = boolean(
        key = "keyboard__quote_auto_close_enabled",
        default = true,
    )
    val incognitoDisplayMode = enum(
        key = "keyboard__incognito_indicator",
        default = IncognitoDisplayMode.DISPLAY_BEHIND_KEYBOARD,
    )

    fun keyHintConfiguration(): KeyHintConfiguration {
        return KeyHintConfiguration(
            numberHintMode = when {
                hintedNumberRowEnabled.get() -> hintedNumberRowMode.get()
                else -> KeyHintMode.DISABLED
            },
            symbolHintMode = when {
                hintedSymbolsEnabled.get() -> hintedSymbolsMode.get()
                else -> KeyHintMode.DISABLED
            },
            mergeHintPopups = mergeHintPopupsEnabled.get(),
        )
    }
}
