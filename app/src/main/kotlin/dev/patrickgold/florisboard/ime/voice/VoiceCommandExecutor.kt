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

import android.view.KeyEvent
import dev.patrickgold.florisboard.ime.editor.EditorInstance
import dev.patrickgold.florisboard.ime.editor.OperationUnit
import dev.patrickgold.florisboard.lib.devtools.LogTopic
import dev.patrickgold.florisboard.lib.devtools.flogInfo

class VoiceCommandExecutor(
    private val actions: VoiceCommandActions,
) {
    fun execute(match: VoiceCommandMatch): VoiceCommandExecutionResult {
        val actionResult = when (match.action) {
            VoiceCommandAction.DELETE_THAT -> actions.deleteThat()
            VoiceCommandAction.UNDO -> actions.undo()
            VoiceCommandAction.REDO -> actions.redo()
            VoiceCommandAction.SELECT_ALL -> actions.selectAll()
            VoiceCommandAction.CLEAR_TEXT -> actions.clearText()
            VoiceCommandAction.NEW_PARAGRAPH -> actions.newParagraph()
            VoiceCommandAction.NEW_LINE -> actions.newLine()
            VoiceCommandAction.CAPITALIZE_NEXT_WORD -> actions.capitalizeNextWord()
            VoiceCommandAction.GO_TO_START -> actions.goToStart()
            VoiceCommandAction.GO_TO_END -> actions.goToEnd()
        }
        val result = VoiceCommandExecutionResult(
            match = match,
            successful = actionResult.successful,
            failureReason = actionResult.failureReason,
        )
        flogInfo(LogTopic.OTHER) {
            "Voice command action=${match.action} confidence=${match.confidence} successful=${result.successful}" +
                result.failureReason?.let { " failure=$it" }.orEmpty()
        }
        return result
    }
}

interface VoiceCommandActions {
    fun insertText(text: String): VoiceCommandActionResult
    fun deleteThat(): VoiceCommandActionResult
    fun undo(): VoiceCommandActionResult
    fun redo(): VoiceCommandActionResult
    fun selectAll(): VoiceCommandActionResult
    fun clearText(): VoiceCommandActionResult
    fun newParagraph(): VoiceCommandActionResult
    fun newLine(): VoiceCommandActionResult
    fun capitalizeNextWord(): VoiceCommandActionResult
    fun goToStart(): VoiceCommandActionResult
    fun goToEnd(): VoiceCommandActionResult
}

class EditorVoiceCommandActions(
    private val editor: EditorInstance,
) : VoiceCommandActions {
    override fun insertText(text: String): VoiceCommandActionResult {
        return editor.commitText(text).toVoiceCommandResult()
    }

    override fun deleteThat(): VoiceCommandActionResult {
        val content = editor.activeContent
        if (
            !editor.activeInfo.isRawInputEditor &&
            content.selectedText.isEmpty() &&
            content.textBeforeSelection.isEmpty()
        ) {
            return VoiceCommandActionResult.failure(VoiceCommandFailureReason.NO_TEXT_TO_DELETE)
        }
        return editor.deleteBackwards(OperationUnit.WORDS).toVoiceCommandResult()
    }

    override fun undo(): VoiceCommandActionResult {
        return editor.performUndo().toVoiceCommandResult()
    }

    override fun redo(): VoiceCommandActionResult {
        return editor.performRedo().toVoiceCommandResult()
    }

    override fun selectAll(): VoiceCommandActionResult {
        return editor.performClipboardSelectAll().toVoiceCommandResult()
    }

    override fun clearText(): VoiceCommandActionResult {
        val content = editor.activeContent
        if (
            !editor.activeInfo.isRawInputEditor &&
            content.selectedText.isEmpty() &&
            content.textBeforeSelection.isEmpty() &&
            content.textAfterSelection.isEmpty()
        ) {
            return VoiceCommandActionResult.failure(VoiceCommandFailureReason.NO_TEXT_TO_DELETE)
        }
        val selected = content.selectedText.isNotEmpty() || editor.performClipboardSelectAll()
        if (!selected) {
            return VoiceCommandActionResult.failure(VoiceCommandFailureReason.ACTION_REJECTED)
        }
        return editor.commitText("").toVoiceCommandResult()
    }

    override fun newParagraph(): VoiceCommandActionResult {
        return editor.commitText("\n\n").toVoiceCommandResult()
    }

    override fun newLine(): VoiceCommandActionResult {
        return editor.performEnter().toVoiceCommandResult()
    }

    override fun capitalizeNextWord(): VoiceCommandActionResult {
        return editor.performCapitalizeNextWord().toVoiceCommandResult()
    }

    override fun goToStart(): VoiceCommandActionResult {
        return editor.sendDownUpKeyEvent(
            keyEventCode = KeyEvent.KEYCODE_MOVE_HOME,
            metaState = editor.meta(ctrl = true),
        ).toVoiceCommandResult()
    }

    override fun goToEnd(): VoiceCommandActionResult {
        return editor.sendDownUpKeyEvent(
            keyEventCode = KeyEvent.KEYCODE_MOVE_END,
            metaState = editor.meta(ctrl = true),
        ).toVoiceCommandResult()
    }

    private fun Boolean.toVoiceCommandResult(): VoiceCommandActionResult {
        return if (this) {
            VoiceCommandActionResult.success()
        } else {
            VoiceCommandActionResult.failure(VoiceCommandFailureReason.ACTION_REJECTED)
        }
    }
}

data class VoiceCommandActionResult(
    val successful: Boolean,
    val failureReason: VoiceCommandFailureReason? = null,
) {
    companion object {
        fun success(): VoiceCommandActionResult {
            return VoiceCommandActionResult(successful = true)
        }

        fun failure(reason: VoiceCommandFailureReason): VoiceCommandActionResult {
            return VoiceCommandActionResult(successful = false, failureReason = reason)
        }
    }
}

data class VoiceCommandExecutionResult(
    val match: VoiceCommandMatch,
    val successful: Boolean,
    val failureReason: VoiceCommandFailureReason? = null,
)

enum class VoiceCommandFailureReason {
    ACTION_REJECTED,
    NO_TEXT_TO_DELETE,
}
