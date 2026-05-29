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
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.set
import io.kotest.property.checkAll

private fun mediaItem(id: Long): ClipboardItem = ClipboardItem(
    id = id,
    type = ItemType.IMAGE,
    text = null,
    uri = null,
    creationTimestampMs = id * 1000L,
    isPinned = false,
    mimeTypes = listOf("image/png"),
)

private fun fileInfoFor(id: Long): ClipboardFileInfo = ClipboardFileInfo(
    id = id,
    displayName = "clip-$id.png",
    size = 1L,
    orientation = 0,
    mimeTypes = listOf("image/png"),
)

/**
 * RESEARCH_FEATURE_PLAN.md EI6 — property-based coverage of
 * [ClipboardStorageReconciliation.plan] complementing the scenario-based
 * `ClipboardStorageReconciliationTest`. Across randomised combinations of
 * history rows, file-info rows, stored-file ids, and provider-backing
 * references, the post-reconciliation state must hold the invariants the
 * scenario tests only spot-check, and the planner must converge in a single
 * pass (idempotence). Generators are bounded (ids 1..8, sizes 0..6) so failures
 * shrink deterministically.
 */
class ClipboardStorageReconciliationPropertyTest : FunSpec({

    val idArb = Arb.long(1L..8L)

    test("reconciliation leaves no dangling rows or orphan files and converges in one pass") {
        checkAll(
            // history item id -> referenced provider file id (null = a non-provider item, e.g. text)
            Arb.map(idArb, idArb.orNull(0.3), minSize = 0, maxSize = 6),
            Arb.set(idArb, 0..6), // file-info ids
            Arb.set(idArb, 0..6), // stored file ids
        ) { refMap, fileInfoIds, storageFileIds ->
            val historyItems = refMap.keys.map { mediaItem(it) }
            val fileInfos = fileInfoIds.map { fileInfoFor(it) }
            val ref: (ClipboardItem) -> Long? = { refMap[it.id] }

            val plan = ClipboardStorageReconciliation.plan(historyItems, fileInfos, storageFileIds, ref)

            val survivingItems = historyItems.filter { it !in plan.historyItemsToDelete }
            val survivingFileInfos = fileInfos.filter { it.id !in plan.fileInfoIdsToDelete }
            val survivingStorage = storageFileIds - plan.storageFileIdsToDelete
            val survivingRefs = survivingItems.mapNotNull(ref).toSet()

            // (A) No surviving history row points at a stored file that is gone.
            survivingItems.forEach { item ->
                ref(item)?.let { fileId -> (fileId in survivingStorage) shouldBe true }
            }
            // (B) No stored file survives without a surviving history row referencing it.
            survivingStorage.forEach { fileId -> (fileId in survivingRefs) shouldBe true }
            // (C) No file-info row survives unless it is both referenced and stored.
            survivingFileInfos.forEach { info ->
                (info.id in survivingRefs && info.id in survivingStorage) shouldBe true
            }
            // (D) Idempotence: re-planning the cleaned state changes nothing.
            ClipboardStorageReconciliation
                .plan(survivingItems, survivingFileInfos, survivingStorage, ref)
                .isEmpty shouldBe true
        }
    }
})
