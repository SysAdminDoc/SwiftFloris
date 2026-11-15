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

    // Issue #23, first login field after the keyboard starts: Android asked for
    // the request before the Smartbar had composed, so the chip height was still
    // 0 and the spec went out with a 48 px maximum. By the response the S25's
    // Smartbar had settled on 107 px chips, inflate threw for 107 > 48, and
    // Bitwarden's only chip was dropped.
    test("a cold keyboard's request leaves room for the chip height it settles on") {
        val coldSpecMax = InlineSuggestionSizePolicy.presentationMaxDimensions(
            keyboardWidthPx = 0,
            chipHeightPx = 0,
        )
        val size = InlineSuggestionSizePolicy.inflateSize(
            chipHeightPx = 107,
            specMinHeightPx = InlineSuggestionSizePolicy.presentationMinDimensions.heightPx,
            specMaxHeightPx = coldSpecMax.heightPx,
        )

        size.heightPx shouldBe 107
        InlineSuggestionSizePolicy.isValidInlineDimensions(coldSpecMax) shouldBe true
    }

    test("a chip inflates inside the spec it was requested with even if the keyboard grew since") {
        val size = InlineSuggestionSizePolicy.inflateSize(
            chipHeightPx = 107,
            specMinHeightPx = 1,
            specMaxHeightPx = 48,
        )

        size.widthPx shouldBe WRAP_CONTENT
        size.heightPx shouldBe 48
    }

    test("a chip inflates inside the spec it was requested with even if the keyboard shrank since") {
        InlineSuggestionSizePolicy.inflateSize(
            chipHeightPx = 40,
            specMinHeightPx = 60,
            specMaxHeightPx = 120,
        ).heightPx shouldBe 60
    }

    test("a malformed spec never stops a chip from inflating at the keyboard's height") {
        InlineSuggestionSizePolicy.inflateSize(
            chipHeightPx = 107,
            specMinHeightPx = 200,
            specMaxHeightPx = 100,
        ).heightPx shouldBe 107
    }

    test("a cold request leaves the chip the slot beside the Smartbar toggle and sticky action") {
        // S25 at density 2.625: full-width keyboard, 133 px Smartbar row (107 px chips plus
        // 5 dp margins), 4 dp either side of the sticky action, 5 dp chip margins.
        val widthPx = InlineSuggestionSizePolicy.estimatedChipWidthPx(
            keyboardWidthPx = 1080,
            smartbarRowHeightPx = 133,
            stickyActionPaddingPx = 21,
            chipMarginPx = 26,
        )

        widthPx shouldBe 1080 - 2 * 133 - 21 - 26
        // A warm keyboard drew "Bitwarden My vault" 507 px wide; the old cold fallback was 320.
        (widthPx >= 507) shouldBe true
    }

    test("a cold estimate for a keyboard narrower than its Smartbar furniture is zero, not negative") {
        InlineSuggestionSizePolicy.estimatedChipWidthPx(
            keyboardWidthPx = 200,
            smartbarRowHeightPx = 133,
            stickyActionPaddingPx = 21,
            chipMarginPx = 26,
        ) shouldBe 0
    }
})

private const val WRAP_CONTENT = -2 // android.view.ViewGroup.LayoutParams.WRAP_CONTENT
