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

package dev.patrickgold.florisboard.ime.editor

import dev.patrickgold.florisboard.ime.nlp.WordSuggestionCandidate
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class EditorSpacingLifecycleStateTest : FunSpec({
    test("auto-space remains active for one editor update then expires") {
        val autoSpace = EditorInstance.AutoSpaceState()

        autoSpace.setActive()

        autoSpace.isActive shouldBe true

        autoSpace.setInactiveFromUpdate()
        autoSpace.isActive shouldBe true

        autoSpace.setInactiveFromUpdate()
        autoSpace.isInactive shouldBe true
    }

    test("auto-space without update grace expires on the next editor update") {
        val autoSpace = EditorInstance.AutoSpaceState()

        autoSpace.setActive(stayActiveNextUpdate = false)

        autoSpace.isActive shouldBe true

        autoSpace.setInactiveFromUpdate()
        autoSpace.isInactive shouldBe true
    }

    test("phantom space keeps candidate and composing visibility through first editor update") {
        val phantomSpace = EditorInstance.PhantomSpaceState()
        val candidate = WordSuggestionCandidate("hello", confidence = 1.0)

        phantomSpace.setActive(showComposingRegion = true, candidate = candidate)

        phantomSpace.isActive shouldBe true
        phantomSpace.showComposingRegion shouldBe true
        phantomSpace.candidateForRevert shouldBe candidate

        phantomSpace.setInactiveFromUpdate()

        phantomSpace.isActive shouldBe true
        phantomSpace.showComposingRegion shouldBe true
        phantomSpace.candidateForRevert shouldBe candidate
    }

    test("phantom space clears candidate after an unprotected editor update") {
        val phantomSpace = EditorInstance.PhantomSpaceState()
        val candidate = WordSuggestionCandidate("hello", confidence = 1.0)

        phantomSpace.setActive(showComposingRegion = false, candidate = candidate)
        phantomSpace.setInactiveFromUpdate()
        phantomSpace.setInactiveFromUpdate()

        phantomSpace.isInactive shouldBe true
        phantomSpace.showComposingRegion shouldBe false
        phantomSpace.candidateForRevert shouldBe null
    }

    test("phantom space explicit inactive clears composing visibility and candidate") {
        val phantomSpace = EditorInstance.PhantomSpaceState()

        phantomSpace.setActive(
            showComposingRegion = true,
            stayActiveNextUpdate = false,
            candidate = WordSuggestionCandidate("hello", confidence = 1.0),
        )

        phantomSpace.setInactive()

        phantomSpace.isInactive shouldBe true
        phantomSpace.showComposingRegion shouldBe false
        phantomSpace.candidateForRevert shouldBe null
    }
})
