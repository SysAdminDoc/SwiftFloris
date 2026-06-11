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

class AutoCommitUndoSessionTest : FunSpec({
    test("shows undo candidate immediately after an accepted correction") {
        val session = AutoCommitUndoSession()

        session.remember(
            originalText = "teh",
            correctedText = "the",
            wordStart = 0,
            sourceProvider = null,
        )
        session.onContentChanged(content(text = "the ", cursor = 4))

        session.activeUndoCandidate?.text shouldBe "teh"
        session.activeUndoCandidate?.secondaryText shouldBe "the"
    }

    test("shows undo candidate when cursor enters corrected word") {
        val session = AutoCommitUndoSession()

        session.remember(
            originalText = "teh",
            correctedText = "the",
            wordStart = 6,
            sourceProvider = null,
        )
        session.onContentChanged(content(text = "Well, the next", cursor = 8))

        session.activeUndoCandidate?.text shouldBe "teh"
    }

    test("hides undo candidate away from corrected word") {
        val session = AutoCommitUndoSession()

        session.remember(
            originalText = "teh",
            correctedText = "the",
            wordStart = 0,
            sourceProvider = null,
        )
        session.onContentChanged(content(text = "the next", cursor = 8))

        session.activeUndoCandidate shouldBe null
    }

    test("expires correction when corrected text no longer matches range") {
        val session = AutoCommitUndoSession()

        session.remember(
            originalText = "teh",
            correctedText = "the",
            wordStart = 0,
            sourceProvider = null,
        )
        session.onContentChanged(content(text = "tge ", cursor = 3))

        session.activeUndoCandidate shouldBe null
    }

    test("retains only most recent correction ranges") {
        val session = AutoCommitUndoSession(maxCorrections = 2)

        session.remember("onee", "one", wordStart = 0, sourceProvider = null)
        session.remember("twoo", "two", wordStart = 4, sourceProvider = null)
        session.remember("threee", "three", wordStart = 8, sourceProvider = null)
        session.onContentChanged(content(text = "one two three", cursor = 1))

        session.activeUndoCandidate shouldBe null

        session.onContentChanged(content(text = "one two three", cursor = 5))
        session.activeUndoCandidate?.text shouldBe "twoo"
    }

    test("consume removes active correction") {
        val session = AutoCommitUndoSession()

        session.remember("teh", "the", wordStart = 0, sourceProvider = null)
        session.onContentChanged(content(text = "the ", cursor = 4))
        val candidate = session.activeUndoCandidate!!

        session.consume(candidate)?.original shouldBe "teh"
        session.onContentChanged(content(text = "the ", cursor = 4))

        session.activeUndoCandidate shouldBe null
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
