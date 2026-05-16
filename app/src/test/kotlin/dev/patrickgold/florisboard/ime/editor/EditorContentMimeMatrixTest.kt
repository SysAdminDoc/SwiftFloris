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

package dev.patrickgold.florisboard.ime.editor

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class EditorContentMimeMatrixTest : FunSpec({

    test("isAccepted: exact match") {
        EditorContentMimeMatrix.isAccepted("image/png", listOf("image/png")) shouldBe true
        EditorContentMimeMatrix.isAccepted("image/png", listOf("text/plain", "image/png")) shouldBe true
    }

    test("isAccepted: editor-side image/* glob accepts every image/X candidate") {
        EditorContentMimeMatrix.isAccepted("image/png", listOf("image/*")) shouldBe true
        EditorContentMimeMatrix.isAccepted("image/webp", listOf("image/*")) shouldBe true
        EditorContentMimeMatrix.isAccepted("image/gif", listOf("image/*")) shouldBe true
        EditorContentMimeMatrix.isAccepted("video/mp4", listOf("image/*")) shouldBe false
    }

    test("isAccepted: editor-side */* accepts any candidate") {
        EditorContentMimeMatrix.isAccepted("image/png", listOf("*/*")) shouldBe true
        EditorContentMimeMatrix.isAccepted("video/mp4", listOf("*/*")) shouldBe true
        EditorContentMimeMatrix.isAccepted("text/plain", listOf("*/*")) shouldBe true
    }

    test("isAccepted: candidate-side glob does not match (editor is authoritative)") {
        EditorContentMimeMatrix.isAccepted("image/*", listOf("image/png")) shouldBe false
        EditorContentMimeMatrix.isAccepted("*/*", listOf("image/png")) shouldBe false
    }

    test("isAccepted: case-insensitive match") {
        EditorContentMimeMatrix.isAccepted("IMAGE/PNG", listOf("image/png")) shouldBe true
        EditorContentMimeMatrix.isAccepted("image/png", listOf("IMAGE/*")) shouldBe true
    }

    test("isAccepted: blank input fails closed") {
        EditorContentMimeMatrix.isAccepted("", listOf("image/png")) shouldBe false
        EditorContentMimeMatrix.isAccepted("   ", listOf("image/png")) shouldBe false
        EditorContentMimeMatrix.isAccepted("image/png", emptyList()) shouldBe false
        EditorContentMimeMatrix.isAccepted("image/png", listOf("")) shouldBe false
    }

    test("isAccepted: malformed editor entries are skipped without crashing") {
        EditorContentMimeMatrix.isAccepted("image/png", listOf("malformed", "image/png")) shouldBe true
        EditorContentMimeMatrix.isAccepted("image/png", listOf("malformed", "image/")) shouldBe false
        EditorContentMimeMatrix.isAccepted("image/png", listOf("malformed", "/png")) shouldBe false
    }

    test("acceptsImages: editor that takes any standard image type returns true") {
        EditorContentMimeMatrix.acceptsImages(listOf("image/png")) shouldBe true
        EditorContentMimeMatrix.acceptsImages(listOf("image/jpeg")) shouldBe true
        EditorContentMimeMatrix.acceptsImages(listOf("image/webp")) shouldBe true
        EditorContentMimeMatrix.acceptsImages(listOf("image/gif")) shouldBe true
        EditorContentMimeMatrix.acceptsImages(listOf("image/*")) shouldBe true
        EditorContentMimeMatrix.acceptsImages(listOf("*/*")) shouldBe true
    }

    test("acceptsImages: text-only editors return false") {
        EditorContentMimeMatrix.acceptsImages(listOf("text/plain")) shouldBe false
        EditorContentMimeMatrix.acceptsImages(emptyList()) shouldBe false
    }

    test("acceptsAny: any-match semantics across the candidate list") {
        EditorContentMimeMatrix.acceptsAny(
            candidates = listOf("image/png", "image/webp"),
            editorMimeTypes = listOf("text/plain", "image/jpeg"),
        ) shouldBe false
        EditorContentMimeMatrix.acceptsAny(
            candidates = listOf("image/png", "image/webp"),
            editorMimeTypes = listOf("text/plain", "image/png"),
        ) shouldBe true
    }

    test("bestMatchFor: returns first preferred candidate the editor accepts") {
        EditorContentMimeMatrix.bestMatchFor(
            available = EditorContentMimeMatrix.PREFERRED_IMAGE_MIMES,
            editorMimeTypes = listOf("image/jpeg", "image/gif"),
        ) shouldBe "image/jpeg"
        EditorContentMimeMatrix.bestMatchFor(
            available = EditorContentMimeMatrix.PREFERRED_IMAGE_MIMES,
            editorMimeTypes = listOf("image/*"),
        ) shouldBe "image/png"
        EditorContentMimeMatrix.bestMatchFor(
            available = EditorContentMimeMatrix.PREFERRED_IMAGE_MIMES,
            editorMimeTypes = listOf("text/plain"),
        ) shouldBe null
    }

    test("preferred-mime lists are non-empty and ordered (PNG-first, WebP-second for sticker)") {
        EditorContentMimeMatrix.PREFERRED_IMAGE_MIMES.isNotEmpty() shouldBe true
        EditorContentMimeMatrix.PREFERRED_STATIC_STICKER_MIMES.first() shouldBe "image/png"
        EditorContentMimeMatrix.PREFERRED_STATIC_STICKER_MIMES[1] shouldBe "image/webp"
        EditorContentMimeMatrix.PREFERRED_ANIMATED_STICKER_MIMES.first() shouldBe "image/webp"
    }
})
