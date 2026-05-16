/*
 * Copyright (C) 2025 The SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.keyboard

/**
 * N15.2 Gboard-parity helper: decides whether tapping a symbols-panel key should auto-flip the
 * keyboard back to the letter view because the user just typed an apostrophe in the middle of a
 * contraction (e.g. "don't", "I'm").
 *
 * Kept pure so the gating contract is unit-testable without spinning up the full
 * [KeyboardManager] / Robolectric stack.
 */
object ApostropheReturnGate {
    fun shouldReturnToCharacters(
        committedText: String,
        currentMode: KeyboardMode,
        autoReturnEnabled: Boolean,
    ): Boolean {
        if (!autoReturnEnabled) return false
        if (committedText != "'") return false
        return currentMode == KeyboardMode.SYMBOLS || currentMode == KeyboardMode.SYMBOLS2
    }
}
