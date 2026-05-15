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

class HebrewNiqqudNormalizerTest : FunSpec({
    test("default no-op leaves Hebrew text untouched") {
        val pointed = "בְּרֵאשִׁית"  // "in the beginning", with Niqqud
        HebrewNiqqudNormalizer.normalize(pointed) shouldBe pointed
    }

    test("stripNiqqud removes every U+05B0..U+05C7 mark") {
        val pointed = "בְּרֵאשִׁית"
        val stripped = HebrewNiqqudNormalizer.normalize(pointed, stripNiqqud = true)
        // The consonant skeleton is: bet, resh, alef, shin, yod, tav.
        stripped shouldBe "ברא\u05E9ית"  // ש + Niqqud over it; only Niqqud is removed.
    }

    test("useGereshGershayim rewrites ASCII apostrophe and quote") {
        HebrewNiqqudNormalizer.normalize(
            "ד'ר",                                  // d-apostrophe-r
            useGereshGershayim = true,
        ) shouldBe "ד\u05F3ר"                       // d-Geresh-r
        HebrewNiqqudNormalizer.normalize(
            "ד\"ר",                                  // d-quote-r
            useGereshGershayim = true,
        ) shouldBe "ד\u05F4ר"                       // d-Gershayim-r
    }

    test("non-Hebrew characters pass through unchanged") {
        HebrewNiqqudNormalizer.normalize("Hello world!", stripNiqqud = true) shouldBe "Hello world!"
    }

    test("empty input returns empty output") {
        HebrewNiqqudNormalizer.normalize("") shouldBe ""
    }

    test("isNiqqud predicate matches U+05B0..U+05C7") {
        HebrewNiqqudNormalizer.isNiqqud('\u05B0') shouldBe true
        HebrewNiqqudNormalizer.isNiqqud('\u05C7') shouldBe true
        HebrewNiqqudNormalizer.isNiqqud('\u05D0') shouldBe false   // alef
        HebrewNiqqudNormalizer.isNiqqud('A') shouldBe false
    }

    test("niqqudCount counts every Niqqud mark") {
        // בְּרֵאשִׁית has 5 vowel/dagesh marks visible in the source above.
        HebrewNiqqudNormalizer.niqqudCount("בְּרֵאשִׁית") shouldBe 5
    }
})
