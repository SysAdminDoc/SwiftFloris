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

package dev.patrickgold.florisboard.ime.smoke

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dev.patrickgold.florisboard.BuildConfig
import dev.patrickgold.florisboard.ime.text.gestures.GlideTypingGesture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImeEndToEndSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val imeId = "${BuildConfig.APPLICATION_ID}/dev.patrickgold.florisboard.FlorisImeService"
    private var previousImeId: String? = null
    private var activity: ImeSmokeHostActivity? = null

    @Before
    fun setUp() {
        previousImeId = shell("settings get secure default_input_method").trim().takeUnless { it == "null" }
        shell("ime enable $imeId")
        shell("ime set $imeId")
    }

    @After
    fun tearDown() {
        activity?.finish()
        previousImeId?.takeIf { it.isNotBlank() }?.let { shell("ime set $it") }
    }

    @Test
    fun enableShowTypeCommitAndGlideSmoke() {
        val host = launchHostActivity()
        instrumentation.runOnMainSync { host.focusAndShowIme() }

        waitUntil("SwiftFloris IME became active") {
            shell("settings get secure default_input_method").trim() == imeId
        }
        waitUntil("SwiftFloris IME window became visible") {
            val state = shell("dumpsys input_method")
            imeId in state && ("mInputShown=true" in state || "mIsInputViewShown=true" in state)
        }

        shell("input text helo")
        waitUntil("typed text committed into host editor") {
            host.currentText() == "helo"
        }
        device.pressKeyCode(KeyEvent.KEYCODE_SPACE)
        waitUntil("space key committed through active input path") {
            host.currentText() == "helo "
        }

        runSyntheticGlidePath()
    }

    private fun launchHostActivity(): ImeSmokeHostActivity {
        val testContext = instrumentation.context
        val intent = Intent().apply {
            setClassName(testContext.packageName, ImeSmokeHostActivity::class.java.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return (instrumentation.startActivitySync(intent) as ImeSmokeHostActivity).also {
            activity = it
        }
    }

    private fun ImeSmokeHostActivity.currentText(): String {
        lateinit var value: String
        instrumentation.runOnMainSync {
            value = editText.text.toString()
        }
        return value
    }

    private fun runSyntheticGlidePath() {
        val detector = GlideTypingGesture.Detector(instrumentation.targetContext)
        val listener = CountingGlideListener()
        detector.registerListener(listener)
        try {
            val downTime = SystemClock.uptimeMillis()
            sendGlideEvent(detector, downTime, downTime, MotionEvent.ACTION_DOWN, 120f, 120f)
            sendGlideEvent(detector, downTime, downTime + 16L, MotionEvent.ACTION_MOVE, 220f, 140f)
            sendGlideEvent(detector, downTime, downTime + 32L, MotionEvent.ACTION_MOVE, 340f, 180f)
            sendGlideEvent(detector, downTime, downTime + 48L, MotionEvent.ACTION_UP, 460f, 200f)

            assertEquals("Synthetic glide should complete once", 1, listener.completedCount)
            assertTrue("Synthetic glide should emit trail points", listener.addedPoints > 0)
        } finally {
            detector.unregisterListener(listener)
        }
    }

    private fun sendGlideEvent(
        detector: GlideTypingGesture.Detector,
        downTime: Long,
        eventTime: Long,
        action: Int,
        x: Float,
        y: Float,
    ) {
        val event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
        try {
            detector.onTouchEvent(event, initialKey = null)
        } finally {
            event.recycle()
        }
    }

    private fun waitUntil(label: String, timeoutMillis: Long = 7_500L, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        while (SystemClock.elapsedRealtime() < deadline) {
            if (predicate()) return
            SystemClock.sleep(100L)
        }
        error("Timed out waiting for $label")
    }

    private fun shell(command: String): String {
        return device.executeShellCommand(command)
    }

    private class CountingGlideListener : GlideTypingGesture.Listener {
        var completedCount = 0
            private set
        var addedPoints = 0
            private set

        override fun onGlideComplete(data: GlideTypingGesture.Detector.PointerData) {
            completedCount += 1
        }

        override fun onGlideAddPoint(point: GlideTypingGesture.Detector.Position) {
            addedPoints += 1
        }
    }
}
