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
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Keyboard-state feature contract F27 — direct coverage of the SHIFT-key state machine
 * lifted out of `KeyboardManager` into [ShiftStateMachine]. `KeyboardManager`
 * itself had zero direct tests; these pin the transitions that previously lived
 * inside its private `handleShiftDown` / `handleShiftUp`.
 */
class ShiftStateMachineTest : FunSpec({

    // --- CAPSLOCK_BY_DOUBLE_TAP -------------------------------------------------

    test("double-tap latches CAPS_LOCK") {
        ShiftStateMachine.onShiftDown(
            current = InputShiftState.SHIFTED_MANUAL,
            behavior = CapitalizationBehavior.CAPSLOCK_BY_DOUBLE_TAP,
            isConsecutiveDown = true,
        ) shouldBe InputShiftState.CAPS_LOCK
    }

    test("single tap from UNSHIFTED shifts manually (double-tap mode)") {
        ShiftStateMachine.onShiftDown(
            current = InputShiftState.UNSHIFTED,
            behavior = CapitalizationBehavior.CAPSLOCK_BY_DOUBLE_TAP,
            isConsecutiveDown = false,
        ) shouldBe InputShiftState.SHIFTED_MANUAL
    }

    test("single tap from a shifted state returns to UNSHIFTED (double-tap mode)") {
        ShiftStateMachine.onShiftDown(
            current = InputShiftState.SHIFTED_MANUAL,
            behavior = CapitalizationBehavior.CAPSLOCK_BY_DOUBLE_TAP,
            isConsecutiveDown = false,
        ) shouldBe InputShiftState.UNSHIFTED
        ShiftStateMachine.onShiftDown(
            current = InputShiftState.CAPS_LOCK,
            behavior = CapitalizationBehavior.CAPSLOCK_BY_DOUBLE_TAP,
            isConsecutiveDown = false,
        ) shouldBe InputShiftState.UNSHIFTED
    }

    // --- CAPSLOCK_BY_CYCLE ------------------------------------------------------

    test("cycle advances UNSHIFTED -> SHIFTED_MANUAL -> CAPS_LOCK -> UNSHIFTED") {
        var state = InputShiftState.UNSHIFTED
        val cycle = CapitalizationBehavior.CAPSLOCK_BY_CYCLE
        state = ShiftStateMachine.onShiftDown(state, cycle, isConsecutiveDown = false)
        state shouldBe InputShiftState.SHIFTED_MANUAL
        state = ShiftStateMachine.onShiftDown(state, cycle, isConsecutiveDown = false)
        state shouldBe InputShiftState.CAPS_LOCK
        state = ShiftStateMachine.onShiftDown(state, cycle, isConsecutiveDown = false)
        state shouldBe InputShiftState.UNSHIFTED
    }

    test("cycle collapses SHIFTED_AUTOMATIC to UNSHIFTED") {
        ShiftStateMachine.onShiftDown(
            current = InputShiftState.SHIFTED_AUTOMATIC,
            behavior = CapitalizationBehavior.CAPSLOCK_BY_CYCLE,
            isConsecutiveDown = false,
        ) shouldBe InputShiftState.UNSHIFTED
    }

    // --- onShiftUp --------------------------------------------------------------

    test("shift-up releases a transient manual shift when nothing else is held") {
        ShiftStateMachine.onShiftUp(
            current = InputShiftState.SHIFTED_MANUAL,
            isAnyKeyPressed = false,
            isUninterruptedSequence = false,
        ) shouldBe InputShiftState.UNSHIFTED
    }

    test("shift-up never releases a latched CAPS_LOCK") {
        ShiftStateMachine.onShiftUp(
            current = InputShiftState.CAPS_LOCK,
            isAnyKeyPressed = false,
            isUninterruptedSequence = false,
        ) shouldBe InputShiftState.CAPS_LOCK
    }

    test("shift-up keeps the shift while another key is held (shift+letter combo)") {
        ShiftStateMachine.onShiftUp(
            current = InputShiftState.SHIFTED_MANUAL,
            isAnyKeyPressed = true,
            isUninterruptedSequence = false,
        ) shouldBe InputShiftState.SHIFTED_MANUAL
    }

    test("shift-up keeps the shift during an uninterrupted event sequence") {
        ShiftStateMachine.onShiftUp(
            current = InputShiftState.SHIFTED_MANUAL,
            isAnyKeyPressed = false,
            isUninterruptedSequence = true,
        ) shouldBe InputShiftState.SHIFTED_MANUAL
    }
})
