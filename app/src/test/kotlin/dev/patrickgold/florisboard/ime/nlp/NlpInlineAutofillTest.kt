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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File

class NlpInlineAutofillTest : FunSpec({
    test("inline suggestion presentation bounds never use zero or unbounded dimensions") {
        InlineSuggestionSizePolicy.presentationMinDimensions.widthPx shouldBe 1
        InlineSuggestionSizePolicy.presentationMinDimensions.heightPx shouldBe 1

        val size = InlineSuggestionSizePolicy.presentationMaxDimensions(
            displayWidthPx = Int.MAX_VALUE,
            chipHeightPx = Int.MAX_VALUE,
        )

        size.widthPx shouldBe 4096
        size.heightPx shouldBe 512
        InlineSuggestionSizePolicy.isValidInlineDimensions(size) shouldBe true
    }

    test("inline suggestion inflate size replaces invalid runtime dimensions with stable fallbacks") {
        val size = InlineSuggestionSizePolicy.inflateSize(
            displayWidthPx = 0,
            chipHeightPx = -1,
        )

        size.widthPx shouldBe 320
        size.heightPx shouldBe 48
        InlineSuggestionSizePolicy.isValidInlineDimensions(size) shouldBe true
    }

    test("inline suggestion inflate size preserves valid keyboard dimensions") {
        val size = InlineSuggestionSizePolicy.inflateSize(
            displayWidthPx = 1080,
            chipHeightPx = 56,
        )

        size.widthPx shouldBe 1080
        size.heightPx shouldBe 56
        InlineSuggestionSizePolicy.isValidInlineDimensions(size) shouldBe true
    }

    test("inline suggestion inflation drops host runtime failures instead of crashing") {
        val source = locateProjectFile(
            "app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/NlpInlineAutofill.kt",
            "src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/NlpInlineAutofill.kt",
        ).readText()

        source shouldContain "rawSuggestion.inflate"
        source shouldContain "catch (e: RuntimeException)"
        source shouldContain "dropping invalid inline suggestion"
        source shouldContain "latch.countDown()"
        source shouldNotContain "ViewGroup.LayoutParams.WRAP_CONTENT"
    }
})

private fun locateProjectFile(vararg candidates: String): File {
    return candidates
        .map { File(it) }
        .firstOrNull { it.exists() }
        ?: error("Unable to locate any of: ${candidates.joinToString()}")
}
