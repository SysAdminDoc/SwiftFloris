/*
 * Copyright (C) 2026 The SwiftFloris Contributors
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

package dev.patrickgold.florisboard.ime.keyboard

import dev.patrickgold.florisboard.ime.ImeUiMode

internal data class KeyboardModeTransitionState(
    val keyboardMode: KeyboardMode = KeyboardMode.CHARACTERS,
    val imeUiMode: ImeUiMode = ImeUiMode.TEXT,
)

/**
 * Owns keyboard-mode entry and exit around the media and clipboard contexts.
 *
 * Context entry records the active text keyboard so closing a panel can restore
 * it. The history stores only enum values, is capped, and is cleared whenever
 * the editor/privacy boundary changes. The controller deliberately keeps no
 * editor content or other user data.
 */
internal class KeyboardModeTransitionController(
    private val maxHistoryEntries: Int = MAX_HISTORY_ENTRIES,
) {
    companion object {
        const val MAX_HISTORY_ENTRIES = 16
    }

    init {
        require(maxHistoryEntries > 0) { "maxHistoryEntries must be positive" }
    }

    private val previousModes = ArrayDeque<KeyboardMode>(maxHistoryEntries)
    private var preservedContextReturnMode: KeyboardMode? = null

    var state: KeyboardModeTransitionState = KeyboardModeTransitionState()
        private set

    internal val historySize: Int
        get() = previousModes.size

    fun transitionToKeyboardMode(mode: KeyboardMode): KeyboardModeTransitionState {
        val normalizedMode = normalize(mode)
        state = state.copy(keyboardMode = normalizedMode)
        if (state.imeUiMode == ImeUiMode.TEXT) {
            clearHistory()
        } else if (previousModes.isEmpty()) {
            // A preserved clipboard context from a new editor has no stale
            // stack entry, but its close action must still return to the new
            // editor's mode rather than to the generic fallback.
            preservedContextReturnMode = normalizedMode
        }
        return state
    }

    fun transitionToImeUiMode(mode: ImeUiMode): KeyboardModeTransitionState {
        if (mode == state.imeUiMode) return state
        if (mode == ImeUiMode.TEXT) {
            val restoredMode = previousModes.removeLastOrNull()
                ?: preservedContextReturnMode
                ?: KeyboardMode.CHARACTERS
            previousModes.clear()
            preservedContextReturnMode = null
            state = state.copy(
                keyboardMode = normalize(restoredMode),
                imeUiMode = ImeUiMode.TEXT,
            )
            return state
        }

        push(state.keyboardMode)
        preservedContextReturnMode = null
        state = state.copy(imeUiMode = mode)
        return state
    }

    /**
     * Starts an editor session while preserving the old clipboard-panel
     * preference. The editor supplies its actual default keyboard mode through
     * [setKeyboardModeForEditor] immediately afterwards.
     */
    fun prepareForEditor(preserveClipboard: Boolean): KeyboardModeTransitionState {
        clearHistory()
        state = state.copy(
            imeUiMode = if (preserveClipboard && state.imeUiMode == ImeUiMode.CLIPBOARD) {
                ImeUiMode.CLIPBOARD
            } else {
                ImeUiMode.TEXT
            },
        )
        return state
    }

    fun setKeyboardModeForEditor(mode: KeyboardMode): KeyboardModeTransitionState {
        val normalizedMode = normalize(mode)
        state = state.copy(keyboardMode = normalizedMode)
        if (state.imeUiMode == ImeUiMode.TEXT) {
            clearHistory()
        } else {
            preservedContextReturnMode = normalizedMode
        }
        return state
    }

    /** Clears all remembered context without exposing a stale panel. */
    fun resetUiModeAndHistory(): KeyboardModeTransitionState {
        clearHistory()
        state = state.copy(imeUiMode = ImeUiMode.TEXT)
        return state
    }

    internal fun clearHistory() {
        previousModes.clear()
        preservedContextReturnMode = null
    }

    private fun push(mode: KeyboardMode) {
        if (previousModes.size == maxHistoryEntries) {
            previousModes.removeFirst()
        }
        previousModes.addLast(normalize(mode))
    }

    private fun normalize(mode: KeyboardMode): KeyboardMode {
        return if (mode == KeyboardMode.UNSPECIFIED) KeyboardMode.CHARACTERS else mode
    }
}
