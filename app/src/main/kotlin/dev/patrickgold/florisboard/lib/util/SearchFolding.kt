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

package dev.patrickgold.florisboard.lib.util

import java.text.Normalizer
import java.util.Locale

private val CombiningMarkRegex = Regex("\\p{Mn}+")

// Letters with no decomposed form, so stripping marks never reaches them. Without these "wloski" misses
// "włoski", "foroyskt" misses "føroyskt" and "azerbaycan" misses "azərbaycan". Keys are lowercase because the
// case fold runs first.
private val UndecomposedLetters = mapOf(
    'ł' to "l", 'ŀ' to "l", 'ø' to "o", 'đ' to "d", 'ð' to "d", 'ħ' to "h", 'ı' to "i", 'ŧ' to "t",
    'ß' to "ss", 'æ' to "ae", 'œ' to "oe", 'þ' to "th",
    'ə' to "e", 'ɛ' to "e", 'ɔ' to "o", 'ʋ' to "v", 'ɖ' to "d", 'ƒ' to "f",
)

/**
 * Folds [text] for matching what someone typed. Accents and other combining marks go, letters with nothing to
 * strip become the plain letters people type instead ("włoski" folds to "wloski", "Straße" to "strasse"), and
 * case folds with the root locale, so "Français", "francais" and "FRANCAIS" all fold to "francais" whatever the
 * phone's language. Settings search, the language list and the first-run language picker all match through this,
 * so they agree on what counts as the same word.
 */
fun foldForSearch(text: String): String {
    val stripped = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(CombiningMarkRegex, "")
        .lowercase(Locale.ROOT)
    if (stripped.none { it in UndecomposedLetters }) return stripped
    return buildString(stripped.length + 2) {
        for (char in stripped) {
            append(UndecomposedLetters[char] ?: char.toString())
        }
    }
}
