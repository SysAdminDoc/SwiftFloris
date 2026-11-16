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

internal object HardwareKeyboardInputPolicy {
    /**
     * What a hardware key does. [isEditorBehindApp] says the keyboard's copy of the text is still
     * missing keys the app inserted itself (it hasn't reported them yet, or they haven't been read
     * back). A space then goes to the app too, after those keys: the word the spacebar handler would
     * correct and learn is cut short ("hedgeh" for "hedgehog"), and a fix committed over it would
     * replace the wrong letters. [hasCommandModifier] (Ctrl, Alt or Meta held) keeps a space with
     * the keyboard either way, so a shortcut doesn't change with how fast the keys came.
     *
     * Enter goes to the app the same way, but only when [isEnterNewline] says it would type a
     * newline in a multi-line field. The keyboard sends its newline over the input connection, and
     * the app handles key events waiting in its queue first, so letters typed after Enter landed
     * before the newline ("hedgehogriv" then "er" on the next line). An Enter that runs the
     * field's action stays with the keyboard, since an app reads a raw Enter as IME_NULL there.
     * So does every Enter when [hasImportedLayout] says the keyboard types this device's letters
     * itself: those go over the input connection too, and a raw Enter could pass the last of them.
     */
    fun keyDownAction(
        keyCode: Int,
        mappedKey: HardwareMappedKey?,
        isMappedKeyAlphabetic: Boolean = false,
        isEditorBehindApp: Boolean = false,
        isEnterNewline: Boolean = false,
        hasImportedLayout: Boolean = false,
        hasCommandModifier: Boolean = false,
    ): HardwareKeyboardKeyDownAction {
        // Only the space key itself: the app types its own char for the key, so a key a layout
        // maps to a space would come out as that char instead.
        if (isEditorBehindApp && !hasCommandModifier && keyCode == KeyEvent.KEYCODE_SPACE &&
            (mappedKey == null || mappedKey.text == " ")
        ) {
            return HardwareKeyboardKeyDownAction.PassThrough
        }
        if (isEditorBehindApp && isEnterNewline && !hasImportedLayout && !hasCommandModifier &&
            keyCode == KeyEvent.KEYCODE_ENTER && mappedKey == null
        ) {
            return HardwareKeyboardKeyDownAction.PassThrough
        }
        if (mappedKey != null) {
            // A space ends the word, and only the spacebar handler learns it and applies autocorrect.
            if (mappedKey.text == " ") return HardwareKeyboardKeyDownAction.HandleSpace
            return HardwareKeyboardKeyDownAction.CommitMappedText(
                text = mappedKey.text,
                shouldFlushAutoCommitCandidate = !isMappedKeyAlphabetic,
            )
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_SPACE -> HardwareKeyboardKeyDownAction.HandleSpace
            KeyEvent.KEYCODE_ENTER -> HardwareKeyboardKeyDownAction.HandleEnter
            KeyEvent.KEYCODE_SHIFT_LEFT,
            KeyEvent.KEYCODE_SHIFT_RIGHT,
            -> HardwareKeyboardKeyDownAction.HandleShiftDown
            else -> HardwareKeyboardKeyDownAction.PassThrough
        }
    }

    /**
     * How many chars the app inserts on its own for a key that passes through, so the editor can
     * tell that cursor move from a jump. 0 for shortcuts, dead keys and control keys. Ctrl with
     * Alt is AltGr on a PC-style keyboard, which types a char (the euro sign) rather than a
     * shortcut, the same way [HardwareKeyboardRuntimeMapper] reads it.
     */
    fun passThroughInsertLength(
        unicodeChar: Int,
        isCtrlPressed: Boolean,
        isAltPressed: Boolean,
        isMetaPressed: Boolean,
    ): Int {
        if (isMetaPressed || (isCtrlPressed && !isAltPressed)) return 0
        // Dead keys carry the combining-accent high bit, so they read as negative here.
        if (unicodeChar <= 0 || !Character.isValidCodePoint(unicodeChar)) return 0
        if (Character.isISOControl(unicodeChar)) return 0
        return Character.charCount(unicodeChar)
    }

    /**
     * True for a key that passes through and steps the app's cursor back one char: a plain
     * Backspace or Left arrow. The editor can predict where that leaves the cursor, so the keys
     * typed after it stay tracked. A modifier makes them go a word or a line instead, and Shift
     * makes Left select.
     */
    fun passThroughStepsBack(
        keyCode: Int,
        isShiftPressed: Boolean,
        isCtrlPressed: Boolean,
        isAltPressed: Boolean,
        isMetaPressed: Boolean,
    ): Boolean {
        if (isShiftPressed || isCtrlPressed || isAltPressed || isMetaPressed) return false
        return keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
    }

    fun keyUpAction(keyCode: Int): HardwareKeyboardKeyUpAction {
        return when (keyCode) {
            KeyEvent.KEYCODE_SHIFT_LEFT,
            KeyEvent.KEYCODE_SHIFT_RIGHT,
            -> HardwareKeyboardKeyUpAction.HandleShiftUp
            else -> HardwareKeyboardKeyUpAction.PassThrough
        }
    }
}

internal sealed interface HardwareKeyboardKeyDownAction {
    data object HandleSpace : HardwareKeyboardKeyDownAction
    data object HandleEnter : HardwareKeyboardKeyDownAction
    data object HandleShiftDown : HardwareKeyboardKeyDownAction
    data object PassThrough : HardwareKeyboardKeyDownAction

    data class CommitMappedText(
        val text: String,
        val shouldFlushAutoCommitCandidate: Boolean,
    ) : HardwareKeyboardKeyDownAction
}

internal enum class HardwareKeyboardKeyUpAction {
    HandleShiftUp,
    PassThrough,
}
