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

class ArabicPersianNumeralConverterTest : FunSpec({

    test("westernToArabicIndic converts '12345' to ١٢٣٤٥") {
        ArabicPersianNumeralConverter.westernToArabicIndic("12345") shouldBe "١٢٣٤٥"
    }

    test("westernToExtendedArabicIndic converts '12345' to ۱۲۳۴۵") {
        ArabicPersianNumeralConverter.westernToExtendedArabicIndic("12345") shouldBe "۱۲۳۴۵"
    }

    test("arabicIndicToWestern round-trips") {
        ArabicPersianNumeralConverter.arabicIndicToWestern("٠١٢٣٤٥٦٧٨٩") shouldBe "0123456789"
    }

    test("extendedArabicIndicToWestern round-trips") {
        ArabicPersianNumeralConverter.extendedArabicIndicToWestern("۰۱۲۳۴۵۶۷۸۹") shouldBe "0123456789"
    }

    test("normaliseToWestern collapses all three families to Western digits") {
        val mixed = "١2۳4٥6۷8٩0"
        ArabicPersianNumeralConverter.normaliseToWestern(mixed) shouldBe "1234567890"
    }

    test("non-digit characters pass through unchanged") {
        val text = "hello ١٢٣ world ۴۵۶ !"
        val normalised = ArabicPersianNumeralConverter.normaliseToWestern(text)
        normalised shouldBe "hello 123 world 456 !"
    }

    test("isAnyDigit recognises all three families and rejects letters") {
        ArabicPersianNumeralConverter.isAnyDigit('5'.code) shouldBe true
        ArabicPersianNumeralConverter.isAnyDigit(0x0660) shouldBe true  // Arabic-Indic 0
        ArabicPersianNumeralConverter.isAnyDigit(0x06F9) shouldBe true  // Persian 9
        ArabicPersianNumeralConverter.isAnyDigit('a'.code) shouldBe false
        ArabicPersianNumeralConverter.isAnyDigit('?'.code) shouldBe false
    }

    test("empty text returns empty unchanged") {
        ArabicPersianNumeralConverter.westernToArabicIndic("") shouldBe ""
        ArabicPersianNumeralConverter.normaliseToWestern("") shouldBe ""
    }
})
