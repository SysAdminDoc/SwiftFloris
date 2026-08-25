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
 * Dropping expired points bounds the buffer by the trail duration instead of by
 * session length. Note that this is not simply a no-op on the rendered output.
 * The renderer finds the first point inside the window and then draws every
 * point after it, and the buffer holds traces from different pointers, so it is
 * not globally ordered by timestamp: an expired point that sits after a
 * surviving one used to be drawn, joining two traces with a stray line. Pruning
 * removes those too. The point the renderer starts from is always kept, because
 * this uses the same window comparison and runs no later than the frame does.
 */
object GlideTrailRetention {

    /**
     * Whether a point stamped [timestampMillis] is still inside the trail
     * window at [nowMillis].
     *
     * Uses the same comparison the renderer uses to find its first visible
     * point.
     */
    fun isWithinTrailWindow(
        timestampMillis: Long,
        nowMillis: Long,
        trailDurationMillis: Long,
    ): Boolean {
        return nowMillis - timestampMillis <= trailDurationMillis
    }

    /**
     * Drops the entries of [points] that the trail window has already expired,
     * in place, preserving the order of what remains.
     *
     * In place because the caller holds a `SnapshotStateList` that Compose is
     * observing; rebuilding it would discard that identity.
     */
    fun <T> dropExpired(
        points: MutableList<T>,
        nowMillis: Long,
        trailDurationMillis: Long,
        timestampOf: (T) -> Long,
    ) {
        points.removeAll {
            !isWithinTrailWindow(timestampOf(it), nowMillis, trailDurationMillis)
        }
    }
}
