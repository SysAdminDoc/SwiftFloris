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

data class VoiceTranscriptChunk(
    val text: String,
    val isFinal: Boolean,
) {
    companion object {
        fun partial(text: String): VoiceTranscriptChunk {
            return VoiceTranscriptChunk(text = text, isFinal = false)
        }

        fun finalResult(text: String): VoiceTranscriptChunk {
            return VoiceTranscriptChunk(text = text, isFinal = true)
        }
    }
}

data class VoiceStreamingTranscriptUpdate(
    val committedText: String,
    val partialText: String,
    val visibleText: String,
    val committedSegment: String?,
    val commandMatch: VoiceCommandMatch?,
    val changed: Boolean,
)

/**
 * ROADMAP §7 Next-2.4 — single envelope returned by
 * [VoiceInputManager.consumeStreamingChunk]: the buffer's transcript update
 * plus the (optional) command-execution result the IME fired on a final
 * chunk match. Partial chunks return [executed] = null even when
 * [transcript].commandMatch is non-null, so the dictation overlay can
 * preview a pending command without committing it.
 */
data class VoiceStreamingCommandUpdate(
    val transcript: VoiceStreamingTranscriptUpdate,
    val executed: VoiceCommandExecutionResult?,
)

class StreamingVoiceTranscriptBuffer(
    private val commandParser: VoiceCommandParser = VoiceCommandParser(),
) {
    private val committedSegments = mutableListOf<String>()
    private var lastPartial = ""

    fun accept(
        chunk: VoiceTranscriptChunk,
        customCommands: VoiceCommandCustomCommands = VoiceCommandCustomCommands.Empty,
        commandMode: Boolean = false,
        commandMinimumConfidence: Double = VoiceCommandParser.DEFAULT_MINIMUM_CONFIDENCE,
        suggestionMinimumConfidence: Double = VoiceCommandParser.DEFAULT_SUGGESTION_MINIMUM_CONFIDENCE,
    ): VoiceStreamingTranscriptUpdate {
        val normalizedText = chunk.text.normalizedTranscript()
        return if (chunk.isFinal) {
            acceptFinal(
                text = normalizedText,
                customCommands = customCommands,
                commandMode = commandMode,
                commandMinimumConfidence = commandMinimumConfidence,
            )
        } else {
            acceptPartial(
                text = normalizedText,
                customCommands = customCommands,
                commandMode = commandMode,
                suggestionMinimumConfidence = suggestionMinimumConfidence,
            )
        }
    }

    fun reset() {
        committedSegments.clear()
        lastPartial = ""
    }

    /**
     * Snapshot of the committed-segment list, in arrival order. Exposed
     * so [VoiceCommandActions.removeItemFromList] (N15.3) can read the
     * dictated-list state before deciding how to mutate the editor.
     */
    fun committedSegmentsSnapshot(): List<String> = committedSegments.toList()

    /**
     * Restore the committed-segment list to a previously captured
     * [committedSegmentsSnapshot]. Used by
     * [VoiceCommandActions.removeItemFromList] to roll the buffer back
     * when [removeCommittedItem] mutated it but the editor edit could
     * not be applied — without this the buffer and the editor would
     * desync (the buffer would think the item was removed while the
     * editor still shows it). Does not touch the live partial.
     */
    fun restoreCommittedSegments(segments: List<String>) {
        committedSegments.clear()
        committedSegments += segments
    }

    /**
     * ROADMAP §6 N15.3 — walk the committed-segment buffer and excise
     * every case-insensitive occurrence of [item] (matched as a
     * standalone phrase, not a substring inside a longer word) along
     * with bridging punctuation / "and" / "with" connectors.
     *
     * Returns a [RemoveCommittedItemResult] describing what was removed
     * and the new committed text so the executor can apply the diff to
     * the editor. The buffer's internal state is updated in-place so
     * the next [accept] call sees the corrected committed text.
     *
     * When [item] is not found the buffer is left untouched and the
     * result reports `removedCount == 0`. Empty / whitespace-only [item]
     * is treated as not-found rather than throwing — protects against a
     * malformed parser argument silently nuking the entire buffer.
     */
    fun removeCommittedItem(item: String): RemoveCommittedItemResult {
        val previousText = committedText()
        val needle = item.trim()
        if (needle.isEmpty() || committedSegments.isEmpty()) {
            return RemoveCommittedItemResult(
                removedCount = 0,
                previousCommittedText = previousText,
                newCommittedText = previousText,
            )
        }
        val rewritten = mutableListOf<String>()
        var removedCount = 0
        for (segment in committedSegments) {
            val (cleaned, count) = stripItemFromSegment(segment, needle)
            removedCount += count
            if (cleaned.isNotBlank()) {
                rewritten += cleaned
            }
        }
        if (removedCount == 0) {
            return RemoveCommittedItemResult(
                removedCount = 0,
                previousCommittedText = previousText,
                newCommittedText = previousText,
            )
        }
        committedSegments.clear()
        committedSegments += rewritten
        return RemoveCommittedItemResult(
            removedCount = removedCount,
            previousCommittedText = previousText,
            newCommittedText = committedText(),
        )
    }

    /**
     * Remove every case-insensitive whole-phrase occurrence of [needle]
     * from [segment]. Bridging punctuation (commas, "and", "with", "&")
     * and surrounding whitespace are collapsed so the result reads
     * naturally. Returns the cleaned segment plus the number of matches.
     */
    private fun stripItemFromSegment(segment: String, needle: String): Pair<String, Int> {
        if (segment.isBlank()) return segment to 0
        val tokens = ItemTokenSplit.split(segment).filter { it.isNotBlank() }.toMutableList()
        if (tokens.isEmpty()) return segment to 0
        val needleTokens = ItemTokenSplit.split(needle)
            .map { it.trim(*ItemEdgePunctuation) }
            .filter { it.isNotBlank() }
        if (needleTokens.isEmpty()) return segment to 0
        val keepFlags = BooleanArray(tokens.size) { true }
        var removed = 0
        var i = 0
        while (i <= tokens.size - needleTokens.size) {
            val candidate = (0 until needleTokens.size).map { tokens[i + it] }
            val isMatch = candidate.zip(needleTokens).all { (c, n) ->
                c.trim(*ItemEdgePunctuation).equals(n, ignoreCase = true)
            }
            if (isMatch) {
                for (k in 0 until needleTokens.size) keepFlags[i + k] = false
                removed++
                i += needleTokens.size
            } else {
                i++
            }
        }
        if (removed == 0) return segment to 0
        // Drop dangling connectors that wrapped a removed item, e.g.
        // `apples and bread` → after removing `apples` we leave
        // `[and, bread]` and want `bread`. Same trick on trailing.
        val kept = tokens.filterIndexed { idx, _ -> keepFlags[idx] }.toMutableList()
        // Trim leading / trailing connector tokens.
        while (kept.isNotEmpty() && kept.first().lowercase().trim(*ItemEdgePunctuation) in ConnectorTokens) {
            kept.removeAt(0)
        }
        while (kept.isNotEmpty() && kept.last().lowercase().trim(*ItemEdgePunctuation) in ConnectorTokens) {
            kept.removeAt(kept.size - 1)
        }
        // Collapse two adjacent connectors that previously bracketed the
        // removed item (e.g. `eggs, and, bread` → `eggs, bread`).
        var j = 0
        while (j < kept.size - 1) {
            val left = kept[j].lowercase().trim(*ItemEdgePunctuation)
            val right = kept[j + 1].lowercase().trim(*ItemEdgePunctuation)
            if (left in ConnectorTokens && right in ConnectorTokens) {
                kept.removeAt(j + 1)
            } else {
                j++
            }
        }
        return kept.joinToString(" ") to removed
    }

    data class RemoveCommittedItemResult(
        val removedCount: Int,
        val previousCommittedText: String,
        val newCommittedText: String,
    ) {
        val didChange: Boolean get() = removedCount > 0 &&
            previousCommittedText != newCommittedText
    }

    private fun acceptPartial(
        text: String,
        customCommands: VoiceCommandCustomCommands,
        commandMode: Boolean,
        suggestionMinimumConfidence: Double,
    ): VoiceStreamingTranscriptUpdate {
        val changed = text != lastPartial
        lastPartial = text
        return update(
            partialText = text,
            committedSegment = null,
            commandMatch = commandMatch(
                text = text,
                customCommands = customCommands,
                commandMode = commandMode,
                minimumConfidence = suggestionMinimumConfidence,
            ),
            changed = changed,
        )
    }

    private fun acceptFinal(
        text: String,
        customCommands: VoiceCommandCustomCommands,
        commandMode: Boolean,
        commandMinimumConfidence: Double,
    ): VoiceStreamingTranscriptUpdate {
        val segment = segmentToCommit(text)
        if (segment.isNotBlank()) {
            committedSegments += segment
        }
        lastPartial = ""
        return update(
            partialText = "",
            committedSegment = segment.takeIf { it.isNotBlank() },
            commandMatch = commandMatch(
                text = text,
                customCommands = customCommands,
                commandMode = commandMode,
                minimumConfidence = commandMinimumConfidence,
            ),
            changed = segment.isNotBlank() || text.isNotBlank(),
        )
    }

    private fun update(
        partialText: String,
        committedSegment: String?,
        commandMatch: VoiceCommandMatch?,
        changed: Boolean,
    ): VoiceStreamingTranscriptUpdate {
        val committedText = committedText()
        return VoiceStreamingTranscriptUpdate(
            committedText = committedText,
            partialText = partialText,
            visibleText = listOf(committedText, partialText)
                .filter { it.isNotBlank() }
                .joinToString(" "),
            committedSegment = committedSegment,
            commandMatch = commandMatch,
            changed = changed,
        )
    }

    private fun segmentToCommit(text: String): String {
        if (text.isBlank()) {
            return ""
        }
        val committedText = committedText()
        if (committedText.isBlank()) {
            return text
        }
        if (text.equals(committedText, ignoreCase = true)) {
            return ""
        }
        val committedPrefix = "$committedText "
        if (text.startsWith(committedPrefix, ignoreCase = true)) {
            return text.substring(committedPrefix.length).trim()
        }
        return text
    }

    private fun commandMatch(
        text: String,
        customCommands: VoiceCommandCustomCommands,
        commandMode: Boolean,
        minimumConfidence: Double,
    ): VoiceCommandMatch? {
        if (!commandMode || text.isBlank()) {
            return null
        }
        return commandParser.parse(
            spokenText = text,
            customCommands = customCommands,
            minimumConfidence = minimumConfidence,
        )
    }

    private fun committedText(): String {
        return committedSegments.joinToString(" ")
    }

    private fun String.normalizedTranscript(): String {
        return replace(WhitespaceRegex, " ").trim()
    }

    companion object {
        private val WhitespaceRegex = "\\s+".toRegex()
        // Split on whitespace AND commas / semicolons so the removal walker
        // can match a single token even when it was spoken inside a comma-
        // separated dictated list ("apples, bread, eggs").
        private val ItemTokenSplit = "[\\s,;]+".toRegex()
        private val ItemEdgePunctuation = charArrayOf('.', ',', ';', '!', '?', ':', '\"', '\'')
        // Lower-case connector tokens that should be dropped if they end
        // up dangling around a removed item.
        private val ConnectorTokens: Set<String> = setOf("and", "or", "plus", "with", "&")
    }
}
