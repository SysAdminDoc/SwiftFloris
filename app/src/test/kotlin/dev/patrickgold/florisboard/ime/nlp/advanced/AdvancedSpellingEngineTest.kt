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

package dev.patrickgold.florisboard.ime.nlp.advanced

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.File

class AdvancedSpellingEngineTest : FunSpec({
    val dictionary = setOf(
        "keyboard",
        "keyboards",
        "key",
        "swift",
        "shift",
        "offline",
    )

    test("levenshteinDistance handles insertion deletion substitution and equality") {
        AdvancedSpellingEngine.levenshteinDistance("swift", "swift") shouldBe 0
        AdvancedSpellingEngine.levenshteinDistance("swift", "shift") shouldBe 1
        AdvancedSpellingEngine.levenshteinDistance("keybord", "keyboard") shouldBe 1
        AdvancedSpellingEngine.levenshteinDistance("", "key") shouldBe 3
    }

    test("generateCorrections returns deterministic edit-distance ranked suggestions") {
        AdvancedSpellingEngine.generateCorrections("keybord", dictionary, maxCount = 3) shouldBe
            listOf("keyboard", "keyboards")
    }

    test("generateCorrections filters distant words and respects max count") {
        AdvancedSpellingEngine.generateCorrections("swft", dictionary, maxCount = 1) shouldBe listOf("swift")
        AdvancedSpellingEngine.generateCorrections("zzzzzz", dictionary, maxCount = 3) shouldBe emptyList()
    }

    test("bundled English word list has production-size autocorrect coverage") {
        val words = bundledEnglishDictionary().readLines()

        (words.size >= 45_000) shouldBe true
        setOf("autocorrect", "because", "dictionary", "keyboard", "conversation").all { it in words } shouldBe true
    }
})

private fun bundledEnglishDictionary(): File {
    return listOf(
        File("src/main/assets/dictionaries/en.txt"),
        File("app/src/main/assets/dictionaries/en.txt"),
    ).first { it.isFile }
}
