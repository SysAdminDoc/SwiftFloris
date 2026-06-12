/*
 * Copyright (C) 2026 The FlorisBoard Contributors
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

import dev.patrickgold.jetpref.datastore.model.PreferenceData
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class AppPrefsPartitionTest : FunSpec({
    test("partition keeps app preference keys, types, and defaults stable") {
        val rows = FlorisPreferenceModelImpl().toGoldenRows()

        rows shouldHaveSize 196
        rows shouldBe EXPECTED_APP_PREFS_GOLDEN.trimIndent().lines()
    }

    test("partition keeps feature groups re-exported from the root model") {
        val prefs = FlorisPreferenceModelImpl()

        prefs.clipboard.useInternalClipboard.key shouldBe "clipboard__use_internal_clipboard"
        prefs.keyboard.windowConfig.key shouldBe "keyboard__window_config"
        prefs.smartbar.actionArrangement.key shouldBe "smartbar__action_arrangement"
        prefs.theme.editorLevel.key shouldBe "theme__editor_level"
    }
})

private fun FlorisPreferenceModelImpl.toGoldenRows(): List<String> {
    return declaredPreferenceEntries.values
        .sortedBy { it.key }
        .map { data ->
            "${data.key}\t${data.type.id}\t${data.encodedDefaultForGolden().ifEmpty { "<empty>" }}"
        }
}

@Suppress("UNCHECKED_CAST")
private fun PreferenceData<*>.encodedDefaultForGolden(): String {
    val typedData = this as PreferenceData<Any>
    return typedData.serializer.serialize(typedData.default).orEmpty()
}

private val EXPECTED_APP_PREFS_GOLDEN = """
addon__signing_cert_pins	s	<empty>
clipboard__clear_primary_clip_affects_history_if_unpinned	b	true
clipboard__history_auto_clean_old_after	i	20
clipboard__history_auto_clean_old_enabled	b	false
clipboard__history_auto_clean_sensitive_after	i	20
clipboard__history_auto_clean_sensitive_enabled	b	false
clipboard__history_enabled	b	false
clipboard__history_hide_on_next_text_field	b	true
clipboard__history_hide_on_paste	b	false
clipboard__history_num_grid_columns_landscape	i	0
clipboard__history_num_grid_columns_portrait	i	0
clipboard__history_search_enabled	b	true
clipboard__history_size_limit	i	20
clipboard__history_size_limit_enabled	b	true
clipboard__suggestion_enabled	b	true
clipboard__suggestion_timeout	i	60
clipboard__sync_to_floris	s	ALL_EVENTS
clipboard__sync_to_system	s	NO_EVENTS
clipboard__use_internal_clipboard	b	false
correction__adaptive_touch_model	b	true
correction__auto_capitalization	b	true
correction__auto_correct	b	true
correction__auto_correct_commit_mode	s	NORMAL
correction__auto_space_punctuation	b	true
correction__double_space_period	b	true
correction__heuristic_smart_compose	b	false
correction__multilingual_suggestions	b	true
correction__quick_prediction_insert	b	false
correction__remember_caps_lock_state	b	false
devtools__enabled	b	false
devtools__show_drag_and_drop_helpers	b	false
devtools__show_inline_autofill_overlay	b	false
devtools__show_input_state_overlay	b	false
devtools__show_primary_clip	b	false
devtools__show_spelling_overlay	b	false
devtools__show_touch_boundaries	b	false
devtools__show_window_resize_handle_boundaries	b	false
dictionary__preview_personal_imports	b	true
emoji__history_data	s	{"pinned":[],"recent":[]}
emoji__history_enabled	b	true
emoji__history_pinned_max_size	i	0
emoji__history_pinned_update_strategy	s	MANUAL_SORT_PREPEND
emoji__history_recent_max_size	i	90
emoji__history_recent_update_strategy	s	AUTO_SORT_PREPEND
emoji__preferred_hair_style	s	DEFAULT
emoji__preferred_skin_tone	s	DEFAULT
emoji__suggestion_candidate_max_count	i	5
emoji__suggestion_candidate_show_name	b	false
emoji__suggestion_enabled	b	true
emoji__suggestion_query_min_length	i	3
emoji__suggestion_type	s	LEADING_COLON
emoji__suggestion_update_history	b	true
gestures__delete_key_long_press	s	DELETE_WORD
gestures__delete_key_swipe_left	s	DELETE_WORD
gestures__space_bar_long_press	s	SHOW_INPUT_METHOD_PICKER
gestures__space_bar_swipe_down	s	NO_ACTION
gestures__space_bar_swipe_left	s	MOVE_CURSOR_LEFT
gestures__space_bar_swipe_right	s	MOVE_CURSOR_RIGHT
gestures__space_bar_swipe_up	s	NO_ACTION
gestures__swipe_distance_threshold	i	32
gestures__swipe_down	s	HIDE_KEYBOARD
gestures__swipe_left	s	SWITCH_TO_NEXT_SUBTYPE
gestures__swipe_right	s	SWITCH_TO_PREV_SUBTYPE
gestures__swipe_up	s	SHIFT
gestures__swipe_velocity_threshold	i	1900
gestures__symbol_flick_enabled	b	false
glide__enabled	b	true
glide__flow_through_space	b	true
glide__immediate_backspace_deletes_word	b	true
glide__language_enabled_de	b	true
glide__language_enabled_en	b	true
glide__language_enabled_es	b	true
glide__language_enabled_fr	b	true
glide__language_enabled_it	b	true
glide__language_enabled_pt	b	true
glide__preview_refresh_delay	i	150
glide__sensitivity	i	50
glide__show_preview	b	true
glide__show_trail	b	true
glide__trail_fade_duration	i	500
glide__trail_theme	s	ACCENT
input_feedback__audio_activation_mode	s	RESPECT_SYSTEM_SETTINGS
input_feedback__audio_enabled	b	true
input_feedback__audio_feat_gesture_moving_swipe	b	false
input_feedback__audio_feat_gesture_swipe	b	false
input_feedback__audio_feat_key_long_press	b	false
input_feedback__audio_feat_key_press	b	true
input_feedback__audio_feat_key_repeated_action	b	false
input_feedback__audio_volume	i	50
input_feedback__haptic_activation_mode	s	RESPECT_SYSTEM_SETTINGS
input_feedback__haptic_enabled	b	true
input_feedback__haptic_feat_gesture_moving_swipe	b	true
input_feedback__haptic_feat_gesture_swipe	b	false
input_feedback__haptic_feat_key_long_press	b	false
input_feedback__haptic_feat_key_press	b	true
input_feedback__haptic_feat_key_repeated_action	b	true
input_feedback__haptic_vibration_duration	i	20
input_feedback__haptic_vibration_mode	s	USE_VIBRATOR_DIRECTLY
input_feedback__haptic_vibration_strength	i	60
internal__ai_features_explainer_seen	b	false
internal__first_run_import_hint_seen	b	false
internal__home_is_beta_toolbox_collapsed_040a01	b	false
internal__is_ime_set_up	b	false
internal__notification_permission_state	s	NOT_SET
internal__version_last_changelog	s	0.0.0
internal__version_last_use	s	0.0.0
internal__version_on_install	s	0.0.0
keyboard__auto_return_after_apostrophe	b	true
keyboard__bottom_row_preset_json	s	automatic
keyboard__capitalization_behavior	s	CAPSLOCK_BY_DOUBLE_TAP
keyboard__floating_onboarding_shown	b	false
keyboard__font_size_multiplier_landscape	i	100
keyboard__font_size_multiplier_portrait	i	100
keyboard__hinted_number_row_enabled	b	false
keyboard__hinted_number_row_mode	s	SMART_PRIORITY
keyboard__hinted_symbols_enabled	b	false
keyboard__hinted_symbols_mode	s	SMART_PRIORITY
keyboard__incognito_indicator	s	DISPLAY_BEHIND_KEYBOARD
keyboard__key_spacing_horizontal	i	100
keyboard__key_spacing_vertical	i	100
keyboard__keyboard_height_multiplier_landscape	i	100
keyboard__keyboard_height_multiplier_portrait	i	100
keyboard__landscape_input_ui_mode	s	DYNAMICALLY_SHOW
keyboard__long_press_delay	i	300
keyboard__merge_hint_popups_enabled	b	false
keyboard__number_row	b	true
keyboard__popup_enabled	b	true
keyboard__quote_auto_close_enabled	b	true
keyboard__space_bar_display_mode	s	NOTHING
keyboard__space_bar_switches_to_characters	b	true
keyboard__split_keyboard_enabled	b	false
keyboard__start_in_floating_mode	b	false
keyboard__stylus_handwriting_enabled	b	false
keyboard__utility_key_action	s	SWITCH_TO_EMOJIS
keyboard__utility_key_enabled	b	true
keyboard__window_config	s	{}
localization__active_subtype_id	l	-1
localization__display_keyboard_labels_in_subtype_language	b	false
localization__display_language_names_in	s	SYSTEM_LOCALE
localization__per_app_subtype_memory	s	{}
localization__remember_subtype_per_app_enabled	b	false
localization__subtypes	s	[]
mcp__disabled_daemon_packages	s	<empty>
mcp__disabled_tools	s	<empty>
mcp__signing_cert_pins	s	<empty>
other__accent_color	s	0000000000000010
other__settings_language	s	auto
other__settings_theme	s	AUTO
other__show_app_icon	b	true
physical_keyboard__show_on_screen_keyboard	b	false
privacy__addon_consent_mcp	s	NEEDS_PROMPT
privacy__addon_consent_smart_compose	s	NEEDS_PROMPT
privacy__addon_consent_translation	s	NEEDS_PROMPT
privacy__per_app_keyboard_profiles	s	{}
smartbar__action_arrangement	s	{"stickyAction":null,"dynamicActions":[{"$":"insert_key","data":{"$":"text_key","type":"system_gui","code":-131,"label":"undo"}},{"$":"insert_key","data":{"$":"text_key","type":"system_gui","code":-132,"label":"redo"}},{"$":"insert_key","data":{"$":"text_key","code":-301,"label":"settings"}},{"$":"insert_key","data":{"$":"text_key","type":"system_gui","code":-109,"label":"toggle_floating_window"}},{"$":"insert_key","data":{"$":"text_key","type":"system_gui","code":-115,"label":"toggle_resize_mode"}},{"$":"insert_key","data":{"$":"text_key","type":"system_gui","code":-213,"label":"ime_ui_mode_clipboard"}},{"$":"insert_key","data":{"$":"text_key","type":"system_gui","code":-212,"label":"ime_ui_mode_media"}},{"$":"insert_key","data":{"$":"text_key","type":"system_gui","code":-110,"label":"toggle_compact_layout"}},{"$":"insert_key","data":{"$":"text_key","type":"function","code":-245,"label":"toggle_autocorrect"}},{"$":"insert_key","data":{"$":"text_key","type":"function","code":-244,"label":"toggle_incognito_mode"}},{"$":"insert_key","data":{"$":"text_key","type":"navigation","code":-23,"label":"arrow_up"}},{"$":"insert_key","data":{"$":"text_key","type":"navigation","code":-24,"label":"arrow_down"}},{"$":"insert_key","data":{"$":"text_key","type":"navigation","code":-21,"label":"arrow_left"}},{"$":"insert_key","data":{"$":"text_key","type":"navigation","code":-22,"label":"arrow_right"}},{"$":"insert_key","data":{"$":"text_key","type":"system_gui","code":-38,"label":"clipboard_clear_primary_clip"}},{"$":"insert_key","data":{"$":"text_key","type":"system_gui","code":-31,"label":"clipboard_copy"}},{"$":"insert_key","data":{"$":"text_key","type":"system_gui","code":-32,"label":"clipboard_cut"}},{"$":"insert_key","data":{"$":"text_key","type":"system_gui","code":-33,"label":"clipboard_paste"}},{"$":"insert_key","data":{"$":"text_key","type":"system_gui","code":-35,"label":"clipboard_select_all"}},{"$":"insert_key","data":{"$":"text_key","type":"system_gui","code":-227,"label":"language_switch"}},{"$":"insert_key","data":{"$":"text_key","type":"enter_editing","code":-9,"label":"forward_delete"}},{"$":"insert_key","data":{"$":"text_key","type":"function","code":-232,"label":"ime_hide_ui"}}],"hiddenActions":[{"$":"insert_task"},{"$":"insert_calendar_event"}]}
smartbar__enabled	b	true
smartbar__extended_actions_expanded	b	false
smartbar__extended_actions_placement	s	ABOVE_CANDIDATES
smartbar__flip_toggles	b	false
smartbar__layout	s	SUGGESTIONS_ACTIONS_SHARED
smartbar__per_app_profiles_enabled	b	true
smartbar__shared_actions_auto_expand_collapse	b	true
smartbar__shared_actions_expand_with_animation	b	true
smartbar__shared_actions_expanded	b	false
spelling__language_mode	s	USE_KEYBOARD_SUBTYPES
spelling__use_contacts	b	true
spelling__use_udm_entries	b	true
sticker__user_folder_uri	s	<empty>
suggestion__api30_inline_suggestions_enabled	b	true
suggestion__block_possibly_offensive	b	false
suggestion__display_mode	s	CLASSIC
suggestion__enable_floris_user_dictionary	b	true
suggestion__enable_system_user_dictionary	b	true
suggestion__enabled	b	true
suggestion__force_incognito_mode_from_dynamic	b	false
suggestion__incognito_mode	s	DYNAMIC_ON_OFF
suggestion__next_word_prediction	b	true
sync__channel_id	s	swiftfloris:disabled
sync__cluster_id	s	<empty>
sync__device_id	s	<empty>
sync__manual_export_target_uri	s	<empty>
sync__paired_devices_json	s	[]
theme__accent_color	s	0000000000000010
theme__day_theme_id	s	org.florisboard.themes:swiftkey_pure_light
theme__editor_color_representation	s	HEX
theme__editor_display_kbd_after_dialogs	s	REMEMBER
theme__editor_level	s	ADVANCED
theme__mode	s	ALWAYS_NIGHT
theme__night_theme_id	s	org.florisboard.themes:swiftkey_pure_dark
theme__per_app_accent_discovery_hint_state	s	COLLECTING
theme__per_app_accent_enabled	b	false
theme__sunrise_time	s	06:00:00.000
theme__sunset_time	s	18:00:00.000
voice__custom_commands	s	{}
voice__embedded_model_preference	s	AUTO
voice__recognition_engine_preference	s	AUTO
"""
