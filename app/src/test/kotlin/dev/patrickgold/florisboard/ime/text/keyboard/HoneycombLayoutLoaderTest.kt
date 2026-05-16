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

package dev.patrickgold.florisboard.ime.text.keyboard

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class HoneycombLayoutLoaderTest : FunSpec({

    test("parses the shipped honeycomb.json shape into 5 rows of labels minus modifiers") {
        // Mirrors the layout shipped at
        // app/src/main/assets/.../layouts/characters/honeycomb.json
        val json = """
        [
          [
            { "code":  113, "label": "q" },
            { "code":  119, "label": "w" },
            { "code":  101, "label": "e" },
            { "code":  114, "label": "r" },
            { "code":  116, "label": "t" },
            { "code":  121, "label": "y" },
            { "code":  117, "label": "u" }
          ],
          [
            { "code":  105, "label": "i" },
            { "code":  111, "label": "o" },
            { "code":  112, "label": "p" },
            { "code":   97, "label": "a" },
            { "code":  115, "label": "s" },
            { "code":  100, "label": "d" },
            { "code":  102, "label": "f" }
          ],
          [
            { "code":  103, "label": "g" },
            { "code":  104, "label": "h" },
            { "code":  106, "label": "j" },
            { "code":  107, "label": "k" },
            { "code":  108, "label": "l" },
            { "code":  122, "label": "z" },
            { "code":  120, "label": "x" }
          ],
          [
            { "code":  -11, "label": "shift", "type": "modifier" },
            { "code":   99, "label": "c" },
            { "code":  118, "label": "v" },
            { "code":   98, "label": "b" },
            { "code":  110, "label": "n" },
            { "code":  109, "label": "m" },
            { "code":   -7, "label": "delete", "type": "enter_editing" }
          ],
          [
            { "code": -202, "label": "view_symbols", "type": "system_gui" },
            { "code":   44, "label": "," },
            { "code":   32, "label": "space" },
            { "code":   46, "label": "." },
            { "code":   10, "label": "enter", "type": "enter_editing" }
          ]
        ]
        """.trimIndent()

        val rows = HoneycombLayoutLoader.parse(json)
        rows.size shouldBe 5
        rows[0] shouldBe listOf("q", "w", "e", "r", "t", "y", "u")
        rows[1] shouldBe listOf("i", "o", "p", "a", "s", "d", "f")
        rows[2] shouldBe listOf("g", "h", "j", "k", "l", "z", "x")
        // "shift" + "delete" filtered (type: modifier / enter_editing).
        rows[3] shouldBe listOf("c", "v", "b", "n", "m")
        // "view_symbols" filtered (type: system_gui).
        // "space" + "enter" filtered (literal label match in MODIFIER_LABELS).
        // "," and "." stay — they're punctuation character keys.
        rows[4] shouldBe listOf(",", ".")
    }

    test("filters out cells with no label") {
        val json = """
        [
          [
            { "code":  113, "label": "q" },
            { "code":  999 },
            { "code":  119, "label": "" },
            { "code":  101, "label": "e" }
          ]
        ]
        """.trimIndent()

        val rows = HoneycombLayoutLoader.parse(json)
        rows.size shouldBe 1
        rows[0] shouldBe listOf("q", "e")
    }

    test("filters out cells with non-empty type field (modifier / system_gui / enter_editing)") {
        val json = """
        [
          [
            { "code":  113, "label": "q" },
            { "code":  -11, "label": "anything", "type": "modifier" },
            { "code":  119, "label": "w" }
          ]
        ]
        """.trimIndent()

        val rows = HoneycombLayoutLoader.parse(json)
        rows[0] shouldBe listOf("q", "w")
    }

    test("skips rows that contain only modifier keys (no character cells)") {
        val json = """
        [
          [
            { "code": -11, "label": "shift", "type": "modifier" },
            { "code":  -7, "label": "delete", "type": "enter_editing" }
          ],
          [
            { "code": 113, "label": "q" }
          ]
        ]
        """.trimIndent()

        val rows = HoneycombLayoutLoader.parse(json)
        rows.size shouldBe 1
        rows[0] shouldBe listOf("q")
    }

    test("trims whitespace inside labels") {
        val json = """
        [
          [
            { "code":  113, "label": "  q  " },
            { "code":  119, "label": "\tw\n" }
          ]
        ]
        """.trimIndent()

        val rows = HoneycombLayoutLoader.parse(json)
        rows[0] shouldBe listOf("q", "w")
    }

    test("returns empty list on malformed JSON") {
        HoneycombLayoutLoader.parse("not a json array") shouldBe emptyList()
        HoneycombLayoutLoader.parse("{}") shouldBe emptyList()
        HoneycombLayoutLoader.parse("") shouldBe emptyList()
    }

    test("returns empty list on empty array") {
        HoneycombLayoutLoader.parse("[]") shouldBe emptyList()
    }

    test("ignores unknown fields on key objects") {
        val json = """
        [
          [
            { "code": 113, "label": "q", "popup": { "relevant": [] }, "groupId": 5, "extraField": "ignored" }
          ]
        ]
        """.trimIndent()

        val rows = HoneycombLayoutLoader.parse(json)
        rows[0] shouldBe listOf("q")
    }
})
