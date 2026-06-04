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

import kotlinx.serialization.Serializable

/**
 * ROADMAP §7 Next-6.4 — hardware-keyboard layout descriptor.
 *
 * Represents a normalized in-memory hardware-keyboard layout sourced from
 * a Windows `.klc` (Microsoft Keyboard Layout Creator) export or a macOS
 * `.keylayout` file. The format-specific parsing lives in dedicated
 * `KlcLayoutParser` / `MacKeylayoutParser` classes; this data class is
 * the cross-format target representation.
 *
 * The v1.8.76 runtime mapper pairs these descriptors with Android hardware
 * device IDs so the IME can remap physical keystrokes from USB / Bluetooth
 * hardware keyboards through one of these layouts.
 */
@Serializable
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

    // Override generated data-class equality so `scancodeMap` is compared by
    // size + reference identity, not by structural walk. A real LDML layout
    // can have ~300 keys, each `HardwareKeyEntry` is a data class with
    // eight fields, and the auto-generated `Map.equals` walks every entry
    // through `HardwareKeyEntry.equals` — O(n*m) on every comparison.
    //
    // The mapper / settings paths compare layouts often (device-attach,
    // pruning, refresh), so this override keeps those checks O(1) in the
    // common cases: identical reference, both empty, or same metadata +
    // size with shared map identity. Two layouts with different content
    // but identical (name, locale, size) and *different* map references
    // would compare not-equal here — which is the right answer, because
    // the parsers always allocate a fresh map for each parse, so the only
    // way two layouts share `scancodeMap` is if they came from the same
    // parse call (and therefore really are the same layout).
    //
    // `componentN()` / `copy()` keep their data-class semantics unchanged.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HardwareKeyboardLayout) return false
        if (name != other.name) return false
        if (locale != other.locale) return false
        if (scancodeMap.size != other.scancodeMap.size) return false
        // Reference equality is the common case (the mapper hands the same
        // layout reference around). When sizes match but references differ
        // we fall through to the structural map walk — preserving
        // correctness for the rare cross-instance comparison.
        if (scancodeMap === other.scancodeMap) return true
        return scancodeMap == other.scancodeMap
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + locale.hashCode()
        result = 31 * result + scancodeMap.size
        return result
    }

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
@Serializable
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
    /**
     * Long-press alternates as a list of output codepoints, in display
     * order. Populated by the LDML parser from `longPress="a b c"` (LDML
     * spec — space-separated list of alternates surfaced on long-press).
     * Empty list when the layout source did not declare alternates.
     *
     * Consumers: the popup-routing slice tracked separately at
     * [v1.8.85 follow-up F8](../../../../../../../../../../../../CHANGELOG.md#v1.8.85)
     * — once the on-screen long-press popup learns to surface
     * hardware-keyboard-source alternates, this field is the source of
     * truth. Until then the field is populated but not consumed at input
     * time; storing it now lets a future popup slice land without a
     * second parser change.
     */
    val longPressAlternates: List<Int> = emptyList(),
)
