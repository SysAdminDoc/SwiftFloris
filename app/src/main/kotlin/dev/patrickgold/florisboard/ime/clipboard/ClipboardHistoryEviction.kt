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

internal object ClipboardHistoryEviction {
    fun overflowItems(history: ClipboardHistory, historySizeLimit: Int): List<ClipboardItem> {
        val nonPinnedItems = history.recent + history.other
        val removeCount = nonPinnedItems.size - historySizeLimit.coerceAtLeast(0)
        if (removeCount <= 0) return emptyList()
        return nonPinnedItems.asReversed().take(removeCount)
    }

    fun expiredItems(
        history: ClipboardHistory,
        nowMs: Long,
        oldEnabled: Boolean,
        oldAfterMinutes: Int,
        sensitiveEnabled: Boolean,
        sensitiveAfterSeconds: Int,
    ): List<ClipboardItem> {
        val itemsToRemove = linkedSetOf<ClipboardItem>()
        if (oldEnabled) {
            val expiryTime = nowMs - oldAfterMinutes.coerceAtLeast(0) * 60_000L
            itemsToRemove.addAll(
                history.unpinned.filter { it.creationTimestampMs < expiryTime },
            )
        }
        if (sensitiveEnabled) {
            val expiryTime = nowMs - sensitiveAfterSeconds.coerceAtLeast(0) * 1_000L
            itemsToRemove.addAll(
                history.all.filter { it.isSensitive && it.creationTimestampMs < expiryTime },
            )
        }
        return itemsToRemove.toList()
    }

    fun closeThenDelete(
        items: List<ClipboardItem>,
        closeItem: (ClipboardItem) -> Unit,
        deleteItems: (List<ClipboardItem>) -> Unit,
    ) {
        if (items.isEmpty()) return
        for (item in items) {
            closeItem(item)
        }
        deleteItems(items)
    }
}
