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
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class PersonalDictionaryImportBatchTest : FunSpec({

    test("empty input list yields a no-op result") {
        val dao = FakeUserDictionaryDao()
        dao.seed("hello", 200, "en")

        val result = PersonalDictionaryImportBatch.import(
            parsedEntries = emptyList(),
            dao = dao,
            format = DictionaryImportFormat.JSON,
        )

        result.insertedCount shouldBe 0
        result.updatedExistingCount shouldBe 0
        result.skippedCount shouldBe 0
        result.totalParsedCount shouldBe 0
        result.noChanges shouldBe true
        result.isRollbackable shouldBe false
        dao.queryAll().size shouldBe 1
    }

    test("new entries are inserted and reported as rollback-eligible") {
        val dao = FakeUserDictionaryDao()

        val result = PersonalDictionaryImportBatch.import(
            parsedEntries = listOf(
                PersonalDictionaryEntry(word = "apples", frequency = 200, shortcut = null, locale = "en"),
                PersonalDictionaryEntry(word = "bread", frequency = 180, shortcut = null, locale = "en"),
                PersonalDictionaryEntry(word = "eggs", frequency = 160, shortcut = null, locale = "en"),
            ),
            dao = dao,
            format = DictionaryImportFormat.JSON,
        )

        result.insertedCount shouldBe 3
        result.updatedExistingCount shouldBe 0
        result.skippedCount shouldBe 0
        result.totalParsedCount shouldBe 3
        result.isRollbackable shouldBe true
        dao.queryAll().map { it.word } shouldContainExactlyInAnyOrder listOf("apples", "bread", "eggs")
    }

    test("entries that already exist at (word, locale) get updated in place, not double-inserted") {
        val dao = FakeUserDictionaryDao()
        dao.seed("apples", 100, "en")
        dao.seed("apples", 100, "es") // different locale — also pre-existing

        val result = PersonalDictionaryImportBatch.import(
            parsedEntries = listOf(
                PersonalDictionaryEntry(word = "apples", frequency = 220, shortcut = null, locale = "en"),
                PersonalDictionaryEntry(word = "bread", frequency = 180, shortcut = null, locale = "en"),
            ),
            dao = dao,
            format = DictionaryImportFormat.JSON,
        )

        result.insertedCount shouldBe 1 // bread only
        result.updatedExistingCount shouldBe 1 // apples (en) updated
        result.totalParsedCount shouldBe 2
        // updated entry now carries the new frequency
        val applesEn = dao.queryAll().first { it.word == "apples" && it.locale == "en" }
        applesEn.freq shouldBe 220
        // unrelated apples (es) is untouched
        val applesEs = dao.queryAll().first { it.word == "apples" && it.locale == "es" }
        applesEs.freq shouldBe 100
    }

    test("blank words are skipped, not inserted") {
        val dao = FakeUserDictionaryDao()

        val result = PersonalDictionaryImportBatch.import(
            parsedEntries = listOf(
                PersonalDictionaryEntry(word = "valid", frequency = 200, shortcut = null, locale = "en"),
                PersonalDictionaryEntry(word = "", frequency = 200, shortcut = null, locale = "en"),
                PersonalDictionaryEntry(word = "   ", frequency = 200, shortcut = null, locale = "en"),
            ),
            dao = dao,
            format = DictionaryImportFormat.CSV,
        )

        result.insertedCount shouldBe 1
        result.skippedCount shouldBe 2
        result.totalParsedCount shouldBe 3
        dao.queryAll().single().word shouldBe "valid"
    }

    test("excluded preview rows are not inserted or updated") {
        val dao = FakeUserDictionaryDao()
        dao.seed("existing", 100, "en")

        val result = PersonalDictionaryImportBatch.import(
            parsedEntries = listOf(
                PersonalDictionaryEntry(word = "imported", frequency = 200, shortcut = null, locale = "en"),
                PersonalDictionaryEntry(word = "existing", frequency = 240, shortcut = null, locale = "en"),
                PersonalDictionaryEntry(word = "also-imported", frequency = 180, shortcut = null, locale = "en"),
            ),
            dao = dao,
            format = DictionaryImportFormat.JSON,
            excludedEntryIndexes = setOf(1),
        )

        result.insertedCount shouldBe 2
        result.updatedExistingCount shouldBe 0
        result.excludedCount shouldBe 1
        result.totalParsedCount shouldBe 3
        dao.queryAll().map { it.word } shouldContainExactlyInAnyOrder listOf(
            "existing",
            "imported",
            "also-imported",
        )
        dao.queryAll().first { it.word == "existing" }.freq shouldBe 100
    }

    test("out-of-range frequencies are clamped, not rejected") {
        val dao = FakeUserDictionaryDao()

        PersonalDictionaryImportBatch.import(
            parsedEntries = listOf(
                PersonalDictionaryEntry(word = "tooLow", frequency = -50, shortcut = null, locale = "en"),
                PersonalDictionaryEntry(word = "tooHigh", frequency = 9999, shortcut = null, locale = "en"),
            ),
            dao = dao,
            format = DictionaryImportFormat.JSON,
        )

        dao.queryAll().first { it.word == "tooLow" }.freq shouldBe FREQUENCY_MIN
        dao.queryAll().first { it.word == "tooHigh" }.freq shouldBe FREQUENCY_MAX
    }

    test("rollback deletes only the newly-inserted ids and leaves updates intact") {
        val dao = FakeUserDictionaryDao()
        dao.seed("apples", 100, "en")

        val result = PersonalDictionaryImportBatch.import(
            parsedEntries = listOf(
                PersonalDictionaryEntry(word = "apples", frequency = 220, shortcut = null, locale = "en"),
                PersonalDictionaryEntry(word = "bread", frequency = 180, shortcut = null, locale = "en"),
                PersonalDictionaryEntry(word = "eggs", frequency = 160, shortcut = null, locale = "en"),
            ),
            dao = dao,
            format = DictionaryImportFormat.JSON,
        )

        val deleted = PersonalDictionaryImportBatch.rollback(result, dao)

        deleted shouldBe 2 // bread + eggs only
        // Original apples is preserved but with the new freq from the update.
        // Rolling back the in-place update is intentionally NOT supported.
        dao.queryAll().map { it.word } shouldContainExactlyInAnyOrder listOf("apples")
        dao.queryAll().single().freq shouldBe 220
    }

    test("rollback is idempotent and tolerates a manual delete between import and rollback") {
        val dao = FakeUserDictionaryDao()

        val result = PersonalDictionaryImportBatch.import(
            parsedEntries = listOf(
                PersonalDictionaryEntry(word = "apples", frequency = 220, shortcut = null, locale = "en"),
                PersonalDictionaryEntry(word = "bread", frequency = 180, shortcut = null, locale = "en"),
            ),
            dao = dao,
            format = DictionaryImportFormat.JSON,
        )
        // User manually deletes "bread" before clicking Undo.
        dao.delete(dao.queryAll().first { it.word == "bread" })

        val deleted = PersonalDictionaryImportBatch.rollback(result, dao)

        deleted shouldBe 1 // apples only — bread was already gone
        dao.queryAll().size shouldBe 0
    }

    test("rollback no-ops when nothing was inserted") {
        val dao = FakeUserDictionaryDao()
        dao.seed("apples", 100, "en")

        val result = PersonalDictionaryImportBatch.import(
            parsedEntries = listOf(
                PersonalDictionaryEntry(word = "apples", frequency = 220, shortcut = null, locale = "en"),
            ),
            dao = dao,
            format = DictionaryImportFormat.JSON,
        )

        result.isRollbackable shouldBe false
        val deleted = PersonalDictionaryImportBatch.rollback(result, dao)
        deleted shouldBe 0
        dao.queryAll().size shouldBe 1
    }

    test("shortcut and locale fields round-trip through the import") {
        val dao = FakeUserDictionaryDao()

        PersonalDictionaryImportBatch.import(
            parsedEntries = listOf(
                PersonalDictionaryEntry(word = "on my way", frequency = 240, shortcut = "omw", locale = "en"),
            ),
            dao = dao,
            format = DictionaryImportFormat.JSON,
        )

        val inserted = dao.queryAll().single()
        inserted.word shouldBe "on my way"
        inserted.shortcut shouldBe "omw"
        inserted.locale shouldBe "en"
        inserted.freq shouldBe 240
    }

    test("malformed locale tag falls back to a null-locale insert (no crash)") {
        val dao = FakeUserDictionaryDao()

        val result = PersonalDictionaryImportBatch.import(
            parsedEntries = listOf(
                PersonalDictionaryEntry(word = "weird", frequency = 200, shortcut = null, locale = "!@#nonsense"),
            ),
            dao = dao,
            format = DictionaryImportFormat.JSON,
        )

        // The entry still gets inserted; FlorisLocale.fromTag failure
        // must not abort the whole batch.
        result.insertedCount shouldBe 1
        dao.queryAll().single().word shouldBe "weird"
    }
})

