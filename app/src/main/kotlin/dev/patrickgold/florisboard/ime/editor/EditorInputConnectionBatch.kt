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

import android.view.inputmethod.InputConnection

internal object EditorInputConnectionBatch {
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

    fun commitText(ic: InputConnection, text: String, composing: EditorRange?) {
        runWithBatchEdit(ic) {
            finishComposingText()
            commitText(text, 1)
            if (composing != null) {
                setComposingRegion(composing)
            }
        }
    }

    fun finalizeComposingText(ic: InputConnection, text: String) {
        runWithBatchEdit(ic) {
            setComposingText(text, 1)
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
}
