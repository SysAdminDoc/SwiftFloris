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

package dev.patrickgold.florisboard.ime.clipboard

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldHaveLength

class ClipboardPreviewCapTest : FunSpec({

    test("short text is returned unchanged") {
        val text = "hello world"
        capPreviewText(text, GRID_PREVIEW_CHAR_LIMIT) shouldBe text
    }

    test("text at exactly the limit is returned unchanged") {
        val text = "a".repeat(GRID_PREVIEW_CHAR_LIMIT)
        capPreviewText(text, GRID_PREVIEW_CHAR_LIMIT) shouldBe text
    }

    test("text exceeding grid limit is capped with ellipsis") {
        val text = "a".repeat(GRID_PREVIEW_CHAR_LIMIT + 100)
        val result = capPreviewText(text, GRID_PREVIEW_CHAR_LIMIT)
        result shouldHaveLength GRID_PREVIEW_CHAR_LIMIT + 1
        result shouldEndWith "…"
    }

    test("text exceeding popup limit is capped with ellipsis") {
        val text = "b".repeat(POPUP_PREVIEW_CHAR_LIMIT + 500)
        val result = capPreviewText(text, POPUP_PREVIEW_CHAR_LIMIT)
        result shouldHaveLength POPUP_PREVIEW_CHAR_LIMIT + 1
        result shouldEndWith "…"
    }

    test("very large text does not allocate the full string in the result") {
        val text = "x".repeat(500_000)
        val result = capPreviewText(text, GRID_PREVIEW_CHAR_LIMIT)
        result shouldHaveLength GRID_PREVIEW_CHAR_LIMIT + 1
    }

    test("empty text is returned unchanged") {
        capPreviewText("", GRID_PREVIEW_CHAR_LIMIT) shouldBe ""
    }

    test("grid limit constant is 500") {
        GRID_PREVIEW_CHAR_LIMIT shouldBe 500
    }

    test("popup limit constant is 2000") {
        POPUP_PREVIEW_CHAR_LIMIT shouldBe 2000
    }
})
