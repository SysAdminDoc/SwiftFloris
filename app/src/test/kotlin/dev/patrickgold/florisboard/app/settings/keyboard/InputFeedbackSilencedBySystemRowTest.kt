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

package dev.patrickgold.florisboard.app.settings.keyboard

import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.FlorisApplication
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.AppTheme
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.apptheme.FlorisAppTheme
import dev.patrickgold.florisboard.ime.input.InputFeedbackActivationMode
import dev.patrickgold.florisboard.screenshot.RoborazziHostActivity
import dev.patrickgold.jetpref.datastore.ui.ProvideDefaultDialogPrefStrings
import io.kotest.matchers.shouldBe
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.florisboard.lib.compose.ProvideLocalizedResources
import org.florisboard.lib.compose.stringRes
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Sounds & Vibration says when the phone's own switch is what keeps the keyboard quiet (#26).
 *
 * The keyboard follows the phone's touch sounds and touch vibration by default. On a Samsung those
 * are the Touch interactions switches, not the volume sliders, so someone with both off turned on
 * every sound and vibration option here and still got nothing, with no hint why. A row now names
 * the switch and, tapped, sets the keyboard to play or vibrate regardless of it.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xxhdpi", sdk = [35])
class InputFeedbackSilencedBySystemRowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<RoborazziHostActivity>()

    private val prefs by FlorisPreferenceStore

    @Before
    fun waitForPreferenceStore() {
        val app = composeRule.activity.application as FlorisApplication
        composeRule.waitUntil(timeoutMillis = 10_000) { app.preferenceStoreLoaded.value }
        allowPreferenceSave()
    }

    @After
    fun restore() {
        setPhoneSwitches(sound = true, vibration = true)
        allowPreferenceSave()
        runBlocking {
            withTimeout(10_000) {
                prefs.inputFeedback.audioActivationMode.set(InputFeedbackActivationMode.RESPECT_SYSTEM_SETTINGS)
            }
        }
        allowPreferenceSave()
        runBlocking {
            withTimeout(10_000) {
                prefs.inputFeedback.hapticActivationMode.set(InputFeedbackActivationMode.RESPECT_SYSTEM_SETTINGS)
            }
        }
    }

    @Test
    fun touchSoundsOffOffersToPlayKeySoundsAnyway() {
        setPhoneSwitches(sound = false, vibration = true)
        composeRule.setContent { Frame { InputFeedbackScreen() } }
        val row = composeRule.activity.getString(R.string.pref__input_feedback__audio_silenced_by_system__label)
        val vibrateRow = composeRule.activity.getString(R.string.pref__input_feedback__haptic_silenced_by_system__label)

        composeRule.onNodeWithText(vibrateRow).assertDoesNotExist()
        composeRule.onNodeWithText(row).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            prefs.inputFeedback.audioActivationMode.get() == InputFeedbackActivationMode.IGNORE_SYSTEM_SETTINGS
        }

        composeRule.onNodeWithText(row).assertDoesNotExist()
        prefs.inputFeedback.hapticActivationMode.get() shouldBe InputFeedbackActivationMode.RESPECT_SYSTEM_SETTINGS
    }

    @Test
    fun touchVibrationOffOffersToVibrateAnyway() {
        setPhoneSwitches(sound = true, vibration = false)
        composeRule.setContent { Frame { InputFeedbackScreen() } }
        val row = composeRule.activity.getString(R.string.pref__input_feedback__haptic_silenced_by_system__label)
        val soundRow = composeRule.activity.getString(R.string.pref__input_feedback__audio_silenced_by_system__label)

        composeRule.onNodeWithText(soundRow).assertDoesNotExist()
        composeRule.onNodeWithText(row).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            prefs.inputFeedback.hapticActivationMode.get() == InputFeedbackActivationMode.IGNORE_SYSTEM_SETTINGS
        }

        composeRule.onNodeWithText(row).assertDoesNotExist()
        prefs.inputFeedback.audioActivationMode.get() shouldBe InputFeedbackActivationMode.RESPECT_SYSTEM_SETTINGS
    }

    @Test
    fun noRowWhileThePhonesSwitchesAreOn() {
        setPhoneSwitches(sound = true, vibration = true)
        composeRule.setContent { Frame { InputFeedbackScreen() } }

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.pref__input_feedback__audio_silenced_by_system__label),
        ).assertDoesNotExist()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.pref__input_feedback__haptic_silenced_by_system__label),
        ).assertDoesNotExist()
    }

    private fun setPhoneSwitches(sound: Boolean, vibration: Boolean) {
        val resolver = composeRule.activity.contentResolver
        Settings.System.putInt(resolver, Settings.System.SOUND_EFFECTS_ENABLED, if (sound) 1 else 0)
        Settings.System.putInt(resolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, if (vibration) 1 else 0)
    }

    /** JetPref saves by renaming a temp file over the old one, which a Windows JVM refuses once it exists. */
    private fun allowPreferenceSave() {
        val saved = File(composeRule.activity.dataDir, "jetpref_datastore/florisboard-app-prefs.jetpref")
        check(saved.delete() || !saved.exists()) { "Couldn't delete the saved preferences" }
    }
}

@Composable
private fun Frame(content: @Composable () -> Unit) {
    ProvideLocalizedResources(resourcesContext = LocalContext.current, appName = R.string.app_name) {
        FlorisAppTheme(theme = AppTheme.LIGHT) {
            val navController = rememberNavController()
            CompositionLocalProvider(LocalNavController provides navController) {
                ProvideDefaultDialogPrefStrings(
                    confirmLabel = stringRes(R.string.action__ok),
                    dismissLabel = stringRes(R.string.action__cancel),
                    neutralLabel = stringRes(R.string.action__default),
                ) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        content()
                    }
                }
            }
        }
    }
}
