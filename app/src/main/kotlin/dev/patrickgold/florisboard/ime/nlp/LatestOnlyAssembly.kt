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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Runs candidate assembly so only the newest request can publish.
 *
 * Each [request] cancels the one before it. Work that can't stop in time still finishes, but its
 * result is dropped once a newer request has started: the generation check and [publish] run under
 * the same lock that [request] takes, so an older result can never land after a newer one.
 */
internal class LatestOnlyAssembly<T>(
    private val scope: CoroutineScope,
    private val publish: (T) -> Unit,
) {
    private var generation = 0L
    private var job: Job? = null
    private val running = AtomicInteger(0)

    /** Assemblies requested and not yet finished, cancelled ones included until they actually stop. */
    val activeAssemblies: Int get() = running.get()

    @Synchronized
    fun request(produce: suspend () -> T) {
        val mine = ++generation
        job?.cancel()
        // Counted here rather than inside the coroutine: a job cancelled before it is dispatched
        // never runs its body, but its completion handler still fires.
        running.incrementAndGet()
        job = scope.launch {
            val result = produce()
            synchronized(this@LatestOnlyAssembly) {
                if (mine == generation) publish(result)
            }
        }.also { it.invokeOnCompletion { running.decrementAndGet() } }
    }
}
