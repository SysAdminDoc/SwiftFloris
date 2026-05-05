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

package dev.patrickgold.florisboard.ime.dictionary

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DictionaryManagerTest : FunSpec({
    test("rankUserDictionaryCandidates prefers internal entries over system duplicates") {
        val ranked = rankUserDictionaryCandidates(
            query = "swi",
            candidates = listOf(
                candidate("SwiftFloris", freq = 255, sourcePriority = 1, matchPriority = 1),
                candidate("SwiftFloris", freq = 128, sourcePriority = 0, matchPriority = 1),
                candidate("swiftly", freq = 220, sourcePriority = 1, matchPriority = 1),
            ),
        )

        ranked.map { it.word } shouldBe listOf("SwiftFloris", "swiftly")
        ranked.first().freq shouldBe 128
    }

    test("rankUserDictionaryCandidates puts shortcut expansions ahead of word completions") {
        val ranked = rankUserDictionaryCandidates(
            query = "omw",
            candidates = listOf(
                candidate("omelet", freq = 255, sourcePriority = 0, matchPriority = 1),
                candidate("On my way", freq = 128, shortcut = "omw", sourcePriority = 0, matchPriority = 0),
            ),
        )

        ranked.map { it.word } shouldBe listOf("On my way", "omelet")
    }

    test("rankUserDictionaryCandidates removes exact current word from suggestion candidates") {
        val ranked = rankUserDictionaryCandidates(
            query = "SwiftFloris",
            candidates = listOf(
                candidate("SwiftFloris", freq = 255, sourcePriority = 0, matchPriority = 1),
                candidate("SwiftFloris keyboard", freq = 128, sourcePriority = 0, matchPriority = 1),
            ),
        )

        ranked.map { it.word } shouldBe listOf("SwiftFloris keyboard")
    }
})

private fun candidate(
    word: String,
    freq: Int,
    shortcut: String? = null,
    sourcePriority: Int,
    matchPriority: Int,
): UserDictionaryCandidate {
    return UserDictionaryCandidate(
        entry = UserDictionaryEntry(
            id = 0,
            word = word,
            freq = freq,
            locale = null,
            shortcut = shortcut,
        ),
        sourcePriority = sourcePriority,
        matchPriority = matchPriority,
    )
}
