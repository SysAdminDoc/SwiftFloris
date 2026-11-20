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

import android.view.KeyCharacterMap
import android.view.KeyEvent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class HardwareKeyboardInputPolicyTest : FunSpec({
    test("space key routes through the IME spacebar handler") {
        HardwareKeyboardInputPolicy.keyDownAction(
            keyCode = KeyEvent.KEYCODE_SPACE,
            mappedKey = null,
        ) shouldBe HardwareKeyboardKeyDownAction.HandleSpace
        HardwareKeyboardInputPolicy.keyUpAction(KeyEvent.KEYCODE_SPACE) shouldBe
            HardwareKeyboardKeyUpAction.PassThrough
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

    test("printable keys the app inserts itself report how many chars it adds") {
        HardwareKeyboardInputPolicy.passThroughInsertLength(
            unicodeChar = 'w'.code,
            isCtrlPressed = false,
            isAltPressed = false,
            isMetaPressed = false,
        ) shouldBe 1
        HardwareKeyboardInputPolicy.passThroughInsertLength(
            unicodeChar = 0x1F600,
            isCtrlPressed = false,
            isAltPressed = false,
            isMetaPressed = false,
        ) shouldBe 2
    }

    test("shortcuts, dead keys and control keys insert nothing") {
        HardwareKeyboardInputPolicy.passThroughInsertLength(
            unicodeChar = 'c'.code,
            isCtrlPressed = true,
            isAltPressed = false,
            isMetaPressed = false,
        ) shouldBe 0
        HardwareKeyboardInputPolicy.passThroughInsertLength(
            unicodeChar = 'a'.code,
            isCtrlPressed = false,
            isAltPressed = false,
            isMetaPressed = true,
        ) shouldBe 0
        HardwareKeyboardInputPolicy.passThroughInsertLength(
            unicodeChar = KeyCharacterMap.COMBINING_ACCENT or 0x0301,
            isCtrlPressed = false,
            isAltPressed = false,
            isMetaPressed = false,
        ) shouldBe 0
        HardwareKeyboardInputPolicy.passThroughInsertLength(
            unicodeChar = '\t'.code,
            isCtrlPressed = false,
            isAltPressed = false,
            isMetaPressed = false,
        ) shouldBe 0
        HardwareKeyboardInputPolicy.passThroughInsertLength(
            unicodeChar = 0,
            isCtrlPressed = false,
            isAltPressed = false,
            isMetaPressed = false,
        ) shouldBe 0
    }

    test("AltGr sent as Ctrl with Alt types a char, but Meta on top of it is a shortcut") {
        HardwareKeyboardInputPolicy.passThroughInsertLength(
            unicodeChar = 0x20AC,
            isCtrlPressed = true,
            isAltPressed = true,
            isMetaPressed = false,
        ) shouldBe 1
        HardwareKeyboardInputPolicy.passThroughInsertLength(
            unicodeChar = 0x20AC,
            isCtrlPressed = true,
            isAltPressed = true,
            isMetaPressed = true,
        ) shouldBe 0
    }

    test("a space from an imported layout still ends the word through the spacebar handler") {
        HardwareKeyboardInputPolicy.keyDownAction(
            keyCode = KeyEvent.KEYCODE_SPACE,
            mappedKey = mapped(" "),
            isMappedKeyAlphabetic = false,
        ) shouldBe HardwareKeyboardKeyDownAction.HandleSpace
    }

    test("a space goes to the app while keys the app inserted haven't reached the keyboard's text") {
        HardwareKeyboardInputPolicy.keyDownAction(
            keyCode = KeyEvent.KEYCODE_SPACE,
            mappedKey = null,
            isEditorBehindApp = true,
        ) shouldBe HardwareKeyboardKeyDownAction.PassThrough
    }

    test("a space stays with the keyboard while behind when an imported layout types the letters") {
        // The layout's letters go over the input connection, which the app reads after the key
        // events waiting in its queue, so a raw space would land in front of them.
        HardwareKeyboardInputPolicy.keyDownAction(
            keyCode = KeyEvent.KEYCODE_SPACE,
            mappedKey = mapped(" "),
            isEditorBehindApp = true,
            hasImportedLayout = true,
        ) shouldBe HardwareKeyboardKeyDownAction.HandleSpace
        // A layout that leaves the space key unmapped still types the letters before it.
        HardwareKeyboardInputPolicy.keyDownAction(
            keyCode = KeyEvent.KEYCODE_SPACE,
            mappedKey = null,
            isEditorBehindApp = true,
            hasImportedLayout = true,
        ) shouldBe HardwareKeyboardKeyDownAction.HandleSpace
    }

    test("only the space key goes to the app when the keyboard's text is behind") {
        // The app would type "." for a space key a layout maps to ".", and its own char for any
        // other key a layout maps to a space.
        HardwareKeyboardInputPolicy.keyDownAction(
            keyCode = KeyEvent.KEYCODE_SPACE,
            mappedKey = mapped("."),
            isEditorBehindApp = true,
        ) shouldBe HardwareKeyboardKeyDownAction.CommitMappedText(
            text = ".",
            shouldFlushAutoCommitCandidate = true,
        )
        HardwareKeyboardInputPolicy.keyDownAction(
            keyCode = KeyEvent.KEYCODE_GRAVE,
            mappedKey = mapped(" "),
            isEditorBehindApp = true,
        ) shouldBe HardwareKeyboardKeyDownAction.HandleSpace
        HardwareKeyboardInputPolicy.keyDownAction(
            keyCode = KeyEvent.KEYCODE_A,
            mappedKey = mapped("a"),
            isMappedKeyAlphabetic = true,
            isEditorBehindApp = true,
        ) shouldBe HardwareKeyboardKeyDownAction.CommitMappedText(
            text = "a",
            shouldFlushAutoCommitCandidate = false,
        )
        HardwareKeyboardInputPolicy.keyDownAction(
            keyCode = KeyEvent.KEYCODE_ENTER,
            mappedKey = null,
            isEditorBehindApp = true,
        ) shouldBe HardwareKeyboardKeyDownAction.HandleEnter
    }

    test("a space with Ctrl, Alt or Meta held stays with the keyboard when its text is behind") {
        // A shortcut must do the same thing however fast the keys before it came.
        HardwareKeyboardInputPolicy.keyDownAction(
            keyCode = KeyEvent.KEYCODE_SPACE,
            mappedKey = null,
            isEditorBehindApp = true,
            hasCommandModifier = true,
        ) shouldBe HardwareKeyboardKeyDownAction.HandleSpace
        HardwareKeyboardInputPolicy.keyDownAction(
            keyCode = KeyEvent.KEYCODE_SPACE,
            mappedKey = null,
            isEditorBehindApp = false,
            hasCommandModifier = true,
        ) shouldBe HardwareKeyboardKeyDownAction.HandleSpace
    }

    test("a newline Enter goes to the app when the keyboard's text is behind") {
        // The keyboard's own newline would land after letters typed later in the burst.
        HardwareKeyboardInputPolicy.keyDownAction(
            keyCode = KeyEvent.KEYCODE_ENTER,
            mappedKey = null,
            isEditorBehindApp = true,
            isEnterNewline = true,
        ) shouldBe HardwareKeyboardKeyDownAction.PassThrough
    }

    test("Enter stays with the keyboard unless it's a newline typed while behind") {
        HardwareKeyboardInputPolicy.keyDownAction(
            keyCode = KeyEvent.KEYCODE_ENTER,
            mappedKey = null,
            isEditorBehindApp = false,
            isEnterNewline = true,
        ) shouldBe HardwareKeyboardKeyDownAction.HandleEnter
        // Search, send and the rest: the app would read a raw Enter as IME_NULL.
        HardwareKeyboardInputPolicy.keyDownAction(
            keyCode = KeyEvent.KEYCODE_ENTER,
            mappedKey = null,
            isEditorBehindApp = true,
            isEnterNewline = false,
        ) shouldBe HardwareKeyboardKeyDownAction.HandleEnter
        HardwareKeyboardInputPolicy.keyDownAction(
            keyCode = KeyEvent.KEYCODE_ENTER,
            mappedKey = null,
            isEditorBehindApp = true,
            isEnterNewline = true,
            hasCommandModifier = true,
        ) shouldBe HardwareKeyboardKeyDownAction.HandleEnter
        // The keyboard types this device's letters over the input connection, and so its Enter.
        HardwareKeyboardInputPolicy.keyDownAction(
            keyCode = KeyEvent.KEYCODE_ENTER,
            mappedKey = null,
            isEditorBehindApp = true,
            isEnterNewline = true,
            hasImportedLayout = true,
        ) shouldBe HardwareKeyboardKeyDownAction.HandleEnter
        // A space never reads the Enter flag.
        HardwareKeyboardInputPolicy.keyDownAction(
            keyCode = KeyEvent.KEYCODE_SPACE,
            mappedKey = null,
            isEditorBehindApp = false,
            isEnterNewline = true,
        ) shouldBe HardwareKeyboardKeyDownAction.HandleSpace
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

    test("only a plain Backspace or Left steps the cursor back") {
        fun stepsBack(
            keyCode: Int,
            shift: Boolean = false,
            ctrl: Boolean = false,
            alt: Boolean = false,
            meta: Boolean = false,
        ) = HardwareKeyboardInputPolicy.passThroughStepsBack(keyCode, shift, ctrl, alt, meta)

        stepsBack(KeyEvent.KEYCODE_DEL) shouldBe true
        stepsBack(KeyEvent.KEYCODE_DPAD_LEFT) shouldBe true
        // Shift+Left selects, Ctrl+Backspace deletes a word, Alt+Backspace a line, Ctrl+Left jumps
        // a word.
        stepsBack(KeyEvent.KEYCODE_DPAD_LEFT, shift = true) shouldBe false
        stepsBack(KeyEvent.KEYCODE_DEL, shift = true) shouldBe false
        stepsBack(KeyEvent.KEYCODE_DEL, ctrl = true) shouldBe false
        stepsBack(KeyEvent.KEYCODE_DEL, alt = true) shouldBe false
        stepsBack(KeyEvent.KEYCODE_DPAD_LEFT, ctrl = true) shouldBe false
        stepsBack(KeyEvent.KEYCODE_DPAD_LEFT, meta = true) shouldBe false
        stepsBack(KeyEvent.KEYCODE_DPAD_RIGHT) shouldBe false
        stepsBack(KeyEvent.KEYCODE_FORWARD_DEL) shouldBe false
    }

    test("a key that types nothing moves the cursor where the field says it does") {
        fun move(
            keyCode: Int,
            shift: Boolean = false,
            ctrl: Boolean = false,
            alt: Boolean = false,
            meta: Boolean = false,
            multiLine: Boolean = false,
        ) = HardwareKeyboardInputPolicy.passThroughMove(keyCode, shift, ctrl, alt, meta, multiLine)

        move(KeyEvent.KEYCODE_DPAD_RIGHT) shouldBe PassThroughMove.Right
        move(KeyEvent.KEYCODE_DPAD_RIGHT, multiLine = true) shouldBe PassThroughMove.Right
        move(KeyEvent.KEYCODE_MOVE_HOME) shouldBe PassThroughMove.LineStart
        move(KeyEvent.KEYCODE_MOVE_END) shouldBe PassThroughMove.LineEnd
        move(KeyEvent.KEYCODE_TAB) shouldBe PassThroughMove.Tab
        move(KeyEvent.KEYCODE_TAB, multiLine = true) shouldBe PassThroughMove.Tab
        // Home and End go to the start and end of the line on screen, which in a multi-line field
        // the keyboard can't tell.
        move(KeyEvent.KEYCODE_MOVE_HOME, multiLine = true) shouldBe PassThroughMove.Unpredictable
        move(KeyEvent.KEYCODE_MOVE_END, multiLine = true) shouldBe PassThroughMove.Unpredictable
        // Shift selects, Ctrl jumps a word or to the end of the text.
        move(KeyEvent.KEYCODE_DPAD_RIGHT, shift = true) shouldBe PassThroughMove.Unpredictable
        move(KeyEvent.KEYCODE_DPAD_RIGHT, ctrl = true) shouldBe PassThroughMove.Unpredictable
        move(KeyEvent.KEYCODE_MOVE_HOME, ctrl = true) shouldBe PassThroughMove.Unpredictable
        move(KeyEvent.KEYCODE_MOVE_END, shift = true) shouldBe PassThroughMove.Unpredictable
        move(KeyEvent.KEYCODE_DPAD_LEFT, shift = true) shouldBe PassThroughMove.Unpredictable
        move(KeyEvent.KEYCODE_DEL, ctrl = true) shouldBe PassThroughMove.Unpredictable
        // A plain Backspace or Left steps back (see passThroughStepsBack).
        move(KeyEvent.KEYCODE_DPAD_LEFT) shouldBe PassThroughMove.None
        move(KeyEvent.KEYCODE_DEL) shouldBe PassThroughMove.None
        // Shift+Tab moves the focus back, and so does a Tab with any other modifier.
        move(KeyEvent.KEYCODE_TAB, shift = true) shouldBe PassThroughMove.None
        move(KeyEvent.KEYCODE_TAB, ctrl = true) shouldBe PassThroughMove.None
        move(KeyEvent.KEYCODE_DPAD_UP) shouldBe PassThroughMove.Unpredictable
        move(KeyEvent.KEYCODE_DPAD_DOWN, multiLine = true) shouldBe PassThroughMove.Unpredictable
        move(KeyEvent.KEYCODE_PAGE_UP) shouldBe PassThroughMove.Unpredictable
        move(KeyEvent.KEYCODE_PAGE_DOWN) shouldBe PassThroughMove.Unpredictable
        move(KeyEvent.KEYCODE_FORWARD_DEL) shouldBe PassThroughMove.Unpredictable
        move(KeyEvent.KEYCODE_PASTE) shouldBe PassThroughMove.Unpredictable
        move(KeyEvent.KEYCODE_CUT) shouldBe PassThroughMove.Unpredictable
        // Paste, cut, undo, redo and select all.
        move(KeyEvent.KEYCODE_V, ctrl = true) shouldBe PassThroughMove.Unpredictable
        move(KeyEvent.KEYCODE_X, ctrl = true) shouldBe PassThroughMove.Unpredictable
        move(KeyEvent.KEYCODE_Z, ctrl = true) shouldBe PassThroughMove.Unpredictable
        move(KeyEvent.KEYCODE_Z, ctrl = true, shift = true) shouldBe PassThroughMove.Unpredictable
        move(KeyEvent.KEYCODE_Y, ctrl = true) shouldBe PassThroughMove.Unpredictable
        move(KeyEvent.KEYCODE_A, ctrl = true) shouldBe PassThroughMove.Unpredictable
        move(KeyEvent.KEYCODE_INSERT, shift = true) shouldBe PassThroughMove.Unpredictable
        // Copy changes nothing, and Ctrl with Alt is AltGr, which types a char.
        move(KeyEvent.KEYCODE_C, ctrl = true) shouldBe PassThroughMove.None
        move(KeyEvent.KEYCODE_INSERT, ctrl = true) shouldBe PassThroughMove.None
        move(KeyEvent.KEYCODE_V, ctrl = true, alt = true) shouldBe PassThroughMove.None
        move(KeyEvent.KEYCODE_V) shouldBe PassThroughMove.None
        // A numpad Enter that doesn't type a newline runs the field's action, which in a
        // multi-line field may still be a newline.
        move(KeyEvent.KEYCODE_NUMPAD_ENTER) shouldBe PassThroughMove.None
        move(KeyEvent.KEYCODE_NUMPAD_ENTER, multiLine = true) shouldBe PassThroughMove.Unpredictable
        move(KeyEvent.KEYCODE_CTRL_LEFT, ctrl = true) shouldBe PassThroughMove.None
        move(KeyEvent.KEYCODE_ESCAPE) shouldBe PassThroughMove.None
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
