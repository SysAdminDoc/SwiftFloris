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

class VoiceCommandExecutorTest : FunSpec({
    test("maps every command action to editor actions") {
        val actions = RecordingVoiceCommandActions()
        val executor = VoiceCommandExecutor(actions)

        VoiceCommandAction.entries.forEach { action ->
            executor.execute(match(action)).successful shouldBe true
        }

        actions.calls shouldBe listOf(
            "deleteThat",
            "undo",
            "redo",
            "selectAll",
            "newParagraph",
            "newLine",
            "capitalizeNextWord",
            "goToStart",
            "goToEnd",
        )
    }

    test("propagates command action failure reason") {
        val actions = RecordingVoiceCommandActions(
            failures = mapOf("deleteThat" to VoiceCommandFailureReason.NO_TEXT_TO_DELETE),
        )
        val result = VoiceCommandExecutor(actions).execute(match(VoiceCommandAction.DELETE_THAT))

        result.successful shouldBe false
        result.failureReason shouldBe VoiceCommandFailureReason.NO_TEXT_TO_DELETE
    }
})

private fun match(action: VoiceCommandAction): VoiceCommandMatch {
    return VoiceCommandMatch(
        action = action,
        spokenText = action.name.lowercase(),
        matchedPhrase = action.name.lowercase(),
        matchedAlias = null,
        confidence = 1.0,
    )
}

private class RecordingVoiceCommandActions(
    private val failures: Map<String, VoiceCommandFailureReason> = emptyMap(),
) : VoiceCommandActions {
    val calls = mutableListOf<String>()

    override fun deleteThat(): VoiceCommandActionResult = record("deleteThat")
    override fun undo(): VoiceCommandActionResult = record("undo")
    override fun redo(): VoiceCommandActionResult = record("redo")
    override fun selectAll(): VoiceCommandActionResult = record("selectAll")
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