/**
 * Minimal in-memory `UserDictionaryDao` that supports just the methods
 * the batch orchestrator uses (`queryAll`, `queryExact`, `insert`,
 * `update`, `delete`). All other DAO methods throw because the batch
 * never calls them; if a regression starts using one we want the test
 * suite to flag it instead of silently passing.
 */
private class FakeUserDictionaryDao : UserDictionaryDao {
    private val rows = mutableListOf<UserDictionaryEntry>()
    private var nextId = 1L

    fun seed(word: String, freq: Int, locale: String?) {
        rows += UserDictionaryEntry(
            id = nextId++,
            word = word,
            freq = freq,
            locale = locale,
            shortcut = null,
        )
    }

    override fun queryAll(): List<UserDictionaryEntry> = rows.toList()

    override fun queryExact(word: String): List<UserDictionaryEntry> {
        return rows.filter { it.word == word }
    }

    override fun queryExact(word: String, locale: FlorisLocale?): List<UserDictionaryEntry> {
        val localeTag = locale?.localeTag()
        return rows.filter { it.word == word && it.locale == localeTag }
    }

    override fun insert(entry: UserDictionaryEntry) {
        rows += entry.copy(id = nextId++)
    }

    override fun update(entry: UserDictionaryEntry) {
        val idx = rows.indexOfFirst { it.id == entry.id }
        if (idx >= 0) rows[idx] = entry
    }

    override fun delete(entry: UserDictionaryEntry) {
        rows.removeAll { it.id == entry.id }
    }

    override fun deleteAll() {
        rows.clear()
    }

    // Unused methods — fail loudly if a future batch starts calling them.
    override fun query(word: String): List<UserDictionaryEntry> = fail("query(word)")
    override fun query(word: String, locale: FlorisLocale?): List<UserDictionaryEntry> = fail("query(word, locale)")
    override fun queryShortcut(shortcut: String): List<UserDictionaryEntry> = fail("queryShortcut")
    override fun queryShortcut(shortcut: String, locale: FlorisLocale?): List<UserDictionaryEntry> = fail("queryShortcut")
    override fun queryAll(locale: FlorisLocale?): List<UserDictionaryEntry> = fail("queryAll(locale)")
    override fun queryExactFuzzyLocale(word: String, locale: FlorisLocale?): List<UserDictionaryEntry> =
        fail("queryExactFuzzyLocale")
    override fun queryLanguageTagList(): List<String> = fail("queryLanguageTagList")

    private fun fail(name: String): Nothing =
        throw AssertionError("FakeUserDictionaryDao.$name should not be called by PersonalDictionaryImportBatch")
}
