/*
 * Copyright (C) 2026 SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.keyboard

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ApostropheReturnGateTest : FunSpec({
    test("apostrophe in SYMBOLS mode flips back to characters when enabled") {
        ApostropheReturnGate.shouldReturnToCharacters(
            committedText = "'",
            currentMode = KeyboardMode.SYMBOLS,
            autoReturnEnabled = true,
        ) shouldBe true
    }

    test("apostrophe in SYMBOLS2 mode flips back to characters when enabled") {
        ApostropheReturnGate.shouldReturnToCharacters(
            committedText = "'",
            currentMode = KeyboardMode.SYMBOLS2,
            autoReturnEnabled = true,
        ) shouldBe true
    }

    test("pref disabled keeps the symbols panel open") {
        ApostropheReturnGate.shouldReturnToCharacters(
            committedText = "'",
            currentMode = KeyboardMode.SYMBOLS,
            autoReturnEnabled = false,
        ) shouldBe false
    }

    test("non-apostrophe symbol does not trigger return") {
        ApostropheReturnGate.shouldReturnToCharacters(
            committedText = "?",
            currentMode = KeyboardMode.SYMBOLS,
            autoReturnEnabled = true,
        ) shouldBe false
    }

    test("apostrophe from CHARACTERS mode is a no-op") {
        ApostropheReturnGate.shouldReturnToCharacters(
            committedText = "'",
            currentMode = KeyboardMode.CHARACTERS,
            autoReturnEnabled = true,
        ) shouldBe false
    }

    test("apostrophe from NUMERIC modes is a no-op (only SYMBOLS panels carry the apostrophe in shipped layouts)") {
        listOf(
            KeyboardMode.NUMERIC,
            KeyboardMode.NUMERIC_ADVANCED,
            KeyboardMode.PHONE,
            KeyboardMode.PHONE2,
        ).forEach { mode ->
            ApostropheReturnGate.shouldReturnToCharacters(
                committedText = "'",
                currentMode = mode,
                autoReturnEnabled = true,
            ) shouldBe false
        }
    }

    test("typographic curly quote U+2019 is NOT auto-returned (it is a separate code point)") {
        // The shipped layout commits the straight ASCII apostrophe U+0027; the curly one is left
        // to the user's caret to keep the gate conservative.
        ApostropheReturnGate.shouldReturnToCharacters(
            committedText = "’",
            currentMode = KeyboardMode.SYMBOLS,
            autoReturnEnabled = true,
        ) shouldBe false
    }

    test("empty string is a no-op (defensive)") {
        ApostropheReturnGate.shouldReturnToCharacters(
            committedText = "",
            currentMode = KeyboardMode.SYMBOLS,
            autoReturnEnabled = true,
        ) shouldBe false
    }
})
