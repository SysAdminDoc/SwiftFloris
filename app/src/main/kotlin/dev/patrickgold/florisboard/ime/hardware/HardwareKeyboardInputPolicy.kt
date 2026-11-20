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
     * So does every Enter and every space when [hasImportedLayout] says the keyboard types this
     * device's letters itself: those go over the input connection too, and a raw key could pass the
     * last of them.
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
        // maps to a space would come out as that char instead. Never with an imported layout: the
        // keyboard types that device's letters over the input connection, which the app reads
        // after its waiting key events, so a raw space would land in front of them.
        if (isEditorBehindApp && !hasImportedLayout && !hasCommandModifier && keyCode == KeyEvent.KEYCODE_SPACE &&
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

    /**
     * How a key that passes through and types nothing moves the app's cursor, as far as the editor
     * can tell before the app reports it (a plain Backspace or Left is [passThroughStepsBack]'s).
     * Home and End go to the ends of the line on screen, which in a multi-line field the keyboard
     * can't tell. A numpad Enter only gets here when it doesn't type a newline the keyboard's Enter
     * would, and in a multi-line field the app may still type one.
     */
    fun passThroughMove(
        keyCode: Int,
        isShiftPressed: Boolean,
        isCtrlPressed: Boolean,
        isAltPressed: Boolean,
        isMetaPressed: Boolean,
        isMultiLine: Boolean,
    ): PassThroughMove {
        val isModified = isShiftPressed || isCtrlPressed || isAltPressed || isMetaPressed
        // Ctrl with Alt is AltGr, which types a char rather than running a shortcut.
        val isShortcut = isCtrlPressed && !isAltPressed
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (isModified) PassThroughMove.Unpredictable else PassThroughMove.Right
            KeyEvent.KEYCODE_MOVE_HOME -> when {
                isModified || isMultiLine -> PassThroughMove.Unpredictable
                else -> PassThroughMove.LineStart
            }
            KeyEvent.KEYCODE_MOVE_END -> when {
                isModified || isMultiLine -> PassThroughMove.Unpredictable
                else -> PassThroughMove.LineEnd
            }
            // With a modifier, a Tab moves the focus back or switches tabs.
            KeyEvent.KEYCODE_TAB -> if (isModified) PassThroughMove.None else PassThroughMove.Tab
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DEL,
            -> if (isModified) PassThroughMove.Unpredictable else PassThroughMove.None
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_PAGE_UP,
            KeyEvent.KEYCODE_PAGE_DOWN,
            KeyEvent.KEYCODE_FORWARD_DEL,
            KeyEvent.KEYCODE_PASTE,
            KeyEvent.KEYCODE_CUT,
            -> PassThroughMove.Unpredictable
            // Shift+Insert pastes.
            KeyEvent.KEYCODE_INSERT -> if (isShiftPressed && !isCtrlPressed) PassThroughMove.Unpredictable else PassThroughMove.None
            // Paste, cut, undo, redo and select all.
            KeyEvent.KEYCODE_V,
            KeyEvent.KEYCODE_X,
            KeyEvent.KEYCODE_Z,
            KeyEvent.KEYCODE_Y,
            KeyEvent.KEYCODE_A,
            -> if (isShortcut) PassThroughMove.Unpredictable else PassThroughMove.None
            KeyEvent.KEYCODE_NUMPAD_ENTER -> if (isMultiLine) PassThroughMove.Unpredictable else PassThroughMove.None
            else -> PassThroughMove.None
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

/** How a key that passes through moves the app's cursor (see [HardwareKeyboardInputPolicy.passThroughMove]). */
internal enum class PassThroughMove {
    /** Moves nothing the keyboard tracks, or moves the focus away. */
    None,
    /** A plain Right arrow, which moves a char the way the paragraph runs. */
    Right,
    /** A plain Home in a single-line field. */
    LineStart,
    /** A plain End in a single-line field. */
    LineEnd,
    /** A plain Tab, which types a tab in some multi-line fields and moves the focus in others. */
    Tab,
    /** Moves the cursor or changes the text in a way only the app's report tells (a paste, Up, Ctrl+Z). */
    Unpredictable,
}

internal enum class HardwareKeyboardKeyUpAction {
    HandleShiftUp,
    PassThrough,
}
