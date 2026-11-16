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

import androidx.collection.LruCache
import kotlin.coroutines.cancellation.CancellationException

/**
 * Word frequencies in `[0, 1]`, cached by key, bounded by entry count.
 *
 * Each keystroke cancels the suggestion job before it, and a provider lookup can suspend
 * (the Latin provider's dictionary planner switches to the IO dispatcher), so lookups are
 * often cancelled partway. Only a lookup that finishes is cached. A cancelled one rethrows
 * and a failed one returns 0.0 for this request alone. Caching either as 0.0 marked the word
 * unknown for the rest of the process: the typed word lost the middle slot and space
 * committed a longer completion instead ("river" became "riverbed").
 */
internal class FrequencyLookupCache(maxEntries: Int) {
    private val cache = LruCache<String, Double>(maxEntries)

    operator fun get(key: String): Double? = cache[key]

    fun put(key: String, frequency: Double) {
        cache.put(key, frequency)
    }

    suspend fun getOrLookup(key: String, lookup: suspend () -> Double): Double {
        cache[key]?.let { return it }
        val frequency = try {
            lookup().coerceIn(0.0, 1.0)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return 0.0
        }
        cache.put(key, frequency)
        return frequency
    }

    fun evictAll() = cache.evictAll()

    fun size(): Int = cache.size()
}
