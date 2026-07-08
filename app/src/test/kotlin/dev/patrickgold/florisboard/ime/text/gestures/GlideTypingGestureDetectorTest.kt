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

package dev.patrickgold.florisboard.ime.text.gestures

import android.content.Context
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class GlideTypingGestureDetectorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun flowThroughSpaceContinuationSurvivesSlowShortTrailingWord() {
        var now = 1_000L
        val detector = GlideTypingGesture.Detector(context) { now }
        val listener = RecordingGlideListener()
        detector.registerListener(listener)

        detector.onTouchEvent(motionEvent(MotionEvent.ACTION_DOWN, x = 0f, y = 0f), initialKey = null)
        now += 20L
        detector.onTouchEvent(motionEvent(MotionEvent.ACTION_MOVE, x = 220f, y = 0f), initialKey = null) shouldBe true
        now += 20L
        detector.onTouchEvent(motionEvent(MotionEvent.ACTION_MOVE, x = 240f, y = 0f), initialKey = null) shouldBe true

        detector.signalWordBoundary()
        listener.boundaries shouldBe 1

        now += 700L
        detector.onTouchEvent(motionEvent(MotionEvent.ACTION_MOVE, x = 242f, y = 1f), initialKey = null) shouldBe true
        detector.onTouchEvent(motionEvent(MotionEvent.ACTION_UP, x = 242f, y = 1f), initialKey = null)

        listener.completions shouldBe 1
    }
}

private class RecordingGlideListener : GlideTypingGesture.Listener {
    var boundaries = 0
    var completions = 0

    override fun onGlideWordBoundary(data: GlideTypingGesture.Detector.PointerData) {
        boundaries += 1
    }

    override fun onGlideComplete(data: GlideTypingGesture.Detector.PointerData) {
        completions += 1
    }
}

private fun motionEvent(action: Int, x: Float, y: Float): MotionEvent {
    return MotionEvent.obtain(0L, 0L, action, x, y, 0)
}
