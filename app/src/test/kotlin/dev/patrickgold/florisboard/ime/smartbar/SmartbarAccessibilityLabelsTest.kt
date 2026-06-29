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

package dev.patrickgold.florisboard.ime.smartbar

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SmartbarAccessibilityLabelsTest : FunSpec({
    // Mirrors the default (English) a11y__candidate__* resource templates.
    val suggestionTemplate = "Suggestion {index} of {count}: {text}"
    val autocorrectTemplate = "Autocorrect suggestion {index} of {count}: {text}"
    val clipboardTemplate = "Clipboard suggestion {index} of {count}: {text}"

    test("candidate label includes type position and text") {
        SmartbarAccessibilityLabels.candidateLabel(
            template = suggestionTemplate,
            text = "hello",
            index = 0,
            count = 3,
        ) shouldBe "Suggestion 1 of 3: hello"
    }

    test("candidate label fills autocorrect and clipboard templates") {
        SmartbarAccessibilityLabels.candidateLabel(
            template = autocorrectTemplate,
            text = "hello",
            index = 1,
            count = 3,
        ) shouldBe "Autocorrect suggestion 2 of 3: hello"

        SmartbarAccessibilityLabels.candidateLabel(
            template = clipboardTemplate,
            text = "Copied address",
            index = 2,
            count = 3,
        ) shouldBe "Clipboard suggestion 3 of 3: Copied address"
    }

    test("candidate label clamps invalid position inputs") {
        SmartbarAccessibilityLabels.candidateLabel(
            template = suggestionTemplate,
            text = "fallback",
            index = -4,
            count = 0,
        ) shouldBe "Suggestion 1 of 1: fallback"
    }

    test("candidate label supports scrollable rows with more than three visible suggestions") {
        SmartbarAccessibilityLabels.candidateLabel(
            template = suggestionTemplate,
            text = "clipboard",
            index = 4,
            count = 6,
        ) shouldBe "Suggestion 5 of 6: clipboard"
    }

    test("quick action label prefers visible display name then tooltip then fallback") {
        SmartbarAccessibilityLabels.quickActionLabel(
            displayName = "Clipboard",
            tooltip = "Open clipboard",
            fallback = "Smartbar action",
        ) shouldBe "Clipboard"

        SmartbarAccessibilityLabels.quickActionLabel(
            displayName = "",
            tooltip = "Open clipboard",
            fallback = "Smartbar action",
        ) shouldBe "Open clipboard"

        SmartbarAccessibilityLabels.quickActionLabel(
            displayName = "",
            tooltip = "",
            fallback = "Smartbar action",
        ) shouldBe "Smartbar action"
    }
})
