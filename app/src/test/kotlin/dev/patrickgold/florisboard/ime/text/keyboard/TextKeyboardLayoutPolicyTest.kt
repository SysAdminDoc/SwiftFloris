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
import io.kotest.matchers.shouldBe

class TextKeyboardLayoutPolicyTest : FunSpec({
    test("three row layouts preserve legacy key height inside a four row shell") {
        TextKeyboardLayoutPolicy.effectiveRowCount(3) shouldBe 4
        TextKeyboardLayoutPolicy.desiredTouchHeightPx(
            mode = KeyboardMode.CHARACTERS,
            rowCount = 3,
            keyboardHeightPx = 240f,
            rowBaseHeightPx = 60f,
        ) shouldBe (67.2f plusOrMinus 0.001f)
    }

    test("four row number-row layouts use proportional row height") {
        val (keyboard, _) = layoutKeyboard(
            listOf(
                "1234567890",
                "qwertyuiop",
                "asdfghjkl",
                "zxcvbnm",
            ),
        )
        val rows = keyboard.rows().asSequence().toList()

        rows[0][0].touchBounds.height shouldBe (60f plusOrMinus 0.001f)
        rows[0][0].touchBounds.top shouldBe (0f plusOrMinus 0.001f)
        rows[3][0].touchBounds.top shouldBe (180f plusOrMinus 0.001f)
        rows[3][0].touchBounds.bottom shouldBe (240f plusOrMinus 0.001f)
        keyboard.getKeyForPos(50f, 30f) shouldBe rows[0][0]
    }

    test("fixed non-character modes keep the baseline key height") {
        TextKeyboardLayoutPolicy.desiredTouchHeightPx(
            mode = KeyboardMode.NUMERIC,
            rowCount = 4,
            keyboardHeightPx = 240f,
            rowBaseHeightPx = 60f,
        ) shouldBe (60f plusOrMinus 0.001f)
    }

    test("popup origin stays centered on source keys for three and four row layouts") {
        val (threeRowKeyboard, threeRowDesiredKey) = layoutKeyboard(
            listOf(
                "qwertyuiop",
                "asdfghjkl",
                "zxcvbnm",
            ),
        )
        assertPopupAnchoredTo(threeRowKeyboard.rows().asSequence().toList()[1][4], threeRowDesiredKey)

        val (fourRowKeyboard, fourRowDesiredKey) = layoutKeyboard(
            listOf(
                "1234567890",
                "qwertyuiop",
                "asdfghjkl",
                "zxcvbnm",
            ),
        )
        assertPopupAnchoredTo(fourRowKeyboard.rows().asSequence().toList()[0][5], fourRowDesiredKey)
    }
})

private fun layoutKeyboard(
    rowLabels: List<String>,
    rowBaseHeight: Float = 60f,
    keyboardWidth: Float = 1000f,
): Pair<TextKeyboard, TextKey> {
    val keyboard = TextKeyboard(
        arrangement = rowLabels.map { labels ->
            labels.map { ch ->
                TextKey(TextKeyData(code = ch.code, label = ch.toString()))
            }.toTypedArray()
        }.toTypedArray(),
        mode = KeyboardMode.CHARACTERS,
        extendedPopupMapping = null,
        extendedPopupMappingDefault = null,
    )
    val keyboardHeight = rowBaseHeight * TextKeyboardLayoutPolicy.effectiveRowCount(rowLabels.size)
    val desiredKey = TextKey(data = TextKeyData.UNSPECIFIED).also { key ->
        key.touchBounds.apply {
            left = 0f
            top = 0f
            right = keyboardWidth / 10f
            bottom = TextKeyboardLayoutPolicy.desiredTouchHeightPx(
                mode = keyboard.mode,
                rowCount = keyboard.rowCount,
                keyboardHeightPx = keyboardHeight,
                rowBaseHeightPx = rowBaseHeight,
            )
        }
        key.visibleBounds.applyFrom(key.touchBounds).deflateBy(4f, 4f)
    }
    keyboard.layout(keyboardWidth, keyboardHeight, desiredKey, extendTouchBoundariesDownwards = false)
    return keyboard to desiredKey
}

private fun assertPopupAnchoredTo(key: TextKey, desiredKey: TextKey) {
    val popup = TextKeyboardLayoutPolicy.popupBounds(
        keyVisibleBounds = key.visibleBounds,
        desiredVisibleBounds = desiredKey.visibleBounds,
        isLandscape = false,
    )
    val keyCenterX = (key.visibleBounds.left + key.visibleBounds.right) / 2f
    val popupCenterX = (popup.left + popup.right) / 2f

    popupCenterX shouldBe (keyCenterX plusOrMinus 0.001f)
    popup.bottom shouldBe (key.visibleBounds.bottom plusOrMinus 0.001f)
    (popup.top < key.visibleBounds.top) shouldBe true
}
