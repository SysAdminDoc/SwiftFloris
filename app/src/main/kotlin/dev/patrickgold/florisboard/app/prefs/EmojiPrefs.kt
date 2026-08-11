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

open class EmojiPrefs : PreferenceModel() {
    override val declaredPreferenceEntries = emptyMap<PreferenceModel.TypedKey, PreferenceData<*>>()

    val preferredSkinTone = enum(
        key = "emoji__preferred_skin_tone",
        default = EmojiSkinTone.DEFAULT,
    )
    val preferredHairStyle = enum(
        key = "emoji__preferred_hair_style",
        default = EmojiHairStyle.DEFAULT,
    )
    val historyEnabled = boolean(
        key = "emoji__history_enabled",
        default = true,
    )
    val historyData = custom(
        key = "emoji__history_data",
        default = EmojiHistory.Empty,
        serializer = EmojiHistory.Serializer,
    )
    val historyPinnedUpdateStrategy = enum(
        key = "emoji__history_pinned_update_strategy",
        default = EmojiHistory.UpdateStrategy.MANUAL_SORT_PREPEND,
    )
    val historyPinnedMaxSize = int(
        key = "emoji__history_pinned_max_size",
        default = EmojiHistory.MaxSizeUnlimited,
    )
    val historyRecentUpdateStrategy = enum(
        key = "emoji__history_recent_update_strategy",
        default = EmojiHistory.UpdateStrategy.AUTO_SORT_PREPEND,
    )
    val historyRecentMaxSize = int(
        key = "emoji__history_recent_max_size",
        default = 90,
    )
    val suggestionEnabled = boolean(
        key = "emoji__suggestion_enabled",
        default = true,
    )
    val suggestionType = enum(
        key = "emoji__suggestion_type",
        default = EmojiSuggestionType.LEADING_COLON,
    )
    val suggestionUpdateHistory = boolean(
        key = "emoji__suggestion_update_history",
        default = true,
    )
    val suggestionCandidateShowName = boolean(
        key = "emoji__suggestion_candidate_show_name",
        default = false,
    )
    /**
     * Keep the typed word and put the emoji after it, instead of replacing the
     * word with the emoji. Only meaningful for [EmojiSuggestionType.INLINE_TEXT]
     * - a `:shortcode:` trigger is scaffolding the user does not want left
     * behind. Default off, so the shipped behaviour is unchanged.
     */
    val suggestionCandidateAppendToWord = boolean(
        key = "emoji__suggestion_candidate_append_to_word",
        default = false,
    )
    val suggestionQueryMinLength = int(
        key = "emoji__suggestion_query_min_length",
        default = 3,
    )
    val suggestionCandidateMaxCount = int(
        key = "emoji__suggestion_candidate_max_count",
        default = 5,
    )
}
