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
import dev.patrickgold.jetpref.datastore.runtime.DataStoreWriter
import dev.patrickgold.jetpref.datastore.runtime.LoadStrategy
import dev.patrickgold.jetpref.datastore.runtime.PersistStrategy
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * The store saves every change through one worker, and each write waits for that worker to
 * answer. A write whose caller goes away while it waits (a screen that closes, a keyboard that
 * stops) must not stop the worker, or every later change stays in memory only and is gone on the
 * next start.
 */
class PreferenceStoreCancelledWriteTest : FunSpec({
    test("a write cancelled while it waits doesn't stop later writes from saving") {
        val firstSaveStarted = CompletableDeferred<Unit>()
        val releaseFirstSave = CompletableDeferred<Unit>()
        val saves = mutableListOf<String>()
        val writer = DataStoreWriter { content ->
            synchronized(saves) { saves += content }
            if (firstSaveStarted.complete(Unit)) releaseFirstSave.await()
        }
        val store = jetprefDataStoreOf(FlorisPreferenceModel::class)
        val model by store
        store.init(LoadStrategy.Disabled, PersistStrategy.UseWriter(writer)).getOrThrow()

        coroutineScope {
            // The worker stays on this save until it's released.
            val first = async(Dispatchers.Default) { model.devtools.enabled.set(true) }
            withTimeout(10_000) { firstSaveStarted.await() }
            // This write queues behind it, and its caller gives up while it waits.
            val abandoned = launch(Dispatchers.Default) { model.devtools.showPrimaryClip.set(true) }
            withTimeout(10_000) {
                while (!model.devtools.showPrimaryClip.get()) delay(1)
            }
            abandoned.cancelAndJoin()
            releaseFirstSave.complete(Unit)
            withTimeout(10_000) { first.await() }.getOrThrow()
        }

        val later = runCatching {
            withTimeout(10_000) { model.devtools.showInputStateOverlay.set(true) }
        }
        withClue("a write after the cancelled one has to reach the worker and come back") {
            later.exceptionOrNull() shouldBe null
        }
        later.getOrThrow().getOrThrow()
        val lastSave = synchronized(saves) { saves.last() }
        lastSave shouldContain "devtools__show_input_state_overlay"
        // The cancelled caller's change is still in memory, so it's saved with the rest.
        lastSave shouldContain "devtools__show_primary_clip"
    }
})
