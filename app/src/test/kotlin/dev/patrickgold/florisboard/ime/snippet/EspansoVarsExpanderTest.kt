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
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import java.time.Month

class EspansoVarsExpanderTest : FunSpec({
    test("date var with default format") {
        val match = EspansoMatch(
            trigger = ":today",
            replace = "Today is {{date}}.",
            vars = listOf(EspansoVar("date", "date")),
        )
        EspansoVarsExpander.expand(
            match,
            nowProvider = { LocalDateTime.of(2026, Month.MAY, 15, 12, 0) },
        ) shouldBe "Today is 2026-05-15."
    }

    test("date var with custom format pattern") {
        val match = EspansoMatch(
            trigger = ":dmy",
            replace = "Date: {{date}}",
            vars = listOf(EspansoVar("date", "date", mapOf("format" to "dd/MM/yyyy"))),
        )
        EspansoVarsExpander.expand(
            match,
            nowProvider = { LocalDateTime.of(2026, Month.MAY, 15, 12, 0) },
        ) shouldBe "Date: 15/05/2026"
    }

    test("clipboard var inlines the provided clipboard value") {
        val match = EspansoMatch(
            trigger = ":paste",
            replace = "Pasted: {{clip}}",
            vars = listOf(EspansoVar("clip", "clipboard")),
        )
        EspansoVarsExpander.expand(
            match,
            clipboardProvider = { "Hello!" },
        ) shouldBe "Pasted: Hello!"
    }

    test("echo var emits its params.echo value") {
        val match = EspansoMatch(
            trigger = ":hello",
            replace = "Hi {{name}}",
            vars = listOf(EspansoVar("name", "echo", mapOf("echo" to "Matt"))),
        )
        EspansoVarsExpander.expand(match) shouldBe "Hi Matt"
    }

    test("random var picks from semicolon-separated params.choices") {
        val match = EspansoMatch(
            trigger = ":dice",
            replace = "Roll: {{dice}}",
            vars = listOf(EspansoVar("dice", "random", mapOf("choices" to "1;2;3;4;5;6"))),
        )
        // Deterministic random provider: always pick the first.
        EspansoVarsExpander.expand(
            match,
            randomProvider = { it.firstOrNull() },
        ) shouldBe "Roll: 1"
    }

    test("unknown var names pass through unchanged in the template") {
        val match = EspansoMatch(
            trigger = ":x",
            replace = "{{unknown}} survives",
            vars = listOf(EspansoVar("date", "date")),
        )
        EspansoVarsExpander.expand(match) shouldBe "{{unknown}} survives"
    }

    test("match with no vars passes through replace verbatim") {
        val match = EspansoMatch(trigger = ":x", replace = "no template here")
        EspansoVarsExpander.expand(match) shouldBe "no template here"
    }

    test("regex-only match (no trigger) requires either trigger or regex") {
        val match = EspansoMatch(trigger = "", replace = "x", regex = "p[ae]rty")
        match.regex shouldBe "p[ae]rty"
    }
})
