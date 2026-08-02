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
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.util.Locale

/**
 * ASCII-only normalisation erased every non-Latin utterance before matching, so a Cyrillic,
 * Arabic or CJK command collapsed to an empty string and never fired. These cases pin the
 * Unicode-safe pipeline: NFC equivalence, script-scoped diacritic folding, locale-aware casing
 * and grapheme-based edit distance.
 */
class VoiceCommandParserUnicodeTest : FunSpec({
    val parser = VoiceCommandParser()

    fun command(action: VoiceCommandAction, phrase: String, vararg aliases: String) =
        VoiceCommandDefinition(action = action, canonicalPhrase = phrase, aliases = aliases.toList())

    test("Cyrillic commands survive normalization") {
        val commands = listOf(command(VoiceCommandAction.UNDO, "отменить"))

        parser.normalizeForMatching("Отменить") shouldBe "отменить"
        parser.parse("Отменить.", additionalCommands = commands)?.action shouldBe VoiceCommandAction.UNDO
    }

    test("Arabic commands match with and without harakat") {
        val commands = listOf(command(VoiceCommandAction.DELETE_THAT, "احذف"))

        parser.parse("احذف", additionalCommands = commands)?.action shouldBe VoiceCommandAction.DELETE_THAT
        // Same word carrying optional vowel marks the recogniser may or may not emit.
        parser.parse("اِحْذِف", additionalCommands = commands)?.action shouldBe VoiceCommandAction.DELETE_THAT
    }

    test("CJK commands match despite the absence of word spaces and with full-width punctuation") {
        val commands = listOf(command(VoiceCommandAction.SELECT_ALL, "全选"))

        parser.parse("全选", additionalCommands = commands)?.action shouldBe VoiceCommandAction.SELECT_ALL
        parser.parse("全选。", additionalCommands = commands)?.action shouldBe VoiceCommandAction.SELECT_ALL
    }

    test("Turkish casing cannot break Latin command phrases") {
        val turkishParser = VoiceCommandParser(matchingLocale = Locale.forLanguageTag("tr"))

        // "I".lowercase(tr) is the dotless "ı"; without the fold this became "delete ıt".
        turkishParser.parse("DELETE IT")?.action shouldBe VoiceCommandAction.DELETE_THAT
        turkishParser.parse("SELECT ALL")?.action shouldBe VoiceCommandAction.SELECT_ALL
    }

    test("Turkish commands normalize dotted and dotless I to one key") {
        val turkishParser = VoiceCommandParser(matchingLocale = Locale.forLanguageTag("tr"))
        val commands = listOf(command(VoiceCommandAction.CLEAR_TEXT, "sil"))

        turkishParser.parse("SİL", additionalCommands = commands)?.action shouldBe VoiceCommandAction.CLEAR_TEXT
        turkishParser.normalizeForMatching("SİL") shouldBe "sil"
    }

    test("decomposed and precomposed spellings normalize to the same key") {
        val precomposed = parser.normalizeForMatching("wórd")
        val decomposed = parser.normalizeForMatching("wórd")

        precomposed shouldBe "word"
        decomposed shouldBe precomposed
    }

    test("marks that carry meaning are preserved outside Latin, Greek, Cyrillic, Arabic and Hebrew") {
        // Devanagari matras change the word; stripping them would merge unrelated commands.
        parser.normalizeForMatching("हिंदी") shouldBe "हिंदी"
        parser.normalizeForMatching("ไทย") shouldBe "ไทย"
    }

    test("emoji separate tokens instead of matching and count as a single edit") {
        parser.parse("select all 🎉")?.action shouldBe VoiceCommandAction.SELECT_ALL
        parser.normalizeForMatching("select 🎉 all") shouldBe "select all"
        // A ZWJ sequence is one grapheme cluster, so it costs one edit rather than five.
        parser.graphemes("👨‍👩‍👧").size shouldBe 1
    }

    test("punctuation stays deterministic around commands") {
        parser.parse("undo!!!")?.action shouldBe VoiceCommandAction.UNDO
        parser.parse("¿undo?")?.action shouldBe VoiceCommandAction.UNDO
        parser.normalizeForMatching("new paragraph") shouldBe "new paragraph"
        parser.normalizeForMatching("new　paragraph") shouldBe "new paragraph"
    }

    test("parameterised removal keeps non-Latin arguments intact") {
        val match = parser.parse("remove яблоки from the list")

        match?.action shouldBe VoiceCommandAction.REMOVE_ITEM_FROM_LIST
        match?.argument shouldBe "яблоки"
    }

    test("non-Latin utterances no longer normalize to an empty key") {
        listOf("отменить", "احذف", "全选", "हिंदी").forEach { utterance ->
            parser.normalizeForMatching(utterance) shouldNotBe ""
        }
    }

    test("unrelated non-Latin dictation is still not classified as a command") {
        parser.parse("сегодня хорошая погода на улице") shouldBe null
    }
})
