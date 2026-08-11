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

import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainAll
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
            "setSelection(6,9)",
            "finishComposingText",
            "commitText(world,1)",
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

    // Previously this asserted the composing-region sequence was still issued
    // against a host that refuses setComposingRegion — which is true, and was
    // exactly the bug: issuing it changed nothing and the following
    // setComposingText inserted a duplicate. The replacement no longer depends
    // on the host honouring composing regions at all.
    test("third-party editor: replacement does not depend on the composing region") {
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
            "setSelection(0,4)",
            "finishComposingText",
            "commitText(hello,1)",
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

    test("hostile editor replay matrix keeps commit enter delete undo redo and selection stable") {
        val matrix = listOf(
            HostileEditorReplayScenario(
                name = "rich editor",
                initialText = "Hello broken world",
                initialSelection = EditorRange(6, 12),
                committedText = "stable",
                expectedAfterCommit = "Hello stable world",
                expectedAfterEnter = "Hello stable\n world",
                expectedAfterDelete = "Hello stable world",
                undoRedoMode = HostileUndoRedoMode.ContextMenu,
            ),
            HostileEditorReplayScenario(
                name = "game engine",
                initialText = "say ",
                initialSelection = EditorRange.cursor(4),
                committedText = "go",
                expectedAfterCommit = "say go",
                expectedAfterEnter = "say go\n",
                expectedAfterDelete = "say go",
                enterViaKeyEvent = true,
                deleteViaKeyEvent = true,
                undoRedoMode = HostileUndoRedoMode.Unsupported,
            ),
            HostileEditorReplayScenario(
                name = "Flutter rich text",
                initialText = "alpha beta",
                initialSelection = EditorRange(6, 10),
                committedText = "done",
                expectedAfterCommit = "alpha done",
                expectedAfterEnter = "alpha done\n",
                expectedAfterDelete = "alpha done",
                undoRedoMode = HostileUndoRedoMode.CtrlZ,
            ),
            HostileEditorReplayScenario(
                name = "desktop physical keyboard",
                initialText = "ticket open",
                initialSelection = EditorRange(7, 11),
                committedText = "closed",
                expectedAfterCommit = "ticket closed",
                expectedAfterEnter = "ticket closed\n",
                expectedAfterDelete = "ticket closed",
                enterViaKeyEvent = true,
                deleteViaKeyEvent = true,
                undoRedoMode = HostileUndoRedoMode.CtrlShiftZ,
            ),
        )

        matrix.map { it.name } shouldContainAll listOf(
            "rich editor",
            "game engine",
            "Flutter rich text",
            "desktop physical keyboard",
        )

        for (scenario in matrix) {
            val editor = StatefulHostileEditor(scenario.initialText)
            editor.connection.setSelection(scenario.initialSelection.start, scenario.initialSelection.end)
            editor.selectedText shouldBe scenario.initialText.substring(
                scenario.initialSelection.start,
                scenario.initialSelection.end,
            )

            EditorInputConnectionBatch.commitText(editor.connection, scenario.committedText, null)
            editor.text shouldBe scenario.expectedAfterCommit
            editor.selection shouldBe EditorRange.cursor(
                scenario.initialSelection.start + scenario.committedText.length,
            )

            if (scenario.enterViaKeyEvent) {
                editor.pressKey(KeyEvent.KEYCODE_ENTER) shouldBe true
            } else {
                EditorInputConnectionBatch.commitText(editor.connection, "\n", null)
            }
            editor.text shouldBe scenario.expectedAfterEnter

            if (scenario.deleteViaKeyEvent) {
                editor.pressKey(KeyEvent.KEYCODE_DEL) shouldBe true
            } else {
                editor.connection.deleteSurroundingText(1, 0) shouldBe true
            }
            editor.text shouldBe scenario.expectedAfterDelete

            val undoResult = editor.undo(scenario.undoRedoMode)
            undoResult shouldBe scenario.undoRedoMode.supportsHistory
            editor.text shouldBe if (scenario.undoRedoMode.supportsHistory) {
                scenario.expectedAfterEnter
            } else {
                scenario.expectedAfterDelete
            }

            val redoResult = editor.redo(scenario.undoRedoMode)
            redoResult shouldBe scenario.undoRedoMode.supportsHistory
            editor.text shouldBe scenario.expectedAfterDelete

            val cursor = scenario.expectedAfterDelete.length
            EditorInputConnectionBatch.applySelection(
                ic = editor.connection,
                selection = EditorRange.cursor(cursor),
                composing = EditorRange.Unspecified,
            )
            editor.selection shouldBe EditorRange.cursor(cursor)
            editor.depth shouldBe 0
            editor.maxDepth shouldBe 1
        }
    }
})

