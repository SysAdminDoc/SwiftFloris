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

import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardFileInfo
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

private fun clipboardMediaItem(
    id: Long,
    type: ItemType = ItemType.IMAGE,
): ClipboardItem = ClipboardItem(
    id = id,
    type = type,
    text = null,
    uri = null,
    creationTimestampMs = id * 1000L,
    isPinned = false,
    mimeTypes = listOf("image/png"),
)

private fun clipboardTextItem(id: Long): ClipboardItem = ClipboardItem(
    id = id,
    type = ItemType.TEXT,
    text = "clip-$id",
    uri = null,
    creationTimestampMs = id * 1000L,
    isPinned = false,
    mimeTypes = listOf("text/plain"),
)

private fun fileInfo(id: Long): ClipboardFileInfo = ClipboardFileInfo(
    id = id,
    displayName = "clip-$id.png",
    size = 123L,
    orientation = 0,
    mimeTypes = listOf("image/png"),
)

private fun reconciliationPlan(
    historyItems: List<ClipboardItem>,
    fileInfos: List<ClipboardFileInfo>,
    storageFileIds: Set<Long>,
    referencedFileIdsByHistoryId: Map<Long, Long>,
): ClipboardStorageReconciliation.Plan {
    return ClipboardStorageReconciliation.plan(
        historyItems = historyItems,
        fileInfos = fileInfos,
        storageFileIds = storageFileIds,
        providerBackedMediaId = { item -> referencedFileIdsByHistoryId[item.id] },
    )
}

class ClipboardStorageReconciliationTest : FunSpec({

    test("keeps referenced provider files with matching storage") {
        val item = clipboardMediaItem(id = 1L)
        val plan = reconciliationPlan(
            historyItems = listOf(item),
            fileInfos = listOf(fileInfo(10L)),
            storageFileIds = setOf(10L),
            referencedFileIdsByHistoryId = mapOf(1L to 10L),
        )

        plan.isEmpty shouldBe true
    }

    test("deletes provider-backed history rows whose stored file is missing") {
        val stale = clipboardMediaItem(id = 1L)
        val plan = reconciliationPlan(
            historyItems = listOf(stale),
            fileInfos = listOf(fileInfo(10L)),
            storageFileIds = emptySet(),
            referencedFileIdsByHistoryId = mapOf(1L to 10L),
        )

        plan.historyItemsToDelete shouldBe listOf(stale)
        plan.fileInfoIdsToDelete shouldBe setOf(10L)
        plan.storageFileIdsToDelete shouldBe emptySet()
    }

    test("deletes stored files and metadata that no history row references") {
        val plan = reconciliationPlan(
            historyItems = listOf(clipboardTextItem(1L)),
            fileInfos = listOf(fileInfo(20L)),
            storageFileIds = setOf(20L, 21L),
            referencedFileIdsByHistoryId = emptyMap(),
        )

        plan.historyItemsToDelete shouldBe emptyList()
        plan.fileInfoIdsToDelete shouldBe setOf(20L)
        plan.storageFileIdsToDelete.shouldContainExactly(20L, 21L)
    }

    test("preserves provider files that still exist even when file info is absent") {
        val item = clipboardMediaItem(id = 1L)
        val plan = reconciliationPlan(
            historyItems = listOf(item),
            fileInfos = emptyList(),
            storageFileIds = setOf(30L),
            referencedFileIdsByHistoryId = mapOf(1L to 30L),
        )

        plan.isEmpty shouldBe true
    }

    test("ignores foreign media URIs") {
        val foreign = clipboardMediaItem(id = 1L)
        val plan = reconciliationPlan(
            historyItems = listOf(foreign),
            fileInfos = emptyList(),
            storageFileIds = emptySet(),
            referencedFileIdsByHistoryId = emptyMap(),
        )

        plan.isEmpty shouldBe true
    }
})
