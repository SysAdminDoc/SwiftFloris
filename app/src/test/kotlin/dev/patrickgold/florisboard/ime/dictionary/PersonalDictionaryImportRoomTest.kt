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

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Imports and Undo against a real Room database rather than a fake DAO. A trigger makes SQLite
 * itself refuse the Nth write, so what's checked is Room's own transaction rolling back the rows
 * written before it: inserts, and the overwritten frequencies and shortcuts.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PersonalDictionaryImportRoomTest {
    private val databases = mutableListOf<FlorisUserDictionaryDatabase>()

    @After
    fun tearDown() {
        databases.forEach { it.close() }
    }

    @Test
    fun importRefusedAtAnyWriteLeavesEveryRowAsItWas() {
        // apples and kiwi are overwritten, bread and eggs inserted, the blank word skipped.
        for (writesAllowed in 0 until 4) {
            val database = seededDatabase()
            val before = database.rows()
            database.refuseWritesAfter(writesAllowed, "INSERT", "UPDATE")

            val failure = runCatching {
                PersonalDictionaryImportBatch.import(
                    parsedEntries = mixedBatch(),
                    dao = database.userDictionaryDao(),
                    format = DictionaryImportFormat.JSON,
                    runInTransaction = database::inTransaction,
                )
            }.exceptionOrNull()

            withClue("refused at write $writesAllowed") {
                failure?.message.orEmpty() shouldContain "injected write failure"
                database.rows() shouldBe before
            }
        }
    }

    @Test
    fun importWithNoFailureKeepsItsCountsAndUndoRemovesOnlyTheNewWords() {
        val database = seededDatabase()

        val result = PersonalDictionaryImportBatch.import(
            parsedEntries = mixedBatch(),
            dao = database.userDictionaryDao(),
            format = DictionaryImportFormat.JSON,
            runInTransaction = database::inTransaction,
        )

        result.insertedIds.size shouldBe 2
        result.updatedExistingCount shouldBe 2
        result.skippedCount shouldBe 1
        result.totalParsedCount shouldBe 5
        database.rows().map { it.word } shouldBe listOf("apples", "kiwi", "bread", "eggs")

        PersonalDictionaryImportBatch.rollback(result, database.userDictionaryDao(), database::inTransaction) shouldBe 2
        database.rows().map { it.word to it.freq } shouldBe listOf("apples" to 220, "kiwi" to 90)
    }

    @Test
    fun undoRefusedPartwayRemovesNothingAndATryAfterRemovesTheImport() {
        val database = seededDatabase()
        val result = PersonalDictionaryImportBatch.import(
            parsedEntries = mixedBatch(),
            dao = database.userDictionaryDao(),
            format = DictionaryImportFormat.JSON,
            runInTransaction = database::inTransaction,
        )
        val afterImport = database.rows()
        database.refuseWritesAfter(1, "DELETE")

        val failure = runCatching {
            PersonalDictionaryImportBatch.rollback(result, database.userDictionaryDao(), database::inTransaction)
        }.exceptionOrNull()

        failure?.message.orEmpty() shouldContain "injected write failure"
        database.rows() shouldBe afterImport

        database.allowAllWrites("DELETE")
        PersonalDictionaryImportBatch.rollback(result, database.userDictionaryDao(), database::inTransaction) shouldBe 2
        database.rows().map { it.word } shouldBe listOf("apples", "kiwi")
    }

    @Test
    fun legacyCombinedListRefusedAtAnyWriteLeavesEveryRowAsItWas() {
        val backup = UserDictionaryCombinedListCodec.encode(
            entries = listOf(
                UserDictionaryEntry(id = 0, word = "apples", freq = 220, locale = "en", shortcut = "ap"),
                UserDictionaryEntry(id = 0, word = "bread", freq = 180, locale = "en", shortcut = null),
                UserDictionaryEntry(id = 0, word = "kiwi", freq = 90, locale = "en", shortcut = null),
                UserDictionaryEntry(id = 0, word = "eggs", freq = 160, locale = "en", shortcut = null),
            ),
            dictionaryName = "backup",
            generatedBy = "test",
            timestampMillis = 0L,
        )
        for (writesAllowed in 0 until 4) {
            val database = seededDatabase()
            val before = database.rows()
            database.refuseWritesAfter(writesAllowed, "INSERT", "UPDATE")

            val failure = runCatching { database.importCombinedListText(backup) }.exceptionOrNull()

            withClue("refused at write $writesAllowed") {
                failure?.message.orEmpty() shouldContain "injected write failure"
                database.rows() shouldBe before
            }
        }

        val database = seededDatabase()
        database.importCombinedListText(backup)
        database.rows().map { it.word to it.freq } shouldBe
            listOf("apples" to 220, "kiwi" to 90, "bread" to 180, "eggs" to 160)
    }

    private fun seededDatabase(): FlorisUserDictionaryDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, FlorisUserDictionaryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        databases += database
        val dao = database.userDictionaryDao()
        dao.insert(UserDictionaryEntry(id = 0, word = "apples", freq = 100, locale = "en", shortcut = null))
        dao.insert(UserDictionaryEntry(id = 0, word = "kiwi", freq = 50, locale = "en", shortcut = "kw"))
        return database
    }

    private fun mixedBatch() = listOf(
        PersonalDictionaryEntry(word = "apples", frequency = 220, shortcut = "ap", locale = "en"),
        PersonalDictionaryEntry(word = "bread", frequency = 180, shortcut = null, locale = "en"),
        PersonalDictionaryEntry(word = " ", frequency = 10, shortcut = null, locale = "en"),
        PersonalDictionaryEntry(word = "kiwi", frequency = 90, shortcut = null, locale = "en"),
        PersonalDictionaryEntry(word = "eggs", frequency = 160, shortcut = null, locale = "en"),
    )

    private fun FlorisUserDictionaryDatabase.rows(): List<UserDictionaryEntry> =
        userDictionaryDao().queryAll().sortedBy { it.id }

    /** Lets [writesAllowed] writes of the given kinds through, then makes SQLite refuse the next one. */
    private fun FlorisUserDictionaryDatabase.refuseWritesAfter(writesAllowed: Int, vararg events: String) {
        val db = openHelper.writableDatabase
        db.execSQL("CREATE TABLE IF NOT EXISTS fault_budget (remaining INTEGER NOT NULL)")
        db.execSQL("DELETE FROM fault_budget")
        db.execSQL("INSERT INTO fault_budget (remaining) VALUES ($writesAllowed)")
        for (event in events) {
            db.execSQL(
                """
                CREATE TRIGGER fault_on_$event BEFORE $event ON words
                BEGIN
                    SELECT RAISE(ABORT, 'injected write failure') WHERE (SELECT remaining FROM fault_budget) <= 0;
                    UPDATE fault_budget SET remaining = remaining - 1;
                END
                """.trimIndent(),
            )
        }
    }

    private fun FlorisUserDictionaryDatabase.allowAllWrites(vararg events: String) {
        val db = openHelper.writableDatabase
        for (event in events) {
            db.execSQL("DROP TRIGGER IF EXISTS fault_on_$event")
        }
    }
}
