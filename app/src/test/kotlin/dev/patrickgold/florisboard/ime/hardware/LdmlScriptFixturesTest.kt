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
import io.kotest.matchers.shouldBe

/**
 * ROADMAP §7 L8.3 — additional LDML script fixtures.
 *
 * The base `KeymanLdmlParser` (L8) and the `LdmlTransformsParser` /
 * displays extension (L8.1 + L8.2) shipped with English + Amharic
 * fixtures. Real Keyman keyboards in the wild span Khmer / Burmese
 * / Tibetan / Lao / Sinhala scripts — each exercises a different
 * combination of `<transforms>` (dead-key composition) + `<displays>`
 * (combining-mark visual hints) + multi-codepoint output strings.
 * This suite pins those edge cases.
 */
class LdmlScriptFixturesTest : FunSpec({

    test("Khmer fixture round-trips combining-mark display labels") {
        val ldml = """
            <keyboard locale="km-KH">
              <names>
                <name value="Khmer (NIDA)"/>
              </names>
              <keys>
                <key id="A01" output="ក"/>
                <key id="A02" output="ខ"/>
                <key id="B01" output="ា"/>
                <key id="B02" output="ោ"/>
              </keys>
              <displays>
                <display to="ា" display="◌ា"/>
                <display to="ោ" display="◌ោ"/>
              </displays>
            </keyboard>
        """.trimIndent()
        val layout = KeymanLdmlParser.parse(ldml)
        layout.locale shouldBe "km-KH"
        layout.scancodeMap.size shouldBe 4
        layout.scancodeMap.values.first { it.virtualKeyName == "B01" }
            .displayLabel shouldBe "◌ា"
        layout.scancodeMap.values.first { it.virtualKeyName == "B02" }
            .displayLabel shouldBe "◌ោ"
        layout.scancodeMap.values.first { it.virtualKeyName == "A01" }
            .displayLabel shouldBe null
    }

    test("Burmese fixture combines transforms + displays for medial Ya") {
        val ldml = """
            <keyboard locale="my-MM">
              <names>
                <name value="Myanmar Unicode"/>
              </names>
              <keys>
                <key id="C01" output="က"/>
                <key id="C02" output="ြ"/>
              </keys>
              <displays>
                <display to="ြ" display="◌ြ"/>
              </displays>
              <transforms type="simple">
                <transformGroup>
                  <transform from="ကြ" to="ြ"/>
                </transformGroup>
              </transforms>
            </keyboard>
        """.trimIndent()
        val layout = KeymanLdmlParser.parse(ldml)
        layout.locale shouldBe "my-MM"
        val transforms = LdmlTransformsParser.parse(ldml)
        transforms.isEmpty shouldBe false
        transforms.rulesByLengthDesc.first().from shouldBe "ကြ"
    }

    test("Tibetan fixture covers consonant + vowel-mark transforms") {
        val ldml = """
            <keyboard locale="bo-CN">
              <names>
                <name value="Tibetan Wylie"/>
              </names>
              <keys>
                <key id="E01" output="ཀ"/>
                <key id="E02" output="ི"/>
              </keys>
              <displays>
                <display to="ི" display="◌ི"/>
              </displays>
              <transforms type="simple">
                <transformGroup>
                  <transform from="ki" to="ཀི"/>
                </transformGroup>
              </transforms>
            </keyboard>
        """.trimIndent()
        val layout = KeymanLdmlParser.parse(ldml)
        layout.scancodeMap.values.first { it.virtualKeyName == "E02" }
            .displayLabel shouldBe "◌ི"
        val transformTable = LdmlTransformsParser.parse(ldml)
        val engine = LdmlTransformEngine(transformTable)
        engine.consumeAll("ki") shouldBe "ཀི"
    }

    test("Lao fixture: bare consonant has no display override, tone-mark does") {
        val ldml = """
            <keyboard locale="lo-LA">
              <names>
                <name value="Lao 2008"/>
              </names>
              <keys>
                <key id="D01" output="ກ"/>
                <key id="D02" output="່"/>
              </keys>
              <displays>
                <display to="່" display="◌່"/>
              </displays>
            </keyboard>
        """.trimIndent()
        val layout = KeymanLdmlParser.parse(ldml)
        layout.scancodeMap.values.first { it.virtualKeyName == "D01" }
            .displayLabel shouldBe null
        layout.scancodeMap.values.first { it.virtualKeyName == "D02" }
            .displayLabel shouldBe "◌່"
    }

    test("Sinhala fixture survives mixed transforms-then-displays section ordering") {
        val ldml = """
            <keyboard locale="si-LK">
              <names>
                <name value="Sinhala Wijesekara"/>
              </names>
              <transforms type="simple">
                <transformGroup>
                  <transform from="ka" to="ක"/>
                </transformGroup>
              </transforms>
              <keys>
                <key id="F01" output="ක"/>
                <key id="F02" output="්"/>
              </keys>
              <displays>
                <display to="්" display="◌්"/>
              </displays>
            </keyboard>
        """.trimIndent()
        val layout = KeymanLdmlParser.parse(ldml)
        layout.locale shouldBe "si-LK"
        layout.scancodeMap.size shouldBe 2
        val transforms = LdmlTransformsParser.parse(ldml)
        transforms.rulesByLengthDesc.first().to shouldBe "ක"
    }
})
