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

package dev.patrickgold.florisboard.ime.media.emoji

import dev.patrickgold.florisboard.ime.nlp.EmojiSuggestionCandidate
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Committing a suggestion replaces the composing region with the candidate's
 * text, so an emoji candidate normally destroys the word the user typed to find
 * it. HeliBoard #2704 asks for the emoji to sit next to the word instead.
 */
class EmojiSuggestionAppendTest : FunSpec({

    val grinning = Emoji(value = "😀", name = "grinning face", keywords = listOf("grin", "smile"))

    test("without a preceding word the candidate still commits the emoji alone") {
        val candidate = EmojiSuggestionCandidate(emoji = grinning, showName = false)

        candidate.text shouldBe "😀"
    }

    test("a preceding word is kept in front of the emoji") {
        val candidate = EmojiSuggestionCandidate(
            emoji = grinning,
            showName = false,
            precedingText = "happy",
        )

        // Committing this replaces the composing region "happy" with
        // "happy😀" — the word survives.
        candidate.text shouldBe "happy😀"
    }

    test("the secondary label is unaffected by append mode") {
        val candidate = EmojiSuggestionCandidate(
            emoji = grinning,
            showName = true,
            precedingText = "happy",
        )

        candidate.secondaryText shouldBe "grinning face"
    }

    test("the emoji itself is still recoverable for history updates") {
        // notifySuggestionAccepted records candidate.emoji, not candidate.text,
        // so append mode must not leak the word into emoji history.
        val candidate = EmojiSuggestionCandidate(
            emoji = grinning,
            showName = false,
            precedingText = "happy",
        )

        candidate.emoji.value shouldBe "😀"
    }
})
