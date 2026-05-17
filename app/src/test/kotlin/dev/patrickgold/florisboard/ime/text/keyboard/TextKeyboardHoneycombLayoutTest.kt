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
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class TextKeyboardHoneycombLayoutTest : FunSpec({
    test("honeycomb layout offsets odd rows by half a column stride") {
        val keyboard = honeycombKeyboard(
            arrayOf(key("q"), key("w")),
            arrayOf(key("a"), key("s")),
        )

        keyboard.layout(
            keyboardWidth = 240f,
            keyboardHeight = 160f,
            desiredKey = desiredKey(),
            extendTouchBoundariesDownwards = true,
        )

        val rows = keyboard.rows().asSequence().toList()
        val columnStride = rows[0][0].touchBounds.width
        (rows[1][0].touchBounds.left - rows[0][0].touchBounds.left) shouldBe
            (columnStride / 2f plusOrMinus 1e-3f)
    }

    test("honeycomb hit testing accepts center points") {
        val q = key("q")
        val keyboard = honeycombKeyboard(arrayOf(q))

        keyboard.layout(
            keyboardWidth = 160f,
            keyboardHeight = 120f,
            desiredKey = desiredKey(),
            extendTouchBoundariesDownwards = true,
        )

        val center = q.touchBounds.center
        keyboard.getKeyForPos(center.x, center.y) shouldBe q
        keyboard.getNearestKeyForPos(center.x, center.y) shouldBe q
    }

    test("honeycomb hit testing rejects bounding-box corners outside the hex") {
        val q = key("q")
        val keyboard = honeycombKeyboard(arrayOf(q))

        keyboard.layout(
            keyboardWidth = 160f,
            keyboardHeight = 120f,
            desiredKey = desiredKey(),
            extendTouchBoundariesDownwards = true,
        )

        keyboard.getKeyForPos(q.touchBounds.left + 1f, q.touchBounds.top + 1f).shouldBeNull()
        keyboard.getNearestKeyForPos(q.touchBounds.left + 1f, q.touchBounds.top + 1f).shouldBeNull()
    }

    test("standard layout still rescues small rectangular row gaps") {
        val topLeft = positionedKey("a", 0f, 0f, 50f, 50f)
        val bottomLeft = positionedKey("b", 0f, 62f, 50f, 112f)
        val keyboard = standardKeyboard(arrayOf(topLeft), arrayOf(bottomLeft))

        keyboard.getKeyForPos(25f, 58f).shouldBeNull()
        keyboard.getNearestKeyForPos(25f, 58f) shouldBe bottomLeft
    }
})

private fun honeycombKeyboard(vararg rows: Array<TextKey>): TextKeyboard {
    return TextKeyboard(
        arrangement = rows.map { it.copyOf() }.toTypedArray(),
        mode = KeyboardMode.CHARACTERS,
        extendedPopupMapping = null,
        extendedPopupMappingDefault = null,
        layoutStyle = TextKeyboardLayoutStyle.Honeycomb,
    )
}

private fun standardKeyboard(vararg rows: Array<TextKey>): TextKeyboard {
    return TextKeyboard(
        arrangement = rows.map { it.copyOf() }.toTypedArray(),
        mode = KeyboardMode.CHARACTERS,
        extendedPopupMapping = null,
        extendedPopupMappingDefault = null,
    )
}

private fun key(label: String): TextKey {
    return TextKey(TextKeyData(code = label.first().code, label = label))
}

private fun positionedKey(label: String, left: Float, top: Float, right: Float, bottom: Float): TextKey {
    return key(label).also { key ->
        key.touchBounds.apply {
            this.left = left
            this.top = top
            this.right = right
            this.bottom = bottom
        }
        key.visibleBounds.applyFrom(key.touchBounds)
    }
}

private fun desiredKey(): TextKey {
    return TextKey(TextKeyData.UNSPECIFIED).also { key ->
        key.touchBounds.apply {
            left = 0f
            top = 0f
            right = 50f
            bottom = 50f
        }
        key.visibleBounds.applyFrom(key.touchBounds).deflateBy(4f, 4f)
    }
}
