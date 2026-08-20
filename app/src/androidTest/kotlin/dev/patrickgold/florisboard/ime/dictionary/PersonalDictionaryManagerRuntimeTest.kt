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

import androidx.test.platform.app.InstrumentationRegistry
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.lib.FlorisLocale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking

class PersonalDictionaryManagerRuntimeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs by FlorisPreferenceStore
    private lateinit var manager: DictionaryManager

    @Before
    fun setUp() = runBlocking {
        manager = DictionaryManager.init(context)
        manager.unloadUserDictionariesIfNecessary()
        context.deleteDatabase(FlorisUserDictionaryDatabase.DB_FILE_NAME)
        prefs.dictionary.enableSystemUserDictionary.set(false).getOrThrow()
        prefs.dictionary.enableFlorisUserDictionary.set(true).getOrThrow()
    }

    @After
    fun tearDown() = runBlocking {
        manager.unloadUserDictionariesIfNecessary()
        context.deleteDatabase(FlorisUserDictionaryDatabase.DB_FILE_NAME)
        prefs.dictionary.enableSystemUserDictionary.set(true).getOrThrow()
        prefs.dictionary.enableFlorisUserDictionary.set(true).getOrThrow()
    }

    @Test
    fun learningWritesOnlyTheAppPrivateRoomDictionaryAndPersistsLocaleTag() {
        val locale = FlorisLocale.fromTag("en-US")
        val dao = manager.florisUserDictionaryDao() ?: error("personal dictionary DAO unavailable")

        manager.learnWord("SwiftFloris", locale)
        val learned = awaitEntry(dao, "swiftfloris", locale)

        assertEquals("swiftfloris", learned.word)
        assertEquals(locale.localeTag(), learned.locale)
        assertEquals(245, learned.freq)
        assertEquals(null, manager.systemUserDictionaryDao())
    }

    @Test
    fun disabledPersonalDictionaryDoesNotCreateOrWriteAStore() {
        runBlocking { prefs.dictionary.enableFlorisUserDictionary.set(false).getOrThrow() }
        manager.syncUserDictionaryStoresWithPreferences()

        manager.learnWord("SwiftFloris", FlorisLocale.fromTag("en-US"))
        TimeUnit.MILLISECONDS.sleep(250)

        assertFalse(context.getDatabasePath(FlorisUserDictionaryDatabase.DB_FILE_NAME).exists())
        assertTrue(manager.florisUserDictionaryDao() == null)
    }

    private fun awaitEntry(
        dao: UserDictionaryDao,
        word: String,
        locale: FlorisLocale,
    ): UserDictionaryEntry {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            dao.queryAll().firstOrNull { it.word == word && it.locale == locale.localeTag() }?.let { return it }
            TimeUnit.MILLISECONDS.sleep(25)
        }
        throw AssertionError("Learned dictionary entry $word/${locale.localeTag()} was not persisted")
    }
}
