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
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe

class CorrectionOutcomePriorsTest : FunSpec({
    test("accepted corrections build confidence for the same typed-corrected pair") {
        val priors = CorrectionOutcomePriors.inMemory()

        priors.recordAccepted("gello", "hello")
        priors.recordAccepted("gello", "hello")

        val signal = priors.signal("gello", "hello")
        signal.acceptedConfidence shouldBeGreaterThan 0.6
        signal.rejectedConfidence shouldBe 0.0
    }

    test("rejected corrections build rejection confidence and damp accepted confidence") {
        val priors = CorrectionOutcomePriors.inMemory()

        priors.recordAccepted("teh", "the")
        priors.recordAccepted("teh", "the")
        priors.recordRejected("teh", "the")

        val signal = priors.signal("teh", "the")
        signal.acceptedConfidence shouldBe (1.0 / 3.0)
        signal.rejectedConfidence shouldBe 0.5
    }

    test("signals are pair-specific and ignore same-word outcomes") {
        val priors = CorrectionOutcomePriors.inMemory()

        priors.recordAccepted("teh", "the")
        priors.recordAccepted("the", "the")

        priors.signal("teh", "ten") shouldBe CorrectionOutcomeSignal()
        priors.signal("the", "the") shouldBe CorrectionOutcomeSignal()
    }

    test("entry count tracks learned pairs and reset clears them") {
        val priors = CorrectionOutcomePriors.inMemory()

        priors.recordAccepted("teh", "the")
        priors.recordRejected("gello", "hello")

        priors.entryCount() shouldBe 2

        priors.reset()

        priors.entryCount() shouldBe 0
        priors.signal("teh", "the") shouldBe CorrectionOutcomeSignal()
    }

    test("awaited reset clears learned pairs before returning") {
        val priors = CorrectionOutcomePriors.inMemory()

        priors.recordAccepted("teh", "the")

        priors.resetAndAwait()

        priors.entryCount() shouldBe 0
    }

    test("accuracy delta compares accepted corrections this week against last week") {
        var nowMs = weekStartMs(20)
        val priors = CorrectionOutcomePriors.inMemory { nowMs }

        repeat(10) { index ->
            priors.recordAccepted("teh$index", "the$index")
        }

        nowMs = weekStartMs(21)
        repeat(7) { index ->
            priors.recordAccepted("gello$index", "hello$index")
        }

        val delta = priors.accuracyDelta()
        delta.previousWeekAccepted shouldBe 10
        delta.currentWeekAccepted shouldBe 7
        delta.changePercent shouldBe 30
        delta.trend shouldBe CorrectionAccuracyTrend.FEWER
    }

    test("accuracy delta reports no baseline until the previous week has accepted corrections") {
        var nowMs = weekStartMs(5)
        val priors = CorrectionOutcomePriors.inMemory { nowMs }

        priors.accuracyDelta() shouldBe CorrectionAccuracyDelta(
            currentWeekAccepted = 0,
            previousWeekAccepted = 0,
        )

        priors.recordAccepted("teh", "the")
        val delta = priors.accuracyDelta()

        delta.currentWeekAccepted shouldBe 1
        delta.previousWeekAccepted shouldBe 0
        delta.changePercent shouldBe null
        delta.trend shouldBe CorrectionAccuracyTrend.NO_BASELINE

        nowMs = weekStartMs(6)
        priors.recordAccepted("gello", "hello")

        priors.accuracyDelta().trend shouldBe CorrectionAccuracyTrend.UNCHANGED
    }
})

private fun weekStartMs(index: Long): Long {
    return index * 7L * 24L * 60L * 60L * 1000L
}
