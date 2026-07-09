/*
 * Copyright (C) 2026 SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest

class FlorisSpellCheckerSyncBridgeTest : FunSpec({
    test("spellchecker sync bridge returns fallback when NLP work exceeds budget") {
        var result = "unset"
        runTest {
            result = SpellCheckerSyncBridge.runWithTimeout(
                operation = "fixture",
                fallback = "fallback",
                timeoutMs = 25L,
            ) {
                delay(250L)
                "late"
            }
        }

        result shouldBe "fallback"
    }

    test("spellchecker sync bridge returns completed result within budget") {
        var result = "unset"
        runTest {
            result = SpellCheckerSyncBridge.runWithTimeout(
                operation = "fixture",
                fallback = "fallback",
                timeoutMs = 250L,
            ) {
                delay(1L)
                "ok"
            }
        }

        result shouldBe "ok"
    }
})
