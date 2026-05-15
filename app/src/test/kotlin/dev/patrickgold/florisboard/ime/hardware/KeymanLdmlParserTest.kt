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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe

private val AMHARIC_LDML = """
<?xml version="1.0" encoding="UTF-8"?>
<keyboard locale="am-ET">
  <names>
    <name value="Amharic SERA"/>
  </names>
  <keys>
    <key id="A01" output="ሀ" longPress="ሁ"/>
    <key id="A02" output="ለ"/>
    <key id="A03" output="ሐ"/>
    <key id="B01" output="ሰ" longPress="ሠ"/>
    <key id="B02" output="ረ"/>
  </keys>
</keyboard>
""".trimIndent()

class KeymanLdmlParserTest : FunSpec({
    test("parses an Amharic LDML keyboard's keys + locale + name") {
        val layout = KeymanLdmlParser.parse(AMHARIC_LDML)
        layout.locale shouldBe "am-ET"
        layout.name shouldBe "Amharic SERA"
        layout.scancodeMap.size shouldBe 5
    }

    test("normal + shift output round-trip through the scancode map") {
        val layout = KeymanLdmlParser.parse(AMHARIC_LDML)
        val a01 = layout.scancodeMap.values.first { it.virtualKeyName == "A01" }
        a01.normal shouldBe "ሀ".codePointAt(0)
        a01.shift shouldBe "ሁ".codePointAt(0)
    }

    test("returns Empty when input is blank") {
        KeymanLdmlParser.parse("") shouldBe HardwareKeyboardLayout.Empty
    }

    test("returns Empty when input has no <key> entries") {
        val emptyKeyboard = """
            <keyboard locale="en-US"><names><name value="None"/></names></keyboard>
        """.trimIndent()
        KeymanLdmlParser.parse(emptyKeyboard) shouldBe HardwareKeyboardLayout.Empty
    }

    test("returns Empty on malformed XML rather than throwing") {
        val malformed = "<keyboard locale=\"en\"><keys><key id=\"A01\" output="
        KeymanLdmlParser.parse(malformed) shouldBe HardwareKeyboardLayout.Empty
    }

    test("scancode map preserves declaration order from the LDML <keys> section") {
        val layout = KeymanLdmlParser.parse(AMHARIC_LDML)
        val ids = layout.scancodeMap.values.map { it.virtualKeyName }
        ids shouldBe listOf("A01", "A02", "A03", "B01", "B02")
    }

    test("keys without output AND without longPress / shift are dropped") {
        val ldml = """
            <keyboard locale="x-test">
              <keys>
                <key id="A01" output="a"/>
                <key id="A02"/>
              </keys>
            </keyboard>
        """.trimIndent()
        val layout = KeymanLdmlParser.parse(ldml)
        layout.scancodeMap.values.shouldNotBeEmpty()
        layout.scancodeMap.size shouldBe 1
    }
})
