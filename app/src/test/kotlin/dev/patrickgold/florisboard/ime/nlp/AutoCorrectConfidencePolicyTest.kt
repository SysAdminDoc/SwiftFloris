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
import io.kotest.matchers.shouldBe

class AutoCorrectConfidencePolicyTest : FunSpec({
    test("threshold percentages clamp to the supported slider range") {
        AutoCorrectConfidencePolicy.thresholdFor(0) shouldBe 0.50
        AutoCorrectConfidencePolicy.thresholdFor(80) shouldBe 0.80
        AutoCorrectConfidencePolicy.thresholdFor(150) shouldBe 1.0
    }

    test("threshold boundary is inclusive") {
        AutoCorrectConfidencePolicy.allows(0.79, 80) shouldBe false
        AutoCorrectConfidencePolicy.allows(0.80, 80) shouldBe true
        AutoCorrectConfidencePolicy.allows(0.81, 80) shouldBe true
    }

    test("default is the scorecard-selected confidence") {
        AutoCorrectConfidencePolicy.DEFAULT_PERCENT shouldBe 50
        AutoCorrectConfidencePolicy.DEFAULT_THRESHOLD shouldBe 0.50
    }
})
