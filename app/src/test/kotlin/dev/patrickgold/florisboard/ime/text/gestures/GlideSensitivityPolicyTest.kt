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
import io.kotest.matchers.doubles.shouldBeExactly

class GlideSensitivityPolicyTest : FunSpec({
    test("default sensitivity preserves legacy thresholds") {
        GlideSensitivityPolicy.thresholdScale(50) shouldBeExactly 1.0
    }

    test("higher sensitivity lowers gesture thresholds") {
        GlideSensitivityPolicy.thresholdScale(100) shouldBeExactly 0.5
    }

    test("lower sensitivity raises gesture thresholds and clamps input") {
        GlideSensitivityPolicy.thresholdScale(0) shouldBeExactly 1.5
        GlideSensitivityPolicy.thresholdScale(-50) shouldBeExactly 1.5
        GlideSensitivityPolicy.thresholdScale(150) shouldBeExactly 0.5
    }
})
