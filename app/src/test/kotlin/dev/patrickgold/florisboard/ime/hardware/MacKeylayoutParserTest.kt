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
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

private val SAMPLE_KEYLAYOUT = """
<?xml version="1.0" encoding="UTF-8"?>
<keyboard group="0" id="777" name="Swift ANSI" locale="en-US">
  <layouts>
    <layout first="0" last="0" mapSet="ANSI" modifiers="mods"/>
  </layouts>
  <modifierMap id="mods" defaultIndex="0">
    <keyMapSelect mapIndex="0">
      <modifier keys=""/>
    </keyMapSelect>
    <keyMapSelect mapIndex="1">
      <modifier keys="shift"/>
      <modifier keys="caps"/>
    </keyMapSelect>
    <keyMapSelect mapIndex="2">
      <modifier keys="option"/>
    </keyMapSelect>
    <keyMapSelect mapIndex="3">
      <modifier keys="shift option"/>
    </keyMapSelect>
    <keyMapSelect mapIndex="4">
      <modifier keys="control"/>
    </keyMapSelect>
  </modifierMap>
  <keyMapSet id="ANSI">
    <keyMap index="0">
      <key code="0" output="a"/>
      <key code="1" output="s"/>
      <key code="2" output="d"/>
    </keyMap>
    <keyMap index="1">
      <key code="0" output="A"/>
      <key code="1" output="S"/>
      <key code="2" output="D"/>
    </keyMap>
    <keyMap index="2">
      <key code="0" output="å"/>
      <key code="1" output="ß"/>
    </keyMap>
    <keyMap index="3">
      <key code="0" output="Å"/>
    </keyMap>
    <keyMap index="4">
      <key code="0" output="ignored-control"/>
    </keyMap>
  </keyMapSet>
</keyboard>
""".trimIndent()

class MacKeylayoutParserTest : FunSpec({
    test("parses keylayout metadata") {
        val layout = MacKeylayoutParser.parse(SAMPLE_KEYLAYOUT)
        layout.name shouldBe "Swift ANSI"
        layout.locale shouldBe "en-US"
    }

    test("maps mac key codes and modifier slots into the hardware layout") {
        val layout = MacKeylayoutParser.parse(SAMPLE_KEYLAYOUT)
        layout.scancodeMap shouldContainKey 0
        val a = layout.scancodeMap[0].shouldNotBeNull()
        a.virtualKeyName shouldBe "MAC_0"
        a.normal shouldBe "a".codePointAt(0)
        a.shift shouldBe "A".codePointAt(0)
        a.altGr shouldBe "å".codePointAt(0)
        a.shiftAltGr shouldBe "Å".codePointAt(0)
    }

    test("ignores command/control-only modifier maps") {
        val key = MacKeylayoutParser.parse(SAMPLE_KEYLAYOUT)
            .scancodeMap[0]
            .shouldNotBeNull()
        key.normal shouldBe "a".codePointAt(0)
        key.displayLabel shouldBe null
    }

    test("selects the keyMapSet referenced by layouts") {
        val xml = """
            <keyboard name="Two Sets">
              <layouts>
                <layout first="0" last="0" mapSet="ISO" modifiers="mods"/>
              </layouts>
              <modifierMap id="mods" defaultIndex="0"/>
              <keyMapSet id="ANSI">
                <keyMap index="0"><key code="0" output="a"/></keyMap>
              </keyMapSet>
              <keyMapSet id="ISO">
                <keyMap index="0"><key code="10" output="§"/></keyMap>
              </keyMapSet>
            </keyboard>
        """.trimIndent()
        val layout = MacKeylayoutParser.parse(xml)
        layout.scancodeMap.size shouldBe 1
        layout.scancodeMap[10].shouldNotBeNull().normal shouldBe "§".codePointAt(0)
    }

    test("falls back to keyMap index order when modifierMap is absent") {
        val xml = """
            <keyboard name="Fallback">
              <keyMapSet id="fallback">
                <keyMap index="0"><key code="5" output="g"/></keyMap>
                <keyMap index="1"><key code="5" output="G"/></keyMap>
              </keyMapSet>
            </keyboard>
        """.trimIndent()
        val key = MacKeylayoutParser.parse(xml).scancodeMap[5].shouldNotBeNull()
        key.normal shouldBe "g".codePointAt(0)
        key.shift shouldBe "G".codePointAt(0)
    }

    test("captures dead-key actions with output as dead-key triggers") {
        val xml = """
            <keyboard name="Dead">
              <modifierMap id="mods" defaultIndex="0"/>
              <keyMapSet id="ANSI">
                <keyMap index="0">
                  <key code="50" action="dead_acute"/>
                </keyMap>
              </keyMapSet>
              <actions>
                <action id="dead_acute">
                  <when state="none" output="´"/>
                </action>
              </actions>
            </keyboard>
        """.trimIndent()
        val key = MacKeylayoutParser.parse(xml).scancodeMap[50].shouldNotBeNull()
        key.normal shouldBe "´".codePointAt(0)
        key.deadKeyTrigger shouldBe "´".codePointAt(0)
        key.displayLabel shouldBe "´"
    }

    test("returns Empty on blank, malformed, and non-keyboard XML") {
        MacKeylayoutParser.parse("") shouldBe HardwareKeyboardLayout.Empty
        MacKeylayoutParser.parse("<keyboard><keyMapSet") shouldBe HardwareKeyboardLayout.Empty
        MacKeylayoutParser.parse("<notKeyboard/>") shouldBe HardwareKeyboardLayout.Empty
    }

    test("rejects DOCTYPE / external-entity keylayout files") {
        val xml = """
            <!DOCTYPE keyboard [
              <!ENTITY xxe SYSTEM "file:///etc/passwd">
            ]>
            <keyboard name="XXE">
              <keyMapSet id="ANSI">
                <keyMap index="0"><key code="0" output="&xxe;"/></keyMap>
              </keyMapSet>
            </keyboard>
        """.trimIndent()
        MacKeylayoutParser.parse(xml) shouldBe HardwareKeyboardLayout.Empty
    }
})
