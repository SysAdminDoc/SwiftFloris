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

class LdmlTransformsParserTest : FunSpec({
    test("parses simple compose-key style transforms") {
        val xml = """
            <keyboard locale="fr-FR">
              <transforms type="simple">
                <transformGroup>
                  <transform from="e'" to="é"/>
                  <transform from="e`" to="è"/>
                </transformGroup>
              </transforms>
            </keyboard>
        """.trimIndent()
        val table = LdmlTransformsParser.parse(xml)
        table.rulesByLengthDesc.size shouldBe 2
    }

    test("returns Empty table on malformed XML rather than throwing") {
        LdmlTransformsParser.parse("<keyboard locale=\"fr\"><transforms").isEmpty shouldBe true
    }

    test("returns Empty table on a keyboard with no transforms section") {
        val xml = """<keyboard locale="x"><keys><key id="A01" output="a"/></keys></keyboard>"""
        LdmlTransformsParser.parse(xml).isEmpty shouldBe true
    }

    test("transform rules sort longest-first for greedy matching") {
        val xml = """
            <keyboard locale="x">
              <transforms>
                <transformGroup>
                  <transform from="ae" to="æ"/>
                  <transform from="aae" to="ǽ"/>
                  <transform from="aE" to="Æ"/>
                </transformGroup>
              </transforms>
            </keyboard>
        """.trimIndent()
        val table = LdmlTransformsParser.parse(xml)
        table.rulesByLengthDesc.first().from shouldBe "aae"
        table.rulesByLengthDesc.last().from.length shouldBe 2
    }

    test("engine applies the longest matching rule greedily on input") {
        val table = LdmlTransformsParser.parse(
            """
            <keyboard locale="x">
              <transforms>
                <transformGroup>
                  <transform from="ae" to="æ"/>
                  <transform from="aae" to="ǽ"/>
                </transformGroup>
              </transforms>
            </keyboard>
            """.trimIndent(),
        )
        val engine = LdmlTransformEngine(table)
        // Typing 'a' then 'a' then 'e' should fire the 'aae' rule.
        engine.consumeAll("aae") shouldBe "ǽ"
    }

    test("engine consumes non-matching input verbatim") {
        val table = LdmlTransformsParser.parse(
            """
            <keyboard locale="x">
              <transforms><transformGroup>
                <transform from="e'" to="é"/>
              </transformGroup></transforms>
            </keyboard>
            """.trimIndent(),
        )
        val engine = LdmlTransformEngine(table)
        engine.consumeAll("hello") shouldBe "hello"
    }

    test("engine reset clears the buffer between sessions") {
        val table = LdmlTransformsParser.parse(
            """
            <keyboard locale="x">
              <transforms><transformGroup>
                <transform from="e'" to="é"/>
              </transformGroup></transforms>
            </keyboard>
            """.trimIndent(),
        )
        val engine = LdmlTransformEngine(table)
        engine.consume('e')
        engine.consume('\'')
        engine.reset()
        engine.output.toString() shouldBe ""
    }
})
