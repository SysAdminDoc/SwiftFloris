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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Regression coverage for the v1.8.184 fix (`.ai/research/2026-05-25` R3):
 * `MultiTextKeyData.asString` and the internal `asString(KeyData)` previously
 * caught malformed code points with a silent `catch (_: Throwable) {}`. The fix
 * keeps the catch (a malformed asset / OEM string is a legitimate failure mode)
 * but logs via `flogWarning`. The load-bearing guarantee these tests pin is the
 * **observable** one: a malformed code point is dropped without throwing, valid
 * code points around it are preserved, and the display path falls back to the
 * label.
 */
class TextKeyDataMalformedCodePointTest : FunSpec({

    test("MultiTextKeyData.asString drops invalid code points without throwing") {
        val data = MultiTextKeyData(codePoints = intArrayOf(-1, 0x110000))
        data.asString(isForDisplay = false) shouldBe ""
    }

    test("MultiTextKeyData.asString keeps valid code points around invalid ones") {
        val data = MultiTextKeyData(codePoints = intArrayOf(0x41, -1, 0x42, 0x110000, 0x43))
        data.asString(isForDisplay = false) shouldBe "ABC"
    }

    test("MultiTextKeyData.asString preserves valid astral (surrogate-pair) code points") {
        val data = MultiTextKeyData(codePoints = intArrayOf(0x1F600))
        data.asString(isForDisplay = false) shouldBe "😀"
    }

    test("MultiTextKeyData.asString display path falls back to the label") {
        val data = MultiTextKeyData(codePoints = intArrayOf(-1), label = "X")
        data.asString(isForDisplay = true) shouldBe "X"
    }

    test("TextKeyData.asString drops an out-of-range code point without throwing") {
        TextKeyData(code = 0x110000).asString(isForDisplay = false) shouldBe ""
    }

    test("TextKeyData.asString emits a valid character") {
        TextKeyData(code = 0x41).asString(isForDisplay = false) shouldBe "A"
    }
})
