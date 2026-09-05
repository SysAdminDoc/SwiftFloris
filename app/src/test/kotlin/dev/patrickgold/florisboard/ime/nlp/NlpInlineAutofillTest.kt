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

package dev.patrickgold.florisboard.ime.nlp

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class NlpInlineAutofillTest : FunSpec({
    test("inline suggestion presentation bounds never use zero or unbounded dimensions") {
        InlineSuggestionSizePolicy.presentationMinDimensions.widthPx shouldBe 1
        InlineSuggestionSizePolicy.presentationMinDimensions.heightPx shouldBe 1

        val size = InlineSuggestionSizePolicy.presentationMaxDimensions(
            keyboardWidthPx = Int.MAX_VALUE,
            chipHeightPx = Int.MAX_VALUE,
        )

        size.widthPx shouldBe 4096
        size.heightPx shouldBe 512
        InlineSuggestionSizePolicy.isValidInlineDimensions(size) shouldBe true
    }

    test("inline suggestion inflate size replaces invalid runtime dimensions with stable fallbacks") {
        val size = InlineSuggestionSizePolicy.inflateSize(
            keyboardWidthPx = 0,
            chipHeightPx = -1,
        )

        size.widthPx shouldBe 320
        size.heightPx shouldBe 48
        InlineSuggestionSizePolicy.isValidInlineDimensions(size) shouldBe true
    }

    test("inline suggestion inflate size preserves valid keyboard dimensions") {
        val size = InlineSuggestionSizePolicy.inflateSize(
            keyboardWidthPx = 1080,
            chipHeightPx = 56,
        )

        size.widthPx shouldBe 1080
        size.heightPx shouldBe 56
        InlineSuggestionSizePolicy.isValidInlineDimensions(size) shouldBe true
    }

    // Issue #23: the chip width used to come from the display, so a keyboard
    // narrower than the display (floating, one-handed, split, or resized) got
    // chips inflated wider than itself and they ran off the edge.
    test("inline suggestion width follows a keyboard narrower than the display") {
        val displayWidthPx = 1440
        val keyboardWidthPx = 900

        val inflate = InlineSuggestionSizePolicy.inflateSize(
            keyboardWidthPx = keyboardWidthPx,
            chipHeightPx = 56,
        )
        val presentation = InlineSuggestionSizePolicy.presentationMaxDimensions(
            keyboardWidthPx = keyboardWidthPx,
            chipHeightPx = 56,
        )

        inflate.widthPx shouldBe keyboardWidthPx
        presentation.widthPx shouldBe keyboardWidthPx
        (inflate.widthPx < displayWidthPx) shouldBe true
        (presentation.widthPx < displayWidthPx) shouldBe true
    }

    test("inline suggestion width matches the keyboard when it fills the display") {
        val widthPx = 1080

        InlineSuggestionSizePolicy.inflateSize(
            keyboardWidthPx = widthPx,
            chipHeightPx = 56,
        ).widthPx shouldBe widthPx
    }

    // The width is published from composition, so it is 0 until the first layout
    // pass. An unmeasured keyboard must fall back rather than inflate at zero.
    test("inline suggestion width falls back while the keyboard is unmeasured") {
        val size = InlineSuggestionSizePolicy.inflateSize(
            keyboardWidthPx = 0,
            chipHeightPx = 56,
        )

        size.widthPx shouldBe 320
        InlineSuggestionSizePolicy.isValidInlineDimensions(size) shouldBe true
    }

})
