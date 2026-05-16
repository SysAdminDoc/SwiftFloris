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

package dev.patrickgold.florisboard.ime.smartcompose

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SmartComposeContextWindowTest : FunSpec({

    test("input shorter than maxChars returns unchanged") {
        val text = "Hello world."
        SmartComposeContextWindow.truncate(text, maxChars = 1024) shouldBe text
    }

    test("truncation snaps to the most recent sentence boundary") {
        // The 30-char tail window contains a non-trailing terminator,
        // so the snap should land on the first letter after that boundary.
        val text = "Pad pad pad pad pad pad pad. Boundary here. Final sentence."
        val trimmed = SmartComposeContextWindow.truncate(text, maxChars = 30)
        (trimmed.length <= 30) shouldBe true
        trimmed.startsWith("Final") shouldBe true
    }

    test("no boundary in window: falls back to the hard cap") {
        val text = "thisIsOneVeryLongSentenceWithoutAnyTerminatorWhatsoeverInsideTheWindow"
        val trimmed = SmartComposeContextWindow.truncate(text, maxChars = 24)
        trimmed.length shouldBe 24
        text.endsWith(trimmed) shouldBe true
    }

    test("convenience overload returns a SmartComposeContext with trimmed precedingText") {
        val ctx = SmartComposeContext(
            precedingText = "Pad pad pad pad pad pad pad. Boundary here. Final sentence.",
            composingPrefix = "",
            locale = "en",
        )
        val out = SmartComposeContextWindow.truncate(ctx, maxChars = 30)
        (out.precedingText.length <= 30) shouldBe true
        out.composingPrefix shouldBe ""
        out.locale shouldBe "en"
    }

    test("convenience overload returns the input unchanged when no trim happens") {
        val ctx = SmartComposeContext(
            precedingText = "short",
            composingPrefix = "",
            locale = "en",
        )
        val out = SmartComposeContextWindow.truncate(ctx, maxChars = 1024)
        out shouldBe ctx
    }

    test("multi-script terminators are honoured") {
        val text = "前文本句。これは長い文ですよ。Second sentence after."
        // Window large enough to capture the CJK boundary but not the start.
        val trimmed = SmartComposeContextWindow.truncate(text, maxChars = 24)
        // Should snap to "Second sentence after." (22 chars).
        trimmed.startsWith("Second") shouldBe true
    }

    test("maxChars below 16 is rejected") {
        var caught = false
        try {
            SmartComposeContextWindow.truncate("anything", maxChars = 15)
        } catch (_: IllegalArgumentException) {
            caught = true
        }
        caught shouldBe true
    }
})
