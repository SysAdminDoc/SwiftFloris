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

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.patrickgold.florisboard.lib.FlorisLocale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PersonalDictionaryRoomSqlCipherRuntimeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseNames = mutableListOf<String>()

    @After
    fun cleanupDatabases() {
        databaseNames.forEach { databaseName ->
            context.deleteDatabase(databaseName)
        }
        databaseNames.clear()
    }

    @Test
    fun encryptedRoomDatabaseSupportsReadWriteAndReadOnlyDaoTransactions() {
        val databaseName = "floris_user_dictionary_sqlcipher_sentinel_${UUID.randomUUID()}"
        databaseNames += databaseName
        val factory = FlorisUserDictionaryEncryption.openHelperFactory(context)
            ?: error(
                "SQLCipher open-helper factory could not be created. " +
                    "Check sqlcipher-android native loading and Tink/Keystore passphrase setup.",
            )
        val database = Room.databaseBuilder(
            context,
            FlorisUserDictionaryDatabase::class.java,
            databaseName,
        ).openHelperFactory(factory).build()
        val executor = Executors.newSingleThreadExecutor()

        try {
            val expectedLocaleTag = FlorisLocale.fromTag("en-US").localeTag()
            val result = executor.submit<SentinelResult> {
                val locale = FlorisLocale.fromTag(expectedLocaleTag)
                val dao = database.userDictionaryDao()
                dao.insert(
                    UserDictionaryEntry(
                        id = 0,
                        word = "swiftfloris",
                        freq = 214,
                        locale = locale.localeTag(),
                        shortcut = "sf",
                    ),
                )

                SentinelResult(
                    exactWord = dao.queryExact("swiftfloris", locale).single().word,
                    shortcutWord = dao.queryShortcut("sf", locale).single().word,
                    readOnlyTransactionWord = dao.queryAllReadOnlyTransaction().single().word,
                    languageTags = dao.queryLanguageList().map { it?.localeTag() },
                )
            }.get(30, TimeUnit.SECONDS)

            assertEquals("swiftfloris", result.exactWord)
            assertEquals("swiftfloris", result.shortcutWord)
            assertEquals("swiftfloris", result.readOnlyTransactionWord)
            assertTrue(
                "Expected normalized locale tag $expectedLocaleTag in ${result.languageTags}.",
                result.languageTags.contains(expectedLocaleTag),
            )
        } finally {
            database.close()
            executor.shutdownNow()
        }

        val header = readDatabaseHeader(databaseName)
        assertFalse(
            "Encrypted personal dictionary database was created with a plaintext SQLite header.",
            FlorisUserDictionaryEncryption.looksLikePlaintextSqliteHeader(header),
        )
    }

    private fun readDatabaseHeader(databaseName: String): ByteArray {
        val databaseFile = context.getDatabasePath(databaseName)
        assertTrue(
            "Encrypted personal dictionary database file was not created at ${databaseFile.path}.",
            databaseFile.isFile,
        )
        return databaseFile.inputStream().use { input ->
            ByteArray(SQLITE_HEADER_BYTES).also { header ->
                assertEquals(
                    "Encrypted personal dictionary database header could not be read.",
                    SQLITE_HEADER_BYTES,
                    input.read(header),
                )
            }
        }
    }

    private data class SentinelResult(
        val exactWord: String,
        val shortcutWord: String,
        val readOnlyTransactionWord: String,
        val languageTags: List<String?>,
    )

    private companion object {
        private const val SQLITE_HEADER_BYTES = 16
    }
}
