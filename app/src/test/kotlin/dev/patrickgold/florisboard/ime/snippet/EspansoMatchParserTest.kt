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

package dev.patrickgold.florisboard.ime.snippet

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

class EspansoMatchParserTest : FunSpec({
    test("parses simple trigger -> replace pairs") {
        val yaml = """
            matches:
              - trigger: ":bug"
                replace: "I'm sorry"
              - trigger: ":sig"
                replace: "Best regards, Matt"
        """.trimIndent()
        val matches = EspansoMatchParser.parse(yaml)
        matches shouldBe listOf(
            EspansoMatch(":bug", "I'm sorry"),
            EspansoMatch(":sig", "Best regards, Matt"),
        )
    }

    test("handles escaped newline in inline replace") {
        val yaml = """
            matches:
              - trigger: ":lf"
                replace: "line1\nline2"
        """.trimIndent()
        EspansoMatchParser.parse(yaml).single() shouldBe EspansoMatch(":lf", "line1\nline2")
    }

    test("handles literal block scalar | for multi-line replace") {
        val yaml = """
            matches:
              - trigger: ":greet"
                replace: |
                  Hello there,
                  Hope you're well.
        """.trimIndent()
        val match = EspansoMatchParser.parse(yaml).single()
        match.trigger shouldBe ":greet"
        match.replace.lines() shouldContain "Hello there,"
        match.replace.lines() shouldContain "Hope you're well."
    }

    test("ignores full-line comments outside block scalars") {
        val yaml = """
            # global comment
            matches:
              # an inner comment
              - trigger: ":hi"
                replace: "hello"
        """.trimIndent()
        EspansoMatchParser.parse(yaml) shouldBe listOf(EspansoMatch(":hi", "hello"))
    }

    test("returns empty list when matches section is absent") {
        val yaml = """
            global_vars:
              - name: foo
                value: bar
        """.trimIndent()
        EspansoMatchParser.parse(yaml) shouldBe emptyList()
    }

    test("skips entries with blank triggers") {
        val yaml = """
            matches:
              - trigger: ""
                replace: "should be skipped"
              - trigger: ":ok"
                replace: "kept"
        """.trimIndent()
        EspansoMatchParser.parse(yaml) shouldBe listOf(EspansoMatch(":ok", "kept"))
    }

    test("tolerates trailing whitespace and mixed quoting") {
        val yaml = """
            matches:
              - trigger: ':abc'
                replace: hello world
        """.trimIndent()
        val match = EspansoMatchParser.parse(yaml).single()
        match.trigger shouldBe ":abc"
        match.replace shouldBe "hello world"
    }

    test("diagnostics report skipped entries with blank triggers") {
        val yaml = """
            matches:
              - trigger: ""
                replace: "should be skipped"
              - trigger: ":ok"
                replace: "kept"
        """.trimIndent()
        val result = EspansoMatchParser.parseWithDiagnostics(yaml)
        result.matches.size shouldBe 1
        result.diagnostics.skippedCount shouldBe 1
        result.diagnostics.hasSkipped shouldBe true
        result.diagnostics.details.size shouldBe 1
    }

    test("diagnostics report zero skipped for clean input") {
        val yaml = """
            matches:
              - trigger: ":a"
                replace: "alpha"
              - trigger: ":b"
                replace: "beta"
        """.trimIndent()
        val result = EspansoMatchParser.parseWithDiagnostics(yaml)
        result.matches.size shouldBe 2
        result.diagnostics.skippedCount shouldBe 0
        result.diagnostics.hasSkipped shouldBe false
    }
})
