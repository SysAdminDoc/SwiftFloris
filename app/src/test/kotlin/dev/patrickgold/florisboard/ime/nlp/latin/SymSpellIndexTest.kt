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

package dev.patrickgold.florisboard.ime.nlp.latin

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe

class SymSpellIndexTest : FunSpec({
    test("distance-two build stops before crossing delete-entry budget") {
        val index = SymSpellIndex.build(
            words = listOf("received", "tomorrow", "separate", "address"),
            maxDistance = 2,
            maxDeleteEntries = 40,
        )

        index.isComplete shouldBe false
        index.indexedWordCount shouldBe 1
        (index.entryCount() <= 40) shouldBe true
        index.candidates("recved") shouldContain "received"
        index.candidates("tomorow") shouldNotContain "tomorrow"
    }

    test("unlimited budget keeps existing complete-index behavior") {
        val index = SymSpellIndex.build(
            words = listOf("received", "tomorrow", "separate", "address"),
            maxDistance = 2,
        )

        index.isComplete shouldBe true
        index.indexedWordCount shouldBe 4
        index.candidates("recved") shouldContain "received"
        index.candidates("tomorow") shouldContain "tomorrow"
    }
})
