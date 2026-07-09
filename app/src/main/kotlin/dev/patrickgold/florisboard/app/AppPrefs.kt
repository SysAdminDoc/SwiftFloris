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

import dev.patrickgold.florisboard.app.prefs.AddonPrefs
import dev.patrickgold.florisboard.app.prefs.ClipboardPrefs
import dev.patrickgold.florisboard.app.prefs.CorrectionPrefs
import dev.patrickgold.florisboard.app.prefs.DevtoolsPrefs
import dev.patrickgold.florisboard.app.prefs.DictionaryPrefs
import dev.patrickgold.florisboard.app.prefs.EmojiPrefs
import dev.patrickgold.florisboard.app.prefs.GesturesPrefs
import dev.patrickgold.florisboard.app.prefs.GlidePrefs
import dev.patrickgold.florisboard.app.prefs.InputFeedbackPrefs
import dev.patrickgold.florisboard.app.prefs.InternalPrefs
import dev.patrickgold.florisboard.app.prefs.KeyboardPrefs
import dev.patrickgold.florisboard.app.prefs.LocalizationPrefs
import dev.patrickgold.florisboard.app.prefs.McpPrefs
import dev.patrickgold.florisboard.app.prefs.OtherPrefs
import dev.patrickgold.florisboard.app.prefs.PhysicalKeyboardPrefs
import dev.patrickgold.florisboard.app.prefs.PrivacyPrefs
import dev.patrickgold.florisboard.app.prefs.SmartbarPrefs
import dev.patrickgold.florisboard.app.prefs.SpellingPrefs
import dev.patrickgold.florisboard.app.prefs.StickerPrefs
import dev.patrickgold.florisboard.app.prefs.SuggestionPrefs
import dev.patrickgold.florisboard.app.prefs.SyncPrefs
import dev.patrickgold.florisboard.app.prefs.ThemePrefs
import dev.patrickgold.florisboard.app.prefs.VoicePrefs
import dev.patrickgold.florisboard.ime.clipboard.ClipboardSyncBehavior
import dev.patrickgold.florisboard.ime.keyboard.SpaceBarMode
import dev.patrickgold.florisboard.ime.media.emoji.EmojiHistory
import dev.patrickgold.florisboard.ime.smartbar.CandidatesDisplayMode
import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickAction
import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickActionArrangement
import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickActionJsonConfig
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.key.UtilityKeyAction
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.ime.theme.ThemeMode
import dev.patrickgold.jetpref.datastore.jetprefDataStoreOf
import dev.patrickgold.jetpref.datastore.model.PreferenceMigrationEntry
import dev.patrickgold.jetpref.datastore.model.PreferenceModel
import dev.patrickgold.jetpref.datastore.model.PreferenceType
import dev.patrickgold.jetpref.material.ui.ColorRepresentation
import kotlinx.serialization.json.Json

val FlorisPreferenceStore = jetprefDataStoreOf(FlorisPreferenceModel::class)

abstract class FlorisPreferenceModel : PreferenceModel() {
    companion object {
        const val NAME = "florisboard-app-prefs"
    }

    val clipboard = Clipboard()
    inner class Clipboard : ClipboardPrefs()
    val correction = Correction()
    inner class Correction : CorrectionPrefs()
    val devtools = Devtools()
    inner class Devtools : DevtoolsPrefs()
    val dictionary = Dictionary()
    inner class Dictionary : DictionaryPrefs()
    val voice = Voice()
    inner class Voice : VoicePrefs()
    val emoji = Emoji()
    inner class Emoji : EmojiPrefs()
    val gestures = Gestures()
    inner class Gestures : GesturesPrefs()
    val sticker = Sticker()
    inner class Sticker : StickerPrefs()
    val glide = Glide()
    inner class Glide : GlidePrefs()
    val inputFeedback = InputFeedback()
    inner class InputFeedback : InputFeedbackPrefs()
    val internal = Internal()
    inner class Internal : InternalPrefs()
    val sync = Sync()
    inner class Sync : SyncPrefs()
    val mcp = Mcp()
    inner class Mcp : McpPrefs()
    val addon = Addon()
    inner class Addon : AddonPrefs()
    val keyboard = Keyboard()
    inner class Keyboard : KeyboardPrefs()
    val localization = Localization()
    inner class Localization : LocalizationPrefs()
    val privacy = Privacy()
    inner class Privacy : PrivacyPrefs()
    val other = Other()
    inner class Other : OtherPrefs()
    val physicalKeyboard = PhysicalKeyboard()
    inner class PhysicalKeyboard : PhysicalKeyboardPrefs()
    val smartbar = Smartbar()
    inner class Smartbar : SmartbarPrefs()
    val spelling = Spelling()
    inner class Spelling : SpellingPrefs()
    val suggestion = Suggestion()
    inner class Suggestion : SuggestionPrefs()
    val theme = Theme()
    inner class Theme : ThemePrefs()

    override fun migrate(entry: PreferenceMigrationEntry): PreferenceMigrationEntry {
        return when (entry.key) {

            // Retained for legacy backup/import compatibility: media prefs were split into emoji prefs.
            // Covered by AppPrefsMigrationTest.
            "media__emoji_recently_used" -> {
                // Filter blanks: split(";") on an empty/trailing-separator value
                // yields "" entries, which would migrate into bogus empty Emoji("")
                // rows in the recent-emoji history.
                val emojiValues = entry.rawValue.split(";").filter { it.isNotEmpty() }
                val recent = emojiValues.map {
                    dev.patrickgold.florisboard.ime.media.emoji.Emoji(it, "", emptyList())
                }
                val data = EmojiHistory(emptyList(), recent)
                entry.transform(key = "emoji__history_data", rawValue = Json.encodeToString(data))
            }
            "media__emoji_recently_used_max_size" -> {
                entry.transform(key = "emoji__history_recent_max_size")
            }

            // Retained for legacy backup/import compatibility: advanced prefs were partitioned.
            // Covered by AppPrefsMigrationTest.
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
            // Retained for legacy backup/import compatibility: clipboard suggestions moved from suggestion prefs.
            // Covered by AppPrefsMigrationTest.
            "suggestion__clipboard_content_enabled" -> {
                entry.transform(key = "clipboard__suggestion_enabled")
            }
            "suggestion__clipboard_content_timeout" -> {
                entry.transform(key = "clipboard__suggestion_timeout")
            }

            // Retained for legacy backup/import compatibility: obsolete one-handed mode values reset safely.
            // Covered by AppPrefsMigrationTest.
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
                if (QuickAction.InsertTask !in newArrangement) {
                    newArrangement = newArrangement.copy(
                        hiddenActions = newArrangement.hiddenActions.plus(QuickAction.InsertTask)
                    )
                }
                if (QuickAction.InsertCalendarEvent !in newArrangement) {
                    newArrangement = newArrangement.copy(
                        hiddenActions = newArrangement.hiddenActions.plus(QuickAction.InsertCalendarEvent)
                    )
                }
                val json = QuickActionJsonConfig.encodeToString(newArrangement.distinct())
                entry.transform(rawValue = json)
            }

            // Retained for legacy backup/import compatibility: theme editor color mode was renamed.
            // Covered by AppPrefsMigrationTest.
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

            // Retained for legacy backup/import compatibility: clipboard history keys were renamed.
            // Covered by AppPrefsMigrationTest.
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

            // Retained for legacy backup/import compatibility: keyboard/theme defaults changed.
            // Covered by AppPrefsMigrationTest.
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
