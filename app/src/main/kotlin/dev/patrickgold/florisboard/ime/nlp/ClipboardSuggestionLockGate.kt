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

/**
 * Pure-Kotlin predicate that decides whether the clipboard suggestion provider should suppress its output because the
 * device is currently behind the lock screen.
 *
 * Even though the system lock screen normally hosts its own secure entry surface, third-party IMEs can be reached on
 * locked devices through emergency dialer, lock-screen widgets, lock-screen-visible app shortcuts, and the brief
 * window where the user unlocks but the lock state has not been cleared yet. Surfacing a recently-copied clipboard
 * entry (a 2FA code, password, OTP, address, etc.) in a smartbar suggestion in that state is a trust-killer: the
 * value of clipboard-history privacy collapses if any onlooker can read the head item from the smartbar without
 * unlocking the phone.
 *
 * This gate keeps the predicate isolated from `android.app.KeyguardManager` so the lock-screen contract is
 * unit-tested without Robolectric. Call sites read both `isDeviceLocked` (Android 5.0+ secure-state flag, true when
 * any credential-backed lock is engaged) and `isKeyguardLocked` (true any time the lock screen is showing, including
 * swipe-only locks) and pass the booleans here. The combined check matches what `ClipboardInputLayout` uses for its
 * UI suppression, so the smartbar surface and the panel surface stay in lockstep.
 */
internal object ClipboardSuggestionLockGate {

    /**
     * @return `true` when the suggestion provider must short-circuit to an empty result list. The smartbar will then
     *  render no clipboard candidate above the keyboard.
     */
    fun shouldSuppress(isDeviceLocked: Boolean, isKeyguardLocked: Boolean): Boolean {
        return isDeviceLocked || isKeyguardLocked
    }
}
