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

import androidx.compose.ui.unit.dp
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SplitKeyboardLayoutCalculatorTest : FunSpec({
    test("calculateRow 10 keys + 80dp gutter on a 1000dp row → 5+5 split with 460dp halves") {
        val geo = SplitKeyboardLayoutCalculator.calculateRow(
            totalWidth = 1000.dp,
            gutter = 80.dp,
            leftKeyCount = 5,
            rightKeyCount = 5,
        )
        geo.leftWidth shouldBe 460.dp
        geo.rightWidth shouldBe 460.dp
        geo.gutterWidth shouldBe 80.dp
        geo.leftKeyWidth shouldBe 92.dp
        geo.rightKeyWidth shouldBe 92.dp
    }

    test("calculateRow proportionally allocates a 5+4 row") {
        val geo = SplitKeyboardLayoutCalculator.calculateRow(
            totalWidth = 900.dp,
            gutter = 100.dp,
            leftKeyCount = 5,
            rightKeyCount = 4,
        )
        // 800dp available, left gets 5/9 ≈ 444.444, right gets 4/9 ≈ 355.555.
        geo.leftWidth.value shouldBe ((800f * 5f / 9f))
        geo.rightWidth.value shouldBe ((800f * 4f / 9f))
    }

    test("totalWidth round-trips: leftWidth + gutterWidth + rightWidth = totalWidth") {
        val geo = SplitKeyboardLayoutCalculator.calculateRow(
            totalWidth = 850.dp,
            gutter = 80.dp,
            leftKeyCount = 5,
            rightKeyCount = 4,
        )
        geo.totalWidth shouldBe 850.dp
    }

    test("zero-key row produces equal halves") {
        val geo = SplitKeyboardLayoutCalculator.calculateRow(
            totalWidth = 600.dp,
            gutter = 80.dp,
            leftKeyCount = 0,
            rightKeyCount = 0,
        )
        geo.leftWidth shouldBe 260.dp
        geo.rightWidth shouldBe 260.dp
        geo.leftKeyWidth shouldBe 0.dp
    }

    test("rejects negative key counts") {
        shouldThrow<IllegalArgumentException> {
            SplitKeyboardLayoutCalculator.calculateRow(800.dp, 80.dp, -1, 5)
        }
    }

    test("qwertyBoundary returns 5+5 for top row, 5+4 for home row, 4+3 for bottom row") {
        SplitKeyboardLayoutCalculator.qwertyBoundary(0, 10) shouldBe (5 to 5)
        SplitKeyboardLayoutCalculator.qwertyBoundary(1, 9) shouldBe (5 to 4)
        SplitKeyboardLayoutCalculator.qwertyBoundary(2, 7) shouldBe (4 to 3)
    }

    test("non-QWERTY row counts fall back to half-and-half") {
        SplitKeyboardLayoutCalculator.qwertyBoundary(0, 8) shouldBe (4 to 4)
        SplitKeyboardLayoutCalculator.qwertyBoundary(5, 11) shouldBe (5 to 6)
    }
})
