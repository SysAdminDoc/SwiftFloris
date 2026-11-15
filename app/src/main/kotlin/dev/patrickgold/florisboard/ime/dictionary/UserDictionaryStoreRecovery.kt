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

import dev.patrickgold.florisboard.ime.security.EncryptedDatabaseFiles
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.FutureTask

/** What the dictionary screen should tell the user about the encrypted personal dictionary. */
enum class UserDictionaryStorageState {
    /** Opened normally, or not needed yet. */
    Available,

    /** The last open failed without corruption evidence. The files and key were kept and it is retried. */
    RetryableReadFailure,

    /** The store was proven unreadable, kept aside under a `.unreadable-<timestamp>` name, and an empty one started. */
    ResetAfterUnreadableStore,
}

internal sealed interface UserDictionaryOpenOutcome<out T> {
    data class Opened<T>(val database: T, val replacedUnreadableStore: Boolean) : UserDictionaryOpenOutcome<T>

    data class Unavailable(val error: Throwable?) : UserDictionaryOpenOutcome<Nothing>
}

internal object UserDictionaryStoreRecovery {
    /**
     * Opens and verifies the store. Only [EncryptedDatabaseFiles.isDefinitiveCorruption] lets the
     * existing files be moved aside; every other failure (busy, locked, I/O, Keystore, a missing
     * SQLCipher factory) closes what was opened and leaves the files where they are for a retry.
     * [recordReset] runs as soon as the files are aside, before a replacement is tried, so the
     * notice survives a replacement that fails to open and a process that dies in between.
     */
    fun <T : Any> open(
        build: () -> T?,
        verify: (T) -> Unit,
        close: (T) -> Unit,
        quarantine: () -> Boolean,
        recordReset: () -> Unit,
    ): UserDictionaryOpenOutcome<T> {
        val database = runCatching { build() }.getOrElse { return UserDictionaryOpenOutcome.Unavailable(it) }
            ?: return UserDictionaryOpenOutcome.Unavailable(null)
        val readFailure = verifyOrFailure(database, verify)
            ?: return UserDictionaryOpenOutcome.Opened(database, replacedUnreadableStore = false)
        runCatching { close(database) }
        if (!EncryptedDatabaseFiles.isDefinitiveCorruption(readFailure)) {
            return UserDictionaryOpenOutcome.Unavailable(readFailure)
        }
        if (!quarantine()) {
            return UserDictionaryOpenOutcome.Unavailable(readFailure)
        }
        recordReset()
        val replacement = runCatching { build() }.getOrElse { return UserDictionaryOpenOutcome.Unavailable(it) }
            ?: return UserDictionaryOpenOutcome.Unavailable(readFailure)
        val replacementFailure = verifyOrFailure(replacement, verify)
            ?: return UserDictionaryOpenOutcome.Opened(replacement, replacedUnreadableStore = true)
        runCatching { close(replacement) }
        return UserDictionaryOpenOutcome.Unavailable(replacementFailure)
    }

    /**
     * Runs [block] on [executor] and waits for it however the calling thread is interrupted, then
     * restores the interrupt. Keyboard lookups run under a short timeout that interrupts their
     * thread; an open abandoned that way would keep running unseen, so the database it opened would
     * leak, a reset it made would never reach the loader, and the next keystroke would start a
     * second open beside it.
     */
    fun <T> runToCompletion(executor: Executor, block: () -> T): T {
        val task = FutureTask(block)
        executor.execute(task)
        var interrupted = false
        try {
            while (true) {
                try {
                    return task.get()
                } catch (interruption: InterruptedException) {
                    interrupted = true
                }
            }
        } catch (failure: ExecutionException) {
            throw failure.cause ?: failure
        } finally {
            if (interrupted) Thread.currentThread().interrupt()
        }
    }

    private fun <T> verifyOrFailure(database: T, verify: (T) -> Unit): Throwable? {
        return try {
            verify(database)
            null
        } catch (error: Throwable) {
            error
        }
    }
}

/**
 * Remembers across restarts that the store was reset, until the user has seen it. The words were
 * moved aside the moment it was recorded, so a notice kept only in memory would be lost with the
 * process, and a later open of the empty replacement would look like nothing happened.
 */
