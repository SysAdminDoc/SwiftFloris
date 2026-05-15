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

package dev.patrickgold.florisboard.ime.geez

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class GeezSeraTransliteratorTest : FunSpec({
    test("default forms map to the 1st-order Ge'ez character") {
        GeezSeraTransliterator.transliterate("h") shouldBe "ሀ"
        GeezSeraTransliterator.transliterate("l") shouldBe "ለ"
        GeezSeraTransliterator.transliterate("s") shouldBe "ሰ"
        GeezSeraTransliterator.transliterate("m") shouldBe "መ"
        GeezSeraTransliterator.transliterate("r") shouldBe "ረ"
    }

    test("vowel suffixes shift to subsequent orders") {
        // 'su' = ሱ (2nd form of ሰ), 'si' = ሲ (3rd form), 'sa' = ሳ (4th).
        GeezSeraTransliterator.transliterate("su") shouldBe "ሱ"
        GeezSeraTransliterator.transliterate("si") shouldBe "ሲ"
        GeezSeraTransliterator.transliterate("sa") shouldBe "ሳ"
    }

    test("'selam' is greedy-matched as se + la + m") {
        // SERA 'e' is a suffix marking the 6th form (schwa). Greedy
        // longest match picks "se" → ስ (s + schwa, 6th form of ሰ),
        // then "la" → ላ (4th form of ለ), then "m" → መ (1st form).
        // Result: ስ + ላ + መ = ስላመ.
        GeezSeraTransliterator.transliterate("selam") shouldBe "ስላመ"
    }

    test("ASCII characters not in the radical set fall through unchanged") {
        // Only the truly-out-of-table Latin letters pass through.
        // 'x', '$', '@' aren't in the table; verify that path.
        GeezSeraTransliterator.transliterate("x\$@") shouldBe "x\$@"
    }

    test("empty input returns empty output") {
        GeezSeraTransliterator.transliterate("") shouldBe ""
    }

    test("Ethiopic punctuation maps correctly") {
        GeezSeraTransliterator.transliterate(".") shouldBe "።"
        GeezSeraTransliterator.transliterate(",") shouldBe "፣"
        GeezSeraTransliterator.transliterate("?") shouldBe "፧"
    }

    test("Ethiopic digits map correctly") {
        GeezSeraTransliterator.transliterate("1") shouldBe "፩"
        GeezSeraTransliterator.transliterate("5") shouldBe "፭"
        GeezSeraTransliterator.transliterate("9") shouldBe "፱"
    }
})
