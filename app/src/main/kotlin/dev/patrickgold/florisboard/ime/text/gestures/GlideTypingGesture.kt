/*
 * Copyright (C) 2025 The FlorisBoard Contributors
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
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.keyboard.TextKey
import dev.patrickgold.florisboard.lib.devtools.flogDebug
import dev.patrickgold.florisboard.lib.util.ViewUtils
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Wrapper class which holds all enums, interfaces and classes for detecting a gesture.
 */
class GlideTypingGesture {
    /**
     * Class which detects swipes based on given [MotionEvent]s. Only supports single-finger swipes
     * and ignores additional pointers provided, if any.
     */
    class Detector(context: Context) {
        private val prefs by FlorisPreferenceStore
        private var pointerData: PointerData = PointerData(mutableListOf(), 0)
        private val keySize = ViewUtils.px2dp(context.resources.getDimension(R.dimen.key_width))
        private val listeners: ArrayList<Listener> = arrayListOf()
        private var pointerId: Int = -1

        companion object {
            private const val MAX_DETECT_TIME = 500
            private const val VELOCITY_THRESHOLD = 0.10 // dp per ms
            private val SWIPE_GESTURE_KEYS = arrayOf(KeyCode.DELETE, KeyCode.SHIFT, KeyCode.SPACE, KeyCode.CJK_SPACE)
        }

        /**
         * Method which evaluates if a given [event] is a gesture.
         *
         * @return whether or not the event was interpreted as part of a gesture.
         */
        fun onTouchEvent(event: MotionEvent, initialKey: TextKey?): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        resetState()
                    }
                    if (pointerId != -1) {
                        // if we already have another pointer, we don't care
                        return false
                    }
                    val pointerIndex = event.actionIndex
                    pointerId = event.getPointerId(pointerIndex)
                    pointerData.apply {
                        positions.add(Position(event.getX(pointerIndex), event.getY(pointerIndex)))
                        startTime = System.currentTimeMillis()
                    }
                    return false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (pointerId != event.getPointerId(event.actionIndex)) {
                        // not our pointer.
                        return false
                    }

                    val pointerIndex = event.findPointerIndex(pointerId)
                    for (i in 0..event.historySize) {
                        val pos = when (i) {
                            event.historySize -> Position(event.getX(pointerIndex), event.getY(pointerIndex))
                            else -> Position(event.getHistoricalX(pointerIndex, i), event.getHistoricalY(pointerIndex, i))
                        }
                        pointerData.positions.add(pos)
                        if (pointerData.isActuallyGesture == null) {
                            // evaluate whether is actually a gesture
                            val dist = ViewUtils.px2dp(pointerData.positions[0].dist(pos))
                            val time = (System.currentTimeMillis() - pointerData.startTime) + 1
                            val thresholdScale = GlideSensitivityPolicy.thresholdScale(prefs.glide.sensitivity.get())
                            val distanceThreshold = keySize * thresholdScale
                            val velocityThreshold = VELOCITY_THRESHOLD * thresholdScale
                            flogDebug { "Distance glided: $dist dp with velocity: ${dist / time} dp/ms" }
                            if (dist > distanceThreshold &&
                                (dist / time) > velocityThreshold &&
                                (initialKey?.computedData?.code !in SWIPE_GESTURE_KEYS)
                            ) {
                                pointerData.isActuallyGesture = true
                                // Let listener know all those points need to be added.
                                pointerData.positions.take(pointerData.positions.size - 1).forEach { point ->
                                    listeners.forEach {
                                        it.onGlideAddPoint(point)
                                    }
                                }
                            } else if (time > MAX_DETECT_TIME) {
                                pointerData.isActuallyGesture = false
                            }

                        }

                        if (pointerData.isActuallyGesture == true) {
                            pointerData.positions.last()
                                .let { point -> listeners.forEach { it.onGlideAddPoint(point) } }
                        }
                    }
                    return pointerData.isActuallyGesture ?: false
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_POINTER_UP -> {
                    if (pointerId != event.getPointerId(event.actionIndex)) {
                        // not our pointer.
                        return false
                    }
                    if (pointerData.isActuallyGesture == true) {
                        listeners.forEach { listener -> listener.onGlideComplete(pointerData) }
                    }
                    resetState()
                    return false
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (pointerData.isActuallyGesture == true) {
                        listeners.forEach { it.onGlideCancelled() }
                    }
                    resetState()
                }
                else -> return false
            }
            return false
        }

        /**
         * Splits the active gesture at the current point: fires `onGlideWordBoundary` to
         * every listener with the points gathered so far, then resets the position list
         * (keeping the pointer id and timing) so that subsequent ACTION_MOVE events feed
         * a fresh gesture for the next word. Used by Flow Through Space when the trace
         * crosses into the space bar mid-word without lifting.
         */
        fun signalWordBoundary() {
            if (pointerId == -1 || pointerData.isActuallyGesture != true) return
            if (pointerData.positions.size < 3) return
            val snapshot = PointerData(
                positions = pointerData.positions.toMutableList(),
                startTime = pointerData.startTime,
                isActuallyGesture = true,
            )
            listeners.forEach { listener -> listener.onGlideWordBoundary(snapshot) }
            // Keep the pointer alive but start a fresh trace from here.
            val lastPoint = pointerData.positions.lastOrNull()
            pointerData.positions.clear()
            if (lastPoint != null) pointerData.positions.add(lastPoint)
            pointerData.startTime = System.currentTimeMillis()
            pointerData.isActuallyGesture = null
        }

        fun registerListener(listener: Listener) {
            listeners.add(listener)
        }

        fun unregisterListener(listener: Listener) {
            listeners.remove(listener)
        }

        private fun resetState() {
            pointerData.apply {
                positions.clear()
                startTime = 0
                isActuallyGesture = null
            }
            pointerId = -1
        }

        data class PointerData(
            val positions: MutableList<Position>,
            var startTime: Long,
            var isActuallyGesture: Boolean? = null,
        )

        data class Position(val x: Float, val y: Float) {
            fun dist(p2: Position): Float {
                return sqrt((p2.x - x).pow(2) + (p2.y - y).pow(2))
            }
        }
    }

    interface Listener {
        /**
         * Called when a gesture is complete.
         */
        fun onGlideComplete(data: Detector.PointerData) {}

        /**
         * Called when a point is added to a gesture.
         * Will not be called before a series of events is detected as a gesture.
         */
        fun onGlideAddPoint(point: Detector.Position) {}

        /**
         * Called to cancel a gesture.
         */
        fun onGlideCancelled() {}

        /**
         * Called when the user crossed the space bar mid-gesture (Flow Through Space).
         * Listeners should treat this as if the user had lifted their finger to commit
         * the current word — the same finger then continues into the next word's trace.
         */
        fun onGlideWordBoundary(data: Detector.PointerData) {}
    }
}
