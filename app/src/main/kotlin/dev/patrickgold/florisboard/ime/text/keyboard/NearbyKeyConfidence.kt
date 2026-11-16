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

package dev.patrickgold.florisboard.ime.text.keyboard

import kotlin.math.sqrt

/**
 * How likely a touch was meant for a key, from how far it landed from the key's center.
 *
 * The distance is measured in half key sizes, so a touch on the key's edge is 1.0 away whatever the key's
 * shape. Keys more than [DistanceLimit] away don't count, and confidence falls off linearly to 0 there.
 */
internal object NearbyKeyConfidence {
    const val DistanceLimit = 1.65f

    /** The confidence for a touch at [pointerX], [pointerY] on the key drawn at these bounds, or null. */
    fun of(pointerX: Float, pointerY: Float, left: Float, top: Float, width: Float, height: Float): Double? {
        val halfWidth = width * 0.5f
        val halfHeight = height * 0.5f
        if (halfWidth <= 0.0f || halfHeight <= 0.0f) {
            return null
        }
        val normalizedX = (pointerX - (left + halfWidth)) / halfWidth
        val normalizedY = (pointerY - (top + halfHeight)) / halfHeight
        val distance = sqrt((normalizedX * normalizedX + normalizedY * normalizedY).toDouble()).toFloat()
        if (distance > DistanceLimit) {
            return null
        }
        return (1.0 - (distance / DistanceLimit).toDouble()).coerceIn(0.0, 1.0)
    }
}
