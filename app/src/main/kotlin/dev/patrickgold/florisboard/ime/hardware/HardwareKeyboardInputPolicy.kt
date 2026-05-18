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
