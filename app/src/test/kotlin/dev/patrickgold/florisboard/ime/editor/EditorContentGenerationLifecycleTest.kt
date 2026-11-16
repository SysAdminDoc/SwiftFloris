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

import android.content.Context
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.ime.text.composing.Appender
import dev.patrickgold.florisboard.ime.text.composing.Composer
import dev.patrickgold.florisboard.lib.ext.ExtensionComponentName
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class EditorContentGenerationLifecycleTest {
    private lateinit var scheduler: TestCoroutineScheduler
    private lateinit var dispatcher: TestDispatcher
    private lateinit var context: Context

    @Before
    fun setUp() {
        scheduler = TestCoroutineScheduler()
        dispatcher = StandardTestDispatcher(scheduler)
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun finishInputViewCancelsPendingStartInputContentJob() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "hello")

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 5), isRestart = false)
        editor.handleFinishInputView()

        scheduler.runCurrent()

        assertEquals(EditorContent.Unspecified, editor.activeContent)
        assertTrue(connection.composingCalls.isEmpty())
    }

    @Test
    fun startInputViewSupersedesPendingStartInputContentJob() {
        val editor = TestEditorInstance(context)
        val firstConnection = LifecycleRecordingInputConnection(textBeforeSelection = "old")
        val secondConnection = LifecycleRecordingInputConnection(textBeforeSelection = "new")

        editor.inputConnection = firstConnection.connection
        editor.handleStartInputView(editorInfo(selection = 3), isRestart = false)
        editor.inputConnection = secondConnection.connection
        editor.handleStartInputView(editorInfo(selection = 3), isRestart = false)

        scheduler.runCurrent()

        assertEquals("new", editor.activeContent.text)
        assertTrue(firstConnection.composingCalls.isEmpty())
        assertEquals(listOf("finishComposingText"), secondConnection.composingCalls)
    }

    @Test
    fun restartingInputViewRebuildsContentFromTheCurrentConnection() {
        val editor = TestEditorInstance(context)
        val firstConnection = LifecycleRecordingInputConnection(textBeforeSelection = "before-restart")
        val restartedConnection = LifecycleRecordingInputConnection(textBeforeSelection = "after-restart")

        editor.inputConnection = firstConnection.connection
        editor.handleStartInputView(editorInfo(selection = 14), isRestart = false)
        scheduler.runCurrent()
        assertEquals("before-restart", editor.activeContent.text)

        editor.inputConnection = restartedConnection.connection
        editor.handleStartInputView(editorInfo(selection = 13), isRestart = true)
        scheduler.runCurrent()

        assertEquals("after-restart", editor.activeContent.text)
        assertEquals(listOf("finishComposingText"), firstConnection.composingCalls)
        assertEquals(listOf("finishComposingText"), restartedConnection.composingCalls)
    }

    @Test
    fun finishInputCancelsPendingSelectionUpdateContentJob() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "hello")

        editor.inputConnection = connection.connection
        editor.handleStartInput(editorInfo(selection = 5))
        editor.handleSelectionUpdate(
            oldSelection = EditorRange.cursor(0),
            newSelection = EditorRange.cursor(5),
            composing = EditorRange(0, 2),
        )
        editor.handleFinishInput()

        scheduler.runCurrent()

        assertEquals(EditorContent.Unspecified, editor.activeContent)
        assertTrue(connection.composingCalls.isEmpty())
    }

    @Test
    fun selectionUpdateChecksCurrentInputConnectionIdentityBeforePublishing() {
        val editor = TestEditorInstance(context)
        val staleConnection = LifecycleRecordingInputConnection(textBeforeSelection = "stale")
        val currentConnection = LifecycleRecordingInputConnection(textBeforeSelection = "current")

        editor.inputConnection = staleConnection.connection
        editor.handleStartInput(editorInfo(selection = 5))
        editor.handleSelectionUpdate(
            oldSelection = EditorRange.cursor(0),
            newSelection = EditorRange.cursor(5),
            composing = EditorRange(0, 2),
        )
        editor.inputConnection = currentConnection.connection

        scheduler.runCurrent()

        assertEquals(EditorContent.Unspecified, editor.activeContent)
        assertTrue(staleConnection.composingCalls.isEmpty())
        assertTrue(currentConnection.composingCalls.isEmpty())
    }

    @Test
    fun selectionJumpAfterCommitPublishesAdjacencyBreakSignal() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "hello")
        val adjacencyBreaks = mutableListOf<Unit>()
        val collectJob = CoroutineScope(dispatcher).launch {
            editor.commitAdjacencyBrokenFlow.collect {
                adjacencyBreaks += Unit
            }
        }

        scheduler.runCurrent()
        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 5), isRestart = false)
        scheduler.runCurrent()
        editor.updateLastCommitPosition()
        editor.handleSelectionUpdate(
            oldSelection = EditorRange.cursor(5),
            newSelection = EditorRange.cursor(2),
            composing = EditorRange.Unspecified,
        )
        scheduler.runCurrent()

        assertEquals(1, adjacencyBreaks.size)
        collectJob.cancel()
    }

    @Test
    fun keyboardOwnEditsAfterCommitKeepAdjacency() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "hello")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 5), isRestart = false)
        scheduler.runCurrent()
        editor.updateLastCommitPosition()
        // The space after a committed word, then the next word's first letter: the editor reports
        // the cursor where the keyboard's own edits put it, so this is still typing in a row.
        editor.commitText(" ")
        editor.handleSelectionUpdate(
            oldSelection = EditorRange.cursor(5),
            newSelection = EditorRange.cursor(6),
            composing = EditorRange.Unspecified,
        )
        editor.commitText("w")
        editor.handleSelectionUpdate(
            oldSelection = EditorRange.cursor(6),
            newSelection = EditorRange.cursor(7),
            composing = EditorRange.Unspecified,
        )
        scheduler.runCurrent()

        assertEquals(0, adjacencyBreaks.size)
    }

    @Test
    fun cursorJumpWithoutTrackedCommitPublishesAdjacencyBreakSignal() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "hello world")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 11), isRestart = false)
        scheduler.runCurrent()
        // A word ended by a typed space leaves no tracked commit, and the user then taps into
        // the middle of the text: the next word isn't typed after the last one.
        editor.handleSelectionUpdate(
            oldSelection = EditorRange.cursor(11),
            newSelection = EditorRange.cursor(3),
            composing = EditorRange.Unspecified,
        )
        scheduler.runCurrent()

        assertEquals(1, adjacencyBreaks.size)
    }

    @Test
    fun repeatedSelectionReportDoesNotBreakAdjacency() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "hello")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 5), isRestart = false)
        scheduler.runCurrent()
        // Some editors report the same cursor again when only the composing region changes.
        editor.handleSelectionUpdate(
            oldSelection = EditorRange.cursor(5),
            newSelection = EditorRange.cursor(5),
            composing = EditorRange(0, 5),
        )
        scheduler.runCurrent()

        assertEquals(0, adjacencyBreaks.size)
    }

    @Test
    fun appInsertedPassThroughKeysKeepAdjacency() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "purple ")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 7), isRestart = false)
        scheduler.runCurrent()
        // A hardware keyboard with no mapped layout: the app inserts "wi" itself. Both keys go
        // down before the app reports either cursor, and the keyboard's composing region for the
        // first letter is reported in between.
        editor.expectAppInsert(1)
        editor.expectAppInsert(1)
        editor.handleSelectionUpdate(
            oldSelection = EditorRange.cursor(7),
            newSelection = EditorRange.cursor(8),
            composing = EditorRange.Unspecified,
        )
        editor.handleSelectionUpdate(
            oldSelection = EditorRange.cursor(8),
            newSelection = EditorRange.cursor(8),
            composing = EditorRange(7, 8),
        )
        editor.handleSelectionUpdate(
            oldSelection = EditorRange.cursor(8),
            newSelection = EditorRange.cursor(9),
            composing = EditorRange(7, 8),
        )
        scheduler.runCurrent()

        assertEquals(0, adjacencyBreaks.size)
    }

    @Test
    fun appInsertRightAfterKeyboardSpaceKeepsAdjacency() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "purple")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 6), isRestart = false)
        scheduler.runCurrent()
        // The keyboard commits the space, and the next letter (inserted by the app) goes down
        // before the editor reports the space, so the letter lands after the space, at 8.
        editor.commitText(" ")
        editor.expectAppInsert(1)
        editor.handleSelectionUpdate(
            oldSelection = EditorRange.cursor(6),
            newSelection = EditorRange.cursor(7),
            composing = EditorRange.Unspecified,
        )
        editor.handleSelectionUpdate(
            oldSelection = EditorRange.cursor(7),
            newSelection = EditorRange.cursor(8),
            composing = EditorRange.Unspecified,
        )
        scheduler.runCurrent()

        assertEquals(0, adjacencyBreaks.size)
    }

    @Test
    fun cursorJumpAfterAppInsertedKeyPublishesAdjacencyBreakSignal() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "purple ")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 7), isRestart = false)
        scheduler.runCurrent()
        editor.expectAppInsert(1)
        editor.handleSelectionUpdate(
            oldSelection = EditorRange.cursor(7),
            newSelection = EditorRange.cursor(8),
            composing = EditorRange.Unspecified,
        )
        // A key the app never inserted leaves a pending cursor at 9; a tap at 2 is still a jump,
        // and so is a later tap that happens to land on the stale 9.
        editor.expectAppInsert(1)
        editor.handleSelectionUpdate(
            oldSelection = EditorRange.cursor(8),
            newSelection = EditorRange.cursor(2),
            composing = EditorRange.Unspecified,
        )
        // The break signal buffers one emission, so let the collector see each break on its own.
        scheduler.runCurrent()
        assertEquals(1, adjacencyBreaks.size)
        editor.handleSelectionUpdate(
            oldSelection = EditorRange.cursor(2),
            newSelection = EditorRange.cursor(9),
            composing = EditorRange.Unspecified,
        )
        scheduler.runCurrent()

        assertEquals(2, adjacencyBreaks.size)
    }

    @Test
    fun massSelectionUpdatePublishesAdjacencyBreakSignal() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "hello")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 5), isRestart = false)
        scheduler.runCurrent()
        editor.massSelectionUpdate(EditorRange(0, 5))
        scheduler.runCurrent()

        assertEquals(1, adjacencyBreaks.size)
    }

    private fun collectAdjacencyBreaks(editor: AbstractEditorInstance): List<Unit> {
        val adjacencyBreaks = mutableListOf<Unit>()
        CoroutineScope(dispatcher).launch {
            editor.commitAdjacencyBrokenFlow.collect {
                adjacencyBreaks += Unit
            }
        }
        scheduler.runCurrent()
        return adjacencyBreaks
    }
}

