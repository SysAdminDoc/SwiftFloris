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

/**
 * ROADMAP §7 Next-6.4 — hardware-keyboard layout descriptor.
 *
 * Represents a normalized in-memory hardware-keyboard layout sourced from
 * a Windows `.klc` (Microsoft Keyboard Layout Creator) export or a macOS
 * `.keylayout` file. The format-specific parsing lives in dedicated
 * `KlcLayoutParser` / `MacKeylayoutParser` classes; this data class is
 * the cross-format target representation.
 *
 * Once paired with the Android `InputManager.getInputDeviceIds()` flow
 * (Phase Next-6.4b — pending), the IME will be able to remap physical
 * keystrokes through one of these layouts when a USB / Bluetooth
 * hardware keyboard is attached to the device.
 */
data class HardwareKeyboardLayout(
    /** Display name, e.g. `"US Dvorak"` or `"German (T2)"`. */
    val name: String,
    /** ISO 639-1 + 3166-1 locale tag, e.g. `"en-US"` or `"de-DE"`. */
    val locale: String,
    /** Platform key code / scancode → key entry. */
    val scancodeMap: Map<Int, HardwareKeyEntry>,
) {
    /** Source format the layout was imported from. */
    val isLoaded: Boolean get() = scancodeMap.isNotEmpty()

    companion object {
        val Empty = HardwareKeyboardLayout(
            name = "",
            locale = "",
            scancodeMap = emptyMap(),
        )
    }
}

/**
 * A single key entry inside a [HardwareKeyboardLayout]. Each shift-state
 * column maps to the output character (or `null` when the slot is unbound).
 * Dead keys carry a non-null [HardwareKeyEntry.deadKeyTrigger] and are
 * resolved through a follow-up dead-key composition pass at input time.
 */
data class HardwareKeyEntry(
    /** Source key identifier, e.g. `"VK_A"`, `"OEM_3"`, `"MAC_0"`, or `"A01"`. */
    val virtualKeyName: String,
    /** Output codepoint with no modifier active. */
    val normal: Int? = null,
    /** Output codepoint with Shift active. */
    val shift: Int? = null,
    /** Output codepoint with Ctrl active. */
    val ctrl: Int? = null,
    /** Output codepoint with AltGr (Ctrl+Alt) active. */
    val altGr: Int? = null,
    /** Output codepoint with Shift + AltGr active. */
    val shiftAltGr: Int? = null,
    /** Capslock applies to the alphabetic slot (per the KLC `cap` column). */
    val capsLock: Boolean = false,
    /** Dead-key trigger codepoint when this slot starts a dead-key composition. */
    val deadKeyTrigger: Int? = null,
    /** Optional visual label override from LDML `<displays>` entries. */
    val displayLabel: String? = null,
)
