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

import dev.patrickgold.florisboard.ime.window.SplitKeyboardLayoutCalculator
import dev.patrickgold.florisboard.lib.FlorisRect

/**
 * ROADMAP §0 P3-renderer (in-tree slice) — split-keyboard post-pass.
 *
 * After [TextKeyboard.layout] has positioned every key for a fixed-
 * mode keyboard, this post-pass:
 *  1. Walks each row of [keyboard.arrangement].
 *  2. For each row, picks the gutter point via
 *     [SplitKeyboardLayoutCalculator.qwertyBoundary].
 *  3. Shifts every key from the gutter point onward to the right by
 *     [gutterPx] pixels.
 *  4. Updates both `touchBounds` and `visibleBounds` in lockstep so
 *     the renderer + hit-tester stay aligned.
 *
 * The post-pass is **non-destructive on the upstream layout maths**
 * — `TextKeyboard.layout` is unchanged. The split-mode caller adds
 * this pass at the end of its layout phase; non-split keyboards
 * skip the call entirely and observe zero behaviour change.
 *
 * The TextKeyboardLayout-level wire-up now pre-shrinks the base layout
 * width by the active split gutter, then calls this helper so the right
 * half lands back inside the final container and the touch hit-test has
 * a real no-key gap.
 */
object SplitGutterPostPass {

    /**
     * Apply the gutter shift in-place to every key in [keyboard].
     *
     * @param gutterPx pixel width of the gutter spacer between the
     *   two halves (typically 80dp converted to px).
     * @return number of keys that were shifted (sum across all rows).
     */
    fun apply(keyboard: TextKeyboard, gutterPx: Float): Int {
        require(gutterPx >= 0f) { "gutterPx must be non-negative; was $gutterPx" }
        if (gutterPx == 0f) return 0
        var shiftedCount = 0
        for ((rowIndex, row) in keyboard.rows().withIndex()) {
            val rowSize = row.size
            if (rowSize == 0) continue
            val (leftKeyCount, _) = SplitKeyboardLayoutCalculator.qwertyBoundary(rowIndex, rowSize)
            if (leftKeyCount <= 0 || leftKeyCount >= rowSize) continue
            // Shift the right half by `gutterPx` to the right.
            row.drop(leftKeyCount).forEach { key ->
                key.touchBounds.translateBy(gutterPx, 0f)
                key.visibleBounds.translateBy(gutterPx, 0f)
                shiftedCount++
            }
        }
        return shiftedCount
    }
}

/**
 * Helper for tests + the future integration callsite. Reads the
 * collection of keys in a row + the row's gutter-shift state in one
 * call. Each [SplitRowSnapshot] carries the row index, the
 * pre-shift key count, the left/right partition counts, and the
 * inclusive x-range of every key for assertion convenience.
 */
data class SplitRowSnapshot(
    val rowIndex: Int,
    val leftKeyCount: Int,
    val rightKeyCount: Int,
    val keyBounds: List<Pair<Float, Float>>,
) {
    /** Distance (px) from the right edge of the last left-half key to the
     *  left edge of the first right-half key. Equals the gutter width
     *  after `SplitGutterPostPass.apply` ran. */
    val gutterMeasure: Float
        get() {
            if (leftKeyCount == 0 || rightKeyCount == 0) return 0f
            val lastLeftRight = keyBounds[leftKeyCount - 1].second
            val firstRightLeft = keyBounds[leftKeyCount].first
            return firstRightLeft - lastLeftRight
        }

    companion object {
        fun captureRow(rowIndex: Int, row: List<TextKey>): SplitRowSnapshot {
            val (leftKeyCount, rightKeyCount) =
                SplitKeyboardLayoutCalculator.qwertyBoundary(rowIndex, row.size)
            return SplitRowSnapshot(
                rowIndex = rowIndex,
                leftKeyCount = leftKeyCount,
                rightKeyCount = rightKeyCount,
                keyBounds = row.map { it.touchBounds.left to it.touchBounds.right },
            )
        }
    }
}

/** Translate this rect's left/right edges by `dx`. Internal companion to
 *  [SplitGutterPostPass.apply] — used in tests. */
internal fun FlorisRect.translateXBy(dx: Float) {
    translateBy(dx, 0f)
}
