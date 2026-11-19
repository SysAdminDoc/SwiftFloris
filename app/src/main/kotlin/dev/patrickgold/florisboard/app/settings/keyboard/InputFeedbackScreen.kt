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

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.enumDisplayEntriesOf
import dev.patrickgold.florisboard.ime.input.HapticVibrationMode
import dev.patrickgold.florisboard.ime.input.InputFeedbackActivationMode
import dev.patrickgold.florisboard.ime.input.InputFeedbackSystemSwitches
import dev.patrickgold.florisboard.ime.input.KeypressSoundClass
import dev.patrickgold.florisboard.ime.input.KeypressSoundStore
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.app.settings.search.DialogSliderPreference
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import dev.patrickgold.florisboard.app.settings.search.ListPreference
import dev.patrickgold.florisboard.app.settings.search.Preference
import dev.patrickgold.florisboard.app.settings.search.PreferenceGroup
import dev.patrickgold.florisboard.app.settings.search.SwitchPreference
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.android.systemVibratorOrNull
import org.florisboard.lib.android.vibrate
import org.florisboard.lib.android.showLongToast
import org.florisboard.lib.android.showShortToast
import org.florisboard.lib.compose.FlorisInfoCard
import org.florisboard.lib.compose.stringRes
import dev.patrickgold.florisboard.lib.util.summarizeForUser

