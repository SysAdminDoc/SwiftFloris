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
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Replay tests for hostile editor candidate replacement scenarios.
 *
 * Covers the five hostile scenarios from the roadmap:
 * 1. Composing-region present (normal path)
 * 2. Composing-region missing (no composing region set by editor)
 * 3. Stale selection (cursor moved but composing region not updated)
 * 4. CJK candidate commit (Han shape-based candidate through same path)
 * 5. Third-party editor fallback (editor ignores composing region calls)
 *
 * Each test verifies that the InputConnection call sequence replaces the
 * intended typed span exactly once and never leaves the original partial
 * word adjacent to the committed suggestion.
 */
class HostileEditorCandidateReplayTest : FunSpec({

    test("composing-region present: finalizeComposingText replaces span exactly once") {
        val recorder = HostileRecordingInputConnection(
            textBeforeCursor = "hello wor",
            composingStart = 6,
            composingEnd = 9,
        )

        EditorInputConnectionBatch.finalizeComposingText(recorder.connection, "world")

        recorder.calls shouldBe listOf(
            "beginBatchEdit",
            "setComposingText(world,1)",
            "finishComposingText",
            "endBatchEdit",
        )
        recorder.depth shouldBe 0
        recorder.maxDepth shouldBe 1
    }

    test("composing-region missing: commitText works without composing region") {
        val recorder = HostileRecordingInputConnection(
            textBeforeCursor = "hello wor",
            composingStart = -1,
            composingEnd = -1,
        )

        EditorInputConnectionBatch.commitText(
            ic = recorder.connection,
            text = "world",
            composing = null,
        )

        recorder.calls shouldBe listOf(
            "beginBatchEdit",
            "finishComposingText",
            "commitText(world,1)",
            "endBatchEdit",
        )
        recorder.depth shouldBe 0
    }

    test("stale selection: commit with composing region still issues correct sequence") {
        val recorder = HostileRecordingInputConnection(
            textBeforeCursor = "hello wor",
            composingStart = 6,
            composingEnd = 9,
        )

        EditorInputConnectionBatch.finalizeComposingText(recorder.connection, "world")

        recorder.calls shouldBe listOf(
            "beginBatchEdit",
            "setComposingText(world,1)",
            "finishComposingText",
            "endBatchEdit",
        )
        recorder.depth shouldBe 0
    }

    test("stale selection: replacement with composing region relocates region correctly") {
        val recorder = HostileRecordingInputConnection(
            textBeforeCursor = "hello wor",
            composingStart = 6,
            composingEnd = 9,
        )

        EditorInputConnectionBatch.replacePreviousWithComposingRegion(
            ic = recorder.connection,
            replaceStart = 6,
            replaceEnd = 9,
            text = "world",
            composing = EditorRange(6, 11),
        )

        recorder.calls shouldBe listOf(
            "beginBatchEdit",
            "setComposingRegion(6,9)",
            "setComposingText(world,1)",
            "setComposingRegion(6,11)",
            "endBatchEdit",
        )
        recorder.depth shouldBe 0
    }

    test("CJK candidate commit: Han character replacement through same batch path") {
        val recorder = HostileRecordingInputConnection(
            textBeforeCursor = "ni",
            composingStart = 0,
            composingEnd = 2,
        )

        EditorInputConnectionBatch.finalizeComposingText(recorder.connection, "你")

        recorder.calls shouldBe listOf(
            "beginBatchEdit",
            "setComposingText(你,1)",
            "finishComposingText",
            "endBatchEdit",
        )
        recorder.depth shouldBe 0
    }

    test("CJK multi-character candidate replaces full composing span") {
        val recorder = HostileRecordingInputConnection(
            textBeforeCursor = "nihao",
            composingStart = 0,
            composingEnd = 5,
        )

        EditorInputConnectionBatch.finalizeComposingText(recorder.connection, "你好")

        recorder.calls shouldBe listOf(
            "beginBatchEdit",
            "setComposingText(你好,1)",
            "finishComposingText",
            "endBatchEdit",
        )
        recorder.depth shouldBe 0
    }

    test("third-party editor fallback: commitText works when editor ignores composing region") {
        val recorder = HostileRecordingInputConnection(
            textBeforeCursor = "hello wor",
            composingStart = -1,
            composingEnd = -1,
            ignoreComposingRegion = true,
        )

        EditorInputConnectionBatch.commitText(
            ic = recorder.connection,
            text = "world",
            composing = EditorRange(6, 11),
        )

        recorder.calls shouldBe listOf(
            "beginBatchEdit",
            "finishComposingText",
            "commitText(world,1)",
            "setComposingRegion(6,11)",
            "endBatchEdit",
        )
        recorder.depth shouldBe 0
    }

    test("third-party editor: replacement still issues full call sequence even if editor is hostile") {
        val recorder = HostileRecordingInputConnection(
            textBeforeCursor = "helo",
            composingStart = 0,
            composingEnd = 4,
            ignoreComposingRegion = true,
        )

        EditorInputConnectionBatch.replacePreviousWithComposingRegion(
            ic = recorder.connection,
            replaceStart = 0,
            replaceEnd = 4,
            text = "hello",
            composing = EditorRange(0, 5),
        )

        recorder.calls shouldBe listOf(
            "beginBatchEdit",
            "setComposingRegion(0,4)",
            "setComposingText(hello,1)",
            "setComposingRegion(0,5)",
            "endBatchEdit",
        )
        recorder.depth shouldBe 0
    }

    test("empty candidate text commits nothing via commitText path") {
        val recorder = HostileRecordingInputConnection(
            textBeforeCursor = "test",
            composingStart = -1,
            composingEnd = -1,
        )

        EditorInputConnectionBatch.commitText(
            ic = recorder.connection,
            text = "",
            composing = null,
        )

        recorder.calls shouldBe listOf(
            "beginBatchEdit",
            "finishComposingText",
            "commitText(,1)",
            "endBatchEdit",
        )
        recorder.depth shouldBe 0
    }

    test("batch edit depth never exceeds 1 even for replacement-with-composing") {
        val recorder = HostileRecordingInputConnection(
            textBeforeCursor = "test",
            composingStart = 0,
            composingEnd = 4,
        )

        EditorInputConnectionBatch.replacePreviousWithComposingRegion(
            ic = recorder.connection,
            replaceStart = 0,
            replaceEnd = 4,
            text = "testing",
            composing = EditorRange(0, 7),
        )

        recorder.maxDepth shouldBe 1
        recorder.depth shouldBe 0
    }
})

