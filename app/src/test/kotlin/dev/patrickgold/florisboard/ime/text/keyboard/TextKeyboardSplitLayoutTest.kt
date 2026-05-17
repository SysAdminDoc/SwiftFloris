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

import dev.patrickgold.florisboard.ime.keyboard.KeyboardMode
import dev.patrickgold.florisboard.ime.window.ImeWindowMode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TextKeyboardSplitLayoutTest : FunSpec({
    test("split gutter is enabled only for viable character-mode split windows") {
        TextKeyboardSplitLayout.gutterPx(
            keyboardMode = KeyboardMode.CHARACTERS,
            fixedMode = ImeWindowMode.Fixed.SPLIT,
            splitViable = true,
            defaultGutterPx = 80f,
            keyboardWidthPx = 1000f,
        ) shouldBe 80f

        TextKeyboardSplitLayout.gutterPx(
            keyboardMode = KeyboardMode.SYMBOLS,
            fixedMode = ImeWindowMode.Fixed.SPLIT,
            splitViable = true,
            defaultGutterPx = 80f,
            keyboardWidthPx = 1000f,
        ) shouldBe 0f

        TextKeyboardSplitLayout.gutterPx(
            keyboardMode = KeyboardMode.CHARACTERS,
            fixedMode = ImeWindowMode.Fixed.NORMAL,
            splitViable = true,
            defaultGutterPx = 80f,
            keyboardWidthPx = 1000f,
        ) shouldBe 0f

        TextKeyboardSplitLayout.gutterPx(
            keyboardMode = KeyboardMode.CHARACTERS,
            fixedMode = ImeWindowMode.Fixed.SPLIT,
            splitViable = false,
            defaultGutterPx = 80f,
            keyboardWidthPx = 1000f,
        ) shouldBe 0f
    }

    test("split gutter is clamped and removed from the pre-pass layout width") {
        val gutter = TextKeyboardSplitLayout.gutterPx(
            keyboardMode = KeyboardMode.CHARACTERS,
            fixedMode = ImeWindowMode.Fixed.SPLIT,
            splitViable = true,
            defaultGutterPx = 500f,
            keyboardWidthPx = 1000f,
        )

        gutter shouldBe 350f
        TextKeyboardSplitLayout.layoutWidthPx(keyboardWidthPx = 1000f, gutterPx = gutter) shouldBe 650f
    }
})
