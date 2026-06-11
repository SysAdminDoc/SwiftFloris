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

package dev.patrickgold.florisboard.ime.sync

import dev.patrickgold.florisboard.ime.dictionary.FREQUENCY_MAX
import dev.patrickgold.florisboard.ime.dictionary.FREQUENCY_MIN
import dev.patrickgold.florisboard.ime.dictionary.UserDictionaryDao
import dev.patrickgold.florisboard.ime.dictionary.UserDictionaryEntry
import dev.patrickgold.florisboard.lib.FlorisLocale

object PersonalDictionarySyncDaoApplier {
    private const val ALL_LOCALES = "all"

    data class Result(
        val insertedCount: Int,
        val updatedCount: Int,
        val deletedCount: Int,
    ) {
        val changedCount: Int get() = insertedCount + updatedCount + deletedCount
        val isNoOp: Boolean get() = changedCount == 0
    }

    fun snapshot(dao: UserDictionaryDao): List<PersonalDictionarySync.SyncDictionaryWord> {
        return dao.queryAll().mapNotNull { entry ->
            val word = entry.word.trim()
            if (word.isBlank()) return@mapNotNull null
            PersonalDictionarySync.SyncDictionaryWord(
                word = word,
                locale = entry.locale?.takeIf { it.isNotBlank() } ?: ALL_LOCALES,
                frequency = entry.freq.coerceIn(FREQUENCY_MIN, FREQUENCY_MAX),
                shortcut = entry.shortcut?.takeIf { it.isNotBlank() },
            )
        }
    }

    fun apply(
        plan: PersonalDictionarySync.ImportPlan,
        dao: UserDictionaryDao,
    ): Result {
        var inserted = 0
        var updated = 0
        var deleted = 0

        for (word in plan.wordsToDelete) {
            val existing = queryExact(dao, word)
            for (entry in existing) {
                dao.delete(entry)
                deleted++
            }
        }

        for (word in plan.wordsToUpsert) {
            val locale = word.locale.toDaoLocale()
            val existing = queryExact(dao, word)
            val row = UserDictionaryEntry(
                id = existing.firstOrNull()?.id ?: 0L,
                word = word.word.trim(),
                freq = word.frequency.coerceIn(FREQUENCY_MIN, FREQUENCY_MAX),
                locale = locale,
                shortcut = word.shortcut?.takeIf { it.isNotBlank() },
            )
            if (existing.isEmpty()) {
                dao.insert(row)
                inserted++
            } else {
                dao.update(row)
                updated++
            }
        }

        return Result(
            insertedCount = inserted,
            updatedCount = updated,
            deletedCount = deleted,
        )
    }

    private fun queryExact(
        dao: UserDictionaryDao,
        word: PersonalDictionarySync.SyncDictionaryWord,
    ): List<UserDictionaryEntry> {
        val locale = word.locale.toDaoLocale()
        val parsedLocale = locale?.let { runCatching { FlorisLocale.fromTag(it) }.getOrNull() }
        return dao.queryExact(word.word.trim(), parsedLocale)
            .filter { it.locale == locale }
    }

    private fun String.toDaoLocale(): String? {
        return trim().takeIf { it.isNotBlank() && it != ALL_LOCALES }
    }
}
