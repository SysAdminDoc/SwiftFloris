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

class VoiceCommandFallbackHandler(
    private val parser: VoiceCommandParser = VoiceCommandParser(),
) {
    fun handleTranscript(
        spokenText: String,
        actions: VoiceCommandActions,
        customCommands: VoiceCommandCustomCommands = VoiceCommandCustomCommands.Empty,
        commandMinimumConfidence: Double = VoiceCommandParser.DEFAULT_MINIMUM_CONFIDENCE,
        suggestionMinimumConfidence: Double = VoiceCommandParser.DEFAULT_SUGGESTION_MINIMUM_CONFIDENCE,
    ): VoiceCommandTranscriptResult {
        val textToInsert = spokenText.trim()
        if (textToInsert.isEmpty()) {
            return VoiceCommandTranscriptResult.IgnoredBlank
        }

        parser.parse(
            spokenText = spokenText,
            customCommands = customCommands,
            minimumConfidence = commandMinimumConfidence,
        )?.let { match ->
            return VoiceCommandTranscriptResult.ExecutedCommand(
                executionResult = VoiceCommandExecutor(actions).execute(match),
            )
        }

        val suggestionThreshold = suggestionMinimumConfidence.coerceIn(0.0, commandMinimumConfidence)
        parser.parse(
            spokenText = spokenText,
            customCommands = customCommands,
            minimumConfidence = suggestionThreshold,
        )?.let { match ->
            return VoiceCommandTranscriptResult.SuggestionPending(
                suggestion = VoiceCommandSuggestion(
                    match = match,
                    spokenText = textToInsert,
                    commandMinimumConfidence = commandMinimumConfidence,
                    suggestionMinimumConfidence = suggestionThreshold,
                ),
            )
        }

        return insertTranscriptText(textToInsert, actions)
    }

    fun acceptSuggestion(
        suggestion: VoiceCommandSuggestion,
        actions: VoiceCommandActions,
    ): VoiceCommandExecutionResult {
        return VoiceCommandExecutor(actions).execute(suggestion.match)
    }

    fun rejectSuggestion(
        suggestion: VoiceCommandSuggestion,
        actions: VoiceCommandActions,
    ): VoiceCommandTranscriptResult.InsertedText {
        return insertTranscriptText(suggestion.spokenText, actions)
    }

    fun handleError(error: VoiceError): VoiceCommandErrorHandlingResult {
        return when (error) {
            VoiceError.NotAvailable,
            VoiceError.NotEnabled,
            VoiceError.PermissionDenied,
            -> VoiceCommandErrorHandlingResult(
                error = error,
                transcriptionState = TranscriptionState.Unavailable,
                recovery = VoiceCommandErrorRecovery.NEEDS_SETUP,
            )

            VoiceError.NetworkTimeout,
            VoiceError.NetworkError,
            VoiceError.RecognizerBusy,
            VoiceError.ServerError,
            VoiceError.SpeechTimeout,
            -> VoiceCommandErrorHandlingResult(
                error = error,
                transcriptionState = TranscriptionState.Error,
                recovery = VoiceCommandErrorRecovery.RETRYABLE,
            )

            VoiceError.NoMatch,
            VoiceError.NoResults,
            -> VoiceCommandErrorHandlingResult(
                error = error,
                transcriptionState = TranscriptionState.Ready,
                recovery = VoiceCommandErrorRecovery.NO_TRANSCRIPT,
            )

            VoiceError.AudioError,
            VoiceError.ClientError,
            is VoiceError.StartFailed,
            is VoiceError.StopFailed,
            is VoiceError.CancelFailed,
            is VoiceError.UnknownError,
            -> VoiceCommandErrorHandlingResult(
                error = error,
                transcriptionState = TranscriptionState.Error,
                recovery = VoiceCommandErrorRecovery.RETRYABLE,
            )
        }
    }

    private fun insertTranscriptText(
        text: String,
        actions: VoiceCommandActions,
    ): VoiceCommandTranscriptResult.InsertedText {
        val result = actions.insertText(text)
        return VoiceCommandTranscriptResult.InsertedText(
            text = text,
            successful = result.successful,
            failureReason = result.failureReason,
        )
    }
}

sealed class VoiceCommandTranscriptResult {
    object IgnoredBlank : VoiceCommandTranscriptResult()

    data class ExecutedCommand(
        val executionResult: VoiceCommandExecutionResult,
    ) : VoiceCommandTranscriptResult()

    data class SuggestionPending(
        val suggestion: VoiceCommandSuggestion,
    ) : VoiceCommandTranscriptResult()

    data class InsertedText(
        val text: String,
        val successful: Boolean,
        val failureReason: VoiceCommandFailureReason? = null,
    ) : VoiceCommandTranscriptResult()
}

data class VoiceCommandSuggestion(
    val match: VoiceCommandMatch,
    val spokenText: String,
    val commandMinimumConfidence: Double,
    val suggestionMinimumConfidence: Double,
)

data class VoiceCommandErrorHandlingResult(
    val error: VoiceError,
    val transcriptionState: TranscriptionState,
    val recovery: VoiceCommandErrorRecovery,
) {
    val retryable: Boolean
        get() = recovery == VoiceCommandErrorRecovery.RETRYABLE
}

enum class VoiceCommandErrorRecovery {
    NEEDS_SETUP,
    RETRYABLE,
    NO_TRANSCRIPT,
}
