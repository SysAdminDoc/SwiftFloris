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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class HoneycombTessellationTest : FunSpec({
    test("rejects non-positive keyRadius") {
        shouldThrow<IllegalArgumentException> {
            HoneycombTessellation(0f, 1, listOf(1))
        }
    }

    test("rejects rowCount and columnCount mismatch") {
        shouldThrow<IllegalArgumentException> {
            HoneycombTessellation(10f, 2, listOf(3))
        }
    }

    test("row stride equals 1.5 * keyRadius (flat-top hex)") {
        val honey = HoneycombTessellation(keyRadius = 20f, rowCount = 1, columnCounts = listOf(1))
        honey.rowStride shouldBe 30f
    }

    test("column stride equals sqrt(3) * keyRadius") {
        val honey = HoneycombTessellation(keyRadius = 20f, rowCount = 1, columnCounts = listOf(1))
        honey.columnStride shouldBe (20f * HoneycombTessellation.SQRT_3 plusOrMinus 1e-3f)
    }

    test("odd rows are offset half a column-stride from even rows") {
        val honey = HoneycombTessellation(
            keyRadius = 20f, rowCount = 2, columnCounts = listOf(3, 3),
        )
        val evenCenter = honey.centerOf(0, 0)
        val oddCenter = honey.centerOf(1, 0)
        // Odd-row offset = columnStride / 2.
        (oddCenter.x - evenCenter.x) shouldBe (honey.columnStride / 2f plusOrMinus 1e-3f)
    }

    test("the center of a hex contains itself") {
        val honey = HoneycombTessellation(20f, 1, listOf(1))
        val c = honey.centerOf(0, 0)
        honey.containsPoint(0, 0, c.x, c.y) shouldBe true
    }

    test("a point far outside the layout returns null cellAt") {
        val honey = HoneycombTessellation(20f, 2, listOf(3, 3))
        honey.cellAt(1000f, 1000f).shouldBeNull()
    }

    test("a point at the center of a known cell is resolved to that cell") {
        val honey = HoneycombTessellation(20f, 2, listOf(3, 3))
        val target = honey.centerOf(1, 2)
        val cell = honey.cellAt(target.x, target.y).shouldNotBeNull()
        cell.row shouldBe 1
        cell.col shouldBe 2
    }

    test("totalWidth and totalHeight match the layout bounds") {
        val honey = HoneycombTessellation(
            keyRadius = 30f, rowCount = 4, columnCounts = listOf(7, 7, 7, 5),
        )
        // Width: 7 columns × columnStride + half-offset gutter.
        honey.totalWidth shouldBe (30f * HoneycombTessellation.SQRT_3 * 7.5f plusOrMinus 1e-3f)
        // Height: 2r + (rowCount-1) * rowStride.
        honey.totalHeight shouldBe (60f + 3 * 45f plusOrMinus 1e-3f)
    }
})
