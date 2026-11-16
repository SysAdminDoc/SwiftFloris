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

package dev.patrickgold.florisboard.ime.dictionary

/** The cleaning [DictionaryManager.learnWord] applies to a committed word before learning it. */
internal object LearnedWordText {
    /** Single chars and digrams are noise. */
    const val MIN_LENGTH = 3

    /** Anything longer is almost certainly a URL, email or pasted token. */
    const val MAX_LENGTH = 32

    /**
     * The form [rawWord] is learned under: surrounding characters other than letters,
     * apostrophes and hyphens trimmed, then lowercased. `null` when that isn't a learnable word:
     * too short or too long, no letter, or anything inside besides letters, `'` and `-`.
     */
    fun normalize(rawWord: String): String? {
        val cleaned = rawWord.trim().trim { ch -> !ch.isLearnable() }
        if (cleaned.length < MIN_LENGTH || cleaned.length > MAX_LENGTH) return null
        if (cleaned.none { it.isLetter() }) return null
        if (cleaned.any { ch -> !ch.isLearnable() }) return null
        return cleaned.lowercase()
    }

    private fun Char.isLearnable(): Boolean = isLetter() || this == '\'' || this == '-'
}
