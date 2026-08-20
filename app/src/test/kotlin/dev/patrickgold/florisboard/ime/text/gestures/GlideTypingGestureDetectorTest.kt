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

    /**
     * Guards the handover rule rather than the old bug: a confirmed glide keeps the detector, so
     * the pointer-down handover that lets a resting finger step aside cannot also let a thumb
     * landing mid-swipe steal the trace. Written without the `isActuallyGesture` condition, the
     * handover fails this test.
     */
    @Test
    fun glideSurvivesASecondFingerLandingMidTrace() {
        var now = 1_000L
        val detector = GlideTypingGesture.Detector(context) { now }
        val listener = RecordingGlideListener()
        detector.registerListener(listener)

        // Finger 0 starts a real glide.
        detector.onTouchEvent(down(pointerId = 0, x = 0f, y = 0f), initialKey = null)
        now += 20L
        detector.onTouchEvent(move(0 to Pt(220f, 0f)), initialKey = null) shouldBe true

        // A thumb lands on the keyboard. The glide must keep tracing finger 0.
        detector.onTouchEvent(pointerDown(pointers = listOf(0 to Pt(220f, 0f), 1 to Pt(10f, 300f)), actionIndex = 1), initialKey = null)
        detector.tracedPointerId shouldBe 0

        now += 20L
        detector.onTouchEvent(move(0 to Pt(320f, 0f), 1 to Pt(10f, 300f)), initialKey = null) shouldBe true

        // The thumb lifts. That is not the traced pointer, so nothing completes.
        detector.onTouchEvent(pointerUp(pointers = listOf(0 to Pt(320f, 0f), 1 to Pt(10f, 300f)), actionIndex = 1), initialKey = null)
        listener.completions shouldBe 0

        detector.onTouchEvent(up(pointerId = 0, x = 320f, y = 0f), initialKey = null)
        listener.completions shouldBe 1
    }

    @Test
    fun aFingerAlreadyRestingOnTheKeyboardDoesNotBlockTheNextGlide() {
        var now = 1_000L
        val detector = GlideTypingGesture.Detector(context) { now }
        val listener = RecordingGlideListener()
        detector.registerListener(listener)

        // A finger rests on the keys and never moves, so it is never classified as a gesture.
        detector.onTouchEvent(down(pointerId = 0, x = 10f, y = 300f), initialKey = null)

        // The other hand glides. This used to be ignored outright, because the resting pointer
        // still owned the detector and ACTION_MOVE resolved the pointer through actionIndex.
        detector.onTouchEvent(pointerDown(pointers = listOf(0 to Pt(10f, 300f), 1 to Pt(0f, 0f)), actionIndex = 1), initialKey = null)
        detector.tracedPointerId shouldBe 1

        now += 20L
        detector.onTouchEvent(move(0 to Pt(10f, 300f), 1 to Pt(220f, 0f)), initialKey = null) shouldBe true
        now += 20L
        detector.onTouchEvent(move(0 to Pt(10f, 300f), 1 to Pt(320f, 0f)), initialKey = null) shouldBe true

        detector.onTouchEvent(pointerUp(pointers = listOf(0 to Pt(10f, 300f), 1 to Pt(320f, 0f)), actionIndex = 1), initialKey = null)
        listener.completions shouldBe 1
    }

    @Test
    fun aMoveForAPointerThatIsNotTracedIsIgnoredRatherThanCrashing() {
        var now = 1_000L
        val detector = GlideTypingGesture.Detector(context) { now }
        detector.registerListener(RecordingGlideListener())

        // No pointer has gone down, so findPointerIndex would return -1 for anything.
        detector.onTouchEvent(move(3 to Pt(50f, 50f)), initialKey = null) shouldBe false
        detector.tracedPointerId shouldBe -1

        detector.onTouchEvent(down(pointerId = 0, x = 0f, y = 0f), initialKey = null)
        now += 20L
        // A move that carries only a different pointer must not be read as the traced one.
        detector.onTouchEvent(move(7 to Pt(220f, 0f)), initialKey = null) shouldBe false
    }
}

private data class Pt(val x: Float, val y: Float)

private fun down(pointerId: Int, x: Float, y: Float): MotionEvent =
    multiPointerEvent(MotionEvent.ACTION_DOWN, listOf(pointerId to Pt(x, y)), actionIndex = 0)

private fun up(pointerId: Int, x: Float, y: Float): MotionEvent =
    multiPointerEvent(MotionEvent.ACTION_UP, listOf(pointerId to Pt(x, y)), actionIndex = 0)

private fun move(vararg pointers: Pair<Int, Pt>): MotionEvent =
    multiPointerEvent(MotionEvent.ACTION_MOVE, pointers.toList(), actionIndex = 0)

private fun pointerDown(pointers: List<Pair<Int, Pt>>, actionIndex: Int): MotionEvent =
    multiPointerEvent(MotionEvent.ACTION_POINTER_DOWN, pointers, actionIndex)

private fun pointerUp(pointers: List<Pair<Int, Pt>>, actionIndex: Int): MotionEvent =
    multiPointerEvent(MotionEvent.ACTION_POINTER_UP, pointers, actionIndex)

/**
 * Builds a real multi-pointer [MotionEvent]. The single-pointer `MotionEvent.obtain` overload
 * cannot express the case these tests exist for: a pointer whose id differs from its index.
 */
private fun multiPointerEvent(
    action: Int,
    pointers: List<Pair<Int, Pt>>,
    actionIndex: Int,
): MotionEvent {
    val properties = pointers.map { (id, _) ->
        MotionEvent.PointerProperties().also {
            it.id = id
            it.toolType = MotionEvent.TOOL_TYPE_FINGER
        }
    }.toTypedArray()
    val coords = pointers.map { (_, point) ->
        MotionEvent.PointerCoords().also {
            it.x = point.x
            it.y = point.y
            it.pressure = 1f
            it.size = 1f
        }
    }.toTypedArray()
    return MotionEvent.obtain(
        0L,
        0L,
        action or (actionIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
        pointers.size,
        properties,
        coords,
        0,
        0,
        1f,
        1f,
        0,
        0,
        0,
        0,
    )
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