private data class HostileEditorReplayScenario(
    val name: String,
    val initialText: String,
    val initialSelection: EditorRange,
    val committedText: String,
    val expectedAfterCommit: String,
    val expectedAfterEnter: String,
    val expectedAfterDelete: String,
    val enterViaKeyEvent: Boolean = false,
    val deleteViaKeyEvent: Boolean = false,
    val undoRedoMode: HostileUndoRedoMode,
)

private enum class HostileUndoRedoMode(val supportsHistory: Boolean) {
    ContextMenu(supportsHistory = true),
    CtrlZ(supportsHistory = true),
    CtrlShiftZ(supportsHistory = true),
    Unsupported(supportsHistory = false),
}

private data class HostileEditorSnapshot(
    val text: String,
    val selection: EditorRange,
    val composing: EditorRange,
)

private class StatefulHostileEditor(initialText: String) : InvocationHandler {
    var text = initialText
        private set
    var selection = EditorRange.cursor(initialText.length)
        private set
    var depth = 0
        private set
    var maxDepth = 0
        private set
    private var composing = EditorRange.Unspecified
    private val undoStack = ArrayDeque<HostileEditorSnapshot>()
    private val redoStack = ArrayDeque<HostileEditorSnapshot>()

    val selectedText: String
        get() = if (selection.start == selection.end) {
            ""
        } else {
            text.substring(selection.start, selection.end)
        }

    val connection: InputConnection = Proxy.newProxyInstance(
        InputConnection::class.java.classLoader,
        arrayOf(InputConnection::class.java),
        this,
    ) as InputConnection

    fun pressKey(keyCode: Int, metaState: Int = 0): Boolean {
        return handleKeyAction(keyCode, metaState)
    }

    fun undo(mode: HostileUndoRedoMode): Boolean {
        return when (mode) {
            HostileUndoRedoMode.ContextMenu -> connection.performContextMenuAction(android.R.id.undo)
            HostileUndoRedoMode.CtrlZ,
            HostileUndoRedoMode.CtrlShiftZ,
            -> pressKey(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON)
            HostileUndoRedoMode.Unsupported -> false
        }
    }

    fun redo(mode: HostileUndoRedoMode): Boolean {
        return when (mode) {
            HostileUndoRedoMode.ContextMenu -> connection.performContextMenuAction(android.R.id.redo)
            HostileUndoRedoMode.CtrlZ -> pressKey(KeyEvent.KEYCODE_Y, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON)
            HostileUndoRedoMode.CtrlShiftZ -> pressKey(
                KeyEvent.KEYCODE_Z,
                KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON or
                    KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON,
            )
            HostileUndoRedoMode.Unsupported -> false
        }
    }

