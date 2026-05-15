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

class PersianUrduNormalizerTest : FunSpec({
    test("Arabic Yeh normalises to Farsi Yeh") {
        PersianUrduNormalizer.normalize("\u064A") shouldBe "\u06CC"
    }

    test("Arabic Kaf normalises to Farsi Kaf") {
        PersianUrduNormalizer.normalize("\u0643") shouldBe "\u06A9"
    }

    test("Alef Maksura (U+0649) normalises to Farsi Yeh") {
        PersianUrduNormalizer.normalize("\u0649") shouldBe "\u06CC"
    }

    test("stripTatweel removes U+0640 only when the flag is on") {
        // "Beh + Tatweel + Teh" stays unchanged by default.
        PersianUrduNormalizer.normalize("\u0628\u0640\u062A") shouldBe "\u0628\u0640\u062A"
        // With strip enabled, the tatweel is removed.
        PersianUrduNormalizer.normalize("\u0628\u0640\u062A", stripTatweel = true) shouldBe "\u0628\u062A"
    }

    test("TO_PERSIAN converts Latin digits to Persian-Arabic-Indic numerals") {
        PersianUrduNormalizer.normalize(
            "123",
            convertDigits = PersianDigitMode.TO_PERSIAN,
        ) shouldBe "\u06F1\u06F2\u06F3"
    }

    test("TO_PERSIAN also converts Arabic-Indic digits (U+0660..)") {
        PersianUrduNormalizer.normalize(
            "\u0661\u0662\u0663",
            convertDigits = PersianDigitMode.TO_PERSIAN,
        ) shouldBe "\u06F1\u06F2\u06F3"
    }

    test("TO_LATIN converts Arabic-Indic digits to Latin") {
        PersianUrduNormalizer.normalize(
            "\u0661\u0662\u0663",
            convertDigits = PersianDigitMode.TO_LATIN,
        ) shouldBe "123"
    }

    test("non-Arabic characters pass through unchanged") {
        PersianUrduNormalizer.normalize("Hello \u0628") shouldBe "Hello \u0628"
    }

    test("empty input returns empty output") {
        PersianUrduNormalizer.normalize("") shouldBe ""
    }
})
