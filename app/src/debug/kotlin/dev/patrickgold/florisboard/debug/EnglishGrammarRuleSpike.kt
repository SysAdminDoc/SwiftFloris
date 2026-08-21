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

package dev.patrickgold.florisboard.debug

import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.TextInfo
import dev.patrickgold.florisboard.ime.nlp.SpellingResult

/**
 * Debug-only English rule set used to prove sentence-level grammar attributes on Android.
 * Release variants do not compile this source set.
 */
object EnglishGrammarRuleSpike {
    private const val MaxMatchesPerSentence = 16
    private val demonstrativeAgreement = Regex(
        pattern = """\b(this|that|these|those)(\s+)(is|are)\b""",
        option = RegexOption.IGNORE_CASE,
    )

    data class Match(
        val offset: Int,
        val length: Int,
        val replacement: String,
    )

    fun findMatches(text: String): List<Match> {
        return demonstrativeAgreement.findAll(text)
            .mapNotNull { match ->
                val subject = match.groups[1]?.value?.lowercase() ?: return@mapNotNull null
                val verb = match.groups[3] ?: return@mapNotNull null
                val replacement = when (subject) {
                    "this", "that" -> "is"
                    "these", "those" -> "are"
                    else -> return@mapNotNull null
                }
                if (verb.value.equals(replacement, ignoreCase = true)) {
                    null
                } else {
                    Match(
                        offset = verb.range.first,
                        length = verb.value.length,
                        replacement = replacement,
                    )
                }
            }
            .take(MaxMatchesPerSentence)
            .toList()
    }

    fun evaluate(textInfo: TextInfo, suggestionsLimit: Int): SentenceSuggestionsInfo? {
        val text = textInfo.text ?: return null
        val matches = findMatches(text)
        if (matches.isEmpty()) return null

        val suggestions = matches.map { match ->
            val replacements = if (suggestionsLimit > 0) {
                arrayOf(match.replacement)
            } else {
                emptyArray()
            }
            SpellingResult.grammarError(
                suggestions = replacements,
                isHighConfidenceResult = true,
            ).suggestionsInfo.apply {
                setCookieAndSequence(textInfo.cookie, textInfo.sequence)
            }
        }.toTypedArray()

        return SentenceSuggestionsInfo(
            suggestions,
            matches.map(Match::offset).toIntArray(),
            matches.map(Match::length).toIntArray(),
        )
    }
}
