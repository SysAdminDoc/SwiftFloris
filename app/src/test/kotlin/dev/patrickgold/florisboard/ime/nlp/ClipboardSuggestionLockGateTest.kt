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

package dev.patrickgold.florisboard.ime.nlp

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Regression tests for the [ClipboardSuggestionLockGate]. These tests pin the lock-screen suppression contract so a
 * future refactor of the clipboard suggestion provider cannot silently re-expose recently-copied 2FA codes / OTPs /
 * passwords / addresses in the smartbar while the device is locked.
 */
class ClipboardSuggestionLockGateTest : FunSpec({
    test("does not suppress when both device unlocked and keyguard not showing") {
        ClipboardSuggestionLockGate.shouldSuppress(isDeviceLocked = false, isKeyguardLocked = false) shouldBe false
    }

    test("suppresses when the device-locked secure-state flag is set") {
        ClipboardSuggestionLockGate.shouldSuppress(isDeviceLocked = true, isKeyguardLocked = false) shouldBe true
    }

    test("suppresses when the keyguard is showing, even for swipe-only locks") {
        ClipboardSuggestionLockGate.shouldSuppress(isDeviceLocked = false, isKeyguardLocked = true) shouldBe true
    }

    test("suppresses when both keyguard and device-locked flags are set") {
        ClipboardSuggestionLockGate.shouldSuppress(isDeviceLocked = true, isKeyguardLocked = true) shouldBe true
    }
})
