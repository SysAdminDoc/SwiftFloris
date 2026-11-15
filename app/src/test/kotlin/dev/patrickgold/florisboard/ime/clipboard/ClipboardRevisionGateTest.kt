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

package dev.patrickgold.florisboard.ime.clipboard

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Collections
import kotlin.random.Random

class ClipboardRevisionGateTest : FunSpec({

    test("a slow media copy finishing after a newer copy and a clear leaves the clear in place") {
        runBlocking {
            val clipboard = CallbackPipeline(this)
            val releaseA = CompletableDeferred<Unit>()

            val a = clipboard.copy("A", cloneGate = releaseA)
            val b = clipboard.copy("B")
            b.join()
            val clear = clipboard.clear()
            clear.join()
            releaseA.complete(Unit)
            a.join()

            clipboard.primary shouldBe null
            clipboard.history.map { it.name } shouldContainExactly listOf("B")
            // A cloned a file, lost the race, and removed it again.
            clipboard.clonedFiles shouldContainExactly listOf("B", "A")
            clipboard.providerFiles.toSet() shouldBe setOf("B")
        }
    }

    test("a slow media copy finishing after a newer copy never becomes newest") {
        runBlocking {
            val clipboard = CallbackPipeline(this)
            val releaseA = CompletableDeferred<Unit>()

            val a = clipboard.copy("A", cloneGate = releaseA)
            clipboard.copy("B").join()
            releaseA.complete(Unit)
            a.join()

            clipboard.primary shouldBe "B"
            clipboard.newestInHistory() shouldBe "B"
            clipboard.providerFiles.toSet() shouldBe setOf("B")
        }
    }

    test("a copy that finishes before the next one arrives is kept, and the later one is newer") {
        runBlocking {
            val clipboard = CallbackPipeline(this)

            clipboard.copy("A").join()
            clipboard.copy("B").join()

            clipboard.primary shouldBe "B"
            clipboard.history.map { it.name } shouldContainExactly listOf("A", "B")
            clipboard.newestInHistory() shouldBe "B"
            clipboard.providerFiles.toSet() shouldBe setOf("A", "B")
        }
    }

    test("a change made in the keyboard outranks a callback that's still cloning") {
        runBlocking {
            val clipboard = CallbackPipeline(this)
            val releaseA = CompletableDeferred<Unit>()

            val a = clipboard.copy("A", cloneGate = releaseA)
            clipboard.localCopy("typed in the keyboard")
            releaseA.complete(Unit)
            a.join()

            clipboard.primary shouldBe "typed in the keyboard"
            clipboard.providerFiles.shouldBeEmpty()
        }
    }

    test("a callback that publishes nothing doesn't make a copy still cloning lose") {
        runBlocking {
            val clipboard = CallbackPipeline(this)
            val releaseA = CompletableDeferred<Unit>()

            val a = clipboard.copy("A", cloneGate = releaseA)
            clipboard.ignored().join()
            releaseA.complete(Unit)
            a.join()

            clipboard.primary shouldBe "A"
            clipboard.history.map { it.name } shouldContainExactly listOf("A")
            clipboard.providerFiles.toSet() shouldBe setOf("A")
        }
    }

    test("publishIfNewest runs nothing once a newer change has published") {
        val gate = ClipboardRevisionGate()
        val first = gate.arrive()
        val second = gate.arrive()
        var runs = 0

        gate.publishIfNewest(second) { runs++ } shouldBe true
        gate.publishIfNewest(first) { runs++ } shouldBe false
        val third = gate.arrive()
        gate.local { }
        gate.publishIfNewest(third) { runs++ } shouldBe false
        runs shouldBe 1
    }

    test("an older revision still publishes when the newer one published nothing") {
        val gate = ClipboardRevisionGate()
        val first = gate.arrive()
        gate.arrive()

        gate.publishIfNewest(first) { } shouldBe true
    }

    test("a repeat of the primary clip claims its revision so an older copy loses") {
        val gate = ClipboardRevisionGate()
        val older = gate.arrive()
        val repeat = gate.arrive()

        gate.claimIfNewest(repeat) { true } shouldBe true
        gate.publishIfNewest(older) { } shouldBe false
    }

    test("a repeat whose clip was replaced claims nothing and can still publish its import") {
        val gate = ClipboardRevisionGate()
        val older = gate.arrive()
        val repeat = gate.arrive()

        gate.publishIfNewest(older) { } shouldBe true
        gate.claimIfNewest(repeat) { false } shouldBe false
        gate.publishIfNewest(repeat) { } shouldBe true
    }

    test("a repeat claims nothing once a newer change has published") {
        val gate = ClipboardRevisionGate()
        val repeat = gate.arrive()
        var checked = false
        gate.local { }

        gate.claimIfNewest(repeat) { checked = true; true } shouldBe false
        checked shouldBe false
    }

    test("a newer revision that never publishes, as a failed copy, leaves an older copy and an older clear to apply") {
        val gate = ClipboardRevisionGate()
        val olderCopy = gate.arrive()
        val olderClear = gate.arrive()
        gate.arrive()
        var runs = 0

        gate.publishIfNewest(olderCopy) { runs++ } shouldBe true
        gate.publishIfNewest(olderClear) { runs++ } shouldBe true
        runs shouldBe 2
    }

    test("a clear runs nothing once a newer copy, repeat or keyboard change has published") {
        val gate = ClipboardRevisionGate()
        var runs = 0

        val beforeCopy = gate.arrive()
        gate.publishIfNewest(gate.arrive()) { } shouldBe true
        gate.publishIfNewest(beforeCopy) { runs++ } shouldBe false

        val beforeRepeat = gate.arrive()
        gate.claimIfNewest(gate.arrive()) { true } shouldBe true
        gate.publishIfNewest(beforeRepeat) { runs++ } shouldBe false

        val beforeLocal = gate.arrive()
        gate.local { }
        gate.publishIfNewest(beforeLocal) { runs++ } shouldBe false
        runs shouldBe 0
    }

    test("a repeat of the primary clip still claims past a newer revision that never published, so an older clear loses") {
        val gate = ClipboardRevisionGate()
        val clear = gate.arrive()
        val repeat = gate.arrive()
        gate.arrive()

        gate.claimIfNewest(repeat) { true } shouldBe true
        gate.publishIfNewest(clear) { } shouldBe false
    }

    test("a clear makes an older copy still cloning lose") {
        val gate = ClipboardRevisionGate()
        val copy = gate.arrive()
        val clear = gate.arrive()

        gate.publishIfNewest(clear) { } shouldBe true
        gate.publishIfNewest(copy) { } shouldBe false
    }

    test("a clear that runs late still applies past a newer callback that published nothing, and a copy made between them lands") {
        runBlocking {
            val clipboard = CallbackPipeline(this)
            clipboard.localCopy("secret")
            val releaseClear = CompletableDeferred<Unit>()
            val releaseA = CompletableDeferred<Unit>()

            // One at a time in this order, the clear empties the clip, A replaces it and the
            // callback that publishes nothing (what a failed copy does) changes nothing, so A
            // ends up on top whatever order they finish in.
            val clear = clipboard.clear(releaseGate = releaseClear)
            val a = clipboard.copy("A", cloneGate = releaseA)
            clipboard.ignored().join()
            releaseClear.complete(Unit)
            clear.join()
            clipboard.primary shouldBe null
            releaseA.complete(Unit)
            a.join()

            clipboard.primary shouldBe "A"
            clipboard.history.map { it.name } shouldContainExactly listOf("A")
            clipboard.providerFiles.toSet() shouldBe setOf("A")
        }
    }

    test("callbacks finishing in any order publish in arrival order and the last one wins") {
        repeat(20) { round ->
            runBlocking {
                val gate = ClipboardRevisionGate()
                val published = Collections.synchronizedList(mutableListOf<Long>())
                val random = Random(round)
                val jobs = (1..50).map {
                    val revision = gate.arrive()
                    val work = random.nextLong(0, 5)
                    launch(Dispatchers.IO) {
                        delay(work)
                        gate.publishIfNewest(revision) { published += revision }
                    }
                }
                jobs.joinAll()

                published.last() shouldBe 50L
                published.toList() shouldBe published.toList().sorted()
                published.toSet().size shouldBe published.size
            }
        }
    }
})