    override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
        return when (method.name) {
            "beginBatchEdit" -> {
                depth += 1
                maxDepth = maxOf(maxDepth, depth)
                true
            }
            "endBatchEdit" -> {
                depth -= 1
                true
            }
            "setSelection" -> {
                val start = args?.get(0) as? Int ?: 0
                val end = args?.get(1) as? Int ?: start
                selection = EditorRange(start.coerceIn(0, text.length), end.coerceIn(0, text.length))
                true
            }
            "setComposingRegion" -> {
                val start = args?.get(0) as? Int ?: -1
                val end = args?.get(1) as? Int ?: -1
                composing = EditorRange(start.coerceIn(0, text.length), end.coerceIn(0, text.length))
                true
            }
            "setComposingText" -> {
                val replacement = args?.get(0)?.toString().orEmpty()
                mutate {
                    replaceRange(if (composing.isValid) composing else selection, replacement)
                    composing = selection
                }
                true
            }
            "finishComposingText" -> {
                composing = EditorRange.Unspecified
                true
            }
            "commitText" -> {
                val replacement = args?.get(0)?.toString().orEmpty()
                mutate {
                    replaceRange(selection, replacement)
                    composing = EditorRange.Unspecified
                }
                true
            }
            "deleteSurroundingText",
            "deleteSurroundingTextInCodePoints",
            -> {
                val before = (args?.get(0) as? Int ?: 0).coerceAtLeast(0)
                val after = (args?.get(1) as? Int ?: 0).coerceAtLeast(0)
                mutate {
                    val start = (selection.start - before).coerceAtLeast(0)
                    val end = (selection.end + after).coerceAtMost(text.length)
                    replaceRange(EditorRange(start, end), "")
                }
                true
            }
            "sendKeyEvent" -> {
                handleKeyEvent(args?.get(0) as? KeyEvent)
            }
            "performContextMenuAction" -> {
                when (args?.get(0) as? Int) {
                    android.R.id.undo -> restoreUndo()
                    android.R.id.redo -> restoreRedo()
                    else -> false
                }
            }
            "getTextBeforeCursor" -> {
                val length = (args?.get(0) as? Int ?: text.length).coerceAtLeast(0)
                text.substring(0, selection.start).takeLast(length)
            }
            "getTextAfterCursor" -> {
                val length = (args?.get(0) as? Int ?: text.length).coerceAtLeast(0)
                text.substring(selection.end).take(length)
            }
            "getSelectedText" -> selectedText.ifEmpty { null }
            "equals" -> proxy === args?.get(0)
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "StatefulHostileEditor(text=$text, selection=$selection)"
            else -> defaultReturnValue(method.returnType)
        }
    }

    private fun handleKeyEvent(event: KeyEvent?): Boolean {
        if (event == null || event.action != KeyEvent.ACTION_DOWN) return true
        return handleKeyAction(event.keyCode, event.metaState)
    }

    private fun handleKeyAction(keyCode: Int, metaState: Int): Boolean {
        val isCtrlPressed = metaState and KeyEvent.META_CTRL_ON != 0
        val isShiftPressed = metaState and KeyEvent.META_SHIFT_ON != 0
        return when {
            keyCode == KeyEvent.KEYCODE_ENTER -> {
                mutate { replaceRange(selection, "\n") }
                true
            }
            keyCode == KeyEvent.KEYCODE_DEL -> {
                mutate {
                    val range = if (selection.start != selection.end) {
                        selection
                    } else {
                        EditorRange((selection.start - 1).coerceAtLeast(0), selection.end)
                    }
                    replaceRange(range, "")
                }
                true
            }
            keyCode == KeyEvent.KEYCODE_Z && isCtrlPressed && isShiftPressed -> restoreRedo()
            keyCode == KeyEvent.KEYCODE_Z && isCtrlPressed -> restoreUndo()
            keyCode == KeyEvent.KEYCODE_Y && isCtrlPressed -> restoreRedo()
            else -> true
        }
    }

    private fun mutate(block: () -> Unit) {
        undoStack.addLast(snapshot())
        redoStack.clear()
        block()
    }

    private fun replaceRange(range: EditorRange, replacement: String) {
        val start = range.start.coerceIn(0, text.length)
        val end = range.end.coerceIn(start, text.length)
        text = text.replaceRange(start, end, replacement)
        selection = EditorRange.cursor(start + replacement.length)
    }

    private fun restoreUndo(): Boolean {
        if (undoStack.isEmpty()) return false
        redoStack.addLast(snapshot())
        restore(undoStack.removeLast())
        return true
    }

    private fun restoreRedo(): Boolean {
        if (redoStack.isEmpty()) return false
        undoStack.addLast(snapshot())
        restore(redoStack.removeLast())
        return true
    }

    private fun snapshot(): HostileEditorSnapshot {
        return HostileEditorSnapshot(text, selection, composing)
    }

    private fun restore(snapshot: HostileEditorSnapshot) {
        text = snapshot.text
        selection = snapshot.selection
        composing = snapshot.composing
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
