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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AdaptiveTouchModelTest : FunSpec({
    beforeTest {
        AdaptiveTouchModel.reset()
        AdaptiveTouchModel.setActiveSubtype("test:en")
    }

    test("persisted adaptive touch stats restore adjusted key centers") {
        val key = key("g", 0f, 0f, 50f, 50f)

        repeat(35) {
            AdaptiveTouchModel.recordTap(key, touchX = 37.5f, touchY = 30f)
        }
        val encoded = AdaptiveTouchModel.encodeSnapshotForPersistence()

        AdaptiveTouchModel.reset()
        AdaptiveTouchModel.adjustedCenter(
            keyCode = 'g'.code,
            fallbackCenterX = 25f,
            fallbackCenterY = 25f,
            halfWidth = 25f,
            halfHeight = 25f,
        ) shouldBe (25f to 25f)

        AdaptiveTouchModel.restoreSnapshotForPersistence(encoded) shouldBe true
        AdaptiveTouchModel.setActiveSubtype("test:en")

        AdaptiveTouchModel.totalSampleCount() shouldBe 35
        AdaptiveTouchModel.adjustedCenter(
            keyCode = 'g'.code,
            fallbackCenterX = 25f,
            fallbackCenterY = 25f,
            halfWidth = 25f,
            halfHeight = 25f,
        ) shouldBe (37.5f to 30f)
    }

    test("invalid persisted adaptive touch stats are ignored") {
        val key = key("g", 0f, 0f, 50f, 50f)

        repeat(35) {
            AdaptiveTouchModel.recordTap(key, touchX = 37.5f, touchY = 30f)
        }

        AdaptiveTouchModel.restoreSnapshotForPersistence("{not-json") shouldBe false
        AdaptiveTouchModel.totalSampleCount() shouldBe 35
    }

    test("reset clears persisted adaptive touch sample state") {
        val key = key("g", 0f, 0f, 50f, 50f)

        repeat(35) {
            AdaptiveTouchModel.recordTap(key, touchX = 37.5f, touchY = 30f)
        }

        AdaptiveTouchModel.totalSampleCount() shouldBe 35

        AdaptiveTouchModel.reset()

        AdaptiveTouchModel.totalSampleCount() shouldBe 0
        AdaptiveTouchModel.adjustedCenter(
            keyCode = 'g'.code,
            fallbackCenterX = 25f,
            fallbackCenterY = 25f,
            halfWidth = 25f,
            halfHeight = 25f,
        ) shouldBe (25f to 25f)
    }
})

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
