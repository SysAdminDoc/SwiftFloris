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
 */
internal class TypedWordKnowledge {
    private class Entry(val key: String, val known: Boolean)

    @Volatile private var last: Entry? = null

    fun record(word: String, known: Boolean) {
        last = candidateSignalKeyOf(word)?.let { Entry(it, known) }
    }

    fun clear() {
        last = null
    }

    /**
     * `true` only when the last pass ranked [word] and knew it. A pass for another word, or none
     * yet, reads as unknown, so a word typed faster than its pass waits for its next sighting.
     */
    fun wasKnown(word: String): Boolean {
        val entry = last ?: return false
        return entry.known && entry.key == candidateSignalKeyOf(word)
    }
}
