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

package dev.patrickgold.florisboard.ime.clipboard.provider

import android.content.Context
import android.database.sqlite.SQLiteDatabaseCorruptException
import dev.patrickgold.florisboard.ime.security.EncryptedDatabaseFiles
import dev.patrickgold.florisboard.lib.devtools.flogError
import dev.patrickgold.florisboard.lib.devtools.flogInfo
import dev.patrickgold.florisboard.lib.devtools.flogWarning
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How the clipboard history database was opened, and what the user needs to know about it. */
enum class ClipboardHistoryStorageState {
    /** Encrypted at rest, opened normally. */
    Encrypted,

    /** Encrypted at rest; an existing plaintext history was migrated into it during this open. */
    MigratedToEncrypted,

    /**
     * The encrypted store could not be read — typically an invalidated Keystore key — so a fresh
     * empty history was created. The unreadable copy was preserved on disk, never deleted.
     */
    ResetAfterUnreadableStore,

    /** The read failed without corruption evidence; keep the database and key for a retry. */
    RetryableReadFailure,

    /** SQLCipher is unavailable on this device; history stays in the plaintext Room database. */
    Unencrypted,
}

/**
 * Opens the clipboard history database, encrypting it at rest and migrating any pre-existing
 * plaintext history exactly once.
 *
 * The migration is deliberately conservative. The plaintext files are staged aside rather than
 * deleted, every row is re-inserted into the encrypted replacement inside a single transaction,
 * the row count is verified before the staged copies are dropped, and any failure rolls the
 * plaintext store back into place. A store proven corrupt is preserved under a
 * `.unreadable-<timestamp>` name; an unclassified read failure remains in place for retry.
 * Both outcomes are reported through [state], and nothing silently discards history.
 */
object ClipboardHistoryStore {
    private val _state = MutableStateFlow(ClipboardHistoryStorageState.Encrypted)

    /** Observable outcome of the last open, so the UI can explain a reset instead of hiding it. */
    val state: StateFlow<ClipboardHistoryStorageState> = _state.asStateFlow()

    /** Acknowledges a reported reset so the notice stops being shown. */
    fun acknowledgeState() {
        if (_state.value == ClipboardHistoryStorageState.ResetAfterUnreadableStore) {
            _state.value = ClipboardHistoryStorageState.Encrypted
        }
    }

    fun open(context: Context): ClipboardHistoryDatabase {
        val appContext = context.applicationContext ?: context
        val factory = try {
            ClipboardHistoryEncryption.openHelperFactory(appContext)
        } catch (error: Throwable) {
            _state.value = ClipboardHistoryStorageState.RetryableReadFailure
            throw ClipboardHistoryRetryableReadException(error)
        }
        if (factory == null) {
            val databaseFile = appContext.getDatabasePath(ClipboardHistoryDatabase.DB_FILE_NAME)
            if (databaseFile.isFile && !EncryptedDatabaseFiles.isPlaintextSqliteDatabase(databaseFile)) {
                _state.value = ClipboardHistoryStorageState.RetryableReadFailure
                throw ClipboardHistoryRetryableReadException(
                    IllegalStateException("Encrypted clipboard history cannot be opened without SQLCipher"),
                )
            }
            // SQLCipher could not be loaded. Falling back to the plaintext database keeps the
            // clipboard working; reporting it keeps the privacy claim honest.
            flogWarning { "Clipboard history is not encrypted: SQLCipher is unavailable" }
            _state.value = ClipboardHistoryStorageState.Unencrypted
            return ClipboardHistoryDatabase.new(appContext)
        }

        val migrated = migratePlaintextHistoryIfNecessary(appContext)
        val database = openVerified(appContext)
        _state.value = when {
            database.wasReset -> ClipboardHistoryStorageState.ResetAfterUnreadableStore
            migrated -> ClipboardHistoryStorageState.MigratedToEncrypted
            else -> ClipboardHistoryStorageState.Encrypted
        }
        return database.database
    }

    private class OpenResult(val database: ClipboardHistoryDatabase, val wasReset: Boolean)

