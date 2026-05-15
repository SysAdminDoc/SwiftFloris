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

package dev.patrickgold.florisboard.ime.bidi

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ArabicShaperTest : FunSpec({
    test("isolated single letter becomes its isolated presentation form") {
        // Beh in isolation → U+FE8F.
        ArabicShaper.shape("\u0628") shouldBe "\uFE8F"
    }

    test("two-letter word: Beh + Teh → initial Beh, final Teh") {
        // \u0628 = Beh, \u062A = Teh
        // Initial Beh = \uFE91, final Teh = \uFE96.
        ArabicShaper.shape("\u0628\u062A") shouldBe "\uFE91\uFE96"
    }

    test("three-letter word: Beh + Beh + Beh → initial / medial / final") {
        ArabicShaper.shape("\u0628\u0628\u0628") shouldBe "\uFE91\uFE92\uFE90"
    }

    test("right-joining-only letter (Alef) takes final form after a joining letter") {
        // \u0628 + \u0627 (Beh + Alef) → initial Beh + final Alef.
        // Initial Beh = \uFE91, final Alef = \uFE8E.
        ArabicShaper.shape("\u0628\u0627") shouldBe "\uFE91\uFE8E"
    }

    test("Alef does not chain to the next letter (right-joining)") {
        // Alef + Beh: Alef is non-joining-after, so Alef → isolated,
        // Beh → isolated.
        ArabicShaper.shape("\u0627\u0628") shouldBe "\uFE8D\uFE8F"
    }

    test("non-Arabic characters pass through unchanged") {
        ArabicShaper.shape("Hello \u0628") shouldBe "Hello \uFE8F"
    }

    test("empty string returns empty string") {
        ArabicShaper.shape("") shouldBe ""
    }
})
