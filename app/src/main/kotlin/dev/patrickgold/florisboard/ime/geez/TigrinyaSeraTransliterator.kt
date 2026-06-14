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
 * ROADMAP §8 L6 — Tigrinya / Tigre / Blin variants of the SERA →
 * Ge'ez transliteration table.
 *
 * Tigrinya (and the Eritrean cousin languages Tigre + Blin) share the
 * SERA-style romanisation pattern with Amharic but ship a small
 * additional inventory of glyphs Amharic doesn't use day-to-day:
 *
 *  - The **qhe** series (ቐ ቑ ቒ ቓ ቔ ቕ ቖ — U+1250..U+1256) — used in
 *    Tigrinya for an emphatic /q'/ that Amharic collapses into ቀ.
 *  - The **xa** series (ኀ ኁ ኂ ኃ ኄ ኅ ኆ — U+1280..U+1286) — historical
 *    `ḫa` retained in Tigrinya / Tigre orthography.
 *  - The Tigrinya-distinctive **labio-velar** ኳ (kwa, U+12B3) and ጓ
 *    (gwa, U+1313) shipped as `kWa` / `gWa` mappings; the full
 *    labio-velar series is left to the L6.2 follow-up slice.
 *
 * Composes the dialect extras on top of the shared
 * [GeezSeraTransliterator.table] using a single greedy longest-match
 * pass, so token boundaries between the Amharic table and the
 * Tigrinya extras are honoured.
 *
 * Reference: [Unicode Ethiopic block charts](https://www.unicode.org/charts/PDF/U1200.pdf)
 * + [SERA standard](https://web.archive.org/web/20140613192616/http://www.abyssiniagateway.net/fidel/sera-faq.html).
 */
object TigrinyaSeraTransliterator {

    /** Transliterate Tigrinya-flavoured SERA into Ge'ez script. */
    fun transliterate(latin: String): String =
        GeezSeraTransliterator.transliterateWith(latin, combinedTable)

    /**
     * Tigrinya / Tigre / Blin extension mappings layered on top of the
     * shared Ge'ez transliterator. Capital `Q` is the SERA convention
     * for the qhe series; capital `X` for the xa series; capital `W`
     * suffix marks the labio-velar variant of the preceding consonant.
     *
     * As in [GeezSeraTransliterator.table], the bare consonant maps to the
     * 6th order (sädis / schwa) and the `e` suffix marks the 1st order (ä),
     * matching the SERA scheme — not the reverse.
     */
    private val tigrinyaExtras: Map<String, String> = mapOf(
        // qhe series — Unicode U+1250..U+1256 in order ä u i a ē ə o.
        "Q" to "ቕ", "Qe" to "ቐ", "Qu" to "ቑ", "Qi" to "ቒ",
        "Qa" to "ቓ", "QE" to "ቔ", "Qo" to "ቖ",
        // xa series — Unicode U+1280..U+1286 in order ä u i a ē ə o.
        "X" to "ኅ", "Xe" to "ኀ", "Xu" to "ኁ", "Xi" to "ኂ",
        "Xa" to "ኃ", "XE" to "ኄ", "Xo" to "ኆ",
        // Distinctive labio-velars.
        "kWa" to "ኳ", "gWa" to "ጓ",
    )

    private val combinedTable: Map<String, String> =
        GeezSeraTransliterator.table + tigrinyaExtras
}
