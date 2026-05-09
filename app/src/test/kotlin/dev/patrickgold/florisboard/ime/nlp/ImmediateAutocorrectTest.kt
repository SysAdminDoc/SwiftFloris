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
    test("standalone i produces an immediate auto-commit candidate") {
        val candidate = ImmediateAutocorrect.englishFirstPersonPronounCandidate(
            rawWord = "i",
            languageCode = "en-US",
        )

        candidate?.text shouldBe "I"
        candidate?.isEligibleForAutoCommit shouldBe true
    }

    test("multi-letter pronoun forms do NOT immediately auto-commit") {
        // These collide with real English words ("ill", "id", "im", "ive") so the
        // immediate path must not silently replace them. The dictionary-aware
        // LatinLanguageProvider.englishPronounCorrection path still surfaces them as
        // suggestions when the typed word is not a real word.
        listOf("id", "ill", "im", "ive", "Id", "Ill", "Im", "Ive").forEach { typed ->
            ImmediateAutocorrect.englishFirstPersonPronounCandidate(typed, "en-US") shouldBe null
        }
    }

    test("englishFirstPersonPronoun still maps multi-letter forms (used by dict-aware path)") {
        // Internal correction map remains intact; only the *immediate* auto-commit path
        // is restricted. Dict-aware callers consume this and apply their own checks.
        ImmediateAutocorrect.englishFirstPersonPronoun("im", "en-US")?.text shouldBe "I'm"
        ImmediateAutocorrect.englishFirstPersonPronoun("ill", "en-US")?.text shouldBe "I'll"
        ImmediateAutocorrect.englishFirstPersonPronoun("id", "en-US")?.text shouldBe "I'd"
        ImmediateAutocorrect.englishFirstPersonPronoun("ive", "en-US")?.text shouldBe "I've"
    }

    test("english first-person pronouns preserve already-correct text") {
        ImmediateAutocorrect.englishFirstPersonPronounCandidate("I'm", "en") shouldBe null
        ImmediateAutocorrect.englishFirstPersonPronounCandidate("I'll", "en") shouldBe null
        ImmediateAutocorrect.englishFirstPersonPronounCandidate("I", "en") shouldBe null
    }

    test("english first-person pronouns preserve all-caps input") {
        ImmediateAutocorrect.englishFirstPersonPronounCandidate("ID", "en") shouldBe null
        ImmediateAutocorrect.englishFirstPersonPronounCandidate("ILL", "en") shouldBe null
        ImmediateAutocorrect.englishFirstPersonPronounCandidate("IM", "en") shouldBe null
        ImmediateAutocorrect.englishFirstPersonPronounCandidate("IVE", "en") shouldBe null
    }

    test("english first-person pronouns do not apply to other languages") {
        ImmediateAutocorrect.englishFirstPersonPronounCandidate("i", "it") shouldBe null
        ImmediateAutocorrect.englishFirstPersonPronounCandidate("im", "it") shouldBe null
        ImmediateAutocorrect.englishFirstPersonPronounCandidate("ill", "fr") shouldBe null
    }
})
