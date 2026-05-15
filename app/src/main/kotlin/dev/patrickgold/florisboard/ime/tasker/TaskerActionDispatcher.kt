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
import dev.patrickgold.florisboard.lib.devtools.flogError
import dev.patrickgold.florisboard.subtypeManager

internal interface TaskerActionSink {
    fun insertText(text: String): Boolean
    fun pasteClipboard(): Boolean
    fun switchLayout(layoutId: String): Boolean
    fun triggerVoice(mode: String?): Boolean
}

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
                val text = extras[TaskerIntentContract.InsertText.EXTRA_TEXT] as String
                val appendSpace = extras[TaskerIntentContract.InsertText.EXTRA_APPEND_SPACE] as? Boolean ?: false
                sink.insertText(if (appendSpace) "$text " else text)
            }
            TaskerIntentContract.InsertClipboard.ACTION -> {
                sink.pasteClipboard()
            }
            TaskerIntentContract.SwitchLayout.ACTION -> {
                val layoutId = extras[TaskerIntentContract.SwitchLayout.EXTRA_LAYOUT_ID] as String
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
        return FlorisImeService.switchToVoiceInputMethod(showFailureToast = false)
    }
}
