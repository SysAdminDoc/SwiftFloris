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
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe

/**
 * Builds a tiny TextKeyboard fixture with pre-positioned keys.
 * Each key occupies an `unitWidth`-wide rectangle at row `r`, column
 * `c`. We use [TextKeyData.UNSPECIFIED] for the key data so we don't
 * need a real popup mapping.
 */
private fun fixtureKeyboard(rowSizes: List<Int>, unitWidth: Float, rowHeight: Float): TextKeyboard {
    val arrangement = Array(rowSizes.size) { r ->
        val rowSize = rowSizes[r]
        Array(rowSize) { c ->
            val key = TextKey(TextKeyData.UNSPECIFIED)
            val left = c * unitWidth
            val top = r * rowHeight
            key.touchBounds.apply {
                this.left = left
                this.top = top
                this.right = left + unitWidth
                this.bottom = top + rowHeight
            }
            key.visibleBounds.applyFrom(key.touchBounds)
            key
        }
    }
    return TextKeyboard(
        arrangement = arrangement,
        mode = KeyboardMode.CHARACTERS,
        extendedPopupMapping = null,
        extendedPopupMappingDefault = null,
    )
}

class SplitGutterPostPassTest : FunSpec({
    test("zero gutter is a no-op") {
        val keyboard = fixtureKeyboard(listOf(10, 9, 7), unitWidth = 100f, rowHeight = 60f)
        val shifted = SplitGutterPostPass.apply(keyboard, gutterPx = 0f)
        shifted shouldBe 0
        keyboard.rows().asSequence().flatMap { it.asSequence() }
            .forEach { (it.touchBounds.left % 100f) shouldBe 0f }
    }

    test("negative gutter is rejected") {
        val keyboard = fixtureKeyboard(listOf(10), 100f, 60f)
        shouldThrow<IllegalArgumentException> {
            SplitGutterPostPass.apply(keyboard, gutterPx = -1f)
        }
    }

    test("canonical QWERTY 3-row keyboard splits with the 5+5 / 5+4 / 4+3 boundaries") {
        val keyboard = fixtureKeyboard(listOf(10, 9, 7), unitWidth = 100f, rowHeight = 60f)
        val shifted = SplitGutterPostPass.apply(keyboard, gutterPx = 80f)
        // qwertyBoundary returns:
        //  row 0 (10 keys) → 5 + 5 → shifts indices 5..9 = 5 keys
        //  row 1 (9 keys)  → 5 + 4 → shifts indices 5..8 = 4 keys
        //  row 2 (7 keys)  → 4 + 3 → shifts indices 4..6 = 3 keys
        shifted shouldBe (5 + 4 + 3)

        val rows = keyboard.rows().asSequence().toList()
        // Row 0 — top row, 5+5 split.
        rows[0][4].touchBounds.left shouldBe 400f      // last left-half key
        rows[0][5].touchBounds.left shouldBe 580f      // first right-half key
        // Row 1 — home row, 5+4 split.
        rows[1][4].touchBounds.left shouldBe 400f
        rows[1][5].touchBounds.left shouldBe 580f
        // Row 2 — bottom row, 4+3 split.
        rows[2][3].touchBounds.left shouldBe 300f
        rows[2][4].touchBounds.left shouldBe 480f
    }

    test("non-canonical row sizes fall back to halfAndHalf split") {
        // Single row of 8 keys at row index 0 → halfAndHalf(8) = 4+4.
        val keyboard = fixtureKeyboard(listOf(8), 100f, 60f)
        SplitGutterPostPass.apply(keyboard, gutterPx = 80f)
        val row = keyboard.rows().next()
        row[3].touchBounds.left shouldBe 300f      // last left-half key
        row[4].touchBounds.left shouldBe 480f      // first right-half key
    }

    test("visibleBounds shift in lockstep with touchBounds") {
        val keyboard = fixtureKeyboard(listOf(10, 9, 7), 100f, 60f)
        SplitGutterPostPass.apply(keyboard, gutterPx = 80f)
        val row = keyboard.rows().asSequence().toList()[0]
        for (k in 5 until 10) {
            row[k].visibleBounds.left shouldBe (row[k].touchBounds.left plusOrMinus 1e-3f)
            row[k].visibleBounds.right shouldBe (row[k].touchBounds.right plusOrMinus 1e-3f)
        }
    }

    test("SplitRowSnapshot.gutterMeasure equals the applied gutter after the post-pass") {
        val keyboard = fixtureKeyboard(listOf(10, 9, 7), 100f, 60f)
        SplitGutterPostPass.apply(keyboard, gutterPx = 80f)
        val rows = keyboard.rows().asSequence().toList()
        val snapshot = SplitRowSnapshot.captureRow(rowIndex = 0, row = rows[0].toList())
        snapshot.gutterMeasure shouldBe (80f plusOrMinus 1e-3f)
    }

    test("pre-shrunk layout plus post-pass keeps the right edge inside the final width") {
        val finalWidth = 1000f
        val gutter = 80f
        val layoutWidth = TextKeyboardSplitLayout.layoutWidthPx(finalWidth, gutter)
        val keyboard = fixtureKeyboard(listOf(10), unitWidth = layoutWidth / 10f, rowHeight = 60f)

        SplitGutterPostPass.apply(keyboard, gutterPx = gutter)

        val row = keyboard.rows().next()
        row.last().touchBounds.right shouldBe (finalWidth plusOrMinus 1e-3f)
        SplitRowSnapshot.captureRow(rowIndex = 0, row = row.toList())
            .gutterMeasure shouldBe (gutter plusOrMinus 1e-3f)
    }

    test("hinge-aligned post-pass puts every row gap on the reported hinge") {
        val finalWidth = 1000f
        val hingePlacement = TextKeyboardSplitLayout.HingePlacement(
            containerWidthPx = finalWidth,
            hingeLeftPx = 470f,
            hingeRightPx = 530f,
        )
        val layoutWidth = TextKeyboardSplitLayout.layoutWidthPx(finalWidth, hingePlacement.gutterPx)
        val keyboard = fixtureKeyboard(listOf(10, 9, 7), unitWidth = layoutWidth / 10f, rowHeight = 60f)

        SplitGutterPostPass.apply(
            keyboard = keyboard,
            gutterPx = hingePlacement.gutterPx,
            placement = hingePlacement,
        )

        keyboard.rows().asSequence().forEachIndexed { rowIndex, row ->
            val snapshot = SplitRowSnapshot.captureRow(rowIndex, row.toList())
            row[snapshot.leftKeyCount - 1].touchBounds.right shouldBe (470f plusOrMinus 1e-3f)
            row[snapshot.leftKeyCount].touchBounds.left shouldBe (530f plusOrMinus 1e-3f)
            snapshot.gutterMeasure shouldBe (60f plusOrMinus 1e-3f)
            row.last().touchBounds.right shouldBe (finalWidth plusOrMinus 1e-3f)
        }
    }

    test("split gutter rejects nearest-key rescue") {
        val finalWidth = 1000f
        val gutter = 80f
        val layoutWidth = TextKeyboardSplitLayout.layoutWidthPx(finalWidth, gutter)
        val keyboard = fixtureKeyboard(listOf(10), unitWidth = layoutWidth / 10f, rowHeight = 60f)

        SplitGutterPostPass.apply(keyboard, gutterPx = gutter)

        val row = keyboard.rows().next()
        val gutterCenterX = (row[4].touchBounds.right + row[5].touchBounds.left) / 2f
        keyboard.isPointInSplitGutter(gutterCenterX, 30f) shouldBe true
        keyboard.getKeyForPos(gutterCenterX, 30f) shouldBe null
        keyboard.getNearestKeyForPos(gutterCenterX, 30f) shouldBe null
    }

    test("an empty-arrangement keyboard is a no-op (defensive)") {
        val keyboard = TextKeyboard(
            arrangement = arrayOf(),
            mode = KeyboardMode.CHARACTERS,
            extendedPopupMapping = null,
            extendedPopupMappingDefault = null,
        )
        SplitGutterPostPass.apply(keyboard, gutterPx = 80f) shouldBe 0
    }

    test("a single-key row (no viable split) is a no-op") {
        val keyboard = fixtureKeyboard(listOf(1), 100f, 60f)
        SplitGutterPostPass.apply(keyboard, gutterPx = 80f) shouldBe 0
        keyboard.rows().next()[0].touchBounds.left shouldBe 0f
    }
})
