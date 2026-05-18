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
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

private fun restoredItem(type: ItemType, mimeTypes: List<String>): ClipboardItem = ClipboardItem(
    type = type,
    text = null,
    uri = null,
    creationTimestampMs = 1L,
    isPinned = false,
    mimeTypes = mimeTypes,
)

class ClipboardRestoredFileInfoTest : FunSpec({

    test("creates image file metadata from restored clipboard item data") {
        val fileInfo = ClipboardRestoredFileInfo.create(
            item = restoredItem(ItemType.IMAGE, listOf("image/png")),
            fileId = 123L,
            fileSizeBytes = 456L,
        )

        fileInfo?.id shouldBe 123L
        fileInfo?.displayName shouldBe "Restored image 123"
        fileInfo?.size shouldBe 456L
        fileInfo?.orientation shouldBe 0
        fileInfo?.mimeTypes?.shouldContainExactly("image/png")
    }

    test("creates video file metadata from restored clipboard item data") {
        val fileInfo = ClipboardRestoredFileInfo.create(
            item = restoredItem(ItemType.VIDEO, listOf("video/mp4")),
            fileId = 456L,
            fileSizeBytes = 789L,
        )

        fileInfo?.id shouldBe 456L
        fileInfo?.displayName shouldBe "Restored video 456"
        fileInfo?.size shouldBe 789L
        fileInfo?.orientation shouldBe 0
        fileInfo?.mimeTypes?.shouldContainExactly("video/mp4")
    }

    test("does not create file metadata for restored text clipboard items") {
        ClipboardRestoredFileInfo.create(
            item = restoredItem(ItemType.TEXT, listOf("text/plain")),
            fileId = 123L,
            fileSizeBytes = 456L,
        ) shouldBe null
    }
})
