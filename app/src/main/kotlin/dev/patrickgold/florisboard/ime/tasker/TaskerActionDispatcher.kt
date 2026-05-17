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

package dev.patrickgold.florisboard.ime.tasker

import android.content.Context
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.smartcompose.SensitiveFieldGuard
import dev.patrickgold.florisboard.lib.devtools.flogError
import dev.patrickgold.florisboard.lib.devtools.flogWarning
import dev.patrickgold.florisboard.subtypeManager

internal interface TaskerActionSink {
    fun insertText(text: String): Boolean
    fun pasteClipboard(): Boolean
    fun switchLayout(layoutId: String): Boolean
    fun triggerVoice(mode: String?): Boolean

    /** Editor-info attributes for the currently focused field, or null when
     *  no editor is attached. Used by the dispatcher to refuse text-inserting
     *  automations on password / no-personalised-learning fields. */
    fun currentEditorAttributes(): EditorAttributes? = null
}

/** Minimal value carrier so the sink can answer the privacy-guard probe
 *  without leaking the full Android `EditorInfo` reference into the
 *  dispatcher seam. */
internal data class EditorAttributes(val inputType: Int, val imeOptions: Int)

internal object TaskerActionDispatcher {
    @Volatile
    internal var sinkFactory: (Context) -> TaskerActionSink = { context ->
        AndroidTaskerActionSink(context.applicationContext ?: context)
    }

    fun dispatch(context: Context, action: String?, extras: Map<String, Any?>): Boolean {
        val safeAction = action.orEmpty()
        when (val validation = TaskerIntentContract.validate(safeAction, extras)) {
            ValidationResult.Accept -> Unit
            is ValidationResult.Reject -> {
                flogError { "TaskerActionReceiver rejected '$safeAction': ${validation.reason}" }
                return false
            }
        }

        val sink = sinkFactory(context)
        val handled = when (safeAction) {
            TaskerIntentContract.InsertText.ACTION -> {
                // Defensive: validate() already enforced the cast, but
                // re-check with `as?` so a future contract change that drops
                // the validator can't crash the receiver.
                val text = extras[TaskerIntentContract.InsertText.EXTRA_TEXT] as? String
                    ?: return false.also {
                        flogError { "TaskerActionReceiver: INSERT_TEXT missing/non-string extra" }
                    }
                if (isTargetingSensitiveField(sink)) {
                    flogWarning { "TaskerActionReceiver: INSERT_TEXT suppressed on sensitive field" }
                    return false
                }
                val appendSpace = extras[TaskerIntentContract.InsertText.EXTRA_APPEND_SPACE] as? Boolean ?: false
                sink.insertText(if (appendSpace) "$text " else text)
            }
            TaskerIntentContract.InsertClipboard.ACTION -> {
                if (isTargetingSensitiveField(sink)) {
                    flogWarning { "TaskerActionReceiver: INSERT_CLIP suppressed on sensitive field" }
                    return false
                }
                sink.pasteClipboard()
            }
            TaskerIntentContract.SwitchLayout.ACTION -> {
                val layoutId = extras[TaskerIntentContract.SwitchLayout.EXTRA_LAYOUT_ID] as? String
                    ?: return false.also {
                        flogError { "TaskerActionReceiver: SWITCH_LAYOUT missing/non-string extra" }
                    }
                sink.switchLayout(layoutId)
            }
            TaskerIntentContract.TriggerVoice.ACTION -> {
                val mode = extras[TaskerIntentContract.TriggerVoice.EXTRA_MODE] as? String
                sink.triggerVoice(mode)
            }
            else -> false
        }
        if (!handled) {
            flogError { "TaskerActionReceiver could not dispatch '$safeAction'" }
        }
        return handled
    }

    /** Returns true if the currently focused editor matches the same
     *  password / no-personalised-learning predicates that gate the
     *  smart-compose / translation / MCP surfaces. Sinks that do not yet
     *  carry editor info (older tests, ad-hoc Android variants) return
     *  null from `currentEditorAttributes` and we fail open so that
     *  legacy automations keep working — the password guard still
     *  applies on the AndroidTaskerActionSink production path. */
    private fun isTargetingSensitiveField(sink: TaskerActionSink): Boolean {
        val attrs = sink.currentEditorAttributes() ?: return false
        return SensitiveFieldGuard.isSensitive(attrs.inputType, attrs.imeOptions)
    }

    internal fun resetSinkFactoryForTests() {
        sinkFactory = { context -> AndroidTaskerActionSink(context.applicationContext ?: context) }
    }
}

private class AndroidTaskerActionSink(private val context: Context) : TaskerActionSink {
    override fun insertText(text: String): Boolean {
        return context.editorInstance().value.commitText(text)
    }

    override fun pasteClipboard(): Boolean {
        return context.editorInstance().value.performClipboardPaste()
    }

    override fun switchLayout(layoutId: String): Boolean {
        return context.subtypeManager().value.switchActiveSubtypeCharactersLayout(layoutId)
    }

    override fun triggerVoice(mode: String?): Boolean {
        // The mode flag is reserved for future routing into a command-grammar
        // session (Next-2.4) vs. raw dictation. Today both modes resolve to
        // the same FUTO/Vosk handoff, so we log + accept rather than fail —
        // dropping the call would break Tasker users that pre-emptively set
        // the mode in anticipation of the eventual split.
        if (mode != null) {
            flogWarning {
                "TaskerActionReceiver: TRIGGER_VOICE mode='$mode' accepted (mode routing pending Next-2.4)"
            }
        }
        return FlorisImeService.switchToVoiceInputMethod(showFailureToast = false)
    }

    override fun currentEditorAttributes(): EditorAttributes? {
        // Snapshot the active editor info from the EditorInstance. When no
        // editor is bound (`isRawInputEditor`), there is no field to protect
        // and `null` makes the dispatcher fail open — the IME's own typing
        // pipeline still runs the SensitiveFieldGuard on any subsequent
        // text mutation downstream.
        val info = runCatching { context.editorInstance().value.activeInfo }.getOrNull()
            ?: return null
        if (info.isRawInputEditor) return null
        return EditorAttributes(
            inputType = info.inputAttributes.raw,
            imeOptions = info.imeOptions.raw,
        )
    }
}
