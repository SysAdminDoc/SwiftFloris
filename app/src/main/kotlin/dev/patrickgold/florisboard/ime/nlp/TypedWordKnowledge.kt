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

package dev.patrickgold.florisboard.ime.nlp

import dev.patrickgold.florisboard.ime.dictionary.LearnedWordText

private val RightSingleQuote = Char(0x2019)

/**
 * The key suggestion signals use for a typed word: trimmed of surrounding punctuation (apostrophes
 * kept, straight or curly) and lowercased. `null` when no letter is left.
 */
internal fun candidateSignalKeyOf(word: String): String? {
    val normalized = word.trim()
        .trim { char -> !char.isLetter() && char != '\'' && char != RightSingleQuote }
        .lowercase()
    if (normalized.isBlank() || normalized.none { it.isLetter() }) return null
    return normalized
}

/**
 * Whether the last suggestion pass found its typed word in the learned words or a dictionary,
 * kept for the space key, which learns a new word only on its second sighting. Written by the
 * suggestion pass and read on the input thread.
 *
 * Words are keyed the way [LearnedWordText.normalize] learns them, plus the language, so the
 * answer is about the exact word the space would learn.
 */
internal class TypedWordKnowledge {
    private class Entry(val key: String, val languageTag: String, val known: Boolean)

    @Volatile private var last: Entry? = null

    /**
     * Records what a pass in [languageTag] found for [word]. [known] is `null` when the pass
     * didn't look (suggestions off). A pass that looked up a different form than the one learned
     * (the dictionary key drops a hyphen at either end) records nothing either.
     */
    fun record(word: String, languageTag: String, known: Boolean?) {
        val key = LearnedWordText.normalize(word)
        last = if (known == null || key == null || key != candidateSignalKeyOf(word)) {
            null
        } else {
            Entry(key, languageTag, known)
        }
    }

    fun clear() {
        last = null
    }

    /**
     * What the last pass found for [word] in [languageTag], or `null` when it ranked another
     * word or none yet (typed faster than its pass, or suggestions are off); the caller looks the
     * word up itself then.
     */
    fun knownState(word: String, languageTag: String): Boolean? {
        val entry = last ?: return null
        if (entry.languageTag != languageTag) return null
        if (entry.key != LearnedWordText.normalize(word)) return null
        return entry.known
    }
}
