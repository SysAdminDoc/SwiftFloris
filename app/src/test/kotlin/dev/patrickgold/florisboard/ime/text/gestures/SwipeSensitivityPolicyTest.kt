/*
 * Copyright (C) 2026 The FlorisBoard Contributors
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
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.doubles.shouldBeExactly

class SwipeSensitivityPolicyTest : FunSpec({
    test("default sensitivity preserves legacy swipe thresholds") {
        SwipeSensitivityPolicy.thresholdScale(50) shouldBeExactly 1.0
        SwipeSensitivityPolicy.distanceThreshold(baseDistance = 32.0, sensitivity = 50) shouldBeExactly 32.0
        SwipeSensitivityPolicy.velocityThreshold(baseVelocity = 1900.0, sensitivity = 50) shouldBeExactly 1900.0
    }

    test("higher sensitivity lowers move and touch-up thresholds") {
        SwipeSensitivityPolicy.thresholdScale(100) shouldBeExactly 0.5
        SwipeSensitivityPolicy.shouldTriggerTouchMove(
            relDiffX = 10.0,
            relDiffY = 0.0,
            baseDistance = 32.0,
            sensitivity = 50,
        ).shouldBeFalse()
        SwipeSensitivityPolicy.shouldTriggerTouchMove(
            relDiffX = 10.0,
            relDiffY = 0.0,
            baseDistance = 32.0,
            sensitivity = 100,
        ).shouldBeTrue()
        SwipeSensitivityPolicy.shouldTriggerTouchUp(
            absDiffX = 24.0,
            absDiffY = 0.0,
            velocityX = 1200.0,
            velocityY = 0.0,
            baseDistance = 32.0,
            baseVelocity = 1900.0,
            sensitivity = 50,
        ).shouldBeFalse()
        SwipeSensitivityPolicy.shouldTriggerTouchUp(
            absDiffX = 24.0,
            absDiffY = 0.0,
            velocityX = 1200.0,
            velocityY = 0.0,
            baseDistance = 32.0,
            baseVelocity = 1900.0,
            sensitivity = 100,
        ).shouldBeTrue()
    }

    test("lower sensitivity raises thresholds and clamps input") {
        SwipeSensitivityPolicy.thresholdScale(0) shouldBeExactly 1.5
        SwipeSensitivityPolicy.thresholdScale(-50) shouldBeExactly 1.5
        SwipeSensitivityPolicy.thresholdScale(150) shouldBeExactly 0.5
        SwipeSensitivityPolicy.shouldTriggerTouchUp(
            absDiffX = 40.0,
            absDiffY = 0.0,
            velocityX = 2200.0,
            velocityY = 0.0,
            baseDistance = 32.0,
            baseVelocity = 1900.0,
            sensitivity = 0,
        ).shouldBeFalse()
    }
})
