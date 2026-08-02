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

import kotlinx.serialization.Serializable
import java.text.BreakIterator
import java.text.Normalizer
import java.util.Locale
import kotlin.math.max

class VoiceCommandParser(
    private val commands: List<VoiceCommandDefinition> = VoiceCommandDefinition.builtIns(),
    private val defaultMinimumConfidence: Double = DEFAULT_MINIMUM_CONFIDENCE,
    /**
     * Locale used for case folding. Defaults to [Locale.ROOT] because command matching must not
     * depend on the device locale; a caller that knows the dictation locale can pass it, and the
     * dotless/dotted-I fold below keeps Turkish casing from breaking Latin command phrases.
     */
    private val matchingLocale: Locale = Locale.ROOT,
) {
    fun parse(
        spokenText: String,
        additionalCommands: List<VoiceCommandDefinition> = emptyList(),
        minimumConfidence: Double = defaultMinimumConfidence,
    ): VoiceCommandMatch? {
        val normalizedSpokenText = normalizeForMatching(spokenText)
        if (normalizedSpokenText.isBlank()) {
            return null
        }

        // ROADMAP §6 N15.3 — parameterised "remove <item>" patterns must
        // run before the fixed-phrase matcher: an utterance like
        // "no longer want apples" would otherwise edit-distance-collapse
        // to one of the unrelated fixed phrases ("undo", "redo") because
        // the matcher walks the whole utterance. Parameterised match
        // requires an exact prefix so false positives stay contained;
        // when no parameterised pattern fits, we fall through to the
        // existing fixed-phrase ranker untouched.
        parameterisedMatch(spokenText, normalizedSpokenText)?.let { match ->
            if (match.confidence >= minimumConfidence.coerceIn(0.0, 1.0)) {
                return match
            }
        }

        val bestMatch = (additionalCommands.map { CommandCandidate(command = it, priority = 1) } +
            commands.map { CommandCandidate(command = it, priority = 0) })
            .flatMap { candidate ->
                candidate.command.phrases.map { phrase ->
                    val normalizedPhrase = normalizeForMatching(phrase)
                    MatchedCommandCandidate(
                        priority = candidate.priority,
                        match = VoiceCommandMatch(
                            action = candidate.command.action,
                            spokenText = spokenText,
                            matchedPhrase = candidate.command.canonicalPhrase,
                            matchedAlias = phrase.takeUnless { it == candidate.command.canonicalPhrase },
                            confidence = confidence(normalizedSpokenText, normalizedPhrase),
                        ),
                    )
                }
            }
            .maxWithOrNull(
                compareBy<MatchedCommandCandidate> { it.match.confidence }
                    .thenBy { it.priority }
                    .thenBy { it.match.matchedAlias == null }
                    .thenBy { it.match.matchedPhrase.length },
            )
            ?.match

        return bestMatch?.takeIf { it.confidence >= minimumConfidence.coerceIn(0.0, 1.0) }
    }

    /**
     * ROADMAP §6 N15.3 — Smart-Edit-voice REMOVE_ITEM_FROM_LIST parser.
     *
     * Recognises one of a small set of prefix / suffix patterns where the
     * spoken text shape is unambiguous about the speaker wanting to remove
     * a named item from the dictated stream so far ("no longer want
     * apples", "remove apples from the list", "scratch apples"). The
     * extracted argument is the noun phrase between the pattern's anchor
     * tokens.
     *
     * Confidence is fixed at 1.0 for an exact pattern match because the
     * anchor tokens themselves disambiguate — partial matches just don't
     * fire (the user has time to retry). The argument is preserved with
     * its original casing so the executor can do a case-insensitive find
     * against the committed buffer but still render the original phrase
     * back in any "Removed 'X'" feedback line.
     */
    internal fun parameterisedMatch(rawSpokenText: String, normalizedSpokenText: String): VoiceCommandMatch? {
        for (pattern in RemoveItemPatterns) {
            val argument = pattern.extract(normalizedSpokenText) ?: continue
            // Pull the matching argument back out of the raw text so the
            // executor can display the original casing in any UX line.
            val rawArgument = pattern.extractRaw(rawSpokenText) ?: argument
            return VoiceCommandMatch(
                action = VoiceCommandAction.REMOVE_ITEM_FROM_LIST,
                spokenText = rawSpokenText,
                matchedPhrase = pattern.canonicalPhrase,
                matchedAlias = null,
                confidence = 1.0,
                argument = rawArgument,
            )
        }
        return null
    }

    fun parse(
        spokenText: String,
        customCommands: VoiceCommandCustomCommands,
        minimumConfidence: Double = defaultMinimumConfidence,
    ): VoiceCommandMatch? {
        return parse(
            spokenText = spokenText,
            additionalCommands = customCommands.enabledDefinitions(),
            minimumConfidence = minimumConfidence,
        )
    }

    /**
     * Normalises an utterance or command phrase into a comparable matching key.
     *
     * The pipeline is Unicode-aware end to end: canonical composition (NFC) so combining-mark
     * spellings compare equal, locale-aware case folding, script-scoped diacritic removal, and a
     * separator pass that keeps every Unicode letter, number and remaining mark. ASCII-only
     * filtering used to erase Cyrillic, Arabic and CJK utterances entirely, which made every
     * non-Latin command and dictation argument collapse to an empty string.
     */
    internal fun normalizeForMatching(text: String): String {
        val composed = Normalizer.normalize(text, Normalizer.Form.NFC)
        val folded = foldCaseInsensitiveLatin(composed.lowercase(matchingLocale))
        return stripOptionalDiacritics(folded)
            .replace(ApostropheRegex, "")
            .replace(NonWordRegex, " ")
            .replace(WhitespaceRegex, " ")
            .trim()
            .removePrefix("please ")
            .removeSuffix(" please")
            .trim()
    }

    /**
     * Splits [text] into grapheme clusters so an emoji, a surrogate pair or a base character with
     * combining marks counts as one edit rather than two to four.
     */
    internal fun graphemes(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
        iterator.setText(text)
        val clusters = ArrayList<String>(text.length)
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            clusters += text.substring(start, end)
            start = end
            end = iterator.next()
        }
        return clusters
    }

    private fun confidence(spokenText: String, commandPhrase: String): Double {
        if (spokenText == commandPhrase) {
            return 1.0
        }
        val left = graphemes(spokenText)
        val right = graphemes(commandPhrase)
        val length = max(left.size, right.size).coerceAtLeast(1)
        return (1.0 - editDistance(left, right).toDouble() / length)
            .coerceIn(0.0, 1.0)
    }

    private fun editDistance(left: List<String>, right: List<String>): Int {
        val distances = Array(left.size + 1) { row -> IntArray(right.size + 1) { column -> row + column } }
        for (row in 0..left.size) {
            distances[row][0] = row
        }
        for (column in 0..right.size) {
            distances[0][column] = column
        }

        for (row in 1..left.size) {
            for (column in 1..right.size) {
                val substitutionCost = if (left[row - 1] == right[column - 1]) 0 else 1
                var best = minOf(
                    distances[row - 1][column] + 1,
                    distances[row][column - 1] + 1,
                    distances[row - 1][column - 1] + substitutionCost,
                )
                if (
                    row > 1 &&
                    column > 1 &&
                    left[row - 1] == right[column - 2] &&
                    left[row - 2] == right[column - 1]
                ) {
                    best = minOf(best, distances[row - 2][column - 2] + 1)
                }
                distances[row][column] = best
            }
        }
        return distances[left.size][right.size]
    }

    /**
     * Folds the two Latin letters whose lowercase form depends on the locale, so `I` dictated
     * under a Turkish locale (`ı`) and `İ` still match the Latin command phrases. `ſ` is folded
     * for the same reason: it lowercases to itself but compares equal to `s` under case folding.
     */
    private fun foldCaseInsensitiveLatin(text: String): String {
        if (text.none { it == 'ı' || it == 'ſ' }) return text
        return text.map { character ->
            when (character) {
                'ı' -> 'i'
                'ſ' -> 's'
                else -> character
            }
        }.joinToString(separator = "")
    }

    /**
     * Removes non-spacing marks only where they are optional for the script — Latin, Greek,
     * Cyrillic, Arabic and Hebrew — so `wórd` still matches `word` while Devanagari, Thai and
     * other scripts keep marks that carry phonemic meaning. Spacing marks are never removed.
     */
    private fun stripOptionalDiacritics(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        val builder = StringBuilder(decomposed.length)
        var baseAllowsStripping = false
        var index = 0
        while (index < decomposed.length) {
            val codePoint = decomposed.codePointAt(index)
            index += Character.charCount(codePoint)
            if (Character.getType(codePoint) == Character.NON_SPACING_MARK.toInt()) {
                if (!baseAllowsStripping) {
                    builder.appendCodePoint(codePoint)
                }
                continue
            }
            baseAllowsStripping = Character.UnicodeScript.of(codePoint) in DiacriticOptionalScripts
            builder.appendCodePoint(codePoint)
        }
        return Normalizer.normalize(builder, Normalizer.Form.NFC)
    }

    companion object {
        const val DEFAULT_MINIMUM_CONFIDENCE = 0.85
        const val DEFAULT_SUGGESTION_MINIMUM_CONFIDENCE = 0.50

        /** Straight, typographic and modifier apostrophes all elide (`don't` == `dont`). */
        private val ApostropheRegex = "['’ʼʹ`´]".toRegex()

        /**
         * Everything that is not a Unicode letter, number, mark or separator becomes a word
         * separator. Punctuation, symbols and emoji therefore split tokens deterministically
         * instead of being pattern-matched.
         */
        private val NonWordRegex = "[^\\p{L}\\p{N}\\p{M}\\s\\p{Z}]".toRegex()

        /** Unicode-aware whitespace collapse; `\s` alone misses NBSP and the ideographic space. */
        private val WhitespaceRegex = "[\\s\\p{Z}]+".toRegex()

        /**
         * Scripts where non-spacing marks are presentational and routinely omitted by speech
         * recognisers. Marks outside this set (Devanagari matras, Thai vowel signs, …) change the
         * word and are preserved.
         */
        private val DiacriticOptionalScripts = setOf(
            Character.UnicodeScript.LATIN,
            Character.UnicodeScript.GREEK,
            Character.UnicodeScript.CYRILLIC,
            Character.UnicodeScript.ARABIC,
            Character.UnicodeScript.HEBREW,
        )

        // ROADMAP §6 N15.3 — Smart Edit voice REMOVE_ITEM_FROM_LIST patterns.
        // Each pattern's `prefix` + optional `suffix` must match the
        // normalised utterance literally; everything between becomes the
        // argument. Order matters — earliest match wins, so put the most
        // specific / longest-prefix patterns first.
        internal val RemoveItemPatterns: List<RemoveItemPattern> = listOf(
            RemoveItemPattern(canonicalPhrase = "no longer want", prefix = "no longer want"),
            RemoveItemPattern(canonicalPhrase = "no longer need", prefix = "no longer need"),
            RemoveItemPattern(
                canonicalPhrase = "remove <item> from the list",
                prefix = "remove",
                suffix = "from the list",
            ),
            RemoveItemPattern(
                canonicalPhrase = "remove <item> from list",
                prefix = "remove",
                suffix = "from list",
            ),
            RemoveItemPattern(
                canonicalPhrase = "delete <item> from the list",
                prefix = "delete",
                suffix = "from the list",
            ),
            RemoveItemPattern(
                canonicalPhrase = "delete <item> from list",
                prefix = "delete",
                suffix = "from list",
            ),
            // "scratch <item>" was previously accepted with only a prefix
            // and no suffix. That made it a *catch-everything* trigger:
            // any utterance starting with the word "scratch" — including
            // ones where the user is dictating natural prose ("let me
            // scratch that out", "scratch the previous note") — silently
            // executed REMOVE_ITEM_FROM_LIST against the committed
            // buffer. Now requires an explicit "from list" / "off list"
            // / "off the list" suffix so the anchor disambiguates intent.
            // Users who genuinely want to remove via "scratch" still have
            // the variant `scratch apples off list`; the bare-prefix
            // attack surface is gone.
            RemoveItemPattern(
                canonicalPhrase = "scratch <item> from the list",
                prefix = "scratch",
                suffix = "from the list",
            ),
            RemoveItemPattern(
                canonicalPhrase = "scratch <item> from list",
                prefix = "scratch",
                suffix = "from list",
            ),
            RemoveItemPattern(
                canonicalPhrase = "scratch <item> off the list",
                prefix = "scratch",
                suffix = "off the list",
            ),
            RemoveItemPattern(
                canonicalPhrase = "scratch <item> off list",
                prefix = "scratch",
                suffix = "off list",
            ),
        )

        // Conservative single-word stopword set rejected as an argument
        // so an utterance like "remove the from the list" or "scratch
        // the" can't excise committed text by accident. Real list items
        // ("apples", "bread") never end up on this list.
        internal val BlockedArguments: Set<String> = setOf(
            "the", "a", "an", "this", "that", "it", "them", "those", "these",
        )
    }

    private data class CommandCandidate(
        val command: VoiceCommandDefinition,
        val priority: Int,
    )

    private data class MatchedCommandCandidate(
        val match: VoiceCommandMatch,
        val priority: Int,
    )

    /** Internal anchor-token pattern for parameterised commands. */
    internal data class RemoveItemPattern(
        val canonicalPhrase: String,
        val prefix: String,
        val suffix: String = "",
    ) {
        /**
         * Returns the argument (the item to remove) extracted from a
         * lowercased / normalised utterance, or null when the pattern's
         * anchor tokens don't match.
         */
        fun extract(normalizedSpokenText: String): String? {
            if (!normalizedSpokenText.startsWith("$prefix ") && normalizedSpokenText != prefix) {
                return null
            }
            val afterPrefix = normalizedSpokenText.removePrefix(prefix).trim()
            val argument = if (suffix.isEmpty()) {
                afterPrefix
            } else {
                if (!afterPrefix.endsWith(" $suffix") && afterPrefix != suffix) {
                    return null
                }
                afterPrefix.removeSuffix(suffix).trim()
            }
            // Reject blank / single-stopword arguments.
            if (argument.isBlank()) return null
            if (argument in BlockedArguments) return null
            return argument
        }

        /**
         * Best-effort extraction of the argument from the *original* raw
         * text so the executor can echo the user's casing back in the
         * UI ("Removed 'Apples'" instead of "Removed 'apples'"). Falls
         * back to null when the raw text can't be matched against the
         * same anchor tokens; the caller then uses the normalised
         * argument as the canonical form.
         */
        fun extractRaw(rawSpokenText: String): String? {
            val collapsed = rawSpokenText.trim()
            val lowerCollapsed = collapsed.lowercase()
            if (!lowerCollapsed.startsWith("$prefix ") && lowerCollapsed != prefix) {
                return null
            }
            val afterPrefixRaw = collapsed.substring(prefix.length).trim().trimStart { c ->
                // Trim leading punctuation introduced by the recogniser
                // ("no longer want, apples" → "apples").
                !c.isLetterOrDigit()
            }
            val argument = if (suffix.isEmpty()) {
                afterPrefixRaw
            } else {
                val lowerSuffix = " $suffix"
                val idx = afterPrefixRaw.lowercase().lastIndexOf(lowerSuffix)
                if (idx < 0) return null
                afterPrefixRaw.substring(0, idx).trim()
            }
            return argument.takeIf { it.isNotBlank() }
        }
    }
}