private class TestEditorInstance(context: Context) : AbstractEditorInstance(context) {
    var inputConnection: InputConnection? = null

    fun massSelectionUpdate(newSelection: EditorRange) {
        handleMassSelectionUpdate(newSelection, EditorRange.Unspecified)
    }

    override fun currentInputConnection(): InputConnection? = inputConnection

    override fun determineComposingEnabled(): Boolean = false

    override fun determineComposer(composerName: ExtensionComponentName): Composer = Appender

    override fun shouldDetermineComposingRegion(editorInfo: FlorisEditorInfo): Boolean = false

    override fun reevaluateInputShiftState() = Unit
}

private class LifecycleRecordingInputConnection(
    private val textBeforeSelection: String,
    private val textAfterSelection: String = "",
    private val selectedText: String = "",
) : InvocationHandler {
    private val calls = mutableListOf<String>()
    val composingCalls: List<String>
        get() = calls.filter { call ->
            call == "finishComposingText" || call.startsWith("setComposingRegion")
        }

    val connection: InputConnection = Proxy.newProxyInstance(
        InputConnection::class.java.classLoader,
        arrayOf(InputConnection::class.java),
        this,
    ) as InputConnection

    override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
        return when (method.name) {
            "getTextBeforeCursor" -> textBeforeSelection
            "getTextAfterCursor" -> textAfterSelection
            "getSelectedText" -> selectedText
            "setComposingRegion" -> {
                calls += "setComposingRegion(${args?.get(0)},${args?.get(1)})"
                true
            }
            "finishComposingText" -> {
                calls += "finishComposingText"
                true
            }
            "requestCursorUpdates" -> {
                calls += "requestCursorUpdates(${args?.get(0)})"
                true
            }
            "equals" -> proxy === args?.get(0)
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "LifecycleRecordingInputConnection($textBeforeSelection)"
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

private fun editorInfo(selection: Int): FlorisEditorInfo {
    return FlorisEditorInfo.wrap(
        EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
            initialSelStart = selection
            initialSelEnd = selection
        },
    )
}
