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
 * Decides which points of a finished glide trail are still worth keeping while
 * it fades out.
 *
 * The fade buffer is shared by every pointer, so a finished trace is appended
 * rather than swapped in: two fingers that lift together both have to keep
 * fading. Appending alone never released anything, so the buffer grew for the
 * whole keyboard session, and the renderer walks it from index 0 on every frame
 * looking for the first point still inside the trail window. Both the retained
 * memory and the per-frame scan therefore grew with every word ever glided.
 *
 * Dropping points the renderer would skip anyway keeps concurrent fades intact
 * and bounds the buffer by the trail duration instead of by session length.
 */
object GlideTrailRetention {

    /**
     * Whether a point stamped [timestampMillis] is still inside the trail
     * window at [nowMillis].
     *
     * Uses the same comparison the renderer uses to find its first visible
     * point, so pruning can never remove something that would still be drawn.
     */
    fun isWithinTrailWindow(
        timestampMillis: Long,
        nowMillis: Long,
        trailDurationMillis: Long,
    ): Boolean {
        return nowMillis - timestampMillis <= trailDurationMillis
    }

    /**
     * Returns [points] without the entries the trail window has already
     * expired, preserving order.
     */
    fun <T> prune(
        points: List<T>,
        nowMillis: Long,
        trailDurationMillis: Long,
        timestampOf: (T) -> Long,
    ): List<T> {
        return points.filter {
            isWithinTrailWindow(timestampOf(it), nowMillis, trailDurationMillis)
        }
    }
}
