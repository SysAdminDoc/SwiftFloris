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
            executor.execute(matchFor(action)).successful shouldBe true
        }

        actions.calls shouldBe listOf(
            "deleteThat",
            "undo",
            "redo",
            "selectAll",
            "clearText",
            "newParagraph",
            "newLine",
            "capitalizeNextWord",
            "goToStart",
            "goToEnd",
            "removeItemFromList:apples",
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

    // ROADMAP §6 N15.3 — Smart Edit voice REMOVE_ITEM_FROM_LIST executor tests.

    test("REMOVE_ITEM_FROM_LIST short-circuits when argument is null") {
        val actions = RecordingVoiceCommandActions()
        val result = VoiceCommandExecutor(actions).execute(
            VoiceCommandMatch(
                action = VoiceCommandAction.REMOVE_ITEM_FROM_LIST,
                spokenText = "no longer want",
                matchedPhrase = "no longer want",
                matchedAlias = null,
                confidence = 1.0,
                argument = null,
            ),
        )

        result.successful shouldBe false
        result.failureReason shouldBe VoiceCommandFailureReason.ACTION_REJECTED
        // The action sink must NOT have been called when the parser
        // failed to extract an argument — otherwise we'd hand the
        // editor an empty string and risk excising the wrong text.
        actions.calls shouldBe emptyList()
    }

    test("REMOVE_ITEM_FROM_LIST short-circuits when argument is whitespace") {
        val actions = RecordingVoiceCommandActions()
        val result = VoiceCommandExecutor(actions).execute(
            VoiceCommandMatch(
                action = VoiceCommandAction.REMOVE_ITEM_FROM_LIST,
                spokenText = "scratch    ",
                matchedPhrase = "scratch",
                matchedAlias = null,
                confidence = 1.0,
                argument = "   ",
            ),
        )

        result.successful shouldBe false
        result.failureReason shouldBe VoiceCommandFailureReason.ACTION_REJECTED
        actions.calls shouldBe emptyList()
    }

    test("REMOVE_ITEM_FROM_LIST forwards trimmed argument to the action sink") {
        val actions = RecordingVoiceCommandActions()
        val result = VoiceCommandExecutor(actions).execute(
            VoiceCommandMatch(
                action = VoiceCommandAction.REMOVE_ITEM_FROM_LIST,
                spokenText = "no longer want apples",
                matchedPhrase = "no longer want",
                matchedAlias = null,
                confidence = 1.0,
                argument = "  apples  ",
            ),
        )

        result.successful shouldBe true
        actions.calls shouldBe listOf("removeItemFromList:apples")
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

/** Helper that adds the required argument for REMOVE_ITEM_FROM_LIST so the
 *  loop-every-action test doesn't trip the new short-circuit guard. */
private fun matchFor(action: VoiceCommandAction): VoiceCommandMatch {
    val base = match(action)
    return if (action == VoiceCommandAction.REMOVE_ITEM_FROM_LIST) {
        base.copy(argument = "apples")
    } else {
        base
    }
}

private class RecordingVoiceCommandActions(
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
    override fun removeItemFromList(item: String): VoiceCommandActionResult =
        record("removeItemFromList:$item")

    private fun record(name: String): VoiceCommandActionResult {
        calls.add(name)
        return failures[name]?.let { VoiceCommandActionResult.failure(it) }
            ?: VoiceCommandActionResult.success()
    }
}
