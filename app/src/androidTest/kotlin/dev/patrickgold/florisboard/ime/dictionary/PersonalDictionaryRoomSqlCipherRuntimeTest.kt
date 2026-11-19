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

import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.patrickgold.florisboard.ime.security.EncryptedDatabaseFiles
import dev.patrickgold.florisboard.lib.FlorisLocale
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class PersonalDictionaryRoomSqlCipherRuntimeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseNames = mutableListOf<String>()

    @After
    fun cleanupDatabases() {
        databaseNames.forEach { databaseName ->
            context.deleteDatabase(databaseName)
            // deleteDatabase only knows SQLite's own names, not the kept-aside copies.
            context.getDatabasePath(databaseName).parentFile
                ?.listFiles { file -> file.name.startsWith(databaseName) }
                ?.forEach { it.delete() }
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

    @Test
    fun encryptedStoresRunSqliteWithThe2026WalAndAuthorizerFixes() {
        // CVE-2026-119375 (WAL page-cache read) and CVE-2026-119377 (authorizer bypass) are
        // fixed in SQLite 3.54.0, and SQLCipher bundles its own SQLite.
        val databaseName = "floris_user_dictionary_sqlcipher_version_${UUID.randomUUID()}"
        databaseNames += databaseName
        val database = buildStore(databaseName)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val version = executor.submit<String> {
                database.openHelper.writableDatabase.query("select sqlite_version()").use { cursor ->
                    assertTrue("sqlite_version() returned no row.", cursor.moveToFirst())
                    cursor.getString(0)
                }
            }.get(30, TimeUnit.SECONDS)
            Log.i(LOG_TAG, "SQLCipher runs SQLite $version")
            val (major, minor) = version.split('.').map(String::toInt)
            assertTrue(
                "SQLCipher runs SQLite $version, older than 3.54.0.",
                major > 3 || (major == 3 && minor >= 54),
            )
        } finally {
            database.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun storeWithADamagedHeaderIsKeptAsideAndAnEmptyOneOpens() {
        // SQLCipher keeps the key salt in the first 16 bytes, so the key no longer derives.
        assertDamagedStoreIsKeptAsideAndReplaced("header") { file ->
            file.seek(0)
            file.write(ByteArray(64) { DAMAGE_BYTE })
        }
    }

    @Test
    fun storeWithDamagedPagesIsKeptAsideAndAnEmptyOneOpens() {
        // Page 1 still decrypts, so the open itself works; the tables behind it don't.
        assertDamagedStoreIsKeptAsideAndReplaced("pages") { file ->
            val pages = file.length() / SQLCIPHER_PAGE_BYTES
            assertTrue("Expected a store of several pages, got ${file.length()} bytes.", pages > 1)
            for (page in 1 until pages) {
                file.seek(page * SQLCIPHER_PAGE_BYTES + 512)
                file.write(ByteArray(256) { DAMAGE_BYTE })
            }
        }
    }

    /**
     * Runs the keyboard's real open path ([UserDictionaryStoreRecovery.open] over SQLCipher) on a
     * store damaged on disk. The damaged bytes must still be there when the quarantine runs:
     * SQLCipher's own corruption handler deletes database files when it isn't handling a codec.
     */
    private fun assertDamagedStoreIsKeptAsideAndReplaced(label: String, damage: (RandomAccessFile) -> Unit) {
        val databaseName = "floris_user_dictionary_sqlcipher_damaged_${label}_${UUID.randomUUID()}"
        databaseNames += databaseName
        val databaseFile = context.getDatabasePath(databaseName)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val seeded = buildStore(databaseName)
            executor.submit {
                seeded.userDictionaryDao().insert(
                    UserDictionaryEntry(id = 0, word = "swiftfloris", freq = 214, locale = "en", shortcut = null),
                )
            }.get(30, TimeUnit.SECONDS)
            seeded.close()
            assertFalse(
                "The write-ahead log outlived the close, so damaging the main file proves nothing.",
                File(databaseFile.path + "-wal").let { it.isFile && it.length() > 0 },
            )
            RandomAccessFile(databaseFile, "rw").use(damage)
            val damagedBytes = databaseFile.readBytes()

            val readFailure = executor.submit<Throwable?> {
                val database = buildStore(databaseName)
                try {
                    runCatching { database.userDictionaryDao().queryLanguageList() }.exceptionOrNull()
                } finally {
                    database.close()
                }
            }.get(30, TimeUnit.SECONDS)
            assertNotNull("The damaged store ($label) read without an error.", readFailure)
            assertTrue(
                "Expected corruption evidence from the damaged store ($label), got $readFailure",
                EncryptedDatabaseFiles.isDefinitiveCorruption(readFailure!!),
            )
            assertArrayEquals(
                "SQLCipher changed or deleted the damaged store ($label) before the app could keep it.",
                damagedBytes,
                databaseFile.readBytes(),
            )

            val resets = AtomicInteger()
            val outcome = UserDictionaryStoreRecovery.runToCompletion(executor) {
                UserDictionaryStoreRecovery.open(
                    build = { buildStore(databaseName) },
                    verify = { database -> database.userDictionaryDao().queryLanguageList() },
                    close = { database -> database.close() },
                    quarantine = { EncryptedDatabaseFiles.quarantine(databaseFile, "unreadable", 1L) },
                    recordReset = { resets.incrementAndGet() },
                )
            }
            val opened = outcome as? UserDictionaryOpenOutcome.Opened
                ?: throw AssertionError("The damaged store ($label) was not replaced: $outcome")
            try {
                assertTrue(opened.replacedUnreadableStore)
                assertEquals(1, resets.get())
                assertArrayEquals(
                    "The kept copy of the damaged store ($label) isn't the damaged file.",
                    damagedBytes,
                    File(databaseFile.parentFile, "${databaseFile.name}.unreadable-1").readBytes(),
                )
                val words = executor.submit<List<UserDictionaryEntry>> {
                    opened.database.userDictionaryDao().queryAll()
                }.get(30, TimeUnit.SECONDS)
                assertEquals(emptyList<UserDictionaryEntry>(), words)
            } finally {
                opened.database.close()
            }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun buildStore(databaseName: String): FlorisUserDictionaryDatabase {
        val factory = FlorisUserDictionaryEncryption.openHelperFactory(context)
            ?: error("SQLCipher open-helper factory could not be created.")
        return Room.databaseBuilder(context, FlorisUserDictionaryDatabase::class.java, databaseName)
            .openHelperFactory(factory)
            .build()
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
        private const val LOG_TAG = "SqlCipherRuntimeTest"
        private const val SQLITE_HEADER_BYTES = 16
        private const val SQLCIPHER_PAGE_BYTES = 4096L
        private const val DAMAGE_BYTE: Byte = 0x5A
    }
}
