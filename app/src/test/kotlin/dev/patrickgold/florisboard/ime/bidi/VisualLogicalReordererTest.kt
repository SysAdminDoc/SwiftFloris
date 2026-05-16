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

package dev.patrickgold.florisboard.ime.bidi

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class VisualLogicalReordererTest : FunSpec({

    test("pure LTR text needs no reordering and is returned unchanged") {
        VisualLogicalReorderer.needsReordering("hello world", baseIsRtl = false) shouldBe false
        VisualLogicalReorderer.logicalToVisual("hello world", baseIsRtl = false) shouldBe
            "hello world"
    }

    test("pure RTL Hebrew text in an RTL paragraph needs reordering") {
        VisualLogicalReorderer.needsReordering("שלום", baseIsRtl = true) shouldBe true
    }

    test("pure RTL Hebrew run reverses to visual order under RTL base") {
        val logical = "שלום"
        val visual = VisualLogicalReorderer.logicalToVisual(logical, baseIsRtl = true)
        visual shouldBe "םולש"
    }

    test("visualToLogical reverses a pure RTL visual string") {
        VisualLogicalReorderer.visualToLogical("םולש", baseIsRtl = true) shouldBe "שלום"
    }

    test("visualToLogical leaves LTR text unchanged") {
        VisualLogicalReorderer.visualToLogical("hello", baseIsRtl = false) shouldBe "hello"
    }

    test("empty text passes through both directions unchanged") {
        VisualLogicalReorderer.logicalToVisual("", baseIsRtl = true) shouldBe ""
        VisualLogicalReorderer.visualToLogical("", baseIsRtl = true) shouldBe ""
        VisualLogicalReorderer.needsReordering("", baseIsRtl = true) shouldBe false
    }

    test("mixed Hebrew + Latin text reports needsReordering = true under RTL base") {
        VisualLogicalReorderer.needsReordering("שלום world", baseIsRtl = true) shouldBe true
    }
})
