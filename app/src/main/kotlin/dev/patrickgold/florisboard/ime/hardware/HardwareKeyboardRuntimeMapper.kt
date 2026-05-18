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

import android.view.KeyEvent

/**
 * ROADMAP §7 Next-6.4b — runtime mapper for imported hardware-keyboard layouts.
 *
 * Android delivers hardware keys as [KeyEvent]s with a device id, Android key
 * code, and platform scan code. The imported layout parsers normalize Windows
 * KLC scan codes, macOS virtual key codes, and LDML key ids into
 * [HardwareKeyboardLayout]. This mapper is the narrow runtime bridge:
 *
 * 1. Bind a parsed layout to a specific [KeyEvent.getDeviceId] value.
 * 2. Prune bindings when [InputManager.getInputDeviceIds]-backed callers report
 *    that a device detached.
 * 3. Resolve the event through direct scan/key-code hits, common PC set-1 KLC
 *    fallbacks, common macOS ANSI virtual-key fallbacks, then source-name
 *    fallbacks.
 */
class HardwareKeyboardRuntimeMapper(
    private val attachedDeviceIdsProvider: () -> IntArray = { intArrayOf() },
) {
    // Touched from the IME input thread (via KeyboardManager.onHardwareKeyDown
    // and the InputManager device-detach callback) AND from the settings UI
    // thread when the user binds a layout to a device. Plain LinkedHashMap
    // throws ConcurrentModificationException on concurrent iteration + write.
    // All accesses go through the `lock` monitor.
    private val lock = Any()
    private val layoutsByDeviceId = LinkedHashMap<Int, HardwareKeyboardLayout>()

    fun setLayoutForDevice(deviceId: Int, layout: HardwareKeyboardLayout) {
        synchronized(lock) {
            if (deviceId < 0 || !layout.isLoaded) {
                layoutsByDeviceId.remove(deviceId)
                return
            }
            layoutsByDeviceId[deviceId] = layout
        }
    }

    fun clearLayoutForDevice(deviceId: Int) {
        synchronized(lock) { layoutsByDeviceId.remove(deviceId) }
    }

    fun layoutForDevice(deviceId: Int): HardwareKeyboardLayout? {
        return synchronized(lock) { layoutsByDeviceId[deviceId] }
    }

    fun pruneDetachedLayouts(): Set<Int> {
        val attached = attachedDeviceIdsProvider().toSet()
        return synchronized(lock) {
            val removed = layoutsByDeviceId.keys.filter { it !in attached }.toSet()
            removed.forEach { layoutsByDeviceId.remove(it) }
            removed
        }
    }

    fun map(event: KeyEvent?): HardwareMappedKey? {
        return event?.let { map(HardwareKeyEventInfo.from(it)) }
    }

    fun map(event: HardwareKeyEventInfo): HardwareMappedKey? {
        if (event.deviceId < 0 || event.isMetaPressed) return null
        // PC-style AltGr is delivered by Android as Ctrl+Alt. We must accept
        // Ctrl ONLY when Alt is also pressed (the AltGr layer); a bare Ctrl
        // press is a shortcut, never a printable character. Without this
        // gating, every AltGr-mapped key (€ on EU layouts, all CJK IME hooks
        // on .klc imports) is silently dropped.
        if (event.isCtrlPressed && !event.isAltPressed) return null
        val layout = synchronized(lock) { layoutsByDeviceId[event.deviceId] } ?: return null
        val (sourceCode, entry) = resolveEntry(layout, event) ?: return null
        val codePoint = entry.outputFor(event) ?: return null
        return HardwareMappedKey(
            deviceId = event.deviceId,
            sourceCode = sourceCode,
            text = String(Character.toChars(codePoint)),
            codePoint = codePoint,
            entry = entry,
        )
    }

    private fun resolveEntry(
        layout: HardwareKeyboardLayout,
        event: HardwareKeyEventInfo,
    ): Pair<Int, HardwareKeyEntry>? {
        val candidates = linkedSetOf<Int>()
        if (event.scanCode > 0) candidates += event.scanCode
        if (event.keyCode > 0) candidates += event.keyCode
        androidToPcSet1ScanCode[event.keyCode]?.let { candidates += it }
        androidToMacVirtualKeyCode[event.keyCode]?.let { candidates += it }

        for (candidate in candidates) {
            layout.scancodeMap[candidate]?.let { return candidate to it }
        }

        val androidName = normalizedAndroidKeyName(event.keyCode) ?: return null
        val namedEntry = layout.scancodeMap.entries.firstOrNull { (_, entry) ->
            entry.virtualKeyName.normalizedSourceName() == androidName
        }
        return namedEntry?.toPair()
    }

    private fun HardwareKeyEntry.outputFor(event: HardwareKeyEventInfo): Int? {
        return when {
            event.isShiftPressed && event.isAltPressed -> shiftAltGr ?: altGr ?: shift ?: normal
            event.isAltPressed -> altGr ?: normal
            event.isShiftPressed -> shift ?: normal
            else -> normal
        }
    }

    private fun normalizedAndroidKeyName(keyCode: Int): String? {
        val raw = KeyEvent.keyCodeToString(keyCode)
            .removePrefix("KEYCODE_")
            .takeIf { it.isNotBlank() && it != keyCode.toString() }
            ?: return null
        return raw.normalizedSourceName()
    }

    private fun String.normalizedSourceName(): String {
        return uppercase()
            .removePrefix("VK_")
            .removePrefix("KEYCODE_")
            .replace("OEM_", "")
            .replace("_", "")
    }

    companion object {
        private val androidToPcSet1ScanCode = mapOf(
            KeyEvent.KEYCODE_1 to 0x02,
            KeyEvent.KEYCODE_2 to 0x03,
            KeyEvent.KEYCODE_3 to 0x04,
            KeyEvent.KEYCODE_4 to 0x05,
            KeyEvent.KEYCODE_5 to 0x06,
            KeyEvent.KEYCODE_6 to 0x07,
            KeyEvent.KEYCODE_7 to 0x08,
            KeyEvent.KEYCODE_8 to 0x09,
            KeyEvent.KEYCODE_9 to 0x0A,
            KeyEvent.KEYCODE_0 to 0x0B,
            KeyEvent.KEYCODE_Q to 0x10,
            KeyEvent.KEYCODE_W to 0x11,
            KeyEvent.KEYCODE_E to 0x12,
            KeyEvent.KEYCODE_R to 0x13,
            KeyEvent.KEYCODE_T to 0x14,
            KeyEvent.KEYCODE_Y to 0x15,
            KeyEvent.KEYCODE_U to 0x16,
            KeyEvent.KEYCODE_I to 0x17,
            KeyEvent.KEYCODE_O to 0x18,
            KeyEvent.KEYCODE_P to 0x19,
            KeyEvent.KEYCODE_LEFT_BRACKET to 0x1A,
            KeyEvent.KEYCODE_RIGHT_BRACKET to 0x1B,
            KeyEvent.KEYCODE_A to 0x1E,
            KeyEvent.KEYCODE_S to 0x1F,
            KeyEvent.KEYCODE_D to 0x20,
            KeyEvent.KEYCODE_F to 0x21,
            KeyEvent.KEYCODE_G to 0x22,
            KeyEvent.KEYCODE_H to 0x23,
            KeyEvent.KEYCODE_J to 0x24,
            KeyEvent.KEYCODE_K to 0x25,
            KeyEvent.KEYCODE_L to 0x26,
            KeyEvent.KEYCODE_SEMICOLON to 0x27,
            KeyEvent.KEYCODE_APOSTROPHE to 0x28,
            KeyEvent.KEYCODE_GRAVE to 0x29,
            KeyEvent.KEYCODE_BACKSLASH to 0x2B,
            KeyEvent.KEYCODE_Z to 0x2C,
            KeyEvent.KEYCODE_X to 0x2D,
            KeyEvent.KEYCODE_C to 0x2E,
            KeyEvent.KEYCODE_V to 0x2F,
            KeyEvent.KEYCODE_B to 0x30,
            KeyEvent.KEYCODE_N to 0x31,
            KeyEvent.KEYCODE_M to 0x32,
            KeyEvent.KEYCODE_COMMA to 0x33,
            KeyEvent.KEYCODE_PERIOD to 0x34,
            KeyEvent.KEYCODE_SLASH to 0x35,
            KeyEvent.KEYCODE_MINUS to 0x0C,
            KeyEvent.KEYCODE_EQUALS to 0x0D,
        )

        private val androidToMacVirtualKeyCode = mapOf(
            KeyEvent.KEYCODE_A to 0,
            KeyEvent.KEYCODE_S to 1,
            KeyEvent.KEYCODE_D to 2,
            KeyEvent.KEYCODE_F to 3,
            KeyEvent.KEYCODE_H to 4,
            KeyEvent.KEYCODE_G to 5,
            KeyEvent.KEYCODE_Z to 6,
            KeyEvent.KEYCODE_X to 7,
            KeyEvent.KEYCODE_C to 8,
            KeyEvent.KEYCODE_V to 9,
            KeyEvent.KEYCODE_B to 11,
            KeyEvent.KEYCODE_Q to 12,
            KeyEvent.KEYCODE_W to 13,
            KeyEvent.KEYCODE_E to 14,
            KeyEvent.KEYCODE_R to 15,
            KeyEvent.KEYCODE_Y to 16,
            KeyEvent.KEYCODE_T to 17,
            KeyEvent.KEYCODE_1 to 18,
            KeyEvent.KEYCODE_2 to 19,
            KeyEvent.KEYCODE_3 to 20,
            KeyEvent.KEYCODE_4 to 21,
            KeyEvent.KEYCODE_6 to 22,
            KeyEvent.KEYCODE_5 to 23,
            KeyEvent.KEYCODE_EQUALS to 24,
            KeyEvent.KEYCODE_9 to 25,
            KeyEvent.KEYCODE_7 to 26,
            KeyEvent.KEYCODE_MINUS to 27,
            KeyEvent.KEYCODE_8 to 28,
            KeyEvent.KEYCODE_0 to 29,
            KeyEvent.KEYCODE_RIGHT_BRACKET to 30,
            KeyEvent.KEYCODE_O to 31,
            KeyEvent.KEYCODE_U to 32,
            KeyEvent.KEYCODE_LEFT_BRACKET to 33,
            KeyEvent.KEYCODE_I to 34,
            KeyEvent.KEYCODE_P to 35,
            KeyEvent.KEYCODE_L to 37,
            KeyEvent.KEYCODE_J to 38,
            KeyEvent.KEYCODE_APOSTROPHE to 39,
            KeyEvent.KEYCODE_K to 40,
            KeyEvent.KEYCODE_SEMICOLON to 41,
            KeyEvent.KEYCODE_BACKSLASH to 42,
            KeyEvent.KEYCODE_COMMA to 43,
            KeyEvent.KEYCODE_SLASH to 44,
            KeyEvent.KEYCODE_N to 45,
            KeyEvent.KEYCODE_M to 46,
            KeyEvent.KEYCODE_PERIOD to 47,
            KeyEvent.KEYCODE_GRAVE to 50,
        )
    }
}

data class HardwareKeyEventInfo(
    val deviceId: Int,
    val keyCode: Int,
    val scanCode: Int = 0,
    val isShiftPressed: Boolean = false,
    val isAltPressed: Boolean = false,
    val isCtrlPressed: Boolean = false,
    val isMetaPressed: Boolean = false,
) {
    companion object {
        fun from(event: KeyEvent): HardwareKeyEventInfo {
            return HardwareKeyEventInfo(
                deviceId = event.deviceId,
                keyCode = event.keyCode,
                scanCode = event.scanCode,
                isShiftPressed = event.isShiftPressed,
                isAltPressed = event.isAltPressed,
                isCtrlPressed = event.isCtrlPressed,
                isMetaPressed = event.isMetaPressed,
            )
        }
    }
}

data class HardwareMappedKey(
    val deviceId: Int,
    val sourceCode: Int,
    val text: String,
    val codePoint: Int,
    val entry: HardwareKeyEntry,
)
