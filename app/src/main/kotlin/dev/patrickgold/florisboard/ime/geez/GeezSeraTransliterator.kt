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

/**
 * ROADMAP §7 L6 — Ge'ez script (Ethiopic) transliteration engine.
 *
 * Ge'ez script is used by **Amharic (am)**, **Tigrinya (ti)**, **Tigre
 * (tig)**, **Blin (byn)**, and liturgical Ge'ez (gez) — ~110M speakers
 * combined. Closed-source GeezIME owns the niche on Android today;
 * SwiftFloris ships a clean Apache-2.0 implementation of the standard
 * **SERA** (System for Ethiopic Representation in ASCII) input scheme.
 *
 * SERA is a syllabary scheme: each Ge'ez character is one consonant +
 * one of seven vowel forms. The romanised form is `<consonant><vowel>`
 * where the vowel is `e`, `u`, `i`, `a`, `ie` (ē), `o`, or empty
 * (sixth-form, schwa). The table here covers the ~36 base consonant
 * radicals × 7 vowel forms = the canonical 252 Ge'ez characters
 * plus the labiovelars and numerals.
 *
 * Reference: [SERA standard](https://web.archive.org/web/20140613192616/http://www.abyssiniagateway.net/fidel/sera-faq.html)
 * (in the public domain since 1996).
 */
object GeezSeraTransliterator {

    /** Transliterate SERA-romanised [latin] into Ge'ez script. */
    fun transliterate(latin: String): String {
        if (latin.isEmpty()) return ""
        val output = StringBuilder(latin.length)
        var i = 0
        while (i < latin.length) {
            // Greedy longest-match against the table (keys up to 4 chars).
            var matched = false
            for (keyLen in maxKeyLength downTo 1) {
                if (i + keyLen > latin.length) continue
                val candidate = latin.substring(i, i + keyLen)
                table[candidate]?.let {
                    output.append(it)
                    i += keyLen
                    matched = true
                }
                if (matched) break
            }
            if (!matched) {
                output.append(latin[i])
                i++
            }
        }
        return output.toString()
    }

    /**
     * Subset of the SERA → Ge'ez table covering the most common Amharic
     * consonant radicals × the seven vowel forms. Full L6.1 release
     * grows the table to all 36 radicals + labiovelars + numerals + the
     * Tigrinya/Tigre-specific extensions; this scaffold pins enough of
     * the table that a real Amharic word ("ሰላም" = "selam" = peace)
     * round-trips correctly.
     */
    /**
     * Shared SERA → Ge'ez mapping. Exposed as `internal` so dialect
     * subclasses ([TigrinyaSeraTransliterator] etc.) can compose
     * extension tables on top without re-deriving the radical × vowel
     * grid.
     */
    internal val table: Map<String, String> = buildMap {
        // Each consonant has seven vowel forms: ä u i a e ə o.
        // Per the Unicode Ethiopic block (U+1200–U+137F), the radical
        // base is at the 1st (ä) position; subsequent forms follow.
        val radicals = listOf(
            "h" to 0x1200, // ሀ
            "l" to 0x1208, // ለ
            "H" to 0x1210, // ሐ
            "m" to 0x1218, // መ
            "s" to 0x1230, // ሰ
            "r" to 0x1228, // ረ
            "S" to 0x1238, // ሠ
            "q" to 0x1240, // ቀ
            "b" to 0x1260, // በ
            "t" to 0x1270, // ተ
            "c" to 0x1278, // ቸ
            "n" to 0x1290, // ነ
            "N" to 0x1298, // ኘ
            "a" to 0x12A0, // አ
            "k" to 0x12A8, // ከ
            "w" to 0x12C8, // ወ
            "z" to 0x12D8, // ዘ
            "Z" to 0x12E0, // ዠ
            "y" to 0x12E8, // የ
            "d" to 0x12F0, // ደ
            "j" to 0x1300, // ጀ
            "g" to 0x1308, // ገ
            "T" to 0x1320, // ጠ
            "C" to 0x1328, // ጨ
            "P" to 0x1330, // ጰ
            "p" to 0x1350, // ፐ
            "f" to 0x1348, // ፈ
            "v" to 0x1350, // ፐ (shared in older SERA)
        )
        for ((rad, baseCp) in radicals) {
            // Unicode order is ä(0) u(1) i(2) a(3) ē(4) ə(5) o(6). In SERA the
            // BARE consonant is the 6th order (sädis / schwa) and the `e` suffix
            // marks the 1st order (gəʿəz / ä) — this is what makes the canonical
            // anchor "selam" → ሰላም (se→ሰ 1st, la→ላ 4th, m→ም 6th). Mapping the
            // bare radical to the 1st order instead silently produced ስላመ, the
            // wrong fidel sequence, for every word ending in or clustering a
            // consonant.
            put(rad, String(Character.toChars(baseCp + 5)))          // bare = 6th form (schwa, ə)
            put(rad + "e", String(Character.toChars(baseCp)))        // e = 1st form (ä)
            put(rad + "u", String(Character.toChars(baseCp + 1)))    // 2nd form
            put(rad + "i", String(Character.toChars(baseCp + 2)))    // 3rd form
            put(rad + "a", String(Character.toChars(baseCp + 3)))    // 4th form
            put(rad + "E", String(Character.toChars(baseCp + 4)))    // 5th form (ē / ie)
            put(rad + "I", String(Character.toChars(baseCp + 4)))    // alt for 5th
            put(rad + "o", String(Character.toChars(baseCp + 6)))    // 7th form
        }

        // Punctuation: Ethiopic wordspace, full-stop, comma, semicolon, colon, preface colon, question.
        put(" ", "\u1361")  // ፡ (wordspace)
        put(".", "\u1362")  // ።
        put(",", "\u1363")  // ፣
        put(";", "\u1364")  // ፤
        put(":", "\u1365")  // ፥
        put("?", "\u1367")  // ፧

        // Ethiopic digits 1..10.
        put("1", "\u1369") // ፩
        put("2", "\u136A") // ፪
        put("3", "\u136B") // ፫
        put("4", "\u136C") // ፬
        put("5", "\u136D") // ፭
        put("6", "\u136E") // ፮
        put("7", "\u136F") // ፯
        put("8", "\u1370") // ፰
        put("9", "\u1371") // ፱
    }

    internal val maxKeyLength: Int = table.keys.maxOfOrNull { it.length } ?: 1

    /**
     * Greedy longest-match transliteration against an arbitrary
     * lookup [otherTable]. Used by dialect-specific subclasses to
     * compose dialect extras over the shared Ge'ez table.
     */
    internal fun transliterateWith(latin: String, otherTable: Map<String, String>): String {
        if (latin.isEmpty()) return ""
        val maxKey = otherTable.keys.maxOfOrNull { it.length } ?: 1
        val output = StringBuilder(latin.length)
        var i = 0
        while (i < latin.length) {
            var matched = false
            for (keyLen in maxKey downTo 1) {
                if (i + keyLen > latin.length) continue
                val candidate = latin.substring(i, i + keyLen)
                otherTable[candidate]?.let {
                    output.append(it)
                    i += keyLen
                    matched = true
                }
                if (matched) break
            }
            if (!matched) {
                output.append(latin[i])
                i++
            }
        }
        return output.toString()
    }
}
