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

package dev.patrickgold.florisboard.ime.window

import android.view.Display
import android.view.WindowManager

/**
 * Holds the display at one refresh rate while a size editor drag runs. Left alone, a display with
 * several rates (the S22 runs 60 and 120 Hz) switched between them all through the drag, and each
 * switch stalled the keyboard's next frames. The IME window asks for the fastest mode instead,
 * and the window manager turns that into a vote the display follows until the finger lifts.
 */
internal object GestureRefreshRate {
    /**
     * The fastest of [supported] at [current]'s resolution, so holding it never changes the
     * resolution. [current] itself when nothing else matches, or when another mode only ties
     * its rate, so a display already at its fastest isn't asked to switch.
     */
    fun fastestAtSameSize(current: Display.Mode, supported: Array<Display.Mode>): Display.Mode {
        return supported
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxWithOrNull(compareBy<Display.Mode> { it.refreshRate }.thenBy { it.modeId == current.modeId })
            ?: current
    }

    /**
     * Writes a hold on [mode] into [attrs], or clears it when [mode] is null. Returns true when
     * [attrs] changed and have to be set on the window again.
     */
    fun applyTo(attrs: WindowManager.LayoutParams, mode: Display.Mode?): Boolean {
        val modeId = mode?.modeId ?: 0
        val refreshRate = mode?.refreshRate ?: 0f
        if (attrs.preferredDisplayModeId == modeId && attrs.preferredRefreshRate == refreshRate) {
            return false
        }
        attrs.preferredDisplayModeId = modeId
        attrs.preferredRefreshRate = refreshRate
        return true
    }
}
