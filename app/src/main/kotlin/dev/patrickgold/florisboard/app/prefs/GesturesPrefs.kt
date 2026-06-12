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

open class GesturesPrefs : PreferenceModel() {
    override val declaredPreferenceEntries = emptyMap<PreferenceModel.TypedKey, PreferenceData<*>>()

    val swipeUp = enum(
        key = "gestures__swipe_up",
        default = SwipeAction.SHIFT,
    )
    val swipeDown = enum(
        key = "gestures__swipe_down",
        default = SwipeAction.HIDE_KEYBOARD,
    )
    val swipeLeft = enum(
        key = "gestures__swipe_left",
        default = SwipeAction.SWITCH_TO_NEXT_SUBTYPE,
    )
    val swipeRight = enum(
        key = "gestures__swipe_right",
        default = SwipeAction.SWITCH_TO_PREV_SUBTYPE,
    )
    val spaceBarSwipeUp = enum(
        key = "gestures__space_bar_swipe_up",
        default = SwipeAction.NO_ACTION,
    )
    // Matrix #15 — downward vertical swipe on the space bar. Defaults to NO_ACTION so existing users
    // see no behavior change; matrix #14 (continuous vertical trackpad) adds the MOVE_CURSOR_DOWN
    // continuous-drag path on top of this dispatch.
    val spaceBarSwipeDown = enum(
        key = "gestures__space_bar_swipe_down",
        default = SwipeAction.NO_ACTION,
    )
    val spaceBarSwipeLeft = enum(
        key = "gestures__space_bar_swipe_left",
        default = SwipeAction.MOVE_CURSOR_LEFT,
    )
    val spaceBarSwipeRight = enum(
        key = "gestures__space_bar_swipe_right",
        default = SwipeAction.MOVE_CURSOR_RIGHT,
    )
    val spaceBarLongPress = enum(
        key = "gestures__space_bar_long_press",
        default = SwipeAction.SHOW_INPUT_METHOD_PICKER,
    )
    val symbolFlickEnabled = boolean(
        key = "gestures__symbol_flick_enabled",
        default = false,
    )
    // ROADMAP §6 N5.1 — match SwiftKey/Gboard convention: hold-backspace and
    // horizontal-swipe-on-backspace both delete by word. Existing users who set
    // a custom value via the gesture settings keep their override; only the
    // default fallback shifts.
    val deleteKeySwipeLeft = enum(
        key = "gestures__delete_key_swipe_left",
        default = SwipeAction.DELETE_WORD,
    )
    val deleteKeyLongPress = enum(
        key = "gestures__delete_key_long_press",
        default = SwipeAction.DELETE_WORD,
    )
    val swipeDistanceThreshold = int(
        key = "gestures__swipe_distance_threshold",
        default = 32,
    )
    val swipeVelocityThreshold = int(
        key = "gestures__swipe_velocity_threshold",
        default = 1900,
    )
}
