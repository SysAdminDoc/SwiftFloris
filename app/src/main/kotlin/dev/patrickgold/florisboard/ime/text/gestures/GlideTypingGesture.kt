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
import java.util.LinkedHashMap
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Wrapper class which holds all enums, interfaces and classes for detecting a gesture.
 */
class GlideTypingGesture {
    /** Class which detects one or two independent glides from [MotionEvent]s. */
    class Detector(
        context: Context,
        private val currentTimeMillis: () -> Long = System::currentTimeMillis,
    ) {
        private val prefs by FlorisPreferenceStore
        private val keySize = ViewUtils.px2dp(context.resources.getDimension(R.dimen.key_width))
        private val listeners: ArrayList<Listener> = arrayListOf()
        private val activePointers = LinkedHashMap<Int, ActivePointer>()

        /**
         * Id of the pointer currently being traced, or -1 when none is. When two traces are
         * active, a confirmed trace wins; otherwise the newest unconfirmed pointer is returned
         * so a resting finger cannot block the next glide.
         */
        val tracedPointerId: Int
            get() = activePointers.values.firstOrNull {
                it.data.isActuallyGesture == true
            }?.pointerId
                ?: activePointers.values.lastOrNull {
                    it.data.isActuallyGesture != false
                }?.pointerId
                ?: -1

        companion object {
            private const val MAX_DETECT_TIME = 500
            private const val VELOCITY_THRESHOLD = 0.10 // dp per ms
            private const val MAX_ACTIVE_POINTERS = 2
            private val SWIPE_GESTURE_KEYS = arrayOf(KeyCode.DELETE, KeyCode.SHIFT, KeyCode.SPACE, KeyCode.CJK_SPACE)
        }

        /**
         * Method which evaluates if a given [event] is a gesture.
         *
         * @return whether or not the event was interpreted as part of a gesture.
         */
        fun onTouchEvent(event: MotionEvent, initialKey: TextKey?): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    resetState()
                    addPointer(event, initialKey)
                    return false
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (activePointers.size >= MAX_ACTIVE_POINTERS) {
                        // Keep confirmed traces. If the second slot is only a resting finger,
                        // replace it so a third touch can still become a glide.
                        val inactive = activePointers.values.firstOrNull {
                            it.data.isActuallyGesture != true
                        }
                        if (inactive == null) return false
                        activePointers.remove(inactive.pointerId)
                    }
                    addPointer(event, initialKey)
                    return false
                }
                MotionEvent.ACTION_MOVE -> {
                    // ACTION_MOVE has no pointer index of its own. Resolve every tracked id so
                    // alternating-hand traces never get merged or dropped.
                    activePointers.values.toList().forEach { activePointer ->
                        val pointerIndex = event.findPointerIndex(activePointer.pointerId)
                        if (pointerIndex == -1) return@forEach
                        for (i in 0..event.historySize) {
                            val pos = when (i) {
                                event.historySize -> Position(event.getX(pointerIndex), event.getY(pointerIndex))
                                else -> Position(
                                    event.getHistoricalX(pointerIndex, i),
                                    event.getHistoricalY(pointerIndex, i),
                                )
                            }
                            processPosition(activePointer, pos)
                        }
                    }
                    return activePointers.values.any { it.data.isActuallyGesture == true }
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_POINTER_UP -> {
                    val pointerId = event.getPointerId(event.actionIndex)
                    val activePointer = activePointers.remove(pointerId) ?: return false
                    if (activePointer.data.isActuallyGesture == true) {
                        listeners.forEach { listener ->
                            listener.onGlideComplete(pointerId, activePointer.data.snapshot())
                        }
                    }
                    if (event.actionMasked == MotionEvent.ACTION_UP) resetState()
                    return false
                }
                MotionEvent.ACTION_CANCEL -> {
                    activePointers.values.forEach { activePointer ->
                        if (activePointer.data.isActuallyGesture == true) {
                            listeners.forEach { it.onGlideCancelled(activePointer.pointerId) }
                        }
                    }
                    resetState()
                }
                else -> return false
            }
            return false
        }

        /**
         * Splits the active gesture at the current point. The pointer id is optional for the
         * single-pointer call site and is required when two thumbs are gliding at once.
         */
        fun signalWordBoundary(pointerId: Int = tracedPointerId) {
            val activePointer = activePointers[pointerId] ?: return
            val pointerData = activePointer.data
            if (pointerData.isActuallyGesture != true || pointerData.positions.size < 3) return
            listeners.forEach { listener ->
                listener.onGlideWordBoundary(pointerId, pointerData.snapshot())
            }
            // Keep the pointer alive but start a fresh trace from here.
            val lastPoint = pointerData.positions.lastOrNull()
            pointerData.positions.clear()
            if (lastPoint != null) pointerData.positions.add(lastPoint)
            pointerData.startTime = currentTimeMillis()
            pointerData.isActuallyGesture = null
            pointerData.hasQualifiedBeforeWordBoundary = true
        }

        fun registerListener(listener: Listener) {
            listeners.add(listener)
        }

        fun unregisterListener(listener: Listener) {
            listeners.remove(listener)
        }

        private fun addPointer(event: MotionEvent, initialKey: TextKey?) {
            val pointerIndex = event.actionIndex
            val pointerId = event.getPointerId(pointerIndex)
            activePointers[pointerId] = ActivePointer(
                pointerId = pointerId,
                initialKey = initialKey,
                data = PointerData(
                    positions = mutableListOf(Position(event.getX(pointerIndex), event.getY(pointerIndex))),
                    startTime = currentTimeMillis(),
                    pointerId = pointerId,
                ),
            )
        }

        private fun processPosition(activePointer: ActivePointer, pos: Position) {
            val pointerData = activePointer.data
            pointerData.positions.add(pos)
            if (pointerData.isActuallyGesture == null) {
                if (pointerData.hasQualifiedBeforeWordBoundary) {
                    pointerData.isActuallyGesture = true
                    pointerData.positions
                        .take(pointerData.positions.size - 1)
                        .forEach { point -> emitPoint(activePointer.pointerId, point) }
                } else {
                    val dist = ViewUtils.px2dp(pointerData.positions[0].dist(pos))
                    val time = (currentTimeMillis() - pointerData.startTime) + 1
                    val thresholdScale = GlideSensitivityPolicy.thresholdScale(prefs.glide.sensitivity.get())
                    val distanceThreshold = keySize * thresholdScale
                    val velocityThreshold = VELOCITY_THRESHOLD * thresholdScale
                    flogDebug { "Distance glided: $dist dp with velocity: ${dist / time} dp/ms" }
                    if (dist > distanceThreshold &&
                        (dist / time) > velocityThreshold &&
                        (activePointer.initialKey?.computedData?.code !in SWIPE_GESTURE_KEYS)
                    ) {
                        pointerData.isActuallyGesture = true
                        pointerData.positions
                            .take(pointerData.positions.size - 1)
                            .forEach { point -> emitPoint(activePointer.pointerId, point) }
                    } else if (time > MAX_DETECT_TIME) {
                        pointerData.isActuallyGesture = false
                    }
                }
            }
            if (pointerData.isActuallyGesture == true) {
                emitPoint(activePointer.pointerId, pointerData.positions.last())
            }
        }

        private fun emitPoint(pointerId: Int, point: Position) {
            listeners.forEach { it.onGlideAddPoint(pointerId, point) }
        }

        private fun resetState() {
            activePointers.clear()
        }

        private data class ActivePointer(
            val pointerId: Int,
            val initialKey: TextKey?,
            val data: PointerData,
        )

        private fun PointerData.snapshot(): PointerData = copy(
            positions = positions.toMutableList(),
        )

        data class PointerData(
            val positions: MutableList<Position>,
            var startTime: Long,
            var isActuallyGesture: Boolean? = null,
            var hasQualifiedBeforeWordBoundary: Boolean = false,
            val pointerId: Int = -1,
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

        fun onGlideComplete(pointerId: Int, data: Detector.PointerData) {
            onGlideComplete(data)
        }

        /**
         * Called when a point is added to a gesture.
         * Will not be called before a series of events is detected as a gesture.
         */
        fun onGlideAddPoint(point: Detector.Position) {}

        fun onGlideAddPoint(pointerId: Int, point: Detector.Position) {
            onGlideAddPoint(point)
        }

        /**
         * Called to cancel a gesture.
         */
        fun onGlideCancelled() {}

        fun onGlideCancelled(pointerId: Int) {
            onGlideCancelled()
        }

        /**
         * Called when the user crossed the space bar mid-gesture (Flow Through Space).
         * Listeners should treat this as if the user had lifted their finger to commit
         * the current word — the same finger then continues into the next word's trace.
         */
        fun onGlideWordBoundary(data: Detector.PointerData) {}

        fun onGlideWordBoundary(pointerId: Int, data: Detector.PointerData) {
            onGlideWordBoundary(data)
        }
    }
}
