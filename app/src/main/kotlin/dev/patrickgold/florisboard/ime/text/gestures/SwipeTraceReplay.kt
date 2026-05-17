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

/**
 * Keyboard rectangle used to replay normalized swipe traces against a concrete rendered layout.
 */
data class SwipeTraceReplayBounds(
    val widthPx: Float,
    val heightPx: Float,
    val leftPx: Float = 0f,
    val topPx: Float = 0f,
) {
    init {
        require(widthPx.isFinite() && widthPx > 0f) { "widthPx must be finite and positive" }
        require(heightPx.isFinite() && heightPx > 0f) { "heightPx must be finite and positive" }
        require(leftPx.isFinite()) { "leftPx must be finite" }
        require(topPx.isFinite()) { "topPx must be finite" }
    }
}

/**
 * Converts FUTO-style normalized swipe traces into the pointer-data shape consumed by glide classifiers.
 */
object SwipeTraceReplay {

    fun toPointerData(
        record: SwipeTraceRecord,
        bounds: SwipeTraceReplayBounds,
    ): GlideTypingGesture.Detector.PointerData {
        val positions = record.samples.map { sample ->
            GlideTypingGesture.Detector.Position(
                x = bounds.leftPx + sample.x * bounds.widthPx,
                y = bounds.topPx + sample.y * bounds.heightPx,
            )
        }.toMutableList()
        return GlideTypingGesture.Detector.PointerData(
            positions = positions,
            startTime = record.samples.first().t,
            isActuallyGesture = true,
        )
    }
}
