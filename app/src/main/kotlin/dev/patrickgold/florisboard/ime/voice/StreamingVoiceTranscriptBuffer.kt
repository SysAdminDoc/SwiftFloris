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
    }
}
