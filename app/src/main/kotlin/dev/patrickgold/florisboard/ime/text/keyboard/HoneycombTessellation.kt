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

import kotlin.math.sqrt

/**
 * ROADMAP §7 L9.2 — honeycomb / hexagonal tessellation geometry.
 *
 * Typewise's CES Innovation 2021 / 2022-winning honeycomb keyboard
 * arranges keys as hexagons in a flat-top tessellation, which shortens
 * the average thumb travel between common letter pairs (the bigram
 * graph fits more naturally onto a 6-neighbour adjacency than a 4-
 * neighbour QWERTY grid). The actual layout JSON ships at
 * `assets/ime/keyboard/.../characters/honeycomb.json`; this file
 * provides the **tessellation math** the renderer + hit-tester will
 * consume.
 *
 * Geometry:
 *  - Flat-top hexagons (vertices at angles 0°, 60°, 120°, …).
 *  - Row stride = `keyRadius * 1.5` (vertical spacing).
 *  - Column stride = `keyRadius * sqrt(3)` (horizontal spacing).
 *  - Even rows are offset by half a column-stride from odd rows.
 *
 * The production renderer integrates by:
 *  1. Computing `keyRadius` from the row width divided by the row's
 *     `keyCount` (or a fixed dp value from theme).
 *  2. Calling [centerOf] for each `(row, col)` pair.
 *  3. Calling [containsPoint] for touch hit-testing.
 *
 * v1.8.79 wires the same geometry into [TextKeyboard.layoutHoneycomb]
 * and the production [TextKeyboardLayout] hit-test/rendering path.
 */
class HoneycombTessellation(
    val keyRadius: Float,
    val rowCount: Int,
    val columnCounts: List<Int>,
) {
    init {
        require(keyRadius > 0) { "keyRadius must be positive" }
        require(rowCount in 1..16) { "rowCount must be in 1..16; was $rowCount" }
        require(columnCounts.size == rowCount) {
            "columnCounts.size (${columnCounts.size}) != rowCount ($rowCount)"
        }
        columnCounts.forEachIndexed { i, n ->
            require(n in 1..16) { "row $i column count must be in 1..16; was $n" }
        }
    }

    val rowStride: Float = keyRadius * 1.5f
    val columnStride: Float = keyRadius * SQRT_3

    /** Center (x, y) in keyboard-local pixels for the cell at [row, col]. */
    fun centerOf(row: Int, col: Int): HexCenter {
        require(row in 0 until rowCount) { "row $row out of range" }
        val rowColumns = columnCounts[row]
        require(col in 0 until rowColumns) { "col $col out of range for row $row" }
        val rowOffset = if (row % 2 == 1) columnStride / 2f else 0f
        val x = rowOffset + col * columnStride + columnStride / 2f
        val y = keyRadius + row * rowStride
        return HexCenter(x, y)
    }

    /** True when point [(px, py)] falls inside the hexagon at [row, col]. */
    fun containsPoint(row: Int, col: Int, px: Float, py: Float): Boolean {
        val center = centerOf(row, col)
        return isPointInFlatTopHex(px, py, center.x, center.y, keyRadius)
    }

    /**
     * Return the (row, col) the [(px, py)] point falls into, or null
     * when it falls in the inter-hex gap. Brute-force across all keys
     * — the layout is small enough (typically ≤ 40 keys) that this is
     * cheap on every touch event.
     */
    fun cellAt(px: Float, py: Float): HexCell? {
        for (row in 0 until rowCount) {
            for (col in 0 until columnCounts[row]) {
                if (containsPoint(row, col, px, py)) return HexCell(row, col)
            }
        }
        return null
    }

    /** Total keyboard width in pixels for the widest row. */
    val totalWidth: Float
        get() = columnStride * (columnCounts.maxOrNull() ?: 0) + columnStride / 2f

    /** Total keyboard height in pixels. */
    val totalHeight: Float
        get() = keyRadius * 2f + rowStride * (rowCount - 1)

    companion object {
        val SQRT_3: Float = sqrt(3f)

        /**
         * Point-in-flat-top-hexagon test. Uses the axis-aligned
         * "two trapezoid" decomposition: a flat-top hex with center
         * `(cx, cy)` and inradius `r` consists of a 2r-wide × √3·r
         * vertical-tall rectangle flanked by two equilateral triangles
         * top and bottom. Equivalent to checking the absolute distance
         * from the center against the hex's clipped envelope.
         */
        fun isPointInFlatTopHex(
            px: Float,
            py: Float,
            cx: Float,
            cy: Float,
            r: Float,
        ): Boolean {
            val dx = kotlin.math.abs(px - cx)
            val dy = kotlin.math.abs(py - cy)
            if (dy > r) return false
            if (dx > r * SQRT_3 / 2f) return false
            // Top + bottom slanted edges.
            return r * SQRT_3 * r - r * SQRT_3 * dy / 2f - SQRT_3 * dx * r / 2f >= 0f ||
                dy <= r / 2f
        }
    }
}

data class HexCenter(val x: Float, val y: Float)
data class HexCell(val row: Int, val col: Int)
