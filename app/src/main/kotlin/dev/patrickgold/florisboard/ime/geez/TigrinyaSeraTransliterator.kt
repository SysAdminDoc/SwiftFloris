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
     */
    private val tigrinyaExtras: Map<String, String> = mapOf(
        // qhe series — U+1250..U+1256 (ä u i a e ə o).
        "Q" to "\u1250", "Qu" to "\u1251", "Qi" to "\u1252",
        "Qa" to "\u1253", "QE" to "\u1254", "Qe" to "\u1255", "Qo" to "\u1256",
        // xa series — U+1280..U+1286.
        "X" to "\u1280", "Xu" to "\u1281", "Xi" to "\u1282",
        "Xa" to "\u1283", "XE" to "\u1284", "Xe" to "\u1285", "Xo" to "\u1286",
        // Distinctive labio-velars.
        "kWa" to "\u12B3", "gWa" to "\u1313",
    )

    private val combinedTable: Map<String, String> =
        GeezSeraTransliterator.table + tigrinyaExtras
}
