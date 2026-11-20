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

package dev.patrickgold.florisboard.lib.io

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.io.IOException
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class AssetListingCacheTest : FunSpec({
    test("a folder is read once and answered from memory after that") {
        val cache = AssetListingCache()
        val reads = AtomicInteger()
        val read: (String) -> Array<String>? = { reads.incrementAndGet(); arrayOf("org.florisboard.layouts", "x.flex") }

        cache.list("ime/keyboard", read) shouldContainExactly listOf("org.florisboard.layouts", "x.flex")
        cache.list("ime/keyboard", read) shouldContainExactly listOf("org.florisboard.layouts", "x.flex")
        cache.list("ime/theme", read)

        reads.get() shouldBe 2
    }

    test("callers asking while a folder is being read wait for that read") {
        val cache = AssetListingCache()
        val reads = AtomicInteger()
        val reading = CountDownLatch(1)
        val release = CountDownLatch(1)
        val read: (String) -> Array<String>? = {
            reads.incrementAndGet()
            reading.countDown()
            release.await(5, TimeUnit.SECONDS)
            arrayOf("a", "b")
        }
        val results = Collections.synchronizedList(mutableListOf<List<String>?>())
        val callers = List(8) { Thread { results.add(cache.list("ime/keyboard", read)) } }

        callers.first().start()
        reading.await(5, TimeUnit.SECONDS) shouldBe true
        callers.drop(1).forEach { it.start() }
        release.countDown()
        callers.forEach { it.join(5_000) }

        reads.get() shouldBe 1
        results.size shouldBe 8
        results.forEach { it shouldContainExactly listOf("a", "b") }
    }

    test("a read that throws or finds nothing is tried again next time") {
        val cache = AssetListingCache()
        val reads = AtomicInteger()
        val answers = ArrayDeque(listOf<() -> Array<String>?>({ throw IOException("asset manager closed") }, { null }, { arrayOf("c") }))
        val read: (String) -> Array<String>? = { reads.incrementAndGet(); answers.removeFirst()() }

        shouldThrow<IOException> { cache.list("ime/keyboard", read) }
        cache.list("ime/keyboard", read).shouldBeNull()
        cache.list("ime/keyboard", read) shouldContainExactly listOf("c")
        cache.list("ime/keyboard", read) shouldContainExactly listOf("c")

        reads.get() shouldBe 3
    }
})
