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

package dev.patrickgold.florisboard.lib.compose

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

internal object DynamicFontScale {
    private const val ExpandedLayoutThreshold = 1.30f
    private const val FixedGeometryMaxRenderedScale = 1.35f

    fun maxLines(
        compact: Int,
        expanded: Int,
        fontScale: Float,
    ): Int {
        val compactLines = compact.coerceAtLeast(1)
        val expandedLines = expanded.coerceAtLeast(compactLines)
        return if (fontScale >= ExpandedLayoutThreshold) expandedLines else compactLines
    }

    fun minHeightDp(
        compact: Float,
        expanded: Float,
        fontScale: Float,
    ): Float {
        val compactHeight = compact.coerceAtLeast(0f)
        val expandedHeight = expanded.coerceAtLeast(compactHeight)
        return if (fontScale >= ExpandedLayoutThreshold) expandedHeight else compactHeight
    }

    fun fixedGeometrySp(
        baseSp: Float,
        fontScale: Float,
        maxRenderedScale: Float = FixedGeometryMaxRenderedScale,
    ): TextUnit {
        val safeBaseSp = baseSp.coerceAtLeast(1f)
        val safeFontScale = fontScale.takeIf { it > 0f } ?: 1f
        val safeMaxRenderedScale = maxRenderedScale.coerceAtLeast(1f)
        val compensatedSp = if (safeFontScale > safeMaxRenderedScale) {
            safeBaseSp * safeMaxRenderedScale / safeFontScale
        } else {
            safeBaseSp
        }
        return compensatedSp.sp
    }
}
