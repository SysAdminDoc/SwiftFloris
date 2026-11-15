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

package dev.patrickgold.florisboard.ime.media.emoji

import dev.patrickgold.florisboard.ime.nlp.EmojiSuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.InternalSuggestionOrder
import dev.patrickgold.florisboard.ime.nlp.WordSuggestionCandidate
import dev.patrickgold.florisboard.lib.FlorisLocale
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * Issue #24: typing `:pensive` offered the word "pensive" instead of 😔.
 * Emoji names only ship for a few languages and only the keyboard's own
 * language was searched, and the emoji that were found sat behind up to eight
 * word guesses in a row that shows three.
 */
class EmojiShortcodeSuggestionTest : FunSpec({

    fun bundledData(name: String): EmojiData {
        val file = listOf(
            File("src/main/assets/ime/media/emoji/$name"),
            File("app/src/main/assets/ime/media/emoji/$name"),
        ).first { it.exists() }
        return EmojiData.parseEmojiDataLines(file.readLines().asSequence())
    }

    fun bundled(name: String): List<Emoji> = bundledData(name).bySkinTone.getValue(EmojiSkinTone.DEFAULT)

    val noTags: (String) -> Collection<String> = { emptyList() }

    test("a shortcode typed on a keyboard language without emoji names still finds the emoji") {
        val english = bundled("en.txt")
        // A keyboard language with no bundled emoji names resolves to no data.
        val keyboardLanguage = emptyList<Emoji>()

        val onlyKeyboardLanguage = EmojiSuggestionRanker.rank(
            query = "pensive",
            sources = listOf(keyboardLanguage),
            customTagsFor = noTags,
            maxCount = 5,
        )
        val withEnglishFallback = EmojiSuggestionRanker.rank(
            query = "pensive",
            sources = listOf(keyboardLanguage, english),
            customTagsFor = noTags,
            maxCount = 5,
        )

        onlyKeyboardLanguage.shouldBeEmpty()
        withEnglishFallback.first().value shouldBe "😔"
    }

    test("the keyboard language's emoji is kept when English also matches it") {
        val localized = Emoji(value = "😔", name = "nachdenkliches gesicht", keywords = listOf("pensive"))
        val english = Emoji(value = "😔", name = "pensive face", keywords = listOf("pensive", "sad"))

        val ranked = EmojiSuggestionRanker.rank(
            query = "pensive",
            sources = listOf(listOf(localized), listOf(english)),
            customTagsFor = noTags,
            maxCount = 5,
        )

        ranked shouldContainExactly listOf(localized)
    }

    test("a custom tag still finds its emoji") {
        val butterfly = Emoji(value = "🦋", name = "butterfly", keywords = listOf("insect"))

        EmojiSuggestionRanker.rank(
            query = "freedom",
            sources = listOf(listOf(butterfly)),
            customTagsFor = { value -> if (value == "🦋") listOf("freedom") else emptyList() },
            maxCount = 5,
        ) shouldContainExactly listOf(butterfly)
    }

    test("a shortcode query puts the emoji in front of the word guesses") {
        val words = listOf("pensive", "pensively", "pension").map { WordSuggestionCandidate(text = it) }
        val pensive = EmojiSuggestionCandidate(
            emoji = Emoji(value = "😔", name = "pensive face", keywords = listOf("pensive")),
            showName = false,
        )

        val merged = InternalSuggestionOrder.merge(
            wordSuggestions = words,
            emojiSuggestions = listOf(pensive),
            ghostTextCandidate = null,
            composingText = ":pensive",
            emojiTriggerPrefix = ":",
        )

        // The CLASSIC candidate row shows the first three.
        merged.take(3).first() shouldBe pensive
    }

    test("the emoji panel searches English names after the keyboard's own languages") {
        val dutch = FlorisLocale.from("nl")
        val german = FlorisLocale.from("de")

        EmojiSearch.searchLocales(listOf(dutch, german, dutch)).map { it.language } shouldContainExactly
            listOf("nl", "de", "en")
        EmojiSearch.searchLocales(listOf(FlorisLocale.ENGLISH, dutch)).map { it.language } shouldContainExactly
            listOf("en", "nl")

        val many = listOf("nl", "de", "fr", "it", "es", "pt", "pl", "sv", "da").map { FlorisLocale.from(it) }
        val limited = EmojiSearch.searchLocales(many)
        limited.size shouldBe EmojiSearch.MaxLocaleMappings
        limited.last().language shouldBe "en"
    }

    test("the tag sheet shows the names an emoji already has") {
        // The palette's own layout data carries no names, only the language files do.
        val root = bundledData("root.txt").byCategory
        val english = bundledData("en.txt").byCategory

        EmojiSearch.builtInLabels(listOf(root), "😔").shouldBeEmpty()

        val labels = EmojiSearch.builtInLabels(listOf(root, english), "😔")
        labels.first() shouldBe "pensive face"
        labels shouldContain "dejected"
        labels shouldNotContain "pensive"
        labels.size shouldBe 6
    }

    test("plain words keep their guesses first") {
        val words = listOf("happy", "happen").map { WordSuggestionCandidate(text = it) }
        val grin = EmojiSuggestionCandidate(
            emoji = Emoji(value = "😀", name = "grinning face", keywords = listOf("happy")),
            showName = false,
        )

        InternalSuggestionOrder.merge(
            wordSuggestions = words,
            emojiSuggestions = listOf(grin),
            ghostTextCandidate = null,
            composingText = "happy",
            emojiTriggerPrefix = "",
        ) shouldContainExactly words + grin
    }
})
