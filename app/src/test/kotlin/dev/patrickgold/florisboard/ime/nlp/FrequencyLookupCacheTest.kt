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

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Test
import java.io.IOException

class FrequencyLookupCacheTest {
    /**
     * The next keystroke cancels a suggestion request while its lookup is suspended. The
     * request after it has to see the word's real frequency, not a 0.0 left by the cancel.
     */
    @Test
    fun aLookupCancelledMidwayIsNotCached() = runTest {
        val cache = FrequencyLookupCache(16)
        val lookupStarted = CompletableDeferred<Unit>()
        val firstRequest = launch {
            cache.getOrLookup("en-river") {
                lookupStarted.complete(Unit)
                awaitCancellation()
            }
        }
        lookupStarted.await()
        firstRequest.cancel()
        yield()

        cache["en-river"] shouldBe null
        cache.getOrLookup("en-river") { 0.93 } shouldBe 0.93
        cache["en-river"] shouldBe 0.93
    }

    @Test
    fun aFailedLookupAnswersZeroOnceAndIsAskedAgain() = runTest {
        val cache = FrequencyLookupCache(16)

        cache.getOrLookup("en-river") { throw IOException("asset busy") } shouldBe 0.0

        cache.size() shouldBe 0
        cache.getOrLookup("en-river") { 0.93 } shouldBe 0.93
    }

    @Test
    fun aFinishedLookupIsCachedAndClamped() = runTest {
        val cache = FrequencyLookupCache(16)
        var lookups = 0

        cache.getOrLookup("en-river") { lookups++; 1.4 } shouldBe 1.0
        cache.getOrLookup("en-river") { lookups++; 0.2 } shouldBe 1.0

        lookups shouldBe 1
    }
}
