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

package dev.patrickgold.florisboard.ime.dictionary

import dev.patrickgold.florisboard.lib.FlorisLocale

/**
 * ROADMAP §6 N16.1 / docs/archive/SWIFTKEY_PARITY_ROADMAP_2026-05-17 Phase A2 —
 * orchestrates a bulk personal-dictionary import with a snapshot-and-diff
 * pattern so the caller can present a "Imported N words" confirmation
 * sheet and offer an Undo action.
 *
 * The DictionaryImporter returns parsed [PersonalDictionaryEntry] records
 * but doesn't touch persistence; this class is the bridge between the
 * parsed list and the [UserDictionaryDao]. By snapshotting the DAO's
 * known ids before the insert pass and re-reading after, we can identify
 * exactly which rows are new (and therefore rollback-eligible) without
 * adding a new DAO method that returns auto-generated ids.
 *
 * Rows that already existed at the same `(word, locale)` key are updated
 * in place — those are NOT rollback-eligible because the previous freq /
 * shortcut is now lost. The result carries the updated count separately
 * so the UI can be honest about what happened.
 *
 * Stays pure-Kotlin (no Android dependency) so the snapshot-diff is
 * exercisable in JVM unit tests with a fake DAO.
 */
object PersonalDictionaryImportBatch {

    /**
     * Apply [parsedEntries] to [dao], returning a [PersonalDictionaryImportResult]
     * with the rollback-eligible id list, the in-place-updated count, the
     * skipped count, and the total parsed count. [format] is preserved
     * verbatim so the UI can report the source shape ("SwiftKey JSON",
     * "Gboard XML", etc.) without re-detecting it.
     *
     * Skip policy:
     *  - blank `word` field → skipped (DictionaryImporter usually drops
     *    these, but defending again here keeps the contract uniform).
     *  - `frequency` falls outside [FREQUENCY_MIN, FREQUENCY_MAX] → clamped
     *    (NOT skipped — we'd rather keep the entry than drop it).
     */
    fun import(
        parsedEntries: List<PersonalDictionaryEntry>,
        dao: UserDictionaryDao,
        format: DictionaryImportFormat?,
    ): PersonalDictionaryImportResult {
        if (parsedEntries.isEmpty()) {
            return PersonalDictionaryImportResult(
                insertedIds = emptyList(),
                updatedExistingCount = 0,
                skippedCount = 0,
                totalParsedCount = 0,
                format = format,
            )
        }
        // Snapshot all known ids before we mutate. queryAll() returns the
        // full table; for a personal dictionary this is bounded (typical
        // size is sub-10k entries even for power users) so the snapshot
        // cost is negligible compared to the per-entry insert/update RPC.
        val beforeIds: Set<Long> = dao.queryAll().mapTo(HashSet()) { it.id }
        var updated = 0
        var skipped = 0
        for (entry in parsedEntries) {
            val word = entry.word.trim()
            if (word.isEmpty()) {
                skipped++
                continue
            }
            val clampedFreq = entry.frequency.coerceIn(FREQUENCY_MIN, FREQUENCY_MAX)
            val locale = entry.locale?.takeIf { it.isNotBlank() }
            val shortcut = entry.shortcut?.takeIf { it.isNotBlank() }
            val parsedLocale = locale?.let { runCatching { FlorisLocale.fromTag(it) }.getOrNull() }
            val existing = dao.queryExact(word, parsedLocale)
            if (existing.isNotEmpty()) {
                dao.update(
                    UserDictionaryEntry(
                        id = existing.first().id,
                        word = word,
                        freq = clampedFreq,
                        locale = locale,
                        shortcut = shortcut,
                    ),
                )
                updated++
            } else {
                dao.insert(
                    UserDictionaryEntry(
                        id = 0,
                        word = word,
                        freq = clampedFreq,
                        locale = locale,
                        shortcut = shortcut,
                    ),
                )
            }
        }
        // Identify newly-inserted ids by diffing against the before-set.
        val insertedIds = dao.queryAll().mapNotNull { row ->
            row.id.takeIf { it !in beforeIds }
        }
        return PersonalDictionaryImportResult(
            insertedIds = insertedIds,
            updatedExistingCount = updated,
            skippedCount = skipped,
            totalParsedCount = parsedEntries.size,
            format = format,
        )
    }

    /**
     * Undo the inserts from a prior [import] call. Iterates the recorded
     * `insertedIds` and issues a single-row delete for each. In-place
     * updates from the same batch are NOT rolled back — the original
     * freq / shortcut values are no longer available, so reverting them
     * would silently corrupt the user's intent.
     *
     * Returns the number of rows actually deleted, which can be less than
     * `result.insertedIds.size` if the user has manually edited any of
     * the imported entries between import and rollback.
     */
    fun rollback(
        result: PersonalDictionaryImportResult,
        dao: UserDictionaryDao,
    ): Int {
        if (result.insertedIds.isEmpty()) return 0
        val remainingIds = dao.queryAll().mapTo(HashSet()) { it.id }
        var deleted = 0
        for (id in result.insertedIds) {
            if (id !in remainingIds) continue
            // The DAO's @Delete uses the primary key; freq / locale /
            // shortcut on the passed entry are ignored.
            dao.delete(UserDictionaryEntry(id = id, word = "", freq = 0, locale = null, shortcut = null))
            deleted++
        }
        return deleted
    }
}

/**
 * Result envelope for [PersonalDictionaryImportBatch.import]. Carries
 * everything the UI needs to render the summary sheet AND everything
 * the rollback call needs to undo the inserts.
 *
 * - [insertedIds] are the auto-generated row ids of the entries that
 *   were truly new (not present in the DAO before the batch). These
 *   are the rollback-eligible rows.
 * - [updatedExistingCount] is the number of entries that already
 *   existed at the same `(word, locale)` key and got their freq /
 *   shortcut overwritten. NOT rollback-eligible.
 * - [skippedCount] is the number of parsed entries that were dropped
 *   for a blank `word`. Lets the UI honestly report "X imported, Y
 *   skipped" instead of just the larger raw count.
 * - [totalParsedCount] is the size of the raw parsed list.
 * - [format] is the source format the importer detected.
 */
data class PersonalDictionaryImportResult(
    val insertedIds: List<Long>,
    val updatedExistingCount: Int,
    val skippedCount: Int,
    val totalParsedCount: Int,
    val format: DictionaryImportFormat?,
) {
    /** Number of net-new rows the batch added. */
    val insertedCount: Int get() = insertedIds.size

    /** True when nothing actually changed in the dictionary. */
    val noChanges: Boolean get() = insertedCount == 0 && updatedExistingCount == 0

    /** True when the rollback action is meaningful (something to undo). */
    val isRollbackable: Boolean get() = insertedCount > 0
}
