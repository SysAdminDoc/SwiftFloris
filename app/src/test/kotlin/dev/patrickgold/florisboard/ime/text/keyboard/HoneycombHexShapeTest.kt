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
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.math.abs

class HoneycombHexShapeTest : FunSpec({

    fun closeEnough(a: Float, b: Float, eps: Float = 0.001f): Boolean =
        abs(a - b) < eps

    test("radiusFor on a perfect-aspect-ratio bounding box returns half the width") {
        // For a flat-top hex, natural aspect ratio is 2 / √3 ≈ 1.1547.
        val size = Size(width = 100f, height = 100f / 1.1547f)
        val r = HoneycombHexShape.radiusFor(size)
        // width=100 → rByWidth = 50; height=86.6 → rByHeight = 86.6/√3 ≈ 50.
        closeEnough(r, 50f, eps = 0.1f) shouldBe true
    }

    test("radiusFor on a tall narrow box clamps to width") {
        val size = Size(width = 50f, height = 200f)
        val r = HoneycombHexShape.radiusFor(size)
        // Width-bound: r = 25. Height would give r = 200/√3 ≈ 115; we take min.
        r shouldBe 25f
    }

    test("radiusFor on a short wide box clamps to height") {
        val size = Size(width = 200f, height = 50f)
        val r = HoneycombHexShape.radiusFor(size)
        // Width gives r = 100; height gives r = 50/√3 ≈ 28.87; we take min.
        closeEnough(r, 50f / 1.7320508f) shouldBe true
    }

    test("centerOf returns the centre of the supplied size") {
        val (cx, cy) = HoneycombHexShape.centerOf(Size(width = 120f, height = 80f))
        cx shouldBe 60f
        cy shouldBe 40f
    }

    test("verticesFor returns six vertices, all equidistant from the centre") {
        val size = Size(width = 100f, height = 100f / 1.1547f)
        val vertices = HoneycombHexShape.verticesFor(size)
        vertices.size shouldBe 6
        val (cx, cy) = HoneycombHexShape.centerOf(size)
        val r = HoneycombHexShape.radiusFor(size)
        for ((vx, vy) in vertices) {
            val dx = vx - cx
            val dy = vy - cy
            val dist = kotlin.math.sqrt(dx * dx + dy * dy)
            closeEnough(dist, r, eps = 0.5f) shouldBe true
        }
    }

    test("verticesFor: leftmost and rightmost vertices flank the centre horizontally") {
        val size = Size(width = 100f, height = 100f / 1.1547f)
        val vertices = HoneycombHexShape.verticesFor(size)
        val (cx, cy) = HoneycombHexShape.centerOf(size)
        val r = HoneycombHexShape.radiusFor(size)
        // Left vertex is at (cx - r, cy).
        vertices.first().first shouldBe cx - r
        vertices.first().second shouldBe cy
        // Right vertex at index 3.
        vertices[3].first shouldBe cx + r
        vertices[3].second shouldBe cy
    }

    // NOTE: testing the actual Compose Path output requires Robolectric
    // (android.graphics.Path isn't available in pure-JVM tests). The
    // geometry helpers above are the testable surface; the Path is just
    // a straightforward translation of verticesFor() into moveTo +
    // lineTo + close.
})
