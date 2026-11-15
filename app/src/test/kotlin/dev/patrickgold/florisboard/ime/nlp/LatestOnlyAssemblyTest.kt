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

package dev.patrickgold.florisboard.ime.nlp

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.Collections

class LatestOnlyAssemblyTest : FunSpec({

    fun published(): MutableList<List<String>> = Collections.synchronizedList(mutableListOf())

    suspend fun waitUntil(condition: () -> Boolean) {
        withTimeout(5_000) {
            while (!condition()) delay(5)
        }
    }

    test("an older assembly that can't be cancelled finishes after a newer empty one and is dropped") {
        runBlocking {
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val results = published()
            val assembly = LatestOnlyAssembly<List<String>>(scope) { results += it }
            val startedA = CompletableDeferred<Unit>()
            val releaseA = CompletableDeferred<Unit>()

            assembly.request {
                // Stands in for provider work that ignores cancellation.
                withContext(NonCancellable) {
                    startedA.complete(Unit)
                    releaseA.await()
                }
                listOf("stale", "candidates")
            }
            startedA.await()
            assembly.request { emptyList() }
            waitUntil { results.isNotEmpty() }
            releaseA.complete(Unit)
            waitUntil { assembly.activeAssemblies == 0 }

            results.toList() shouldContainExactly listOf(emptyList())
            scope.cancel()
        }
    }

    test("cancelled assemblies stop, leaving at most the newest one running") {
        runBlocking {
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val results = published()
            val assembly = LatestOnlyAssembly<List<String>>(scope) { results += it }
            val releaseNewest = CompletableDeferred<Unit>()

            repeat(5) { index ->
                assembly.request {
                    if (index == 4) releaseNewest.await() else CompletableDeferred<Unit>().await()
                    listOf("request $index")
                }
            }
            waitUntil { assembly.activeAssemblies <= 1 }
            assembly.activeAssemblies shouldBeLessThanOrEqual 1
            releaseNewest.complete(Unit)
            waitUntil { results.isNotEmpty() && assembly.activeAssemblies == 0 }

            results.toList() shouldContainExactly listOf(listOf("request 4"))
            scope.cancel()
        }
    }

    test("requests that finish in any order publish only in request order") {
        runBlocking {
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val results = published()
            val assembly = LatestOnlyAssembly<List<String>>(scope) { results += it }

            repeat(200) { index ->
                assembly.request {
                    withContext(NonCancellable) { delay((index * 7L) % 5) }
                    listOf("$index")
                }
            }
            waitUntil { assembly.activeAssemblies == 0 }

            val order = results.toList().map { it.single().toInt() }
            order shouldBe order.sorted()
            order.last() shouldBe 199
            scope.cancel()
        }
    }
})
