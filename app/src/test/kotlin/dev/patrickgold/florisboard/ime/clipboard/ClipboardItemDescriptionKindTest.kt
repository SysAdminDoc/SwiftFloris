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

import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private fun textItem(content: String, isSensitive: Boolean = false): ClipboardItem = ClipboardItem(
    type = ItemType.TEXT,
    text = content,
    uri = null,
    creationTimestampMs = 1L,
    isPinned = false,
    mimeTypes = listOf("text/plain"),
    isSensitive = isSensitive,
)

class ClipboardItemDescriptionKindTest : FunSpec({

    test("classifies plain URLs for clipboard item description") {
        clipboardItemDescriptionKind(textItem("https://swiftfloris.local")) shouldBe ClipboardItemDescriptionKind.URL
    }

    test("does not classify sensitive URL-like clipboard text") {
        clipboardItemDescriptionKind(textItem("https://secret.example/token", isSensitive = true)) shouldBe null
    }

    test("keeps existing email and phone classifications for non-sensitive text") {
        clipboardItemDescriptionKind(textItem("person@example.com")) shouldBe ClipboardItemDescriptionKind.EMAIL
        clipboardItemDescriptionKind(textItem("+1 555 123 4567")) shouldBe ClipboardItemDescriptionKind.PHONE
    }
})
