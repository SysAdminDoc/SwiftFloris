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

package dev.patrickgold.florisboard.ime.keyboard

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SpacebarLearnPolicyTest : FunSpec({
    test("a word typed with no autocorrect is learned as typed") {
        SpacebarLearnPolicy.typedWordToLearn("swift", candidateCommitted = false) shouldBe "swift"
    }

    test("a committed autocorrect was learned by the commit, so the space learns nothing") {
        SpacebarLearnPolicy.typedWordToLearn("floris", candidateCommitted = true) shouldBe null
    }

    test("a candidate that failed to commit leaves the typed word in the text, so it's learned") {
        SpacebarLearnPolicy.typedWordToLearn("floris", candidateCommitted = false) shouldBe "floris"
    }

    test("a space with no word before it learns nothing") {
        SpacebarLearnPolicy.typedWordToLearn("", candidateCommitted = false) shouldBe null
        SpacebarLearnPolicy.typedWordToLearn("  ", candidateCommitted = false) shouldBe null
    }
})
