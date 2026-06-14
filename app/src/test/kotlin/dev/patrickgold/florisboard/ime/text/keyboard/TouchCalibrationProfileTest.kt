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

package dev.patrickgold.florisboard.ime.text.keyboard

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TouchCalibrationProfileTest : FunSpec({
    test("default profile is NORMAL") {
        TouchCalibrationProfile.Default shouldBe TouchCalibrationProfile.NORMAL
    }

    test("NORMAL reproduces the historically shipped hardcoded constants") {
        // These MUST match the old AdaptiveTouchModel / TextKeyboard constants so
        // the default behaviour is byte-for-byte unchanged after calibration landed.
        with(TouchCalibrationProfile.NORMAL) {
            gapRescueDistanceFactor shouldBe 0.32f
            neighbourHorizontalTolerance shouldBe 0.40f
            neighbourVerticalTolerance shouldBe 0.60f
            minSamplesPerKey shouldBe 30
        }
    }

    test("gap-rescue distance widens from conservative to rescue-heavy") {
        val ordered = listOf(
            TouchCalibrationProfile.CONSERVATIVE,
            TouchCalibrationProfile.NORMAL,
            TouchCalibrationProfile.RESCUE_HEAVY,
        )
        ordered.zipWithNext().forEach { (tighter, looser) ->
            (tighter.gapRescueDistanceFactor < looser.gapRescueDistanceFactor) shouldBe true
            (tighter.neighbourHorizontalTolerance < looser.neighbourHorizontalTolerance) shouldBe true
            (tighter.neighbourVerticalTolerance < looser.neighbourVerticalTolerance) shouldBe true
        }
    }

    test("conservative demands the most evidence, rescue-heavy the least") {
        (TouchCalibrationProfile.CONSERVATIVE.minSamplesPerKey >
            TouchCalibrationProfile.NORMAL.minSamplesPerKey) shouldBe true
        (TouchCalibrationProfile.NORMAL.minSamplesPerKey >
            TouchCalibrationProfile.RESCUE_HEAVY.minSamplesPerKey) shouldBe true
    }

    test("every profile keeps physically sane values") {
        TouchCalibrationProfile.values().forEach { profile ->
            (profile.gapRescueDistanceFactor > 0f) shouldBe true
            (profile.gapRescueDistanceFactor < 1f) shouldBe true
            (profile.neighbourHorizontalTolerance > 0f) shouldBe true
            (profile.neighbourVerticalTolerance > 0f) shouldBe true
            (profile.minSamplesPerKey > 0) shouldBe true
        }
    }
})
