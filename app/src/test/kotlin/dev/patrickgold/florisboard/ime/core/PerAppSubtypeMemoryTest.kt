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

package dev.patrickgold.florisboard.ime.core

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PerAppSubtypeMemoryTest : FunSpec({
    test("remember stores subtype id by package and resolves it") {
        val raw = PerAppSubtypeMemory.remember(
            rawJson = PerAppSubtypeMemory.EmptyJson,
            packageName = "com.example.notes",
            subtypeId = 42L,
            availableSubtypeIds = setOf(42L, 77L),
        )

        raw shouldBe """{"com.example.notes":42}"""
        PerAppSubtypeMemory.resolve(raw, "com.example.notes", setOf(42L)).subtypeId shouldBe 42L
    }

    test("remember ignores blank package names and unavailable subtype ids") {
        val raw = PerAppSubtypeMemory.remember(
            rawJson = """{"com.example.notes":42}""",
            packageName = " ",
            subtypeId = 77L,
            availableSubtypeIds = setOf(42L, 77L),
        )
        val unavailable = PerAppSubtypeMemory.remember(
            rawJson = raw,
            packageName = "com.example.mail",
            subtypeId = 99L,
            availableSubtypeIds = setOf(42L, 77L),
        )

        unavailable shouldBe """{"com.example.notes":42}"""
    }

    test("resolve prunes deleted subtypes from persisted memory") {
        val decision = PerAppSubtypeMemory.resolve(
            rawJson = """{"com.example.mail":77,"com.example.notes":42}""",
            packageName = "com.example.mail",
            availableSubtypeIds = setOf(42L),
        )

        decision.subtypeId shouldBe null
        decision.prunedRawJson shouldBe """{"com.example.notes":42}"""
    }

    test("legacy list shape is accepted and normalized") {
        val raw = """[{"packageName":"com.example.notes","subtypeId":42}]"""

        PerAppSubtypeMemory.count(raw) shouldBe 1
        PerAppSubtypeMemory.resolve(raw, "com.example.notes", setOf(42L)).subtypeId shouldBe 42L
    }

    test("malformed json falls back to empty memory") {
        PerAppSubtypeMemory.count("not-json") shouldBe 0
        PerAppSubtypeMemory.resolve("not-json", "com.example.notes", setOf(42L)).prunedRawJson shouldBe "{}"
    }
})
