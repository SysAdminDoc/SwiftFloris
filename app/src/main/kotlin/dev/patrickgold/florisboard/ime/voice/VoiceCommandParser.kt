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

import java.text.Normalizer
import kotlin.math.max

class VoiceCommandParser(
    private val commands: List<VoiceCommandDefinition> = VoiceCommandDefinition.builtIns(),
    private val defaultMinimumConfidence: Double = DEFAULT_MINIMUM_CONFIDENCE,
) {
    fun parse(
        spokenText: String,
        minimumConfidence: Double = defaultMinimumConfidence,
    ): VoiceCommandMatch? {
        val normalizedSpokenText = normalizeForMatching(spokenText)
        if (normalizedSpokenText.isBlank()) {
            return null
        }

        val bestMatch = commands
            .flatMap { command ->
                command.phrases.map { phrase ->
                    val normalizedPhrase = normalizeForMatching(phrase)
                    VoiceCommandMatch(
                        action = command.action,
                        spokenText = spokenText,
                        matchedPhrase = command.canonicalPhrase,
                        matchedAlias = phrase.takeUnless { it == command.canonicalPhrase },
                        confidence = confidence(normalizedSpokenText, normalizedPhrase),
                    )
                }
            }
            .maxWithOrNull(
                compareBy<VoiceCommandMatch> { it.confidence }
                    .thenBy { it.matchedAlias == null }
                    .thenBy { it.matchedPhrase.length },
            )

        return bestMatch?.takeIf { it.confidence >= minimumConfidence.coerceIn(0.0, 1.0) }
    }

    internal fun normalizeForMatching(text: String): String {
        val withoutDiacritics = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(CombiningMarksRegex, "")
        return withoutDiacritics
            .replace(ApostropheRegex, "")
            .replace(NonWordRegex, " ")
            .replace(WhitespaceRegex, " ")
            .trim()
            .removePrefix("please ")
            .removeSuffix(" please")
            .trim()
    }

    private fun confidence(spokenText: String, commandPhrase: String): Double {
        if (spokenText == commandPhrase) {
            return 1.0
        }
        val length = max(spokenText.length, commandPhrase.length).coerceAtLeast(1)
        return (1.0 - editDistance(spokenText, commandPhrase).toDouble() / length)
            .coerceIn(0.0, 1.0)
    }

    private fun editDistance(left: String, right: String): Int {
        val distances = Array(left.length + 1) { row -> IntArray(right.length + 1) { column -> row + column } }
        for (row in 0..left.length) {
            distances[row][0] = row
        }
        for (column in 0..right.length) {
            distances[0][column] = column
        }

        for (row in 1..left.length) {
            for (column in 1..right.length) {
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
        return distances[left.length][right.length]
    }

    companion object {
        const val DEFAULT_MINIMUM_CONFIDENCE = 0.85

        private val CombiningMarksRegex = "\\p{Mn}+".toRegex()
        private val ApostropheRegex = "[']".toRegex()
        private val NonWordRegex = "[^a-z0-9\\s]".toRegex()
        private val WhitespaceRegex = "\\s+".toRegex()
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
)

enum class VoiceCommandAction {
    DELETE_THAT,
    UNDO,
    REDO,
    SELECT_ALL,
    NEW_PARAGRAPH,
    NEW_LINE,
    CAPITALIZE_NEXT_WORD,
    GO_TO_START,
    GO_TO_END,
}
