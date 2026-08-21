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

package dev.patrickgold.florisboard.ime.window

import android.content.Context
import android.graphics.Rect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.CancellationException

/** A vertical fold or hinge reported in the IME root window's pixel coordinates. */
internal data class ImeVerticalHingeBounds(
    val leftPx: Float,
    val topPx: Float,
    val rightPx: Float,
    val bottomPx: Float,
) {
    fun translatedBy(dx: Float, dy: Float): ImeVerticalHingeBounds {
        return copy(
            leftPx = leftPx + dx,
            topPx = topPx + dy,
            rightPx = rightPx + dx,
            bottomPx = bottomPx + dy,
        )
    }

    companion object {
        fun from(bounds: Rect): ImeVerticalHingeBounds? {
            if (bounds.bottom <= bounds.top) return null
            return ImeVerticalHingeBounds(
                leftPx = bounds.left.toFloat(),
                topPx = bounds.top.toFloat(),
                rightPx = bounds.right.toFloat(),
                bottomPx = bounds.bottom.toFloat(),
            )
        }
    }
}

internal val LocalImeVerticalHingeBounds = staticCompositionLocalOf<ImeVerticalHingeBounds?> { null }

@Composable
internal fun rememberImeVerticalHingeBounds(context: Context): ImeVerticalHingeBounds? {
    val tracker = remember(context) { WindowInfoTracker.getOrCreate(context) }
    return produceState<ImeVerticalHingeBounds?>(initialValue = null, context, tracker) {
        try {
            tracker.windowLayoutInfo(context).collect { windowLayoutInfo ->
                value = windowLayoutInfo.displayFeatures
                    .asSequence()
                    .filterIsInstance<FoldingFeature>()
                    .filter { feature -> feature.orientation == FoldingFeature.Orientation.VERTICAL }
                    .mapNotNull { feature -> ImeVerticalHingeBounds.from(feature.bounds) }
                    .minWithOrNull(
                        compareBy<ImeVerticalHingeBounds> { it.rightPx - it.leftPx }
                            .thenBy { it.leftPx },
                    )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            value = null
        }
    }.value
}
