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

package dev.patrickgold.florisboard.ime.voice

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe

class VoiceCommandParserTest : FunSpec({
    val parser = VoiceCommandParser()

    test("detects built in v1 voice commands") {
        mapOf(
            "delete that" to VoiceCommandAction.DELETE_THAT,
            "undo" to VoiceCommandAction.UNDO,
            "redo" to VoiceCommandAction.REDO,
            "select all" to VoiceCommandAction.SELECT_ALL,
            "clear text" to VoiceCommandAction.CLEAR_TEXT,
            "new paragraph" to VoiceCommandAction.NEW_PARAGRAPH,
            "new line" to VoiceCommandAction.NEW_LINE,
            "capitalize next word" to VoiceCommandAction.CAPITALIZE_NEXT_WORD,
            "go to start" to VoiceCommandAction.GO_TO_START,
            "go to end" to VoiceCommandAction.GO_TO_END,
        ).forEach { (spokenText, action) ->
            parser.parse(spokenText)?.action shouldBe action
        }
    }

    test("normalizes punctuation casing courtesy words and diacritics") {
        val match = parser.parse("Please, CAPITALIZE next wórd.")

        match?.action shouldBe VoiceCommandAction.CAPITALIZE_NEXT_WORD
        match?.confidence shouldBe 1.0
    }

    test("uses aliases for natural variants") {
        parser.parse("line break")?.action shouldBe VoiceCommandAction.NEW_LINE
        parser.parse("select everything")?.action shouldBe VoiceCommandAction.SELECT_ALL
        parser.parse("delete all")?.action shouldBe VoiceCommandAction.CLEAR_TEXT
        parser.parse("go to beginning")?.action shouldBe VoiceCommandAction.GO_TO_START
    }

    test("tolerates likely recognition typos above default threshold") {
        val match = parser.parse("sellect all")

        match?.action shouldBe VoiceCommandAction.SELECT_ALL
        match?.confidence?.shouldBeGreaterThan(VoiceCommandParser.DEFAULT_MINIMUM_CONFIDENCE)
    }

    test("does not classify normal dictated text as a command") {
        parser.parse("delete the old message after lunch") shouldBe null
        parser.parse("new paragraph about keyboards") shouldBe null
        parser.parse("") shouldBe null
    }

    test("honors explicit confidence thresholds") {
        parser.parse("go to stat", minimumConfidence = 0.50)?.action shouldBe VoiceCommandAction.GO_TO_START
        parser.parse("go to stat", minimumConfidence = 0.95) shouldBe null
    }
})
