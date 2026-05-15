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

package dev.patrickgold.florisboard.ime.handwriting

/**
 * ROADMAP §7 Next-4.2 — single stylus stroke = a polyline traced by one
 * uninterrupted pen-down → pen-up gesture.
 *
 * Coordinates are in *view-local* pixels at the moment of capture. The
 * stroke recogniser is responsible for normalizing to whatever input
 * space its backing model expects.
 *
 * Timing carries the (relative) milliseconds since stroke start. This
 * preserves the dynamic-time information ML Kit Digital Ink uses to
 * disambiguate visually similar characters (e.g. `o` vs `0` vs unfilled
 * circle) by sampling the user's pen-speed profile.
 */
data class Stroke(
    val points: List<StrokePoint>,
) {
    init {
        require(points.size >= 2) { "Stroke must have at least two points (got ${points.size})" }
    }

    val durationMs: Long
        get() = points.last().timestampMs - points.first().timestampMs

    val isInstantaneous: Boolean
        get() = durationMs == 0L
}

data class StrokePoint(
    val x: Float,
    val y: Float,
    val timestampMs: Long,
) {
    init {
        require(timestampMs >= 0) { "timestampMs must be non-negative; was $timestampMs" }
    }
}
