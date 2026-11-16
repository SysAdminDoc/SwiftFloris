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
    fun keyDownAction(
        keyCode: Int,
        mappedKey: HardwareMappedKey?,
        isMappedKeyAlphabetic: Boolean = false,
    ): HardwareKeyboardKeyDownAction {
        if (mappedKey != null) {
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
