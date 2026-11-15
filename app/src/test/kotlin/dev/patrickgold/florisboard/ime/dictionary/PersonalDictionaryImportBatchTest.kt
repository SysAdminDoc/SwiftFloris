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
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
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
            runInTransaction = dao::runInTransaction,
        )

        result.insertedCount shouldBe 0
        result.updatedExistingCount shouldBe 0
        result.skippedCount shouldBe 0
        result.totalParsedCount shouldBe 0
        result.noChanges shouldBe true
        result.isRollbackable shouldBe false
        dao.queryAll().size shouldBe 1
    }

    test("a BCP-47 locale tag is stored the way the lookup spells it") {
        // The column is a raw string; queryExact takes a FlorisLocale that Room
        // converts through localeTag(), which joins with '_'. A SwiftKey export
        // supplies BCP-47, so storing the tag verbatim wrote "en-GB" and looked
        // up "en_GB".
        val dao = FakeUserDictionaryDao()

        PersonalDictionaryImportBatch.import(
            parsedEntries = listOf(
                PersonalDictionaryEntry(word = "colour", frequency = 200, shortcut = null, locale = "en-GB"),
            ),
            dao = dao,
            format = DictionaryImportFormat.JSON,
            runInTransaction = dao::runInTransaction,
        )

        dao.queryAll().single().locale shouldBe "en_GB"
        dao.queryExact("colour", FlorisLocale.fromTag("en-GB")).size shouldBe 1
    }

    test("re-importing the same BCP-47 entry updates it instead of duplicating") {
        val dao = FakeUserDictionaryDao()
        val entry = PersonalDictionaryEntry(word = "colour", frequency = 200, shortcut = null, locale = "en-GB")

        val first = PersonalDictionaryImportBatch.import(
            parsedEntries = listOf(entry),
            dao = dao,
            format = DictionaryImportFormat.JSON,
            runInTransaction = dao::runInTransaction,
        )
        val second = PersonalDictionaryImportBatch.import(
            parsedEntries = listOf(entry.copy(frequency = 240)),
            dao = dao,
            format = DictionaryImportFormat.JSON,
            runInTransaction = dao::runInTransaction,
        )

        first.insertedCount shouldBe 1
        second.insertedCount shouldBe 0
        second.updatedExistingCount shouldBe 1
        dao.queryAll().size shouldBe 1
        dao.queryAll().single().freq shouldBe 240
    }

    test("a locale tag that does not parse is still stored as given") {
        // Nothing can be canonicalised without a parse, and dropping the value
        // would lose what the file said.
        val dao = FakeUserDictionaryDao()

        PersonalDictionaryImportBatch.import(
            parsedEntries = listOf(
                PersonalDictionaryEntry(word = "word", frequency = 100, shortcut = null, locale = "  "),
            ),
            dao = dao,
            format = DictionaryImportFormat.JSON,
            runInTransaction = dao::runInTransaction,
        )

        dao.queryAll().single().locale shouldBe null
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
            runInTransaction = dao::runInTransaction,
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
            runInTransaction = dao::runInTransaction,
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
            runInTransaction = dao::runInTransaction,
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
            runInTransaction = dao::runInTransaction,
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
            runInTransaction = dao::runInTransaction,
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
            runInTransaction = dao::runInTransaction,
        )

        val deleted = PersonalDictionaryImportBatch.rollback(result, dao, dao::runInTransaction)

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
            runInTransaction = dao::runInTransaction,
        )
        // User manually deletes "bread" before clicking Undo.
        dao.delete(dao.queryAll().first { it.word == "bread" })

        val deleted = PersonalDictionaryImportBatch.rollback(result, dao, dao::runInTransaction)

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
            runInTransaction = dao::runInTransaction,
        )

        result.isRollbackable shouldBe false
        val deleted = PersonalDictionaryImportBatch.rollback(result, dao, dao::runInTransaction)
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
            runInTransaction = dao::runInTransaction,
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
            runInTransaction = dao::runInTransaction,
        )

        // The entry still gets inserted; FlorisLocale.fromTag failure
        // must not abort the whole batch.
        result.insertedCount shouldBe 1
        dao.queryAll().single().word shouldBe "weird"
    }

    test("a write that fails at any point leaves the dictionary exactly as it was") {
        // Four writes: update apples, insert bread, update kiwi, insert eggs.
        for (failingWrite in 0 until 4) {
            val (dao, entries) = seededMixedBatch()
            val before = dao.queryAll()
            val nextIdBefore = dao.nextId
            dao.mutationsBeforeFailure = failingWrite

            shouldThrow<InjectedWriteFailure> {
                PersonalDictionaryImportBatch.import(
                    parsedEntries = entries,
                    dao = dao,
                    format = DictionaryImportFormat.JSON,
                    runInTransaction = dao::runInTransaction,
                )
            }

            withClue("failure at write $failingWrite") {
                dao.mutations shouldBe failingWrite
                dao.transactions shouldBe 1
                dao.queryAll() shouldBe before
                dao.nextId shouldBe nextIdBefore
            }
        }
    }

    test("the same batch with no failure keeps its summary counts") {
        val (dao, entries) = seededMixedBatch()

        val result = PersonalDictionaryImportBatch.import(
            parsedEntries = entries,
            dao = dao,
            format = DictionaryImportFormat.JSON,
            runInTransaction = dao::runInTransaction,
        )

        dao.transactions shouldBe 1
        dao.mutations shouldBe 4
        result.insertedCount shouldBe 2
        result.updatedExistingCount shouldBe 2
        result.skippedCount shouldBe 1
        result.totalParsedCount shouldBe 5
        dao.queryAll().map { it.word to it.freq } shouldContainExactlyInAnyOrder listOf(
            "apples" to 220,
            "kiwi" to 90,
            "bread" to 180,
            "eggs" to 160,
        )
    }

    test("an Undo that fails partway removes nothing, and a retry removes the import") {
        val (dao, entries) = seededMixedBatch()
        val result = PersonalDictionaryImportBatch.import(
            parsedEntries = entries,
            dao = dao,
            format = DictionaryImportFormat.JSON,
            runInTransaction = dao::runInTransaction,
        )
        val afterImport = dao.queryAll()
        // Let the first delete through and fail the second.
        dao.mutationsBeforeFailure = dao.mutations + 1

        shouldThrow<InjectedWriteFailure> {
            PersonalDictionaryImportBatch.rollback(result, dao, dao::runInTransaction)
        }
        dao.queryAll() shouldBe afterImport

        dao.mutationsBeforeFailure = null
        PersonalDictionaryImportBatch.rollback(result, dao, dao::runInTransaction) shouldBe 2
        dao.queryAll().map { it.word } shouldContainExactlyInAnyOrder listOf("apples", "kiwi")
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
    var nextId = 1L
        private set

    /** Writes allowed before the next insert, update or delete throws; null never fails. */
    var mutationsBeforeFailure: Int? = null
    var mutations = 0
        private set
    var transactions = 0
        private set

    /**
     * Stands in for Room's runInTransaction: a throw puts back the rows and
     * the id counter as they were when the block started, then rethrows.
     */
    fun runInTransaction(block: () -> Unit) {
        transactions++
        val savedRows = rows.toList()
        val savedNextId = nextId
        try {
            block()
        } catch (error: Throwable) {
            rows.clear()
            rows += savedRows
            nextId = savedNextId
            throw error
        }
    }

    private fun mutate() {
        val limit = mutationsBeforeFailure
        if (limit != null && mutations >= limit) {
            throw InjectedWriteFailure("write $mutations failed")
        }
        mutations++
    }

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
        mutate()
        rows += entry.copy(id = nextId++)
    }

    override fun update(entry: UserDictionaryEntry) {
        mutate()
        val idx = rows.indexOfFirst { it.id == entry.id }
        if (idx >= 0) rows[idx] = entry
    }

    override fun delete(entry: UserDictionaryEntry) {
        mutate()
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
    override fun queryAllReadOnlyTransaction(): List<UserDictionaryEntry> = fail("queryAllReadOnlyTransaction")
    override fun queryAll(locale: FlorisLocale?): List<UserDictionaryEntry> = fail("queryAll(locale)")
    override fun queryExactFuzzyLocale(word: String, locale: FlorisLocale?): List<UserDictionaryEntry> =
        fail("queryExactFuzzyLocale")
    override fun queryLanguageTagList(): List<String> = fail("queryLanguageTagList")

    private fun fail(name: String): Nothing =
        throw AssertionError("FakeUserDictionaryDao.$name should not be called by PersonalDictionaryImportBatch")
}

private class InjectedWriteFailure(message: String) : RuntimeException(message)

/** Two updates and two inserts around a blank row, so every write index is both kinds. */
private fun seededMixedBatch(): Pair<FakeUserDictionaryDao, List<PersonalDictionaryEntry>> {
    val dao = FakeUserDictionaryDao()
    dao.seed("apples", 100, "en")
    dao.seed("kiwi", 50, "en")
    return dao to listOf(
        PersonalDictionaryEntry(word = "apples", frequency = 220, shortcut = "ap", locale = "en"),
        PersonalDictionaryEntry(word = "bread", frequency = 180, shortcut = null, locale = "en"),
        PersonalDictionaryEntry(word = " ", frequency = 10, shortcut = null, locale = "en"),
        PersonalDictionaryEntry(word = "kiwi", frequency = 90, shortcut = null, locale = "en"),
        PersonalDictionaryEntry(word = "eggs", frequency = 160, shortcut = null, locale = "en"),
    )
}
