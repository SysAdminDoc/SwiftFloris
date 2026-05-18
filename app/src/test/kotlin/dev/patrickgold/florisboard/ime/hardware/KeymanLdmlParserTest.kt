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

    test("shift attribute is preferred over longPress for the shift slot") {
        // When both are declared, LDML's shift= is the shift-modifier
        // mapping and longPress= is a space-separated alternates list.
        // shift= must win.
        val ldml = """
            <keyboard locale="x-test">
              <keys>
                <key id="A01" output="a" shift="A" longPress="ä á à"/>
              </keys>
            </keyboard>
        """.trimIndent()
        val layout = KeymanLdmlParser.parse(ldml)
        val a01 = layout.scancodeMap.values.first { it.virtualKeyName == "A01" }
        a01.normal shouldBe 'a'.code
        a01.shift shouldBe 'A'.code
    }

    test("multi-alternate longPress with no shift leaves shift slot null") {
        // longPress with spaces is a list of alternates; none should be
        // promoted into the shift slot. The alternates list is populated
        // into longPressAlternates for the future popup-routing slice.
        val ldml = """
            <keyboard locale="x-test">
              <keys>
                <key id="A01" output="a" longPress="ä á à"/>
              </keys>
            </keyboard>
        """.trimIndent()
        val layout = KeymanLdmlParser.parse(ldml)
        val a01 = layout.scancodeMap.values.first { it.virtualKeyName == "A01" }
        a01.normal shouldBe 'a'.code
        a01.shift shouldBe null
        a01.longPressAlternates shouldBe listOf('ä'.code, 'á'.code, 'à'.code)
    }

    test("single-alternate longPress with no shift remains usable as shift fallback") {
        // Preserves the Amharic SERA-style backward-compat case where the
        // LDML author used longPress as the shift workaround.
        // The alternates list still gets populated — both surfaces are
        // accurate representations of the LDML.
        val ldml = """
            <keyboard locale="x-test">
              <keys>
                <key id="A01" output="a" longPress="A"/>
              </keys>
            </keyboard>
        """.trimIndent()
        val layout = KeymanLdmlParser.parse(ldml)
        val a01 = layout.scancodeMap.values.first { it.virtualKeyName == "A01" }
        a01.normal shouldBe 'a'.code
        a01.shift shouldBe 'A'.code
        a01.longPressAlternates shouldBe listOf('A'.code)
    }

    test("longPress alternates are populated alongside shift when both attributes are present") {
        // shift= wins for the shift slot (v1.8.92); longPressAlternates
        // independently captures the full list for the long-press popup.
        val ldml = """
            <keyboard locale="x-test">
              <keys>
                <key id="A01" output="a" shift="A" longPress="ä á à â"/>
              </keys>
            </keyboard>
        """.trimIndent()
        val layout = KeymanLdmlParser.parse(ldml)
        val a01 = layout.scancodeMap.values.first { it.virtualKeyName == "A01" }
        a01.normal shouldBe 'a'.code
        a01.shift shouldBe 'A'.code
        a01.longPressAlternates shouldBe listOf('ä'.code, 'á'.code, 'à'.code, 'â'.code)
    }

    test("empty longPressAlternates when longPress attribute is absent") {
        val ldml = """
            <keyboard locale="x-test">
              <keys>
                <key id="A01" output="a" shift="A"/>
              </keys>
            </keyboard>
        """.trimIndent()
        val layout = KeymanLdmlParser.parse(ldml)
        val a01 = layout.scancodeMap.values.first { it.virtualKeyName == "A01" }
        a01.longPressAlternates shouldBe emptyList()
    }

    test("display override with to attribute labels the matching key output") {
        val ldml = """
            <keyboard locale="km-KH">
              <keys>
                <key id="A01" output="ក"/>
                <key id="A02" output="ខ"/>
              </keys>
              <displays>
                <display to="ក" display="ka"/>
              </displays>
            </keyboard>
        """.trimIndent()
        val layout = KeymanLdmlParser.parse(ldml)
        layout.scancodeMap.values.first { it.virtualKeyName == "A01" }.displayLabel shouldBe "ka"
        layout.scancodeMap.values.first { it.virtualKeyName == "A02" }.displayLabel shouldBe null
    }

    test("display override with output attribute supports current LDML keyboard syntax") {
        val ldml = """
            <keyboard locale="lo-LA">
              <keys>
                <key id="A01" output="\u{0303}"/>
              </keys>
              <displays>
                <display output="\u{0303}" display="\u{25CC}\u{0303}"/>
              </displays>
            </keyboard>
        """.trimIndent()
        val key = KeymanLdmlParser.parse(ldml).scancodeMap.values.single()
        key.normal shouldBe 0x0303
        key.displayLabel shouldBe "◌̃"
    }

    test("display override with keyId labels the matching key id") {
        val ldml = """
            <keyboard locale="bo-CN">
              <keys>
                <key id="E01" output="་"/>
                <key id="E02" output="།"/>
              </keys>
              <displays>
                <display keyId="E02" display="shad"/>
              </displays>
            </keyboard>
        """.trimIndent()
        val layout = KeymanLdmlParser.parse(ldml)
        layout.scancodeMap.values.first { it.virtualKeyName == "E01" }.displayLabel shouldBe null
        layout.scancodeMap.values.first { it.virtualKeyName == "E02" }.displayLabel shouldBe "shad"
    }

    test("display override with id attribute supports legacy draft key targeting") {
        val ldml = """
            <keyboard locale="x-test">
              <keys>
                <key id="shiftLayer" output="⇧"/>
              </keys>
              <displays>
                <display id="shiftLayer" display="Shift"/>
              </displays>
            </keyboard>
        """.trimIndent()
        val key = KeymanLdmlParser.parse(ldml).scancodeMap.values.single()
        key.displayLabel shouldBe "Shift"
    }

    test("unmatched shared display maps do not create synthetic key entries") {
        val ldml = """
            <keyboard locale="km-KH">
              <keys>
                <key id="A01" output="ក"/>
              </keys>
              <displays>
                <display to="ឃ" display="kho"/>
                <display keyId="missing" display="Missing"/>
              </displays>
            </keyboard>
        """.trimIndent()
        val layout = KeymanLdmlParser.parse(ldml)
        layout.scancodeMap.size shouldBe 1
        layout.scancodeMap.values.single().displayLabel shouldBe null
    }

    test("Burmese display fixture round-trips combining medial label") {
        val ldml = """
            <keyboard locale="my-MM">
              <names>
                <name value="Myanmar Visual"/>
              </names>
              <keys>
                <key id="C01" output="ြ"/>
                <key id="C02" output="က"/>
              </keys>
              <displays>
                <display to="ြ" display="◌ြ"/>
              </displays>
            </keyboard>
        """.trimIndent()
        val layout = KeymanLdmlParser.parse(ldml)
        layout.locale shouldBe "my-MM"
        layout.name shouldBe "Myanmar Visual"
        layout.scancodeMap.values.first { it.virtualKeyName == "C01" }.displayLabel shouldBe "◌ြ"
        layout.scancodeMap.values.first { it.virtualKeyName == "C02" }.displayLabel shouldBe null
    }
})
