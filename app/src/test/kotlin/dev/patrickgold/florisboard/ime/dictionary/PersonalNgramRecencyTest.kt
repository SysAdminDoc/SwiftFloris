/*
 * Copyright (C) 2026 SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.dictionary

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe

class PersonalNgramRecencyTest : FunSpec({
    test("recent phrase continuation can beat a stale higher count") {
        val now = 1_800_000_000_000L
        val recentScore = PersonalNgramRecency.decayedScore(
            count = 1,
            lastSeenMs = now,
            nowMs = now,
        )
        val staleScore = PersonalNgramRecency.decayedScore(
            count = 4,
            lastSeenMs = now - 60L * 86_400_000L,
            nowMs = now,
        )

        recentScore shouldBeGreaterThan staleScore
    }

    test("normalized score stays bounded") {
        val now = 1_800_000_000_000L
        val maxScore = PersonalNgramRecency.decayedScore(
            count = 2,
            lastSeenMs = now,
            nowMs = now,
        )

        PersonalNgramRecency.normalizedScore(
            count = 2,
            lastSeenMs = now,
            maxScore = maxScore,
            nowMs = now,
        ) shouldBe 1.0
    }

    test("missing legacy timestamp behaves as freshly migrated data") {
        val now = 1_800_000_000_000L

        PersonalNgramRecency.decayedScore(
            count = 3,
            lastSeenMs = 0L,
            nowMs = now,
        ) shouldBe 3.0
    }
})
