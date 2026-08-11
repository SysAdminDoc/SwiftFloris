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
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class EditorInputConnectionBatchTest : FunSpec({
    test("selection updates stay inside one minimal batch") {
        val recorder = RecordingInputConnection()

        EditorInputConnectionBatch.applySelection(
            ic = recorder.connection,
            selection = EditorRange.cursor(4),
            composing = EditorRange(1, 4),
        )

        recorder.calls shouldBe listOf(
            "beginBatchEdit",
            "setSelection(4,4)",
            "setComposingRegion(1,4)",
            "endBatchEdit",
        )
        recorder.depth shouldBe 0
        recorder.maxDepth shouldBe 1
    }

    test("replacement commit preserves composing-region call order") {
        val recorder = RecordingInputConnection()

        EditorInputConnectionBatch.replacePreviousWithComposingRegion(
            ic = recorder.connection,
            replaceStart = 2,
            replaceEnd = 5,
            text = "x",
            composing = EditorRange(3, 6),
        )

        recorder.calls shouldBe listOf(
            "beginBatchEdit",
            // Selection-based replacement, not a composing region: hosts that
            // ignore setComposingRegion duplicated the word. See HostDesyncReplayTest.
            "setSelection(2,5)",
            "finishComposingText",
            "commitText(x,1)",
            "setComposingRegion(3,6)",
            "endBatchEdit",
        )
        recorder.depth shouldBe 0
        recorder.maxDepth shouldBe 1
    }

    test("text commits finish composing before commit and restore composing after commit") {
        val recorder = RecordingInputConnection()

        EditorInputConnectionBatch.commitText(
            ic = recorder.connection,
            text = "hi",
            composing = EditorRange(0, 2),
        )

        recorder.calls shouldBe listOf(
            "beginBatchEdit",
            "finishComposingText",
            "commitText(hi,1)",
            "setComposingRegion(0,2)",
            "endBatchEdit",
        )
        recorder.depth shouldBe 0
        recorder.maxDepth shouldBe 1
    }

    test("raw text commits skip composing-region restoration") {
        val recorder = RecordingInputConnection()

        EditorInputConnectionBatch.commitText(
            ic = recorder.connection,
            text = "raw",
            composing = null,
        )

        recorder.calls shouldBe listOf(
            "beginBatchEdit",
            "finishComposingText",
            "commitText(raw,1)",
            "endBatchEdit",
        )
        recorder.depth shouldBe 0
        recorder.maxDepth shouldBe 1
    }

    test("finalize composing text writes composing text then finishes composing") {
        val recorder = RecordingInputConnection()

        EditorInputConnectionBatch.finalizeComposingText(recorder.connection, "done")

        recorder.calls shouldBe listOf(
            "beginBatchEdit",
            "setComposingText(done,1)",
            "finishComposingText",
            "endBatchEdit",
        )
        recorder.depth shouldBe 0
        recorder.maxDepth shouldBe 1
    }

    test("batch edit always ends when a mutation throws") {
        val recorder = RecordingInputConnection()

        shouldThrow<IllegalStateException> {
            EditorInputConnectionBatch.runWithBatchEdit(recorder.connection) {
                throw IllegalStateException("boom")
            }
        }

        recorder.calls shouldBe listOf(
            "beginBatchEdit",
            "endBatchEdit",
        )
        recorder.depth shouldBe 0
        recorder.maxDepth shouldBe 1
    }
})

private class RecordingInputConnection : InvocationHandler {
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
                true
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
            "equals" -> proxy === args?.get(0)
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "RecordingInputConnection"
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
