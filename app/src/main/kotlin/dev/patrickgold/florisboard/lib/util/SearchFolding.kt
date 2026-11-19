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

/**
 * Folds [text] for matching what someone typed: accents and other combining marks go, and so does case, so
 * "Français", "francais" and "FRANCAIS" all fold to "francais". Settings search and the language list both match
 * through this, so they agree on what counts as the same word. Case folds with the root locale, so the result
 * doesn't change with the phone's language (a Turkish default would otherwise turn "I" into a dotless "ı").
 */
fun foldForSearch(text: String): String {
    return Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(CombiningMarkRegex, "")
        .lowercase(Locale.ROOT)
}
