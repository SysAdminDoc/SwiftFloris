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

package dev.patrickgold.florisboard.ime.window

import android.graphics.Rect
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ImeFoldableLayoutTest : FunSpec({
    test("vertical hinge bounds preserve the reported pixel rectangle") {
        val rect = Rect().apply {
            left = 470
            top = 0
            right = 530
            bottom = 800
        }
        ImeVerticalHingeBounds.from(rect) shouldBe ImeVerticalHingeBounds(
            leftPx = 470f,
            topPx = 0f,
            rightPx = 530f,
            bottomPx = 800f,
        )
    }

    test("vertical hinge bounds translate from the root to the IME window") {
        val rootBounds = ImeVerticalHingeBounds(470f, 0f, 530f, 800f)

        rootBounds.translatedBy(dx = -20f, dy = -600f) shouldBe ImeVerticalHingeBounds(
            leftPx = 450f,
            topPx = -600f,
            rightPx = 510f,
            bottomPx = 200f,
        )
    }

    test("empty display-feature bounds are ignored") {
        val rect = Rect().apply {
            left = 470
            top = 800
            right = 530
            bottom = 800
        }
        ImeVerticalHingeBounds.from(rect) shouldBe null
    }
})
