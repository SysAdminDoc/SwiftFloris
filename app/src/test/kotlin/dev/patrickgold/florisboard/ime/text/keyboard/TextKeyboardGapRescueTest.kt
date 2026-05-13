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
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TextKeyboardGapRescueTest : FunSpec({
    test("nearest key rescues taps inside a small row gap") {
        val topLeft = key("a", 0f, 0f, 50f, 50f)
        val topRight = key("b", 50f, 0f, 100f, 50f)
        val bottomLeft = key("c", 0f, 62f, 50f, 112f)
        val bottomRight = key("d", 50f, 62f, 100f, 112f)
        val keyboard = keyboard(
            arrayOf(topLeft, topRight),
            arrayOf(bottomLeft, bottomRight),
        )

        keyboard.getKeyForPos(25f, 58f) shouldBe null
        keyboard.getNearestKeyForPos(25f, 58f) shouldBe bottomLeft
    }

    test("nearest key does not rescue distant touches") {
        val topLeft = key("a", 0f, 0f, 50f, 50f)
        val keyboard = keyboard(arrayOf(topLeft))

        keyboard.getNearestKeyForPos(25f, 95f) shouldBe null
    }

    test("nearest key ignores unavailable keys") {
        val hidden = key("a", 0f, 0f, 50f, 50f).also { it.isVisible = false }
        val available = key("b", 50f, 0f, 100f, 50f)
        val keyboard = keyboard(arrayOf(hidden, available))

        keyboard.getNearestKeyForPos(45f, 25f) shouldBe available
    }
})

private fun keyboard(vararg rows: Array<TextKey>): TextKeyboard {
    return TextKeyboard(
        arrangement = rows.map { it.copyOf() }.toTypedArray(),
        mode = KeyboardMode.CHARACTERS,
        extendedPopupMapping = null,
        extendedPopupMappingDefault = null,
    )
}

private fun key(label: String, left: Float, top: Float, right: Float, bottom: Float): TextKey {
    return TextKey(TextKeyData(code = label.first().code, label = label)).also { key ->
        key.touchBounds.apply {
            this.left = left
            this.top = top
            this.right = right
            this.bottom = bottom
        }
        key.visibleBounds.applyFrom(key.touchBounds)
    }
}
