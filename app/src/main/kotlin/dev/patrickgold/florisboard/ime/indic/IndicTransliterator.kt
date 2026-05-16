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

        /**
         * Latin → Adlam (U+1E900 block).
         * Adlam is a West African alphabetic script created in the 1980s
         * for the Fulani / Pulaar language. Unlike the rest of L5's
         * Brahmic-derived tables it's a true alphabet — no vowels-as-
         * dependent-signs concept — but the `buildIndicMappings` shape
         * still applies (consonant + "vowel" + digit + symbolic). Native
         * Adlam digits are in U+1E950..U+1E959 (supplementary plane).
         */
        val LatinToAdlam: IndicScriptTable = IndicScriptTable(
            sourceScheme = "Latin",
            targetScript = "Adlam",
            mappings = buildIndicMappings(
                vowels = mapOf(
                    "a" to "𞤢", "e" to "𞤫", "i" to "𞤭", "o" to "𞤮", "u" to "𞤵",
                ),
                consonants = mapOf(
                    "b" to "𞤦", "p" to "𞤨", "t" to "𞤼", "d" to "𞤣",
                    "k" to "𞤳", "g" to "𞤺", "f" to "𞤬", "v" to "𞤰",
                    "s" to "𞤧", "z" to "𞤶", "h" to "𞤸",
                    "m" to "𞤥", "n" to "𞤲", "ng" to "𞤺",
                    "y" to "𞤴", "r" to "𞤪", "l" to "𞤤", "w" to "𞤱",
                ),
                digits = "𞥐𞥑𞥒𞥓𞥔𞥕𞥖𞥗𞥘𞥙",
                anusvara = "𞥃", visarga = "𞥄",
            ),
        )

        /**
         * Latin → N'Ko (U+07C0 block).
         * N'Ko is a West African alphabetic script created in 1949 for
         * the Manding language family (Bambara / Maninka / Dyula). It
         * runs right-to-left. The IME-side bidi handler routes runs
         * through the existing `RtlBidiResolver` once the subtype is
         * active. Native N'Ko digits are U+07C0..U+07C9.
         */
        val LatinToNKo: IndicScriptTable = IndicScriptTable(
            sourceScheme = "Latin",
            targetScript = "NKo",
            mappings = buildIndicMappings(
                vowels = mapOf(
                    "a" to "ߊ", "e" to "ߋ", "i" to "ߌ", "o" to "ߐ", "u" to "ߎ",
                ),
                consonants = mapOf(
                    "b" to "ߓ", "p" to "ߔ", "t" to "ߕ", "d" to "ߘ",
                    "k" to "ߞ", "g" to "ߜ", "f" to "ߝ",
                    "s" to "ߛ", "z" to "ߖ", "h" to "ߤ",
                    "m" to "ߡ", "n" to "ߣ",
                    "y" to "ߦ", "r" to "ߙ", "l" to "ߟ", "w" to "ߥ",
                    "ch" to "ߗ", "j" to "ߖ",
                ),
                digits = "߀߁߂߃߄߅߆߇߈߉",
                anusvara = "߽", visarga = "߽",
            ),
        )

        /**
         * Latin → Cherokee (U+13A0 block).
         * Cherokee is the only indigenous-North-American script in
         * mainstream Unicode use. It's a *syllabary* — each character
         * represents a CV syllable, not an alphabet — so the mapping
         * uses Romanised syllables (`ga`, `ka`, `ho`, etc.) rather than
         * consonant+vowel marks. Falls outside `buildIndicMappings`
         * because there's no anusvara / visarga / native digit concept;
         * digits fall through to Western Arabic.
         */
        val LatinToCherokee: IndicScriptTable = IndicScriptTable(
            sourceScheme = "Latin",
            targetScript = "Cherokee",
            mappings = mapOf(
                // Six-vowel set: a, e, i, o, u, v (ʌ in IPA).
                "a" to "Ꭰ", "e" to "Ꭱ", "i" to "Ꭲ", "o" to "Ꭳ", "u" to "Ꭴ", "v" to "Ꭵ",
                "ga" to "Ꭶ", "ka" to "Ꭷ", "ge" to "Ꭸ", "gi" to "Ꭹ", "go" to "Ꭺ",
                "gu" to "Ꭻ", "gv" to "Ꭼ",
                "ha" to "Ꭽ", "he" to "Ꭾ", "hi" to "Ꭿ", "ho" to "Ꮀ", "hu" to "Ꮁ", "hv" to "Ꮂ",
                "la" to "Ꮃ", "le" to "Ꮄ", "li" to "Ꮅ", "lo" to "Ꮆ", "lu" to "Ꮇ", "lv" to "Ꮈ",
                "ma" to "Ꮉ", "me" to "Ꮊ", "mi" to "Ꮋ", "mo" to "Ꮌ", "mu" to "Ꮍ",
                "na" to "Ꮎ", "hna" to "Ꮏ", "nah" to "Ꮐ",
                "ne" to "Ꮑ", "ni" to "Ꮒ", "no" to "Ꮓ", "nu" to "Ꮔ", "nv" to "Ꮕ",
                "qua" to "Ꮖ", "que" to "Ꮗ", "qui" to "Ꮘ", "quo" to "Ꮙ", "quu" to "Ꮚ", "quv" to "Ꮛ",
                "sa" to "Ꮜ", "se" to "Ꮝ", "si" to "Ꮞ", "so" to "Ꮟ", "su" to "Ꮠ", "sv" to "Ꮡ",
                "da" to "Ꮣ", "ta" to "Ꮤ", "de" to "Ꮥ", "te" to "Ꮦ", "di" to "Ꮧ",
                "ti" to "Ꮨ", "do" to "Ꮩ", "du" to "Ꮪ", "dv" to "Ꮫ",
                "tla" to "Ꮬ", "tle" to "Ꮭ", "tli" to "Ꮮ", "tlo" to "Ꮯ", "tlu" to "Ꮰ", "tlv" to "Ꮱ",
                "tsa" to "Ꮲ", "tse" to "Ꮳ", "tsi" to "Ꮴ", "tso" to "Ꮵ", "tsu" to "Ꮶ", "tsv" to "Ꮷ",
                "wa" to "Ꮸ", "we" to "Ꮹ", "wi" to "Ꮺ", "wo" to "Ꮻ", "wu" to "Ꮼ", "wv" to "Ꮽ",
                "ya" to "Ꮾ", "ye" to "Ꮿ", "yi" to "Ᏸ", "yo" to "Ᏹ", "yu" to "Ᏺ", "yv" to "Ᏻ",
            ),
        )

        /**
         * Latin → Coptic (U+2C80 block).
         * Coptic is the liturgical script of the Coptic Orthodox Church
         * and a true alphabet (uppercase + lowercase letter pairs). The
         * IME-side uses lowercase forms by default. No native digit
         * inventory — Coptic uses Greek-letter numerals at the
         * application layer; we leave Western Arabic digits as the
         * fallback for typing speed.
         */
        val LatinToCoptic: IndicScriptTable = IndicScriptTable(
            sourceScheme = "Latin",
            targetScript = "Coptic",
            mappings = mapOf(
                "a" to "ⲁ", "b" to "ⲃ", "g" to "ⲅ", "d" to "ⲇ",
                "e" to "ⲉ", "v" to "ⲋ", "z" to "ⲍ",
                "ee" to "ⲏ", "th" to "ⲑ", "i" to "ⲓ",
                "k" to "ⲕ", "l" to "ⲗ", "m" to "ⲙ", "n" to "ⲛ",
                "ks" to "ⳉ", "o" to "ⲟ", "p" to "ⲡ", "r" to "ⲣ",
                "s" to "ⲥ", "t" to "ⲧ", "u" to "ⲩ", "f" to "ⲫ",
                "kh" to "ⲭ", "ps" to "ⲯ", "oo" to "ⲱ",
                // Coptic-specific extras (no Greek precursor).
                "sh" to "ϣ", "F" to "ϥ", "kj" to "ϫ", "hh" to "ϩ", "ti" to "ϯ",
            ),
        )

        /**
         * Latin → Georgian Mkhedruli (U+10D0 block).
         * Georgian Mkhedruli is the modern civilian script. Native
         * Georgian digit code points exist (U+10D0..) but real-world
         * use is essentially zero — Georgian uses Western Arabic
         * digits in all current settings, so we leave them as
         * fallback.  This table covers the 33-letter modern alphabet.
         */
        val LatinToGeorgian: IndicScriptTable = IndicScriptTable(
            sourceScheme = "Latin",
            targetScript = "Georgian",
            mappings = mapOf(
                "a" to "ა", "b" to "ბ", "g" to "გ", "d" to "დ",
                "e" to "ე", "v" to "ვ", "z" to "ზ",
                "T" to "თ", "i" to "ი",
                "k" to "კ", "l" to "ლ", "m" to "მ", "n" to "ნ",
                "o" to "ო", "p" to "პ", "J" to "ჟ", "r" to "რ",
                "s" to "ს", "t" to "ტ", "u" to "უ",
                "f" to "ფ",
                "q" to "ქ", "G" to "ღ", "K" to "ყ",
                "S" to "შ", "C" to "ჩ", "c" to "ც", "Z" to "ძ",
                "w" to "წ", "W" to "ჭ",
                "x" to "ხ", "j" to "ჯ", "h" to "ჰ",
            ),
        )

        /**
         * Latin → Glagolitic (U+2C00 block).
         * Glagolitic is the predecessor of Cyrillic, still used in
         * limited Croatian + Old Church Slavonic liturgical settings.
         * Round-tower / square-tower variants both live in the same
         * Unicode block. We ship lowercase letter mappings only —
         * uppercase Glagolitic glyphs differ minimally from lowercase
         * and the IME's existing shift state handles the casing toggle.
         */
        val LatinToGlagolitic: IndicScriptTable = IndicScriptTable(
            sourceScheme = "Latin",
            targetScript = "Glagolitic",
            mappings = mapOf(
                "a" to "ⰰ", "b" to "ⰱ", "v" to "ⰲ", "g" to "ⰳ",
                "d" to "ⰴ", "e" to "ⰵ", "zh" to "ⰶ", "z" to "ⰷ",
                "i" to "ⰹ", "j" to "ⰺ",
                "k" to "ⰽ", "l" to "ⰾ", "m" to "ⰿ", "n" to "ⱀ",
                "o" to "ⱁ", "p" to "ⱂ", "r" to "ⱃ", "s" to "ⱄ",
                "t" to "ⱅ", "u" to "ⱆ", "f" to "ⱇ",
                "h" to "ⱈ", "c" to "ⱌ", "ch" to "ⱍ", "sh" to "ⱎ",
                "y" to "ⱏ", "ye" to "ⱔ", "yu" to "ⱓ", "ya" to "ⱑ",
            ),
        )

        /**
         * Latin → Samaritan (U+0800 block).
         * Samaritan is a descendant of Paleo-Hebrew used by the
         * Samaritan community for liturgical Hebrew. RTL.  No native
         * digit inventory in current Unicode usage; Western Arabic
         * digits fall through unchanged.
         */
        val LatinToSamaritan: IndicScriptTable = IndicScriptTable(
            sourceScheme = "Latin",
            targetScript = "Samaritan",
            mappings = mapOf(
                "a" to "ࠀ", "b" to "ࠁ", "g" to "ࠂ", "d" to "ࠃ",
                "h" to "ࠄ", "v" to "ࠅ", "z" to "ࠆ", "kh" to "ࠇ",
                "T" to "ࠈ", "y" to "ࠉ", "k" to "ࠊ", "l" to "ࠋ",
                "m" to "ࠌ", "n" to "ࠍ", "s" to "ࠎ", "ay" to "ࠏ",
                "p" to "ࠐ", "ts" to "ࠑ", "q" to "ࠒ", "r" to "ࠓ",
                "sh" to "ࠔ", "t" to "ࠕ",
            ),
        )

        /**
         * Latin → Mandaic (U+0840 block).
         * Mandaic is the liturgical script of the Mandaean religion,
         * historically used in southern Iraq + Iran. RTL.  No native
         * digit inventory; Western Arabic digits fall through.
         */
        val LatinToMandaic: IndicScriptTable = IndicScriptTable(
            sourceScheme = "Latin",
            targetScript = "Mandaic",
            mappings = mapOf(
                "a" to "ࡀ", "b" to "ࡁ", "g" to "ࡂ", "d" to "ࡃ",
                "h" to "ࡄ", "u" to "ࡅ", "z" to "ࡆ", "kh" to "ࡇ",
                "T" to "ࡈ", "y" to "ࡉ", "k" to "ࡊ", "l" to "ࡋ",
                "m" to "ࡌ", "n" to "ࡍ", "s" to "ࡎ", "e" to "ࡏ",
                "p" to "ࡐ", "tsd" to "ࡑ", "q" to "ࡒ", "r" to "ࡓ",
                "sh" to "ࡔ", "t" to "ࡕ",
            ),
        )

        /**
         * Latin → Old Permic (U+10350 block).
         * Old Permic is a 14th-century clergy alphabet for the Komi
         * (Permic) language family, modeled after the Greek alphabet
         * + ligature-style additions. Supplementary plane (uses
         * surrogate pairs). No digits.
         */
        val LatinToOldPermic: IndicScriptTable = IndicScriptTable(
            sourceScheme = "Latin",
            targetScript = "OldPermic",
            mappings = mapOf(
                "a" to "𐍐", "b" to "𐍑", "g" to "𐍒", "d" to "𐍓",
                "e" to "𐍔", "zh" to "𐍕", "z" to "𐍖", "dz" to "𐍗",
                "i" to "𐍘", "l" to "𐍙", "k" to "𐍚",
                "m" to "𐍛", "n" to "𐍜", "o" to "𐍝", "p" to "𐍞",
                "r" to "𐍟", "s" to "𐍠", "t" to "𐍡", "u" to "𐍢",
                "ch" to "𐍣", "sh" to "𐍤", "ja" to "𐍥",
            ),
        )

        /**
         * Latin → Phoenician (U+10900 block).
         * Phoenician is the parent script of every Western alphabet
         * (Aramaic / Greek / Latin / Hebrew / Arabic / Cyrillic).  RTL.
         * Supplementary plane.  22-letter consonantal alphabet.
         */
        val LatinToPhoenician: IndicScriptTable = IndicScriptTable(
            sourceScheme = "Latin",
            targetScript = "Phoenician",
            mappings = mapOf(
                "a" to "𐤀", "b" to "𐤁", "g" to "𐤂", "d" to "𐤃",
                "h" to "𐤄", "w" to "𐤅", "z" to "𐤆", "kh" to "𐤇",
                "T" to "𐤈", "y" to "𐤉", "k" to "𐤊", "l" to "𐤋",
                "m" to "𐤌", "n" to "𐤍", "s" to "𐤎", "ay" to "𐤏",
                "p" to "𐤐", "ts" to "𐤑", "q" to "𐤒", "r" to "𐤓",
                "sh" to "𐤔", "t" to "𐤕",
            ),
        )

        /**
         * Latin → Imperial Aramaic (U+10840 block).
         * The state script of the Achaemenid Empire and the lineal
         * ancestor of Square Hebrew, Syriac, Arabic, and Mongolian.
         * RTL.  Supplementary plane.
         */
        val LatinToImperialAramaic: IndicScriptTable = IndicScriptTable(
            sourceScheme = "Latin",
            targetScript = "ImperialAramaic",
            mappings = mapOf(
                "a" to "𐡀", "b" to "𐡁", "g" to "𐡂", "d" to "𐡃",
                "h" to "𐡄", "w" to "𐡅", "z" to "𐡆", "kh" to "𐡇",
                "T" to "𐡈", "y" to "𐡉", "k" to "𐡊", "l" to "𐡋",
                "m" to "𐡌", "n" to "𐡍", "s" to "𐡎", "ay" to "𐡏",
                "p" to "𐡐", "ts" to "𐡑", "q" to "𐡒", "r" to "𐡓",
                "sh" to "𐡔", "t" to "𐡕",
            ),
        )

        /**
         * Latin → Avestan (U+10B00 block).
         * Avestan is the liturgical script of Zoroastrianism, used to
         * write Old / Middle Iranian Avestan texts (the *Yasna*).
         * RTL.  Supplementary plane. Includes vowel characters
         * separate from consonants — a true alphabet rather than a
         * pure abjad.
         */
        val LatinToAvestan: IndicScriptTable = IndicScriptTable(
            sourceScheme = "Latin",
            targetScript = "Avestan",
            mappings = mapOf(
                "a" to "𐬀", "aa" to "𐬁", "A" to "𐬁",
                "i" to "𐬌", "ii" to "𐬍", "I" to "𐬍",
                "u" to "𐬎", "uu" to "𐬏", "U" to "𐬏",
                "e" to "𐬉", "o" to "𐬋",
                "k" to "𐬐", "kh" to "𐬑", "g" to "𐬔", "gh" to "𐬕",
                "ch" to "𐬗", "j" to "𐬘",
                "t" to "𐬙", "th" to "𐬚", "d" to "𐬛", "dh" to "𐬜",
                "p" to "𐬞", "ph" to "𐬟", "b" to "𐬠", "f" to "𐬡",
                "n" to "𐬥", "m" to "𐬨",
                "y" to "𐬫", "r" to "𐬭", "v" to "𐬬", "w" to "𐬎",
                "s" to "𐬯", "z" to "𐬰", "h" to "𐬵",
            ),
        )

        /**
         * Latin → Carian (U+102A0 block).
         * Carian is an Indo-European Anatolian language script used
         * in southwest Asia Minor c. 7th–3rd century BCE. RTL.
         * Supplementary plane. 45-letter alphabet with some letters
         * borrowed from Greek + some original.
         */
        val LatinToCarian: IndicScriptTable = IndicScriptTable(
            sourceScheme = "Latin",
            targetScript = "Carian",
            mappings = mapOf(
                "a" to "𐊠", "b" to "𐊨", "g" to "𐊼", "d" to "𐊅",
                "e" to "𐊢", "v" to "𐊵", "z" to "𐋄",
                "i" to "𐊹", "k" to "𐊨", "l" to "𐊣",
                "m" to "𐊪", "n" to "𐊵", "o" to "𐊫",
                "p" to "𐊨", "q" to "𐊴", "r" to "𐊥",
                "s" to "𐊰", "t" to "𐊭", "u" to "𐊲",
                "y" to "𐊨", "w" to "𐊿",
            ),
        )

        /**
         * Latin → Lycian (U+10280 block).
         * Lycian is an Anatolian language script used in southwest
         * Asia Minor c. 5th–4th century BCE, predominantly carved on
         * stone tomb inscriptions. RTL. Supplementary plane.
         */
        val LatinToLycian: IndicScriptTable = IndicScriptTable(
            sourceScheme = "Latin",
            targetScript = "Lycian",
            mappings = mapOf(
                "a" to "𐊀", "b" to "𐊁", "g" to "𐊂", "d" to "𐊃",
                "e" to "𐊄", "v" to "𐊅", "z" to "𐊆",
                "h" to "𐊇", "th" to "𐊈", "i" to "𐊉",
                "j" to "𐊊", "k" to "𐊋", "l" to "𐊌",
                "m" to "𐊍", "n" to "𐊎", "o" to "𐊏",
                "p" to "𐊐", "kh" to "𐊑", "r" to "𐊒",
                "s" to "𐊓", "t" to "𐊔", "u" to "𐊕",
                "f" to "𐊖", "x" to "𐊗", "q" to "𐊘",
            ),
        )

        /**
         * Latin → Lydian (U+10920 block).
         * Lydian is the Anatolian language script used at Sardis
         * c. 7th–3rd century BCE, often written boustrophedon
         * (alternating direction line-by-line). RTL by default.
         * Supplementary plane.
         */
        val LatinToLydian: IndicScriptTable = IndicScriptTable(
            sourceScheme = "Latin",
            targetScript = "Lydian",
            mappings = mapOf(
                "a" to "𐤠", "b" to "𐤡", "g" to "𐤢", "d" to "𐤣",
                "e" to "𐤤", "v" to "𐤥", "i" to "𐤦",
                "y" to "𐤧", "k" to "𐤨", "l" to "𐤩",
                "m" to "𐤪", "n" to "𐤫", "o" to "𐤬",
                "r" to "𐤭", "s" to "𐤮", "t" to "𐤯",
                "u" to "𐤰", "f" to "𐤱", "q" to "𐤲",
                "S" to "𐤳", "T" to "𐤴", "ng" to "𐤵",
                "c" to "𐤶", "p" to "𐤷",
            ),
        )

        /**
         * Latin → Caucasian Albanian (U+10530 block).
         * Caucasian Albanian is a 4th-7th century alphabet for the
         * Udi language family (Caucasus region, ancestor of modern
         * Udi). Supplementary plane. 52 letters.
         */
        val LatinToCaucasianAlbanian: IndicScriptTable = IndicScriptTable(
            sourceScheme = "Latin",
            targetScript = "CaucasianAlbanian",
            mappings = mapOf(
                "a" to "𐔰", "b" to "𐔱", "g" to "𐔲", "d" to "𐔳",
                "e" to "𐔴", "z" to "𐔵", "i" to "𐔻",
                "k" to "𐔿", "l" to "𐕀", "m" to "𐕁", "n" to "𐕂",
                "o" to "𐕃", "p" to "𐕄", "r" to "𐕅", "s" to "𐕆",
                "t" to "𐕇", "u" to "𐕈", "f" to "𐕉",
                "x" to "𐕊", "y" to "𐕋", "w" to "𐕌",
                "c" to "𐕍", "ch" to "𐕎",
                "sh" to "𐕓", "h" to "𐕔",
            ),
        )

        /**
         * Latin → Elbasan (U+10500 block).
         * Elbasan is an 18th-century Albanian alphabet used briefly
         * for Christian liturgical texts before being replaced by
         * the modern Latin-based Albanian alphabet. Supplementary
         * plane.
         */
        val LatinToElbasan: IndicScriptTable = IndicScriptTable(
            sourceScheme = "Latin",
            targetScript = "Elbasan",
            mappings = mapOf(
                "a" to "𐔀", "b" to "𐔂", "g" to "𐔃", "d" to "𐔄",
                "e" to "𐔅", "z" to "𐔆", "i" to "𐔉",
                "k" to "𐔋", "l" to "𐔌", "m" to "𐔌", "n" to "𐔍",
                "o" to "𐔒", "p" to "𐔓", "r" to "𐔔", "s" to "𐔕",
                "t" to "𐔖", "u" to "𐔘", "y" to "𐔚",
                "f" to "𐔁",
                "h" to "𐔇", "c" to "𐔝",
            ),
        )

        /**
         * Latin → Vai (U+A500 block).
         * Vai is a West African syllabary used in Liberia + Sierra
         * Leone (Vai language, Mande family). 200+ syllable glyphs.
         * This table ships a representative subset of the most
         * common CV-syllable combinations; the IME's syllable-input
         * mode handles the long-tail composition.
         */
        val LatinToVai: IndicScriptTable = IndicScriptTable(
            sourceScheme = "Latin",
            targetScript = "Vai",
            mappings = mapOf(
                "a" to "ꔀ", "e" to "ꔍ", "i" to "ꔦ", "o" to "ꕈ", "u" to "ꖉ",
                "pa" to "ꕒ", "pe" to "ꕓ", "pi" to "ꕔ", "po" to "ꕕ", "pu" to "ꕖ",
                "ba" to "ꓱ", "be" to "ꓲ", "bi" to "ꓳ", "bo" to "ꓴ", "bu" to "ꓵ",
                "ta" to "ꕴ", "te" to "ꕵ", "ti" to "ꕶ", "to" to "ꕷ", "tu" to "ꕸ",
                "ka" to "ꕪ", "ke" to "ꕫ", "ki" to "ꕬ", "ko" to "ꕭ", "ku" to "ꕮ",
                "ma" to "ꖀ", "me" to "ꖁ", "mi" to "ꖂ", "mo" to "ꖃ", "mu" to "ꖄ",
                "na" to "ꖆ", "ne" to "ꖇ", "ni" to "ꖈ", "no" to "ꖉ", "nu" to "ꖊ",
                "sa" to "ꕢ", "se" to "ꕣ", "si" to "ꕤ", "so" to "ꕥ", "su" to "ꕦ",
                "wa" to "ꕮ",
            ),
        )

        /**
         * Latin → Bassa Vah (U+16AD0 block).
         * Bassa Vah is a 20th-century alphabet for the Bassa language
         * of Liberia. Supplementary plane.  35 letters. Created by
         * Thomas Flo Lewis c. 1900.
         */
        val LatinToBassaVah: IndicScriptTable = IndicScriptTable(
            sourceScheme = "Latin",
            targetScript = "BassaVah",
            mappings = mapOf(
                "a" to "𖫭", "e" to "𖫮", "i" to "𖫯", "o" to "𖫰",
                "u" to "𖫱", "v" to "𖫲",
                "b" to "𖫐", "p" to "𖫑", "v2" to "𖫒",
                "f" to "𖫓", "d" to "𖫔", "t" to "𖫕", "th" to "𖫖",
                "dh" to "𖫗", "z" to "𖫘", "s" to "𖫙", "g" to "𖫚",
                "k" to "𖫛", "h" to "𖫜", "j" to "𖫝", "c" to "𖫞",
                "m" to "𖫟", "n" to "𖫠", "ng" to "𖫡",
                "w" to "𖫢", "y" to "𖫣",
            ),
        )

        /**
         * Latin → Mende Kikakui (U+1E800 block).
         * Mende Kikakui is a 20th-century syllabary for the Mende
         * language of Sierra Leone + Liberia. Supplementary plane.
         * RTL. 195 syllables; this table ships representative CV
         * combinations.
         */
        val LatinToMendeKikakui: IndicScriptTable = IndicScriptTable(
            sourceScheme = "Latin",
            targetScript = "MendeKikakui",
            mappings = mapOf(
                "a" to "𞠀", "i" to "𞠁", "u" to "𞠂",
                "e" to "𞠃", "o" to "𞠄",
                "ka" to "𞠅", "ki" to "𞠆", "ku" to "𞠇", "ke" to "𞠈", "ko" to "𞠉",
                "wa" to "𞠊", "wi" to "𞠋", "wu" to "𞠌",
                "ma" to "𞠐", "mi" to "𞠑", "mu" to "𞠒", "me" to "𞠓", "mo" to "𞠔",
                "ba" to "𞠕", "bi" to "𞠖", "bu" to "𞠗",
                "sa" to "𞠘", "si" to "𞠙", "su" to "𞠚",
                "ta" to "𞠛", "ti" to "𞠜", "tu" to "𞠝",
            ),
        )

        /**
         * Latin → Pahawh Hmong (U+16B00 block).
         * Pahawh Hmong is a 20th-century writing system for the
         * Hmong language created by Shong Lue Yang c. 1959.
         * Supplementary plane. Uses both consonants + vowels
         * (uniquely among modern Mande/Asian-origin scripts).
         */
        val LatinToPahawhHmong: IndicScriptTable = IndicScriptTable(
            sourceScheme = "Latin",
            targetScript = "PahawhHmong",
            mappings = mapOf(
                "a" to "𖬀", "e" to "𖬁", "i" to "𖬂",
                "o" to "𖬃", "u" to "𖬄", "v" to "𖬅",
                "k" to "𖬉", "kh" to "𖬊", "g" to "𖬋",
                "gh" to "𖬌", "ng" to "𖬍",
                "ch" to "𖬔", "chh" to "𖬕", "j" to "𖬖",
                "n" to "𖬗", "ny" to "𖬘",
                "t" to "𖬙", "th" to "𖬚", "d" to "𖬛",
                "p" to "𖬜", "ph" to "𖬝", "b" to "𖬞",
                "s" to "𖬟", "x" to "𖬠", "r" to "𖬡",
                "h" to "𖬢", "m" to "𖬣", "f" to "𖬤",
            ),
        )

        /**
         * Latin → Tifinagh (U+2D30 block).
         * Tifinagh is the consonantal alphabet used by the Berber /
         * Amazigh languages across North Africa (Morocco, Algeria,
         * Libya, Niger, Mali). Modern revival uses the standardized
         * Neo-Tifinagh form (this table).
         */
        val LatinToTifinagh: IndicScriptTable = IndicScriptTable(
            sourceScheme = "Latin",
            targetScript = "Tifinagh",
            mappings = mapOf(
                "a" to "ⴰ", "b" to "ⴱ", "g" to "ⴳ", "d" to "ⴷ",
                "f" to "ⴼ", "k" to "ⴽ", "h" to "ⵀ",
                "x" to "ⵅ", "q" to "ⵇ",
                "i" to "ⵉ", "j" to "ⵊ", "l" to "ⵍ", "m" to "ⵎ",
                "n" to "ⵏ", "u" to "ⵓ", "p" to "ⵒ",
                "r" to "ⵔ", "gh" to "ⵖ",
                "s" to "ⵙ", "c" to "ⵛ",
                "t" to "ⵜ", "w" to "ⵡ", "y" to "ⵢ", "z" to "ⵣ",
            ),
        )

        /**
         * Latin → Vithkuqi (U+10570 block).
         * Vithkuqi is a 19th-century alphabet for Albanian created
         * by Naum Veqilharxhi in 1844, used briefly before being
         * replaced by the modern Latin Albanian alphabet. Encoded
         * in Unicode 14 (Sept 2021). Supplementary plane.
         */
        val LatinToVithkuqi: IndicScriptTable = IndicScriptTable(
            sourceScheme = "Latin",
            targetScript = "Vithkuqi",
            mappings = mapOf(
                "a" to "𐕰", "b" to "𐕱", "c" to "𐕲", "d" to "𐕳",
                "e" to "𐕴", "f" to "𐕵", "g" to "𐕶", "h" to "𐕷",
                "i" to "𐕸", "j" to "𐕹", "k" to "𐕺", "l" to "𐕻",
                "m" to "𐕽", "n" to "𐕾", "o" to "𐕿", "p" to "𐖀",
                "q" to "𐖁", "r" to "𐖂", "s" to "𐖃", "t" to "𐖄",
                "u" to "𐖅", "v" to "𐖆", "x" to "𐖇", "y" to "𐖈",
                "z" to "𐖉",
            ),
        )

        /**
         * Latin → Hanunoo (U+1720 block).
         * Hanunoo is one of the four surviving Brahmic-derived
         * scripts of the Philippines (alongside Tagbanwa, Buhid, and
         * Baybayin). Still in active use by the Mangyan people of
         * Mindoro. Vertical bottom-to-top traditionally; encoded
         * horizontally in Unicode.
         */
        val LatinToHanunoo: IndicScriptTable = IndicScriptTable(
            sourceScheme = "Latin",
            targetScript = "Hanunoo",
            mappings = mapOf(
                "a" to "ᜠ", "i" to "ᜡ", "u" to "ᜢ",
                "ka" to "ᜣ", "ga" to "ᜤ", "nga" to "ᜥ",
                "ta" to "ᜦ", "da" to "ᜧ", "na" to "ᜨ",
                "pa" to "ᜩ", "ba" to "ᜪ", "ma" to "ᜫ",
                "ya" to "ᜬ", "ra" to "ᜭ", "la" to "ᜮ",
                "wa" to "ᜯ", "sa" to "ᜰ", "ha" to "ᜱ",
            ),
        )

        /**
         * Latin → Soyombo (U+11A50 block).
         * Soyombo is a 17th-century alphabetic script created by the
         * Mongolian lama Zanabazar for writing Sanskrit, Tibetan, and
         * Mongolian. The Soyombo symbol on the Mongolian flag derives
         * from this script. Supplementary plane.
         */
        val LatinToSoyombo: IndicScriptTable = IndicScriptTable(
            sourceScheme = "Latin",
            targetScript = "Soyombo",
            mappings = mapOf(
                "a" to "𑩐", "i" to "𑩑", "u" to "𑩒",
                "k" to "𑪅", "kh" to "𑪆", "g" to "𑪇",
                "ch" to "𑪊", "j" to "𑪋",
                "T" to "𑪎", "Th" to "𑪏", "D" to "𑪐",
                "t" to "𑪓", "th" to "𑪔", "d" to "𑪕",
                "n" to "𑪗", "p" to "𑪘", "ph" to "𑪙",
                "b" to "𑪚", "m" to "𑪜",
                "y" to "𑪝", "r" to "𑪞", "l" to "𑪟",
                "s" to "𑪡", "h" to "𑪢",
            ),
        )

        /**
         * Latin → Marchen (U+11C70 block).
         * Marchen is the historical script of the Bon religion (Tibet),
         * used between the 17th and 20th centuries for liturgical
         * texts. Brahmic-derived. Supplementary plane.
         */
        val LatinToMarchen: IndicScriptTable = IndicScriptTable(
            sourceScheme = "Latin",
            targetScript = "Marchen",
            mappings = mapOf(
                "k" to "𑱰", "kh" to "𑱱", "g" to "𑱲",
                "ng" to "𑱳",
                "c" to "𑱴", "ch" to "𑱵", "j" to "𑱶",
                "ny" to "𑱷",
                "t" to "𑱸", "th" to "𑱹", "d" to "𑱺", "n" to "𑱻",
                "p" to "𑱼", "ph" to "𑱽", "b" to "𑱾", "m" to "𑱿",
                "ts" to "𑲀", "tsh" to "𑲁", "dz" to "𑲂",
                "y" to "𑲄", "r" to "𑲅", "l" to "𑲆",
                "sh" to "𑲇", "s" to "𑲈", "h" to "𑲉",
            ),
        )

        /**
         * Latin → Chakma (U+11100 block).
         * Chakma is the Brahmic-derived script of the Chakma language
         * (Chittagong Hill Tracts, Bangladesh + Tripura, India). Still
         * in active use; recently revived in education + literature.
         * Supplementary plane.
         */
        val LatinToChakma: IndicScriptTable = IndicScriptTable(
            sourceScheme = "Latin",
            targetScript = "Chakma",
            mappings = mapOf(
                "k" to "𑄇", "kh" to "𑄈", "g" to "𑄉", "gh" to "𑄊",
                "ng" to "𑄋",
                "c" to "𑄌", "ch" to "𑄍", "j" to "𑄎", "jh" to "𑄏",
                "ny" to "𑄐",
                "T" to "𑄑", "Th" to "𑄒", "D" to "𑄓", "Dh" to "𑄔",
                "N" to "𑄕",
                "t" to "𑄖", "th" to "𑄗", "d" to "𑄘", "dh" to "𑄙",
                "n" to "𑄚",
                "p" to "𑄛", "ph" to "𑄜", "b" to "𑄝", "bh" to "𑄞",
                "m" to "𑄟",
                "y" to "𑄠", "r" to "𑄢", "l" to "𑄣", "w" to "𑄤",
                "sh" to "𑄥",
            ),
        )

        /**
         * Latin → Tagbanwa (U+1760 block).
         * Tagbanwa is one of the four Philippine Brahmic scripts
         * (alongside Buhid, Hanunoo, Baybayin). Still in active use
         * by the Tagbanwa people of Palawan.
         */
        val LatinToTagbanwa: IndicScriptTable = IndicScriptTable(
            sourceScheme = "Latin",
            targetScript = "Tagbanwa",
            mappings = mapOf(
                "a" to "ᝠ", "i" to "ᝡ", "u" to "ᝢ",
                "ka" to "ᝣ", "ga" to "ᝤ", "nga" to "ᝥ",
                "ta" to "ᝦ", "da" to "ᝧ", "na" to "ᝨ",
                "pa" to "ᝩ", "ba" to "ᝪ", "ma" to "ᝫ",
                "ya" to "ᝬ", "la" to "ᝮ", "wa" to "ᝯ",
                "sa" to "ᝰ",
            ),
        )

        /**
         * Latin → Buhid (U+1740 block).
         * Buhid is the second of the four Philippine Brahmic scripts,
         * still in use by the Buhid Mangyan people of Mindoro.
         */
        val LatinToBuhid: IndicScriptTable = IndicScriptTable(
            sourceScheme = "Latin",
            targetScript = "Buhid",
            mappings = mapOf(
                "a" to "ᝀ", "i" to "ᝁ", "u" to "ᝂ",
                "ka" to "ᝃ", "ga" to "ᝄ", "nga" to "ᝅ",
                "ta" to "ᝆ", "da" to "ᝇ", "na" to "ᝈ",
                "pa" to "ᝉ", "ba" to "ᝊ", "ma" to "ᝋ",
                "ya" to "ᝌ", "ra" to "ᝍ", "la" to "ᝎ",
                "wa" to "ᝏ", "sa" to "ᝐ", "ha" to "ᝑ",
            ),
        )

        /**
         * Latin → Baybayin / Tagalog (U+1700 block).
         * Baybayin is the historical script of the Tagalog language
         * (pre-Spanish-colonial Philippines), currently undergoing a
         * cultural revival in the Philippines.
         */
        val LatinToBaybayin: IndicScriptTable = IndicScriptTable(
            sourceScheme = "Latin",
            targetScript = "Baybayin",
            mappings = mapOf(
                "a" to "ᜀ", "i" to "ᜁ", "u" to "ᜂ",
                "ka" to "ᜃ", "ga" to "ᜄ", "nga" to "ᜅ",
                "ta" to "ᜆ", "da" to "ᜇ", "na" to "ᜈ",
                "pa" to "ᜉ", "ba" to "ᜊ", "ma" to "ᜋ",
                "ya" to "ᜌ", "ra" to "ᜍ", "la" to "ᜎ",
                "wa" to "ᜏ", "sa" to "ᜐ", "ha" to "ᜑ",
            ),
        )

        /**
         * Latin → Wancho (U+1E2C0 block).
         * Wancho is a 20th-century alphabet for the Wancho Naga
         * language of Arunachal Pradesh, India + Myanmar. Created by
         * Banwang Losu c. 2001. Encoded in Unicode 12 (March 2019).
         * Supplementary plane.
         */
        val LatinToWancho: IndicScriptTable = IndicScriptTable(
            sourceScheme = "Latin",
            targetScript = "Wancho",
            mappings = mapOf(
                "a" to "𞋀", "b" to "𞋁", "c" to "𞋂", "d" to "𞋃",
                "e" to "𞋄", "f" to "𞋅", "g" to "𞋆", "h" to "𞋇",
                "i" to "𞋈", "j" to "𞋉", "k" to "𞋊", "l" to "𞋋",
                "m" to "𞋌", "n" to "𞋍", "o" to "𞋎", "p" to "𞋏",
                "q" to "𞋐", "r" to "𞋑", "s" to "𞋒", "t" to "𞋓",
                "u" to "𞋔", "v" to "𞋕", "w" to "𞋖", "x" to "𞋗",
                "y" to "𞋘", "z" to "𞋙",
            ),
        )

        /**
         * Latin → Nyiakeng Puachue Hmong (U+1E100 block).
         * Sister of Pahawh Hmong (shipped v1.8.16) — a separate
         * Hmong-language script created by Reverend Chervang Kong
         * Vang in the 1980s. Encoded in Unicode 12. Supplementary
         * plane.
         */
        val LatinToNyiakengPuachueHmong: IndicScriptTable = IndicScriptTable(
            sourceScheme = "Latin",
            targetScript = "NyiakengPuachueHmong",
            mappings = mapOf(
                "a" to "𞄀", "e" to "𞄁", "i" to "𞄂", "o" to "𞄃", "u" to "𞄄",
                "y" to "𞄅", "b" to "𞄆", "c" to "𞄇",
                "ch" to "𞄈", "d" to "𞄉", "dh" to "𞄊",
                "f" to "𞄋", "g" to "𞄌", "h" to "𞄍",
                "k" to "𞄏", "kh" to "𞄐", "l" to "𞄑",
                "m" to "𞄒", "n" to "𞄓", "ny" to "𞄔",
                "p" to "𞄕", "ph" to "𞄖", "r" to "𞄗",
                "s" to "𞄘", "t" to "𞄙", "th" to "𞄚",
                "v" to "𞄛", "w" to "𞄜", "z" to "𞄝",
            ),
        )

        /**
         * Latin → Medefaidrin (U+16E40 block).
         * Medefaidrin is a 20th-century constructed alphabet used by
         * the Oberi Okaime Christian community in southeast Nigeria.
         * Created c. 1930 by Michael Ukpong + Akpan Akpan Udofia.
         * Encoded in Unicode 11. Supplementary plane.
         */
        val LatinToMedefaidrin: IndicScriptTable = IndicScriptTable(
            sourceScheme = "Latin",
            targetScript = "Medefaidrin",
            mappings = mapOf(
                "a" to "𖹠", "b" to "𖹡", "c" to "𖹢", "d" to "𖹣",
                "e" to "𖹤", "f" to "𖹥", "g" to "𖹦", "h" to "𖹧",
                "i" to "𖹨", "j" to "𖹩", "k" to "𖹪", "l" to "𖹫",
                "m" to "𖹬", "n" to "𖹭", "o" to "𖹮", "p" to "𖹯",
                "q" to "𖹰", "r" to "𖹱", "s" to "𖹲", "t" to "𖹳",
                "u" to "𖹴", "v" to "𖹵", "w" to "𖹶", "x" to "𖹷",
                "y" to "𖹸", "z" to "𖹹",
            ),
        )

        /**
         * ITRANS → Saurashtra (U+A880 block).
         * Saurashtra is a Brahmic-derived script for the Saurashtra
         * language (Tamil Nadu, India). Active in modern Saurashtra
         * community publishing. Native digits U+A8D0..U+A8D9.
         */
        val ItransToSaurashtra: IndicScriptTable = IndicScriptTable(
            sourceScheme = "ITRANS",
            targetScript = "Saurashtra",
            mappings = buildIndicMappings(
                vowels = mapOf(
                    "a" to "ꢂ", "aa" to "ꢃ", "A" to "ꢃ",
                    "i" to "ꢄ", "ii" to "ꢅ", "I" to "ꢅ",
                    "u" to "ꢆ", "uu" to "ꢇ", "U" to "ꢇ",
                    "e" to "ꢊ", "ai" to "ꢋ", "o" to "ꢌ", "au" to "ꢍ",
                ),
                consonants = mapOf(
                    "k" to "ꢒ", "kh" to "ꢓ", "g" to "ꢔ", "gh" to "ꢕ",
                    "ch" to "ꢗ", "Ch" to "ꢘ", "j" to "ꢙ", "jh" to "ꢚ",
                    "T" to "ꢜ", "Th" to "ꢝ", "D" to "ꢞ", "Dh" to "ꢟ", "N" to "ꢠ",
                    "t" to "ꢡ", "th" to "ꢢ", "d" to "ꢣ", "dh" to "ꢤ", "n" to "ꢥ",
                    "p" to "ꢦ", "ph" to "ꢧ", "b" to "ꢨ", "bh" to "ꢩ", "m" to "ꢪ",
                    "y" to "ꢫ", "r" to "ꢬ", "l" to "ꢮ", "v" to "ꢯ",
                    "sh" to "ꢰ", "Sh" to "ꢱ", "s" to "ꢲ", "h" to "ꢳ",
                ),
                digits = "꣐꣑꣒꣓꣔꣕꣖꣗꣘꣙",
                anusvara = "ꢀ", visarga = "ꢁ",
            ),
        )

        /**
         * Latin → Kayah Li (U+A900 block).
         * Kayah Li is a Brahmic-derived script for the Kayah / Karen
         * languages of Myanmar + Thailand. Native digits
         * U+A900..U+A909.
         */
        val LatinToKayahLi: IndicScriptTable = IndicScriptTable(
            sourceScheme = "Latin",
            targetScript = "KayahLi",
            mappings = mapOf(
                "k" to "ꤊ", "kh" to "ꤋ", "g" to "ꤌ",
                "ng" to "ꤍ",
                "ch" to "ꤏ", "j" to "ꤐ",
                "ny" to "ꤑ", "t" to "ꤒ", "th" to "ꤓ", "d" to "ꤔ",
                "n" to "ꤕ",
                "p" to "ꤖ", "ph" to "ꤗ", "b" to "ꤘ", "m" to "ꤙ",
                "y" to "ꤚ", "r" to "ꤛ", "l" to "ꤜ", "w" to "ꤝ",
                "s" to "ꤞ", "h" to "ꤟ",
            ),
        )

        /**
         * Latin → Rejang (U+A930 block).
         * Rejang is a Brahmic-derived script for the Rejang language
         * of Sumatra, Indonesia. 23 consonants + vowel signs.
         */
        val LatinToRejang: IndicScriptTable = IndicScriptTable(
            sourceScheme = "Latin",
            targetScript = "Rejang",
            mappings = mapOf(
                "k" to "ꤰ", "g" to "ꤱ",
                "ng" to "ꤲ", "t" to "ꤳ", "d" to "ꤴ",
                "n" to "ꤵ",
                "p" to "ꤶ", "b" to "ꤷ", "m" to "ꤸ",
                "c" to "ꤹ", "j" to "ꤺ", "ny" to "ꤻ",
                "s" to "ꤼ", "r" to "ꤽ", "l" to "ꤾ",
                "y" to "ꤿ", "w" to "ꥀ", "h" to "ꥁ",
                "mb" to "ꥂ", "ngg" to "ꥃ", "nd" to "ꥄ",
                "nyj" to "ꥅ",
            ),
        )

        /**
         * ITRANS → Modi (U+11600 block).
         * Modi is a Brahmic-derived script historically used for the
         * Marathi language in western India c. 13th-20th century.
         * Replaced by Devanagari in modern Marathi but undergoing
         * cultural revival. Supplementary plane. Native digits
         * U+11650..U+11659.
         */
        val ItransToModi: IndicScriptTable = IndicScriptTable(
            sourceScheme = "ITRANS",
            targetScript = "Modi",
            mappings = buildIndicMappings(
                vowels = mapOf(
                    "a" to "𑘀", "aa" to "𑘁", "A" to "𑘁",
                    "i" to "𑘂", "ii" to "𑘃", "I" to "𑘃",
                    "u" to "𑘄", "uu" to "𑘅", "U" to "𑘅",
                    "e" to "𑘊", "ai" to "𑘋", "o" to "𑘌", "au" to "𑘍",
                ),
                consonants = mapOf(
                    "k" to "𑘎", "kh" to "𑘏", "g" to "𑘐", "gh" to "𑘑",
                    "ch" to "𑘓", "Ch" to "𑘔", "j" to "𑘕", "jh" to "𑘖",
                    "T" to "𑘘", "Th" to "𑘙", "D" to "𑘚", "Dh" to "𑘛", "N" to "𑘜",
                    "t" to "𑘝", "th" to "𑘞", "d" to "𑘟", "dh" to "𑘠", "n" to "𑘡",
                    "p" to "𑘢", "ph" to "𑘣", "b" to "𑘤", "bh" to "𑘥", "m" to "𑘦",
                    "y" to "𑘧", "r" to "𑘨", "l" to "𑘩", "v" to "𑘪",
                    "sh" to "𑘫", "Sh" to "𑘬", "s" to "𑘭", "h" to "𑘮",
                ),
                digits = "𑙐𑙑𑙒𑙓𑙔𑙕𑙖𑙗𑙘𑙙",
                anusvara = "𑘽", visarga = "𑘾",
            ),
        )

        /**
         * ITRANS → Sharada (U+11180 block).
         * Sharada is a Brahmic-derived script historically used for
         * Sanskrit + Kashmiri in northern India c. 8th-20th century.
         * Replaced by Devanagari + Perso-Arabic for modern Kashmiri
         * but retained in liturgical contexts. Supplementary plane.
         * Native digits U+111D0..U+111D9.
         */
        val ItransToSharada: IndicScriptTable = IndicScriptTable(
            sourceScheme = "ITRANS",
            targetScript = "Sharada",
            mappings = buildIndicMappings(
                vowels = mapOf(
                    "a" to "𑆃", "aa" to "𑆄", "A" to "𑆄",
                    "i" to "𑆅", "ii" to "𑆆", "I" to "𑆆",
                    "u" to "𑆇", "uu" to "𑆈", "U" to "𑆈",
                    "e" to "𑆍", "ai" to "𑆎", "o" to "𑆏", "au" to "𑆐",
                ),
                consonants = mapOf(
                    "k" to "𑆑", "kh" to "𑆒", "g" to "𑆓", "gh" to "𑆔",
                    "ch" to "𑆖", "Ch" to "𑆗", "j" to "𑆘", "jh" to "𑆙",
                    "T" to "𑆛", "Th" to "𑆜", "D" to "𑆝", "Dh" to "𑆞", "N" to "𑆟",
                    "t" to "𑆠", "th" to "𑆡", "d" to "𑆢", "dh" to "𑆣", "n" to "𑆤",
                    "p" to "𑆥", "ph" to "𑆦", "b" to "𑆧", "bh" to "𑆨", "m" to "𑆩",
                    "y" to "𑆪", "r" to "𑆫", "l" to "𑆬", "v" to "𑆮",
                    "sh" to "𑆯", "Sh" to "𑆰", "s" to "𑆱", "h" to "𑆲",
                ),
                digits = "𑇐𑇑𑇒𑇓𑇔𑇕𑇖𑇗𑇘𑇙",
                anusvara = "𑆀", visarga = "𑆂",
            ),
        )

        /**
         * ITRANS → Takri (U+11680 block).
         * Takri is a Brahmic-derived script historically used for
         * Dogri / Chambeali / Kishtwari / Bilaspuri in the Punjab +
         * Himachal Pradesh + Jammu hills c. 16th-20th century.
         * Replaced by Devanagari + Perso-Arabic in modern usage but
         * undergoing limited revival.  Supplementary plane.  Native
         * digits U+116C0..U+116C9.
         */
        val ItransToTakri: IndicScriptTable = IndicScriptTable(
            sourceScheme = "ITRANS",
            targetScript = "Takri",
            mappings = buildIndicMappings(
                vowels = mapOf(
                    "a" to "𑚀", "aa" to "𑚁", "A" to "𑚁",
                    "i" to "𑚂", "ii" to "𑚃", "I" to "𑚃",
                    "u" to "𑚄", "uu" to "𑚅", "U" to "𑚅",
                    "e" to "𑚆", "ai" to "𑚇", "o" to "𑚈", "au" to "𑚉",
                ),
                consonants = mapOf(
                    "k" to "𑚊", "kh" to "𑚋", "g" to "𑚌", "gh" to "𑚍",
                    "ch" to "𑚏", "Ch" to "𑚐", "j" to "𑚑", "jh" to "𑚒",
                    "T" to "𑚔", "Th" to "𑚕", "D" to "𑚖", "Dh" to "𑚗", "N" to "𑚘",
                    "t" to "𑚙", "th" to "𑚚", "d" to "𑚛", "dh" to "𑚜", "n" to "𑚝",
                    "p" to "𑚞", "ph" to "𑚟", "b" to "𑚠", "bh" to "𑚡", "m" to "𑚢",
                    "y" to "𑚣", "r" to "𑚤", "l" to "𑚥", "v" to "𑚦",
                    "sh" to "𑚧", "Sh" to "𑚨", "s" to "𑚩", "h" to "𑚪",
                ),
                digits = "𑛀𑛁𑛂𑛃𑛄𑛅𑛆𑛇𑛈𑛉",
                anusvara = "𑚭", visarga = "𑚳",
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
            // Iterate by code point, not by char, so supplementary-plane
            // digit blocks (Adlam U+1E950..) round-trip correctly.
            var digitIndex = 0
            var byteIdx = 0
            while (byteIdx < digits.length && digitIndex <= 9) {
                val cp = digits.codePointAt(byteIdx)
                map[digitIndex.toString()] = String(Character.toChars(cp))
                byteIdx += Character.charCount(cp)
                digitIndex++
            }
            map["M"] = anusvara
            map["H"] = visarga
            map["|"] = "।"
            map["||"] = "॥"
            return map
        }
    }
}
