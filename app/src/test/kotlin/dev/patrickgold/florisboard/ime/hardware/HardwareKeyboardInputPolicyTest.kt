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

package dev.patrickgold.florisboard.ime.hardware

import android.view.KeyEvent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class HardwareKeyboardInputPolicyTest : FunSpec({
    test("space key routes through the IME spacebar handler") {
        HardwareKeyboardInputPolicy.keyDownAction(
            keyCode = KeyEvent.KEYCODE_SPACE,
            mappedKey = null,
        ) shouldBe HardwareKeyboardKeyDownAction.HandleSpace
    }

    test("enter key routes through the IME enter handler") {
        HardwareKeyboardInputPolicy.keyDownAction(
            keyCode = KeyEvent.KEYCODE_ENTER,
            mappedKey = null,
        ) shouldBe HardwareKeyboardKeyDownAction.HandleEnter
    }

    test("delete keys pass through to the host editor path when not layout-mapped") {
        HardwareKeyboardInputPolicy.keyDownAction(
            keyCode = KeyEvent.KEYCODE_DEL,
            mappedKey = null,
        ) shouldBe HardwareKeyboardKeyDownAction.PassThrough
        HardwareKeyboardInputPolicy.keyDownAction(
            keyCode = KeyEvent.KEYCODE_FORWARD_DEL,
            mappedKey = null,
        ) shouldBe HardwareKeyboardKeyDownAction.PassThrough
    }

    test("shift down and up route through the input-event dispatcher") {
        HardwareKeyboardInputPolicy.keyDownAction(
            keyCode = KeyEvent.KEYCODE_SHIFT_LEFT,
            mappedKey = null,
        ) shouldBe HardwareKeyboardKeyDownAction.HandleShiftDown
        HardwareKeyboardInputPolicy.keyUpAction(KeyEvent.KEYCODE_SHIFT_RIGHT) shouldBe
            HardwareKeyboardKeyUpAction.HandleShiftUp
    }

    test("mapped punctuation commits text and flushes a pending autocorrect candidate first") {
        HardwareKeyboardInputPolicy.keyDownAction(
            keyCode = KeyEvent.KEYCODE_PERIOD,
            mappedKey = mapped("."),
            isMappedKeyAlphabetic = false,
        ) shouldBe HardwareKeyboardKeyDownAction.CommitMappedText(
            text = ".",
            shouldFlushAutoCommitCandidate = true,
        )
    }

    test("mapped letters commit text without flushing autocorrect first") {
        HardwareKeyboardInputPolicy.keyDownAction(
            keyCode = KeyEvent.KEYCODE_A,
            mappedKey = mapped("a"),
            isMappedKeyAlphabetic = true,
        ) shouldBe HardwareKeyboardKeyDownAction.CommitMappedText(
            text = "a",
            shouldFlushAutoCommitCandidate = false,
        )
    }

    test("mapped punctuation wins before built-in key handling") {
        HardwareKeyboardInputPolicy.keyDownAction(
            keyCode = KeyEvent.KEYCODE_SPACE,
            mappedKey = mapped("."),
            isMappedKeyAlphabetic = false,
        ) shouldBe HardwareKeyboardKeyDownAction.CommitMappedText(
            text = ".",
            shouldFlushAutoCommitCandidate = true,
        )
    }
})

private fun mapped(text: String): HardwareMappedKey {
    val codePoint = text.codePointAt(0)
    return HardwareMappedKey(
        deviceId = 7,
        sourceCode = 1,
        text = text,
        codePoint = codePoint,
        entry = HardwareKeyEntry(virtualKeyName = "TEST", normal = codePoint),
    )
}
