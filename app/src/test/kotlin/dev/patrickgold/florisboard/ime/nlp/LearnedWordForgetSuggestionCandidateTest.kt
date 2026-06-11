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

import dev.patrickgold.florisboard.lib.FlorisLocale
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LearnedWordForgetSuggestionCandidateTest : FunSpec({
    test("forget learned word candidate is an action, not commit text") {
        val locale = FlorisLocale.fromTag("en")
        val candidate = LearnedWordForgetSuggestionCandidate("kabob", locale)

        candidate.text shouldBe "kabob"
        candidate.locale shouldBe locale
        candidate.isEligibleForAutoCommit shouldBe false
        candidate.isEligibleForUserRemoval shouldBe false
        candidate.sourceProvider shouldBe null
    }
})
