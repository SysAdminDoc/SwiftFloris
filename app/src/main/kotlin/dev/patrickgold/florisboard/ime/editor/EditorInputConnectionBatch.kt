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

package dev.patrickgold.florisboard.ime.editor

import android.os.Build
import android.view.inputmethod.InputConnection
import android.view.inputmethod.TextAttribute
import androidx.annotation.RequiresApi

internal object EditorInputConnectionBatch {
    private const val Android17Api = 37

    internal interface TextSuggestionAttributeWriter {
        fun commitText(
            ic: InputConnection,
            text: CharSequence,
            newCursorPosition: Int,
        ): Boolean

        fun setComposingText(
            ic: InputConnection,
            text: CharSequence,
            newCursorPosition: Int,
        ): Boolean
    }

    internal fun shouldUseSelectedTextSuggestionAttribute(
        selectedTextSuggestion: Boolean,
        sdkInt: Int = Build.VERSION.SDK_INT,
    ): Boolean {
        return selectedTextSuggestion && sdkInt >= Android17Api
    }

    inline fun runWithBatchEdit(ic: InputConnection, block: InputConnection.() -> Unit) {
        ic.beginBatchEdit()
        try {
            ic.block()
        } finally {
            ic.endBatchEdit()
        }
    }

    fun applySelection(ic: InputConnection, selection: EditorRange, composing: EditorRange) {
        runWithBatchEdit(ic) {
            setSelection(selection.start, selection.end)
            setComposingRegion(composing)
        }
    }

    fun replacePreviousWithComposingRegion(
        ic: InputConnection,
        replaceStart: Int,
        replaceEnd: Int,
        text: String,
        composing: EditorRange,
    ) {
        runWithBatchEdit(ic) {
            setComposingRegion(replaceStart, replaceEnd)
            setComposingText(text, 1)
            setComposingRegion(composing)
        }
    }

    fun commitText(
        ic: InputConnection,
        text: String,
        composing: EditorRange?,
        selectedTextSuggestion: Boolean = false,
        sdkInt: Int = Build.VERSION.SDK_INT,
        textSuggestionAttributeWriter: TextSuggestionAttributeWriter = Api37TextAttributes,
    ) {
        runWithBatchEdit(ic) {
            finishComposingText()
            commitTextWithAttributes(text, 1, selectedTextSuggestion, sdkInt, textSuggestionAttributeWriter)
            if (composing != null) {
                setComposingRegion(composing)
            }
        }
    }

    fun finalizeComposingText(
        ic: InputConnection,
        text: String,
        selectedTextSuggestion: Boolean = false,
        sdkInt: Int = Build.VERSION.SDK_INT,
        textSuggestionAttributeWriter: TextSuggestionAttributeWriter = Api37TextAttributes,
    ) {
        runWithBatchEdit(ic) {
            setComposingTextWithAttributes(text, 1, selectedTextSuggestion, sdkInt, textSuggestionAttributeWriter)
            finishComposingText()
        }
    }

    private fun InputConnection.setComposingRegion(composing: EditorRange) {
        if (composing.isValid) {
            setComposingRegion(composing.start, composing.end)
        } else {
            finishComposingText()
        }
    }

    private fun InputConnection.commitTextWithAttributes(
        text: CharSequence,
        newCursorPosition: Int,
        selectedTextSuggestion: Boolean,
        sdkInt: Int,
        textSuggestionAttributeWriter: TextSuggestionAttributeWriter,
    ): Boolean {
        return if (shouldUseSelectedTextSuggestionAttribute(selectedTextSuggestion, sdkInt)) {
            textSuggestionAttributeWriter.commitText(this, text, newCursorPosition)
        } else {
            commitText(text, newCursorPosition)
        }
    }

    private fun InputConnection.setComposingTextWithAttributes(
        text: CharSequence,
        newCursorPosition: Int,
        selectedTextSuggestion: Boolean,
        sdkInt: Int,
        textSuggestionAttributeWriter: TextSuggestionAttributeWriter,
    ): Boolean {
        return if (shouldUseSelectedTextSuggestionAttribute(selectedTextSuggestion, sdkInt)) {
            textSuggestionAttributeWriter.setComposingText(this, text, newCursorPosition)
        } else {
            setComposingText(text, newCursorPosition)
        }
    }

    private object Api37TextAttributes : TextSuggestionAttributeWriter {
        @RequiresApi(Android17Api)
        override fun commitText(
            ic: InputConnection,
            text: CharSequence,
            newCursorPosition: Int,
        ): Boolean {
            return ic.commitText(text, newCursorPosition, selectedTextSuggestionAttribute())
        }

        @RequiresApi(Android17Api)
        override fun setComposingText(
            ic: InputConnection,
            text: CharSequence,
            newCursorPosition: Int,
        ): Boolean {
            return ic.setComposingText(text, newCursorPosition, selectedTextSuggestionAttribute())
        }

        @RequiresApi(Android17Api)
        private fun selectedTextSuggestionAttribute(): TextAttribute {
            return TextAttribute.Builder()
                .setTextSuggestionSelected(true)
                .build()
        }
    }
}