data class VoiceCommandDefinition(
    val action: VoiceCommandAction,
    val canonicalPhrase: String,
    val aliases: List<String> = emptyList(),
) {
    val phrases: List<String>
        get() = listOf(canonicalPhrase) + aliases

    companion object {
        fun builtIns(): List<VoiceCommandDefinition> {
            return listOf(
                VoiceCommandDefinition(
                    action = VoiceCommandAction.DELETE_THAT,
                    canonicalPhrase = "delete that",
                    aliases = listOf("delete it", "remove that"),
                ),
                VoiceCommandDefinition(
                    action = VoiceCommandAction.UNDO,
                    canonicalPhrase = "undo",
                ),
                VoiceCommandDefinition(
                    action = VoiceCommandAction.REDO,
                    canonicalPhrase = "redo",
                ),
                VoiceCommandDefinition(
                    action = VoiceCommandAction.SELECT_ALL,
                    canonicalPhrase = "select all",
                    aliases = listOf("select everything"),
                ),
                VoiceCommandDefinition(
                    action = VoiceCommandAction.CLEAR_TEXT,
                    canonicalPhrase = "clear text",
                    aliases = listOf("clear field", "delete all"),
                ),
                VoiceCommandDefinition(
                    action = VoiceCommandAction.NEW_PARAGRAPH,
                    canonicalPhrase = "new paragraph",
                    aliases = listOf("next paragraph"),
                ),
                VoiceCommandDefinition(
                    action = VoiceCommandAction.NEW_LINE,
                    canonicalPhrase = "new line",
                    aliases = listOf("line break"),
                ),
                VoiceCommandDefinition(
                    action = VoiceCommandAction.CAPITALIZE_NEXT_WORD,
                    canonicalPhrase = "capitalize next word",
                    aliases = listOf("capitalize next"),
                ),
                VoiceCommandDefinition(
                    action = VoiceCommandAction.GO_TO_START,
                    canonicalPhrase = "go to start",
                    aliases = listOf("go to beginning", "start of field"),
                ),
                VoiceCommandDefinition(
                    action = VoiceCommandAction.GO_TO_END,
                    canonicalPhrase = "go to end",
                    aliases = listOf("end of field"),
                ),
            )
        }
    }
}

data class VoiceCommandMatch(
    val action: VoiceCommandAction,
    val spokenText: String,
    val matchedPhrase: String,
    val matchedAlias: String?,
    val confidence: Double,
    /**
     * Optional argument extracted from a parameterised command
     * (currently only [VoiceCommandAction.REMOVE_ITEM_FROM_LIST]).
     * Null for every fixed-phrase command. Preserves the original
     * casing so UX feedback ("Removed 'Apples'") reads naturally.
     */
    val argument: String? = null,
)

@Serializable
enum class VoiceCommandAction {
    DELETE_THAT,
    UNDO,
    REDO,
    SELECT_ALL,
    CLEAR_TEXT,
    NEW_PARAGRAPH,
    NEW_LINE,
    CAPITALIZE_NEXT_WORD,
    GO_TO_START,
    GO_TO_END,

    /**
     * ROADMAP §6 N15.3 — Smart Edit voice. Walks the dictated-list
     * buffer and excises a named item ("no longer want apples").
     * Always carries [VoiceCommandMatch.argument]; the executor refuses
     * to run if the argument is null / blank.
     */
    REMOVE_ITEM_FROM_LIST,
}
