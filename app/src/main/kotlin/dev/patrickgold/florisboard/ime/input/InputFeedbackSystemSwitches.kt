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

package dev.patrickgold.florisboard.ime.input

import android.content.ContentResolver
import android.provider.Settings

/**
 * The phone's own touch sounds and touch vibration switches, read the same way by the keyboard
 * and by its Sounds & Vibration screen.
 *
 * In [InputFeedbackActivationMode.RESPECT_SYSTEM_SETTINGS], the default, a switch that is off
 * keeps the keyboard's matching feedback quiet even though every option of its own is on. On a
 * Samsung these are the Touch interactions switches, not the volume sliders, which left the
 * keyboard silent with nothing on its settings screen saying why (#26). Reading both places from
 * here keeps the screen's explanation showing exactly when the keyboard is held quiet.
 */
object InputFeedbackSystemSwitches {
    fun isTouchSoundOn(resolver: ContentResolver?): Boolean =
        read(resolver, Settings.System.SOUND_EFFECTS_ENABLED)

    fun isTouchVibrationOn(resolver: ContentResolver?): Boolean =
        read(resolver, Settings.System.HAPTIC_FEEDBACK_ENABLED)

    /** True while the keyboard's own switch is on but the phone's switch keeps it quiet. */
    fun isSilencedByPhone(
        keyboardSwitchOn: Boolean,
        mode: InputFeedbackActivationMode,
        phoneSwitchOn: Boolean,
    ): Boolean = keyboardSwitchOn && mode == InputFeedbackActivationMode.RESPECT_SYSTEM_SETTINGS && !phoneSwitchOn

    private fun read(resolver: ContentResolver?, name: String): Boolean {
        if (resolver == null) return false
        return Settings.System.getInt(resolver, name, 0) != 0
    }
}
