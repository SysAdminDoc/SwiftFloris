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

class TigrinyaSeraTransliteratorTest : FunSpec({

    test("qhe series — bare 'Q' maps to the 6th-order ቕ (schwa)") {
        TigrinyaSeraTransliterator.transliterate("Q") shouldBe "ቕ"  // ቕ
    }

    test("qhe series — 'Qe' suffix marks the 1st-order ቐ") {
        TigrinyaSeraTransliterator.transliterate("Qe") shouldBe "ቐ"  // ቐ
    }

    test("qhe series — 'Qa' maps to ቓ (4th-form labio-velar precursor)") {
        TigrinyaSeraTransliterator.transliterate("Qa") shouldBe "ቓ"  // ቓ
    }

    test("xa series — bare 'X' maps to the 6th-order ኅ (schwa)") {
        TigrinyaSeraTransliterator.transliterate("X") shouldBe "ኅ"  // ኅ
    }

    test("labio-velar 'kWa' maps to ኳ") {
        TigrinyaSeraTransliterator.transliterate("kWa") shouldBe "ኳ"
    }

    test("shared Amharic mappings still work for 'slam' (Amharic SERA for 'peace')") {
        // 'slam' parses as bare 's' + 'la' + bare 'm' = sə (ስ, U+1235) +
        // la (ላ, U+120B) + mə (ም, U+121D) = ስላም.
        TigrinyaSeraTransliterator.transliterate("slam") shouldBe "ስላም"
    }

    test("Tigrinya longest-match beats Amharic on collisions ('Qa' over 'Q' alone)") {
        // 'Qa' should match the 2-char Tigrinya extra, not 'Q' then 'a'.
        TigrinyaSeraTransliterator.transliterate("Qa") shouldBe "ቓ"
    }

    test("unmapped punctuation passes through unchanged while digit '1' maps") {
        // '@' isn't in either table; '1' maps to Ethiopic digit ፩ (U+1369).
        TigrinyaSeraTransliterator.transliterate("@1") shouldBe "@፩"
    }
})
