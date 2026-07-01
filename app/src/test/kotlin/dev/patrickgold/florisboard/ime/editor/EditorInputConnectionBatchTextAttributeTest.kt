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
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

@RunWith(AndroidJUnit4::class)
class EditorInputConnectionBatchTextAttributeTest {
    @Test
    fun selectedSuggestionAttributeGateRequiresSelectionAndApi37() {
        assertFalse(EditorInputConnectionBatch.shouldUseSelectedTextSuggestionAttribute(false, 37))
        assertFalse(EditorInputConnectionBatch.shouldUseSelectedTextSuggestionAttribute(true, 35))
        assertTrue(EditorInputConnectionBatch.shouldUseSelectedTextSuggestionAttribute(true, 37))
    }

    @Test
    @Config(sdk = [35])
    fun selectedCjkFinalizeFallsBackBeforeApi37() {
        val recorder = AttributeRecordingInputConnection()

        EditorInputConnectionBatch.finalizeComposingText(
            ic = recorder.connection,
            text = SelectedCjkText,
            selectedTextSuggestion = true,
        )

        assertEquals(
            listOf(
                "beginBatchEdit",
                "setComposingText($SelectedCjkText,1)",
                "finishComposingText",
                "endBatchEdit",
            ),
            recorder.calls,
        )
    }

    @Test
    fun selectedCjkFinalizeAttachesTextSuggestionAttributeOnApi37() {
        val recorder = AttributeRecordingInputConnection()

        EditorInputConnectionBatch.finalizeComposingText(
            ic = recorder.connection,
            text = SelectedCjkText,
            selectedTextSuggestion = true,
            sdkInt = 37,
            textSuggestionAttributeWriter = RecordingTextSuggestionAttributeWriter(recorder),
        )

        assertEquals(
            listOf(
                "beginBatchEdit",
                "setComposingTextWithAttribute($SelectedCjkText,1,true)",
                "finishComposingText",
                "endBatchEdit",
            ),
            recorder.calls,
        )
    }

    @Test
    fun ordinaryFinalizeDoesNotAttachTextSuggestionAttributeOnApi37() {
        val recorder = AttributeRecordingInputConnection()

        EditorInputConnectionBatch.finalizeComposingText(
            ic = recorder.connection,
            text = SelectedCjkText,
            selectedTextSuggestion = false,
            sdkInt = 37,
        )

        assertEquals(
            listOf(
                "beginBatchEdit",
                "setComposingText($SelectedCjkText,1)",
                "finishComposingText",
                "endBatchEdit",
            ),
            recorder.calls,
        )
    }

    @Test
    fun selectedCjkCommitAttachesTextSuggestionAttributeOnApi37() {
        val recorder = AttributeRecordingInputConnection()

        EditorInputConnectionBatch.commitText(
            ic = recorder.connection,
            text = SelectedCjkText,
            composing = null,
            selectedTextSuggestion = true,
            sdkInt = 37,
            textSuggestionAttributeWriter = RecordingTextSuggestionAttributeWriter(recorder),
        )

        assertEquals(
            listOf(
                "beginBatchEdit",
                "finishComposingText",
                "commitTextWithAttribute($SelectedCjkText,1,true)",
                "endBatchEdit",
            ),
            recorder.calls,
        )
    }
}

private const val SelectedCjkText = "\u4f60"

private class RecordingTextSuggestionAttributeWriter(
    private val recorder: AttributeRecordingInputConnection,
) : EditorInputConnectionBatch.TextSuggestionAttributeWriter {
    override fun commitText(
        ic: InputConnection,
        text: CharSequence,
        newCursorPosition: Int,
    ): Boolean {
        recorder.calls += "commitTextWithAttribute($text,$newCursorPosition,true)"
        return true
    }

    override fun setComposingText(
        ic: InputConnection,
        text: CharSequence,
        newCursorPosition: Int,
    ): Boolean {
        recorder.calls += "setComposingTextWithAttribute($text,$newCursorPosition,true)"
        return true
    }
}

private class AttributeRecordingInputConnection : InvocationHandler {
    val calls = mutableListOf<String>()

    val connection: InputConnection = Proxy.newProxyInstance(
        InputConnection::class.java.classLoader,
        arrayOf(InputConnection::class.java),
        this,
    ) as InputConnection

    override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
        return when (method.name) {
            "beginBatchEdit" -> {
                calls += "beginBatchEdit"
                true
            }
            "endBatchEdit" -> {
                calls += "endBatchEdit"
                true
            }
            "finishComposingText" -> {
                calls += "finishComposingText"
                true
            }
            "setComposingText" -> {
                calls += recordTextMutation(method.name, args)
                true
            }
            "commitText" -> {
                calls += recordTextMutation(method.name, args)
                true
            }
            "equals" -> proxy === args?.get(0)
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "AttributeRecordingInputConnection"
            else -> defaultReturnValue(method.returnType)
        }
    }

    private fun defaultReturnValue(returnType: Class<*>): Any? {
        return when (returnType) {
            java.lang.Boolean.TYPE -> false
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Character.TYPE -> 0.toChar()
            java.lang.Double.TYPE -> 0.0
            java.lang.Float.TYPE -> 0f
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Void.TYPE -> null
            else -> null
        }
    }

    private fun recordTextMutation(methodName: String, args: Array<out Any?>?): String {
        val text = args?.getOrNull(0)
        val cursor = args?.getOrNull(1)
        return if ((args?.size ?: 0) >= 3) {
            "${methodName}WithAttribute($text,$cursor,true)"
        } else {
            "$methodName($text,$cursor)"
        }
    }
}
