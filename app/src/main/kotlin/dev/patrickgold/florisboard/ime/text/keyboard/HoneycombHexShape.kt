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

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * ROADMAP §8 L9.2 — flat-top hexagon Compose [Shape] for honeycomb-
 * tiled keyboards (Typewise-style alternative layout).
 *
 * Sized to **fit inside** the supplied [Size] bounding rect:
 *
 *  - For a flat-top hex with width `w` and height `h`, the
 *    "radius" `r` (centre → vertex) is `w / 2`.
 *  - The hex height is therefore `√3 · r ≈ 1.732 · r`.
 *  - When the bounding rect's aspect ratio differs from the
 *    natural `2 / √3`, the hex is centred within the rect rather
 *    than stretched — preserves the regular-hex angles
 *    (60° at every vertex) so the tessellation looks right.
 *
 * Companion to [HoneycombTessellation] (shipped v1.8.4) which owns
 * the grid math; this Shape is what TextKeyboardLayout's hex-mode
 * call site will use to clip each key's backdrop. The renderer
 * wire-up is the remaining L9.2 sub-task.
 *
 * Reference: Red Blob Games — Hexagonal Grids
 * (https://www.redblobgames.com/grids/hexagons/).
 */
object HoneycombHexShape : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Generic(path = buildPath(size))

    /**
     * Build the path describing a flat-top hexagon inscribed in
     * [size]. Public so geometry tests can assert the resulting
     * coordinates without instantiating a Compose `Density`.
     */
    fun buildPath(size: Size): Path {
        val (cx, cy) = centerOf(size)
        val r = radiusFor(size)
        val halfHeight = (r * SQRT_3) / 2f
        val path = Path()
        // Flat-top hex vertices starting at the left flat edge, going
        // clockwise: left, top-left, top-right, right, bottom-right,
        // bottom-left.
        path.moveTo(cx - r, cy)
        path.lineTo(cx - r / 2f, cy - halfHeight)
        path.lineTo(cx + r / 2f, cy - halfHeight)
        path.lineTo(cx + r, cy)
        path.lineTo(cx + r / 2f, cy + halfHeight)
        path.lineTo(cx - r / 2f, cy + halfHeight)
        path.close()
        return path
    }

    /**
     * Effective hex radius inscribed in [size]. Picks the larger
     * dimension cap that still keeps the hex inside the bounding
     * rect (so a short-but-wide rect produces a shorter hex, and a
     * narrow rect produces a smaller hex centred horizontally).
     */
    fun radiusFor(size: Size): Float {
        val rByWidth = size.width / 2f
        val rByHeight = size.height / SQRT_3
        return minOf(rByWidth, rByHeight)
    }

    /** Centre of [size]. Exposed for the renderer's text-placement layer. */
    fun centerOf(size: Size): Pair<Float, Float> =
        Pair(size.width / 2f, size.height / 2f)

    /** Vertex coordinates of the hex inscribed in [size], clockwise from left. */
    fun verticesFor(size: Size): List<Pair<Float, Float>> {
        val (cx, cy) = centerOf(size)
        val r = radiusFor(size)
        val halfHeight = (r * SQRT_3) / 2f
        return listOf(
            (cx - r) to cy,
            (cx - r / 2f) to (cy - halfHeight),
            (cx + r / 2f) to (cy - halfHeight),
            (cx + r) to cy,
            (cx + r / 2f) to (cy + halfHeight),
            (cx - r / 2f) to (cy + halfHeight),
        )
    }

    private const val SQRT_3: Float = 1.7320508f
}
