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
 * What a space learns, for the on-screen space key and a physical keyboard's space bar alike.
 *
 * A space ends a word. If the spacebar's autocorrect candidate was committed, committing it
 * already learned it, so the space learns nothing more: learning it a second time would count
 * the word twice and chain it after itself, which taught the trigram store triples like
 * "a florida florida". Otherwise the space learns the word as typed.
 */
internal object SpacebarLearnPolicy {
    fun typedWordToLearn(typedWordBeforeCommit: String, candidateCommitted: Boolean): String? {
        if (candidateCommitted) return null
        return typedWordBeforeCommit.takeIf { it.isNotBlank() }
    }
}
