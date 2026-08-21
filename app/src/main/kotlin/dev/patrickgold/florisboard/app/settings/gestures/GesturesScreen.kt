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

package dev.patrickgold.florisboard.app.settings.gestures

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.enumDisplayEntriesOf
import dev.patrickgold.florisboard.ime.text.gestures.GlideTypingCapability
import dev.patrickgold.florisboard.ime.text.gestures.GlideTypingEngine
import dev.patrickgold.florisboard.ime.text.gestures.GlideTypingLanguageProfile
import dev.patrickgold.florisboard.ime.text.gestures.GlideTypingLanguageSupport
import dev.patrickgold.florisboard.ime.text.gestures.GlideTypingQuality
import dev.patrickgold.florisboard.ime.text.gestures.GlideTypingUnavailableReason
import dev.patrickgold.florisboard.ime.text.gestures.GlideTrailTheme
import dev.patrickgold.florisboard.ime.text.gestures.SpaceTouchpadPolicy
import dev.patrickgold.florisboard.ime.text.gestures.SwipeAction
import dev.patrickgold.florisboard.lib.FlorisLocale
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.florisboard.app.settings.search.DialogSliderPreference
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import dev.patrickgold.florisboard.app.settings.search.ListPreference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.florisboard.app.settings.search.SwitchPreference
import org.florisboard.lib.compose.FlorisInfoCard
import org.florisboard.lib.compose.FlorisWarningCard
import org.florisboard.lib.compose.stringRes

