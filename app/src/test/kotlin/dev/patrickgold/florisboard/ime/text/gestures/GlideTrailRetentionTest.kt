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

package dev.patrickgold.florisboard.ime.text.gestures

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

/**
 * Every assertion here drives [GlideTrailRetention.dropExpired], which is the
 * function the keyboard controller actually calls on the shared fade buffer.
 */
class GlideTrailRetentionTest : FunSpec({

    val trailDuration = 200L

    fun buffer(vararg points: Pair<Int, Long>) = mutableListOf(*points)

    fun MutableList<Pair<Int, Long>>.prune(nowMillis: Long) {
        GlideTrailRetention.dropExpired(
            points = this,
            nowMillis = nowMillis,
            trailDurationMillis = trailDuration,
            timestampOf = { it.second },
        )
    }

    test("a point exactly on the window edge is still drawn, so it is kept") {
        GlideTrailRetention.isWithinTrailWindow(
            timestampMillis = 1_000L,
            nowMillis = 1_200L,
            trailDurationMillis = trailDuration,
        ) shouldBe true
    }

    test("a point one millisecond past the edge is no longer drawn") {
        GlideTrailRetention.isWithinTrailWindow(
            timestampMillis = 1_000L,
            nowMillis = 1_201L,
            trailDurationMillis = trailDuration,
        ) shouldBe false
    }

    test("dropping keeps order and mutates the caller's list rather than replacing it") {
        // The controller's buffer is a SnapshotStateList that Compose observes,
        // so the pruning has to happen in place or the draw stops updating.
        val points = buffer(0 to 800L, 1 to 999L, 2 to 1_000L, 3 to 1_150L)
        val identity = points

        points.prune(nowMillis = 1_200L)

        points shouldContainExactly listOf(2 to 1_000L, 3 to 1_150L)
        (points === identity) shouldBe true
    }

    test("the buffer stays bounded by the window instead of by session length") {
        // Each glided word appends its trace to the shared buffer. Unpruned, the
        // buffer keeps every word the session produced and the renderer rescans
        // all of it per frame. Replaying many words leaves only the recent one.
        val pointsPerWord = 40
        val points = buffer()
        var clock = 0L

        repeat(50) { word ->
            clock += trailDuration * 2
            points.prune(nowMillis = clock)
            repeat(pointsPerWord) { index -> points.add(word to (clock + index)) }
        }

        points.size shouldBe pointsPerWord
        points.map { it.first }.toSet() shouldBe setOf(49)
    }

    test("two traces finishing inside one window both survive") {
        // Lifting two fingers together must not discard the first trail.
        val points = buffer(1 to 1_000L, 1 to 1_001L, 1 to 1_002L)

        points.prune(nowMillis = 1_060L)
        listOf(2 to 1_050L, 2 to 1_051L, 2 to 1_052L).forEach(points::add)

        points.map { it.first }.toSet() shouldBe setOf(1, 2)
    }

    test("an expired point sitting after a surviving one is removed too") {
        // The buffer holds traces from different pointers, so it is not globally
        // ordered by timestamp. The renderer draws everything after the first
        // in-window point it finds, which used to include stale points from an
        // earlier trace and joined the two with a stray line.
        val points = buffer(1 to 1_150L, 2 to 900L, 2 to 1_180L)

        points.prune(nowMillis = 1_200L)

        points shouldContainExactly listOf(1 to 1_150L, 2 to 1_180L)
    }

    test("an empty buffer is left alone") {
        val points = buffer()
        points.prune(nowMillis = 5_000L)
        points.size shouldBe 0
    }
})
