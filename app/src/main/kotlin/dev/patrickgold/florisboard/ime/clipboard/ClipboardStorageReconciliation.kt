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

import android.content.Context
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardFileInfo
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardFileStorage
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardFilesDao
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardFilesDatabase
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardHistoryDao
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardMediaProvider
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType

object ClipboardStorageReconciliation {
    data class Plan(
        val historyItemsToDelete: List<ClipboardItem>,
        val fileInfoIdsToDelete: Set<Long>,
        val storageFileIdsToDelete: Set<Long>,
    ) {
        val isEmpty: Boolean
            get() = historyItemsToDelete.isEmpty() &&
                fileInfoIdsToDelete.isEmpty() &&
                storageFileIdsToDelete.isEmpty()
    }

    fun reconcile(context: Context, historyDao: ClipboardHistoryDao) {
        // Files created before media encryption must be upgraded before any
        // provider URI can serve them. The migration keeps the plaintext file
        // in place until its authenticated encrypted replacement is complete.
        ClipboardFileStorage.migratePlaintextFiles(context)
        val filesDb = ClipboardFilesDatabase.new(context)
        try {
            val filesDao = filesDb.clipboardFilesDao()
            apply(
                context = context,
                historyDao = historyDao,
                filesDao = filesDao,
                plan = plan(
                    historyItems = historyDao.getAll(),
                    fileInfos = filesDao.getAll(),
                    storageFileIds = ClipboardFileStorage.listStoredFileIds(context),
                ),
            )
        } finally {
            filesDb.close()
        }
    }

    internal fun apply(
        context: Context,
        historyDao: ClipboardHistoryDao,
        filesDao: ClipboardFilesDao,
        plan: Plan,
    ) {
        if (plan.isEmpty) return
        if (plan.historyItemsToDelete.isNotEmpty()) {
            historyDao.delete(plan.historyItemsToDelete)
        }
        for (id in plan.fileInfoIdsToDelete) {
            filesDao.delete(id)
        }
        for (id in plan.storageFileIdsToDelete) {
            ClipboardFileStorage.deleteById(context, id)
        }
    }

    internal fun plan(
        historyItems: List<ClipboardItem>,
        fileInfos: List<ClipboardFileInfo>,
        storageFileIds: Set<Long>,
        providerBackedMediaId: (ClipboardItem) -> Long? = { it.providerBackedMediaId() },
    ): Plan {
        val referencedPairs = historyItems.mapNotNull { item ->
            providerBackedMediaId(item)?.let { fileId -> item to fileId }
        }
        val referencedFileIds = referencedPairs.map { it.second }.toSet()
        val fileInfoIds = fileInfos.map { it.id }.toSet()

        return Plan(
            historyItemsToDelete = referencedPairs
                .filter { (_, fileId) -> fileId !in storageFileIds }
                .map { it.first },
            fileInfoIdsToDelete = fileInfoIds
                .filter { fileId -> fileId !in referencedFileIds || fileId !in storageFileIds }
                .toSet(),
            storageFileIdsToDelete = storageFileIds - referencedFileIds,
        )
    }

    internal fun ClipboardItem.providerBackedMediaId(): Long? {
        if (type != ItemType.IMAGE && type != ItemType.VIDEO) return null
        val itemUri = uri ?: return null
        if (itemUri.authority != ClipboardMediaProvider.AUTHORITY) return null
        return itemUri.lastPathSegment?.toLongOrNull()
    }
}
