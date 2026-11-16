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

import dev.patrickgold.florisboard.ime.editor.ImeOptions
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class EnterKeyPolicyTest : FunSpec({
    test("Enter runs the field's action") {
        listOf(
            ImeOptions.Action.DONE,
            ImeOptions.Action.GO,
            ImeOptions.Action.NEXT,
            ImeOptions.Action.PREVIOUS,
            ImeOptions.Action.SEARCH,
            ImeOptions.Action.SEND,
        ).forEach { action ->
            EnterKeyPolicy.editorAction(
                action = action,
                flagNoEnterAction = false,
                isMultiLine = false,
                isShiftPressed = false,
            ) shouldBe action
        }
    }

    test("Enter types a newline in a field with no action") {
        EnterKeyPolicy.editorAction(
            action = ImeOptions.Action.NONE,
            flagNoEnterAction = false,
            isMultiLine = true,
            isShiftPressed = false,
        ) shouldBe null
        EnterKeyPolicy.editorAction(
            action = ImeOptions.Action.UNSPECIFIED,
            flagNoEnterAction = false,
            isMultiLine = false,
            isShiftPressed = false,
        ) shouldBe null
    }

    test("no-enter-action fields always get a newline") {
        EnterKeyPolicy.editorAction(
            action = ImeOptions.Action.SEND,
            flagNoEnterAction = true,
            isMultiLine = true,
            isShiftPressed = false,
        ) shouldBe null
    }

    test("Shift+Enter types a newline only in a multi-line field") {
        EnterKeyPolicy.editorAction(
            action = ImeOptions.Action.SEND,
            flagNoEnterAction = false,
            isMultiLine = true,
            isShiftPressed = true,
        ) shouldBe null
        EnterKeyPolicy.editorAction(
            action = ImeOptions.Action.SEARCH,
            flagNoEnterAction = false,
            isMultiLine = false,
            isShiftPressed = true,
        ) shouldBe ImeOptions.Action.SEARCH
    }
})
