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

class EmojiSearchTest : FunSpec({
    test("search matches emoji names and keywords case-insensitively") {
        val smile = emojiSet("😀", "grinning face", "smile", "happy")
        val thumbs = emojiSet("👍", "thumbs up", "yes", "approve")
        val mappings = emojiMappings(listOf(smile, thumbs))

        EmojiSearch.results(mappings, "SMIL").map { it.base().value } shouldContainExactly listOf("😀")
        EmojiSearch.results(mappings, "approv").map { it.base().value } shouldContainExactly listOf("👍")
    }

    test("search ranks exact name before prefix and contains matches") {
        val exact = emojiSet("✨", "spark", "sparkle")
        val prefix = emojiSet("🎇", "sparkler", "firework")
        val contains = emojiSet("🧨", "firespark", "crackle")
        val mappings = emojiMappings(listOf(contains, prefix, exact))

        EmojiSearch.results(mappings, "spark").map { it.base().value } shouldContainExactly listOf("✨", "🎇", "🧨")
    }

    test("search excludes recently used pseudo category") {
        val recentOnly = mapOf(
            EmojiCategory.RECENTLY_USED to listOf(emojiSet("😀", "grinning face", "smile")),
        )

        EmojiSearch.results(recentOnly, "smile") shouldBe emptyList()
    }
})

private fun emojiMappings(sets: List<EmojiSet>): EmojiDataByCategory {
    return EmojiCategory.entries.associateWith { category ->
        if (category == EmojiCategory.SMILEYS_EMOTION) sets else emptyList()
    }
}

private fun emojiSet(value: String, name: String, vararg keywords: String): EmojiSet {
    return EmojiSet(listOf(Emoji(value, name, keywords.toList())))
}
