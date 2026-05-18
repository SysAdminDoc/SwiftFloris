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

private fun clip(
    id: Long,
    creationTimestampMs: Long,
    isPinned: Boolean = false,
    isSensitive: Boolean = false,
): ClipboardItem = ClipboardItem(
    id = id,
    type = ItemType.TEXT,
    text = "clip-$id",
    uri = null,
    creationTimestampMs = creationTimestampMs,
    isPinned = isPinned,
    mimeTypes = listOf("text/plain"),
    isSensitive = isSensitive,
)

class ClipboardHistoryEvictionTest : FunSpec({
    test("history size overflow selects the oldest unpinned items") {
        val now = System.currentTimeMillis()
        val newest = clip(1, now)
        val middle = clip(2, now - 1_000L)
        val oldest = clip(3, now - 2_000L)
        val pinnedOldest = clip(4, now - 10_000L, isPinned = true)
        val history = ClipboardHistory(listOf(newest, middle, oldest, pinnedOldest))

        ClipboardHistoryEviction.overflowItems(history, historySizeLimit = 2) shouldBe listOf(oldest)
    }

    test("expiry selects old unpinned items and sensitive items") {
        val now = 1_000_000L
        val oldUnpinned = clip(1, now - 61_000L)
        val freshUnpinned = clip(2, now - 30_000L)
        val oldPinned = clip(3, now - 61_000L, isPinned = true)
        val oldPinnedSensitive = clip(4, now - 11_000L, isPinned = true, isSensitive = true)
        val history = ClipboardHistory(listOf(oldUnpinned, freshUnpinned, oldPinned, oldPinnedSensitive))

        ClipboardHistoryEviction.expiredItems(
            history = history,
            nowMs = now,
            oldEnabled = true,
            oldAfterMinutes = 1,
            sensitiveEnabled = true,
            sensitiveAfterSeconds = 10,
        ) shouldBe listOf(oldUnpinned, oldPinnedSensitive)
    }

    test("closeThenDelete closes every selected item before deleting rows") {
        val first = clip(1, creationTimestampMs = 1_000L)
        val second = clip(2, creationTimestampMs = 2_000L)
        val events = mutableListOf<String>()

        ClipboardHistoryEviction.closeThenDelete(
            items = listOf(first, second),
            closeItem = { events += "close:${it.id}" },
            deleteItems = { items -> events += "delete:${items.joinToString(",") { it.id.toString() }}" },
        )

        events shouldBe listOf("close:1", "close:2", "delete:1,2")
    }
})
