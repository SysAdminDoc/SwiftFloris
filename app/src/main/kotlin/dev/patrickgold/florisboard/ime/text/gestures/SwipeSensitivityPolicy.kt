/*
 * Copyright (C) 2026 The FlorisBoard Contributors
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

import kotlin.math.abs

object SwipeSensitivityPolicy {
    const val DEFAULT_SENSITIVITY = 50

    fun thresholdScale(sensitivity: Int): Double {
        val clamped = sensitivity.coerceIn(0, 100)
        return 1.5 - (clamped / 100.0)
    }

    fun distanceThreshold(baseDistance: Double, sensitivity: Int): Double {
        return baseDistance * thresholdScale(sensitivity)
    }

    fun velocityThreshold(baseVelocity: Double, sensitivity: Int): Double {
        return baseVelocity * thresholdScale(sensitivity)
    }

    fun unitWidth(baseDistance: Double, sensitivity: Int): Double {
        return distanceThreshold(baseDistance, sensitivity) / 4.0
    }

    fun shouldTriggerTouchMove(
        relDiffX: Double,
        relDiffY: Double,
        baseDistance: Double,
        sensitivity: Int,
    ): Boolean {
        val threshold = distanceThreshold(baseDistance, sensitivity) / 2.0
        return abs(relDiffX) > threshold || abs(relDiffY) > threshold
    }

    fun shouldTriggerTouchUp(
        absDiffX: Double,
        absDiffY: Double,
        velocityX: Double,
        velocityY: Double,
        baseDistance: Double,
        baseVelocity: Double,
        sensitivity: Int,
    ): Boolean {
        val distance = distanceThreshold(baseDistance, sensitivity)
        val velocity = velocityThreshold(baseVelocity, sensitivity)
        return (abs(absDiffX) > distance || abs(absDiffY) > distance) &&
            (abs(velocityX) > velocity || abs(velocityY) > velocity)
    }
}
