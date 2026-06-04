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
import dev.patrickgold.florisboard.ime.theme.PerAppAccentDiscoveryHintState
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

open class ThemePrefs : PreferenceModel() {
    override val declaredPreferenceEntries = emptyMap<PreferenceModel.TypedKey, PreferenceData<*>>()

    val mode = enum(
        key = "theme__mode",
        default = ThemeMode.ALWAYS_NIGHT,
    )
    val dayThemeId = custom(
        key = "theme__day_theme_id",
        default = extCoreTheme("swiftkey_pure_light"),
        serializer = ExtensionComponentName.Serializer,
    )
    val nightThemeId = custom(
        key = "theme__night_theme_id",
        default = extCoreTheme("swiftkey_pure_dark"),
        serializer = ExtensionComponentName.Serializer,
    )
    val accentColor = custom(
        key = "theme__accent_color",
        default = Color.Unspecified,
        serializer = ColorPreferenceSerializer,
    )
    /** ROADMAP §7 Next-11.3 + Next-11.3a — Per-app adaptive accent.
     *  When enabled, the keyboard's accent token tints toward the dominant
     *  saturated color of the foreground editor's app icon (resolved via
     *  PerAppAccentResolver). Default off because the feature reads
     *  installed-package icons (still no extra permission required, but
     *  defaulting opt-in matches the §1 privacy-by-default stance). */
    val perAppAccentEnabled = boolean(
        key = "theme__per_app_accent_enabled",
        default = false,
    )
    val perAppAccentDiscoveryHintState = enum(
        key = "theme__per_app_accent_discovery_hint_state",
        default = PerAppAccentDiscoveryHintState.COLLECTING,
    )
    val sunriseTime = localTime(
        key = "theme__sunrise_time",
        default = LocalTime(6, 0),
    )
    val sunsetTime = localTime(
        key = "theme__sunset_time",
        default = LocalTime(18, 0),
    )
    val editorColorRepresentation = enum(
        key = "theme__editor_color_representation",
        default = ColorRepresentation.HEX,
    )
    val editorDisplayKbdAfterDialogs = enum(
        key = "theme__editor_display_kbd_after_dialogs",
        default = DisplayKbdAfterDialogs.REMEMBER,
    )
    val editorLevel = enum(
        key = "theme__editor_level",
        default = SnyggLevel.ADVANCED,
    )
}
