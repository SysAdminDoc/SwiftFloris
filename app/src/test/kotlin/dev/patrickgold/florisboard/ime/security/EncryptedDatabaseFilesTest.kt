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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.io.File
import java.nio.file.Files

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
})
