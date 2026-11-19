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

package dev.patrickgold.florisboard.ime.input

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.VibrationAttributes
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.FlorisApplication
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowVibrator

/**
 * Key vibration against the phone's own touch vibration switch (#26).
 *
 * With that switch off, "Always vibrate, regardless of system settings" asked for a vibration
 * that carried no attributes. Android files those as touch feedback and drops them while the
 * switch is off (`ignored_for_settings` in `dumpsys vibrator_manager` on an Android 16 emulator),
 * so the option did nothing on Android 13 and later. It now asks as physical emulation, which no
 * haptic feedback switch turns off. Following the switch, the default, still stays quiet.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class InputFeedbackSystemSwitchTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs by FlorisPreferenceStore
    private var changedPrefs = false

    private lateinit var ims: InputMethodService
    private lateinit var controller: InputFeedbackController
    private lateinit var vibrator: ShadowVibrator

    @Before
    fun setUp() {
        ims = Robolectric.buildService(InputMethodService::class.java).create().get()
        val systemVibrator: Vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator
        vibrator = shadowOf(systemVibrator)
        vibrator.setHasVibrator(true)
        vibrator.setHasAmplitudeControl(true)
        controller = InputFeedbackController.new(ims)
    }

    @After
    fun tearDown() {
        controller.dispose()
        Settings.System.putInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1)
        if (changedPrefs) {
            // The preference model outlives this test's app, so the default goes back.
            savePref { prefs.inputFeedback.hapticActivationMode.set(InputFeedbackActivationMode.RESPECT_SYSTEM_SETTINGS) }
        }
    }

    @Test
    fun alwaysVibrateStillVibratesWithThePhonesTouchVibrationOff() {
        setTouchVibration(on = false)
        setHapticMode(InputFeedbackActivationMode.IGNORE_SYSTEM_SETTINGS)

        controller.updateSystemPrefsState()
        controller.keyPress()

        val attributes = awaitVibrationAttributes()
        withClue("usage of the key press vibration") {
            attributes?.usage shouldBe VibrationAttributes.USAGE_PHYSICAL_EMULATION
        }
    }

    @Test
    fun followingThePhoneStaysQuietWhileItsTouchVibrationIsOff() {
        setTouchVibration(on = false)
        setHapticMode(InputFeedbackActivationMode.RESPECT_SYSTEM_SETTINGS)

        controller.updateSystemPrefsState()
        controller.keyPress()

        // Nothing is launched on this path, so a short wait is enough to catch a stray call.
        Thread.sleep(300)
        vibrator.vibrationAttributesFromLastVibration shouldBe null
    }

    @Test
    fun followingThePhoneVibratesAsTouchFeedbackWhileItsTouchVibrationIsOn() {
        setTouchVibration(on = true)
        setHapticMode(InputFeedbackActivationMode.RESPECT_SYSTEM_SETTINGS)

        controller.updateSystemPrefsState()
        controller.keyPress()

        val attributes = awaitVibrationAttributes()
        withClue("a vibration was sent") { attributes shouldNotBe null }
        // Left to Android to classify, so the phone's touch vibration strength still applies.
        attributes?.usage shouldNotBe VibrationAttributes.USAGE_PHYSICAL_EMULATION
    }

    private fun setTouchVibration(on: Boolean) {
        Settings.System.putInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, if (on) 1 else 0)
    }

    private fun setHapticMode(mode: InputFeedbackActivationMode) {
        savePref { prefs.inputFeedback.hapticActivationMode.set(mode) }
    }

    private fun awaitVibrationAttributes(): VibrationAttributes? {
        waitUntil { vibrator.vibrationAttributesFromLastVibration != null }
        return vibrator.vibrationAttributesFromLastVibration as VibrationAttributes?
    }

    private fun savePref(write: suspend () -> Result<*>) {
        changedPrefs = true
        // The app loads the store in the background and swaps it in when done, which would drop a
        // value set before then.
        waitUntil { (context as FlorisApplication).preferenceStoreLoaded.value }
        // JetPref saves by renaming a temp file over the old one, which a Windows JVM refuses once
        // the file exists, so the saved copy goes first.
        val saved = File(context.dataDir, "jetpref_datastore/florisboard-app-prefs.jetpref")
        check(saved.delete() || !saved.exists()) { "Couldn't delete the saved preferences" }
        runBlocking { withTimeout(10_000) { write().getOrThrow() } }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition()) {
            check(System.nanoTime() < deadline) { "Timed out waiting" }
            Thread.sleep(10)
        }
    }
}