@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun InputFeedbackScreen() = FlorisScreen {
    title = stringRes(R.string.settings__input_feedback__title)
    previewFieldVisible = true
    iconSpaceReserved = false

    val context = LocalContext.current
    val detailsUnavailable = stringRes(R.string.error__details_unavailable)
    val vibrator = context.systemVibratorOrNull()
    val scope = rememberCoroutineScope()
    var pendingSoundClass by remember { mutableStateOf<KeypressSoundClass?>(null) }
    var selectedSoundClasses by remember { mutableStateOf(KeypressSoundStore.available(context)) }

    // The phone's touch sounds and touch vibration switches, read again on every return here so
    // a trip to the phone's settings shows up without reopening the screen.
    val lifecycleOwner = LocalLifecycleOwner.current
    var phoneTouchSoundOn by remember {
        mutableStateOf(InputFeedbackSystemSwitches.isTouchSoundOn(context.contentResolver))
    }
    var phoneTouchVibrationOn by remember {
        mutableStateOf(InputFeedbackSystemSwitches.isTouchVibrationOn(context.contentResolver))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                phoneTouchSoundOn = InputFeedbackSystemSwitches.isTouchSoundOn(context.contentResolver)
                phoneTouchVibrationOn = InputFeedbackSystemSwitches.isTouchVibrationOn(context.contentResolver)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val soundPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val soundClass = pendingSoundClass
        pendingSoundClass = null
        if (uri == null || soundClass == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    KeypressSoundStore.import(context, soundClass, uri)
                }
            }.onSuccess {
                selectedSoundClasses = KeypressSoundStore.available(context)
                context.showLongToast(R.string.pref__input_feedback__custom_sounds__imported)
            }.onFailure { error ->
                context.showLongToast(
                    R.string.pref__input_feedback__custom_sounds__import_failed,
                    "error_message" to error.summarizeForUser(detailsUnavailable),
                )
            }
        }
    }

    fun chooseSound(soundClass: KeypressSoundClass) {
        pendingSoundClass = soundClass
        soundPicker.launch(arrayOf("audio/*"))
    }

    @Composable
    fun soundSummary(soundClass: KeypressSoundClass): String = if (soundClass in selectedSoundClasses) {
        stringRes(R.string.pref__input_feedback__custom_sounds__selected)
    } else {
        stringRes(R.string.pref__input_feedback__custom_sounds__system_default)
    }

    content {
        PreferenceGroup(title = stringRes(R.string.pref__input_feedback__group_audio__label)) {
            ListPreference(
                listPref = prefs.inputFeedback.audioActivationMode,
                switchPref = prefs.inputFeedback.audioEnabled,
                title = stringRes(R.string.pref__input_feedback__audio_enabled__label),
                summarySwitchDisabled = stringRes(R.string.pref__input_feedback__audio_enabled__summary_disabled),
                entries = enumDisplayEntriesOf(InputFeedbackActivationMode::class, "audio"),
            )
            val audioEnabled by prefs.inputFeedback.audioEnabled.collectAsState()
            val audioMode by prefs.inputFeedback.audioActivationMode.collectAsState()
            // Without this the keyboard went quiet with every option here on and no word why (#26).
            if (InputFeedbackSystemSwitches.isSilencedByPhone(audioEnabled, audioMode, phoneTouchSoundOn)) {
                Preference(
                    title = stringRes(R.string.pref__input_feedback__audio_silenced_by_system__label),
                    summary = stringRes(R.string.pref__input_feedback__audio_silenced_by_system__summary),
                    onClick = {
                        scope.launch {
                            prefs.inputFeedback.audioActivationMode.set(InputFeedbackActivationMode.IGNORE_SYSTEM_SETTINGS)
                            context.showShortToast(R.string.pref__input_feedback__audio_silenced_by_system__done)
                        }
                    },
                )
            }
            DialogSliderPreference(
                prefs.inputFeedback.audioVolume,
                title = stringRes(R.string.pref__input_feedback__audio_volume__label),
                valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
                min = 1,
                max = 100,
                stepIncrement = 1,
                enabledIf = { prefs.inputFeedback.audioEnabled isEqualTo true },
            )
            Preference(
                title = stringRes(R.string.pref__input_feedback__custom_sounds__standard__label),
                summary = soundSummary(KeypressSoundClass.STANDARD),
                onClick = { chooseSound(KeypressSoundClass.STANDARD) },
            )
            Preference(
                title = stringRes(R.string.pref__input_feedback__custom_sounds__delete__label),
                summary = soundSummary(KeypressSoundClass.DELETE),
                onClick = { chooseSound(KeypressSoundClass.DELETE) },
            )
            Preference(
                title = stringRes(R.string.pref__input_feedback__custom_sounds__return__label),
                summary = soundSummary(KeypressSoundClass.RETURN),
                onClick = { chooseSound(KeypressSoundClass.RETURN) },
            )
            Preference(
                title = stringRes(R.string.pref__input_feedback__custom_sounds__spacebar__label),
                summary = soundSummary(KeypressSoundClass.SPACEBAR),
                onClick = { chooseSound(KeypressSoundClass.SPACEBAR) },
            )
            Preference(
                title = stringRes(R.string.pref__input_feedback__custom_sounds__reset__label),
                summary = stringRes(R.string.pref__input_feedback__custom_sounds__reset__summary),
                onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            KeypressSoundStore.deleteAll(context)
                        }
                        selectedSoundClasses = emptySet()
                        context.showLongToast(R.string.pref__input_feedback__custom_sounds__reset_done)
                    }
                },
            )
            FlorisInfoCard(
                modifier = Modifier.padding(8.dp),
                text = stringRes(R.string.pref__input_feedback__custom_sounds__summary),
            )
            SwitchPreference(
                prefs.inputFeedback.audioFeatKeyPress,
                title = stringRes(R.string.pref__input_feedback__audio_feat_key_press__label),
                summary = stringRes(R.string.pref__input_feedback__any_feat_key_press__summary),
                enabledIf = { prefs.inputFeedback.audioEnabled isEqualTo true },
            )
            SwitchPreference(
                prefs.inputFeedback.audioFeatKeyLongPress,
                title = stringRes(R.string.pref__input_feedback__audio_feat_key_long_press__label),
                summary = stringRes(R.string.pref__input_feedback__any_feat_key_long_press__summary),
                enabledIf = { prefs.inputFeedback.audioEnabled isEqualTo true },
            )
            SwitchPreference(
                prefs.inputFeedback.audioFeatKeyRepeatedAction,
                title = stringRes(R.string.pref__input_feedback__audio_feat_key_repeated_action__label),
                summary = stringRes(R.string.pref__input_feedback__any_feat_key_repeated_action__summary),
                enabledIf = { prefs.inputFeedback.audioEnabled isEqualTo true },
            )
            SwitchPreference(
                prefs.inputFeedback.audioFeatGestureSwipe,
                title = stringRes(R.string.pref__input_feedback__audio_feat_gesture_swipe__label),
                summary = stringRes(R.string.pref__input_feedback__any_feat_gesture_swipe__summary),
                enabledIf = { prefs.inputFeedback.audioEnabled isEqualTo true },
            )
            SwitchPreference(
                prefs.inputFeedback.audioFeatGestureMovingSwipe,
                title = stringRes(R.string.pref__input_feedback__audio_feat_gesture_moving_swipe__label),
                summary = stringRes(R.string.pref__input_feedback__any_feat_gesture_moving_swipe__summary),
                enabledIf = { prefs.inputFeedback.audioEnabled isEqualTo true },
            )
        }

        PreferenceGroup(title = stringRes(R.string.pref__input_feedback__group_haptic__label)) {
            if (vibrator == null || !vibrator.hasVibrator()) {
                FlorisInfoCard(
                    modifier = Modifier.padding(8.dp),
                    text = stringRes(R.string.pref__input_feedback__haptic_vibrator_unavailable__message),
                )
            } else if (!vibrator.hasAmplitudeControl()) {
                FlorisInfoCard(
                    modifier = Modifier.padding(8.dp),
                    text = stringRes(R.string.pref__input_feedback__haptic_amplitude_unavailable__message),
                )
            }

            ListPreference(
                listPref = prefs.inputFeedback.hapticActivationMode,
                switchPref = prefs.inputFeedback.hapticEnabled,
                title = stringRes(R.string.pref__input_feedback__haptic_enabled__label),
                summarySwitchDisabled = stringRes(R.string.pref__input_feedback__haptic_enabled__summary_disabled),
                entries = enumDisplayEntriesOf(InputFeedbackActivationMode::class, "haptic")
            )
            val hapticEnabled by prefs.inputFeedback.hapticEnabled.collectAsState()
            val hapticMode by prefs.inputFeedback.hapticActivationMode.collectAsState()
            if (InputFeedbackSystemSwitches.isSilencedByPhone(hapticEnabled, hapticMode, phoneTouchVibrationOn)) {
                Preference(
                    title = stringRes(R.string.pref__input_feedback__haptic_silenced_by_system__label),
                    summary = stringRes(R.string.pref__input_feedback__haptic_silenced_by_system__summary),
                    onClick = {
                        scope.launch {
                            prefs.inputFeedback.hapticActivationMode.set(InputFeedbackActivationMode.IGNORE_SYSTEM_SETTINGS)
                            context.showShortToast(R.string.pref__input_feedback__haptic_silenced_by_system__done)
                        }
                    },
                )
            }
            ListPreference(
                prefs.inputFeedback.hapticVibrationMode,
                title = stringRes(R.string.pref__input_feedback__haptic_vibration_mode__label),
                enabledIf = { prefs.inputFeedback.hapticEnabled isEqualTo true },
                entries = enumDisplayEntriesOf(HapticVibrationMode::class),
            )
            DialogSliderPreference(
                prefs.inputFeedback.hapticVibrationDuration,
                title = stringRes(R.string.pref__input_feedback__haptic_vibration_duration__label),
                valueLabel = { stringRes(R.string.unit__milliseconds__symbol, "v" to it) },
                summary = {
                    if (vibrator == null || !vibrator.hasVibrator()) {
                        stringRes(R.string.pref__input_feedback__haptic_vibration_duration__summary_no_vibrator)
                    } else {
                        stringRes(R.string.unit__milliseconds__symbol, "v" to it)
                    }
                },
                min = 1,
                max = 100,
                stepIncrement = 1,
                onPreviewSelectedValue = { duration ->
                    val strength = prefs.inputFeedback.hapticVibrationStrength.get()
                    // A slider preview should feel like the key presses it tunes, so it follows the same mode.
                    vibrator?.vibrate(
                        duration,
                        strength,
                        ignoreSystemSettings = prefs.inputFeedback.hapticActivationMode.get() ==
                            InputFeedbackActivationMode.IGNORE_SYSTEM_SETTINGS,
                    )
                },
                enabledIf = {
                    prefs.inputFeedback.hapticEnabled isEqualTo true &&
                        prefs.inputFeedback.hapticVibrationMode isEqualTo HapticVibrationMode.USE_VIBRATOR_DIRECTLY &&
                        vibrator != null && vibrator.hasVibrator()
                },
            )
            DialogSliderPreference(
                prefs.inputFeedback.hapticVibrationStrength,
                title = stringRes(R.string.pref__input_feedback__haptic_vibration_strength__label),
                valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
                summary = { strength ->
                    if (vibrator == null || !vibrator.hasVibrator()) {
                        stringRes(R.string.pref__input_feedback__haptic_vibration_strength__summary_no_vibrator)
                    } else if (!vibrator.hasAmplitudeControl()) {
                        stringRes(R.string.pref__input_feedback__haptic_vibration_strength__summary_no_amplitude_ctrl)
                    } else {
                        stringRes(R.string.unit__percent__symbol, "v" to strength)
                    }
                },
                min = 1,
                max = 100,
                stepIncrement = 1,
                onPreviewSelectedValue = { strength ->
                    val duration = prefs.inputFeedback.hapticVibrationDuration.get()
                    vibrator?.vibrate(
                        duration,
                        strength,
                        ignoreSystemSettings = prefs.inputFeedback.hapticActivationMode.get() ==
                            InputFeedbackActivationMode.IGNORE_SYSTEM_SETTINGS,
                    )
                },
                enabledIf = {
                    prefs.inputFeedback.hapticEnabled isEqualTo true &&
                        prefs.inputFeedback.hapticVibrationMode isEqualTo HapticVibrationMode.USE_VIBRATOR_DIRECTLY &&
                        vibrator != null && vibrator.hasVibrator() &&
                        vibrator.hasAmplitudeControl()
                },
            )
            SwitchPreference(
                prefs.inputFeedback.hapticFeatKeyPress,
                title = stringRes(R.string.pref__input_feedback__haptic_feat_key_press__label),
                summary = stringRes(R.string.pref__input_feedback__any_feat_key_press__summary),
                enabledIf = { prefs.inputFeedback.hapticEnabled isEqualTo true },
            )
            SwitchPreference(
                prefs.inputFeedback.hapticFeatKeyLongPress,
                title = stringRes(R.string.pref__input_feedback__haptic_feat_key_long_press__label),
                summary = stringRes(R.string.pref__input_feedback__any_feat_key_long_press__summary),
                enabledIf = { prefs.inputFeedback.hapticEnabled isEqualTo true },
            )
            SwitchPreference(
                prefs.inputFeedback.hapticFeatKeyRepeatedAction,
                title = stringRes(R.string.pref__input_feedback__haptic_feat_key_repeated_action__label),
                summary = stringRes(R.string.pref__input_feedback__any_feat_key_repeated_action__summary),
                enabledIf = { prefs.inputFeedback.hapticEnabled isEqualTo true },
            )
            SwitchPreference(
                prefs.inputFeedback.hapticFeatGestureSwipe,
                title = stringRes(R.string.pref__input_feedback__haptic_feat_gesture_swipe__label),
                summary = stringRes(R.string.pref__input_feedback__any_feat_gesture_swipe__summary),
                enabledIf = { prefs.inputFeedback.hapticEnabled isEqualTo true },
            )
            SwitchPreference(
                prefs.inputFeedback.hapticFeatGestureMovingSwipe,
                title = stringRes(R.string.pref__input_feedback__haptic_feat_gesture_moving_swipe__label),
                summary = stringRes(R.string.pref__input_feedback__any_feat_gesture_moving_swipe__summary),
                enabledIf = { prefs.inputFeedback.hapticEnabled isEqualTo true },
            )
        }
    }
}
