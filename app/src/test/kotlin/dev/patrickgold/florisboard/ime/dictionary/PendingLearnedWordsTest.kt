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
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import java.io.File
import java.nio.file.Files

class PendingLearnedWordsTest : FunSpec({
    fun newFile(): File = File(Files.createTempDirectory("pending-learn").toFile(), PendingLearnedWords.FILE_NAME)

    test("a word is pending after its first sighting and learned on the second") {
        val pending = PendingLearnedWords(newFile())

        pending.recordSighting("thw", "en-US").shouldBeFalse()
        pending.contains("thw", "en-US").shouldBeTrue()

        pending.recordSighting("thw", "en-US").shouldBeTrue()
        pending.contains("thw", "en-US").shouldBeFalse()
        // Learned words leave the list, so a third sighting starts over.
        pending.recordSighting("thw", "en-US").shouldBeFalse()
    }

    test("sightings match case-insensitively and stay apart per language") {
        val pending = PendingLearnedWords(newFile())

        pending.recordSighting("Zorblat", "en-US").shouldBeFalse()
        pending.recordSighting("zorblat", "de-DE").shouldBeFalse()
        pending.recordSighting("ZORBLAT", "en-US").shouldBeTrue()
        pending.contains("zorblat", "de-DE").shouldBeTrue()
    }

    test("remove takes a word off without learning it") {
        val pending = PendingLearnedWords(newFile())
        pending.recordSighting("thw", "en-US")

        pending.remove("thw", "en-US").shouldBeTrue()
        pending.remove("thw", "en-US").shouldBeFalse()
        pending.recordSighting("thw", "en-US").shouldBeFalse()
    }

    test("the oldest pending word is dropped past the cap") {
        val pending = PendingLearnedWords(newFile(), maxEntries = 3)
        listOf("aaa", "bbb", "ccc", "ddd").forEach { pending.recordSighting(it, "en-US") }

        pending.size() shouldBe 3
        pending.contains("aaa", "en-US").shouldBeFalse()
        pending.contains("ddd", "en-US").shouldBeTrue()
    }

    test("the pending list survives a restart") {
        val file = newFile()
        val before = PendingLearnedWords(file)
        before.recordSighting("thw", "en-US")
        before.recordSighting("zorblat", "de-DE")
        before.save().shouldBeTrue()

        val after = PendingLearnedWords(file)
        after.contains("zorblat", "de-DE").shouldBeTrue()
        // The second sighting after the restart learns it.
        after.recordSighting("thw", "en-US").shouldBeTrue()
    }

    test("a restart keeps the newest words when the file holds more than the cap") {
        val file = newFile()
        val big = PendingLearnedWords(file, maxEntries = 10)
        listOf("aaa", "bbb", "ccc", "ddd").forEach { big.recordSighting(it, "en-US") }
        big.save().shouldBeTrue()

        val small = PendingLearnedWords(file, maxEntries = 2)
        small.size() shouldBe 2
        small.contains("ccc", "en-US").shouldBeTrue()
        small.contains("ddd", "en-US").shouldBeTrue()
    }

    test("damaged lines in the file are skipped") {
        val file = newFile()
        file.writeText("en-US\tthw\nno tab here\n\tmissingtag\nen-US\t\n\nde-DE\tzorblat\n")

        val pending = PendingLearnedWords(file)
        pending.size() shouldBe 2
        pending.contains("thw", "en-US").shouldBeTrue()
        pending.contains("zorblat", "de-DE").shouldBeTrue()
    }

    test("deleteFile empties the list and removes the saved copy") {
        val file = newFile()
        val pending = PendingLearnedWords(file)
        pending.recordSighting("thw", "en-US")
        pending.save().shouldBeTrue()

        pending.deleteFile().shouldBeTrue()
        file.exists().shouldBeFalse()
        pending.size() shouldBe 0
        PendingLearnedWords(file).size() shouldBe 0
    }

    test("a new word typed once waits, and typed twice is learned") {
        val pending = PendingLearnedWords(newFile())

        pending.admit("thw", "en-US", requireRepeat = true, alreadyLearned = false) shouldBe
            PendingLearnedWords.Admission(learnNow = false, listChanged = true)
        pending.admit("thw", "en-US", requireRepeat = true, alreadyLearned = false) shouldBe
            PendingLearnedWords.Admission(learnNow = true, listChanged = true)
        pending.size() shouldBe 0
    }

    test("a word already learned is reinforced right away and never noted") {
        val pending = PendingLearnedWords(newFile())

        pending.admit("swift", "en-US", requireRepeat = true, alreadyLearned = true) shouldBe
            PendingLearnedWords.Admission(learnNow = true, listChanged = false)
        pending.size() shouldBe 0
    }

    test("a word picked from the strip is learned at once and clears its sighting") {
        val pending = PendingLearnedWords(newFile())
        pending.admit("zorblat", "en-US", requireRepeat = true, alreadyLearned = false)

        pending.admit("zorblat", "en-US", requireRepeat = false, alreadyLearned = false) shouldBe
            PendingLearnedWords.Admission(learnNow = true, listChanged = true)
        pending.contains("zorblat", "en-US").shouldBeFalse()
        pending.admit("other", "en-US", requireRepeat = false, alreadyLearned = false) shouldBe
            PendingLearnedWords.Admission(learnNow = true, listChanged = false)
    }

    test("backspacing a correction, then typing the word again, learns it") {
        val pending = PendingLearnedWords(newFile())
        // The backspace that takes back "thw" -> "the" is the first sighting...
        pending.addSighting("thw", "en-US").shouldBeTrue()
        // ...and the space that keeps the retyped "thw" is the second.
        pending.admit("thw", "en-US", requireRepeat = true, alreadyLearned = false).learnNow.shouldBeTrue()
    }

    test("a sighting noted for a word already pending leaves it pending") {
        val pending = PendingLearnedWords(newFile())
        pending.admit("thw", "en-US", requireRepeat = true, alreadyLearned = false)

        // A backspace into a word seen once before must not count as the learning sighting.
        pending.addSighting("thw", "en-US").shouldBeFalse()
        pending.contains("thw", "en-US").shouldBeTrue()
        pending.admit("thw", "en-US", requireRepeat = true, alreadyLearned = false).learnNow.shouldBeTrue()
    }

    test("a noted sighting keeps to the cap too") {
        val pending = PendingLearnedWords(newFile(), maxEntries = 2)
        pending.addSighting("one", "en-US")
        pending.addSighting("two", "en-US")
        pending.addSighting("three", "en-US")

        pending.size() shouldBe 2
        pending.contains("one", "en-US").shouldBeFalse()
    }

    test("deleting the file also deletes a half-written copy") {
        val file = newFile()
        val pending = PendingLearnedWords(file)
        pending.addSighting("thw", "en-US")
        pending.save().shouldBeTrue()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText("en-US\tstale\n")

        pending.deleteFile().shouldBeTrue()

        file.exists().shouldBeFalse()
        tmp.exists().shouldBeFalse()
        PendingLearnedWords(file).contains("thw", "en-US").shouldBeFalse()
    }

    test("a sighting before a restart counts toward learning after it") {
        val file = newFile()
        val before = PendingLearnedWords(file)
        before.admit("thw", "en-US", requireRepeat = true, alreadyLearned = false)
        before.save().shouldBeTrue()

        PendingLearnedWords(file).admit("thw", "en-US", requireRepeat = true, alreadyLearned = false)
            .learnNow.shouldBeTrue()
    }
})