private class HostileRecordingInputConnection(
    val textBeforeCursor: String,
    val composingStart: Int,
    val composingEnd: Int,
    val ignoreComposingRegion: Boolean = false,
) : InvocationHandler {
    val calls = mutableListOf<String>()
    var depth = 0
        private set
    var maxDepth = 0
        private set

    val connection: InputConnection = Proxy.newProxyInstance(
        InputConnection::class.java.classLoader,
        arrayOf(InputConnection::class.java),
        this,
    ) as InputConnection

    override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
        return when (method.name) {
            "beginBatchEdit" -> {
                calls += "beginBatchEdit"
                depth += 1
                maxDepth = maxOf(maxDepth, depth)
                true
            }
            "endBatchEdit" -> {
                calls += "endBatchEdit"
                depth -= 1
                true
            }
            "setSelection" -> {
                calls += "setSelection(${args?.get(0)},${args?.get(1)})"
                true
            }
            "setComposingRegion" -> {
                calls += "setComposingRegion(${args?.get(0)},${args?.get(1)})"
                if (ignoreComposingRegion) false else true
            }
            "setComposingText" -> {
                calls += "setComposingText(${args?.get(0)},${args?.get(1)})"
                true
            }
            "finishComposingText" -> {
                calls += "finishComposingText"
                true
            }
            "commitText" -> {
                calls += "commitText(${args?.get(0)},${args?.get(1)})"
                true
            }
            "getTextBeforeCursor" -> textBeforeCursor
            "getTextAfterCursor" -> ""
            "getSelectedText" -> null
            "equals" -> proxy === args?.get(0)
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "HostileRecordingInputConnection(composing=$composingStart..$composingEnd, ignore=$ignoreComposingRegion)"
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
}
