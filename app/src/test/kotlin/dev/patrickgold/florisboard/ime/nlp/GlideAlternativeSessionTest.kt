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

import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.editor.EditorRange
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class GlideAlternativeSessionTest : FunSpec({
    test("restores ranked alternatives when the cursor returns to an unchanged word") {
        val session = GlideAlternativeSession(timeoutMs = 1_000L)

        session.remember(
            committedText = "the",
            alternatives = listOf("tree", "three", "the"),
            range = EditorRange(4, 7),
            now = 100L,
        )
        session.onContentChanged(content("one the next", cursor = 0), now = 101L)
        session.activeCandidates(now = 101L) shouldBe emptyList()

        session.onContentChanged(content("one the next", cursor = 5), now = 102L)
        session.activeCandidates(now = 102L).map { it.text.toString() } shouldBe listOf("tree", "three")
    }

    test("retains only the five most recent committed ranges") {
        val text = "one two three four five six"
        val words = listOf(
            "one" to 0,
            "two" to 4,
            "three" to 8,
            "four" to 14,
            "five" to 19,
            "six" to 24,
        )
        val session = GlideAlternativeSession()
        words.forEach { (word, start) ->
            session.remember(
                committedText = word,
                alternatives = listOf("${word}x"),
                range = EditorRange(start, start + word.length),
                now = 100L,
            )
        }

        session.onContentChanged(content(text, cursor = 1), now = 101L)
        session.activeCandidates(now = 101L) shouldBe emptyList()
        session.onContentChanged(content(text, cursor = 25), now = 102L)
        session.activeCandidates(now = 102L).map { it.text.toString() } shouldBe listOf("sixx")
    }

    test("drops alternatives after an edit, privacy gate, or timeout") {
        val session = GlideAlternativeSession(timeoutMs = 1_000L)
        val range = EditorRange(0, 3)
        session.remember("the", listOf("tree"), range, now = 100L)

        session.onContentChanged(content("tge", cursor = 3), now = 101L)
        session.activeCandidates(now = 101L) shouldBe emptyList()

        session.remember("the", listOf("tree"), range, now = 200L)
        session.onContentChanged(content("the", cursor = 3), now = 201L, allowRetention = false)
        session.activeCandidates(now = 201L) shouldBe emptyList()

        session.remember("the", listOf("tree"), range, now = 300L)
        session.onContentChanged(content("the", cursor = 3), now = 1_300L)
        session.activeCandidates(now = 1_300L) shouldBe emptyList()
    }

    test("bounds and de-duplicates each ranked alternative list") {
        val session = GlideAlternativeSession(maxAlternativesPerCommit = 2)
        session.remember(
            committedText = "the",
            alternatives = listOf("tree", "tree", "three", "there"),
            range = EditorRange(0, 3),
            now = 100L,
        )

        session.activeCandidates(now = 101L).map { it.text.toString() } shouldBe listOf("tree", "three")
    }
})

private fun content(text: String, cursor: Int): EditorContent {
    return EditorContent(
        text = text,
        offset = 0,
        localSelection = EditorRange.cursor(cursor),
        localComposing = EditorRange.Unspecified,
        localCurrentWord = EditorRange.Unspecified,
    )
}
