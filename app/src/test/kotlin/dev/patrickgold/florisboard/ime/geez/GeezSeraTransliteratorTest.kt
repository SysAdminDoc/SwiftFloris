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
    test("bare consonants map to the 6th-order Ge'ez character (schwa)") {
        // SERA: a bare consonant is the 6th order (sädis / ə), not the 1st.
        GeezSeraTransliterator.transliterate("h") shouldBe "ህ"
        GeezSeraTransliterator.transliterate("l") shouldBe "ል"
        GeezSeraTransliterator.transliterate("s") shouldBe "ስ"
        GeezSeraTransliterator.transliterate("m") shouldBe "ም"
        GeezSeraTransliterator.transliterate("r") shouldBe "ር"
    }

    test("the 'e' suffix marks the 1st order (ä)") {
        // 'he' = ሀ, 'se' = ሰ, 'me' = መ (the gəʿəz / 1st forms).
        GeezSeraTransliterator.transliterate("he") shouldBe "ሀ"
        GeezSeraTransliterator.transliterate("se") shouldBe "ሰ"
        GeezSeraTransliterator.transliterate("me") shouldBe "መ"
    }

    test("vowel suffixes shift to subsequent orders") {
        // 'su' = ሱ (2nd form of ሰ), 'si' = ሲ (3rd form), 'sa' = ሳ (4th).
        GeezSeraTransliterator.transliterate("su") shouldBe "ሱ"
        GeezSeraTransliterator.transliterate("si") shouldBe "ሲ"
        GeezSeraTransliterator.transliterate("sa") shouldBe "ሳ"
    }

    test("'selam' round-trips to ሰላም (the canonical SERA anchor)") {
        // Greedy longest match: "se" → ሰ (1st form of ሰ), "la" → ላ (4th
        // form of ለ), bare "m" → ም (6th form, schwa). Result: ሰላም — the
        // correct Ge'ez spelling of "peace".
        GeezSeraTransliterator.transliterate("selam") shouldBe "ሰላም"
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
