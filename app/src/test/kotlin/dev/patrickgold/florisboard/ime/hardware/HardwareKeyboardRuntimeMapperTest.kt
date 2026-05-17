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
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class HardwareKeyboardRuntimeMapperTest : FunSpec({
    test("maps an Android key through a KLC set-1 scan-code fallback") {
        val mapper = HardwareKeyboardRuntimeMapper()
        mapper.setLayoutForDevice(
            deviceId = 7,
            layout = HardwareKeyboardLayout(
                name = "KLC",
                locale = "en-US",
                scancodeMap = mapOf(
                    0x1E to HardwareKeyEntry(
                        virtualKeyName = "A",
                        normal = "a".codePointAt(0),
                        shift = "A".codePointAt(0),
                    ),
                ),
            ),
        )

        val mapped = mapper.map(
            HardwareKeyEventInfo(
                deviceId = 7,
                keyCode = KeyEvent.KEYCODE_A,
                isShiftPressed = true,
            ),
        ).shouldNotBeNull()

        mapped.sourceCode shouldBe 0x1E
        mapped.text shouldBe "A"
    }

    test("maps an Android key through a macOS virtual-key fallback") {
        val mapper = HardwareKeyboardRuntimeMapper()
        mapper.setLayoutForDevice(
            deviceId = 8,
            layout = HardwareKeyboardLayout(
                name = "Mac",
                locale = "en-US",
                scancodeMap = mapOf(
                    0 to HardwareKeyEntry(
                        virtualKeyName = "MAC_0",
                        normal = "a".codePointAt(0),
                        altGr = "å".codePointAt(0),
                    ),
                ),
            ),
        )

        val mapped = mapper.map(
            HardwareKeyEventInfo(
                deviceId = 8,
                keyCode = KeyEvent.KEYCODE_A,
                isAltPressed = true,
            ),
        ).shouldNotBeNull()

        mapped.sourceCode shouldBe 0
        mapped.text shouldBe "å"
    }

    test("direct scan-code match wins over key-code fallbacks") {
        val mapper = HardwareKeyboardRuntimeMapper()
        mapper.setLayoutForDevice(
            deviceId = 9,
            layout = HardwareKeyboardLayout(
                name = "Direct",
                locale = "x-test",
                scancodeMap = mapOf(
                    42 to HardwareKeyEntry(virtualKeyName = "DIRECT", normal = "x".codePointAt(0)),
                    0x1E to HardwareKeyEntry(virtualKeyName = "A", normal = "a".codePointAt(0)),
                ),
            ),
        )

        val mapped = mapper.map(
            HardwareKeyEventInfo(
                deviceId = 9,
                keyCode = KeyEvent.KEYCODE_A,
                scanCode = 42,
            ),
        ).shouldNotBeNull()

        mapped.sourceCode shouldBe 42
        mapped.text shouldBe "x"
    }

    test("matches source virtual-key names as a final fallback") {
        val mapper = HardwareKeyboardRuntimeMapper()
        mapper.setLayoutForDevice(
            deviceId = 10,
            layout = HardwareKeyboardLayout(
                name = "Names",
                locale = "x-test",
                scancodeMap = mapOf(
                    900 to HardwareKeyEntry(virtualKeyName = "VK_B", normal = "β".codePointAt(0)),
                ),
            ),
        )

        val mapped = mapper.map(
            HardwareKeyEventInfo(
                deviceId = 10,
                keyCode = KeyEvent.KEYCODE_B,
            ),
        ).shouldNotBeNull()

        mapped.sourceCode shouldBe 900
        mapped.text shouldBe "β"
    }

    test("ignores ctrl and meta modified events") {
        val mapper = HardwareKeyboardRuntimeMapper()
        mapper.setLayoutForDevice(
            deviceId = 11,
            layout = HardwareKeyboardLayout(
                name = "KLC",
                locale = "en-US",
                scancodeMap = mapOf(
                    0x1E to HardwareKeyEntry(virtualKeyName = "A", normal = "a".codePointAt(0)),
                ),
            ),
        )

        mapper.map(
            HardwareKeyEventInfo(
                deviceId = 11,
                keyCode = KeyEvent.KEYCODE_A,
                isCtrlPressed = true,
            ),
        ).shouldBeNull()
        mapper.map(
            HardwareKeyEventInfo(
                deviceId = 11,
                keyCode = KeyEvent.KEYCODE_A,
                isMetaPressed = true,
            ),
        ).shouldBeNull()
    }

    test("prunes layouts for detached input devices") {
        val mapper = HardwareKeyboardRuntimeMapper { intArrayOf(7) }
        val layout = HardwareKeyboardLayout(
            name = "Attached",
            locale = "x-test",
            scancodeMap = mapOf(
                0x1E to HardwareKeyEntry(virtualKeyName = "A", normal = "a".codePointAt(0)),
            ),
        )
        mapper.setLayoutForDevice(7, layout)
        mapper.setLayoutForDevice(8, layout)

        mapper.pruneDetachedLayouts() shouldBe setOf(8)
        mapper.layoutForDevice(7).shouldNotBeNull()
        mapper.layoutForDevice(8).shouldBeNull()
    }
})
