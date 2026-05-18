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
    test("candidate label includes type position and text") {
        SmartbarAccessibilityLabels.candidateLabel(
            text = "hello",
            index = 0,
            count = 3,
            isClipboard = false,
            isAutoCommit = false,
        ) shouldBe "Suggestion 1 of 3: hello"
    }

    test("candidate label identifies autocorrect and clipboard suggestions") {
        SmartbarAccessibilityLabels.candidateLabel(
            text = "hello",
            index = 1,
            count = 3,
            isClipboard = false,
            isAutoCommit = true,
        ) shouldBe "Autocorrect suggestion 2 of 3: hello"

        SmartbarAccessibilityLabels.candidateLabel(
            text = "Copied address",
            index = 2,
            count = 3,
            isClipboard = true,
            isAutoCommit = true,
        ) shouldBe "Clipboard suggestion 3 of 3: Copied address"
    }

    test("candidate label clamps invalid position inputs") {
        SmartbarAccessibilityLabels.candidateLabel(
            text = "fallback",
            index = -4,
            count = 0,
            isClipboard = false,
            isAutoCommit = false,
        ) shouldBe "Suggestion 1 of 1: fallback"
    }

    test("quick action label prefers visible display name then tooltip") {
        SmartbarAccessibilityLabels.quickActionLabel(
            displayName = "Clipboard",
            tooltip = "Open clipboard",
        ) shouldBe "Clipboard"

        SmartbarAccessibilityLabels.quickActionLabel(
            displayName = "",
            tooltip = "Open clipboard",
        ) shouldBe "Open clipboard"

        SmartbarAccessibilityLabels.quickActionLabel(
            displayName = "",
            tooltip = "",
        ) shouldBe "Smartbar action"
    }
})
