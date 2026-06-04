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

open class GlidePrefs : PreferenceModel() {
    override val declaredPreferenceEntries = emptyMap<PreferenceModel.TypedKey, PreferenceData<*>>()

    val enabled = boolean(
        key = "glide__enabled",
        default = true,
    )
    val showTrail = boolean(
        key = "glide__show_trail",
        default = true,
    )
    val trailTheme = enum(
        key = "glide__trail_theme",
        default = GlideTrailTheme.ACCENT,
    )
    val trailDuration = int(
        key = "glide__trail_fade_duration",
        default = 500,
    )
    val showPreview = boolean(
        key = "glide__show_preview",
        default = true,
    )
    val previewRefreshDelay = int(
        key = "glide__preview_refresh_delay",
        default = 150,
    )
    val immediateBackspaceDeletesWord = boolean(
        key = "glide__immediate_backspace_deletes_word",
        default = true,
    )
    val sensitivity = int(
        key = "glide__sensitivity",
        default = 50,
    )
    val flowThroughSpace = boolean(
        key = "glide__flow_through_space",
        default = true,
    )
    val enabledEnglish = boolean(
        key = "glide__language_enabled_en",
        default = true,
    )
    val enabledGerman = boolean(
        key = "glide__language_enabled_de",
        default = true,
    )
    val enabledSpanish = boolean(
        key = "glide__language_enabled_es",
        default = true,
    )
    val enabledFrench = boolean(
        key = "glide__language_enabled_fr",
        default = true,
    )
    val enabledItalian = boolean(
        key = "glide__language_enabled_it",
        default = true,
    )
    val enabledPortuguese = boolean(
        key = "glide__language_enabled_pt",
        default = true,
    )

    fun languagePreference(languageCode: String): PreferenceData<Boolean>? {
        return when (languageCode) {
            "en" -> enabledEnglish
            "de" -> enabledGerman
            "es" -> enabledSpanish
            "fr" -> enabledFrench
            "it" -> enabledItalian
            "pt" -> enabledPortuguese
            else -> null
        }
    }

    fun isEnabledForSubtype(subtype: Subtype): Boolean {
        return languagePreference(subtype.primaryLocale.language)?.get() ?: false
    }
}
