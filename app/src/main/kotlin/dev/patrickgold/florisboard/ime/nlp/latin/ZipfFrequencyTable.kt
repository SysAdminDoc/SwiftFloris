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

package dev.patrickgold.florisboard.ime.nlp.latin

import android.content.Context
import dev.patrickgold.florisboard.lib.devtools.flogError
import dev.patrickgold.florisboard.lib.devtools.flogInfo
import java.io.BufferedReader
import java.io.IOException
import java.io.StringReader

/**
 * ROADMAP §7 Next-3.2 — SUBTLEX / wordfreq Zipf-scale frequency overlay.
 *
 * Subtitle frequencies predict typing behavior better than dictionary
 * frequencies. SCOWL's 0..255 byte-frequency lands the right rough order
 * for the lexicon, but a 5-million-utterance subtitle corpus captures
 * conversational usage patterns SCOWL doesn't reach.
 *
 * Zipf scale (per rspeer/wordfreq):
 *  - Zipf 1 = once per billion words (rare technical term)
 *  - Zipf 3 = once per million words (uncommon)
 *  - Zipf 5 = once per ten thousand words (common)
 *  - Zipf 7 = once per hundred words (top function word — `the`, `a`, `i`)
 *
 * The table is loaded from `assets/freq/<langCode>.tsv` (one
 * `word\tzipf` line per entry, tab-separated, UTF-8). The shipping
 * default tables are tiny (~top-1000 words per language) — they exist
 * mainly to ship the merger plumbing today; a follow-up will bundle
 * full SUBTLEX-extracted tables once they're packaged as a separate
 * addon (Next-10.3 dictionary-pack).
 *
 * Blended-frequency formula: when a word appears in *both* the SCOWL
 * dictionary (0..1) AND the Zipf table (1..8), we blend
 *   `0.6 * scowl + 0.4 * (zipf / 8.0)`
 * giving SCOWL the primary signal but letting subtitle frequency adjust
 * the rank for usage-vs-spelling-list bias. Words only in SCOWL fall
 * back to pure SCOWL; words only in Zipf get `zipf / 8.0` directly
 * (which lets uncommon-but-spoken words like `okay` rank meaningfully
 * even when SCOWL ranks them low).
 */
class ZipfFrequencyTable private constructor(
    private val zipfByWord: Map<String, Float>,
) {

    /** Return the Zipf value for [word] in [1, 8], or null when absent. */
    fun zipfFor(word: String): Float? = zipfByWord[word.lowercase()]

    /**
     * Blend a SCOWL frequency (0..1) with this Zipf table for [word].
     * Returns the blended frequency in [0, 1]. See class doc for the
     * exact formula.
     */
    fun blendedFrequency(word: String, scowlFrequency: Double): Double {
        val zipf = zipfFor(word)
        if (zipf == null) return scowlFrequency
        val zipfNorm = (zipf / 8.0).coerceIn(0.0, 1.0)
        if (scowlFrequency <= 0.0) return zipfNorm
        return 0.6 * scowlFrequency + 0.4 * zipfNorm
    }

    val size: Int get() = zipfByWord.size

    companion object {
        /** Empty table; `blendedFrequency` becomes a passthrough. */
        val Empty = ZipfFrequencyTable(emptyMap())

        /**
         * Load the `assets/freq/<langCode>.tsv` table for the given
         * language. Returns [Empty] when the asset is missing or
         * malformed — the merger is still a passthrough in that case,
         * so missing data degrades gracefully.
         */
        fun load(context: Context, languageCode: String): ZipfFrequencyTable {
            val path = "freq/${languageCode.lowercase()}.tsv"
            return try {
                context.assets.open(path).bufferedReader(Charsets.UTF_8).use { reader ->
                    parseReader(languageCode, reader)
                }
            } catch (_: IOException) {
                // Asset missing for this language — pass-through (no Zipf adjustment).
                Empty
            } catch (e: Throwable) {
                flogError { "ZipfFrequencyTable($languageCode) load failed: $e" }
                Empty
            }
        }

        /**
         * Parse a Zipf TSV blob directly. Used by [LatinDictionaryStore] so
         * the same asset-reader abstraction is reused for the SCOWL `.json`
         * and the Zipf `.tsv` — keeping IO out of the table and the table
         * trivially unit-testable.
         */
        fun parse(languageCode: String, rawTsv: String?): ZipfFrequencyTable {
            if (rawTsv.isNullOrBlank()) return Empty
            return try {
                BufferedReader(StringReader(rawTsv)).use { reader ->
                    parseReader(languageCode, reader)
                }
            } catch (e: Throwable) {
                flogError { "ZipfFrequencyTable($languageCode) parse failed: $e" }
                Empty
            }
        }

        private fun parseReader(languageCode: String, reader: BufferedReader): ZipfFrequencyTable {
            val map = HashMap<String, Float>(8192)
            reader.lineSequence().forEach { rawLine ->
                if (rawLine.isBlank() || rawLine.startsWith('#')) return@forEach
                val parts = rawLine.split('\t')
                if (parts.size != 2) return@forEach
                val word = parts[0].trim().lowercase()
                val zipf = parts[1].trim().toFloatOrNull() ?: return@forEach
                if (word.isBlank()) return@forEach
                if (zipf < 1f || zipf > 8f) return@forEach
                map[word] = zipf
            }
            flogInfo { "ZipfFrequencyTable($languageCode) loaded ${map.size} entries" }
            return if (map.isEmpty()) Empty else ZipfFrequencyTable(map.toMap())
        }
    }
}