internal interface UserDictionaryResetNotice {
    fun isPending(): Boolean

    fun record()

    fun clear()
}

/**
 * Holds the personal dictionary's open state between attempts: when the next attempt may run, and
 * what the dictionary screen shows. A reset notice outranks a retry notice and stays until the user
 * acknowledges it, whatever later attempts do.
 */
internal class UserDictionaryStoreLoader<T : Any>(
    now: () -> Long,
    private val resetNotice: UserDictionaryResetNotice,
    private val onUnavailable: (failures: Int, retryInMs: Long, error: Throwable?) -> Unit = { _, _, _ -> },
) {
    private val backoff = UserDictionaryRetryBackoff(now)
    private val stateLock = Any()
    private var failing = false
    private var resetPending: Boolean? = null
    private val _state = MutableStateFlow(UserDictionaryStorageState.Available)
    val state: StateFlow<UserDictionaryStorageState> = _state.asStateFlow()

    /**
     * Runs [open] unless the backoff after a recent failure is still running. Returns the opened
     * store or null. An exception out of [open] counts as a failed attempt like any other.
     */
    fun load(open: () -> UserDictionaryOpenOutcome<T>): T? {
        if (!backoff.canAttempt()) return null
        val outcome = try {
            open()
        } catch (error: Exception) {
            UserDictionaryOpenOutcome.Unavailable(error)
        }
        return when (outcome) {
            is UserDictionaryOpenOutcome.Opened -> {
                backoff.reset()
                synchronized(stateLock) {
                    failing = false
                    publishState()
                }
                outcome.database
            }
            is UserDictionaryOpenOutcome.Unavailable -> {
                backoff.recordFailure()
                synchronized(stateLock) {
                    failing = true
                    publishState()
                }
                onUnavailable(backoff.consecutiveFailures, backoff.delayAfter(backoff.consecutiveFailures), outcome.error)
                null
            }
        }
    }

    /** Called the moment the unreadable files are aside; see [UserDictionaryStoreRecovery.open]. */
    fun markReset() {
        resetNotice.record()
        synchronized(stateLock) {
            resetPending = true
            publishState()
        }
    }

    /** Lets the next [load] run now, for an explicit "Try again". */
    fun skipBackoff() = backoff.reset()

    fun acknowledgeReset() {
        resetNotice.clear()
        synchronized(stateLock) {
            resetPending = false
            publishState()
        }
    }

    /** A closed store isn't failing; the next open starts without waiting out an old backoff. */
    fun closed() {
        backoff.reset()
        synchronized(stateLock) {
            failing = false
            publishState(readNotice = false)
        }
    }

    private fun publishState(readNotice: Boolean = true) {
        val pending = resetPending ?: (readNotice && resetNotice.isPending()).also { if (readNotice) resetPending = it }
        _state.value = when {
            pending -> UserDictionaryStorageState.ResetAfterUnreadableStore
            failing -> UserDictionaryStorageState.RetryableReadFailure
            else -> UserDictionaryStorageState.Available
        }
    }
}

/**
 * Spaces out reopen attempts after a failure: [baseDelayMs], doubling per consecutive failure, never
 * more than [maxDelayMs]. Every dictionary lookup asks for the store, so without this a store that
 * stays unreadable would be reopened on each keystroke.
 */
internal class UserDictionaryRetryBackoff(
    private val now: () -> Long,
    private val baseDelayMs: Long = 1_000L,
    private val maxDelayMs: Long = 60_000L,
) {
    var consecutiveFailures: Int = 0
        private set
    private var nextAttemptAtMs: Long = 0L

    fun canAttempt(): Boolean = consecutiveFailures == 0 || now() >= nextAttemptAtMs

    fun recordFailure() {
        consecutiveFailures++
        nextAttemptAtMs = now() + delayAfter(consecutiveFailures)
    }

    fun reset() {
        consecutiveFailures = 0
        nextAttemptAtMs = 0L
    }

    fun delayAfter(failures: Int): Long {
        if (failures <= 0) return 0L
        val doublings = (failures - 1).coerceAtMost(30)
        return (baseDelayMs shl doublings).coerceIn(baseDelayMs, maxDelayMs)
    }
}
