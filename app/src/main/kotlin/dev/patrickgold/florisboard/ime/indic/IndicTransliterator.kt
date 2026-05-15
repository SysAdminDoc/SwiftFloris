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

        /** ITRANS → Bengali (U+0980 block; Bengali / Assamese). */
        val ItransToBengali: IndicScriptTable = IndicScriptTable(
            sourceScheme = "ITRANS",
            targetScript = "Bengali",
            mappings = buildIndicMappings(
                vowels = mapOf(
                    "a" to "অ", "aa" to "আ", "A" to "আ",
                    "i" to "ই", "ii" to "ঈ", "I" to "ঈ",
                    "u" to "উ", "uu" to "ঊ", "U" to "ঊ",
                    "e" to "এ", "ai" to "ঐ",
                    "o" to "ও", "au" to "ঔ",
                ),
                consonants = mapOf(
                    "k" to "ক", "kh" to "খ", "g" to "গ", "gh" to "ঘ",
                    "ch" to "চ", "Ch" to "ছ", "j" to "জ", "jh" to "ঝ",
                    "T" to "ট", "Th" to "ঠ", "D" to "ড", "Dh" to "ঢ", "N" to "ণ",
                    "t" to "ত", "th" to "থ", "d" to "দ", "dh" to "ধ", "n" to "ন",
                    "p" to "প", "ph" to "ফ", "b" to "ব", "bh" to "ভ", "m" to "ম",
                    "y" to "য", "r" to "র", "l" to "ল",
                    "sh" to "শ", "Sh" to "ষ", "s" to "স", "h" to "হ",
                ),
                digits = "০১২৩৪৫৬৭৮৯",
                anusvara = "ং", visarga = "ঃ",
            ),
        )

        /** ITRANS → Tamil (U+0B80 block). Tamil has no aspirated stops. */
        val ItransToTamil: IndicScriptTable = IndicScriptTable(
            sourceScheme = "ITRANS",
            targetScript = "Tamil",
            mappings = buildIndicMappings(
                vowels = mapOf(
                    "a" to "அ", "aa" to "ஆ", "A" to "ஆ",
                    "i" to "இ", "ii" to "ஈ", "I" to "ஈ",
                    "u" to "உ", "uu" to "ஊ", "U" to "ஊ",
                    "e" to "எ", "ee" to "ஏ",
                    "ai" to "ஐ", "o" to "ஒ", "oo" to "ஓ", "au" to "ஔ",
                ),
                consonants = mapOf(
                    "k" to "க", "ng" to "ங",
                    "ch" to "ச", "nj" to "ஞ",
                    "T" to "ட", "N" to "ண",
                    "t" to "த", "n" to "ந",
                    "p" to "ப", "m" to "ம",
                    "y" to "ய", "r" to "ர", "l" to "ல", "v" to "வ",
                    "zh" to "ழ", "L" to "ள",
                    "R" to "ற", "n2" to "ன",
                    "j" to "ஜ", "sh" to "ஷ", "s" to "ஸ", "h" to "ஹ",
                ),
                digits = "௦௧௨௩௪௫௬௭௮௯",
                anusvara = "ஃ", visarga = "ஃ",
            ),
        )

        /** ITRANS → Telugu (U+0C00 block). */
        val ItransToTelugu: IndicScriptTable = IndicScriptTable(
            sourceScheme = "ITRANS",
            targetScript = "Telugu",
            mappings = buildIndicMappings(
                vowels = mapOf(
                    "a" to "అ", "aa" to "ఆ", "A" to "ఆ",
                    "i" to "ఇ", "ii" to "ఈ", "I" to "ఈ",
                    "u" to "ఉ", "uu" to "ఊ", "U" to "ఊ",
                    "e" to "ఎ", "ee" to "ఏ", "ai" to "ఐ",
                    "o" to "ఒ", "oo" to "ఓ", "au" to "ఔ",
                ),
                consonants = mapOf(
                    "k" to "క", "kh" to "ఖ", "g" to "గ", "gh" to "ఘ",
                    "ch" to "చ", "Ch" to "ఛ", "j" to "జ", "jh" to "ఝ",
                    "T" to "ట", "Th" to "ఠ", "D" to "డ", "Dh" to "ఢ", "N" to "ణ",
                    "t" to "త", "th" to "థ", "d" to "ద", "dh" to "ధ", "n" to "న",
                    "p" to "ప", "ph" to "ఫ", "b" to "బ", "bh" to "భ", "m" to "మ",
                    "y" to "య", "r" to "ర", "l" to "ల", "v" to "వ",
                    "sh" to "శ", "Sh" to "ష", "s" to "స", "h" to "హ",
                ),
                digits = "౦౧౨౩౪౫౬౭౮౯",
                anusvara = "ం", visarga = "ః",
            ),
        )

        /** ITRANS → Gujarati (U+0A80 block). */
        val ItransToGujarati: IndicScriptTable = IndicScriptTable(
            sourceScheme = "ITRANS",
            targetScript = "Gujarati",
            mappings = buildIndicMappings(
                vowels = mapOf(
                    "a" to "અ", "aa" to "આ", "A" to "આ",
                    "i" to "ઇ", "ii" to "ઈ", "I" to "ઈ",
                    "u" to "ઉ", "uu" to "ઊ", "U" to "ઊ",
                    "e" to "એ", "ai" to "ઐ",
                    "o" to "ઓ", "au" to "ઔ",
                ),
                consonants = mapOf(
                    "k" to "ક", "kh" to "ખ", "g" to "ગ", "gh" to "ઘ",
                    "ch" to "ચ", "Ch" to "છ", "j" to "જ", "jh" to "ઝ",
                    "T" to "ટ", "Th" to "ઠ", "D" to "ડ", "Dh" to "ઢ", "N" to "ણ",
                    "t" to "ત", "th" to "થ", "d" to "દ", "dh" to "ધ", "n" to "ન",
                    "p" to "પ", "ph" to "ફ", "b" to "બ", "bh" to "ભ", "m" to "મ",
                    "y" to "ય", "r" to "ર", "l" to "લ", "v" to "વ",
                    "sh" to "શ", "Sh" to "ષ", "s" to "સ", "h" to "હ",
                ),
                digits = "૦૧૨૩૪૫૬૭૮૯",
                anusvara = "ં", visarga = "ઃ",
            ),
        )

        /** ITRANS → Gurmukhi (U+0A00 block; Punjabi script). */
        val ItransToGurmukhi: IndicScriptTable = IndicScriptTable(
            sourceScheme = "ITRANS",
            targetScript = "Gurmukhi",
            mappings = buildIndicMappings(
                vowels = mapOf(
                    "a" to "ਅ", "aa" to "ਆ", "A" to "ਆ",
                    "i" to "ਇ", "ii" to "ਈ", "I" to "ਈ",
                    "u" to "ਉ", "uu" to "ਊ", "U" to "ਊ",
                    "e" to "ਏ", "ai" to "ਐ",
                    "o" to "ਓ", "au" to "ਔ",
                ),
                consonants = mapOf(
                    "k" to "ਕ", "kh" to "ਖ", "g" to "ਗ", "gh" to "ਘ",
                    "ch" to "ਚ", "Ch" to "ਛ", "j" to "ਜ", "jh" to "ਝ",
                    "T" to "ਟ", "Th" to "ਠ", "D" to "ਡ", "Dh" to "ਢ", "N" to "ਣ",
                    "t" to "ਤ", "th" to "ਥ", "d" to "ਦ", "dh" to "ਧ", "n" to "ਨ",
                    "p" to "ਪ", "ph" to "ਫ", "b" to "ਬ", "bh" to "ਭ", "m" to "ਮ",
                    "y" to "ਯ", "r" to "ਰ", "l" to "ਲ", "v" to "ਵ",
                    "sh" to "ਸ਼", "s" to "ਸ", "h" to "ਹ",
                ),
                digits = "੦੧੨੩੪੫੬੭੮੯",
                anusvara = "ਂ", visarga = "ਃ",
            ),
        )

        /** ITRANS → Malayalam (U+0D00 block). */
        val ItransToMalayalam: IndicScriptTable = IndicScriptTable(
            sourceScheme = "ITRANS",
            targetScript = "Malayalam",
            mappings = buildIndicMappings(
                vowels = mapOf(
                    "a" to "അ", "aa" to "ആ", "A" to "ആ",
                    "i" to "ഇ", "ii" to "ഈ", "I" to "ഈ",
                    "u" to "ഉ", "uu" to "ഊ", "U" to "ഊ",
                    "e" to "എ", "ee" to "ഏ",
                    "ai" to "ഐ", "o" to "ഒ", "oo" to "ഓ", "au" to "ഔ",
                ),
                consonants = mapOf(
                    "k" to "ക", "kh" to "ഖ", "g" to "ഗ", "gh" to "ഘ",
                    "ch" to "ച", "Ch" to "ഛ", "j" to "ജ", "jh" to "ഝ",
                    "T" to "ട", "Th" to "ഠ", "D" to "ഡ", "Dh" to "ഢ", "N" to "ണ",
                    "t" to "ത", "th" to "ഥ", "d" to "ദ", "dh" to "ധ", "n" to "ന",
                    "p" to "പ", "ph" to "ഫ", "b" to "ബ", "bh" to "ഭ", "m" to "മ",
                    "y" to "യ", "r" to "ര", "l" to "ല", "v" to "വ", "L" to "ള",
                    "sh" to "ശ", "Sh" to "ഷ", "s" to "സ", "h" to "ഹ",
                ),
                digits = "൦൧൨൩൪൫൬൭൮൯",
                anusvara = "ം", visarga = "ഃ",
            ),
        )

        /** ITRANS → Odia (U+0B00 block; formerly known as Oriya). */
        val ItransToOdia: IndicScriptTable = IndicScriptTable(
            sourceScheme = "ITRANS",
            targetScript = "Odia",
            mappings = buildIndicMappings(
                vowels = mapOf(
                    "a" to "ଅ", "aa" to "ଆ", "A" to "ଆ",
                    "i" to "ଇ", "ii" to "ଈ", "I" to "ଈ",
                    "u" to "ଉ", "uu" to "ଊ", "U" to "ଊ",
                    "e" to "ଏ", "ai" to "ଐ",
                    "o" to "ଓ", "au" to "ଔ",
                ),
                consonants = mapOf(
                    "k" to "କ", "kh" to "ଖ", "g" to "ଗ", "gh" to "ଘ",
                    "ch" to "ଚ", "Ch" to "ଛ", "j" to "ଜ", "jh" to "ଝ",
                    "T" to "ଟ", "Th" to "ଠ", "D" to "ଡ", "Dh" to "ଢ", "N" to "ଣ",
                    "t" to "ତ", "th" to "ଥ", "d" to "ଦ", "dh" to "ଧ", "n" to "ନ",
                    "p" to "ପ", "ph" to "ଫ", "b" to "ବ", "bh" to "ଭ", "m" to "ମ",
                    "y" to "ଯ", "r" to "ର", "l" to "ଲ", "v" to "ଵ",
                    "sh" to "ଶ", "Sh" to "ଷ", "s" to "ସ", "h" to "ହ",
                ),
                digits = "୦୧୨୩୪୫୬୭୮୯",
                anusvara = "ଂ", visarga = "ଃ",
            ),
        )

        /** ITRANS → Sinhala (U+0D80 block). */
        val ItransToSinhala: IndicScriptTable = IndicScriptTable(
            sourceScheme = "ITRANS",
            targetScript = "Sinhala",
            mappings = buildIndicMappings(
                vowels = mapOf(
                    "a" to "අ", "aa" to "ආ", "A" to "ආ",
                    "i" to "ඉ", "ii" to "ඊ", "I" to "ඊ",
                    "u" to "උ", "uu" to "ඌ", "U" to "ඌ",
                    "e" to "එ", "ee" to "ඒ",
                    "ai" to "ඓ", "o" to "ඔ", "oo" to "ඕ", "au" to "ඖ",
                ),
                consonants = mapOf(
                    "k" to "ක", "kh" to "ඛ", "g" to "ග", "gh" to "ඝ",
                    "ch" to "ච", "Ch" to "ඡ", "j" to "ජ", "jh" to "ඣ",
                    "T" to "ට", "Th" to "ඨ", "D" to "ඩ", "Dh" to "ඪ", "N" to "ණ",
                    "t" to "ත", "th" to "ථ", "d" to "ද", "dh" to "ධ", "n" to "න",
                    "p" to "ප", "ph" to "ඵ", "b" to "බ", "bh" to "භ", "m" to "ම",
                    "y" to "ය", "r" to "ර", "l" to "ල", "v" to "ව",
                    "sh" to "ශ", "Sh" to "ෂ", "s" to "ස", "h" to "හ",
                ),
                // Sinhala uses Western Arabic numerals; no script-native digits in current Unicode.
                digits = "0123456789",
                anusvara = "ං", visarga = "ඃ",
            ),
        )

        /**
         * ITRANS → Burmese / Myanmar (U+1000 block).
         * Burmese is a Brahmic-derived script (Tibeto-Burman) — same
         * vowel + consonant + digit shape as the Indic tables, so it
         * slots cleanly into [buildIndicMappings]. Aspirated stops use
         * the canonical Myanmar aspirate-marker form. Digits use the
         * native Myanmar digit code points U+1040..U+1049.
         */
        val ItransToBurmese: IndicScriptTable = IndicScriptTable(
            sourceScheme = "ITRANS",
            targetScript = "Burmese",
            mappings = buildIndicMappings(
                vowels = mapOf(
                    "a" to "အ", "aa" to "အာ", "A" to "အာ",
                    "i" to "ဣ", "ii" to "ဤ", "I" to "ဤ",
                    "u" to "ဥ", "uu" to "ဦ", "U" to "ဦ",
                    "e" to "ဧ", "o" to "ဩ", "au" to "ဪ",
                ),
                consonants = mapOf(
                    "k" to "က", "kh" to "ခ", "g" to "ဂ", "gh" to "ဃ",
                    "ch" to "စ", "Ch" to "ဆ", "j" to "ဇ", "jh" to "ဈ",
                    "T" to "ဋ", "Th" to "ဌ", "D" to "ဍ", "Dh" to "ဎ", "N" to "ဏ",
                    "t" to "တ", "th" to "ထ", "d" to "ဒ", "dh" to "ဓ", "n" to "န",
                    "p" to "ပ", "ph" to "ဖ", "b" to "ဗ", "bh" to "ဘ", "m" to "မ",
                    "y" to "ယ", "r" to "ရ", "l" to "လ", "v" to "ဝ",
                    "sh" to "သျှ", "s" to "သ", "h" to "ဟ",
                ),
                digits = "၀၁၂၃၄၅၆၇၈၉",
                anusvara = "ံ", visarga = "း",
            ),
        )

        /**
         * ITRANS → Lao (U+0E80 block).
         * Lao is a sister script to Thai; it shares the Brahmic
         * consonant + vowel skeleton, so [buildIndicMappings] applies
         * with Lao-specific glyphs. Digits use Lao-native code points
         * U+0ED0..U+0ED9. Lao traditionally has no anusvara/visarga
         * separately marked, so we map both `M`/`H` to the Lao niggahita
         * (U+0ECD) which is the closest visual + phonetic analogue.
         */
        val ItransToLao: IndicScriptTable = IndicScriptTable(
            sourceScheme = "ITRANS",
            targetScript = "Lao",
            mappings = buildIndicMappings(
                vowels = mapOf(
                    "a" to "ອ", "aa" to "ອາ", "A" to "ອາ",
                    "i" to "ອິ", "ii" to "ອີ", "I" to "ອີ",
                    "u" to "ອຸ", "uu" to "ອູ", "U" to "ອູ",
                    "e" to "ເອ", "o" to "ໂອ", "au" to "ເອົາ",
                ),
                consonants = mapOf(
                    "k" to "ກ", "kh" to "ຂ", "g" to "ຄ",
                    "ng" to "ງ",
                    "ch" to "ຈ", "Ch" to "ສ", "j" to "ຊ",
                    "T" to "ດ", "Th" to "ຖ", "D" to "ທ",
                    "t" to "ຕ", "th" to "ຖ", "d" to "ດ", "n" to "ນ",
                    "p" to "ປ", "ph" to "ຜ", "b" to "ບ", "bh" to "ພ", "m" to "ມ",
                    "y" to "ຍ", "r" to "ຣ", "l" to "ລ", "v" to "ວ",
                    "s" to "ສ", "h" to "ຫ",
                ),
                digits = "໐໑໒໓໔໕໖໗໘໙",
                anusvara = "ໍ", visarga = "ໍ",
            ),
        )

        /**
         * ITRANS → Tibetan / Bod-yig (U+0F00 block).
         * Tibetan is Brahmic-derived but uses syllable-final delimiters
         * (the tsheg `་`) in real text — that detail is handled by the
         * caller, not the table. The table itself ships the base
         * consonant + vowel inventory and the native Tibetan digit
         * code points U+0F20..U+0F29.
         */
        val ItransToTibetan: IndicScriptTable = IndicScriptTable(
            sourceScheme = "ITRANS",
            targetScript = "Tibetan",
            mappings = buildIndicMappings(
                vowels = mapOf(
                    "a" to "ཨ", "aa" to "ཨཱ", "A" to "ཨཱ",
                    "i" to "ཨི", "ii" to "ཨཱི", "I" to "ཨཱི",
                    "u" to "ཨུ", "uu" to "ཨཱུ", "U" to "ཨཱུ",
                    "e" to "ཨེ", "o" to "ཨོ",
                ),
                consonants = mapOf(
                    "k" to "ཀ", "kh" to "ཁ", "g" to "ག", "gh" to "གྷ",
                    "ng" to "ང",
                    "ch" to "ཅ", "Ch" to "ཆ", "j" to "ཇ", "jh" to "ཛྷ",
                    "T" to "ཊ", "Th" to "ཋ", "D" to "ཌ", "Dh" to "ཌྷ", "N" to "ཎ",
                    "t" to "ཏ", "th" to "ཐ", "d" to "ད", "dh" to "དྷ", "n" to "ན",
                    "p" to "པ", "ph" to "ཕ", "b" to "བ", "bh" to "བྷ", "m" to "མ",
                    "y" to "ཡ", "r" to "ར", "l" to "ལ", "v" to "ཝ",
                    "sh" to "ཤ", "Sh" to "ཥ", "s" to "ས", "h" to "ཧ",
                ),
                digits = "༠༡༢༣༤༥༦༧༨༩",
                anusvara = "ཾ", visarga = "ཿ",
            ),
        )

        /**
         * ITRANS → Khmer / Cambodian (U+1780 block).
         * Khmer is Brahmic-derived (Pali / Sanskrit liturgical pedigree)
         * so the [buildIndicMappings] shape carries over. Native Khmer
         * digits live at U+17E0..U+17E9; the visarga slot maps to the
         * Khmer reah-muk (U+17C7). Khmer has no native anusvara, so
         * `M` maps to the niggahita (U+17C6).
         */
        val ItransToKhmer: IndicScriptTable = IndicScriptTable(
            sourceScheme = "ITRANS",
            targetScript = "Khmer",
            mappings = buildIndicMappings(
                vowels = mapOf(
                    "a" to "អ", "aa" to "អា", "A" to "អា",
                    "i" to "ឥ", "ii" to "ឦ", "I" to "ឦ",
                    "u" to "ឧ", "uu" to "ឩ", "U" to "ឩ",
                    "e" to "ឯ", "o" to "ឱ", "au" to "ឳ",
                ),
                consonants = mapOf(
                    "k" to "ក", "kh" to "ខ", "g" to "គ", "gh" to "ឃ",
                    "ng" to "ង",
                    "ch" to "ច", "Ch" to "ឆ", "j" to "ជ", "jh" to "ឈ",
                    "T" to "ដ", "Th" to "ឋ", "D" to "ឌ", "Dh" to "ឍ", "N" to "ណ",
                    "t" to "ត", "th" to "ថ", "d" to "ទ", "dh" to "ធ", "n" to "ន",
                    "p" to "ប", "ph" to "ផ", "b" to "ព", "bh" to "ភ", "m" to "ម",
                    "y" to "យ", "r" to "រ", "l" to "ល", "v" to "វ",
                    "sh" to "ឝ", "Sh" to "ឞ", "s" to "ស", "h" to "ហ",
                ),
                digits = "០១២៣៤៥៦៧៨៩",
                anusvara = "ំ", visarga = "ះ",
            ),
        )

        /**
         * ITRANS → Thai (U+0E00 block).
         * Thai is a sister-script to Lao with its own Brahmic-derived
         * consonant set and tone-marker conventions (those handled at
         * the IME layer, not the transliterator). Native Thai digits
         * U+0E50..U+0E59. Thai lacks native anusvara/visarga code
         * points distinct from existing marks, so both map to the
         * Thai niggahita (U+0E4D).
         */
        val ItransToThai: IndicScriptTable = IndicScriptTable(
            sourceScheme = "ITRANS",
            targetScript = "Thai",
            mappings = buildIndicMappings(
                vowels = mapOf(
                    "a" to "อ", "aa" to "อา", "A" to "อา",
                    "i" to "อิ", "ii" to "อี", "I" to "อี",
                    "u" to "อุ", "uu" to "อู", "U" to "อู",
                    "e" to "เอ", "o" to "โอ", "au" to "เอา",
                ),
                consonants = mapOf(
                    "k" to "ก", "kh" to "ข", "g" to "ค", "gh" to "ฆ",
                    "ng" to "ง",
                    "ch" to "จ", "Ch" to "ฉ", "j" to "ช", "jh" to "ฌ",
                    "T" to "ฎ", "Th" to "ฏ", "D" to "ฑ", "Dh" to "ฒ", "N" to "ณ",
                    "t" to "ต", "th" to "ถ", "d" to "ด", "dh" to "ธ", "n" to "น",
                    "p" to "ป", "ph" to "ผ", "b" to "บ", "bh" to "ภ", "m" to "ม",
                    "y" to "ย", "r" to "ร", "l" to "ล", "v" to "ว",
                    "sh" to "ศ", "Sh" to "ษ", "s" to "ส", "h" to "ห",
                ),
                digits = "๐๑๒๓๔๕๖๗๘๙",
                anusvara = "ํ", visarga = "ํ",
            ),
        )

        /**
         * Latin → Mongolian (U+1800 block).
         * Mongolian is a Brahmic-derived script written **vertically**
         * historically, but the Unicode block carries the consonant +
         * vowel + digit inventory used for both vertical Mongolian and
         * Hudum Mongolian Cyrillic transliteration. Digits U+1810..U+1819.
         * The script does not carry anusvara / visarga concepts — slots
         * map to the Mongolian "Sibe" delimiter (U+1806) which is the
         * closest punctuation analogue.
         */
        val LatinToMongolian: IndicScriptTable = IndicScriptTable(
            sourceScheme = "Latin",
            targetScript = "Mongolian",
            mappings = buildIndicMappings(
                vowels = mapOf(
                    "a" to "ᠠ", "e" to "ᠡ",
                    "i" to "ᠢ", "o" to "ᠣ", "u" to "ᠤ",
                ),
                consonants = mapOf(
                    "k" to "ᠺ", "g" to "ᠭ", "ng" to "ᠩ",
                    "ch" to "ᠴ", "j" to "ᠵ",
                    "t" to "ᠲ", "d" to "ᠳ", "n" to "ᠨ",
                    "p" to "ᠫ", "b" to "ᠪ", "m" to "ᠮ",
                    "r" to "ᠷ", "l" to "ᠯ", "v" to "ᠸ", "y" to "ᠶ",
                    "s" to "ᠰ", "h" to "ᠬ",
                ),
                digits = "᠐᠑᠒᠓᠔᠕᠖᠗᠘᠙",
                anusvara = "᠆", visarga = "᠆",
            ),
        )

        /**
         * ITRANS → Javanese (U+A980 block).
         * Javanese is a Brahmic-derived Indonesian / Sundanese family
         * script with the standard vowel + consonant skeleton. Digits
         * U+A9D0..U+A9D9. Anusvara maps to Javanese "Cecak" (U+A981);
         * visarga maps to "Wignyan" (U+A983).
         */
        val ItransToJavanese: IndicScriptTable = IndicScriptTable(
            sourceScheme = "ITRANS",
            targetScript = "Javanese",
            mappings = buildIndicMappings(
                vowels = mapOf(
                    "a" to "ꦄ", "aa" to "ꦄꦴ", "A" to "ꦄꦴ",
                    "i" to "ꦆ", "ii" to "ꦇ", "I" to "ꦇ",
                    "u" to "ꦈ", "uu" to "ꦈꦴ", "U" to "ꦈꦴ",
                    "e" to "ꦌ", "o" to "ꦎ",
                ),
                consonants = mapOf(
                    "k" to "ꦏ", "kh" to "ꦑ", "g" to "ꦒ", "gh" to "ꦓ",
                    "ng" to "ꦔ",
                    "ch" to "ꦕ", "j" to "ꦗ",
                    "T" to "ꦛ", "Th" to "ꦜ", "D" to "ꦝ", "Dh" to "ꦞ", "N" to "ꦟ",
                    "t" to "ꦠ", "th" to "ꦡ", "d" to "ꦢ", "dh" to "ꦣ", "n" to "ꦤ",
                    "p" to "ꦥ", "ph" to "ꦦ", "b" to "ꦧ", "bh" to "ꦨ", "m" to "ꦩ",
                    "y" to "ꦪ", "r" to "ꦫ", "l" to "ꦭ", "v" to "ꦮ",
                    "sh" to "ꦯ", "s" to "ꦱ", "h" to "ꦲ",
                ),
                digits = "꧐꧑꧒꧓꧔꧕꧖꧗꧘꧙",
                anusvara = "ꦁ", visarga = "ꦃ",
            ),
        )

        /**
         * ITRANS → Sundanese (U+1B80 block).
         * Sundanese is a Western-Javanese / Indonesian Brahmic script
         * close to Javanese but with its own native code points + digits
         * U+1BB0..U+1BB9. Both anusvara + visarga collapse to the
         * Sundanese pamaaeh (U+1BAA), which is the script's pasangan
         * vowel-killer mark — closest functional analogue.
         */
        val ItransToSundanese: IndicScriptTable = IndicScriptTable(
            sourceScheme = "ITRANS",
            targetScript = "Sundanese",
            mappings = buildIndicMappings(
                vowels = mapOf(
                    "a" to "ᮃ", "i" to "ᮄ",
                    "u" to "ᮅ", "e" to "ᮆ", "o" to "ᮇ",
                ),
                consonants = mapOf(
                    "k" to "ᮊ", "g" to "ᮌ", "ng" to "ᮍ",
                    "ch" to "ᮎ", "j" to "ᮏ",
                    "T" to "ᮒ", "D" to "ᮓ", "N" to "ᮔ",
                    "t" to "ᮒ", "d" to "ᮓ", "n" to "ᮔ",
                    "p" to "ᮕ", "b" to "ᮘ", "m" to "ᮙ",
                    "y" to "ᮚ", "r" to "ᮛ", "l" to "ᮜ", "v" to "ᮝ",
                    "s" to "ᮞ", "h" to "ᮠ",
                ),
                digits = "᮰᮱᮲᮳᮴᮵᮶᮷᮸᮹",
                anusvara = "᮪", visarga = "᮪",
            ),
        )

        /** ITRANS → Kannada (U+0C80 block). */
        val ItransToKannada: IndicScriptTable = IndicScriptTable(
            sourceScheme = "ITRANS",
            targetScript = "Kannada",
            mappings = buildIndicMappings(
                vowels = mapOf(
                    "a" to "ಅ", "aa" to "ಆ", "A" to "ಆ",
                    "i" to "ಇ", "ii" to "ಈ", "I" to "ಈ",
                    "u" to "ಉ", "uu" to "ಊ", "U" to "ಊ",
                    "e" to "ಎ", "ee" to "ಏ", "ai" to "ಐ",
                    "o" to "ಒ", "oo" to "ಓ", "au" to "ಔ",
                ),
                consonants = mapOf(
                    "k" to "ಕ", "kh" to "ಖ", "g" to "ಗ", "gh" to "ಘ",
                    "ch" to "ಚ", "Ch" to "ಛ", "j" to "ಜ", "jh" to "ಝ",
                    "T" to "ಟ", "Th" to "ಠ", "D" to "ಡ", "Dh" to "ಢ", "N" to "ಣ",
                    "t" to "ತ", "th" to "ಥ", "d" to "ದ", "dh" to "ಧ", "n" to "ನ",
                    "p" to "ಪ", "ph" to "ಫ", "b" to "ಬ", "bh" to "ಭ", "m" to "ಮ",
                    "y" to "ಯ", "r" to "ರ", "l" to "ಲ", "v" to "ವ",
                    "sh" to "ಶ", "Sh" to "ಷ", "s" to "ಸ", "h" to "ಹ",
                ),
                digits = "೦೧೨೩೪೫೬೭೮೯",
                anusvara = "ಂ", visarga = "ಃ",
            ),
        )

        /** Common Indic table builder — keeps each per-script table
         *  concise + audit-able. */
        private fun buildIndicMappings(
            vowels: Map<String, String>,
            consonants: Map<String, String>,
            digits: String,
            anusvara: String,
            visarga: String,
        ): Map<String, String> {
            val map = LinkedHashMap<String, String>(vowels.size + consonants.size + 12)
            map.putAll(vowels)
            map.putAll(consonants)
            for ((i, char) in digits.withIndex()) {
                if (i > 9) break
                map[i.toString()] = char.toString()
            }
            map["M"] = anusvara
            map["H"] = visarga
            map["|"] = "।"
            map["||"] = "॥"
            return map
        }
    }
}
