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

package dev.patrickgold.florisboard.ime.smartbar

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class CandidatesDisplayPolicyTest : FunSpec({
    val candidates = listOf("one", "two", "three", "four", "five", "six")

    test("classic mode keeps the legacy three visible candidates") {
        CandidatesDisplayPolicy.visibleCandidates(
            CandidatesDisplayMode.CLASSIC,
            candidates,
        ) shouldContainExactly listOf("one", "two", "three")

        CandidatesDisplayPolicy.visibleCandidateCount(
            CandidatesDisplayMode.CLASSIC,
            candidateCount = 6,
        ) shouldBe 3
    }

    test("dynamic scrollable mode preserves every candidate for scrolling and TalkBack counts") {
        CandidatesDisplayPolicy.visibleCandidates(
            CandidatesDisplayMode.DYNAMIC_SCROLLABLE,
            candidates,
        ) shouldContainExactly candidates

        CandidatesDisplayPolicy.visibleCandidateCount(
            CandidatesDisplayMode.DYNAMIC_SCROLLABLE,
            candidateCount = 6,
        ) shouldBe 6
    }

    test("horizontal scroll is enabled only for multi-candidate scrollable rows") {
        CandidatesDisplayPolicy.isHorizontallyScrollable(
            CandidatesDisplayMode.CLASSIC,
            candidateCount = 6,
        ) shouldBe false
        CandidatesDisplayPolicy.isHorizontallyScrollable(
            CandidatesDisplayMode.DYNAMIC,
            candidateCount = 6,
        ) shouldBe false
        CandidatesDisplayPolicy.isHorizontallyScrollable(
            CandidatesDisplayMode.DYNAMIC_SCROLLABLE,
            candidateCount = 1,
        ) shouldBe false
        CandidatesDisplayPolicy.isHorizontallyScrollable(
            CandidatesDisplayMode.DYNAMIC_SCROLLABLE,
            candidateCount = 6,
        ) shouldBe true
    }
})
