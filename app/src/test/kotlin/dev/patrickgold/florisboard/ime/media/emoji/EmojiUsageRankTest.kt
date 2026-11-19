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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File

/**
 * Every English emoji with "good" among its keywords scored the same, and ties kept the palette order, so
 * ":good" offered 🫡 😪 😴 and 👍 came sixth. Ties now go by Unicode's usage counts (emoji-freq.txt in
 * unicodetools, where 👍 is 18th, 👏 36th and 😴 38th).
 */
class EmojiUsageRankTest : FunSpec({

    fun mainAsset(path: String): File = listOf(
        File("src/main/assets/$path"),
        File("app/src/main/assets/$path"),
    ).first { it.exists() }

    val english = EmojiData.parseEmojiDataLines(mainAsset("ime/media/emoji/en.txt").readLines().asSequence())
    val bundledRank = EmojiUsageRank.parse(mainAsset(EmojiUsageRank.ASSET_PATH).readLines().asSequence())
    val noTags: (String) -> Collection<String> = { emptyList() }

    fun suggest(query: String, tone: EmojiSkinTone = EmojiSkinTone.DEFAULT, rank: Map<String, Int> = bundledRank) =
        EmojiSuggestionRanker.rank(
            query = query,
            sources = listOf(english.bySkinTone.getValue(tone)),
            customTagsFor = noTags,
            maxCount = 3,
            usageRank = rank,
        ).map { it.value }

    test("\":good\" offers the most used emoji that list it, thumbs up first") {
        suggest("good") shouldContainExactly listOf("👍", "👏", "😴")
    }

    test("a skin tone keeps its emoji's rank") {
        suggest("good", EmojiSkinTone.MEDIUM_DARK_SKIN_TONE) shouldContainExactly listOf("👍🏾", "👏🏾", "😴")
    }

    test("a name match still beats a more used emoji that only matches a keyword") {
        suggest("thumb").first() shouldBe "👍"
        // 😼 "cat with wry smile" is the only name hit, so it stays ahead of ☺️ and 😍 (ranks 2 and 5).
        suggest("smile") shouldContainExactly listOf("😼", "☺️", "😍")
    }

    test("ties the usage rank doesn't cover keep the palette order") {
        val first = Emoji(value = "a", name = "first", keywords = listOf("tie"))
        val second = Emoji(value = "b", name = "second", keywords = listOf("tie"))
        val ranked = Emoji(value = "c", name = "third", keywords = listOf("tie"))

        EmojiSuggestionRanker.rank(
            query = "tie",
            sources = listOf(listOf(first, second, ranked)),
            customTagsFor = noTags,
            maxCount = 3,
            usageRank = mapOf("c" to 0),
        ) shouldContainExactly listOf(ranked, first, second)
    }

    test("the rank file gives every form on a line one rank and doesn't read the keycap # as a comment") {
        val keycap = "#" + Char(0xFE0F) + Char(0x20E3)
        val rank = EmojiUsageRank.parse(
            sequenceOf(
                "# EMOJI-VERSION: 18.0",
                "",
                "😂",
                "👍 👍🏻 👍🏿",
                keycap,
                "😂",
            ),
        )

        rank shouldBe mapOf("😂" to 0, "👍" to 1, "👍🏻" to 1, "👍🏿" to 1, keycap to 2)
    }

    test("every emoji in the bundled rank file is one the palette has") {
        val palette = EmojiData.parseEmojiDataLines(mainAsset("ime/media/emoji/root.txt").readLines().asSequence())
            .byCategory.values.flatten().flatMap { set -> set.emojis.map { it.value } }.toSet()

        (bundledRank.keys - palette) shouldBe emptySet()
        bundledRank["👍"] shouldNotBe null
    }
})
