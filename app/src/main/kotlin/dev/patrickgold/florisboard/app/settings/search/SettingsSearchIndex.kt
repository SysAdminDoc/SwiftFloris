/*
 * Copyright (C) 2026 SwiftFloris Contributors
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

package dev.patrickgold.florisboard.app.settings.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.patrickgold.florisboard.R
import java.text.Normalizer

enum class SettingsSearchDestination {
    HOME,
    LOCALIZATION,
    SELECT_LOCALE,
    PER_APP_LANGUAGE,
    LANGUAGE_PACK_MANAGER,
    SUBTYPE_ADD,
    THEME,
    THEME_MANAGER,
    KEYBOARD,
    INPUT_FEEDBACK,
    SMARTBAR,
    TYPING,
    TYPING_STATS,
    VOICE_INPUT,
    DICTIONARY,
    MIGRATION_ASSISTANT,
    USER_DICTIONARY_SYSTEM,
    USER_DICTIONARY_FLORIS,
    LEARNED_ENTRIES,
    SYNC,
    MCP,
    ADDONS,
    EXTENSIONS,
    GESTURES,
    CLIPBOARD,
    MEDIA,
    OTHER,
    PHYSICAL_KEYBOARD,
    BACKUP,
    RESTORE,
    PRIVACY_POSTURE,
    PER_APP_KEYBOARD_PROFILES,
    PRIVACY_AUDIT,
    SNIPPET_SETTINGS,
    ABOUT,
    AI_FEATURES,
    PROJECT_LICENSE,
    THIRD_PARTY_LICENSES,
    DEVTOOLS,
}

data class SettingsSearchEntry(
    val id: String,
    val screenTitleResId: Int,
    val titleResId: Int,
    val summaryResId: Int? = null,
    val destination: SettingsSearchDestination,
    val keywords: List<String> = emptyList(),
)

data class SettingsSearchResult(
    val entry: SettingsSearchEntry,
    val score: Int,
)

data class SettingsSearchTarget(
    val entryId: String,
    val screenTitle: String,
    val title: String,
    val summary: String?,
    val query: String,
)

object SettingsSearchHighlightStore {
    var activeTarget by mutableStateOf<SettingsSearchTarget?>(null)
        private set

    fun mark(entry: SettingsSearchEntry, query: String, resolveString: (Int) -> String) {
        activeTarget = SettingsSearchTarget(
            entryId = entry.id,
            screenTitle = resolveString(entry.screenTitleResId),
            title = resolveString(entry.titleResId),
            summary = entry.summaryResId?.let(resolveString),
            query = query.trim(),
        )
    }

    fun consumeTargetFor(screenTitle: String): SettingsSearchTarget? {
        val target = activeTarget
        return if (target?.screenTitle == screenTitle) {
            activeTarget = null
            target
        } else {
            null
        }
    }

    fun clear() {
        activeTarget = null
    }
}

object SettingsSearchIndex {
    val entries = listOf(
        entry("home", R.string.settings__title, R.string.settings__title, R.string.settings__home__about_summary, SettingsSearchDestination.HOME),

        entry("localization", R.string.settings__localization__title, R.string.settings__localization__title, R.string.settings__home__localization_summary, SettingsSearchDestination.LOCALIZATION, "language layout subtype"),
        entry("localization.display-names", R.string.settings__localization__title, R.string.settings__localization__display_language_names_in__label, destination = SettingsSearchDestination.LOCALIZATION),
        entry("localization.keyboard-labels", R.string.settings__localization__title, R.string.settings__localization__display_keyboard_labels_in_subtype_language, destination = SettingsSearchDestination.LOCALIZATION),
        entry("localization.per-app-language", R.string.settings__per_app_language__title, R.string.settings__per_app_language__title, R.string.settings__per_app_language__summary, SettingsSearchDestination.PER_APP_LANGUAGE, "remember language app subtype package"),
        entry("localization.select-locale", R.string.settings__localization__subtype_select_locale, R.string.settings__localization__subtype_select_locale, destination = SettingsSearchDestination.SELECT_LOCALE, keywords = "language picker"),
        entry("localization.language-packs", R.string.settings__localization__language_pack_title, R.string.settings__localization__language_pack_title, R.string.settings__localization__language_pack_summary, SettingsSearchDestination.LANGUAGE_PACK_MANAGER),
        entry("localization.add-subtype", R.string.settings__localization__subtype_add_title, R.string.settings__localization__subtype_add_title, destination = SettingsSearchDestination.SUBTYPE_ADD, keywords = "keyboard language add"),

        entry("keyboard", R.string.settings__keyboard__title, R.string.settings__keyboard__title, R.string.settings__home__keyboard_summary, SettingsSearchDestination.KEYBOARD),
        entry("keyboard.number-row", R.string.settings__keyboard__title, R.string.pref__keyboard__number_row__label, destination = SettingsSearchDestination.KEYBOARD),
        entry("keyboard.hinted-number-row", R.string.settings__keyboard__title, R.string.pref__keyboard__hinted_number_row_mode__label, destination = SettingsSearchDestination.KEYBOARD),
        entry("keyboard.hinted-symbols", R.string.settings__keyboard__title, R.string.pref__keyboard__hinted_symbols_mode__label, destination = SettingsSearchDestination.KEYBOARD),
        entry("keyboard.bottom-row", R.string.settings__keyboard__title, R.string.pref__keyboard__bottom_row_preset__label, destination = SettingsSearchDestination.KEYBOARD, keywords = "voice navigation spacebar"),
        entry("keyboard.spacebar-mode", R.string.settings__keyboard__title, R.string.pref__keyboard__space_bar_mode__label, destination = SettingsSearchDestination.KEYBOARD),
        entry("keyboard.capitalization", R.string.settings__keyboard__title, R.string.pref__keyboard__capitalization_behavior__label, destination = SettingsSearchDestination.KEYBOARD),
        entry("keyboard.incognito", R.string.settings__keyboard__title, R.string.pref__keyboard__incognito_indicator__label, destination = SettingsSearchDestination.KEYBOARD),
        entry("keyboard.custom-layout-editor", R.string.settings__keyboard__title, R.string.settings__keyboard__custom_layout_editor__title, R.string.settings__keyboard__custom_layout_editor__summary, SettingsSearchDestination.KEYBOARD, "custom layout editor clone local character row key"),
        entry("keyboard.landscape", R.string.settings__keyboard__title, R.string.pref__keyboard__landscape_input_ui_mode__label, destination = SettingsSearchDestination.KEYBOARD),
        entry("keyboard.font-size", R.string.settings__keyboard__title, R.string.pref__keyboard__font_size_multiplier__label, destination = SettingsSearchDestination.KEYBOARD),
        entry("keyboard.height", R.string.settings__keyboard__title, R.string.pref__keyboard__keyboard_height_multiplier__label, destination = SettingsSearchDestination.KEYBOARD),
        entry("keyboard.spacing", R.string.settings__keyboard__title, R.string.pref__keyboard__key_spacing__label, destination = SettingsSearchDestination.KEYBOARD),
        entry("keyboard.floating", R.string.settings__keyboard__title, R.string.pref__keyboard__start_in_floating_mode__label, destination = SettingsSearchDestination.KEYBOARD),
        entry("keyboard.popups", R.string.settings__keyboard__title, R.string.pref__keyboard__popup_enabled__label, destination = SettingsSearchDestination.KEYBOARD),
        entry("keyboard.long-press", R.string.settings__keyboard__title, R.string.pref__keyboard__long_press_delay__label, destination = SettingsSearchDestination.KEYBOARD),

        entry("input-feedback", R.string.settings__input_feedback__title, R.string.settings__input_feedback__title, R.string.settings__home__input_feedback_summary, SettingsSearchDestination.INPUT_FEEDBACK, "sound vibration haptic keypress"),
        entry("smartbar", R.string.settings__smartbar__title, R.string.settings__smartbar__title, R.string.settings__home__smartbar_summary, SettingsSearchDestination.SMARTBAR, "candidate suggestion strip actions"),
        entry("smartbar.enabled", R.string.settings__smartbar__title, R.string.pref__smartbar__enabled__label, R.string.pref__smartbar__enabled__summary, SettingsSearchDestination.SMARTBAR),
        entry("smartbar.layout", R.string.settings__smartbar__title, R.string.pref__smartbar__layout__label, destination = SettingsSearchDestination.SMARTBAR),
        entry("smartbar.display-mode", R.string.settings__smartbar__title, R.string.pref__suggestion__display_mode__label, destination = SettingsSearchDestination.SMARTBAR),

        entry("typing", R.string.settings__typing__title, R.string.settings__typing__title, R.string.settings__home__typing_summary, SettingsSearchDestination.TYPING, "suggestion autocorrect spelling"),
        entry("typing.suggestions", R.string.settings__typing__title, R.string.pref__suggestion__enabled__label, R.string.pref__suggestion__enabled__summary, SettingsSearchDestination.TYPING),
        entry("typing.offensive", R.string.settings__typing__title, R.string.pref__suggestion__block_possibly_offensive__label, R.string.pref__suggestion__block_possibly_offensive__summary, SettingsSearchDestination.TYPING),
        entry("typing.incognito", R.string.settings__typing__title, R.string.pref__suggestion__incognito_mode__label, destination = SettingsSearchDestination.TYPING),
        entry("typing.auto-capitalization", R.string.settings__typing__title, R.string.pref__correction__auto_capitalization__label, R.string.pref__correction__auto_capitalization__summary, SettingsSearchDestination.TYPING),
        entry("typing.autocorrect", R.string.settings__typing__title, R.string.pref__correction__auto_correct__label, R.string.pref__correction__auto_correct__summary, SettingsSearchDestination.TYPING),
        entry("typing.quick-prediction", R.string.settings__typing__title, R.string.pref__correction__quick_prediction_insert__label, R.string.pref__correction__quick_prediction_insert__summary, SettingsSearchDestination.TYPING),
        entry("typing.auto-space-punctuation", R.string.settings__typing__title, R.string.pref__correction__auto_space_punctuation__label, R.string.pref__correction__auto_space_punctuation__summary, SettingsSearchDestination.TYPING, "punctuation period spacing auto space"),
        entry("typing.double-space-period", R.string.settings__typing__title, R.string.pref__correction__double_space_period__label, R.string.pref__correction__double_space_period__summary, SettingsSearchDestination.TYPING),
        entry("typing.adaptive-touch", R.string.settings__typing__title, R.string.pref__correction__adaptive_touch_model__label, R.string.pref__correction__adaptive_touch_model__summary, SettingsSearchDestination.TYPING),
        entry("typing.next-word", R.string.settings__typing__title, R.string.pref__suggestion__next_word_prediction__label, R.string.pref__suggestion__next_word_prediction__summary, SettingsSearchDestination.TYPING),
        entry("typing.multilingual", R.string.settings__typing__title, R.string.pref__correction__multilingual_suggestions__label, R.string.pref__correction__multilingual_suggestions__summary, SettingsSearchDestination.TYPING),
        entry("typing.smart-compose", R.string.settings__typing__title, R.string.pref__correction__heuristic_smart_compose__label, R.string.pref__correction__heuristic_smart_compose__summary, SettingsSearchDestination.TYPING),
        entry("typing.spell-checker", R.string.settings__typing__title, R.string.pref__spelling__title, destination = SettingsSearchDestination.TYPING),
        entry("typing.stats", R.string.settings__typing_stats__title, R.string.settings__typing_stats__title, R.string.settings__typing_stats__summary, SettingsSearchDestination.TYPING_STATS, "erase dictionary learned words trace"),
        entry("typing.snippets", R.string.settings__snippet__title, R.string.settings__snippet__title, R.string.settings__snippet__summary, SettingsSearchDestination.SNIPPET_SETTINGS, "snippet expansion espanso yaml trigger replace text shortcut"),

        entry("theme", R.string.settings__theme__title, R.string.settings__theme__title, R.string.settings__home__theme_summary, SettingsSearchDestination.THEME),
        entry("theme.mode", R.string.settings__theme__title, R.string.pref__theme__mode__label, destination = SettingsSearchDestination.THEME, keywords = "dark light system appearance mode"),
        entry("theme.sunrise", R.string.settings__theme__title, R.string.pref__theme__sunrise_time__label, destination = SettingsSearchDestination.THEME),
        entry("theme.sunset", R.string.settings__theme__title, R.string.pref__theme__sunset_time__label, destination = SettingsSearchDestination.THEME),
        entry("theme.day", R.string.settings__theme__title, R.string.pref__theme__day, destination = SettingsSearchDestination.THEME),
        entry("theme.night", R.string.settings__theme__title, R.string.pref__theme__night, destination = SettingsSearchDestination.THEME),
        entry("theme.accent", R.string.settings__theme__title, R.string.pref__theme__theme_accent_color__label, destination = SettingsSearchDestination.THEME),
        entry("theme.per-app-accent", R.string.settings__theme__title, R.string.pref__theme__per_app_accent_enabled__label, R.string.pref__theme__per_app_accent_enabled__summary, SettingsSearchDestination.THEME),
        entry("theme.customization", R.string.settings__theme__title, R.string.pref__theme__customization__label, R.string.pref__theme__customization__summary, SettingsSearchDestination.THEME),
        entry("theme.manager", R.string.ext__list__ext_theme, R.string.settings__theme_manager__title_manage, destination = SettingsSearchDestination.THEME_MANAGER, keywords = "installed themes"),

        entry("gestures", R.string.settings__gestures__title, R.string.settings__gestures__title, R.string.settings__home__gestures_summary, SettingsSearchDestination.GESTURES, "glide swipe trail"),
        entry("gestures.glide", R.string.settings__gestures__title, R.string.pref__glide__enabled__label, destination = SettingsSearchDestination.GESTURES, keywords = "swipe trace shape writing gesture typing"),
        entry("gestures.glide-sensitivity", R.string.settings__gestures__title, R.string.pref__glide__sensitivity__label, destination = SettingsSearchDestination.GESTURES, keywords = "swipe trace shape writing threshold"),
        entry("gestures.trail", R.string.settings__gestures__title, R.string.pref__glide__show_trail__label, destination = SettingsSearchDestination.GESTURES, keywords = "trace shape writing path"),
        entry("gestures.symbol-flick", R.string.settings__gestures__title, R.string.pref__gestures__symbol_flick_enabled__label, destination = SettingsSearchDestination.GESTURES, keywords = "punctuation programmer hinted symbols"),
        entry("gestures.spacebar-sensitivity", R.string.settings__gestures__title, R.string.pref__gestures__space_bar_swipe_sensitivity__label, destination = SettingsSearchDestination.GESTURES, keywords = "spacebar cursor touchpad swipe threshold"),
        entry("gestures.delete-sensitivity", R.string.settings__gestures__title, R.string.pref__gestures__delete_key_swipe_sensitivity__label, destination = SettingsSearchDestination.GESTURES, keywords = "backspace delete swipe threshold"),
        entry("gestures.language-switch-sensitivity", R.string.settings__gestures__title, R.string.pref__gestures__language_switch_swipe_sensitivity__label, destination = SettingsSearchDestination.GESTURES, keywords = "language subtype switch swipe threshold"),

        entry("voice", R.string.settings__voice_input__title, R.string.settings__voice_input__title, R.string.settings__home__voice_input_summary, SettingsSearchDestination.VOICE_INPUT, "futo microphone whisper vosk dictation"),
        entry("voice.engine", R.string.settings__voice_input__title, R.string.settings__voice_input__recognition_engine_preference, destination = SettingsSearchDestination.VOICE_INPUT),
        entry("voice.model", R.string.settings__voice_input__title, R.string.settings__voice_input__embedded_model_preference, destination = SettingsSearchDestination.VOICE_INPUT),
        entry("voice.futo", R.string.settings__voice_input__title, R.string.settings__voice_input__open_futo_language_settings, destination = SettingsSearchDestination.VOICE_INPUT),

        entry("dictionary", R.string.settings__dictionary__title, R.string.settings__dictionary__title, R.string.settings__home__dictionary_summary, SettingsSearchDestination.DICTIONARY, "personal words"),
        entry("dictionary.migration-assistant", R.string.settings__migration_assistant__title, R.string.settings__migration_assistant__title, R.string.settings__migration_assistant__home_summary, SettingsSearchDestination.MIGRATION_ASSISTANT, "swiftkey gboard import migrate words"),
        entry("dictionary.system", R.string.settings__udm__title_system, R.string.pref__dictionary__manage_system_user_dictionary__label, destination = SettingsSearchDestination.USER_DICTIONARY_SYSTEM),
        entry("dictionary.floris", R.string.settings__udm__title_floris, R.string.pref__dictionary__manage_floris_user_dictionary__label, destination = SettingsSearchDestination.USER_DICTIONARY_FLORIS),
        entry("dictionary.learned", R.string.settings__learned_entries__title, R.string.pref__dictionary__manage_learned_entries__label, R.string.pref__dictionary__manage_learned_entries__summary, SettingsSearchDestination.LEARNED_ENTRIES, "learned words bigrams trigrams forget remove phrases predictions"),

        entry("clipboard", R.string.settings__clipboard__title, R.string.settings__clipboard__title, R.string.settings__home__clipboard_summary, SettingsSearchDestination.CLIPBOARD),
        entry("clipboard.internal", R.string.settings__clipboard__title, R.string.pref__clipboard__use_internal_clipboard__label, R.string.pref__clipboard__use_internal_clipboard__summary, SettingsSearchDestination.CLIPBOARD),
        entry("clipboard.history", R.string.settings__clipboard__title, R.string.pref__clipboard__enable_clipboard_history__label, R.string.pref__clipboard__enable_clipboard_history__summary, SettingsSearchDestination.CLIPBOARD),
        entry("clipboard.suggestion", R.string.settings__clipboard__title, R.string.pref__clipboard__suggestion_enabled__label, R.string.pref__clipboard__suggestion_enabled__summary, SettingsSearchDestination.CLIPBOARD),
        entry("clipboard.search", R.string.settings__clipboard__title, R.string.pref__clipboard__history_search_enabled__label, R.string.pref__clipboard__history_search_enabled__summary, SettingsSearchDestination.CLIPBOARD),
        entry("clipboard.cleanup", R.string.settings__clipboard__title, R.string.pref__clipboard__clean_up_old__label, R.string.pref__clipboard__clean_up_old__summary, SettingsSearchDestination.CLIPBOARD),
        entry("clipboard.sensitive", R.string.settings__clipboard__title, R.string.pref__clipboard__auto_clean_sensitive__label, R.string.pref__clipboard__auto_clean_sensitive__summary, SettingsSearchDestination.CLIPBOARD),

        entry("media", R.string.settings__media__title, R.string.settings__media__title, R.string.settings__home__media_summary, SettingsSearchDestination.MEDIA, "emoji stickers gif"),
        entry("media.emoji-history", R.string.settings__media__title, R.string.prefs__media__emoji_history_enabled, R.string.prefs__media__emoji_history_enabled__summary, SettingsSearchDestination.MEDIA),
        entry("media.emoji-suggestions", R.string.settings__media__title, R.string.prefs__media__emoji_suggestion_enabled, R.string.prefs__media__emoji_suggestion_enabled__summary, SettingsSearchDestination.MEDIA),
        entry("media.sticker-pack-import", R.string.settings__media__title, R.string.prefs__media__stickers_pack_import, R.string.prefs__media__stickers_pack_import__summary, SettingsSearchDestination.MEDIA, "local stickers pack zip share image import export"),
        entry("media.sticker-pack-export", R.string.settings__media__title, R.string.prefs__media__stickers_pack_export, R.string.prefs__media__stickers_pack_export__summary, SettingsSearchDestination.MEDIA, "local stickers pack zip portable backup"),
        entry("media.stickers", R.string.settings__media__title, R.string.prefs__media__stickers_folder, R.string.prefs__media__stickers_folder__summary_empty, SettingsSearchDestination.MEDIA),

        entry("sync", R.string.settings__sync__title, R.string.settings__sync__title, R.string.settings__home__sync_summary, SettingsSearchDestination.SYNC, "pair devices local folder manual export syncthing"),
        entry("mcp", R.string.settings__mcp__title, R.string.settings__mcp__title, R.string.settings__home__mcp_summary, SettingsSearchDestination.MCP, "daemon bridge tools"),
        entry("addons", R.string.settings__addons__title, R.string.settings__addons__title, R.string.settings__home__addons_summary, SettingsSearchDestination.ADDONS, "apk dictionary packs rejected install"),
        entry("extensions", R.string.ext__home__title, R.string.ext__home__title, R.string.settings__home__extensions_summary, SettingsSearchDestination.EXTENSIONS, "keyboard theme language extension"),
        entry("privacy-posture", R.string.settings__privacy_posture__title, R.string.settings__privacy_posture__title, R.string.settings__privacy_posture__home_summary, SettingsSearchDestination.PRIVACY_POSTURE, "privacy posture simple mode power saving focus mode low distraction no internet clipboard learning addons voice"),
        entry("privacy-posture.automation", R.string.settings__privacy_posture__title, R.string.settings__privacy_posture__automation_title, R.string.settings__privacy_posture__automation_summary, SettingsSearchDestination.PRIVACY_POSTURE, "tasker automation broadcast intent macro external"),
        entry("per-app-keyboard-profiles", R.string.settings__per_app_keyboard_profiles__title, R.string.settings__per_app_keyboard_profiles__title, R.string.settings__per_app_keyboard_profiles__intro_summary, SettingsSearchDestination.PER_APP_KEYBOARD_PROFILES, "per app package profile adaptive accent incognito clipboard suggestions gestures app-specific"),
        entry("privacy-audit", R.string.settings__privacy_audit__title, R.string.settings__privacy_audit__title, R.string.settings__privacy_audit__home_summary, SettingsSearchDestination.PRIVACY_AUDIT, "local audit log addon privacy data safety"),

        entry("other", R.string.settings__other__title, R.string.settings__other__title, R.string.settings__home__other_summary, SettingsSearchDestination.OTHER, "system advanced"),
        entry("other.theme", R.string.settings__other__title, R.string.pref__other__settings_theme__label, destination = SettingsSearchDestination.OTHER),
        entry("other.accent", R.string.settings__other__title, R.string.pref__other__settings_accent_color__label, destination = SettingsSearchDestination.OTHER),
        entry("other.language", R.string.settings__other__title, R.string.pref__other__settings_language__label, destination = SettingsSearchDestination.OTHER),
        entry("other.app-icon", R.string.settings__other__title, R.string.pref__other__show_app_icon__label, destination = SettingsSearchDestination.OTHER),
        entry("physical-keyboard", R.string.physical_keyboard__title, R.string.physical_keyboard__title, R.string.settings__other__physical_keyboard_summary, SettingsSearchDestination.PHYSICAL_KEYBOARD, "hardware keyboard"),
        entry("devtools", R.string.devtools__title, R.string.devtools__title, R.string.settings__other__devtools_summary, SettingsSearchDestination.DEVTOOLS, "debug logs android settings"),
        entry("backup", R.string.backup_and_restore__back_up__title, R.string.backup_and_restore__back_up__title, R.string.backup_and_restore__back_up__summary, SettingsSearchDestination.BACKUP),
        entry("restore", R.string.backup_and_restore__restore__title, R.string.backup_and_restore__restore__title, R.string.backup_and_restore__restore__summary, SettingsSearchDestination.RESTORE),

        entry("about", R.string.about__title, R.string.about__title, R.string.settings__home__about_summary, SettingsSearchDestination.ABOUT, "version source license privacy"),
        entry("about.ai", R.string.about__ai_features__title, R.string.about__ai_features__title, R.string.about__ai_features__summary, SettingsSearchDestination.AI_FEATURES, "machine learning disclosure"),
        entry("about.project-license", R.string.about__project_license__title, R.string.about__project_license__title, destination = SettingsSearchDestination.PROJECT_LICENSE, keywords = "apache"),
        entry("about.third-party", R.string.about__third_party_licenses__title, R.string.about__third_party_licenses__title, destination = SettingsSearchDestination.THIRD_PARTY_LICENSES, keywords = "open source licenses"),

        // Generated from the preference rows the settings screens declare, so that a new
        // preference without an index entry fails SettingsSearchIndexIntegrityTest rather than
        // silently returning nothing when a user searches for it.
        // gestures/GesturesScreen.kt
        entry("gestures.trail-theme", R.string.settings__gestures__title, R.string.pref__glide__trail_theme__label, destination = SettingsSearchDestination.GESTURES),
        entry("gestures.glide-trail-fade-duration", R.string.settings__gestures__title, R.string.pref__glide_trail_fade_duration, destination = SettingsSearchDestination.GESTURES),
        entry("gestures.show-preview", R.string.settings__gestures__title, R.string.pref__glide__show_preview, destination = SettingsSearchDestination.GESTURES),
        entry("gestures.glide-preview-refresh-delay", R.string.settings__gestures__title, R.string.pref__glide_preview_refresh_delay, destination = SettingsSearchDestination.GESTURES),
        entry("gestures.immediate-backspace-deletes-word", R.string.settings__gestures__title, R.string.pref__glide__immediate_backspace_deletes_word__label, destination = SettingsSearchDestination.GESTURES),
        entry("gestures.flow-through-space", R.string.settings__gestures__title, R.string.pref__glide__flow_through_space__label, destination = SettingsSearchDestination.GESTURES),
        entry("gestures.language-enabled", R.string.settings__gestures__title, R.string.pref__glide__language_enabled__label, destination = SettingsSearchDestination.GESTURES),
        entry("gestures.swipe-up", R.string.settings__gestures__title, R.string.pref__gestures__swipe_up__label, destination = SettingsSearchDestination.GESTURES),
        entry("gestures.swipe-down", R.string.settings__gestures__title, R.string.pref__gestures__swipe_down__label, destination = SettingsSearchDestination.GESTURES),
        entry("gestures.swipe-left", R.string.settings__gestures__title, R.string.pref__gestures__swipe_left__label, destination = SettingsSearchDestination.GESTURES),
        entry("gestures.swipe-right", R.string.settings__gestures__title, R.string.pref__gestures__swipe_right__label, destination = SettingsSearchDestination.GESTURES),
        entry("gestures.space-bar-touchpad-mode", R.string.settings__gestures__title, R.string.pref__gestures__space_bar_touchpad_mode__label, destination = SettingsSearchDestination.GESTURES),
        entry("gestures.space-bar-swipe-up", R.string.settings__gestures__title, R.string.pref__gestures__space_bar_swipe_up__label, destination = SettingsSearchDestination.GESTURES),
        entry("gestures.space-bar-swipe-down", R.string.settings__gestures__title, R.string.pref__gestures__space_bar_swipe_down__label, destination = SettingsSearchDestination.GESTURES),
        entry("gestures.space-bar-swipe-left", R.string.settings__gestures__title, R.string.pref__gestures__space_bar_swipe_left__label, destination = SettingsSearchDestination.GESTURES),
        entry("gestures.space-bar-swipe-right", R.string.settings__gestures__title, R.string.pref__gestures__space_bar_swipe_right__label, destination = SettingsSearchDestination.GESTURES),
        entry("gestures.space-bar-long-press", R.string.settings__gestures__title, R.string.pref__gestures__space_bar_long_press__label, destination = SettingsSearchDestination.GESTURES),
        entry("gestures.delete-key-swipe-left", R.string.settings__gestures__title, R.string.pref__gestures__delete_key_swipe_left__label, destination = SettingsSearchDestination.GESTURES),
        entry("gestures.delete-key-long-press", R.string.settings__gestures__title, R.string.pref__gestures__delete_key_long_press__label, destination = SettingsSearchDestination.GESTURES),
        entry("gestures.swipe-velocity-threshold", R.string.settings__gestures__title, R.string.pref__gestures__swipe_velocity_threshold__label, destination = SettingsSearchDestination.GESTURES),
        entry("gestures.swipe-distance-threshold", R.string.settings__gestures__title, R.string.pref__gestures__swipe_distance_threshold__label, destination = SettingsSearchDestination.GESTURES),

        // privacy/PrivacyPostureScreen.kt
        entry("privacy.network", R.string.settings__privacy_posture__title, R.string.settings__privacy_posture__network_title, destination = SettingsSearchDestination.PRIVACY_POSTURE),
        entry("privacy.advanced-protection", R.string.settings__privacy_posture__title, R.string.settings__privacy_posture__advanced_protection_title, destination = SettingsSearchDestination.PRIVACY_POSTURE),
        entry("privacy.learning", R.string.settings__privacy_posture__title, R.string.settings__privacy_posture__learning_title, destination = SettingsSearchDestination.PRIVACY_POSTURE),
        entry("privacy.clipboard", R.string.settings__privacy_posture__title, R.string.settings__privacy_posture__clipboard_title, destination = SettingsSearchDestination.PRIVACY_POSTURE),
        entry("privacy.emoji", R.string.settings__privacy_posture__title, R.string.settings__privacy_posture__emoji_title, destination = SettingsSearchDestination.PRIVACY_POSTURE),
        entry("privacy.addons", R.string.settings__privacy_posture__title, R.string.settings__privacy_posture__addons_title, destination = SettingsSearchDestination.PRIVACY_POSTURE),
        entry("privacy.automation-rotate", R.string.settings__privacy_posture__title, R.string.settings__privacy_posture__automation_rotate_title, destination = SettingsSearchDestination.PRIVACY_POSTURE),
        entry("privacy.voice", R.string.settings__privacy_posture__title, R.string.settings__privacy_posture__voice_title, destination = SettingsSearchDestination.PRIVACY_POSTURE),
        entry("privacy.export-policy", R.string.settings__privacy_posture__title, R.string.settings__privacy_posture__export_policy_title, destination = SettingsSearchDestination.PRIVACY_POSTURE),
        entry("privacy.per-app-profiles", R.string.settings__privacy_posture__title, R.string.settings__privacy_posture__per_app_profiles_title, destination = SettingsSearchDestination.PRIVACY_POSTURE),
        entry("privacy.source-code", R.string.settings__privacy_posture__title, R.string.settings__privacy_posture__source_code_title, destination = SettingsSearchDestination.PRIVACY_POSTURE),
        entry("privacy.verification", R.string.settings__privacy_posture__title, R.string.settings__privacy_posture__verification_title, destination = SettingsSearchDestination.PRIVACY_POSTURE),
        entry("privacy.copy-proof", R.string.settings__privacy_posture__title, R.string.settings__privacy_posture__copy_proof_title, destination = SettingsSearchDestination.PRIVACY_POSTURE),
        entry("privacy.simple-mode", R.string.settings__privacy_posture__title, R.string.settings__privacy_posture__simple_mode_title, destination = SettingsSearchDestination.PRIVACY_POSTURE),
        entry("privacy.power-saving", R.string.settings__privacy_posture__title, R.string.settings__privacy_posture__power_saving_title, destination = SettingsSearchDestination.PRIVACY_POSTURE),
        entry("privacy.focus-mode", R.string.settings__privacy_posture__title, R.string.settings__privacy_posture__focus_mode_title, destination = SettingsSearchDestination.PRIVACY_POSTURE),
        entry("privacy.restore-full", R.string.settings__privacy_posture__title, R.string.settings__privacy_posture__restore_full_title, destination = SettingsSearchDestination.PRIVACY_POSTURE),

        // keyboard/InputFeedbackScreen.kt
        entry("input-feedback.audio-enabled", R.string.settings__input_feedback__title, R.string.pref__input_feedback__audio_enabled__label, destination = SettingsSearchDestination.INPUT_FEEDBACK),
        entry("input-feedback.audio-volume", R.string.settings__input_feedback__title, R.string.pref__input_feedback__audio_volume__label, destination = SettingsSearchDestination.INPUT_FEEDBACK),
        entry("input-feedback.audio-feat-key-press", R.string.settings__input_feedback__title, R.string.pref__input_feedback__audio_feat_key_press__label, destination = SettingsSearchDestination.INPUT_FEEDBACK),
        entry("input-feedback.audio-feat-key-long-press", R.string.settings__input_feedback__title, R.string.pref__input_feedback__audio_feat_key_long_press__label, destination = SettingsSearchDestination.INPUT_FEEDBACK),
        entry("input-feedback.audio-feat-key-repeated-action", R.string.settings__input_feedback__title, R.string.pref__input_feedback__audio_feat_key_repeated_action__label, destination = SettingsSearchDestination.INPUT_FEEDBACK),
        entry("input-feedback.audio-feat-gesture-swipe", R.string.settings__input_feedback__title, R.string.pref__input_feedback__audio_feat_gesture_swipe__label, destination = SettingsSearchDestination.INPUT_FEEDBACK),
        entry("input-feedback.audio-feat-gesture-moving-swipe", R.string.settings__input_feedback__title, R.string.pref__input_feedback__audio_feat_gesture_moving_swipe__label, destination = SettingsSearchDestination.INPUT_FEEDBACK),
        entry("input-feedback.haptic-enabled", R.string.settings__input_feedback__title, R.string.pref__input_feedback__haptic_enabled__label, destination = SettingsSearchDestination.INPUT_FEEDBACK),
        entry("input-feedback.haptic-vibration-mode", R.string.settings__input_feedback__title, R.string.pref__input_feedback__haptic_vibration_mode__label, destination = SettingsSearchDestination.INPUT_FEEDBACK),
        entry("input-feedback.haptic-vibration-duration", R.string.settings__input_feedback__title, R.string.pref__input_feedback__haptic_vibration_duration__label, destination = SettingsSearchDestination.INPUT_FEEDBACK),
        entry("input-feedback.haptic-vibration-strength", R.string.settings__input_feedback__title, R.string.pref__input_feedback__haptic_vibration_strength__label, destination = SettingsSearchDestination.INPUT_FEEDBACK),
        entry("input-feedback.haptic-feat-key-press", R.string.settings__input_feedback__title, R.string.pref__input_feedback__haptic_feat_key_press__label, destination = SettingsSearchDestination.INPUT_FEEDBACK),
        entry("input-feedback.haptic-feat-key-long-press", R.string.settings__input_feedback__title, R.string.pref__input_feedback__haptic_feat_key_long_press__label, destination = SettingsSearchDestination.INPUT_FEEDBACK),
        entry("input-feedback.haptic-feat-key-repeated-action", R.string.settings__input_feedback__title, R.string.pref__input_feedback__haptic_feat_key_repeated_action__label, destination = SettingsSearchDestination.INPUT_FEEDBACK),
        entry("input-feedback.haptic-feat-gesture-swipe", R.string.settings__input_feedback__title, R.string.pref__input_feedback__haptic_feat_gesture_swipe__label, destination = SettingsSearchDestination.INPUT_FEEDBACK),
        entry("input-feedback.haptic-feat-gesture-moving-swipe", R.string.settings__input_feedback__title, R.string.pref__input_feedback__haptic_feat_gesture_moving_swipe__label, destination = SettingsSearchDestination.INPUT_FEEDBACK),

        // typing/TypingStatsScreen.kt
        entry("typing-stats.words-learned", R.string.settings__typing_stats__title, R.string.settings__typing_stats__words_learned, destination = SettingsSearchDestination.TYPING_STATS),
        entry("typing-stats.phrase-pairs", R.string.settings__typing_stats__title, R.string.settings__typing_stats__phrase_pairs, destination = SettingsSearchDestination.TYPING_STATS),
        entry("typing-stats.phrase-triples", R.string.settings__typing_stats__title, R.string.settings__typing_stats__phrase_triples, destination = SettingsSearchDestination.TYPING_STATS),
        entry("typing-stats.correction-decisions", R.string.settings__typing_stats__title, R.string.settings__typing_stats__correction_decisions, destination = SettingsSearchDestination.TYPING_STATS),
        entry("typing-stats.accuracy-delta", R.string.settings__typing_stats__title, R.string.settings__typing_stats__accuracy_delta, destination = SettingsSearchDestination.TYPING_STATS),
        entry("typing-stats.adaptive-touch-samples", R.string.settings__typing_stats__title, R.string.settings__typing_stats__adaptive_touch_samples, destination = SettingsSearchDestination.TYPING_STATS),
        entry("typing-stats.refresh", R.string.settings__typing_stats__title, R.string.settings__typing_stats__refresh, destination = SettingsSearchDestination.TYPING_STATS),
        entry("typing-stats.reset-phrase-history", R.string.settings__typing_stats__title, R.string.settings__typing_stats__reset_phrase_history, destination = SettingsSearchDestination.TYPING_STATS),
        entry("typing-stats.reset-correction-memory", R.string.settings__typing_stats__title, R.string.settings__typing_stats__reset_correction_memory, destination = SettingsSearchDestination.TYPING_STATS),
        entry("typing-stats.reset-adaptive-touch", R.string.settings__typing_stats__title, R.string.settings__typing_stats__reset_adaptive_touch, destination = SettingsSearchDestination.TYPING_STATS),
        entry("typing-stats.reset-all-learning", R.string.settings__typing_stats__title, R.string.settings__typing_stats__reset_all_learning, destination = SettingsSearchDestination.TYPING_STATS),
        entry("typing-stats.erase-everything", R.string.settings__typing_stats__title, R.string.settings__typing_stats__erase_everything, destination = SettingsSearchDestination.TYPING_STATS),
        entry("typing-stats.trace-capture", R.string.settings__typing_stats__title, R.string.settings__typing_stats__trace_capture, destination = SettingsSearchDestination.TYPING_STATS),
        entry("typing-stats.trace-share", R.string.settings__typing_stats__title, R.string.settings__typing_stats__trace_share, destination = SettingsSearchDestination.TYPING_STATS),
        entry("typing-stats.trace-fixture-share", R.string.settings__typing_stats__title, R.string.settings__typing_stats__trace_fixture_share, destination = SettingsSearchDestination.TYPING_STATS),
        entry("typing-stats.trace-clear", R.string.settings__typing_stats__title, R.string.settings__typing_stats__trace_clear, destination = SettingsSearchDestination.TYPING_STATS),

        // media/MediaScreen.kt
        entry("media.media-emoji-preferred-skin-tone", R.string.settings__media__title, R.string.prefs__media__emoji_preferred_skin_tone, destination = SettingsSearchDestination.MEDIA),
        entry("media.media-emoji-history-pinned-update-strategy", R.string.settings__media__title, R.string.prefs__media__emoji_history_pinned_update_strategy, destination = SettingsSearchDestination.MEDIA),
        entry("media.media-emoji-history-recent-update-strategy", R.string.settings__media__title, R.string.prefs__media__emoji_history_recent_update_strategy, destination = SettingsSearchDestination.MEDIA),
        entry("media.media-emoji-history-max-size", R.string.settings__media__title, R.string.prefs__media__emoji_history_max_size, destination = SettingsSearchDestination.MEDIA),
        entry("media.media-emoji-history-pinned-reset", R.string.settings__media__title, R.string.prefs__media__emoji_history_pinned_reset, destination = SettingsSearchDestination.MEDIA),
        entry("media.media-emoji-history-reset", R.string.settings__media__title, R.string.prefs__media__emoji_history_reset, destination = SettingsSearchDestination.MEDIA),
        entry("media.media-emoji-suggestion-type", R.string.settings__media__title, R.string.prefs__media__emoji_suggestion_type, destination = SettingsSearchDestination.MEDIA),
        entry("media.media-emoji-suggestion-update-history", R.string.settings__media__title, R.string.prefs__media__emoji_suggestion_update_history, destination = SettingsSearchDestination.MEDIA),
        entry("media.media-emoji-suggestion-candidate-show-name", R.string.settings__media__title, R.string.prefs__media__emoji_suggestion_candidate_show_name, destination = SettingsSearchDestination.MEDIA),
        entry("media.media-emoji-suggestion-query-min-length", R.string.settings__media__title, R.string.prefs__media__emoji_suggestion_query_min_length, destination = SettingsSearchDestination.MEDIA),
        entry("media.media-emoji-suggestion-candidate-max-count", R.string.settings__media__title, R.string.prefs__media__emoji_suggestion_candidate_max_count, destination = SettingsSearchDestination.MEDIA),
        entry("media.media-emoji-tags-manage", R.string.settings__media__title, R.string.prefs__media__emoji_tags__manage, destination = SettingsSearchDestination.MEDIA),
        entry("media.media-stickers-pack-clear", R.string.settings__media__title, R.string.prefs__media__stickers_pack_clear, destination = SettingsSearchDestination.MEDIA),
        entry("media.media-stickers-folder-clear", R.string.settings__media__title, R.string.prefs__media__stickers_folder_clear, destination = SettingsSearchDestination.MEDIA),

        // clipboard/ClipboardScreen.kt
        entry("clipboard.sync-from-system-clipboard", R.string.settings__clipboard__title, R.string.pref__clipboard__sync_from_system_clipboard__label, destination = SettingsSearchDestination.CLIPBOARD),
        entry("clipboard.sync-to-system-clipboard", R.string.settings__clipboard__title, R.string.pref__clipboard__sync_to_system_clipboard__label, destination = SettingsSearchDestination.CLIPBOARD),
        entry("clipboard.suggestion-timeout", R.string.settings__clipboard__title, R.string.pref__clipboard__suggestion_timeout__label, destination = SettingsSearchDestination.CLIPBOARD),
        entry("clipboard.num-history-grid-columns", R.string.settings__clipboard__title, R.string.pref__clipboard__num_history_grid_columns__label, destination = SettingsSearchDestination.CLIPBOARD),
        entry("clipboard.clean-up-after", R.string.settings__clipboard__title, R.string.pref__clipboard__clean_up_after__label, destination = SettingsSearchDestination.CLIPBOARD),
        entry("clipboard.auto-clean-sensitive-after", R.string.settings__clipboard__title, R.string.pref__clipboard__auto_clean_sensitive_after__label, destination = SettingsSearchDestination.CLIPBOARD),
        entry("clipboard.limit-history-size", R.string.settings__clipboard__title, R.string.pref__clipboard__limit_history_size__label, destination = SettingsSearchDestination.CLIPBOARD),
        entry("clipboard.max-history-size", R.string.settings__clipboard__title, R.string.pref__clipboard__max_history_size__label, destination = SettingsSearchDestination.CLIPBOARD),
        entry("clipboard.history-hide-on-paste", R.string.settings__clipboard__title, R.string.pref__clipboard__history_hide_on_paste__label, destination = SettingsSearchDestination.CLIPBOARD),
        entry("clipboard.history-hide-on-next-text-field", R.string.settings__clipboard__title, R.string.pref__clipboard__history_hide_on_next_text_field__label, destination = SettingsSearchDestination.CLIPBOARD),
        entry("clipboard.clear-primary-clip-affects-history-if-unpinned", R.string.settings__clipboard__title, R.string.pref__clipboard__clear_primary_clip_affects_history_if_unpinned__label, destination = SettingsSearchDestination.CLIPBOARD),

        // keyboard/KeyboardScreen.kt
        entry("keyboard.utility-key-enabled", R.string.settings__keyboard__title, R.string.pref__keyboard__utility_key_enabled__label, destination = SettingsSearchDestination.KEYBOARD),
        entry("keyboard.utility-key-action", R.string.settings__keyboard__title, R.string.pref__keyboard__utility_key_action__label, destination = SettingsSearchDestination.KEYBOARD),
        entry("keyboard.floating-onboarding-reset", R.string.settings__keyboard__title, R.string.pref__keyboard__floating_onboarding_reset__label, destination = SettingsSearchDestination.KEYBOARD),
        entry("keyboard.stylus-handwriting", R.string.settings__keyboard__title, R.string.pref__keyboard__stylus_handwriting__label, destination = SettingsSearchDestination.KEYBOARD),
        entry("keyboard.merge-hint-popups-enabled", R.string.settings__keyboard__title, R.string.pref__keyboard__merge_hint_popups_enabled__label, destination = SettingsSearchDestination.KEYBOARD),
        entry("keyboard.space-bar-switches-to-characters", R.string.settings__keyboard__title, R.string.pref__keyboard__space_bar_switches_to_characters__label, destination = SettingsSearchDestination.KEYBOARD),
        entry("keyboard.auto-return-after-apostrophe", R.string.settings__keyboard__title, R.string.pref__keyboard__auto_return_after_apostrophe__label, destination = SettingsSearchDestination.KEYBOARD),
        entry("keyboard.quote-auto-close-enabled", R.string.settings__keyboard__title, R.string.pref__keyboard__quote_auto_close_enabled__label, destination = SettingsSearchDestination.KEYBOARD),

        // about/AboutScreen.kt
        entry("about.title", R.string.about__title, R.string.about__version__title, destination = SettingsSearchDestination.ABOUT),
        entry("about.title-2", R.string.about__title, R.string.about__signing_fingerprint__title, destination = SettingsSearchDestination.ABOUT),
        entry("about.title-3", R.string.about__title, R.string.about__whats_new__title, destination = SettingsSearchDestination.ABOUT),
        entry("about.title-4", R.string.about__title, R.string.about__release_history__title, destination = SettingsSearchDestination.ABOUT),
        entry("about.title-5", R.string.about__title, R.string.about__changelog__title, destination = SettingsSearchDestination.ABOUT),
        entry("about.title-6", R.string.about__title, R.string.about__repository__title, destination = SettingsSearchDestination.ABOUT),
        entry("about.title-7", R.string.about__title, R.string.about__privacy_policy__title, destination = SettingsSearchDestination.ABOUT),

        // sync/SyncSettingsScreen.kt
        entry("sync.legacy-export-encrypted", R.string.settings__sync__title, R.string.settings__sync__legacy_export_encrypted, destination = SettingsSearchDestination.SYNC),
        entry("sync.legacy-import-encrypted", R.string.settings__sync__title, R.string.settings__sync__legacy_import_encrypted, destination = SettingsSearchDestination.SYNC),
        entry("sync.export-now", R.string.settings__sync__title, R.string.settings__sync__export_now, destination = SettingsSearchDestination.SYNC),
        entry("sync.import-now", R.string.settings__sync__title, R.string.settings__sync__import_now, destination = SettingsSearchDestination.SYNC),
        entry("sync.choose-manual-export-target", R.string.settings__sync__title, R.string.settings__sync__choose_manual_export_target, destination = SettingsSearchDestination.SYNC),
        entry("sync.pair-new-device", R.string.settings__sync__title, R.string.settings__sync__pair_new_device, destination = SettingsSearchDestination.SYNC),
        entry("sync.receive-pairing", R.string.settings__sync__title, R.string.settings__sync__receive_pairing, destination = SettingsSearchDestination.SYNC),

        // typing/TypingScreen.kt
        entry("typing.api30-inline-suggestions-enabled", R.string.settings__typing__title, R.string.pref__suggestion__api30_inline_suggestions_enabled__label, destination = SettingsSearchDestination.TYPING),
        entry("typing.auto-correct-commit-mode", R.string.settings__typing__title, R.string.pref__correction__auto_correct_commit_mode__label, destination = SettingsSearchDestination.TYPING),
        entry("typing.remember-caps-lock-state", R.string.settings__typing__title, R.string.pref__correction__remember_caps_lock_state__label, destination = SettingsSearchDestination.TYPING),
        entry("typing.touch-calibration-profile", R.string.settings__typing__title, R.string.pref__correction__touch_calibration_profile__label, destination = SettingsSearchDestination.TYPING),
        entry("typing.language-mode", R.string.settings__typing__title, R.string.pref__spelling__language_mode__label, destination = SettingsSearchDestination.TYPING),
        entry("typing.use-contacts", R.string.settings__typing__title, R.string.pref__spelling__use_contacts__label, destination = SettingsSearchDestination.TYPING),
        entry("typing.use-udm-entries", R.string.settings__typing__title, R.string.pref__spelling__use_udm_entries__label, destination = SettingsSearchDestination.TYPING),

        // about/AiFeaturesScreen.kt
        entry("ai-features.local-processing", R.string.about__ai_features__title, R.string.about__ai_features__local_processing_title, destination = SettingsSearchDestination.AI_FEATURES),
        entry("ai-features.no-accounts", R.string.about__ai_features__title, R.string.about__ai_features__no_accounts_title, destination = SettingsSearchDestination.AI_FEATURES),
        entry("ai-features.docs-group", R.string.about__ai_features__title, R.string.about__ai_features__docs_group, destination = SettingsSearchDestination.AI_FEATURES),
        entry("ai-features.privacy-doc", R.string.about__ai_features__title, R.string.about__ai_features__privacy_doc_title, destination = SettingsSearchDestination.AI_FEATURES),
        entry("ai-features.threat-model", R.string.about__ai_features__title, R.string.about__ai_features__threat_model_title, destination = SettingsSearchDestination.AI_FEATURES),
        entry("ai-features.project-context", R.string.about__ai_features__title, R.string.about__ai_features__project_context_title, destination = SettingsSearchDestination.AI_FEATURES),

        // dictionary/MigrationAssistantScreen.kt
        entry("migration.swiftkey", R.string.settings__migration_assistant__title, R.string.settings__migration_assistant__swiftkey_title, destination = SettingsSearchDestination.MIGRATION_ASSISTANT),
        entry("migration.gboard", R.string.settings__migration_assistant__title, R.string.settings__migration_assistant__gboard_title, destination = SettingsSearchDestination.MIGRATION_ASSISTANT),
        entry("migration.florisboard", R.string.settings__migration_assistant__title, R.string.settings__migration_assistant__florisboard_title, destination = SettingsSearchDestination.MIGRATION_ASSISTANT),
        entry("migration.encrypted", R.string.settings__migration_assistant__title, R.string.settings__migration_assistant__encrypted_title, destination = SettingsSearchDestination.MIGRATION_ASSISTANT),
        entry("migration.manual", R.string.settings__migration_assistant__title, R.string.settings__migration_assistant__manual_title, destination = SettingsSearchDestination.MIGRATION_ASSISTANT),
        entry("migration.guide", R.string.settings__migration_assistant__title, R.string.settings__migration_assistant__guide_title, destination = SettingsSearchDestination.MIGRATION_ASSISTANT),

        // addons/AddonsSettingsScreen.kt
        entry("addons.reset-trust", R.string.settings__addons__title, R.string.settings__addons__reset_trust, destination = SettingsSearchDestination.ADDONS),
        entry("addons.dictionary-pack-rejected", R.string.settings__addons__title, R.string.settings__addons__dictionary_pack_rejected, destination = SettingsSearchDestination.ADDONS),
        entry("addons.trust-new-certificate", R.string.settings__addons__title, R.string.settings__addons__trust_new_certificate, destination = SettingsSearchDestination.ADDONS),
        entry("addons.trust-changed-certificate", R.string.settings__addons__title, R.string.settings__addons__trust_changed_certificate, destination = SettingsSearchDestination.ADDONS),
        entry("addons.install", R.string.settings__addons__title, R.string.settings__addons__install_title, destination = SettingsSearchDestination.ADDONS),

        // mcp/McpSettingsScreen.kt
        entry("mcp.bridge-enabled", R.string.settings__mcp__title, R.string.settings__mcp__bridge_enabled, destination = SettingsSearchDestination.MCP),
        entry("mcp.reset-trust", R.string.settings__mcp__title, R.string.settings__mcp__reset_trust, destination = SettingsSearchDestination.MCP),
        entry("mcp.rescan-failed", R.string.settings__mcp__title, R.string.settings__mcp__rescan_failed, destination = SettingsSearchDestination.MCP),
        entry("mcp.daemon-retry", R.string.settings__mcp__title, R.string.settings__mcp__daemon_retry, destination = SettingsSearchDestination.MCP),

        // privacy/PrivacyAuditScreen.kt
        entry("privacy-audit.export", R.string.settings__privacy_audit__title, R.string.settings__privacy_audit__export, destination = SettingsSearchDestination.PRIVACY_AUDIT),
        entry("privacy-audit.save", R.string.settings__privacy_audit__title, R.string.settings__privacy_audit__save, destination = SettingsSearchDestination.PRIVACY_AUDIT),
        entry("privacy-audit.share", R.string.settings__privacy_audit__title, R.string.settings__privacy_audit__share, destination = SettingsSearchDestination.PRIVACY_AUDIT),
        entry("privacy-audit.clear", R.string.settings__privacy_audit__title, R.string.settings__privacy_audit__clear, destination = SettingsSearchDestination.PRIVACY_AUDIT),

        // voice/VoiceInputScreen.kt
        entry("voice.open-keyboard-settings", R.string.settings__voice_input__title, R.string.voice_input_setup__open_keyboard_settings, destination = SettingsSearchDestination.VOICE_INPUT),
        entry("voice.open-futo-permissions", R.string.settings__voice_input__title, R.string.settings__voice_input__open_futo_permissions, destination = SettingsSearchDestination.VOICE_INPUT),
        entry("voice.install-fdroid", R.string.settings__voice_input__title, R.string.voice_input_setup__install_fdroid, destination = SettingsSearchDestination.VOICE_INPUT),
        entry("voice.custom-command-add", R.string.settings__voice_input__title, R.string.settings__voice_input__custom_command_add, destination = SettingsSearchDestination.VOICE_INPUT),

        // dictionary/DictionaryScreen.kt
        entry("dictionary.enable-system-user-dictionary", R.string.settings__dictionary__title, R.string.pref__dictionary__enable_system_user_dictionary__label, destination = SettingsSearchDestination.DICTIONARY),
        entry("dictionary.enable-internal-user-dictionary", R.string.settings__dictionary__title, R.string.pref__dictionary__enable_internal_user_dictionary__label, destination = SettingsSearchDestination.DICTIONARY),
        entry("dictionary.preview-imports", R.string.settings__dictionary__title, R.string.pref__dictionary__preview_imports__label, destination = SettingsSearchDestination.DICTIONARY),

        // localization/PerAppLanguageScreen.kt
        entry("per-app-language.enabled", R.string.settings__per_app_language__title, R.string.settings__per_app_language__enabled, destination = SettingsSearchDestination.PER_APP_LANGUAGE),
        entry("per-app-language.clear", R.string.settings__per_app_language__title, R.string.settings__per_app_language__clear, destination = SettingsSearchDestination.PER_APP_LANGUAGE),
        entry("per-app-language.privacy", R.string.settings__per_app_language__title, R.string.settings__per_app_language__privacy_title, destination = SettingsSearchDestination.PER_APP_LANGUAGE),

        // smartbar/SmartbarScreen.kt
        entry("smartbar.per-app-profiles", R.string.settings__smartbar__title, R.string.pref__smartbar__per_app_profiles__label, destination = SettingsSearchDestination.SMARTBAR),
        entry("smartbar.flip-toggles", R.string.settings__smartbar__title, R.string.pref__smartbar__flip_toggles__label, destination = SettingsSearchDestination.SMARTBAR),
        entry("smartbar.extended-actions-placement", R.string.settings__smartbar__title, R.string.pref__smartbar__extended_actions_placement__label, destination = SettingsSearchDestination.SMARTBAR),

        // advanced/PhysicalKeyboardScreen.kt
        entry("physical-keyboard.system-settings", R.string.physical_keyboard__title, R.string.physical_keyboard__system_settings__title, destination = SettingsSearchDestination.PHYSICAL_KEYBOARD),
        entry("physical-keyboard.show-on-screen-keyboard", R.string.physical_keyboard__title, R.string.physical_keyboard__show_on_screen_keyboard__title, destination = SettingsSearchDestination.PHYSICAL_KEYBOARD),

        // privacy/PerAppKeyboardProfileScreen.kt
        entry("per-app-profiles.add-current-app", R.string.settings__per_app_keyboard_profiles__title, R.string.settings__per_app_keyboard_profiles__add_current_app, destination = SettingsSearchDestination.PER_APP_KEYBOARD_PROFILES),
        entry("per-app-profiles.add-package", R.string.settings__per_app_keyboard_profiles__title, R.string.settings__per_app_keyboard_profiles__add_package, destination = SettingsSearchDestination.PER_APP_KEYBOARD_PROFILES),

        // typing/SnippetSettingsScreen.kt
        entry("snippets.import-yaml", R.string.settings__snippet__title, R.string.settings__snippet__import_yaml, destination = SettingsSearchDestination.SNIPPET_SETTINGS),
        entry("snippets.clear-all", R.string.settings__snippet__title, R.string.settings__snippet__clear_all, destination = SettingsSearchDestination.SNIPPET_SETTINGS),

        // localization/LanguagePackManagerScreen.kt
        entry("language-packs.import", R.string.settings__localization__language_pack_title, R.string.action__import, destination = SettingsSearchDestination.LANGUAGE_PACK_MANAGER, keywords = "language pack"),

        // localization/LocalizationScreen.kt
        entry("localization.cjk-mixed-script-spacing", R.string.settings__localization__title, R.string.settings__localization__cjk_mixed_script_spacing__label, destination = SettingsSearchDestination.LOCALIZATION),
    )

    fun search(query: String, resolveString: (Int) -> String): List<SettingsSearchResult> {
        val terms = query.normalizeTerms()
        if (terms.isEmpty()) return emptyList()
        return entries.mapNotNull { entry ->
            val title = resolveString(entry.titleResId)
            val summary = entry.summaryResId?.let(resolveString).orEmpty()
            val screenTitle = resolveString(entry.screenTitleResId)
            val keywords = entry.keywords.joinToString(" ")
            val haystacks = listOf(title, summary, screenTitle, keywords).map { it.searchNormalize() }
            if (terms.all { term -> haystacks.any { it.contains(term) } }) {
                SettingsSearchResult(entry, entry.score(terms, title, summary, screenTitle, keywords))
            } else {
                null
            }
        }.sortedWith(compareByDescending<SettingsSearchResult> { it.score }.thenBy { resolveString(it.entry.titleResId) })
    }

    private fun entry(
        id: String,
        screenTitleResId: Int,
        titleResId: Int,
        summaryResId: Int? = null,
        destination: SettingsSearchDestination,
        keywords: String = "",
    ): SettingsSearchEntry {
        return SettingsSearchEntry(
            id = id,
            screenTitleResId = screenTitleResId,
            titleResId = titleResId,
            summaryResId = summaryResId,
            destination = destination,
            keywords = keywords.split(' ').filter { it.isNotBlank() },
        )
    }

    private fun SettingsSearchEntry.score(
        terms: List<String>,
        title: String,
        summary: String,
        screenTitle: String,
        keywords: String,
    ): Int {
        val titleText = title.searchNormalize()
        val summaryText = summary.searchNormalize()
        val screenText = screenTitle.searchNormalize()
        val keywordText = keywords.searchNormalize()
        return terms.sumOf { term ->
            when {
                titleText == term -> 120
                titleText.startsWith(term) -> 90
                titleText.contains(term) -> 70
                screenText.contains(term) -> 45
                keywordText.contains(term) -> 35
                summaryText.contains(term) -> 20
                else -> 0
            }
        }
    }
}

private fun String.normalizeTerms(): List<String> {
    return searchNormalize().split(' ').filter { it.isNotBlank() }
}

private val CombiningMarkRegex = Regex("\\p{Mn}+")
private val WhitespaceRegex = Regex("\\s+")

private fun String.searchNormalize(): String {
    return Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(CombiningMarkRegex, "")
        .lowercase()
        .replace('&', ' ')
        .replace('/', ' ')
        .replace('-', ' ')
        .replace('_', ' ')
        .replace(WhitespaceRegex, " ")
        .trim()
}
