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

class GlideTrailRetentionTest : FunSpec({

    val trailDuration = 200L

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

    test("pruning drops only expired points and preserves order") {
        val points = listOf(
            "oldest" to 800L,
            "expired" to 999L,
            "edge" to 1_000L,
            "recent" to 1_150L,
        )

        GlideTrailRetention.prune(
            points = points,
            nowMillis = 1_200L,
            trailDurationMillis = trailDuration,
            timestampOf = { it.second },
        ) shouldContainExactly listOf("edge" to 1_000L, "recent" to 1_150L)
    }

    test("a trail buffer stays bounded by the window instead of by session length") {
        // Each glided word appends its trace to the shared fade buffer. Without
        // pruning the buffer keeps every word the session ever produced, and the
        // renderer rescans all of it per frame. Replaying many words has to leave
        // only the most recent one behind.
        val pointsPerWord = 40
        var buffer = emptyList<Pair<Int, Long>>()
        var clock = 0L

        repeat(50) { word ->
            clock += trailDuration * 2
            val trace = List(pointsPerWord) { index -> word to (clock + index) }
            buffer = GlideTrailRetention.prune(
                points = buffer,
                nowMillis = clock,
                trailDurationMillis = trailDuration,
                timestampOf = { it.second },
            ) + trace
        }

        buffer.size shouldBe pointsPerWord
        buffer.map { it.first }.toSet() shouldBe setOf(49)
    }

    test("two traces finishing inside one window both survive") {
        // Lifting two fingers together must not discard the first trail.
        val firstFinger = List(3) { 1 to (1_000L + it) }
        val secondFinger = List(3) { 2 to (1_050L + it) }

        val buffer = GlideTrailRetention.prune(
            points = firstFinger,
            nowMillis = 1_060L,
            trailDurationMillis = trailDuration,
            timestampOf = { it.second },
        ) + secondFinger

        buffer.map { it.first }.toSet() shouldBe setOf(1, 2)
    }
})
