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

package dev.patrickgold.florisboard.ime.cjk

import dev.patrickgold.florisboard.ime.cjk.MixedScriptSpacing.ScriptClass
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class MixedScriptSpacingTest : FunSpec({
    fun classify(s: String) = MixedScriptSpacing.classify(s.codePointAt(0))

    context("classify") {
        test("ASCII digits are DIGIT") {
            classify("0") shouldBe ScriptClass.DIGIT
            classify("9") shouldBe ScriptClass.DIGIT
        }
        test("Latin letters are LATIN") {
            classify("A") shouldBe ScriptClass.LATIN
            classify("z") shouldBe ScriptClass.LATIN
            classify("é") shouldBe ScriptClass.LATIN
        }
        test("Han ideographs are HAN") {
            classify("你") shouldBe ScriptClass.HAN
            classify("好") shouldBe ScriptClass.HAN
            classify("章") shouldBe ScriptClass.HAN
        }
        test("Extension B ideographs (surrogate pair) are HAN") {
            // U+20000 — first code point of CJK Ext B.
            classify("𠀀") shouldBe ScriptClass.HAN
        }
        test("Japanese kana are HAN-class") {
            classify("あ") shouldBe ScriptClass.HAN // Hiragana
            classify("カ") shouldBe ScriptClass.HAN // Katakana
        }
        test("Hangul is NEUTRAL (Korean is already word-spaced)") {
            classify("한") shouldBe ScriptClass.NEUTRAL
        }
        test("whitespace and punctuation are NEUTRAL") {
            classify(" ") shouldBe ScriptClass.NEUTRAL
            classify(",") shouldBe ScriptClass.NEUTRAL
            classify("，") shouldBe ScriptClass.NEUTRAL // full-width comma
        }
    }

    context("needsBoundary matrix") {
        test("Han↔Latin both directions") {
            MixedScriptSpacing.needsBoundary(ScriptClass.HAN, ScriptClass.LATIN) shouldBe true
            MixedScriptSpacing.needsBoundary(ScriptClass.LATIN, ScriptClass.HAN) shouldBe true
        }
        test("Han↔Digit both directions") {
            MixedScriptSpacing.needsBoundary(ScriptClass.HAN, ScriptClass.DIGIT) shouldBe true
            MixedScriptSpacing.needsBoundary(ScriptClass.DIGIT, ScriptClass.HAN) shouldBe true
        }
        test("Latin↔Digit is NOT a boundary (H2O, mp3)") {
            MixedScriptSpacing.needsBoundary(ScriptClass.LATIN, ScriptClass.DIGIT) shouldBe false
            MixedScriptSpacing.needsBoundary(ScriptClass.DIGIT, ScriptClass.LATIN) shouldBe false
        }
        test("same-class runs never get a boundary") {
            MixedScriptSpacing.needsBoundary(ScriptClass.HAN, ScriptClass.HAN) shouldBe false
            MixedScriptSpacing.needsBoundary(ScriptClass.LATIN, ScriptClass.LATIN) shouldBe false
            MixedScriptSpacing.needsBoundary(ScriptClass.DIGIT, ScriptClass.DIGIT) shouldBe false
        }
        test("neutral on either side never gets a boundary") {
            MixedScriptSpacing.needsBoundary(ScriptClass.NEUTRAL, ScriptClass.HAN) shouldBe false
            MixedScriptSpacing.needsBoundary(ScriptClass.HAN, ScriptClass.NEUTRAL) shouldBe false
        }
    }

    context("shouldInsertLeadingSpace") {
        test("Latin word before Han candidate → space") {
            MixedScriptSpacing.shouldInsertLeadingSpace("install App", "你好") shouldBe true
            MixedScriptSpacing.shouldInsertLeadingSpace("App", "你好") shouldBe true
        }
        test("Han before Latin commit → space") {
            MixedScriptSpacing.shouldInsertLeadingSpace("安装", "App") shouldBe true
        }
        test("Han before digit commit → space") {
            MixedScriptSpacing.shouldInsertLeadingSpace("第", "3") shouldBe true
        }
        test("digit before Han commit → space (第 3 章)") {
            MixedScriptSpacing.shouldInsertLeadingSpace("第3", "章") shouldBe true
        }
        test("Han run stays glued") {
            MixedScriptSpacing.shouldInsertLeadingSpace("你", "好") shouldBe false
        }
        test("Latin run stays glued") {
            MixedScriptSpacing.shouldInsertLeadingSpace("hel", "lo") shouldBe false
        }
        test("already separated by whitespace → no double space") {
            MixedScriptSpacing.shouldInsertLeadingSpace("安装 ", "App") shouldBe false
            MixedScriptSpacing.shouldInsertLeadingSpace("App", " 你好") shouldBe false
        }
        test("full-width punctuation boundary is not spaced") {
            MixedScriptSpacing.shouldInsertLeadingSpace("你好，", "App") shouldBe false
        }
        test("empty inputs are safe") {
            MixedScriptSpacing.shouldInsertLeadingSpace("", "App") shouldBe false
            MixedScriptSpacing.shouldInsertLeadingSpace("安装", "") shouldBe false
        }
        test("surrogate-pair Han before Latin → space") {
            MixedScriptSpacing.shouldInsertLeadingSpace("𠀀", "x") shouldBe true
        }
    }
})
