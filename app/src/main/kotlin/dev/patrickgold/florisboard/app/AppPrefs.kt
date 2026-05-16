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

package dev.patrickgold.florisboard.app

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
import dev.patrickgold.jetpref.datastore.annotations.Preferences
import dev.patrickgold.jetpref.datastore.jetprefDataStoreOf
import dev.patrickgold.jetpref.datastore.model.LocalTime
import dev.patrickgold.jetpref.datastore.model.PreferenceData
import dev.patrickgold.jetpref.datastore.model.PreferenceMigrationEntry
import dev.patrickgold.jetpref.datastore.model.PreferenceModel
import dev.patrickgold.jetpref.datastore.model.PreferenceType
import dev.patrickgold.jetpref.material.ui.ColorRepresentation
import kotlinx.serialization.json.Json
import org.florisboard.lib.android.isOrientationPortrait

val FlorisPreferenceStore = jetprefDataStoreOf(FlorisPreferenceModel::class)

@Preferences
abstract class FlorisPreferenceModel : PreferenceModel() {
    companion object {
        const val NAME = "florisboard-app-prefs"
    }

    val clipboard = Clipboard()
    inner class Clipboard {
        val useInternalClipboard = boolean(
            key = "clipboard__use_internal_clipboard",
            default = false,
        )
        val syncToFloris = enum(
            key = "clipboard__sync_to_floris",
            default = ClipboardSyncBehavior.ALL_EVENTS,
        )
        val syncToSystem = enum(
            key = "clipboard__sync_to_system",
            default = ClipboardSyncBehavior.NO_EVENTS,
        )
        val suggestionEnabled = boolean(
            key = "clipboard__suggestion_enabled",
            default = true,
        )
        val suggestionTimeout = int(
            key = "clipboard__suggestion_timeout",
            default = 60,
        )
        val historyEnabled = boolean(
            key = "clipboard__history_enabled",
            default = false,
        )
        val historyNumGridColumnsPortrait = int(
            key = "clipboard__history_num_grid_columns_portrait",
            default = CLIPBOARD_HISTORY_NUM_GRID_COLUMNS_AUTO,
        )
        val historyNumGridColumnsLandscape = int(
            key = "clipboard__history_num_grid_columns_landscape",
            default = CLIPBOARD_HISTORY_NUM_GRID_COLUMNS_AUTO,
        )
        @Composable
        fun historyNumGridColumns(): PreferenceData<Int> {
            val configuration = LocalConfiguration.current
            return if (configuration.isOrientationPortrait()) {
                historyNumGridColumnsPortrait
            } else {
                historyNumGridColumnsLandscape
            }
        }
        val historyAutoCleanOldEnabled = boolean(
            key = "clipboard__history_auto_clean_old_enabled",
            default = false,
        )
        val historyAutoCleanOldAfter = int(
            key = "clipboard__history_auto_clean_old_after",
            default = 20,
        )
        val historyAutoCleanSensitiveEnabled = boolean(
            key = "clipboard__history_auto_clean_sensitive_enabled",
            default = false,
        )
        val historyAutoCleanSensitiveAfter = int(
            key = "clipboard__history_auto_clean_sensitive_after",
            default = 20,
        )
        val historySizeLimitEnabled = boolean(
            key = "clipboard__history_size_limit_enabled",
            default = true,
        )
        val historySizeLimit = int(
            key = "clipboard__history_size_limit",
            default = 20,
        )
        val historyHideOnPaste = boolean(
            key = "clipboard__history_hide_on_paste",
            default = false,
        )
        val historyHideOnNextTextField = boolean(
            key = "clipboard__history_hide_on_next_text_field",
            default = true,
        )
        val clearPrimaryClipAffectsHistoryIfUnpinned = boolean(
            key = "clipboard__clear_primary_clip_affects_history_if_unpinned",
            default = true,
        )

        /**
         * ROADMAP matrix #33 — toggle for the clipboard history search box. When `true`, the clipboard panel
         * exposes a search bar that filters the visible history via [dev.patrickgold.florisboard.ime.clipboard
         * .ClipboardHistoryFilter]. The actual filter is privacy-neutral (no network, no logging) so the toggle
         * exists primarily as a UI density preference — users on small phones may prefer the extra row of
         * tiles over the search affordance.
         */
        val historySearchEnabled = boolean(
            key = "clipboard__history_search_enabled",
            default = true,
        )
    }

