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

import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.editor.EditorRange
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking

class FallbackNlpProviderTest : FunSpec({
    test("fallback provider exposes inert suggestion defaults") {
        runBlocking {
            FallbackNlpProvider.providerId shouldBe "org.florisboard.nlp.providers.fallback"
            FallbackNlpProvider.forcesSuggestionOn shouldBe false

            FallbackNlpProvider.create()
            FallbackNlpProvider.preload(Subtype.DEFAULT)

            FallbackNlpProvider.suggest(
                subtype = Subtype.DEFAULT,
                content = editorContent("hel"),
                maxCandidateCount = 3,
                allowPossiblyOffensive = true,
                isPrivateSession = false,
            ) shouldBe emptyList()
            FallbackNlpProvider.removeSuggestion(
                subtype = Subtype.DEFAULT,
                candidate = WordSuggestionCandidate("hello"),
            ) shouldBe false
            FallbackNlpProvider.getListOfWords(Subtype.DEFAULT) shouldBe emptyList()
            FallbackNlpProvider.getFrequencyForWord(Subtype.DEFAULT, "hello") shouldBe 0.0

            FallbackNlpProvider.destroy()
        }
    }
})

private fun editorContent(text: String): EditorContent {
    return EditorContent(
        text = text,
        offset = 0,
        localSelection = EditorRange.cursor(text.length),
        localComposing = EditorRange.Unspecified,
        localCurrentWord = EditorRange(0, text.length),
    )
}
