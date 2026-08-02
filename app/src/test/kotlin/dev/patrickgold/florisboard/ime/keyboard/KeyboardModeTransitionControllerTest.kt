/*
 * Copyright (C) 2026 The SwiftFloris Contributors
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
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class KeyboardModeTransitionControllerTest : FunSpec({
    test("Symbols to clipboard and close restores Symbols") {
        val controller = KeyboardModeTransitionController()

        controller.transitionToKeyboardMode(KeyboardMode.SYMBOLS)
        controller.transitionToImeUiMode(ImeUiMode.CLIPBOARD)
        val restored = controller.transitionToImeUiMode(ImeUiMode.TEXT)

        restored.keyboardMode shouldBe KeyboardMode.SYMBOLS
        restored.imeUiMode shouldBe ImeUiMode.TEXT
        controller.historySize shouldBe 0
    }

    test("Numeric to media and close restores Numeric") {
        val controller = KeyboardModeTransitionController()

        controller.transitionToKeyboardMode(KeyboardMode.NUMERIC)
        controller.transitionToImeUiMode(ImeUiMode.MEDIA)
        val restored = controller.transitionToImeUiMode(ImeUiMode.TEXT)

        restored.keyboardMode shouldBe KeyboardMode.NUMERIC
        restored.imeUiMode shouldBe ImeUiMode.TEXT
    }

    test("nested context toggles remain bounded and close to the original mode") {
        val controller = KeyboardModeTransitionController()

        controller.transitionToKeyboardMode(KeyboardMode.SYMBOLS2)
        controller.transitionToImeUiMode(ImeUiMode.CLIPBOARD)
        controller.transitionToImeUiMode(ImeUiMode.MEDIA)
        val restored = controller.transitionToImeUiMode(ImeUiMode.TEXT)

        restored.keyboardMode shouldBe KeyboardMode.SYMBOLS2
        controller.historySize shouldBe 0
    }

    test("history is capped at sixteen constant-size entries") {
        val controller = KeyboardModeTransitionController()

        repeat(KeyboardModeTransitionController.MAX_HISTORY_ENTRIES + 9) { index ->
            controller.transitionToImeUiMode(
                if (index % 2 == 0) ImeUiMode.MEDIA else ImeUiMode.CLIPBOARD,
            )
        }

        controller.historySize shouldBe KeyboardModeTransitionController.MAX_HISTORY_ENTRIES
    }

    test("underflow falls back to Characters and invalid modes normalize") {
        val controller = KeyboardModeTransitionController()

        controller.transitionToKeyboardMode(KeyboardMode.UNSPECIFIED).keyboardMode shouldBe
            KeyboardMode.CHARACTERS
        controller.transitionToImeUiMode(ImeUiMode.CLIPBOARD)
        controller.clearHistory()
        controller.transitionToImeUiMode(ImeUiMode.TEXT).keyboardMode shouldBe
            KeyboardMode.CHARACTERS
    }

    test("editor and privacy boundaries clear stale context history") {
        val controller = KeyboardModeTransitionController()

        controller.transitionToKeyboardMode(KeyboardMode.SYMBOLS)
        controller.transitionToImeUiMode(ImeUiMode.CLIPBOARD)
        controller.prepareForEditor(preserveClipboard = false)
        controller.setKeyboardModeForEditor(KeyboardMode.NUMERIC)

        controller.historySize shouldBe 0
        controller.state.imeUiMode shouldBe ImeUiMode.TEXT
        controller.state.keyboardMode shouldBe KeyboardMode.NUMERIC

        controller.transitionToImeUiMode(ImeUiMode.MEDIA)
        controller.resetUiModeAndHistory()

        controller.historySize shouldBe 0
        controller.state.imeUiMode shouldBe ImeUiMode.TEXT
        controller.state.keyboardMode shouldBe KeyboardMode.NUMERIC
    }

    test("preserved clipboard context closes to the new editor mode") {
        val controller = KeyboardModeTransitionController()

        controller.transitionToKeyboardMode(KeyboardMode.SYMBOLS)
        controller.transitionToImeUiMode(ImeUiMode.CLIPBOARD)
        controller.prepareForEditor(preserveClipboard = true)
        controller.setKeyboardModeForEditor(KeyboardMode.PHONE)

        controller.transitionToImeUiMode(ImeUiMode.TEXT).keyboardMode shouldBe KeyboardMode.PHONE
        controller.historySize shouldBe 0
    }
})