    val correction = Correction()
    inner class Correction {
        val autoCapitalization = boolean(
            key = "correction__auto_capitalization",
            default = true,
        )
        val autoCorrect = boolean(
            key = "correction__auto_correct",
            default = true,
        )
        val quickPredictionInsert = boolean(
            key = "correction__quick_prediction_insert",
            default = false,
        )
        val autoSpacePunctuation = boolean(
            key = "correction__auto_space_punctuation",
            default = true,
        )
        val doubleSpacePeriod = boolean(
            key = "correction__double_space_period",
            default = true,
        )
        val rememberCapsLockState = boolean(
            key = "correction__remember_caps_lock_state",
            default = false,
        )
        val adaptiveTouchModel = boolean(
            key = "correction__adaptive_touch_model",
            default = true,
        )
        val multilingualSuggestions = boolean(
            key = "correction__multilingual_suggestions",
            default = true,
        )
    }

    val devtools = Devtools()
    inner class Devtools {
        val enabled = boolean(
            key = "devtools__enabled",
            default = false,
        )
        val showPrimaryClip = boolean(
            key = "devtools__show_primary_clip",
            default = false,
        )
        val showInputStateOverlay = boolean(
            key = "devtools__show_input_state_overlay",
            default = false,
        )
        val showSpellingOverlay = boolean(
            key = "devtools__show_spelling_overlay",
            default = false,
        )
        val showInlineAutofillOverlay = boolean(
            key = "devtools__show_inline_autofill_overlay",
            default = false,
        )
        val showKeyTouchBoundaries = boolean(
            key = "devtools__show_touch_boundaries",
            default = false,
        )
        val showDragAndDropHelpers = boolean(
            key = "devtools__show_drag_and_drop_helpers",
            default = false,
        )
        val showWindowResizeHandleBoundaries = boolean(
            key = "devtools__show_window_resize_handle_boundaries",
            default = false,
        )
    }

    val dictionary = Dictionary()
    inner class Dictionary {
        val enableSystemUserDictionary = boolean(
            key = "suggestion__enable_system_user_dictionary",
            default = true,
        )
        val enableFlorisUserDictionary = boolean(
            key = "suggestion__enable_floris_user_dictionary",
            default = true,
        )
    }

    val voice = Voice()
    inner class Voice {
        val recognitionEnginePreference = enum(
            key = "voice__recognition_engine_preference",
            default = VoiceRecognitionEnginePreference.AUTO,
        )
        val embeddedModelPreference = enum(
            key = "voice__embedded_model_preference",
            default = VoiceModelPreference.AUTO,
        )
        val customCommands = custom(
            key = "voice__custom_commands",
            default = VoiceCommandCustomCommands.Empty,
            serializer = VoiceCommandCustomCommands.Serializer,
        )
    }

    val emoji = Emoji()
    inner class Emoji {
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
        val suggestionQueryMinLength = int(
            key = "emoji__suggestion_query_min_length",
            default = 3,
        )
        val suggestionCandidateMaxCount = int(
            key = "emoji__suggestion_candidate_max_count",
            default = 5,
        )
    }

