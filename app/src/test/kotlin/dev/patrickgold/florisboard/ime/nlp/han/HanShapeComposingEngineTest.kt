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

package dev.patrickgold.florisboard.ime.nlp.han

import dev.patrickgold.florisboard.ime.editor.EditorRange
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class HanShapeComposingEngineTest : FunSpec({
    val defaultKeys = "abcdefghijklmnopqrstuvwxyz".toSet()

    test("determineLocalComposing returns the trailing shape-code range") {
        HanShapeComposingEngine.determineLocalComposing(
            textBeforeSelection = "你好abcd",
            keyCodeLocale = defaultKeys,
            localLastCommitPosition = 0,
        ) shouldBe EditorRange(2, 6)
    }

    test("determineLocalComposing stops at the last committed boundary") {
        HanShapeComposingEngine.determineLocalComposing(
            textBeforeSelection = "abcdef",
            keyCodeLocale = defaultKeys,
            localLastCommitPosition = 3,
        ) shouldBe EditorRange(3, 6)
    }

    test("determineLocalComposing returns unspecified when the cursor is not after a shape code") {
        HanShapeComposingEngine.determineLocalComposing(
            textBeforeSelection = "abcd!",
            keyCodeLocale = defaultKeys,
            localLastCommitPosition = 0,
        ) shouldBe EditorRange.Unspecified
    }

    test("determineLocalComposing respects locale-specific key codes") {
        HanShapeComposingEngine.determineLocalComposing(
            textBeforeSelection = "abc123",
            keyCodeLocale = "12345".toSet(),
            localLastCommitPosition = 0,
        ) shouldBe EditorRange(3, 6)
    }
})
