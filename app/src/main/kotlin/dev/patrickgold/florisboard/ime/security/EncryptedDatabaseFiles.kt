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
import net.zetetic.database.sqlcipher.SQLiteNotADatabaseException
import java.io.File

/**
 * File-level helpers shared by the SQLCipher-backed stores.
 *
 * Encrypting an existing plaintext database is a move-aside / rebuild / verify sequence, and every
 * one of those steps has to be reversible: the staged plaintext files are the user's only copy
 * until the encrypted replacement is proven readable.
 */
internal object EncryptedDatabaseFiles {
    /** A plaintext SQLite file starts with this header; a SQLCipher file never does. */
    private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    /** One staged file and where it came from. */
    data class Backup(val original: File, val backup: File)

    fun looksLikePlaintextSqliteHeader(header: ByteArray): Boolean {
        if (header.size < SQLITE_HEADER.size) return false
        for (index in SQLITE_HEADER.indices) {
            if (header[index] != SQLITE_HEADER[index]) return false
        }
        return true
    }

    fun isPlaintextSqliteDatabase(file: File): Boolean {
        if (!file.isFile || file.length() < SQLITE_HEADER.size) return false
        return runCatching {
            file.inputStream().use { input ->
                val header = ByteArray(SQLITE_HEADER.size)
                input.read(header) == SQLITE_HEADER.size && looksLikePlaintextSqliteHeader(header)
            }
        }.getOrDefault(false)
    }

    /** The database file plus the WAL / SHM / journal siblings Room may have created. */
    fun siblingsOf(databaseFile: File): List<File> {
        return listOf(
            databaseFile,
            File("${databaseFile.path}-wal"),
            File("${databaseFile.path}-shm"),
            File("${databaseFile.path}-journal"),
        )
    }

    /**
     * Renames every existing sibling out of the way with a `.<suffix>-<timestamp>` name.
     *
     * @throws IllegalStateException if any rename fails, after restoring the ones that succeeded —
     *  a half-staged database is not a state any caller can recover from.
     */
    fun moveAside(databaseFile: File, suffix: String, timestampMs: Long): List<Backup> {
        val backups = mutableListOf<Backup>()
        for (file in siblingsOf(databaseFile)) {
            if (!file.exists()) continue
            val backup = File(file.parentFile, "${file.name}.$suffix-$timestampMs")
            if (file.renameTo(backup)) {
                backups += Backup(file, backup)
            } else {
                restore(backups)
                error("Could not move ${file.name} to ${backup.name}")
            }
        }
        return backups
    }

    /** Puts staged files back. Returns false when any single file could not be restored. */
    fun restore(backups: List<Backup>): Boolean {
        var restored = true
        for ((original, backup) in backups) {
            if (original.exists() && !original.delete()) {
                restored = false
            }
            if (backup.exists() && !backup.renameTo(original)) {
                restored = false
            }
        }
        return restored
    }

    /** Drops staged copies once the replacement is proven good. */
    fun deleteBackups(backups: List<Backup>): Boolean {
        var deleted = true
        for ((_, backup) in backups) {
            if (backup.exists() && !backup.delete()) {
                deleted = false
            }
        }
        return deleted
    }

    /**
     * Deletes the database for good: the file, its WAL / SHM / journal, every copy kept aside under
     * a `.<suffix>-<timestamp>` name by [moveAside] or [quarantine], and any marker named after it.
     * Returns whatever is still there afterwards; empty means nothing of the store is left.
     */
    fun deleteEveryCopy(databaseFile: File): List<File> {
        val names = siblingsOf(databaseFile).map { it.name }
        val copies = databaseFile.parentFile?.listFiles { file ->
            names.any { name -> file.name == name || file.name.startsWith("$name.") }
        }.orEmpty()
        return copies.filter { it.exists() && !it.delete() }
    }

    /**
     * Preserves an unusable database under a `.<reason>-<timestamp>` name instead of deleting it.
     * Moves every sibling or none: returns false when anything could not be preserved and the store
     * was put back in place, which callers must treat as "do not replace" — deleting data the user
     * might still recover is worse than staying broken.
     */
    fun quarantine(databaseFile: File, reason: String, timestampMs: Long): Boolean {
        val moved = mutableListOf<Backup>()
        for (file in siblingsOf(databaseFile)) {
            if (!file.exists()) continue
            val target = File(file.parentFile, "${file.name}.$reason-$timestampMs")
            if (file.renameTo(target)) {
                moved += Backup(file, target)
                continue
            }
            // Put back what already moved so a failure leaves the store whole where it was. Only
            // if the main file can't be put back is it reported as moved: its words are no longer
            // at the live path either way, and the caller has to say so.
            for ((original, backup) in moved.asReversed()) {
                backup.renameTo(original)
            }
            return !databaseFile.exists()
        }
        return true
    }

    /**
     * True only for evidence that the file itself can never be read with the key this app holds:
     * SQLite's corruption error, or SQLCipher's "file is not a database" (a damaged header or a
     * database encrypted under a key that no longer exists). Busy, locked, I/O and Keystore
     * failures can clear on their own, so they never authorize [quarantine].
     */
    fun isDefinitiveCorruption(error: Throwable): Boolean {
        val seen = HashSet<Throwable>()
        var current: Throwable? = error
        while (current != null && seen.add(current)) {
            if (current is SQLiteDatabaseCorruptException || current is SQLiteNotADatabaseException) {
                return true
            }
            current = current.cause
        }
        return false
    }
}
