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

/**
 * Pangu-style mixed-script boundary spacing.
 *
 * CJK users routinely interleave Han characters with Latin words and Arabic
 * digits (`安装App` / `第3章`). Most CJK input methods insert a thin boundary
 * space at the script transition so the mixed run reads cleanly
 * (`安装 App` / `第 3 章`). SwiftFloris ships the policy here as a pure,
 * allocation-light, fully unit-testable unit; the IME editor commit path
 * ([dev.patrickgold.florisboard.ime.editor.EditorInstance]) consults
 * [shouldInsertLeadingSpace] before committing a character or candidate and
 * prepends a single `U+0020` when a boundary is crossed.
 *
 * Documented matrix (left = char already in the editor, right = char being
 * committed). A boundary space is inserted only on Han↔Latin and Han↔Digit
 * transitions; same-class runs and any transition involving a neutral
 * character (whitespace, punctuation, full-width punctuation, symbols,
 * Hangul, …) are left untouched because they are already self-separating:
 *
 * | left \ right | HAN | LATIN | DIGIT | NEUTRAL |
 * |--------------|-----|-------|-------|---------|
 * | HAN          |  -  |  ␣    |  ␣    |   -     |
 * | LATIN        |  ␣  |  -    |  -    |   -     |
 * | DIGIT        |  ␣  |  -    |  -    |   -     |
 * | NEUTRAL      |  -  |  -    |  -    |   -     |
 *
 * Latin↔Digit is intentionally *not* spaced — `H2O` / `mp3` are ordinary
 * Latin-script tokens, not a mixed-script boundary.
 */
object MixedScriptSpacing {
    enum class ScriptClass { HAN, LATIN, DIGIT, NEUTRAL }

    /**
     * Classifies a Unicode code point into one of the [ScriptClass] buckets.
     *
     * HAN covers CJK ideographs (incl. extensions A–F + compatibility) and the
     * Japanese kana blocks, because kana↔Latin transitions want the same
     * treatment as ideograph↔Latin ones. Hangul is deliberately NEUTRAL:
     * Korean is already word-spaced, so pangu spacing there would be wrong.
     */
    fun classify(cp: Int): ScriptClass {
        return when {
            cp in 0x30..0x39 -> ScriptClass.DIGIT
            isLatin(cp) -> ScriptClass.LATIN
            isHan(cp) -> ScriptClass.HAN
            else -> ScriptClass.NEUTRAL
        }
    }

    private fun isLatin(cp: Int): Boolean {
        return cp in 0x41..0x5A ||      // A–Z
            cp in 0x61..0x7A ||         // a–z
            cp in 0xC0..0x24F ||        // Latin-1 Supplement + Latin Extended-A/B
            cp in 0x1E00..0x1EFF        // Latin Extended Additional
    }

    private fun isHan(cp: Int): Boolean {
        return cp in 0x3040..0x309F ||  // Hiragana
            cp in 0x30A0..0x30FF ||     // Katakana
            cp in 0x31F0..0x31FF ||     // Katakana Phonetic Extensions
            cp in 0x3400..0x4DBF ||     // CJK Unified Ideographs Extension A
            cp in 0x4E00..0x9FFF ||     // CJK Unified Ideographs
            cp in 0xF900..0xFAFF ||     // CJK Compatibility Ideographs
            cp in 0x20000..0x2A6DF ||   // CJK Unified Ideographs Extension B
            cp in 0x2A700..0x2EBEF ||   // CJK Unified Ideographs Extensions C–F
            cp in 0x2F800..0x2FA1F      // CJK Compatibility Ideographs Supplement
    }

    /** True when a boundary space belongs between a [left]- and [right]-class char. */
    fun needsBoundary(left: ScriptClass, right: ScriptClass): Boolean {
        return when {
            left == ScriptClass.HAN && (right == ScriptClass.LATIN || right == ScriptClass.DIGIT) -> true
            right == ScriptClass.HAN && (left == ScriptClass.LATIN || left == ScriptClass.DIGIT) -> true
            else -> false
        }
    }

    /**
     * Returns true when committing [committing] immediately after [textBefore]
     * crosses a Han↔Latin/Digit boundary. The caller is expected to prepend a
     * single `U+0020` to the committed text. Returns false for empty inputs,
     * for runs that already share a script class, and whenever either side of
     * the boundary is a neutral character (so no double spaces are produced).
     */
    fun shouldInsertLeadingSpace(textBefore: CharSequence, committing: CharSequence): Boolean {
        if (textBefore.isEmpty() || committing.isEmpty()) return false
        val left = classify(Character.codePointBefore(textBefore, textBefore.length))
        if (left == ScriptClass.NEUTRAL) return false
        val right = classify(Character.codePointAt(committing, 0))
        if (right == ScriptClass.NEUTRAL) return false
        return needsBoundary(left, right)
    }
}
