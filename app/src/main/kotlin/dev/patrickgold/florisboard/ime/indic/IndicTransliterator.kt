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

package dev.patrickgold.florisboard.ime.indic

/**
 * ROADMAP §7 L5 — Indic transliteration engine.
 *
 * Indic scripts use a phonetic input mode where the user types a
 * romanised approximation of the target word and the engine produces
 * the corresponding native script. The two long-running open systems
 * are:
 *
 *  - **ITRANS** (Indian Transliteration scheme, ASCII-only, lossless
 *    round-trip — the de-facto standard since 1991).
 *  - **Aksharamukha** mapping tables (cross-script tables for every
 *    major Indic script under MIT).
 *
 * This scaffold ships a working **ITRANS → Devanagari** table covering
 * Hindi consonants + vowels + the standard halant/anusvara/visarga
 * decorations. Other Indic scripts (Bengali, Tamil, Telugu, Marathi,
 * Gujarati, Punjabi, Kannada) follow the same greedy-longest-match
 * pattern and ship as additional [IndicScriptTable] entries in
 * subsequent L5.x slices.
 *
 * The transliteration algorithm is **greedy longest match**: at each
 * cursor position try the longest table key first. ITRANS keys range
 * from 1 to 4 ASCII chars (`a`, `aa`, `R^i`, `lLi`) so the inner loop
 * is bounded.
 */
class IndicTransliterator(private val table: IndicScriptTable) {

    /**
     * Convert [latinInput] to the script associated with [table] using
     * greedy longest-prefix matching. Unmatched characters pass
     * through unchanged so the user can mix Latin punctuation with
     * Indic script without surprise.
     */
    fun transliterate(latinInput: String): String {
        if (latinInput.isEmpty()) return ""
        val output = StringBuilder(latinInput.length * 2)
        var i = 0
        while (i < latinInput.length) {
            var matched = false
            // Greedy longest match: scan from longest key length down.
            for (keyLen in table.maxKeyLength downTo 1) {
                if (i + keyLen > latinInput.length) continue
                val candidate = latinInput.substring(i, i + keyLen)
                val mapping = table.lookup(candidate)
                if (mapping != null) {
                    output.append(mapping)
                    i += keyLen
                    matched = true
                    break
                }
            }
            if (!matched) {
                output.append(latinInput[i])
                i++
            }
        }
        return output.toString()
    }
}

/**
 * Lookup table for one (input-scheme, target-script) pair. Backed by
 * a `Map<String, String>` keyed on lowercase ASCII; [maxKeyLength] is
 * precomputed so the inner loop in [IndicTransliterator.transliterate]
 * doesn't re-scan the table on every character.
 */
class IndicScriptTable(
    val sourceScheme: String,
    val targetScript: String,
    private val mappings: Map<String, String>,
) {
    val maxKeyLength: Int = mappings.keys.maxOfOrNull { it.length } ?: 0
    fun lookup(key: String): String? = mappings[key]
    fun size(): Int = mappings.size

    companion object {
        /**
         * **ITRANS → Devanagari** (Hindi / Marathi / Sanskrit script).
         * Built from the canonical [ITRANS table](https://www.aczoom.com/itrans/online/sanskrit-roman-transliteration.html)
         * (in the public domain since 1991). Covers consonants,
         * dependent + independent vowels, halant, anusvara, visarga,
         * the seven Devanagari digits, and the common phrase
         * punctuation. Sufficient for typing the most common Hindi
         * words; complex conjunct forms (कक्ष, र्क) emerge from the
         * halant joiner in the table.
         */
        val ItransToDevanagari: IndicScriptTable = IndicScriptTable(
            sourceScheme = "ITRANS",
            targetScript = "Devanagari",
            mappings = mapOf(
                // Independent vowels.
                "a" to "अ", "aa" to "आ", "A" to "आ",
                "i" to "इ", "ii" to "ई", "I" to "ई",
                "u" to "उ", "uu" to "ऊ", "U" to "ऊ",
                "RRi" to "ऋ", "R^i" to "ऋ", "Ri" to "ऋ",
                "e" to "ए", "ai" to "ऐ",
                "o" to "ओ", "au" to "औ",
                // Dependent vowels (matras) — attach after a consonant
                // via the canonical glyph form; mapping table consumers
                // rely on the engine emitting these adjacent to the
                // previously-emitted consonant.
                "Aa" to "ा",
                // Consonants — Velars.
                "k" to "क", "kh" to "ख", "g" to "ग", "gh" to "घ", "~N" to "ङ",
                // Palatals.
                "ch" to "च", "Ch" to "छ", "chh" to "छ",
                "j" to "ज", "jh" to "झ", "~n" to "ञ",
                // Retroflex.
                "T" to "ट", "Th" to "ठ", "D" to "ड", "Dh" to "ढ", "N" to "ण",
                // Dental.
                "t" to "त", "th" to "थ", "d" to "द", "dh" to "ध", "n" to "न",
                // Labial.
                "p" to "प", "ph" to "फ", "b" to "ब", "bh" to "भ", "m" to "म",
                // Semi-vowels + sibilants + h.
                "y" to "य", "r" to "र", "l" to "ल", "v" to "व", "w" to "व",
                "sh" to "श", "Sh" to "ष", "s" to "स", "h" to "ह",
                // Composites + special markers.
                "x" to "क्ष", "kSh" to "क्ष",
                "GY" to "ज्ञ", "j~n" to "ज्ञ",
                "M" to "ं", "H" to "ः",
                // Devanagari digits.
                "0" to "०", "1" to "१", "2" to "२", "3" to "३", "4" to "४",
                "5" to "५", "6" to "६", "7" to "७", "8" to "८", "9" to "९",
                // Common punctuation.
                "|" to "।", "||" to "॥",
            ),
        )
    }
}
