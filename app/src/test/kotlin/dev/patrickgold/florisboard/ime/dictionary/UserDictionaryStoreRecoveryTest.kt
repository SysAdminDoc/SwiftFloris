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

import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteDiskIOException
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import net.zetetic.database.sqlcipher.SQLiteNotADatabaseException
import java.io.IOException
import java.security.KeyStoreException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Fault injection for the personal dictionary open path. [FakeStore] stands in for the database
 * files and the key: a failure the store can recover from must leave its words where they were,
 * and only proven corruption may move them aside, exactly once.
 */
class UserDictionaryStoreRecoveryTest : FunSpec({

    class FakeDatabase(val words: List<String>) {
        var closed = false
    }

    class FakeStore(var words: List<String>) {
        val quarantined = mutableListOf<List<String>>()
        val verifyFailures = ArrayDeque<Throwable>()
        var buildFailure: Throwable? = null
        var keyAvailable = true
        var quarantineWorks = true
        var builds = 0
        val opened = mutableListOf<FakeDatabase>()

        fun build(): FakeDatabase? {
            buildFailure?.let { throw it }
            if (!keyAvailable) return null
            builds++
            return FakeDatabase(words).also { opened += it }
        }

        fun verify(database: FakeDatabase) {
            verifyFailures.removeFirstOrNull()?.let { throw it }
        }

        /** Runs once the files are aside, to make the replacement fail. */
        var afterQuarantine: (FakeStore.() -> Unit)? = null

        fun quarantine(): Boolean {
            if (!quarantineWorks) return false
            quarantined += words
            words = emptyList()
            afterQuarantine?.invoke(this)
            afterQuarantine = null
            return true
        }

        var resetsRecorded = 0

        fun open(recordReset: () -> Unit = {}): UserDictionaryOpenOutcome<FakeDatabase> = UserDictionaryStoreRecovery.open(
            build = ::build,
            verify = ::verify,
            close = { it.closed = true },
            quarantine = ::quarantine,
            recordReset = {
                resetsRecorded++
                recordReset()
            },
        )
    }

    /** Stands in for the marker file; one instance shared by two loaders is a process restart. */
    class FakeResetNotice : UserDictionaryResetNotice {
        var pending = false
        var reads = 0

        override fun isPending(): Boolean {
            reads++
            return pending
        }

        override fun record() {
            pending = true
        }

        override fun clear() {
            pending = false
        }
    }

    fun newLoader(notice: UserDictionaryResetNotice = FakeResetNotice(), now: () -> Long = { 0L }) =
        UserDictionaryStoreLoader<FakeDatabase>(now = now, resetNotice = notice)

    /** What DictionaryManager does: the open reports a reset to the loader the moment it happens. */
    fun UserDictionaryStoreLoader<FakeDatabase>.loadFrom(store: FakeStore): FakeDatabase? =
        load { store.open(recordReset = ::markReset) }

    fun FakeStore.shouldReopenWithOriginalWords(original: List<String>) {
        val reopened = open()
        reopened.shouldBeInstanceOf<UserDictionaryOpenOutcome.Opened<FakeDatabase>>()
        reopened.database.words shouldBe original
        reopened.replacedUnreadableStore shouldBe false
        quarantined shouldBe emptyList()
    }

    val words = listOf("Kubernetes", "Zoë", "SwiftFloris")

    test("SQLITE_BUSY keeps the store and a later attempt reopens it") {
        val store = FakeStore(words)
        store.verifyFailures += SQLiteDatabaseLockedException("database is locked (code 5 SQLITE_BUSY)")

        val first = store.open()

        first.shouldBeInstanceOf<UserDictionaryOpenOutcome.Unavailable>()
        first.error.shouldBeInstanceOf<SQLiteDatabaseLockedException>()
        store.opened.single().closed shouldBe true
        store.words shouldBe words
        store.shouldReopenWithOriginalWords(words)
    }

    test("a transient I/O failure keeps the store and a later attempt reopens it") {
        for (failure in listOf(
            IllegalStateException("query failed", IOException("EIO")),
            SQLiteDiskIOException("disk I/O error (code 10)"),
        )) {
            val store = FakeStore(words)
            store.verifyFailures += failure

            store.open().shouldBeInstanceOf<UserDictionaryOpenOutcome.Unavailable>()

            store.words shouldBe words
            store.shouldReopenWithOriginalWords(words)
        }
    }

    test("a key that can't be read right now keeps the store and a later attempt reopens it") {
        // The SQLCipher factory returns null when the Keystore can't unwrap the passphrase.
        val store = FakeStore(words)
        store.keyAvailable = false
        store.open() shouldBe UserDictionaryOpenOutcome.Unavailable(null)
        store.builds shouldBe 0

        store.keyAvailable = true
        store.shouldReopenWithOriginalWords(words)

        // A Keystore exception thrown out of the build is no different.
        val throwing = FakeStore(words)
        throwing.buildFailure = KeyStoreException("Keystore operation failed")
        throwing.open().shouldBeInstanceOf<UserDictionaryOpenOutcome.Unavailable>()
        throwing.buildFailure = null
        throwing.shouldReopenWithOriginalWords(words)
    }

    test("a corrupt store is preserved once and replaced, and the replacement opens normally after") {
        for (corruption in listOf(
            SQLiteDatabaseCorruptException("database disk image is malformed (code 11)"),
            SQLiteNotADatabaseException("file is not a database (code 26)"),
        )) {
            val store = FakeStore(words)
            store.verifyFailures += corruption

            val outcome = store.open()

            outcome.shouldBeInstanceOf<UserDictionaryOpenOutcome.Opened<FakeDatabase>>()
            outcome.replacedUnreadableStore shouldBe true
            outcome.database.words shouldBe emptyList()
            store.opened.first().closed shouldBe true
            store.quarantined shouldBe listOf(words)

            val next = store.open()
            next.shouldBeInstanceOf<UserDictionaryOpenOutcome.Opened<FakeDatabase>>()
            next.replacedUnreadableStore shouldBe false
            store.quarantined.size shouldBe 1
        }
    }

    test("a corrupt store that can't be preserved is left in place and no replacement is made") {
        val store = FakeStore(words)
        store.quarantineWorks = false
        store.verifyFailures += SQLiteDatabaseCorruptException("malformed")

        store.open().shouldBeInstanceOf<UserDictionaryOpenOutcome.Unavailable>()

        store.builds shouldBe 1
        store.words shouldBe words
    }

    test("a replacement that also fails to read is closed and reported unavailable") {
        val store = FakeStore(words)
        store.verifyFailures += SQLiteDatabaseCorruptException("malformed")
        store.verifyFailures += SQLiteDiskIOException("disk I/O error")

        store.open().shouldBeInstanceOf<UserDictionaryOpenOutcome.Unavailable>()

        store.opened.size shouldBe 2
        store.opened.all { it.closed } shouldBe true
        // The words were moved aside before the replacement failed, so the reset is on record.
        store.resetsRecorded shouldBe 1
    }

    test("a reset whose replacement fails to open still shows the reset notice, not the retry one") {
        for (replacementFailure in listOf<FakeStore.() -> Unit>(
            { verifyFailures += SQLiteDiskIOException("disk I/O error") },
            { buildFailure = KeyStoreException("Keystore operation failed") },
        )) {
            val store = FakeStore(words)
            val loader = newLoader()
            store.verifyFailures += SQLiteDatabaseCorruptException("malformed")
            store.afterQuarantine = replacementFailure

            loader.loadFrom(store) shouldBe null
            loader.state.value shouldBe UserDictionaryStorageState.ResetAfterUnreadableStore

            // The empty replacement opening normally later must not hide what happened.
            store.buildFailure = null
            loader.skipBackoff()
            loader.loadFrom(store)?.words shouldBe emptyList()
            loader.state.value shouldBe UserDictionaryStorageState.ResetAfterUnreadableStore
            store.quarantined shouldBe listOf(words)
        }
    }

    test("the reset notice comes back after a restart until the user acknowledges it") {
        val notice = FakeResetNotice()
        val store = FakeStore(words)
        store.verifyFailures += SQLiteDatabaseCorruptException("malformed")
        newLoader(notice).loadFrom(store)

        // A new process: new loader, same marker file.
        val restarted = newLoader(notice)
        restarted.state.value shouldBe UserDictionaryStorageState.Available
        restarted.loadFrom(store)?.words shouldBe emptyList()
        restarted.state.value shouldBe UserDictionaryStorageState.ResetAfterUnreadableStore

        // A failed open after that keeps the reset notice on top.
        store.keyAvailable = false
        restarted.closed()
        restarted.loadFrom(store) shouldBe null
        restarted.state.value shouldBe UserDictionaryStorageState.ResetAfterUnreadableStore

        restarted.acknowledgeReset()
        notice.pending shouldBe false
        restarted.state.value shouldBe UserDictionaryStorageState.RetryableReadFailure

        store.keyAvailable = true
        val afterAcknowledging = newLoader(notice)
        afterAcknowledging.loadFrom(store)?.words shouldBe emptyList()
        afterAcknowledging.state.value shouldBe UserDictionaryStorageState.Available
        store.quarantined.size shouldBe 1
    }

    test("an exception thrown out of the open counts as a failed attempt and starts the backoff") {
        var nowMs = 0L
        val loader = newLoader(now = { nowMs })
        var attempts = 0

        loader.load {
            attempts++
            throw InterruptedException("lookup timed out")
        } shouldBe null
        loader.state.value shouldBe UserDictionaryStorageState.RetryableReadFailure

        loader.load { attempts++; error("must wait out the backoff") } shouldBe null
        attempts shouldBe 1

        nowMs += 1_000
        val store = FakeStore(words)
        loader.loadFrom(store)?.words shouldBe words
        loader.state.value shouldBe UserDictionaryStorageState.Available
    }

    test("an open interrupted while it runs still finishes, and its caller gets the result") {
        val executor = Executors.newSingleThreadExecutor()
        try {
            val started = CountDownLatch(1)
            val release = CountDownLatch(1)
            val outcome = AtomicReference<String?>()
            val stillInterrupted = AtomicBoolean(false)
            val caller = Thread {
                outcome.set(
                    UserDictionaryStoreRecovery.runToCompletion(executor) {
                        started.countDown()
                        release.await()
                        "opened"
                    },
                )
                stillInterrupted.set(Thread.currentThread().isInterrupted)
            }
            caller.start()
            started.await(5, TimeUnit.SECONDS) shouldBe true
            caller.interrupt()
            Thread.sleep(50)
            caller.isAlive shouldBe true
            release.countDown()
            caller.join(5_000)

            outcome.get() shouldBe "opened"
            stillInterrupted.get() shouldBe true
        } finally {
            executor.shutdownNow()
        }
    }

    test("an open that throws hands its own error to the caller") {
        val executor = Executors.newSingleThreadExecutor()
        try {
            val failure = SQLiteDiskIOException("disk I/O error")
            val thrown = runCatching {
                UserDictionaryStoreRecovery.runToCompletion<String>(executor) { throw failure }
            }.exceptionOrNull()
            thrown shouldBe failure
        } finally {
            executor.shutdownNow()
        }
    }

    test("the loader waits out a growing backoff, then reopens and clears the retry notice") {
        var nowMs = 10_000L
        val store = FakeStore(words)
        val loader = newLoader(now = { nowMs })
        store.verifyFailures += SQLiteDatabaseLockedException("busy")
        store.verifyFailures += SQLiteDatabaseLockedException("busy")

        loader.loadFrom(store) shouldBe null
        loader.state.value shouldBe UserDictionaryStorageState.RetryableReadFailure
        store.builds shouldBe 1

        nowMs += 999
        loader.loadFrom(store) shouldBe null
        store.builds shouldBe 1

        nowMs += 1
        loader.loadFrom(store) shouldBe null
        store.builds shouldBe 2

        nowMs += 1_999
        loader.loadFrom(store) shouldBe null
        store.builds shouldBe 2

        nowMs += 1
        loader.loadFrom(store)?.words shouldBe words
        loader.state.value shouldBe UserDictionaryStorageState.Available
        store.quarantined shouldBe emptyList()
    }

    test("the loader keeps the reset notice until it is acknowledged") {
        val store = FakeStore(words)
        val loader = newLoader()
        store.verifyFailures += SQLiteDatabaseCorruptException("malformed")

        loader.loadFrom(store)?.words shouldBe emptyList()
        loader.state.value shouldBe UserDictionaryStorageState.ResetAfterUnreadableStore

        loader.closed()
        loader.loadFrom(store)
        loader.state.value shouldBe UserDictionaryStorageState.ResetAfterUnreadableStore

        loader.acknowledgeReset()
        loader.state.value shouldBe UserDictionaryStorageState.Available
    }

    test("an explicit retry skips the backoff, and closing the store clears the retry state") {
        val store = FakeStore(words)
        val loader = newLoader()
        store.keyAvailable = false
        loader.loadFrom(store) shouldBe null
        loader.loadFrom(store) shouldBe null

        loader.skipBackoff()
        store.keyAvailable = true
        loader.loadFrom(store)?.words shouldBe words

        store.keyAvailable = false
        loader.loadFrom(store) shouldBe null
        loader.state.value shouldBe UserDictionaryStorageState.RetryableReadFailure
        loader.closed()
        loader.state.value shouldBe UserDictionaryStorageState.Available
        store.keyAvailable = true
        loader.loadFrom(store)?.words shouldBe words
    }

    test("backoff doubles from one second and stops at one minute") {
        val backoff = UserDictionaryRetryBackoff(now = { 0L })
        (1..8).map { backoff.delayAfter(it) } shouldBe
            listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 60_000L, 60_000L)
        backoff.delayAfter(0) shouldBe 0L
        backoff.delayAfter(Int.MAX_VALUE) shouldBe 60_000L
    }
})
