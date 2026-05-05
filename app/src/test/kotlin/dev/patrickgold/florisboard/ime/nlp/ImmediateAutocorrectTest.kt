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

class ImmediateAutocorrectTest : FunSpec({
    test("english first-person pronouns produce immediate auto-commit candidates") {
        val corrections = listOf(
            "i" to "I",
            "id" to "I'd",
            "ill" to "I'll",
            "im" to "I'm",
            "ive" to "I've",
        )

        corrections.forEach { (typed, expected) ->
            val candidate = ImmediateAutocorrect.englishFirstPersonPronounCandidate(
                rawWord = typed,
                languageCode = "en-US",
            )

            candidate?.text shouldBe expected
            candidate?.isEligibleForAutoCommit shouldBe true
        }
    }

    test("english first-person pronouns preserve already-correct text") {
        ImmediateAutocorrect.englishFirstPersonPronounCandidate("I'm", "en") shouldBe null
        ImmediateAutocorrect.englishFirstPersonPronounCandidate("I'll", "en") shouldBe null
    }

    test("english first-person pronouns do not apply to other languages") {
        ImmediateAutocorrect.englishFirstPersonPronounCandidate("im", "it") shouldBe null
        ImmediateAutocorrect.englishFirstPersonPronounCandidate("ill", "fr") shouldBe null
    }
})
