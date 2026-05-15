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

import java.util.Locale

object EmojiSearch {
    private const val DEFAULT_MAX_RESULTS = 96

    /**
     * Search emoji by name / Emojibase keyword / **custom user tag**
     * (ROADMAP §7 Next-9.4 — emoji search-by-tag). Custom tags carry
     * the highest non-trivial priority so a user-tagged 🦋 with
     * "freedom" wins over a generic emoji whose name merely *contains*
     * "freedom". When [customTagStore] is null, the search falls back
     * to the bundled-name + Emojibase-keyword behaviour shipped in
     * v1.7.8.
     */
    fun results(
        mappings: EmojiDataByCategory,
        query: String,
        customTagStore: CustomEmojiTagStore? = null,
        maxResults: Int = DEFAULT_MAX_RESULTS,
    ): List<EmojiSet> {
        val normalizedQuery = query.normalizedForEmojiSearch()
        if (normalizedQuery.isBlank() || maxResults <= 0) return emptyList()

        return mappings.asSequence()
            .filter { (category, _) -> category != EmojiCategory.RECENTLY_USED }
            .flatMap { (_, emojiSets) -> emojiSets.asSequence() }
            .distinctBy { emojiSet -> emojiSet.base().value }
            .mapNotNull { emojiSet ->
                val score = score(emojiSet, normalizedQuery, customTagStore)
                    ?: return@mapNotNull null
                ScoredEmojiSet(emojiSet, score)
            }
            .sortedWith(
                compareBy<ScoredEmojiSet> { it.score }
                    .thenBy { it.emojiSet.base().name }
                    .thenBy { it.emojiSet.base().value },
            )
            .take(maxResults)
            .map { it.emojiSet }
            .toList()
    }

    private fun score(
        emojiSet: EmojiSet,
        query: String,
        customTagStore: CustomEmojiTagStore?,
    ): Int? {
        var bestScore: Int? = null
        for (emoji in emojiSet.emojis) {
            val name = emoji.name.normalizedForEmojiSearch()
            val keywords = emoji.keywords.map { it.normalizedForEmojiSearch() }
            val customTags = customTagStore?.tagsFor(emoji.value)
                ?.map { it.normalizedForEmojiSearch() }
                ?: emptyList()
            val score = when {
                emoji.value == query -> 0
                name == query -> 1
                // ROADMAP §7 Next-9.4 — custom tag exact match. User
                // intent is explicit (they typed the tag themselves) so
                // it ranks above bundled Emojibase keyword matches.
                customTags.any { it == query } -> 2
                keywords.any { it == query } -> 3
                customTags.any { it.wordStartsWith(query) } -> 4
                name.wordStartsWith(query) -> 5
                keywords.any { it.wordStartsWith(query) } -> 6
                customTags.any { it.contains(query) } -> 7
                name.contains(query) -> 8
                keywords.any { it.contains(query) } -> 9
                else -> null
            }
            if (score != null && (bestScore == null || score < bestScore)) {
                bestScore = score
            }
        }
        return bestScore
    }

    private data class ScoredEmojiSet(
        val emojiSet: EmojiSet,
        val score: Int,
    )
}

private fun String.normalizedForEmojiSearch(): String {
    return lowercase(Locale.ROOT)
        .replace('_', ' ')
        .replace('-', ' ')
        .trim()
        .replace(Regex("\\s+"), " ")
}

private fun String.wordStartsWith(query: String): Boolean {
    return split(' ').any { word -> word.startsWith(query) }
}
