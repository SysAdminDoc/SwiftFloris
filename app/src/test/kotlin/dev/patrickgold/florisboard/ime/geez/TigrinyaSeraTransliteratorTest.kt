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

    test("qhe series — bare 'Q' maps to ቐ (Tigrinya-only)") {
        TigrinyaSeraTransliterator.transliterate("Q") shouldBe "\u1250"  // ቐ
    }

    test("qhe series — 'Qa' maps to ቓ (4th-form labio-velar precursor)") {
        TigrinyaSeraTransliterator.transliterate("Qa") shouldBe "\u1253"  // ቓ
    }

    test("xa series — bare 'X' maps to ኀ (Tigrinya-only)") {
        TigrinyaSeraTransliterator.transliterate("X") shouldBe "\u1280"  // ኀ
    }

    test("labio-velar 'kWa' maps to ኳ") {
        TigrinyaSeraTransliterator.transliterate("kWa") shouldBe "\u12B3"
    }

    test("shared Amharic mappings still work for 'slam' (Amharic SERA for 'peace')") {
        // 'slam' parses as 's' + 'lam' = sä (ሰ, U+1230) + lam (la=ላ U+120B + m=መ U+1218).
        TigrinyaSeraTransliterator.transliterate("slam") shouldBe "\u1230\u120B\u1218"
    }

    test("Tigrinya longest-match beats Amharic on collisions ('Qa' over 'Q' alone)") {
        // 'Qa' should match the 2-char Tigrinya extra, not 'Q' then 'a'.
        TigrinyaSeraTransliterator.transliterate("Qa") shouldBe "\u1253"
    }

    test("unmapped punctuation passes through unchanged while digit '1' maps") {
        // '@' isn't in either table; '1' maps to Ethiopic digit ፩ (U+1369).
        TigrinyaSeraTransliterator.transliterate("@1") shouldBe "@\u1369"
    }
})
