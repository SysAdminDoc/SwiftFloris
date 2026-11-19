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

/** The words before the cursor that next-word prediction reads its context from. */
internal object PreviousWords {
    private const val SENTENCE_BOUNDARY_CHARS = ".!?…\n"

    /**
     * The [depth]th word before the cursor in the current sentence, or null. A sentence end or a
     * line break between the cursor and that word means there isn't one, so the first word of a
     * sentence isn't predicted from the last word of the sentence before. The next-word chain
     * resets at the same places when it learns (see `WordBoundaryLearnPolicy`). Commas and other
     * punctuation inside a sentence are skipped.
     */
    fun inSentence(textBeforeCursor: String, depth: Int = 1): String? {
        if (textBeforeCursor.isBlank() || depth < 1) return null
        var end = textBeforeCursor.length
        var lastFound: String? = null
        for (n in 1..depth) {
            while (end > 0) {
                val ch = textBeforeCursor[end - 1]
                if (ch.isWordChar()) break
                if (ch in SENTENCE_BOUNDARY_CHARS) return null
                end--
            }
            if (end == 0) return null
            var start = end
            while (start > 0 && textBeforeCursor[start - 1].isWordChar()) start--
            lastFound = textBeforeCursor.substring(start, end)
            if (n == depth) return lastFound
            end = start
        }
        return lastFound
    }

    private fun Char.isWordChar(): Boolean = isLetter() || this == '\'' || this == '-'
}
