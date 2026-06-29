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

import dev.patrickgold.florisboard.ime.keyboard.KeyboardMode
import dev.patrickgold.florisboard.lib.FlorisRect

internal object TextKeyboardLayoutPolicy {
    const val MinEffectiveRowCount = 4
    const val MaxEffectiveRowCount = 6

    private const val LegacyThreeRowHeightScale = 1.12f

    fun effectiveRowCount(rowCount: Int): Int {
        return rowCount.coerceIn(MinEffectiveRowCount, MaxEffectiveRowCount)
    }

    fun desiredTouchHeightPx(
        mode: KeyboardMode,
        rowCount: Int,
        keyboardHeightPx: Float,
        rowBaseHeightPx: Float,
    ): Float {
        if (!mode.usesDynamicTextRowHeight()) {
            return rowBaseHeightPx
        }
        if (!keyboardHeightPx.isFinite() || keyboardHeightPx <= 0.0f) {
            return rowBaseHeightPx
        }
        if (!rowBaseHeightPx.isFinite() || rowBaseHeightPx <= 0.0f) {
            return keyboardHeightPx / rowCount.coerceAtLeast(1).toFloat()
        }
        val divisor = if (rowCount < MinEffectiveRowCount) {
            rowCount.coerceAtLeast(1)
        } else {
            effectiveRowCount(rowCount)
        }
        return (keyboardHeightPx / divisor.toFloat())
            .coerceAtMost(rowBaseHeightPx * LegacyThreeRowHeightScale)
    }

    fun popupBounds(
        keyVisibleBounds: FlorisRect,
        desiredVisibleBounds: FlorisRect,
        isLandscape: Boolean,
    ): FlorisRect {
        val keyPopupWidth: Float
        val keyPopupHeight: Float
        when {
            isLandscape -> {
                keyPopupWidth = desiredVisibleBounds.width * 1.0f
                keyPopupHeight = desiredVisibleBounds.height * 3.0f
            }
            else -> {
                keyPopupWidth = desiredVisibleBounds.width * 1.1f
                keyPopupHeight = desiredVisibleBounds.height * 2.5f
            }
        }
        val keyPopupDiffX = (keyVisibleBounds.width - keyPopupWidth) / 2.0f
        return FlorisRect.new().apply {
            left = keyVisibleBounds.left + keyPopupDiffX
            top = keyVisibleBounds.bottom - keyPopupHeight
            right = left + keyPopupWidth
            bottom = top + keyPopupHeight
        }
    }

    private fun KeyboardMode.usesDynamicTextRowHeight(): Boolean {
        return this == KeyboardMode.CHARACTERS ||
            this == KeyboardMode.NUMERIC_ADVANCED ||
            this == KeyboardMode.SYMBOLS ||
            this == KeyboardMode.SYMBOLS2
    }
}
