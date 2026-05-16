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

package dev.patrickgold.florisboard.ime.translate

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LanguageDetectorTest : FunSpec({

    test("pure Latin text detects as LATIN with full confidence") {
        val det = LanguageDetector.detect("hello world")
        det.script shouldBe LanguageDetector.DetectedScript.LATIN
        det.confidence shouldBe 1f
    }

    test("Cyrillic text detects as CYRILLIC even with embedded digits") {
        val det = LanguageDetector.detect("Привет 12345")
        det.script shouldBe LanguageDetector.DetectedScript.CYRILLIC
        // Digits + space excluded — six Cyrillic letters / six letters total = 1.0.
        det.confidence shouldBe 1f
    }

    test("Hebrew text classifies correctly") {
        val det = LanguageDetector.detect("שלום עולם")
        det.script shouldBe LanguageDetector.DetectedScript.HEBREW
    }

    test("Arabic text classifies correctly") {
        val det = LanguageDetector.detect("مرحبا بالعالم")
        det.script shouldBe LanguageDetector.DetectedScript.ARABIC
    }

    test("Devanagari text classifies correctly") {
        val det = LanguageDetector.detect("नमस्ते दुनिया")
        det.script shouldBe LanguageDetector.DetectedScript.DEVANAGARI
    }

    test("CJK text classifies as CJK across Han + Hiragana + Hangul ranges") {
        LanguageDetector.detect("你好").script shouldBe LanguageDetector.DetectedScript.CJK
        LanguageDetector.detect("こんにちは").script shouldBe LanguageDetector.DetectedScript.CJK
        LanguageDetector.detect("안녕하세요").script shouldBe LanguageDetector.DetectedScript.CJK
    }

    test("Thai text classifies correctly") {
        val det = LanguageDetector.detect("สวัสดี")
        det.script shouldBe LanguageDetector.DetectedScript.THAI
    }

    test("mixed Latin + Hebrew picks the majority script") {
        val det = LanguageDetector.detect("hello שלום")
        // Latin 5 letters, Hebrew 4 letters → Latin wins.
        det.script shouldBe LanguageDetector.DetectedScript.LATIN
        (det.confidence > 0.5f) shouldBe true
        (det.confidence < 1f) shouldBe true
    }

    test("empty or pure-digit input returns UNKNOWN with confidence 0") {
        LanguageDetector.detect("").script shouldBe LanguageDetector.DetectedScript.UNKNOWN
        LanguageDetector.detect("").confidence shouldBe 0f
        LanguageDetector.detect("12345").script shouldBe LanguageDetector.DetectedScript.UNKNOWN
        LanguageDetector.detect("12345").confidence shouldBe 0f
    }
})
