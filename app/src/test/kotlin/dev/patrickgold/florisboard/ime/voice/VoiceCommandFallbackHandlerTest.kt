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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class VoiceCommandFallbackHandlerTest : FunSpec({
    test("executes confident command transcripts") {
        val actions = FallbackRecordingVoiceCommandActions()
        val result = VoiceCommandFallbackHandler().handleTranscript("select all", actions)

        (result as VoiceCommandTranscriptResult.ExecutedCommand)
            .executionResult.match.action shouldBe VoiceCommandAction.SELECT_ALL
        actions.calls shouldBe listOf("selectAll")
    }

    test("inserts unrecognized command-like text") {
        val actions = FallbackRecordingVoiceCommandActions()
        val result = VoiceCommandFallbackHandler().handleTranscript("delete the old note after lunch", actions)

        result shouldBe VoiceCommandTranscriptResult.InsertedText(
            text = "delete the old note after lunch",
            successful = true,
        )
        actions.calls shouldBe listOf("insertText:delete the old note after lunch")
    }

    test("holds low confidence command matches for accept or reject") {
        val actions = FallbackRecordingVoiceCommandActions()
        val result = VoiceCommandFallbackHandler().handleTranscript("go start", actions)

        val suggestion = (result as VoiceCommandTranscriptResult.SuggestionPending).suggestion
        suggestion.match.action shouldBe VoiceCommandAction.GO_TO_START
        actions.calls shouldBe emptyList()

        VoiceCommandFallbackHandler().acceptSuggestion(suggestion, actions)
        actions.calls shouldBe listOf("goToStart")

        val rejectionActions = FallbackRecordingVoiceCommandActions()
        VoiceCommandFallbackHandler().rejectSuggestion(suggestion, rejectionActions)
        rejectionActions.calls shouldBe listOf("insertText:go start")
    }

    test("ignores blank transcripts without editor mutation") {
        val actions = FallbackRecordingVoiceCommandActions()
        val result = VoiceCommandFallbackHandler().handleTranscript("   ", actions)

        result shouldBe VoiceCommandTranscriptResult.IgnoredBlank
        actions.calls shouldBe emptyList()
    }

    test("maps network timeout to retryable error state") {
        val result = VoiceCommandFallbackHandler().handleError(VoiceError.NetworkTimeout)

        result.transcriptionState shouldBe TranscriptionState.Error
        result.recovery shouldBe VoiceCommandErrorRecovery.RETRYABLE
        result.retryable shouldBe true
    }

    test("maps setup errors to unavailable state") {
        val result = VoiceCommandFallbackHandler().handleError(VoiceError.PermissionDenied)

        result.transcriptionState shouldBe TranscriptionState.Unavailable
        result.recovery shouldBe VoiceCommandErrorRecovery.NEEDS_SETUP
        result.retryable shouldBe false
    }
})

private class FallbackRecordingVoiceCommandActions(
    private val failures: Map<String, VoiceCommandFailureReason> = emptyMap(),
) : VoiceCommandActions {
    val calls = mutableListOf<String>()

    override fun insertText(text: String): VoiceCommandActionResult = record("insertText:$text")
    override fun deleteThat(): VoiceCommandActionResult = record("deleteThat")
    override fun undo(): VoiceCommandActionResult = record("undo")
    override fun redo(): VoiceCommandActionResult = record("redo")
    override fun selectAll(): VoiceCommandActionResult = record("selectAll")
    override fun clearText(): VoiceCommandActionResult = record("clearText")
    override fun newParagraph(): VoiceCommandActionResult = record("newParagraph")
    override fun newLine(): VoiceCommandActionResult = record("newLine")
    override fun capitalizeNextWord(): VoiceCommandActionResult = record("capitalizeNextWord")
    override fun goToStart(): VoiceCommandActionResult = record("goToStart")
    override fun goToEnd(): VoiceCommandActionResult = record("goToEnd")

    private fun record(name: String): VoiceCommandActionResult {
        calls.add(name)
        return failures[name]?.let { VoiceCommandActionResult.failure(it) }
            ?: VoiceCommandActionResult.success()
    }
}