@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun GesturesScreen() = FlorisScreen {
    val prefs by FlorisPreferenceStore
    title = stringRes(R.string.settings__gestures__title)
    previewFieldVisible = true

    content {
        val isGlideEnabled by prefs.glide.enabled.collectAsState()
        val symbolFlickEnabled by prefs.gestures.symbolFlickEnabled.collectAsState()
        val hintedSymbolsEnabled by prefs.keyboard.hintedSymbolsEnabled.collectAsState()
        val spaceBarSwipeUp by prefs.gestures.spaceBarSwipeUp.collectAsState()
        val spaceBarSwipeDown by prefs.gestures.spaceBarSwipeDown.collectAsState()
        val spaceBarSwipeLeft by prefs.gestures.spaceBarSwipeLeft.collectAsState()
        val spaceBarSwipeRight by prefs.gestures.spaceBarSwipeRight.collectAsState()
        val deleteKeySwipeLeft by prefs.gestures.deleteKeySwipeLeft.collectAsState()
        val autoReturnAfterApostrophe by prefs.keyboard.autoReturnAfterApostrophe.collectAsState()
        val conflictSummary = GestureConflictPolicy.evaluate(
            GesturePreferenceSnapshot(
                glideEnabled = isGlideEnabled,
                symbolFlickEnabled = symbolFlickEnabled,
                hintedSymbolsEnabled = hintedSymbolsEnabled,
                spaceBarSwipeUp = spaceBarSwipeUp,
                spaceBarSwipeDown = spaceBarSwipeDown,
                spaceBarSwipeLeft = spaceBarSwipeLeft,
                spaceBarSwipeRight = spaceBarSwipeRight,
                deleteKeySwipeLeft = deleteKeySwipeLeft,
                autoReturnAfterApostrophe = autoReturnAfterApostrophe,
            ),
        )

        FlorisInfoCard(
            modifier = Modifier.padding(8.dp),
            text = stringRes(R.string.settings__gestures__intro),
        )
        // Glide can be switched on in preferences and still not run: the device may be flagged
        // low-RAM, or this session may have released the gesture data after a failed allocation.
        val glideCapability by GlideTypingCapability.state.collectAsState()
        glideCapability.unavailableReason?.let { reason ->
            FlorisWarningCard(
                modifier = Modifier.padding(8.dp),
                text = stringRes(R.string.settings__gestures__glide_unavailable_title),
                secondaryText = stringRes(
                    when (reason) {
                        GlideTypingUnavailableReason.LowRamDevice ->
                            R.string.settings__gestures__glide_unavailable_low_ram
                        GlideTypingUnavailableReason.AllocationFailed ->
                            R.string.settings__gestures__glide_unavailable_memory
                    },
                ),
            )
        }
        if (conflictSummary.glidePausesGeneralKeySwipes) {
            FlorisInfoCard(
                modifier = Modifier.padding(8.dp),
                text = stringRes(R.string.settings__gestures__glide_conflict_notice),
            )
        }
        when {
            conflictSummary.symbolFlickReady -> FlorisInfoCard(
                modifier = Modifier.padding(8.dp),
                text = stringRes(R.string.settings__gestures__symbol_flick_ready),
            )
            conflictSummary.symbolFlickNeedsHintedSymbols -> FlorisInfoCard(
                modifier = Modifier.padding(8.dp),
                text = stringRes(R.string.settings__gestures__symbol_flick_needs_hints),
            )
        }
        val spaceBarTouchpadMode by prefs.gestures.spaceBarTouchpadMode.collectAsState()
        if (spaceBarTouchpadMode) {
            FlorisInfoCard(
                modifier = Modifier.padding(8.dp),
                text = stringRes(R.string.settings__gestures__touchpad_cursor_ready),
            )
        } else if (conflictSummary.spaceBarCursorMovementEnabled) {
            FlorisInfoCard(
                modifier = Modifier.padding(8.dp),
                text = stringRes(R.string.settings__gestures__spacebar_cursor_ready),
            )
        }
        if (conflictSummary.deleteSwipeEnabled) {
            FlorisInfoCard(
                modifier = Modifier.padding(8.dp),
                text = stringRes(R.string.settings__gestures__delete_swipe_ready),
            )
        }

        PreferenceGroup(title = stringRes(R.string.pref__glide__title)) {
            SwitchPreference(
                prefs.glide.enabled,
                title = stringRes(R.string.pref__glide__enabled__label),
                summary = stringRes(R.string.pref__glide__enabled__summary),
            )
            SwitchPreference(
                prefs.glide.showTrail,
                title = stringRes(R.string.pref__glide__show_trail__label),
                summary = stringRes(R.string.pref__glide__show_trail__summary),
                enabledIf = { prefs.glide.enabled isEqualTo true },
            )
            ListPreference(
                prefs.glide.trailTheme,
                title = stringRes(R.string.pref__glide__trail_theme__label),
                entries = enumDisplayEntriesOf(GlideTrailTheme::class),
                enabledIf = { prefs.glide.enabled isEqualTo true && prefs.glide.showTrail isEqualTo true },
            )
            DialogSliderPreference(
                prefs.glide.trailDuration,
                title = stringRes(R.string.pref__glide_trail_fade_duration),
                valueLabel = { stringRes(R.string.unit__milliseconds__symbol, "v" to it) },
                min = 0,
                max = 500,
                stepIncrement = 10,
                enabledIf = { prefs.glide.enabled isEqualTo true && prefs.glide.showTrail isEqualTo true },
            )
            SwitchPreference(
                prefs.glide.showPreview,
                title = stringRes(R.string.pref__glide__show_preview),
                summary = stringRes(R.string.pref__glide__show_preview__summary),
                enabledIf = { prefs.glide.enabled isEqualTo true },
            )
            DialogSliderPreference(
                prefs.glide.previewRefreshDelay,
                title = stringRes(R.string.pref__glide_preview_refresh_delay),
                valueLabel = { stringRes(R.string.unit__milliseconds__symbol, "v" to it) },
                min = 50,
                max = 500,
                stepIncrement = 25,
                enabledIf = { prefs.glide.enabled isEqualTo true && prefs.glide.showPreview isEqualTo true },
            )
            DialogSliderPreference(
                prefs.glide.sensitivity,
                title = stringRes(R.string.pref__glide__sensitivity__label),
                valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
                min = 0,
                max = 100,
                stepIncrement = 5,
                enabledIf = { prefs.glide.enabled isEqualTo true },
            )
            SwitchPreference(
                prefs.glide.immediateBackspaceDeletesWord,
                title = stringRes(R.string.pref__glide__immediate_backspace_deletes_word__label),
                summary = stringRes(R.string.pref__glide__immediate_backspace_deletes_word__summary),
                enabledIf = { prefs.glide.enabled isEqualTo true },
            )
            SwitchPreference(
                prefs.glide.flowThroughSpace,
                title = stringRes(R.string.pref__glide__flow_through_space__label),
                summary = stringRes(R.string.pref__glide__flow_through_space__summary),
                enabledIf = { prefs.glide.enabled isEqualTo true },
            )
        }

        PreferenceGroup(title = stringRes(R.string.pref__glide__languages_title)) {
            listOf(
                FlorisLocale.ENGLISH to prefs.glide.enabledEnglish,
                FlorisLocale.from("de") to prefs.glide.enabledGerman,
                FlorisLocale.from("es") to prefs.glide.enabledSpanish,
                FlorisLocale.from("fr") to prefs.glide.enabledFrench,
                FlorisLocale.from("it") to prefs.glide.enabledItalian,
                FlorisLocale.from("pt") to prefs.glide.enabledPortuguese,
            ).forEach { (locale, preference) ->
                val language = locale.displayLanguage()
                val profile = GlideTypingLanguageSupport.profileFor(locale.language)
                SwitchPreference(
                    preference,
                    title = stringRes(
                        R.string.pref__glide__language_enabled__label,
                        "language" to language,
                    ),
                    summary = stringRes(
                        R.string.pref__glide__language_enabled__summary_with_quality,
                        "language" to language,
                        "quality" to stringRes(profile.qualityLabelRes),
                        "engine" to stringRes(profile.engineLabelRes),
                    ),
                    enabledIf = { prefs.glide.enabled isEqualTo true },
                )
            }
        }

        PreferenceGroup(title = stringRes(R.string.pref__gestures__general_title)) {
            ListPreference(
                prefs.gestures.swipeUp,
                title = stringRes(R.string.pref__gestures__swipe_up__label),
                entries = enumDisplayEntriesOf(SwipeAction::class, "general"),
                enabledIf = { prefs.glide.enabled isEqualTo false },
            )
            ListPreference(
                prefs.gestures.swipeDown,
                title = stringRes(R.string.pref__gestures__swipe_down__label),
                entries = enumDisplayEntriesOf(SwipeAction::class, "general"),
                enabledIf = { prefs.glide.enabled isEqualTo false },
            )
            ListPreference(
                prefs.gestures.swipeLeft,
                title = stringRes(R.string.pref__gestures__swipe_left__label),
                entries = enumDisplayEntriesOf(SwipeAction::class, "general"),
                enabledIf = { prefs.glide.enabled isEqualTo false },
            )
            ListPreference(
                prefs.gestures.swipeRight,
                title = stringRes(R.string.pref__gestures__swipe_right__label),
                entries = enumDisplayEntriesOf(SwipeAction::class, "general"),
                enabledIf = { prefs.glide.enabled isEqualTo false },
            )
            DialogSliderPreference(
                prefs.gestures.languageSwitchSwipeSensitivity,
                title = stringRes(R.string.pref__gestures__language_switch_swipe_sensitivity__label),
                valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
                min = 0,
                max = 100,
                stepIncrement = 5,
                enabledIf = { prefs.glide.enabled isEqualTo false },
            )
            SwitchPreference(
                prefs.gestures.symbolFlickEnabled,
                title = stringRes(R.string.pref__gestures__symbol_flick_enabled__label),
                summary = stringRes(R.string.pref__gestures__symbol_flick_enabled__summary),
                enabledIf = { prefs.glide.enabled isEqualTo false },
            )
        }

        PreferenceGroup(title = stringRes(R.string.pref__gestures__space_bar_title)) {
            SwitchPreference(
                prefs.gestures.spaceBarTouchpadMode,
                title = stringRes(R.string.pref__gestures__space_bar_touchpad_mode__label),
                summary = stringRes(R.string.pref__gestures__space_bar_touchpad_mode__summary),
            )
            DialogSliderPreference(
                prefs.gestures.spaceBarTouchpadRatio,
                title = stringRes(R.string.pref__gestures__space_bar_touchpad_ratio__label),
                valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
                min = SpaceTouchpadPolicy.MIN_RATIO_PERCENT,
                max = SpaceTouchpadPolicy.MAX_RATIO_PERCENT,
                stepIncrement = 5,
                enabledIf = { prefs.gestures.spaceBarTouchpadMode isEqualTo true },
            )
            DialogSliderPreference(
                prefs.gestures.spaceBarSwipeSensitivity,
                title = stringRes(R.string.pref__gestures__space_bar_swipe_sensitivity__label),
                valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
                min = 0,
                max = 100,
                stepIncrement = 5,
            )
            ListPreference(
                prefs.gestures.spaceBarSwipeUp,
                title = stringRes(R.string.pref__gestures__space_bar_swipe_up__label),
                entries = enumDisplayEntriesOf(SwipeAction::class, "general"),
                enabledIf = { prefs.gestures.spaceBarTouchpadMode isEqualTo false },
            )
            ListPreference(
                prefs.gestures.spaceBarSwipeDown,
                title = stringRes(R.string.pref__gestures__space_bar_swipe_down__label),
                entries = enumDisplayEntriesOf(SwipeAction::class, "general"),
                enabledIf = { prefs.gestures.spaceBarTouchpadMode isEqualTo false },
            )
            ListPreference(
                prefs.gestures.spaceBarSwipeLeft,
                title = stringRes(R.string.pref__gestures__space_bar_swipe_left__label),
                entries = enumDisplayEntriesOf(SwipeAction::class, "general"),
                enabledIf = { prefs.gestures.spaceBarTouchpadMode isEqualTo false },
            )
            ListPreference(
                prefs.gestures.spaceBarSwipeRight,
                title = stringRes(R.string.pref__gestures__space_bar_swipe_right__label),
                entries = enumDisplayEntriesOf(SwipeAction::class, "general"),
                enabledIf = { prefs.gestures.spaceBarTouchpadMode isEqualTo false },
            )
            ListPreference(
                prefs.gestures.spaceBarLongPress,
                title = stringRes(R.string.pref__gestures__space_bar_long_press__label),
                entries = enumDisplayEntriesOf(SwipeAction::class, "general"),
            )
        }

        PreferenceGroup(title = stringRes(R.string.pref__gestures__other_title)) {
            ListPreference(
                prefs.gestures.deleteKeySwipeLeft,
                title = stringRes(R.string.pref__gestures__delete_key_swipe_left__label),
                entries = enumDisplayEntriesOf(SwipeAction::class, "deleteSwipe"),
            )
            DialogSliderPreference(
                prefs.gestures.deleteKeySwipeSensitivity,
                title = stringRes(R.string.pref__gestures__delete_key_swipe_sensitivity__label),
                valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
                min = 0,
                max = 100,
                stepIncrement = 5,
            )
            ListPreference(
                prefs.gestures.deleteKeyLongPress,
                title = stringRes(R.string.pref__gestures__delete_key_long_press__label),
                entries = enumDisplayEntriesOf(SwipeAction::class, "deleteLongPress"),
            )
            DialogSliderPreference(
                prefs.gestures.swipeVelocityThreshold,
                title = stringRes(R.string.pref__gestures__swipe_velocity_threshold__label),
                valueLabel = { stringRes(R.string.unit__display_pixel_per_seconds__symbol, "v" to it) },
                min = 400,
                max = 4000,
                stepIncrement = 100,
            )
            DialogSliderPreference(
                prefs.gestures.swipeDistanceThreshold,
                title = stringRes(R.string.pref__gestures__swipe_distance_threshold__label),
                valueLabel = { stringRes(R.string.unit__display_pixel__symbol, "v" to it) },
                min = 12,
                max = 72,
                stepIncrement = 1,
            )
        }
    }
}

private val GlideTypingLanguageProfile?.qualityLabelRes: Int
    get() = when (this?.quality) {
        GlideTypingQuality.EXPANDED_STATISTICAL -> R.string.pref__glide__quality__expanded_statistical
        GlideTypingQuality.IMPORTED_STATISTICAL -> R.string.pref__glide__quality__imported_statistical
        null -> R.string.pref__glide__quality__unsupported
    }

private val GlideTypingLanguageProfile?.engineLabelRes: Int
    get() = when (this?.engine) {
        GlideTypingEngine.STATISTICAL -> R.string.pref__glide__engine__statistical
        null -> R.string.pref__glide__engine__unsupported
    }
