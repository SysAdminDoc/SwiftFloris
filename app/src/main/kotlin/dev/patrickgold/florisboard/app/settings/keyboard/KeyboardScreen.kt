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

package dev.patrickgold.florisboard.app.settings.keyboard

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.enumDisplayEntriesOf
import dev.patrickgold.florisboard.ime.input.CapitalizationBehavior
import dev.patrickgold.florisboard.ime.keyboard.SpaceBarMode
import dev.patrickgold.florisboard.ime.landscapeinput.LandscapeInputUiMode
import dev.patrickgold.florisboard.ime.smartbar.IncognitoDisplayMode
import dev.patrickgold.florisboard.ime.text.key.KeyHintMode
import dev.patrickgold.florisboard.ime.text.key.UtilityKeyAction
import dev.patrickgold.florisboard.ime.text.keyboard.BottomRowPreset
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.ui.DialogSliderPreference
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import dev.patrickgold.jetpref.datastore.ui.ListPreference
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference
import dev.patrickgold.jetpref.datastore.ui.listPrefEntries
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun KeyboardScreen() = FlorisScreen {
    title = stringRes(R.string.settings__keyboard__title)
    previewFieldVisible = true

    val navController = LocalNavController.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    content {
        PreferenceGroup(title = stringRes(R.string.pref__keyboard__group_rows_and_hints__label)) {
            SwitchPreference(
                prefs.keyboard.numberRow,
                title = stringRes(R.string.pref__keyboard__number_row__label),
                summary = stringRes(R.string.pref__keyboard__number_row__summary),
            )
            ListPreference(
                listPref = prefs.keyboard.hintedNumberRowMode,
                switchPref = prefs.keyboard.hintedNumberRowEnabled,
                title = stringRes(R.string.pref__keyboard__hinted_number_row_mode__label),
                summarySwitchDisabled = stringRes(R.string.state__disabled),
                entries = enumDisplayEntriesOf(KeyHintMode::class),
                enabledIf = { prefs.keyboard.numberRow.isFalse() },
            )
            ListPreference(
                listPref = prefs.keyboard.hintedSymbolsMode,
                switchPref = prefs.keyboard.hintedSymbolsEnabled,
                title = stringRes(R.string.pref__keyboard__hinted_symbols_mode__label),
                summarySwitchDisabled = stringRes(R.string.state__disabled),
                entries = enumDisplayEntriesOf(KeyHintMode::class),
            )
            ListPreference(
                prefs.keyboard.bottomRowPresetJson,
                title = stringRes(R.string.pref__keyboard__bottom_row_preset__label),
                entries = listPrefEntries {
                    listOf(
                        entry(
                            key = BottomRowPreset.AutomaticPreferenceValue,
                            label = stringRes(R.string.pref__keyboard__bottom_row_preset__automatic),
                        ),
                        entry(
                            key = BottomRowPreset.SwiftKey.toJson(),
                            label = stringRes(R.string.pref__keyboard__bottom_row_preset__swiftkey),
                        ),
                        entry(
                            key = BottomRowPreset.Language.toJson(),
                            label = stringRes(R.string.pref__keyboard__bottom_row_preset__language),
                        ),
                        entry(
                            key = BottomRowPreset.Voice.toJson(),
                            label = stringRes(R.string.pref__keyboard__bottom_row_preset__voice),
                        ),
                        entry(
                            key = BottomRowPreset.Settings.toJson(),
                            label = stringRes(R.string.pref__keyboard__bottom_row_preset__settings),
                        ),
                        entry(
                            key = BottomRowPreset.Minimal.toJson(),
                            label = stringRes(R.string.pref__keyboard__bottom_row_preset__minimal),
                        ),
                        entry(
                            // ROADMAP §7 Next-8.1a — programmer-mode bottom-row preset.
                            // Surfaces Tab + Esc + bracket/brace popup directly in the
                            // main letter view (complements the Next-8.2 CODE smartbar
                            // profile that auto-activates on terminal/IDE packages).
                            key = BottomRowPreset.Programmer.toJson(),
                            label = stringRes(R.string.pref__keyboard__bottom_row_preset__programmer),
                        ),
                        entry(
                            // docs/archive/SWIFTKEY_PARITY_ROADMAP_2026-05-17 §C2 — SwiftKey
                            // "Modes → Arrow keys" parity. Swaps the bottom row
                            // for ← ↑ ↓ → so cursor navigation doesn't need the
                            // space-bar trackpad gesture or a hardware-keyboard
                            // handoff. Space bar shrinks but stays present.
                            key = BottomRowPreset.Navigation.toJson(),
                            label = stringRes(R.string.pref__keyboard__bottom_row_preset__navigation),
                        ),
                    )
                },
            )
        }

        PreferenceGroup(title = stringRes(R.string.pref__keyboard__group_behavior__label)) {
            SwitchPreference(
                prefs.keyboard.utilityKeyEnabled,
                title = stringRes(R.string.pref__keyboard__utility_key_enabled__label),
                summary = stringRes(R.string.pref__keyboard__utility_key_enabled__summary),
            )
            ListPreference(
                prefs.keyboard.utilityKeyAction,
                title = stringRes(R.string.pref__keyboard__utility_key_action__label),
                entries = enumDisplayEntriesOf(UtilityKeyAction::class),
                visibleIf = { prefs.keyboard.utilityKeyEnabled isEqualTo true },
            )
            ListPreference(
                prefs.keyboard.spaceBarMode,
                title = stringRes(R.string.pref__keyboard__space_bar_mode__label),
                entries = enumDisplayEntriesOf(SpaceBarMode::class),
            )
            ListPreference(
                prefs.keyboard.capitalizationBehavior,
                title = stringRes(R.string.pref__keyboard__capitalization_behavior__label),
                entries = enumDisplayEntriesOf(CapitalizationBehavior::class),
            )
            ListPreference(
                listPref = prefs.keyboard.incognitoDisplayMode,
                title = stringRes(R.string.pref__keyboard__incognito_indicator__label),
                entries = enumDisplayEntriesOf(IncognitoDisplayMode::class),
            )
        }

        PreferenceGroup(title = stringRes(R.string.pref__keyboard__group_layout__label)) {
            ListPreference(
                prefs.keyboard.landscapeInputUiMode,
                title = stringRes(R.string.pref__keyboard__landscape_input_ui_mode__label),
                entries = enumDisplayEntriesOf(LandscapeInputUiMode::class),
            )
            DialogSliderPreference(
                primaryPref = prefs.keyboard.fontSizeMultiplierPortrait,
                secondaryPref = prefs.keyboard.fontSizeMultiplierLandscape,
                title = stringRes(R.string.pref__keyboard__font_size_multiplier__label),
                primaryLabel = stringRes(R.string.screen_orientation__portrait),
                secondaryLabel = stringRes(R.string.screen_orientation__landscape),
                valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
                min = 50,
                max = 150,
                stepIncrement = 5,
            )
            // ROADMAP §6 N5.3 — keyboard height slider (HeliBoard #1342, ASK #1775).
            DialogSliderPreference(
                primaryPref = prefs.keyboard.keyboardHeightMultiplierPortrait,
                secondaryPref = prefs.keyboard.keyboardHeightMultiplierLandscape,
                title = stringRes(R.string.pref__keyboard__keyboard_height_multiplier__label),
                primaryLabel = stringRes(R.string.screen_orientation__portrait),
                secondaryLabel = stringRes(R.string.screen_orientation__landscape),
                valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
                min = 50,
                max = 150,
                stepIncrement = 5,
            )
            DialogSliderPreference(
                primaryPref = prefs.keyboard.keySpacingVertical,
                secondaryPref = prefs.keyboard.keySpacingHorizontal,
                title = stringRes(R.string.pref__keyboard__key_spacing__label),
                primaryLabel = stringRes(R.string.screen_orientation__vertical),
                secondaryLabel = stringRes(R.string.screen_orientation__horizontal),
                valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
                min = 50,
                max = 150,
                stepIncrement = 5,
            )
            // ROADMAP §7 Next-7.1 — floating-mode default toggle. The user can
            // still flip at runtime via the existing TOGGLE_COMPACT_LAYOUT
            // smartbar quick-action / swipe binding.
            SwitchPreference(
                pref = prefs.keyboard.startInFloatingMode,
                title = stringRes(R.string.pref__keyboard__start_in_floating_mode__label),
                summary = stringRes(R.string.pref__keyboard__start_in_floating_mode__summary),
            )
            val floatingOnboardingResetToast = stringRes(R.string.pref__keyboard__floating_onboarding_reset__toast)
            Preference(
                title = stringRes(R.string.pref__keyboard__floating_onboarding_reset__label),
                summary = stringRes(R.string.pref__keyboard__floating_onboarding_reset__summary),
                onClick = {
                    scope.launch {
                        prefs.keyboard.floatingOnboardingShown.set(false)
                        Toast.makeText(context, floatingOnboardingResetToast, Toast.LENGTH_SHORT).show()
                    }
                },
            )
            // ROADMAP §7 Next-4.3 — stylus handwriting toggle. Off by default
            // until the recogniser (Next-4.2) lands.
            SwitchPreference(
                pref = prefs.keyboard.stylusHandwritingEnabled,
                title = stringRes(R.string.pref__keyboard__stylus_handwriting__label),
                summary = stringRes(R.string.pref__keyboard__stylus_handwriting__summary),
            )
        }

        PreferenceGroup(title = stringRes(R.string.pref__keyboard__group_keypress__label)) {
            Preference(
                title = stringRes(R.string.settings__input_feedback__title),
                summary = stringRes(R.string.settings__input_feedback__summary),
                onClick = { navController.navigate(Routes.Settings.InputFeedback) },
            )
            SwitchPreference(
                prefs.keyboard.popupEnabled,
                title = stringRes(R.string.pref__keyboard__popup_enabled__label),
                summary = stringRes(R.string.pref__keyboard__popup_enabled__summary),
            )
            SwitchPreference(
                prefs.keyboard.mergeHintPopupsEnabled,
                title = stringRes(R.string.pref__keyboard__merge_hint_popups_enabled__label),
                summary = stringRes(R.string.pref__keyboard__merge_hint_popups_enabled__summary),
            )
            DialogSliderPreference(
                prefs.keyboard.longPressDelay,
                title = stringRes(R.string.pref__keyboard__long_press_delay__label),
                valueLabel = { stringRes(R.string.unit__milliseconds__symbol, "v" to it) },
                min = 100,
                max = 700,
                stepIncrement = 10,
            )
            SwitchPreference(
                prefs.keyboard.spaceBarSwitchesToCharacters,
                title = stringRes(R.string.pref__keyboard__space_bar_switches_to_characters__label),
                summary = stringRes(R.string.pref__keyboard__space_bar_switches_to_characters__summary),
            )
            SwitchPreference(
                prefs.keyboard.autoReturnAfterApostrophe,
                title = stringRes(R.string.pref__keyboard__auto_return_after_apostrophe__label),
                summary = stringRes(R.string.pref__keyboard__auto_return_after_apostrophe__summary),
            )
            SwitchPreference(
                prefs.keyboard.quoteAutoCloseEnabled,
                title = stringRes(R.string.pref__keyboard__quote_auto_close_enabled__label),
                summary = stringRes(R.string.pref__keyboard__quote_auto_close_enabled__summary),
            )
        }
    }
}