    private fun openVerified(context: Context): OpenResult {
        val database = ClipboardHistoryDatabase.newEncrypted(context)
        val readFailure = try {
            database.clipboardItemDao().getAll()
            null
        } catch (error: Throwable) {
            error
        }
        if (readFailure == null) {
            return OpenResult(database, wasReset = false)
        }
        database.close()
        if (!isDefinitiveCorruption(readFailure)) {
            _state.value = ClipboardHistoryStorageState.RetryableReadFailure
            flogWarning {
                "Encrypted clipboard history read failed without corruption evidence; preserving the store"
            }
            throw ClipboardHistoryRetryableReadException(readFailure)
        }
        flogWarning { "Encrypted clipboard history is corrupt; preserving it before creating a replacement" }
        val databaseFile = context.getDatabasePath(ClipboardHistoryDatabase.DB_FILE_NAME)
        if (!EncryptedDatabaseFiles.quarantine(databaseFile, "unreadable", System.currentTimeMillis())) {
            // Preserving failed, so the existing ciphertext is still in place. Do not create a
            // replacement or retire the key while the user's only copy may still be recoverable.
            _state.value = ClipboardHistoryStorageState.RetryableReadFailure
            flogError { "Could not preserve corrupt clipboard history; leaving it untouched" }
            throw ClipboardHistoryRetryableReadException(
                IllegalStateException("Could not preserve corrupt clipboard history"),
            )
        }
        // The old passphrase can no longer open anything, so retire it with the data it protected.
        if (!ClipboardHistoryEncryption.clearStoredPassphrase(context)) {
            _state.value = ClipboardHistoryStorageState.RetryableReadFailure
            throw ClipboardHistoryRetryableReadException(
                IllegalStateException("Could not retire the corrupt clipboard history key"),
            )
        }
        return try {
            OpenResult(ClipboardHistoryDatabase.newEncrypted(context), wasReset = true)
        } catch (error: Throwable) {
            _state.value = ClipboardHistoryStorageState.RetryableReadFailure
            throw ClipboardHistoryRetryableReadException(error)
        }
    }

    /** Only this concrete platform exception is evidence strong enough to quarantine data. */
    internal fun isDefinitiveCorruption(error: Throwable): Boolean {
        val seen = HashSet<Throwable>()
        var current: Throwable? = error
        while (current != null && seen.add(current)) {
            if (current is SQLiteDatabaseCorruptException) return true
            current = current.cause
        }
        return false
    }

    /**
     * Moves an existing plaintext history into the encrypted store. Returns true only when rows
     * were actually migrated.
     */
    private fun migratePlaintextHistoryIfNecessary(context: Context): Boolean {
        val databaseFile = context.getDatabasePath(ClipboardHistoryDatabase.DB_FILE_NAME)
        if (!EncryptedDatabaseFiles.isPlaintextSqliteDatabase(databaseFile)) {
            return false
        }
        val items = runCatching {
            val plaintext = ClipboardHistoryDatabase.new(context)
            try {
                plaintext.clipboardItemDao().getAll()
            } finally {
                plaintext.close()
            }
        }.getOrElse { error ->
            flogWarning { "Unable to read plaintext clipboard history for encryption: ${error::class.java.simpleName}" }
            return false
        }

        val staged = runCatching {
            EncryptedDatabaseFiles.moveAside(databaseFile, "plaintext", System.currentTimeMillis())
        }.getOrElse { error ->
            flogWarning { "Unable to stage plaintext clipboard history: ${error::class.java.simpleName}" }
            return false
        }

        val encrypted = ClipboardHistoryDatabase.newEncrypted(context)
        return runCatching {
            val dao = encrypted.clipboardItemDao()
            dao.replaceAllForRestore(items)
            val migratedCount = dao.getAll().size
            check(migratedCount == items.size) {
                "clipboard history row count mismatch: expected ${items.size}, found $migratedCount"
            }
            encrypted.close()
            EncryptedDatabaseFiles.deleteBackups(staged)
            flogInfo { "Migrated ${items.size} clipboard history rows to encrypted storage" }
            items.isNotEmpty()
        }.getOrElse { error ->
            flogError {
                "Encrypted clipboard history migration failed; restoring the plaintext store: " +
                    error::class.java.simpleName
            }
            encrypted.close()
            // Preserve the half-built encrypted store before the plaintext files come back, so a
            // failed migration never overwrites evidence of what went wrong.
            EncryptedDatabaseFiles.quarantine(databaseFile, "failed-migration", System.currentTimeMillis())
            EncryptedDatabaseFiles.restore(staged)
            false
        }
    }
}

internal class ClipboardHistoryRetryableReadException(cause: Throwable) :
    IllegalStateException("Clipboard history could not be read; retry without replacing it", cause)
