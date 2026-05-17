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
            VoiceCommandAction.REMOVE_ITEM_FROM_LIST -> {
                val item = match.argument?.trim().orEmpty()
                if (item.isEmpty()) {
                    VoiceCommandActionResult.failure(VoiceCommandFailureReason.ACTION_REJECTED)
                } else {
                    actions.removeItemFromList(item)
                }
            }
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

    /**
     * ROADMAP §6 N15.3 — excise a named item from the dictated list
     * (committed transcript buffer + editor state). Default impl
     * returns [VoiceCommandFailureReason.ACTION_REJECTED] so the
     * existing test doubles (and any external implementations of this
     * interface) compile unchanged; the production
     * [EditorVoiceCommandActions] overrides this.
     */
    fun removeItemFromList(item: String): VoiceCommandActionResult {
        return VoiceCommandActionResult.failure(VoiceCommandFailureReason.ACTION_REJECTED)
    }
}

class EditorVoiceCommandActions(
    private val editor: EditorInstance,
    /**
     * Optional buffer reference for the N15.3 REMOVE_ITEM_FROM_LIST
     * path. When null (legacy call sites that pre-date the streaming
     * harness), the action returns ACTION_REJECTED so the executor
     * surfaces a clean failure instead of partially mutating the
     * editor.
     */
    private val transcriptBuffer: StreamingVoiceTranscriptBuffer? = null,
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

    override fun removeItemFromList(item: String): VoiceCommandActionResult {
        // We need the streaming buffer to know what the dictated list
        // looked like before this mutation; without it we don't have a
        // safe reference frame for the diff and refuse rather than guess.
        val buffer = transcriptBuffer
            ?: return VoiceCommandActionResult.failure(VoiceCommandFailureReason.ACTION_REJECTED)
        if (editor.activeInfo.isRawInputEditor) {
            return VoiceCommandActionResult.failure(VoiceCommandFailureReason.ACTION_REJECTED)
        }
        val diff = buffer.removeCommittedItem(item)
        if (!diff.didChange) {
            // Item was not found in the buffer — surface a distinct
            // failure reason so the dictation overlay can render a
            // helpful "no match" hint instead of a generic rejection.
            return VoiceCommandActionResult.failure(VoiceCommandFailureReason.ITEM_NOT_FOUND)
        }
        // Best-effort editor replacement: if the editor's current
        // text-before-cursor ends with the buffer's previous committed
        // text, we can swap exactly that suffix. Otherwise the user
        // typed something the IME didn't see in between (rare given
        // dictation flow), and we fall back to a no-op + signal to
        // avoid corrupting the editor.
        val content = editor.activeContent
        val before = content.textBeforeSelection.toString()
        val previous = diff.previousCommittedText
        if (previous.isNotEmpty() && before.endsWith(previous, ignoreCase = false)) {
            // Select the previously-committed slice + commit the new one.
            // Two ops instead of one because the IME's commitText API
            // does not accept a length-to-replace.
            val startOfOld = before.length - previous.length
            if (!editor.setSelection(startOfOld, content.textBeforeSelection.length)) {
                return VoiceCommandActionResult.failure(VoiceCommandFailureReason.ACTION_REJECTED)
            }
            val committed = editor.commitText(diff.newCommittedText)
            return if (committed) {
                VoiceCommandActionResult.success()
            } else {
                VoiceCommandActionResult.failure(VoiceCommandFailureReason.ACTION_REJECTED)
            }
        }
        // We mutated the buffer but couldn't safely apply to the editor.
        // Signal a distinct failure so the IME can decide whether to
        // re-sync the buffer to the editor or surface a hint to the user.
        return VoiceCommandActionResult.failure(VoiceCommandFailureReason.EDITOR_OUT_OF_SYNC)
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
    /**
     * ROADMAP §6 N15.3 — the parameterised "remove <item>" command
     * matched but the requested item was not found in the dictated-list
     * buffer. Distinct from ACTION_REJECTED so the dictation overlay
     * can render "no match in your list" instead of a generic failure.
     */
    ITEM_NOT_FOUND,
    /**
     * ROADMAP §6 N15.3 — the buffer was mutated but the editor's
     * text-before-cursor no longer ends with the previous committed
     * text (the user typed manually between dictation chunks), so the
     * action refuses to corrupt the editor. The IME can re-sync the
     * buffer to the editor and ask the user to retry.
     */
    EDITOR_OUT_OF_SYNC,
}
