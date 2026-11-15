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

package dev.patrickgold.florisboard.ime.security

import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteDiskIOException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import net.zetetic.database.sqlcipher.SQLiteNotADatabaseException
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.KeyStoreException

/**
 * Encrypting an existing plaintext database is a move-aside / rebuild / verify sequence over the
 * user's only copy of that data. These cases pin the reversibility of every step: a staged store
 * comes back intact on failure, and an unusable store is preserved rather than deleted.
 */
class EncryptedDatabaseFilesTest : FunSpec({

    val sqliteHeader = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    fun tempDir(): File = Files.createTempDirectory("encrypted-db").toFile()

    fun plaintextDatabase(dir: File, name: String = "history"): File {
        return File(dir, name).apply { writeBytes(sqliteHeader + ByteArray(64) { 0x41 }) }
    }

    test("a plaintext SQLite file is recognised by its header") {
        val dir = tempDir()
        EncryptedDatabaseFiles.isPlaintextSqliteDatabase(plaintextDatabase(dir)) shouldBe true
    }

    test("an encrypted or truncated file is not mistaken for plaintext") {
        val dir = tempDir()
        val encrypted = File(dir, "encrypted").apply { writeBytes(ByteArray(64) { 0x7F }) }
        val truncated = File(dir, "truncated").apply { writeBytes("SQLite".toByteArray()) }
        val missing = File(dir, "missing")

        EncryptedDatabaseFiles.isPlaintextSqliteDatabase(encrypted) shouldBe false
        EncryptedDatabaseFiles.isPlaintextSqliteDatabase(truncated) shouldBe false
        EncryptedDatabaseFiles.isPlaintextSqliteDatabase(missing) shouldBe false
        EncryptedDatabaseFiles.looksLikePlaintextSqliteHeader(ByteArray(4)) shouldBe false
    }

    test("siblings cover the WAL, SHM and journal companions") {
        val dir = tempDir()
        val database = File(dir, "history")

        EncryptedDatabaseFiles.siblingsOf(database).map { it.name } shouldContainExactlyInAnyOrder listOf(
            "history",
            "history-wal",
            "history-shm",
            "history-journal",
        )
    }

    test("staging moves every existing companion and leaves the originals gone") {
        val dir = tempDir()
        val database = plaintextDatabase(dir)
        File(dir, "history-wal").writeText("wal")
        // No -shm / -journal on disk: staging must not invent them.

        val staged = EncryptedDatabaseFiles.moveAside(database, "plaintext", 1_000L)

        staged.map { it.backup.name } shouldContainExactlyInAnyOrder listOf(
            "history.plaintext-1000",
            "history-wal.plaintext-1000",
        )
        database.exists() shouldBe false
        File(dir, "history.plaintext-1000").exists() shouldBe true
    }

    test("restore puts a staged store back byte for byte") {
        val dir = tempDir()
        val database = plaintextDatabase(dir)
        val originalBytes = database.readBytes().toList()

        val staged = EncryptedDatabaseFiles.moveAside(database, "plaintext", 2_000L)
        // A failed migration would have written a replacement in the meantime.
        database.writeText("half-built encrypted replacement")

        EncryptedDatabaseFiles.restore(staged) shouldBe true

        database.readBytes().toList() shouldBe originalBytes
        File(dir, "history.plaintext-2000").exists() shouldBe false
    }

    test("dropping staged copies only happens on demand") {
        val dir = tempDir()
        val database = plaintextDatabase(dir)
        val staged = EncryptedDatabaseFiles.moveAside(database, "plaintext", 3_000L)

        File(dir, "history.plaintext-3000").exists() shouldBe true
        EncryptedDatabaseFiles.deleteBackups(staged) shouldBe true
        File(dir, "history.plaintext-3000").exists() shouldBe false
    }

    test("quarantine preserves an unusable store instead of deleting it") {
        val dir = tempDir()
        val database = File(dir, "history").apply { writeText("unreadable ciphertext") }
        File(dir, "history-wal").writeText("wal")

        EncryptedDatabaseFiles.quarantine(database, "unreadable", 4_000L) shouldBe true

        database.exists() shouldBe false
        File(dir, "history.unreadable-4000").readText() shouldBe "unreadable ciphertext"
        File(dir, "history-wal.unreadable-4000").exists() shouldBe true
    }

    test("a quarantine that can't move every file puts the store back and reports it kept") {
        val dir = tempDir()
        val database = File(dir, "history").apply { writeText("ciphertext") }
        val wal = File(dir, "history-wal").apply { writeText("wal") }
        // A directory at the destination name makes only the -wal rename fail.
        File(dir, "history-wal.unreadable-6000").mkdirs()
        File(dir, "history-wal.unreadable-6000/occupied").writeText("x")

        EncryptedDatabaseFiles.quarantine(database, "unreadable", 6_000L) shouldBe false

        database.readText() shouldBe "ciphertext"
        wal.readText() shouldBe "wal"
        File(dir, "history.unreadable-6000").exists() shouldBe false
    }

    test("a partial staging failure restores what it already moved") {
        val dir = tempDir()
        val database = plaintextDatabase(dir)
        val wal = File(dir, "history-wal").apply { writeText("wal") }
        // A directory at the destination name makes that single rename fail.
        File(dir, "history-wal.plaintext-5000").mkdirs()
        File(dir, "history-wal.plaintext-5000/occupied").writeText("x")

        shouldThrow<IllegalStateException> {
            EncryptedDatabaseFiles.moveAside(database, "plaintext", 5_000L)
        }

        database.exists() shouldBe true
        wal.readText() shouldBe "wal"
    }

    test("deleting every copy removes the store, its sidecars, kept copies and markers only") {
        val dir = tempDir()
        val store = plaintextDatabase(dir, "words")
        val ours = listOf(
            "words-wal", "words-shm", "words-journal",
            "words.unreadable-1", "words-wal.unreadable-1", "words.plaintext-2", "words.reset-notice",
        ).map { File(dir, it).apply { writeText(it) } }
        val unrelated = listOf("words_backup", "wordsmith", "history", "history-wal.unreadable-1")
            .map { File(dir, it).apply { writeText(it) } }

        EncryptedDatabaseFiles.deleteEveryCopy(store) shouldBe emptyList()

        (ours + store).filter { it.exists() } shouldBe emptyList()
        unrelated.all { it.readText() == it.name } shouldBe true
    }

    test("a copy that can't be deleted is reported and the rest are still deleted") {
        val dir = tempDir()
        val store = plaintextDatabase(dir, "words")
        val wal = File(dir, "words-wal").apply { writeText("wal") }
        // A directory with something in it is the one name File.delete() can't remove.
        val stuck = File(dir, "words.unreadable-1").apply { mkdirs() }
        File(stuck, "inside").writeText("x")

        EncryptedDatabaseFiles.deleteEveryCopy(store) shouldBe listOf(stuck)

        store.exists() shouldBe false
        wal.exists() shouldBe false
    }

    test("deleting every copy of a store that was never created finds nothing left") {
        val dir = tempDir()
        EncryptedDatabaseFiles.deleteEveryCopy(File(dir, "words")) shouldBe emptyList()
        EncryptedDatabaseFiles.deleteEveryCopy(File(File(dir, "missing-dir"), "words")) shouldBe emptyList()
    }

    test("only proven corruption authorizes quarantine") {
        EncryptedDatabaseFiles.isDefinitiveCorruption(
            SQLiteDatabaseCorruptException("malformed database"),
        ) shouldBe true
        // SQLCipher raises this for SQLITE_NOTADB: a damaged header or a key that no longer exists.
        EncryptedDatabaseFiles.isDefinitiveCorruption(
            SQLiteNotADatabaseException("file is not a database"),
        ) shouldBe true
        EncryptedDatabaseFiles.isDefinitiveCorruption(
            IllegalStateException("query failed", SQLiteDatabaseCorruptException("malformed database")),
        ) shouldBe true
        EncryptedDatabaseFiles.isDefinitiveCorruption(
            IOException("temporary read failure"),
        ) shouldBe false
        EncryptedDatabaseFiles.isDefinitiveCorruption(
            IllegalStateException("database read failed", IOException("disk unavailable")),
        ) shouldBe false
        EncryptedDatabaseFiles.isDefinitiveCorruption(
            SQLiteDatabaseLockedException("database is locked (code 5 SQLITE_BUSY)"),
        ) shouldBe false
        EncryptedDatabaseFiles.isDefinitiveCorruption(
            SQLiteDiskIOException("disk I/O error (code 10)"),
        ) shouldBe false
        EncryptedDatabaseFiles.isDefinitiveCorruption(
            KeyStoreException("Keystore operation failed"),
        ) shouldBe false
    }

    test("a cause chain that loops back on itself ends without corruption evidence") {
        val outer = IllegalStateException("outer")
        val inner = IllegalStateException("inner", outer)
        outer.initCause(inner)
        EncryptedDatabaseFiles.isDefinitiveCorruption(outer) shouldBe false
    }
})