    val gestures = Gestures()
    inner class Gestures {
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

    val glide = Glide()
    inner class Glide {
        val enabled = boolean(
            key = "glide__enabled",
            default = true,
        )
        val showTrail = boolean(
            key = "glide__show_trail",
            default = true,
        )
        val trailDuration = int(
            key = "glide__trail_fade_duration",
            default = 200,
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

    val inputFeedback = InputFeedback()
    inner class InputFeedback {
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

    val internal = Internal()
    inner class Internal {
        val homeIsBetaToolboxCollapsed = boolean(
            key = "internal__home_is_beta_toolbox_collapsed_040a01",
            default = false,
        )
        val isImeSetUp = boolean(
            key = "internal__is_ime_set_up",
            default = false,
        )
        val versionOnInstall = string(
            key = "internal__version_on_install",
            default = VersionName.DEFAULT_RAW,
        )
        val versionLastUse = string(
            key = "internal__version_last_use",
            default = VersionName.DEFAULT_RAW,
        )
        val versionLastChangelog = string(
            key = "internal__version_last_changelog",
            default = VersionName.DEFAULT_RAW,
        )
        val notificationPermissionState = enum(
            key = "internal__notification_permission_state",
            default = NotificationPermissionState.NOT_SET,
        )
    }

    val sync = Sync()
    inner class Sync {
        val channelId = string(
            key = "sync__channel_id",
            default = "swiftfloris:disabled",
        )
        val clusterId = string(
            key = "sync__cluster_id",
            default = "",
        )
        val deviceId = string(
            key = "sync__device_id",
            default = "",
        )
        val pairedDevicesJson = string(
            key = "sync__paired_devices_json",
            default = "[]",
        )
        val manualExportTargetUri = string(
            key = "sync__manual_export_target_uri",
            default = "",
        )
    }

    val mcp = Mcp()
    inner class Mcp {
        /**
         * ROADMAP §10.5 L7.6b — newline-separated list of MCP daemon package
         * names the user has disabled. `McpDispatchRouter` consults this set
         * before forwarding a `callTool` request; daemons in the set are
         * still bound but never receive traffic. Storing as a single string
         * (`\n` joined) avoids the need for a `Set<String>` JetPref type
         * that doesn't ship in this version of the datastore.
         */
        val disabledDaemonPackages = string(
            key = "mcp__disabled_daemon_packages",
            default = "",
        )

        /**
         * ROADMAP matrix #38 — newline-separated list of MCP tools the user has
         * disabled at per-tool granularity. Entries are formatted as
         * `<daemonPackageName>::<toolName>`. `McpDispatchRouter` consults this
         * set after the daemon-disabled gate; a disabled tool short-circuits to
         * `Response.Suppressed("tool X on daemon Y disabled by user")`. Per-tool
         * disables stack with per-daemon disables (disabling a whole daemon
         * implicitly disables every tool it advertises). Codec lives in
         * [dev.patrickgold.florisboard.ime.mcp.DisabledToolSet].
         */
        val disabledTools = string(
            key = "mcp__disabled_tools",
            default = "",
        )
    }

    val keyboard = Keyboard()
    inner class Keyboard {
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
         *  The actual key-rect distribution lands in Next-7.2a. */
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

    val localization = Localization()
    inner class Localization {
        val displayLanguageNamesIn = enum(
            key = "localization__display_language_names_in",
            default = DisplayLanguageNamesIn.SYSTEM_LOCALE,
        )
        val displayKeyboardLabelsInSubtypeLanguage = boolean(
            key = "localization__display_keyboard_labels_in_subtype_language",
            default = false,
        )
        val activeSubtypeId = long(
            key = "localization__active_subtype_id",
            default = Subtype.DEFAULT.id,
        )
        val subtypes = string(
            key = "localization__subtypes",
            default = "[]",
        )
    }

    val privacy = Privacy()
    inner class Privacy {
        /**
         * ROADMAP matrix #37 — addon consent state for the smart-compose surface. Defaults to
         * `NEEDS_PROMPT` so the IME presents a one-time consent dialog on first invocation rather than
         * silently running addon code on the user's behalf. The router consults this via the
         * [AddonConsentState.allowsInvocation] predicate; only `GRANTED` lets invocations through.
         */
        val smartComposeConsent = enum(
            key = "privacy__addon_consent_smart_compose",
            default = AddonConsentState.NEEDS_PROMPT,
        )
        val translationConsent = enum(
            key = "privacy__addon_consent_translation",
            default = AddonConsentState.NEEDS_PROMPT,
        )
        val mcpConsent = enum(
            key = "privacy__addon_consent_mcp",
            default = AddonConsentState.NEEDS_PROMPT,
        )
    }

    val other = Other()
    inner class Other {
        val settingsTheme = enum(
            key = "other__settings_theme",
            default = AppTheme.AUTO,
        )
        val accentColor = custom(
            key = "other__accent_color",
            default = Color.Unspecified,
            serializer = ColorPreferenceSerializer,
        )
        val settingsLanguage = string(
            key = "other__settings_language",
            default = "auto",
        )
        val showAppIcon = boolean(
            key = "other__show_app_icon",
            default = true,
        )
    }

    val physicalKeyboard = PhysicalKeyboard()
    inner class PhysicalKeyboard {
        val showOnScreenKeyboard = boolean(
            key = "physical_keyboard__show_on_screen_keyboard",
            default = false,
        )
    }

    val smartbar = Smartbar()
    inner class Smartbar {
        val enabled = boolean(
            key = "smartbar__enabled",
            default = true,
        )
        val layout = enum(
            key = "smartbar__layout",
            default = SmartbarLayout.SUGGESTIONS_ACTIONS_SHARED,
        )
        val actionArrangement = custom(
            key = "smartbar__action_arrangement",
            default = QuickActionArrangement.Default,
            serializer = QuickActionArrangement.Serializer,
        )
        val perAppProfilesEnabled = boolean(
            key = "smartbar__per_app_profiles_enabled",
            default = true,
        )
        val flipToggles = boolean(
            key = "smartbar__flip_toggles",
            default = false,
        )
        val sharedActionsExpanded = boolean(
            key = "smartbar__shared_actions_expanded",
            default = false,
        )
        @Deprecated("Always enabled due to UX issues")
        val sharedActionsAutoExpandCollapse = boolean(
            key = "smartbar__shared_actions_auto_expand_collapse",
            default = true,
        )
        val sharedActionsExpandWithAnimation = boolean(
            key = "smartbar__shared_actions_expand_with_animation",
            default = true,
        )
        val extendedActionsExpanded = boolean(
            key = "smartbar__extended_actions_expanded",
            default = false,
        )
        val extendedActionsPlacement = enum(
            key = "smartbar__extended_actions_placement",
            default = ExtendedActionsPlacement.ABOVE_CANDIDATES,
        )
    }

    val spelling = Spelling()
    inner class Spelling {
        val languageMode = enum(
            key = "spelling__language_mode",
            default = SpellingLanguageMode.USE_KEYBOARD_SUBTYPES,
        )
        val useContacts = boolean(
            key = "spelling__use_contacts",
            default = true,
        )
        val useUdmEntries = boolean(
            key = "spelling__use_udm_entries",
            default = true,
        )
    }

    val suggestion = Suggestion()
    inner class Suggestion {
        val api30InlineSuggestionsEnabled = boolean(
            key = "suggestion__api30_inline_suggestions_enabled",
            default = true,
        )
        val enabled = boolean(
            key = "suggestion__enabled",
            default = true,
        )
        val displayMode = enum(
            key = "suggestion__display_mode",
            default = CandidatesDisplayMode.CLASSIC,
        )
        val blockPossiblyOffensive = boolean(
            key = "suggestion__block_possibly_offensive",
            default = true,
        )
        val incognitoMode = enum(
            key = "suggestion__incognito_mode",
            default = IncognitoMode.DYNAMIC_ON_OFF,
        )
        // Internal pref
        val forceIncognitoModeFromDynamic = boolean(
            key = "suggestion__force_incognito_mode_from_dynamic",
            default = false,
        )
        val nextWordPrediction = boolean(
            key = "suggestion__next_word_prediction",
            default = true,
        )
    }

    val theme = Theme()
    inner class Theme {
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

    override fun migrate(entry: PreferenceMigrationEntry): PreferenceMigrationEntry {
        return when (entry.key) {

            // Migrate media prefs to emoji prefs
            // Keep migration rule until: 0.6 dev cycle
            "media__emoji_recently_used" -> {
                val emojiValues = entry.rawValue.split(";")
                val recent = emojiValues.map {
                    dev.patrickgold.florisboard.ime.media.emoji.Emoji(it, "", emptyList())
                }
                val data = EmojiHistory(emptyList(), recent)
                entry.transform(key = "emoji__history_data", rawValue = Json.encodeToString(data))
            }
            "media__emoji_recently_used_max_size" -> {
                entry.transform(key = "emoji__history_recent_max_size")
            }

            // Migrate advanced prefs to other prefs
            // Keep migration rules until: 0.7 dev cycle
            "advanced__settings_theme" -> {
                entry.transform(key = "other__settings_theme")
            }
            "advanced__accent_color" -> {
                entry.transform(key = "other__accent_color")
            }
            "advanced__settings_language" -> {
                entry.transform(key = "other__settings_language")
            }
            "advanced__show_app_icon" -> {
                entry.transform(key = "other__show_app_icon")
            }
            "advanced__incognito_mode" -> {
                entry.transform(key = "suggestion__incognito_mode")
            }
            "advanced__force_incognito_mode_from_dynamic" -> {
                entry.transform(key = "suggestion__force_incognito_mode_from_dynamic")
            }
            // Migrate clipboard suggestion prefs to clipboard
            // Keep migration rules until: 0.7 dev cycle
            "suggestion__clipboard_content_enabled" -> {
                entry.transform(key = "clipboard__suggestion_enabled")
            }
            "suggestion__clipboard_content_timeout" -> {
                entry.transform(key = "clipboard__suggestion_timeout")
            }

            //Migrate one hand mode prefs keep until: 0.7 dev cycle
            "keyboard__one_handed_mode" -> {
                if (entry.rawValue == "OFF") {
                    entry.reset()
                } else {
                    entry.keepAsIs()
                }
            }
            "smartbar__action_arrangement" -> {
                fun migrateAction(action: QuickAction): QuickAction {
                    return if (action is QuickAction.InsertKey && action.data.code == KeyCode.COMPACT_LAYOUT_TO_RIGHT) {
                        action.copy(data = TextKeyData.TOGGLE_COMPACT_LAYOUT)
                    } else {
                        action
                    }
                }

                val arrangement = QuickActionArrangement.Serializer.deserialize(entry.rawValue)
                var newArrangement = arrangement.copy(
                    stickyAction = arrangement.stickyAction?.let{ migrateAction(it) },
                    dynamicActions = arrangement.dynamicActions.map { migrateAction(it) },
                    hiddenActions = arrangement.hiddenActions.map { migrateAction(it) },
                )
                if (newArrangement.stickyAction == QuickAction.InsertKey(TextKeyData.VOICE_INPUT)) {
                    newArrangement = newArrangement.copy(stickyAction = null)
                }
                if (QuickAction.InsertKey(TextKeyData.LANGUAGE_SWITCH) !in newArrangement) {
                    newArrangement = newArrangement.copy(
                        dynamicActions = newArrangement.dynamicActions.plus(QuickAction.InsertKey(TextKeyData.LANGUAGE_SWITCH))
                    )
                }
                if (QuickAction.InsertKey(TextKeyData.FORWARD_DELETE) !in newArrangement) {
                    newArrangement = newArrangement.copy(
                        dynamicActions = newArrangement.dynamicActions.plus(QuickAction.InsertKey(TextKeyData.FORWARD_DELETE))
                    )
                }
                if (QuickAction.InsertKey(TextKeyData.IME_HIDE_UI) !in newArrangement) {
                    newArrangement = newArrangement.copy(
                        dynamicActions = newArrangement.dynamicActions.plus(QuickAction.InsertKey(TextKeyData.IME_HIDE_UI))
                    )
                }
                if (QuickAction.InsertKey(TextKeyData.TOGGLE_FLOATING_WINDOW) !in newArrangement) {
                    newArrangement = newArrangement.copy(
                        dynamicActions = newArrangement.dynamicActions.plus(QuickAction.InsertKey(TextKeyData.TOGGLE_FLOATING_WINDOW))
                    )
                }
                if (QuickAction.InsertKey(TextKeyData.TOGGLE_RESIZE_MODE) !in newArrangement) {
                    newArrangement = newArrangement.copy(
                        dynamicActions = newArrangement.dynamicActions.plus(QuickAction.InsertKey(TextKeyData.TOGGLE_RESIZE_MODE))
                    )
                }
                val json = QuickActionJsonConfig.encodeToString(newArrangement.distinct())
                entry.transform(rawValue = json)
            }

            // Migrate theme editor fine-tuning
            // Keep migration rule until: 0.6 dev cycle
            "theme__editor_display_colors_as" -> {
                val colorRepresentation = when (entry.rawValue) {
                    "RGBA" -> ColorRepresentation.RGB
                    else -> ColorRepresentation.HEX
                }
                entry.transform(
                    key = "theme__editor_color_representation",
                    rawValue = colorRepresentation.name,
                )
            }

            // Migrate clipboard history pref names
            // Keep migration rules until: 0.7 dev cycle
            "clipboard__sync_to_floris", "clipboard__sync_to_system" -> {
                entry.transform(
                    type = PreferenceType.string(),
                    rawValue = when (entry.rawValue) {
                        "true" -> ClipboardSyncBehavior.ALL_EVENTS.name
                        "false" -> ClipboardSyncBehavior.NO_EVENTS.name
                        else -> entry.rawValue
                    },
                )
            }
            "clipboard__num_history_grid_columns_portrait" -> {
                entry.transform(key = "clipboard__history_num_grid_columns_portrait")
            }
            "clipboard__num_history_grid_columns_landscape" -> {
                entry.transform(key = "clipboard__history_num_grid_columns_landscape")
            }
            "clipboard__clean_up_old" -> {
                entry.transform(key = "clipboard__history_auto_clean_old_enabled")
            }
            "clipboard__clean_up_after" -> {
                entry.transform(key = "clipboard__history_auto_clean_old_after")
            }
            "clipboard__auto_clean_sensitive" -> {
                entry.transform(key = "clipboard__history_auto_clean_sensitive_enabled")
            }
            "clipboard__auto_clean_sensitive_after" -> {
                entry.transform(key = "clipboard__history_auto_clean_sensitive_after")
            }
            "clipboard__limit_history_size" -> {
                entry.transform(key = "clipboard__history_size_limit_enabled")
            }
            "clipboard__max_history_size" -> {
                entry.transform(key = "clipboard__history_size_limit")
            }
            "clipboard__clear_primary_clip_deletes_last_item" -> {
                entry.transform(key = "clipboard__clear_primary_clip_affects_history_if_unpinned")
            }

            // Migrate key spacing rules
            // Keep migration rules until: 0.8 dev cycle
            "keyboard__key_spacing_horizontal" -> {
                if (entry.type.isFloat()) {
                    entry.reset()
                } else {
                    entry.keepAsIs()
                }
            }
            "keyboard__key_spacing_vertical" -> {
                if (entry.type.isFloat()) {
                    entry.reset()
                } else {
                    entry.keepAsIs()
                }
            }
            "keyboard__number_row" -> {
                if (entry.rawValue.equals("false", ignoreCase = true)) {
                    entry.transform(rawValue = "true")
                } else {
                    entry.keepAsIs()
                }
            }
            "keyboard__hinted_number_row_enabled",
            "keyboard__hinted_symbols_enabled" -> {
                if (entry.rawValue.equals("true", ignoreCase = true)) {
                    entry.transform(rawValue = "false")
                } else {
                    entry.keepAsIs()
                }
            }
            "keyboard__utility_key_action" -> {
                if (entry.rawValue == UtilityKeyAction.DYNAMIC_SWITCH_LANGUAGE_EMOJIS.name) {
                    entry.transform(rawValue = UtilityKeyAction.SWITCH_TO_EMOJIS.name)
                } else {
                    entry.keepAsIs()
                }
            }
            "keyboard__space_bar_display_mode" -> {
                if (entry.rawValue == SpaceBarMode.CURRENT_LANGUAGE.name) {
                    entry.transform(rawValue = SpaceBarMode.NOTHING.name)
                } else {
                    entry.keepAsIs()
                }
            }
            "suggestion__display_mode" -> {
                if (entry.rawValue == CandidatesDisplayMode.DYNAMIC_SCROLLABLE.name) {
                    entry.transform(rawValue = CandidatesDisplayMode.CLASSIC.name)
                } else {
                    entry.keepAsIs()
                }
            }
            "theme__mode" -> {
                if (entry.rawValue == ThemeMode.FOLLOW_SYSTEM.name) {
                    entry.transform(rawValue = ThemeMode.ALWAYS_NIGHT.name)
                } else {
                    entry.keepAsIs()
                }
            }
            "theme__day_theme_id" -> {
                if (entry.rawValue == "org.florisboard.themes:floris_day") {
                    entry.transform(rawValue = "org.florisboard.themes:swiftkey_pure_light")
                } else {
                    entry.keepAsIs()
                }
            }
            "theme__night_theme_id" -> {
                if (entry.rawValue == "org.florisboard.themes:floris_night") {
                    entry.transform(rawValue = "org.florisboard.themes:swiftkey_pure_dark")
                } else {
                    entry.keepAsIs()
                }
            }

            // Default: keep entry
            else -> entry.keepAsIs()
        }
    }
}
