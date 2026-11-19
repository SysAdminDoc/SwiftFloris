/*
 * Copyright (C) 2024-2025 The FlorisBoard Contributors
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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Context
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.nlp.CandidateTrailingSpacePolicy
import dev.patrickgold.florisboard.ime.nlp.EmojiSuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.SuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.SuggestionProvider
import dev.patrickgold.florisboard.lib.FlorisLocale
import io.github.reactivecircus.cache4k.Cache

/**
 * Provides emoji suggestions within a text input context.
 *
 * This class handles the following tasks:
 * - Initializes and maintains a list of supported emojis.
 * - Generates and returns emoji suggestions based on user input and preferences.
 *
 * @param context The application context.
 */
class EmojiSuggestionProvider(private val context: Context) : SuggestionProvider {
    override val providerId = "org.florisboard.nlp.providers.emoji"
    override val candidateTrailingSpacePolicy = CandidateTrailingSpacePolicy.NEVER

    private val prefs by FlorisPreferenceStore
    private val lettersRegex = "^[A-Za-z]*$".toRegex()

    private val cachedEmojiMappings = Cache.Builder<FlorisLocale, EmojiDataBySkinTone>().build()

    override suspend fun create() {
    }

    override suspend fun preload(subtype: Subtype) {
        (subtype.locales() + ShortcodeFallbackLocale).forEach { locale ->
            mappingsFor(locale)
        }
        EmojiUsageRank.get(context)
    }

    private suspend fun mappingsFor(locale: FlorisLocale): EmojiDataBySkinTone {
        return cachedEmojiMappings.get(locale) {
            EmojiData.get(context, locale).bySkinTone
        }
    }

    override suspend fun suggest(
        subtype: Subtype,
        content: EditorContent,
        maxCandidateCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean
    ): List<SuggestionCandidate> {
        val preferredSkinTone = prefs.emoji.preferredSkinTone.get()
        val showName = prefs.emoji.suggestionCandidateShowName.get()
        val query = validateInputQuery(content.composingText) ?: return emptyList()
        // Append mode keeps the typed word in front of the emoji. It only
        // applies to the inline trigger: with a leading `:` the composing text
        // is a shortcode, and keeping ":smile" in the document would be wrong.
        val precedingText = if (
            prefs.emoji.suggestionCandidateAppendToWord.get() &&
            prefs.emoji.suggestionType.get() == EmojiSuggestionType.INLINE_TEXT
        ) {
            content.composingText
        } else {
            ""
        }
        // Emoji names only ship for a handful of languages, so the keyboard
        // language alone finds nothing for `:pensive` on most subtypes (#24).
        // Shortcodes are English by convention, so English names are always
        // searched after the keyboard's own language.
        val sources = buildList {
            add(mappingsFor(subtype.primaryLocale)[preferredSkinTone].orEmpty())
            if (subtype.primaryLocale.language != ShortcodeFallbackLocale.language) {
                add(mappingsFor(ShortcodeFallbackLocale)[preferredSkinTone].orEmpty())
            }
        }
        // ROADMAP §7 Next-9.4 — custom user tags participate in predict-by-tag
        // ranking.
        val customTagStore = CustomEmojiTagStore.get(context)
        val usageRank = EmojiUsageRank.get(context)
        val emojis = withContext(Dispatchers.Default) {
            EmojiSuggestionRanker.rank(
                query = query,
                sources = sources,
                customTagsFor = customTagStore::tagsFor,
                maxCount = maxCandidateCount,
                usageRank = usageRank,
            )
        }
        return emojis.map { emoji ->
            EmojiSuggestionCandidate(
                emoji = emoji,
                showName = showName,
                precedingText = precedingText,
                sourceProvider = this@EmojiSuggestionProvider,
            )
        }
    }

    override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
        val updateHistory = prefs.emoji.suggestionUpdateHistory.get()
        if (!updateHistory || candidate !is EmojiSuggestionCandidate) {
            return
        }
        EmojiHistoryHelper.markEmojiUsed(prefs, candidate.emoji)
    }

    override suspend fun notifySuggestionReverted(subtype: Subtype, candidate: SuggestionCandidate) {
        // No-op
    }

    override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate) = false

    override suspend fun getListOfWords(subtype: Subtype) = emptyList<String>()

    override suspend fun getFrequencyForWord(subtype: Subtype, word: String) = 0.0

    override suspend fun destroy() {
        cachedEmojiMappings.invalidateAll()
    }

    /**
     * Validates the user input query for emoji suggestions.
     */
    private fun validateInputQuery(composingText: CharSequence): String? {
        val prefix = prefs.emoji.suggestionType.get().prefix
        val queryMinLength = prefs.emoji.suggestionQueryMinLength.get() + prefix.length
        if (prefix.isNotEmpty() && !composingText.startsWith(prefix)) {
            return null
        }
        if (composingText.length < queryMinLength) {
            return null
        }
        val emojiPartialName = composingText.substring(prefix.length)
        if (!lettersRegex.matches(emojiPartialName)) {
            return null
        }
        return emojiPartialName
    }

    private companion object {
        val ShortcodeFallbackLocale = FlorisLocale.ENGLISH
    }
}

/**
 * Scores emoji against a suggestion query. [rank] takes the emoji lists in
 * priority order (keyboard language first, then the English fallback) and
 * emits each emoji value once with its best score, keeping the first list's
 * emoji so a localized name wins the display.
 */
internal object EmojiSuggestionRanker {
    // Custom user tags carry the highest weight (1.0) because the user typed
    // them themselves and wants them to win.
    fun score(emoji: Emoji, query: String, customTags: Collection<String>): Double {
        val nameWeight = emoji.name.containsWeighted(query, ignoreCase = true)
        val keywordWeight = if (emoji.keywords.any { it.contains(query, ignoreCase = true) }) 1.0 else 0.0
        val customTagWeight = if (customTags.any { it.contains(query, ignoreCase = true) }) 1.0 else 0.0
        return nameWeight * 0.55 + keywordWeight * 0.25 + customTagWeight * 0.20
    }

    /**
     * [usageRank] maps an emoji value to how often people use it (0 is the most used, see [EmojiUsageRank]).
     * It only orders emoji that score the same; unranked ones go after them in source order.
     */
    fun rank(
        query: String,
        sources: List<List<Emoji>>,
        customTagsFor: (String) -> Collection<String>,
        maxCount: Int,
        usageRank: Map<String, Int> = emptyMap(),
    ): List<Emoji> {
        if (query.isEmpty() || maxCount <= 0) return emptyList()
        val best = LinkedHashMap<String, Pair<Emoji, Double>>()
        sources.forEach { emojis ->
            emojis.forEach { emoji ->
                if (emoji.value.isEmpty()) return@forEach
                val score = score(emoji, query, customTagsFor(emoji.value))
                if (score <= 0.0) return@forEach
                val existing = best[emoji.value]
                if (existing == null) {
                    best[emoji.value] = emoji to score
                } else if (score > existing.second) {
                    best[emoji.value] = existing.first to score
                }
            }
        }
        // sortedWith is stable, so ties the usage rank doesn't settle keep the source order.
        return best.values
            .sortedWith(
                compareByDescending<Pair<Emoji, Double>> { it.second }
                    .thenBy { usageRank[it.first.value] ?: Int.MAX_VALUE },
            )
            .take(maxCount)
            .map { it.first }
    }
}

private fun String.containsWeighted(other: String, ignoreCase: Boolean = false): Double = let { str ->
    if (str.contains(other, ignoreCase = ignoreCase)) {
        other.length.toDouble() / str.length.toDouble()
    } else {
        0.0
    }
}
