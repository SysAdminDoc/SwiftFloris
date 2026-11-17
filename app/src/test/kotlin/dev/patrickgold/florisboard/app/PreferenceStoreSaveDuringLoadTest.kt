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

package dev.patrickgold.florisboard.app

import dev.patrickgold.jetpref.datastore.jetprefDataStoreOf
import dev.patrickgold.jetpref.datastore.runtime.DataStore
import dev.patrickgold.jetpref.datastore.runtime.DataStoreReader
import dev.patrickgold.jetpref.datastore.runtime.DataStoreWriter
import dev.patrickgold.jetpref.datastore.runtime.ImportStrategy
import dev.patrickgold.jetpref.datastore.runtime.LoadStrategy
import dev.patrickgold.jetpref.datastore.runtime.PersistStrategy
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A load (the store opening at startup, or a restore importing a backup) sets every setting from
 * what it read, and a change to a setting waits for the store's one worker to save it. A change
 * made while a load runs must not hold up the load, or each waits for the other and no setting
 * saves again until the process ends.
 */
class PreferenceStoreSaveDuringLoadTest : FunSpec({
    suspend fun changeDuringLoad(
        load: suspend (DataStore<FlorisPreferenceModel>, DataStoreReader, DataStoreWriter) -> Result<Unit>,
    ) {
        val saves = CopyOnWriteArrayList<String>()
        val writer = DataStoreWriter { content -> saves += content }
        val store = jetprefDataStoreOf(FlorisPreferenceModel::class)
        val model by store
        store.init(LoadStrategy.Disabled, PersistStrategy.UseWriter(writer)).getOrThrow()

        val readStarted = CompletableDeferred<Unit>()
        val releaseRead = CompletableDeferred<Unit>()
        val reader = DataStoreReader {
            readStarted.complete(Unit)
            releaseRead.await()
            ""
        }
        // Not children of the test, so a deadlock fails it instead of hanging the run.
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            val loading = scope.async { load(store, reader, writer) }
            withTimeout(10_000) { readStarted.await() }
            // The worker is inside the load, so this change's save queues behind it.
            val changing = scope.async { model.clipboard.historyEnabled.set(true) }
            withTimeout(10_000) {
                while (!model.clipboard.historyEnabled.get()) delay(1)
            }
            releaseRead.complete(Unit)
            val results = runCatching {
                withTimeout(10_000) { loading.await() to changing.await() }
            }
            withClue("the load and the change made during it both have to finish") {
                results.exceptionOrNull() shouldBe null
            }
            val (loaded, changed) = results.getOrThrow()
            loaded.getOrThrow()
            changed.getOrThrow()
        } finally {
            scope.cancel()
        }
        // The change was queued after the load, so its value is the one saved.
        saves.last() shouldContain "clipboard__history_enabled;true"
        // And the worker is still there for the next change.
        withTimeout(10_000) { model.clipboard.historyEnabled.set(false) }.getOrThrow()
        saves.last() shouldContain "clipboard__history_enabled;false"
    }

    test("a change made while the store opens doesn't deadlock it") {
        changeDuringLoad { store, reader, writer ->
            store.init(LoadStrategy.UseReader(reader), PersistStrategy.UseWriter(writer))
        }
    }

    test("a change made while a restore imports doesn't deadlock it") {
        changeDuringLoad { store, reader, _ ->
            store.import(ImportStrategy.Merge, reader)
        }
    }
})
