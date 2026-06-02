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

/**
 * ROADMAP §10.5 L2.1c — script-based fast language detector.
 *
 * The Translation quick-action surface (P2, v1.8.3) asks for "the
 * source language of the selected text" so it can pre-fill the
 * source dropdown when the user hits "Translate selection". A full
 * statistical detector (CLD3, Lingua) would be overkill — the
 * IME's existing subtype list narrows the candidate set to the
 * languages the user has actually activated, and the **dominant
 * Unicode script** in the selection collapses the ambiguity for
 * 80% of real-world inputs without any training data.
 *
 * This detector classifies text into a [DetectedScript] and returns
 * a confidence in `[0, 1]` based on the fraction of letters in the
 * dominant script. Whitespace + punctuation + digits are excluded
 * from the denominator so "Привет! 12345" still classifies as
 * Cyrillic with high confidence.
 *
 * Heavy multilingual disambiguation lives in the translator addon
 * itself (Bergamot ships a language-identification head); this is
 * the pre-flight that the IME runs without binding.
 */
object LanguageDetector {

    enum class DetectedScript(val rangesLo: IntArray, val rangesHi: IntArray) {
        // Basic Latin (A-Z, a-z) plus Latin-1 Supplement / Latin Extended-A/-B
        // (0x00C0-0x024F) and Latin Extended Additional (0x1E00-0x1EFF) so that
        // accented letters (é à ñ ü ç ã …) — ubiquitous in the very languages
        // this detector targets — classify as LATIN instead of UNKNOWN. The
        // non-letter symbols inside 0x00C0-0x00FF (× ÷) are already filtered out
        // upstream by the Character.isLetter() gate.
        LATIN(intArrayOf(0x0041, 0x0061, 0x00C0, 0x1E00), intArrayOf(0x005A, 0x007A, 0x024F, 0x1EFF)),
        CYRILLIC(intArrayOf(0x0400), intArrayOf(0x04FF)),
        GREEK(intArrayOf(0x0370), intArrayOf(0x03FF)),
        HEBREW(intArrayOf(0x0590), intArrayOf(0x05FF)),
        ARABIC(intArrayOf(0x0600), intArrayOf(0x06FF)),
        DEVANAGARI(intArrayOf(0x0900), intArrayOf(0x097F)),
        BENGALI(intArrayOf(0x0980), intArrayOf(0x09FF)),
        CJK(intArrayOf(0x4E00, 0x3040, 0xAC00), intArrayOf(0x9FFF, 0x30FF, 0xD7AF)),
        THAI(intArrayOf(0x0E00), intArrayOf(0x0E7F)),
        UNKNOWN(intArrayOf(), intArrayOf());

        fun contains(codePoint: Int): Boolean {
            for (i in rangesLo.indices) {
                if (codePoint in rangesLo[i]..rangesHi[i]) return true
            }
            return false
        }
    }

    data class Detection(val script: DetectedScript, val confidence: Float) {
        init {
            require(confidence in 0f..1f) { "confidence must be in [0, 1]" }
        }
    }

    /**
     * Detect the dominant script in [text]. Returns
     * [DetectedScript.UNKNOWN] with confidence 0 when [text] is
     * empty or contains no letter characters at all.
     */
    fun detect(text: String): Detection {
        if (text.isEmpty()) return Detection(DetectedScript.UNKNOWN, 0f)
        val counts = HashMap<DetectedScript, Int>(8)
        var letterCount = 0
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            if (!Character.isLetter(cp)) continue
            letterCount++
            val script = classifyCodePoint(cp)
            counts[script] = (counts[script] ?: 0) + 1
        }
        if (letterCount == 0) return Detection(DetectedScript.UNKNOWN, 0f)
        // Never let stray unclassified letters become the "dominant script":
        // pick the most frequent *recognised* script. Falls back to UNKNOWN only
        // when no supported script appears at all.
        val best = counts.entries
            .filter { it.key != DetectedScript.UNKNOWN }
            .maxByOrNull { it.value }
            ?: return Detection(DetectedScript.UNKNOWN, 0f)
        val confidence = best.value.toFloat() / letterCount.toFloat()
        return Detection(best.key, confidence)
    }

    /** Classify a single letter code point into a [DetectedScript]. */
    private fun classifyCodePoint(codePoint: Int): DetectedScript {
        for (script in DetectedScript.entries) {
            if (script == DetectedScript.UNKNOWN) continue
            if (script.contains(codePoint)) return script
        }
        return DetectedScript.UNKNOWN
    }
}
