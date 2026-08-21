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

package dev.patrickgold.florisboard.debug

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class EnglishGrammarRuleSpikeTest : FunSpec({
    test("singular demonstratives reject plural agreement") {
        EnglishGrammarRuleSpike.findMatches("This are ready.") shouldBe listOf(
            EnglishGrammarRuleSpike.Match(offset = 5, length = 3, replacement = "is"),
        )
    }

    test("plural demonstratives reject singular agreement") {
        EnglishGrammarRuleSpike.findMatches("These is ready.") shouldBe listOf(
            EnglishGrammarRuleSpike.Match(offset = 6, length = 2, replacement = "are"),
        )
    }

    test("correct agreement produces no result") {
        EnglishGrammarRuleSpike.findMatches("This is ready. Those are ready.").shouldBeEmpty()
    }
})
