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

import dev.patrickgold.florisboard.ime.dictionary.UserDictionaryDao
import dev.patrickgold.florisboard.ime.dictionary.UserDictionaryEntry
import dev.patrickgold.florisboard.lib.FlorisLocale
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class PersonalDictionarySyncDaoApplierTest : FunSpec({

    test("snapshot maps all-language dictionary rows to a sync-safe locale token") {
        val dao = FakeUserDictionaryDao()
        dao.seed("global", 300, null, shortcut = "")
        dao.seed("bonjour", 120, "fr", shortcut = "bjr")

        val snapshot = PersonalDictionarySyncDaoApplier.snapshot(dao)

        snapshot.shouldContainExactlyInAnyOrder(
            listOf(
                PersonalDictionarySync.SyncDictionaryWord(
                    word = "global",
                    locale = "all",
                    frequency = 255,
                    shortcut = null,
                ),
                PersonalDictionarySync.SyncDictionaryWord(
                    word = "bonjour",
                    locale = "fr",
                    frequency = 120,
                    shortcut = "bjr",
                ),
            ),
        )
    }

    test("apply imports inserts updates and tombstone deletes") {
        val dao = FakeUserDictionaryDao()
        dao.seed("old", 100, "en")
        dao.seed("remove-me", 100, null)

        val plan = PersonalDictionarySync.ImportPlan(
            newState = PersonalDictionaryCrdt(deviceId = "dev-a", clock = 1L),
            wordsToUpsert = listOf(
                PersonalDictionarySync.SyncDictionaryWord(
                    word = "old",
                    locale = "en",
                    frequency = 220,
                    shortcut = null,
                ),
                PersonalDictionarySync.SyncDictionaryWord(
                    word = "new",
                    locale = "all",
                    frequency = 180,
                    shortcut = "nn",
                ),
            ),
            wordsToDelete = listOf(
                PersonalDictionarySync.SyncDictionaryWord(
                    word = "remove-me",
                    locale = "all",
                    frequency = 100,
                    shortcut = null,
                ),
            ),
        )

        val result = PersonalDictionarySyncDaoApplier.apply(plan, dao)

        result.insertedCount shouldBe 1
        result.updatedCount shouldBe 1
        result.deletedCount shouldBe 1
        dao.queryAll().map { it.word to it.locale } shouldContainExactlyInAnyOrder listOf(
            "old" to "en",
            "new" to null,
        )
        dao.queryAll().first { it.word == "old" }.freq shouldBe 220
        dao.queryAll().first { it.word == "new" }.shortcut shouldBe "nn"
    }
})

private class FakeUserDictionaryDao : UserDictionaryDao {
    private val rows = mutableListOf<UserDictionaryEntry>()
    private var nextId = 1L

    fun seed(word: String, freq: Int, locale: String?, shortcut: String? = null) {
        rows += UserDictionaryEntry(
            id = nextId++,
            word = word,
            freq = freq,
            locale = locale,
            shortcut = shortcut,
        )
    }

    override fun queryAll(): List<UserDictionaryEntry> = rows.toList()

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

    override fun query(word: String): List<UserDictionaryEntry> = fail("query(word)")
    override fun query(word: String, locale: FlorisLocale?): List<UserDictionaryEntry> = fail("query(word, locale)")
    override fun queryShortcut(shortcut: String): List<UserDictionaryEntry> = fail("queryShortcut")
    override fun queryShortcut(shortcut: String, locale: FlorisLocale?): List<UserDictionaryEntry> = fail("queryShortcut")
    override fun queryAll(locale: FlorisLocale?): List<UserDictionaryEntry> = fail("queryAll(locale)")
    override fun queryExact(word: String): List<UserDictionaryEntry> = fail("queryExact(word)")
    override fun queryExactFuzzyLocale(word: String, locale: FlorisLocale?): List<UserDictionaryEntry> =
        fail("queryExactFuzzyLocale")
    override fun queryLanguageTagList(): List<String> = fail("queryLanguageTagList")
    override fun deleteAll() = fail("deleteAll")

    private fun fail(name: String): Nothing =
        throw AssertionError("FakeUserDictionaryDao.$name should not be called by PersonalDictionarySyncDaoApplier")
}
