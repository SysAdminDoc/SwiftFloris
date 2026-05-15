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

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * ROADMAP §7 Next-7.2a — split-keyboard renderer key-rect distribution.
 *
 * Given the available row width + gutter, produce per-side widths so
 * the renderer can lay out each row's keys with the **left half**
 * reachable by the left thumb and the **right half** reachable by the
 * right thumb, with a hand-rest gutter between them.
 *
 * The split is driven by the column position of each key in the row:
 *  - QWERTY: q w e r t | y u i o p   → 5 / 5 split
 *  - QWERTY second row: a s d f g | h j k l → 5 / 4 split
 *  - Bottom row symbols and modifiers: caller-driven via [keyCount]
 *
 * The calculator only computes geometry — actual key-rect emission
 * lives in the existing layout-engine code which consumes this output
 * via the matching `ImeWindowConstraints.Fixed.Split` (Next-7.2 v1.8.0).
 */
object SplitKeyboardLayoutCalculator {

    /**
     * Compute side-by-side rectangles for one row of [leftKeyCount] +
     * [rightKeyCount] keys inside a row of [totalWidth] dp, with a
     * mid-row [gutter] between the two halves. Returns geometry in
     * dp so the renderer composes directly with Compose's `dp`-based
     * layout primitives.
     */
    fun calculateRow(
        totalWidth: Dp,
        gutter: Dp,
        leftKeyCount: Int,
        rightKeyCount: Int,
    ): SplitRowGeometry {
        require(leftKeyCount >= 0) { "leftKeyCount must be non-negative" }
        require(rightKeyCount >= 0) { "rightKeyCount must be non-negative" }
        require(totalWidth.value > 0) { "totalWidth must be positive" }
        require(gutter.value in 0f..totalWidth.value) {
            "gutter must be in [0, totalWidth]; was $gutter / $totalWidth"
        }
        val keyCount = leftKeyCount + rightKeyCount
        if (keyCount == 0) {
            return SplitRowGeometry(
                leftWidth = ((totalWidth.value - gutter.value) / 2f).dp,
                gutterWidth = gutter,
                rightWidth = ((totalWidth.value - gutter.value) / 2f).dp,
                leftKeyWidth = 0.dp,
                rightKeyWidth = 0.dp,
            )
        }
        // Allocate per-side widths proportional to the key counts so a
        // 5+4 row (typical QWERTY second row) doesn't squash the left
        // side to fit the right's narrower count.
        val available = (totalWidth.value - gutter.value).coerceAtLeast(0f)
        val leftWidth = available * (leftKeyCount.toFloat() / keyCount)
        val rightWidth = available - leftWidth
        val leftKeyWidth = if (leftKeyCount > 0) leftWidth / leftKeyCount else 0f
        val rightKeyWidth = if (rightKeyCount > 0) rightWidth / rightKeyCount else 0f
        return SplitRowGeometry(
            leftWidth = leftWidth.dp,
            gutterWidth = gutter,
            rightWidth = rightWidth.dp,
            leftKeyWidth = leftKeyWidth.dp,
            rightKeyWidth = rightKeyWidth.dp,
        )
    }

    /**
     * Default split for a QWERTY-style row of [keyCount] keys.
     * Splits keys at the canonical hand boundary: q/w/e/r/t on the
     * left of the top row, y/u/i/o/p on the right; a/s/d/f/g on the
     * left of the middle row, h/j/k/l on the right; z/x/c/v on the
     * left of the bottom row, b/n/m on the right.
     */
    fun qwertyBoundary(rowIndex: Int, keyCount: Int): Pair<Int, Int> {
        return when (rowIndex) {
            0 -> when (keyCount) {
                10 -> 5 to 5
                else -> halfAndHalf(keyCount)
            }
            1 -> when (keyCount) {
                9 -> 5 to 4
                else -> halfAndHalf(keyCount)
            }
            2 -> when (keyCount) {
                7 -> 4 to 3
                else -> halfAndHalf(keyCount)
            }
            else -> halfAndHalf(keyCount)
        }
    }

    private fun halfAndHalf(keyCount: Int): Pair<Int, Int> {
        val left = keyCount / 2
        return left to (keyCount - left)
    }
}

data class SplitRowGeometry(
    val leftWidth: Dp,
    val gutterWidth: Dp,
    val rightWidth: Dp,
    val leftKeyWidth: Dp,
    val rightKeyWidth: Dp,
) {
    val totalWidth: Dp get() = (leftWidth.value + gutterWidth.value + rightWidth.value).dp
}
