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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class GhostTextSuggestionCandidateTest : FunSpec({
    test("happy path: a multi-token continuation candidate") {
        val candidate = GhostTextSuggestionCandidate(
            text = "to the meeting",
            confidence = 0.85,
            tokenCount = 3,
        )
        candidate.text shouldBe "to the meeting"
        candidate.confidence shouldBe 0.85
        candidate.tokenCount shouldBe 3
        candidate.isEligibleForAutoCommit shouldBe false
        candidate.isEligibleForUserRemoval shouldBe false
        candidate.icon shouldBe null
        candidate.secondaryText shouldBe null
    }

    test("rejects empty text") {
        shouldThrow<IllegalArgumentException> {
            GhostTextSuggestionCandidate(text = "", confidence = 0.5)
        }
    }

    test("rejects confidence outside [0, 1]") {
        shouldThrow<IllegalArgumentException> {
            GhostTextSuggestionCandidate(text = "x", confidence = -0.1)
        }
        shouldThrow<IllegalArgumentException> {
            GhostTextSuggestionCandidate(text = "x", confidence = 1.5)
        }
    }

    test("rejects tokenCount out of range") {
        shouldThrow<IllegalArgumentException> {
            GhostTextSuggestionCandidate(text = "x", confidence = 0.5, tokenCount = 0)
        }
        shouldThrow<IllegalArgumentException> {
            GhostTextSuggestionCandidate(text = "x", confidence = 0.5, tokenCount = 33)
        }
    }

    test("defaults to tokenCount = 1 for a single-word ghost-text candidate") {
        GhostTextSuggestionCandidate(text = "tomorrow", confidence = 0.7).tokenCount shouldBe 1
    }
})
