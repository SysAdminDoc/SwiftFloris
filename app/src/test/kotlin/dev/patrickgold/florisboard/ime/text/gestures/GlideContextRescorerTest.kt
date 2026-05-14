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

package dev.patrickgold.florisboard.ime.text.gestures

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class GlideContextRescorerTest : FunSpec({
    test("following context can rescue an ambiguous short glide word") {
        val replacement = GlideContextRescorer.chooseReplacement(
            committedWord = "in",
            candidateWords = listOf("in", "I'm", "on"),
            nextWord = "going",
            contextScores = mapOf(
                "i'm" to 0.44,
                "in" to 0.0,
            ),
        )

        replacement shouldBe "I'm"
    }

    test("weak context does not override the top glide candidate") {
        val replacement = GlideContextRescorer.chooseReplacement(
            committedWord = "to",
            candidateWords = listOf("to", "go", "too"),
            nextWord = "the",
            contextScores = mapOf(
                "go" to 0.30,
                "to" to 0.22,
            ),
        )

        replacement shouldBe null
    }

    test("long words are not retroactively replaced by short-context rescoring") {
        val replacement = GlideContextRescorer.chooseReplacement(
            committedWord = "through",
            candidateWords = listOf("through", "though"),
            nextWord = "the",
            contextScores = mapOf("though" to 1.0),
        )

        replacement shouldBe null
    }
})
