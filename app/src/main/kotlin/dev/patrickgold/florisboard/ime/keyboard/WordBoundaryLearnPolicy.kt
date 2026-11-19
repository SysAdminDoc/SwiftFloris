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

package dev.patrickgold.florisboard.ime.keyboard

/**
 * Which typed characters end the word before them, and which end the sentence.
 *
 * Space was the only key that learned the word it ended, so a word typed right before a comma,
 * a period or Enter ("see you tomorrow.") was never learned and never paired with the words
 * around it. A punctuation key now learns that word the way space does (see
 * [SpacebarLearnPolicy]). Apostrophes and hyphens join words ("don't", "well-known"), and symbols
 * like `@`, `/` and `#` sit inside addresses and tags, so none of them end a word here.
 *
 * A sentence end also breaks the next-word chain, so the first word of a sentence isn't learned
 * as a continuation of the last word of the one before.
 */
internal object WordBoundaryLearnPolicy {
    private const val WORD_ENDERS = ".,!?;:)]}\"”»…"
    private const val SENTENCE_ENDERS = ".!?…"

    /** True when typing [text] ends the word before the cursor. */
    fun endsWord(text: String): Boolean = text.length == 1 && text[0] in WORD_ENDERS

    /** True when typing [text] ends the sentence, so the next word starts a new chain. */
    fun endsSentence(text: String): Boolean = text.length == 1 && text[0] in SENTENCE_ENDERS
}
