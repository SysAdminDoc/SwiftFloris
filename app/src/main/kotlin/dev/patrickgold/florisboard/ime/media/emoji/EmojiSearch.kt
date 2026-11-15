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

import dev.patrickgold.florisboard.lib.FlorisLocale
import java.util.Locale

object EmojiSearch {
    internal const val MaxLocaleMappings = 8
    private const val MAX_SEARCH_MAPPINGS = MaxLocaleMappings + 1
    private const val MAX_EMOJI_SETS_PER_LOCALE = 4096
    private const val MAX_INDEXED_EMOJI_VALUES = 4096
    private const val DEFAULT_MAX_RESULTS = 96
    private const val MAX_BUILT_IN_LABELS = 6

    /**
     * The languages whose emoji names the palette searches: the keyboard's own
     * first, then English. Names only ship for a few languages, so without
     * English a keyboard in any other language finds nothing for "pensive" and
     * every emoji looks unlabeled (#24).
     */
    internal fun searchLocales(keyboardLocales: List<FlorisLocale>): List<FlorisLocale> {
        val distinct = keyboardLocales.distinctBy { locale -> locale.languageTag() }
        val limited = distinct.take(MaxLocaleMappings)
        if (limited.any { it.language == FlorisLocale.ENGLISH.language }) return limited
        return distinct.take(MaxLocaleMappings - 1) + FlorisLocale.ENGLISH
    }

    /**
     * The bundled name and keywords for [emojiValue], from the first language
     * in [mappingsByLocale] that names it. Empty when none does.
     */
    internal fun builtInLabels(
        mappingsByLocale: List<EmojiDataByCategory>,
        emojiValue: String,
        maxLabels: Int = MAX_BUILT_IN_LABELS,
    ): List<String> {
        if (emojiValue.isEmpty() || maxLabels <= 0) return emptyList()
        for (mappings in mappingsByLocale) {
            val emoji = mappings.asSequence()
                .filter { (category, _) -> category != EmojiCategory.RECENTLY_USED }
                .flatMap { (_, emojiSets) -> emojiSets.asSequence() }
                .flatMap { emojiSet -> emojiSet.emojis.asSequence() }
                .firstOrNull { it.value == emojiValue && it.name.isNotBlank() }
                ?: continue
            // The name already covers its own words ("pensive face" finds
            // "pensive"), so the keywords listed are the extra ones.
            val name = emoji.name.trim()
            val extraKeywords = emoji.keywords
                .map { it.trim() }
                .filter { it.isNotEmpty() && !name.contains(it, ignoreCase = true) }
            return (listOf(name) + extraKeywords)
                .distinctBy { it.lowercase(Locale.ROOT) }
                .take(maxLabels)
        }
        return emptyList()
    }

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
        return results(
            mappingsByLocale = listOf(mappings),
            query = query,
            customTagStore = customTagStore,
            maxResults = maxResults,
        )
    }

    /**
     * Searches locale mappings in priority order. The active subtype's primary locale should be first, followed by
     * its secondary locales and other enrolled subtype locales; the root mapping can be appended as the final
     * fallback. A value is emitted once, while the best matching locale annotation wins its score and locale tie-break.
     */
    fun results(
        mappingsByLocale: List<EmojiDataByCategory>,
        query: String,
        customTagStore: CustomEmojiTagStore? = null,
        maxResults: Int = DEFAULT_MAX_RESULTS,
    ): List<EmojiSet> {
        val normalizedQuery = query.normalizedForEmojiSearch()
        if (normalizedQuery.isBlank() || maxResults <= 0) return emptyList()

        val ranking = compareBy<ScoredEmojiSet> { it.score }
            .thenBy { it.localePriority }
            .thenBy { it.emojiSet.base().name }
            .thenBy { it.emojiSet.base().value }
        val bestByValue = LinkedHashMap<String, ScoredEmojiSet>()
        mappingsByLocale
            .asSequence()
            .take(MAX_SEARCH_MAPPINGS)
            .forEachIndexed { localePriority, mappings ->
                mappings.asSequence()
                    .filter { (category, _) -> category != EmojiCategory.RECENTLY_USED }
                    .flatMap { (_, emojiSets) -> emojiSets.asSequence() }
                    .take(MAX_EMOJI_SETS_PER_LOCALE)
                    .forEach { emojiSet ->
                        val value = emojiSet.base().value
                        val score = score(emojiSet, normalizedQuery, customTagStore) ?: return@forEach
                        val candidate = ScoredEmojiSet(emojiSet, score, localePriority)
                        val existing = bestByValue[value]
                        if (
                            existing == null && bestByValue.size < MAX_INDEXED_EMOJI_VALUES ||
                            existing != null && ranking.compare(candidate, existing) < 0
                        ) {
                            bestByValue[value] = candidate
                        }
                    }
            }
        return bestByValue.values
            .sortedWith(ranking)
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
        val localePriority: Int,
    )

    /** What the palette shows under a non-blank search query. */
    internal enum class Panel {
        RESULTS,
        NO_MATCH,
        LOADING,
    }

    /**
     * The emoji names load in the background each time the palette opens, so a query that comes back
     * from the letter keys runs before they arrive (#24). "No emojis match" is only true once they have.
     */
    internal fun panel(hasResults: Boolean, namesLoaded: Boolean): Panel {
        return when {
            hasResults -> Panel.RESULTS
            namesLoaded -> Panel.NO_MATCH
            else -> Panel.LOADING
        }
    }
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
