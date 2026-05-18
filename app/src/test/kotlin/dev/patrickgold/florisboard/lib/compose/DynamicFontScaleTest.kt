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

package dev.patrickgold.florisboard.lib.compose

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DynamicFontScaleTest : FunSpec({
    test("maxLines keeps compact layouts below high font scale") {
        DynamicFontScale.maxLines(
            compact = 1,
            expanded = 3,
            fontScale = 1.20f,
        ) shouldBe 1
    }

    test("maxLines expands wrapping room at high font scale") {
        DynamicFontScale.maxLines(
            compact = 1,
            expanded = 3,
            fontScale = 1.30f,
        ) shouldBe 3
    }

    test("maxLines never returns less than one line or less than compact") {
        DynamicFontScale.maxLines(
            compact = 0,
            expanded = 0,
            fontScale = 2.00f,
        ) shouldBe 1

        DynamicFontScale.maxLines(
            compact = 3,
            expanded = 2,
            fontScale = 2.00f,
        ) shouldBe 3
    }

    test("minHeightDp keeps compact height below high font scale") {
        DynamicFontScale.minHeightDp(
            compact = 36f,
            expanded = 48f,
            fontScale = 1.20f,
        ) shouldBe 36f
    }

    test("minHeightDp applies expanded height at high font scale") {
        DynamicFontScale.minHeightDp(
            compact = 36f,
            expanded = 48f,
            fontScale = 1.30f,
        ) shouldBe 48f
    }
})
