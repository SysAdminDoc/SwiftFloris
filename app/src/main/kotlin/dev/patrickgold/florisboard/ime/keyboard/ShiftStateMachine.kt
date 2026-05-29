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

package dev.patrickgold.florisboard.ime.keyboard

import dev.patrickgold.florisboard.ime.input.CapitalizationBehavior
import dev.patrickgold.florisboard.ime.input.InputShiftState

/**
 * Pure shift-key state machine extracted from `KeyboardManager` (RESEARCH_FEATURE_PLAN.md
 * F27). `KeyboardManager` itself is a heavily Android-coupled dispatch
 * orchestrator with no direct tests; this follows the project's Workstream-3
 * pattern (cf. [KeyboardAutoCommitFlushPolicy], `ApostropheReturnGate`,
 * `QuoteAutoCloseGate`) of lifting the deterministic decision out so it can be
 * JVM-unit-tested without a real keyboard, dispatcher, or editor.
 *
 * The transitions are the SHIFT-key half of the input shift state machine:
 *  - [onShiftDown] — what a SHIFT key-down does, honouring the user's
 *    [CapitalizationBehavior] (double-tap-to-caps-lock vs. cycle).
 *  - [onShiftUp] — whether a SHIFT key-up releases a transient manual shift.
 */
internal object ShiftStateMachine {

    /**
     * Resolves the next [InputShiftState] for a SHIFT key-down from [current],
     * the user's [behavior], and whether this is a consecutive (double-tap)
     * press ([isConsecutiveDown]).
     *
     * - [CapitalizationBehavior.CAPSLOCK_BY_DOUBLE_TAP]: a double-tap latches
     *   CAPS_LOCK; otherwise SHIFT toggles between UNSHIFTED and SHIFTED_MANUAL.
     * - [CapitalizationBehavior.CAPSLOCK_BY_CYCLE]: each press advances
     *   UNSHIFTED → SHIFTED_MANUAL → CAPS_LOCK → UNSHIFTED (and SHIFTED_AUTOMATIC
     *   collapses to UNSHIFTED).
     */
    fun onShiftDown(
        current: InputShiftState,
        behavior: CapitalizationBehavior,
        isConsecutiveDown: Boolean,
    ): InputShiftState {
        return when (behavior) {
            CapitalizationBehavior.CAPSLOCK_BY_DOUBLE_TAP -> when {
                isConsecutiveDown -> InputShiftState.CAPS_LOCK
                current == InputShiftState.UNSHIFTED -> InputShiftState.SHIFTED_MANUAL
                else -> InputShiftState.UNSHIFTED
            }
            CapitalizationBehavior.CAPSLOCK_BY_CYCLE -> when (current) {
                InputShiftState.UNSHIFTED -> InputShiftState.SHIFTED_MANUAL
                InputShiftState.SHIFTED_MANUAL -> InputShiftState.CAPS_LOCK
                InputShiftState.SHIFTED_AUTOMATIC -> InputShiftState.UNSHIFTED
                InputShiftState.CAPS_LOCK -> InputShiftState.UNSHIFTED
            }
        }
    }

    /**
     * Resolves the next [InputShiftState] for a SHIFT key-up. A transient manual
     * shift is released to UNSHIFTED only when CAPS_LOCK is not latched, no other
     * key is held ([isAnyKeyPressed]), and the up event is not part of an
     * uninterrupted shift+key combo ([isUninterruptedSequence]); otherwise the
     * state is left unchanged.
     */
    fun onShiftUp(
        current: InputShiftState,
        isAnyKeyPressed: Boolean,
        isUninterruptedSequence: Boolean,
    ): InputShiftState {
        return if (current != InputShiftState.CAPS_LOCK && !isAnyKeyPressed && !isUninterruptedSequence) {
            InputShiftState.UNSHIFTED
        } else {
            current
        }
    }
}
