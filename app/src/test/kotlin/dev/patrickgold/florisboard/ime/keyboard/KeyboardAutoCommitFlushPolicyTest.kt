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

package dev.patrickgold.florisboard.ime.keyboard

import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.ime.text.key.KeyType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class KeyboardAutoCommitFlushPolicyTest : FunSpec({
    test("text media mode flushes before text commit") {
        KeyboardAutoCommitFlushPolicy.shouldFlushBeforeCommit(
            imeUiMode = ImeUiMode.MEDIA,
            keyboardMode = KeyboardMode.CHARACTERS,
            keyType = KeyType.CHARACTER,
            text = "a",
            isFirstCodePointAlphabetic = true,
        ) shouldBe true
    }

    test("alphabetic character keys do not flush pending autocorrect") {
        KeyboardAutoCommitFlushPolicy.shouldFlushBeforeCommit(
            imeUiMode = ImeUiMode.TEXT,
            keyboardMode = KeyboardMode.CHARACTERS,
            keyType = KeyType.CHARACTER,
            text = "a",
            isFirstCodePointAlphabetic = true,
        ) shouldBe false
    }

    test("punctuation character keys flush pending autocorrect") {
        KeyboardAutoCommitFlushPolicy.shouldFlushBeforeCommit(
            imeUiMode = ImeUiMode.TEXT,
            keyboardMode = KeyboardMode.CHARACTERS,
            keyType = KeyType.CHARACTER,
            text = ".",
            isFirstCodePointAlphabetic = false,
        ) shouldBe true
    }

    test("numeric keys in character mode flush like non-letter commits") {
        KeyboardAutoCommitFlushPolicy.shouldFlushBeforeCommit(
            imeUiMode = ImeUiMode.TEXT,
            keyboardMode = KeyboardMode.CHARACTERS,
            keyType = KeyType.NUMERIC,
            text = "1",
            isFirstCodePointAlphabetic = false,
        ) shouldBe true
    }

    test("numeric and phone keyboard modes commit punctuation without autocorrect flush") {
        listOf(
            KeyboardMode.NUMERIC,
            KeyboardMode.NUMERIC_ADVANCED,
            KeyboardMode.PHONE,
            KeyboardMode.PHONE2,
        ).forEach { mode ->
            KeyboardAutoCommitFlushPolicy.shouldFlushBeforeCommit(
                imeUiMode = ImeUiMode.TEXT,
                keyboardMode = mode,
                keyType = KeyType.CHARACTER,
                text = ".",
                isFirstCodePointAlphabetic = false,
            ) shouldBe false
        }
    }

    test("non text key types and empty text do not flush") {
        KeyboardAutoCommitFlushPolicy.shouldFlushBeforeCommit(
            imeUiMode = ImeUiMode.TEXT,
            keyboardMode = KeyboardMode.CHARACTERS,
            keyType = KeyType.FUNCTION,
            text = ".",
            isFirstCodePointAlphabetic = false,
        ) shouldBe false

        KeyboardAutoCommitFlushPolicy.shouldFlushBeforeCommit(
            imeUiMode = ImeUiMode.TEXT,
            keyboardMode = KeyboardMode.CHARACTERS,
            keyType = KeyType.CHARACTER,
            text = "",
        ) shouldBe false
    }
})
