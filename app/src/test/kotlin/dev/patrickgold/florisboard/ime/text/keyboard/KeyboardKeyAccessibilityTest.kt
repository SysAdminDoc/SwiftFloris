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

import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.key.KeyType
import dev.patrickgold.florisboard.lib.FlorisRect
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class KeyboardKeyAccessibilityTest : FunSpec({
    test("printable key descriptions preserve visible glyph and short hint") {
        keyContentDescription(
            code = 'a'.code,
            label = "a",
            hintedLabel = "@",
            getString = ::testString,
            getFormattedString = ::testFormattedString,
        ) shouldBe "a, alternative: @"
    }

    test("common action keys have specific labels instead of generic fallback") {
        val cases = mapOf(
            KeyCode.SPACE to "Space",
            KeyCode.CJK_SPACE to "Space",
            KeyCode.DELETE to "Backspace",
            KeyCode.ENTER to "Enter",
            KeyCode.CLIPBOARD_PASTE to "Paste",
            KeyCode.CLIPBOARD_SELECT_ALL to "Select all",
            KeyCode.VOICE_INPUT to "Voice input",
            KeyCode.TOGGLE_INCOGNITO_MODE to "Toggle incognito mode",
            KeyCode.TOGGLE_AUTOCORRECT to "Toggle autocorrect",
            KeyCode.TOGGLE_ACTIONS_OVERFLOW to "More smartbar actions",
            KeyCode.VIEW_PHONE to "Phone keypad",
            KeyCode.SYSTEM_NEXT_INPUT_METHOD to "Next input method",
            KeyCode.IME_SUBTYPE_PICKER to "Language picker",
        )

        cases.forEach { (code, expected) ->
            keyContentDescription(
                code = code,
                label = null,
                getString = ::testString,
                getFormattedString = ::testFormattedString,
            ) shouldBe expected
        }
    }

    test("semantic key target follows touch bounds when they exceed the visual key") {
        val key = TextKey(
            data = TextKeyData(
                type = KeyType.CHARACTER,
                code = 'a'.code,
                label = "a",
            ),
        )
        key.visibleBounds.applyFrom(FlorisRect.new(left = 4f, top = 4f, right = 44f, bottom = 44f))
        key.touchBounds.applyFrom(FlorisRect.new(left = 0f, top = 0f, right = 48f, bottom = 48f))

        keyAccessibilityBounds(key) shouldBe key.touchBounds
    }

    test("semantic key target falls back to visual bounds before layout assigns touch bounds") {
        val key = TextKey(
            data = TextKeyData(
                type = KeyType.CHARACTER,
                code = 'b'.code,
                label = "b",
            ),
        )
        key.visibleBounds.applyFrom(FlorisRect.new(left = 2f, top = 2f, right = 42f, bottom = 42f))

        keyAccessibilityBounds(key) shouldBe key.visibleBounds
    }
})

private fun testFormattedString(resId: Int, value: String): String {
    return when (resId) {
        R.string.a11y__key__alternative_suffix -> ", alternative: $value"
        else -> error("Unexpected formatted string id: $resId")
    }
}

private fun testString(resId: Int): String {
    return when (resId) {
        R.string.a11y__key__space -> "Space"
        R.string.a11y__key__delete -> "Backspace"
        R.string.a11y__key__enter -> "Enter"
        R.string.a11y__key__clipboard_paste -> "Paste"
        R.string.a11y__key__clipboard_select_all -> "Select all"
        R.string.a11y__key__voice_input -> "Voice input"
        R.string.a11y__key__toggle_incognito -> "Toggle incognito mode"
        R.string.a11y__key__toggle_autocorrect -> "Toggle autocorrect"
        R.string.a11y__key__toggle_actions_overflow -> "More smartbar actions"
        R.string.a11y__key__view_phone -> "Phone keypad"
        R.string.a11y__key__next_input_method -> "Next input method"
        R.string.a11y__key__subtype_picker -> "Language picker"
        R.string.a11y__key__generic -> "Key"
        else -> error("Unexpected string id: $resId")
    }
}