/**
 * Does what ClipboardManager.onPrimaryClipChanged does with the gate: take a revision when the
 * callback arrives, clone off the main thread, then publish or drop the clone and its file.
 */
private class CallbackPipeline(private val scope: CoroutineScope) {
    data class HistoryRow(val name: String, val timestampMs: Long)

    private val gate = ClipboardRevisionGate()
    private val lock = Any()
    private var clock = 0L

    @Volatile
    var primary: String? = null
        private set
    val history: MutableList<HistoryRow> = Collections.synchronizedList(mutableListOf())
    val providerFiles: MutableSet<String> = Collections.synchronizedSet(linkedSetOf())
    val clonedFiles: MutableList<String> = Collections.synchronizedList(mutableListOf())

    fun copy(name: String, cloneGate: CompletableDeferred<Unit>? = null): Job {
        val revision = gate.arrive()
        return scope.launch(Dispatchers.IO) {
            cloneGate?.await()
            providerFiles += name
            clonedFiles += name
            // fromClipData stamps the item after the clone, as the real one does.
            val timestampMs = synchronized(lock) { ++clock }
            if (!gate.publishIfNewest(revision) { primary = name }) {
                providerFiles -= name
                return@launch
            }
            history += HistoryRow(name, timestampMs)
        }
    }

    fun clear(releaseGate: CompletableDeferred<Unit>? = null): Job {
        val revision = gate.arrive()
        return scope.launch(Dispatchers.IO) {
            releaseGate?.await()
            gate.publishIfNewest(revision) { primary = null }
        }
    }

    /**
     * A callback that arrives and publishes nothing: a clear or copy the sync setting ignores, or
     * a copy that couldn't be imported. The manager tests cover the failed import itself.
     */
    fun ignored(): Job {
        gate.arrive()
        return scope.launch(Dispatchers.IO) { }
    }

    fun localCopy(text: String) {
        gate.local { primary = text }
    }

    fun newestInHistory(): String? = synchronized(history) { history.maxByOrNull { it.timestampMs }?.name }
}
