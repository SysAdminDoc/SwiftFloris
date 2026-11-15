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

    test("inline suggestion inflate height replaces invalid runtime values with a stable fallback") {
        val size = InlineSuggestionSizePolicy.inflateSize(chipHeightPx = -1)

        size.heightPx shouldBe 48
        InlineSuggestionSizePolicy.isValidInlineDimensions(size) shouldBe true
    }

    // Issue #23: chips were inflated at a fixed width as large as the keyboard,
    // so every chip was keyboard-wide. Sitting after the actions toggle, a short
    // "Vault is locked" chip had its label pushed past the right screen edge.
    // The chip has to size to its content and let the spec maximum bound it.
    test("inline suggestions inflate at their content width") {
        val size = InlineSuggestionSizePolicy.inflateSize(chipHeightPx = 56)

        size.widthPx shouldBe WRAP_CONTENT
        size.heightPx shouldBe 56
        InlineSuggestionSizePolicy.isValidInlineDimensions(size) shouldBe true
    }

    // Issue #23: on a full-width keyboard the keyboard width is the display
    // width, but the chips only get the Smartbar slot between the actions toggle
    // and the sticky action. The spec maximum must come from that slot.
    test("inline suggestion maximum width is the measured smartbar slot, not the keyboard") {
        val keyboardChipMaxPx = 1070
        val slotChipMaxPx = 790

        val available = InlineSuggestionSizePolicy.availableChipWidthPx(
            keyboardChipMaxWidthPx = keyboardChipMaxPx,
            slotChipMaxWidthPx = slotChipMaxPx,
        )
        val presentation = InlineSuggestionSizePolicy.presentationMaxDimensions(
            keyboardWidthPx = available,
            chipHeightPx = 56,
        )

        available shouldBe slotChipMaxPx
        presentation.widthPx shouldBe slotChipMaxPx
    }

    test("inline suggestion maximum width never exceeds the keyboard even if the slot reports wider") {
        InlineSuggestionSizePolicy.availableChipWidthPx(
            keyboardChipMaxWidthPx = 900,
            slotChipMaxWidthPx = 1400,
        ) shouldBe 900
    }

    // Issue #23: a floating, one-handed, split or resized keyboard is narrower
    // than the display. Before the Smartbar is measured the keyboard width is
    // the bound, never the display width.
    test("inline suggestion maximum width follows the keyboard until the slot is measured") {
        val displayWidthPx = 1440
        val keyboardChipMaxPx = 900

        val available = InlineSuggestionSizePolicy.availableChipWidthPx(
            keyboardChipMaxWidthPx = keyboardChipMaxPx,
            slotChipMaxWidthPx = 0,
        )
        val presentation = InlineSuggestionSizePolicy.presentationMaxDimensions(
            keyboardWidthPx = available,
            chipHeightPx = 56,
        )

        presentation.widthPx shouldBe keyboardChipMaxPx
        (presentation.widthPx < displayWidthPx) shouldBe true
    }

    // The widths are published from composition and layout, so both are 0 until
    // the first pass. An unmeasured keyboard must fall back rather than request 0.
    test("inline suggestion maximum width falls back while nothing is measured") {
        val available = InlineSuggestionSizePolicy.availableChipWidthPx(
            keyboardChipMaxWidthPx = 0,
            slotChipMaxWidthPx = 0,
        )
        val size = InlineSuggestionSizePolicy.presentationMaxDimensions(
            keyboardWidthPx = available,
            chipHeightPx = 56,
        )

        size.widthPx shouldBe 320
        InlineSuggestionSizePolicy.isValidInlineDimensions(size) shouldBe true
    }
})

private const val WRAP_CONTENT = -2 // android.view.ViewGroup.LayoutParams.WRAP_CONTENT
