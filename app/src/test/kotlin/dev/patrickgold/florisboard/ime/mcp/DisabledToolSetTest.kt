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

package dev.patrickgold.florisboard.ime.mcp

import dev.patrickgold.florisboard.ime.mcp.DisabledToolSet.ToolKey
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DisabledToolSetTest : FunSpec({

    test("parse returns empty set for blank input") {
        DisabledToolSet.parse("") shouldBe emptySet()
        DisabledToolSet.parse("   ") shouldBe emptySet()
        DisabledToolSet.parse("\n\n") shouldBe emptySet()
    }

    test("parse splits newline-separated entries on the :: separator") {
        DisabledToolSet.parse("com.daemon.a::tool_x\ncom.daemon.b::tool_y") shouldBe setOf(
            ToolKey("com.daemon.a", "tool_x"),
            ToolKey("com.daemon.b", "tool_y"),
        )
    }

    test("parse trims surrounding whitespace and ignores empty lines") {
        DisabledToolSet.parse("  com.daemon.a::tool_x  \n\n\tcom.daemon.b::tool_y\n") shouldBe setOf(
            ToolKey("com.daemon.a", "tool_x"),
            ToolKey("com.daemon.b", "tool_y"),
        )
    }

    test("parse drops malformed entries (no separator, leading separator, trailing separator)") {
        DisabledToolSet.parse("com.daemon.a::tool_x\nmalformed\n::orphan_tool\ncom.daemon.b::") shouldBe setOf(
            ToolKey("com.daemon.a", "tool_x"),
        )
    }

    test("encode produces deterministic sorted output") {
        DisabledToolSet.encode(
            listOf(
                ToolKey("com.daemon.b", "tool_z"),
                ToolKey("com.daemon.a", "tool_y"),
                ToolKey("com.daemon.a", "tool_x"),
            ),
        ) shouldBe """
            |com.daemon.a::tool_x
            |com.daemon.a::tool_y
            |com.daemon.b::tool_z
        """.trimMargin()
    }

    test("encode deduplicates equivalent entries with whitespace differences") {
        DisabledToolSet.encode(
            listOf(
                ToolKey("  com.daemon.a  ", "tool_x"),
                ToolKey("com.daemon.a", "  tool_x  "),
            ),
        ) shouldBe "com.daemon.a::tool_x"
    }

    test("encode drops empty package or empty tool entries") {
        DisabledToolSet.encode(
            listOf(
                ToolKey("", "tool"),
                ToolKey("com.daemon.a", ""),
                ToolKey("com.daemon.a", "tool_x"),
            ),
        ) shouldBe "com.daemon.a::tool_x"
    }

    test("add inserts a new entry into the persisted set") {
        DisabledToolSet.add("", "com.daemon.a", "tool_x") shouldBe "com.daemon.a::tool_x"
        DisabledToolSet.add(
            "com.daemon.a::tool_x",
            "com.daemon.b",
            "tool_y",
        ) shouldBe "com.daemon.a::tool_x\ncom.daemon.b::tool_y"
    }

    test("add is idempotent for duplicates") {
        DisabledToolSet.add(
            "com.daemon.a::tool_x",
            "com.daemon.a",
            "tool_x",
        ) shouldBe "com.daemon.a::tool_x"
    }

    test("add ignores blank inputs") {
        DisabledToolSet.add("com.daemon.a::tool_x", "", "tool") shouldBe "com.daemon.a::tool_x"
        DisabledToolSet.add("com.daemon.a::tool_x", "com.daemon.b", "") shouldBe "com.daemon.a::tool_x"
    }

    test("remove drops an entry from the persisted set") {
        DisabledToolSet.remove(
            "com.daemon.a::tool_x\ncom.daemon.b::tool_y",
            "com.daemon.a",
            "tool_x",
        ) shouldBe "com.daemon.b::tool_y"
    }

    test("remove is a no-op for absent entries") {
        DisabledToolSet.remove(
            "com.daemon.a::tool_x",
            "com.daemon.b",
            "tool_y",
        ) shouldBe "com.daemon.a::tool_x"
    }

    test("contains reflects presence in the persisted set") {
        val s = "com.daemon.a::tool_x\ncom.daemon.b::tool_y"
        DisabledToolSet.contains(s, "com.daemon.a", "tool_x") shouldBe true
        DisabledToolSet.contains(s, "com.daemon.b", "tool_y") shouldBe true
        DisabledToolSet.contains(s, "com.daemon.a", "tool_y") shouldBe false
        DisabledToolSet.contains(s, "com.daemon.c", "tool_x") shouldBe false
        DisabledToolSet.contains(s, "", "tool_x") shouldBe false
        DisabledToolSet.contains(s, "com.daemon.a", "") shouldBe false
    }

    test("toolsFor returns the set of disabled tools for a single daemon") {
        val s = "com.daemon.a::tool_x\ncom.daemon.a::tool_y\ncom.daemon.b::tool_z"
        DisabledToolSet.toolsFor(s, "com.daemon.a") shouldBe setOf("tool_x", "tool_y")
        DisabledToolSet.toolsFor(s, "com.daemon.b") shouldBe setOf("tool_z")
        DisabledToolSet.toolsFor(s, "com.daemon.c") shouldBe emptySet()
        DisabledToolSet.toolsFor(s, "") shouldBe emptySet()
    }
})
