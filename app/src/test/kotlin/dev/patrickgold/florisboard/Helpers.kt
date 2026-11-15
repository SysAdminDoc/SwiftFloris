/*
 * Copyright (C) 2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.floats.FloatToleranceMatcher
import io.kotest.matchers.floats.gt
import io.kotest.matchers.floats.gte
import io.kotest.matchers.floats.lt
import io.kotest.matchers.floats.lte
import io.kotest.matchers.shouldBe

fun Dp.shouldBeLessThan(other: Dp, tolerance: Dp = 0.dp): Dp {
    this.value shouldBe lt((other + tolerance).value)
    return this
}

fun Dp.shouldBeLessThanOrEqualTo(other: Dp, tolerance: Dp = 0.dp): Dp {
    this.value shouldBe lte((other + tolerance).value)
    return this
}

fun Dp.shouldBeGreaterThan(other: Dp, tolerance: Dp = 0.dp): Dp {
    this.value shouldBe gt((other - tolerance).value)
    return this
}

fun Dp.shouldBeGreaterThanOrEqualTo(other: Dp, tolerance: Dp = 0.dp): Dp {
    this.value shouldBe gte((other - tolerance).value)
    return this
}

infix fun Dp.plusOrMinus(tolerance: Dp): DpToleranceMatcher = DpToleranceMatcher(this, tolerance)

class DpToleranceMatcher(expected: Dp, tolerance: Dp) : Matcher<Dp> {
    val matcher = FloatToleranceMatcher(expected.value, tolerance.value)

    override fun test(value: Dp): MatcherResult {
        return matcher.test(value.value)
    }
}

/**
 * Runs [call] on another thread while this one holds [monitor], and passes once that thread is
 * blocked waiting for it. Fails when [call] finishes first, which means it never takes the lock.
 * Any exception [call] throws once the lock is released is rethrown here.
 */
fun shouldWaitForMonitor(monitor: Any, call: () -> Unit) {
    var failure: Throwable? = null
    val thread = Thread {
        try {
            call()
        } catch (error: Throwable) {
            failure = error
        }
    }
    thread.isDaemon = true
    synchronized(monitor) {
        thread.start()
        val deadline = System.nanoTime() + 5_000_000_000L
        while (thread.state != Thread.State.BLOCKED) {
            check(thread.isAlive) { "The call finished without waiting for the lock." }
            check(System.nanoTime() < deadline) { "The call never reached the lock." }
            Thread.yield()
        }
    }
    thread.join(5_000L)
    check(!thread.isAlive) { "The call didn't finish once the lock was released." }
    failure?.let { throw it }
}
