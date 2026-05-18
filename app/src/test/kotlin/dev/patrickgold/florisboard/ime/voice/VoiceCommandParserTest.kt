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

    // ROADMAP §6 N15.3 — Smart Edit voice REMOVE_ITEM_FROM_LIST parser tests.

    test("parses 'no longer want X' as REMOVE_ITEM_FROM_LIST with the noun as argument") {
        val match = parser.parse("no longer want apples")

        match?.action shouldBe VoiceCommandAction.REMOVE_ITEM_FROM_LIST
        match?.argument shouldBe "apples"
        match?.confidence shouldBe 1.0
    }

    test("preserves the original casing of the argument") {
        val match = parser.parse("no longer want Apples")

        match?.argument shouldBe "Apples"
    }

    test("parses 'remove X from the list' and 'remove X from list' variants") {
        parser.parse("remove apples from the list")?.argument shouldBe "apples"
        parser.parse("remove apples from list")?.argument shouldBe "apples"
        parser.parse("delete apples from the list")?.argument shouldBe "apples"
    }

    test("parses 'scratch X from list' / 'scratch X off list' variants") {
        // v1.8.107 dropped the bare-prefix "scratch <X>" pattern because it
        // silently triggered REMOVE_ITEM_FROM_LIST on natural-prose
        // utterances starting with "scratch" ("let me scratch that idea").
        // The disambiguated forms with an explicit suffix keep the
        // shopping-list UX.
        parser.parse("scratch apples from list")?.argument shouldBe "apples"
        parser.parse("scratch apples from the list")?.argument shouldBe "apples"
        parser.parse("scratch apples off list")?.argument shouldBe "apples"
        parser.parse("scratch apples off the list")?.argument shouldBe "apples"
        parser.parse("scratch almond butter from list")?.argument shouldBe "almond butter"
    }

    test("bare 'scratch X' (no suffix) no longer triggers removal") {
        // Regression guard for v1.8.107. The bare prefix used to fire on
        // any utterance starting with "scratch", which silently excised
        // text from the committed buffer when the user was just
        // dictating prose. With the suffix now required, these all
        // return null.
        parser.parse("scratch apples") shouldBe null
        parser.parse("scratch that idea") shouldBe null
        parser.parse("scratch the previous note") shouldBe null
    }

    test("does not classify 'remove that' as a REMOVE_ITEM_FROM_LIST (DELETE_THAT alias wins)") {
        // "remove that" is an alias for DELETE_THAT and must keep that
        // shape so the existing built-in command surface stays
        // unaffected by the new parameterised path.
        val match = parser.parse("remove that")

        match?.action shouldBe VoiceCommandAction.DELETE_THAT
        match?.argument shouldBe null
    }

    test("rejects parameterised patterns with stopword-only arguments") {
        // "scratch the" / "remove the from the list" must NOT excise
        // the whole list; the argument is rejected upstream.
        parser.parse("scratch the") shouldBe null
        parser.parse("remove the from the list") shouldBe null
    }

    test("rejects 'scratch' on its own with no item") {
        // "scratch" with no argument is a legitimate utterance the user
        // might say; the command must not fire because we have no item
        // to remove.
        parser.parse("scratch") shouldBe null
    }

    test("does not fire on long ambient utterances that happen to contain the pattern") {
        // Plain dictation ("delete the old message after lunch") must
        // NOT be classified as REMOVE_ITEM_FROM_LIST just because it
        // starts with "delete" — the suffix anchor protects against
        // false positives.
        parser.parse("delete the old message after lunch") shouldBe null
    }
})
