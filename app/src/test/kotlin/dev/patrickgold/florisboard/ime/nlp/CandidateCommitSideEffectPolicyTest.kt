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

class CandidateCommitSideEffectPolicyTest : FunSpec({
    test("provider acceptance notification requires a successful commit and a provider") {
        CandidateCommitSideEffectPolicy.shouldNotifyAcceptedProvider(
            commitSucceeded = true,
            hasSourceProvider = true,
        ) shouldBe true

        CandidateCommitSideEffectPolicy.shouldNotifyAcceptedProvider(
            commitSucceeded = false,
            hasSourceProvider = true,
        ) shouldBe false

        CandidateCommitSideEffectPolicy.shouldNotifyAcceptedProvider(
            commitSucceeded = true,
            hasSourceProvider = false,
        ) shouldBe false
    }

    test("learning follows successful non-clipboard candidate commits only") {
        CandidateCommitSideEffectPolicy.shouldLearnCommittedCandidate(
            commitSucceeded = true,
            isClipboardCandidate = false,
        ) shouldBe true

        CandidateCommitSideEffectPolicy.shouldLearnCommittedCandidate(
            commitSucceeded = true,
            isClipboardCandidate = true,
        ) shouldBe false

        CandidateCommitSideEffectPolicy.shouldLearnCommittedCandidate(
            commitSucceeded = false,
            isClipboardCandidate = false,
        ) shouldBe false
    }
})
