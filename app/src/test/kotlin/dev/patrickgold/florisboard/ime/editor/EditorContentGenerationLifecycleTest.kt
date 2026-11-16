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
import java.time.Duration
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock

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

    @Test
    fun leavingTheFieldPublishesAdjacencyBreakSignal() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "hello")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 5), isRestart = false)
        scheduler.runCurrent()
        editor.updateLastCommitPosition()
        // Closing the field and opening one that looks the same (the same chat after a trip to
        // another app) takes the field info to Unspecified and back to an equal value, which a
        // collector of the info alone can miss. The next word isn't typed after this one.
        editor.handleFinishInputView()
        editor.handleStartInputView(editorInfo(selection = 5), isRestart = false)
        scheduler.runCurrent()

        assertEquals(1, adjacencyBreaks.size)
    }

    @Test
    fun appInsertThatNeverLandsStopsCountingAsTyping() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "purple ")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 7), isRestart = false)
        scheduler.runCurrent()
        // A key the app took as a shortcut instead of inserting it leaves a pending cursor at 8.
        // A tap there seconds later is a tap, not that key's insert.
        editor.expectAppInsert(1)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(3))
        editor.handleSelectionUpdate(
            oldSelection = EditorRange.cursor(7),
            newSelection = EditorRange.cursor(8),
            composing = EditorRange.Unspecified,
        )
        scheduler.runCurrent()

        assertEquals(1, adjacencyBreaks.size)
    }

    @Test
    fun editorStaysBehindUntilTheAppsInsertIsReadBack() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "hedgeh")

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 6), isRestart = false)
        scheduler.runCurrent()
        assertFalse(editor.isContentBehindEditor())
        // A burst from a keyboard with no mapped layout: the app inserts "o" itself, and a space
        // that went down now would correct "hedgeh".
        editor.expectAppInsert(1)
        assertTrue(editor.isContentBehindEditor())
        connection.textBeforeSelection = "hedgeho"
        editor.handleSelectionUpdate(
            oldSelection = EditorRange.cursor(6),
            newSelection = EditorRange.cursor(7),
            composing = EditorRange.Unspecified,
        )
        // Reported, but the text read after the report isn't published yet.
        assertTrue(editor.isContentBehindEditor())
        assertEquals("hedgeh", editor.activeContent.text)
        scheduler.runCurrent()

        assertFalse(editor.isContentBehindEditor())
        assertEquals("hedgeho", editor.activeContent.text)
    }

    @Test
    fun spaceHandedToTheAppBeforeItsLetterIsReportedKeepsAdjacency() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "hedgeh")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 6), isRestart = false)
        scheduler.runCurrent()
        // "o" goes to the app, and the space goes down before the app reports it, so the keyboard
        // hands the space to the app too and expects its insert right after the letter's.
        editor.expectAppInsert(1)
        assertTrue(editor.isContentBehindEditor())
        editor.expectAppInsert(1)
        connection.textBeforeSelection = "hedgeho"
        editor.handleSelectionUpdate(
            oldSelection = EditorRange.cursor(6),
            newSelection = EditorRange.cursor(7),
            composing = EditorRange.Unspecified,
        )
        connection.textBeforeSelection = "hedgeho "
        editor.handleSelectionUpdate(
            oldSelection = EditorRange.cursor(7),
            newSelection = EditorRange.cursor(8),
            composing = EditorRange.Unspecified,
        )
        scheduler.runCurrent()

        assertEquals(0, adjacencyBreaks.size)
        assertFalse(editor.isContentBehindEditor())
        assertEquals("hedgeho ", editor.activeContent.text)
    }

    @Test
    fun appInsertThatNeverLandsStopsHoldingTheEditorBehind() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "purple ")

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 7), isRestart = false)
        scheduler.runCurrent()
        // A key the app took as a shortcut never shows up, and it mustn't keep every space after
        // it from correcting.
        editor.expectAppInsert(1)
        assertTrue(editor.isContentBehindEditor())
        ShadowSystemClock.advanceBy(Duration.ofSeconds(3))

        assertFalse(editor.isContentBehindEditor())
    }

    @Test
    fun cursorMoveTheKeyboardDidNotPredictHoldsTheEditorBehindUntilRead() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "hedgeh")

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 6), isRestart = false)
        scheduler.runCurrent()
        // The app's insert lands where the keyboard didn't predict (two chars for one key, or a
        // report after its entry expired), so it reads as a jump and drops what was pending. The
        // keyboard's text still reads "hedgeh" until the new text is published.
        editor.expectAppInsert(1)
        connection.textBeforeSelection = "hedgehog"
        editor.handleSelectionUpdate(
            oldSelection = EditorRange.cursor(6),
            newSelection = EditorRange.cursor(8),
            composing = EditorRange.Unspecified,
        )
        assertTrue(editor.isContentBehindEditor())
        assertEquals("hedgeh", editor.activeContent.text)
        scheduler.runCurrent()

        assertFalse(editor.isContentBehindEditor())
        assertEquals("hedgehog", editor.activeContent.text)
    }

    @Test
    fun keyboardsOwnEditsDontPutTheEditorBehind() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "purple")

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 6), isRestart = false)
        scheduler.runCurrent()
        editor.commitText(" ")
        assertFalse(editor.isContentBehindEditor())
        editor.handleSelectionUpdate(
            oldSelection = EditorRange.cursor(6),
            newSelection = EditorRange.cursor(7),
            composing = EditorRange.Unspecified,
        )

        assertFalse(editor.isContentBehindEditor())
    }

    @Test
    fun backspaceInBurstKeepsKeysTracked() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "purple ")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 7), isRestart = false)
        scheduler.runCurrent()
        // A text expander with no mapped layout fixes "abx" to "abcd". The app types and deletes
        // every key itself, and all six go down before it reports the first.
        repeat(3) { editor.expectAppInsert(1) }
        editor.expectAppStepBack()
        repeat(2) { editor.expectAppInsert(1) }
        editor.report(7, 8)
        editor.report(8, 9)
        editor.report(9, 10)
        editor.report(10, 9)
        // Backspace is read back, but "c" and "d" are still on their way, so a space now goes to
        // the app after them.
        assertTrue(editor.isContentBehindEditor())
        editor.report(9, 10)
        assertTrue(editor.isContentBehindEditor())
        editor.report(10, 11)

        assertFalse(editor.isContentBehindEditor())
        // Backspace moved the cursor back, but the letters typed after it are still typing.
        assertEquals(1, adjacencyBreaks.size)
    }

    @Test
    fun fortyKeyBurstStaysTracked() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "purple ")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 7), isRestart = false)
        scheduler.runCurrent()
        // A scanner types 40 keys before the app reports the first one.
        repeat(40) { editor.expectAppInsert(1) }
        for (cursor in 8..46) {
            editor.report(cursor - 1, cursor)
            assertTrue("behind after the report at $cursor", editor.isContentBehindEditor())
        }
        editor.report(46, 47)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(0, adjacencyBreaks.size)
    }

    @Test
    fun selectionBackspaceInBurst() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "abc", selectedText = "def")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 3), isRestart = false)
        scheduler.runCurrent()
        editor.handleSelectionUpdate(
            oldSelection = EditorRange.cursor(3),
            newSelection = EditorRange(3, 6),
            composing = EditorRange.Unspecified,
        )
        scheduler.runCurrent()
        // Backspace deletes the selection, and "x" goes down before the app reports that.
        editor.expectAppStepBack()
        editor.expectAppInsert(1)
        editor.handleSelectionUpdate(
            oldSelection = EditorRange(3, 6),
            newSelection = EditorRange.cursor(3),
            composing = EditorRange.Unspecified,
        )
        scheduler.runCurrent()
        assertTrue(editor.isContentBehindEditor())
        editor.report(3, 4)

        assertFalse(editor.isContentBehindEditor())
        // Selecting and deleting moved the cursor, the letter typed after them didn't.
        assertEquals(2, adjacencyBreaks.size)
    }

    @Test
    fun emojiBackspaceInBurst() {
        val grin = String(Character.toChars(0x1F600))
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "ab$grin")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 4), isRestart = false)
        scheduler.runCurrent()
        // Backspace takes both chars of the emoji, and the app reports it together with the "x"
        // typed after it. Guessing one char would leave "x" waiting for a cursor that never comes.
        editor.expectAppStepBack()
        editor.expectAppInsert(1)
        editor.report(4, 3)
        assertFalse(editor.isContentBehindEditor())
        editor.expectAppInsert(1)
        editor.report(3, 4)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(1, adjacencyBreaks.size)
    }

    @Test
    fun typedEmojiBackspaceInBurst() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "purple ")

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 7), isRestart = false)
        scheduler.runCurrent()
        // The burst types an emoji, deletes it and types "x", and the app reports all three at once.
        editor.expectAppInsert(2)
        editor.expectAppStepBack()
        editor.expectAppInsert(1)
        editor.report(7, 8)

        assertFalse(editor.isContentBehindEditor())
    }

    @Test
    fun backspaceAtStartDoesNotHoldBehind() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 0), isRestart = false)
        scheduler.runCurrent()
        // Backspace at the start deletes nothing, so the app never reports it.
        editor.expectAppStepBack()
        assertFalse(editor.isContentBehindEditor())
        editor.expectAppInsert(1)
        editor.report(0, 1)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(0, adjacencyBreaks.size)
    }

    @Test
    fun heartBackspaceInBurst() {
        val heart = String(Character.toChars(0x2764)) + String(Character.toChars(0xFE0F))
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "ok $heart")

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 5), isRestart = false)
        scheduler.runCurrent()
        // Backspace takes the heart and its variation selector together, and the app reports it
        // with the "x" typed after it.
        editor.expectAppStepBack()
        editor.expectAppInsert(1)
        editor.report(5, 4)

        assertFalse(editor.isContentBehindEditor())
    }

    @Test
    fun leftOverAccentInBurst() {
        val accent = String(Character.toChars(0x301))
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "ae$accent")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 3), isRestart = false)
        scheduler.runCurrent()
        // Left moves over the "e" and its accent at once, and the app reports it together with the
        // "x" typed after it. One char back would leave "x" waiting for a cursor that never comes.
        editor.expectAppStepBack(wholeCluster = true)
        editor.expectAppInsert(1)
        editor.report(3, 2)
        assertFalse(editor.isContentBehindEditor())
        editor.expectAppInsert(1)
        editor.report(2, 3)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(1, adjacencyBreaks.size)
    }

    @Test
    fun awaitAppEditsReadsAfterTheKeyboardsCommit() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "with")

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 4), isRestart = false)
        scheduler.runCurrent()
        val before = connection.editAndReadCalls.size
        // A hardware space the keyboard handles: the app answers the read only after it has run
        // the commit queued ahead of it, so the next key of a burst can't land in front of it.
        editor.commitText(" ")
        editor.awaitAppEdits()

        assertEquals(listOf("commitText", "getTextBeforeCursor(1)"), connection.editAndReadCalls.drop(before))
        editor.inputConnection = null
        editor.awaitAppEdits()
    }

    @Test
    fun leftOverAccentReportedAlone() {
        val accent = String(Character.toChars(0x301))
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "ae$accent")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 3), isRestart = false)
        scheduler.runCurrent()
        editor.expectAppStepBack(wholeCluster = true)
        editor.expectAppInsert(1)
        editor.report(3, 1)
        // Left is read back, and "x" is still on its way.
        assertTrue(editor.isContentBehindEditor())
        editor.report(1, 2)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(1, adjacencyBreaks.size)
    }

    @Test
    fun backspaceOverAccentInBurst() {
        val accent = String(Character.toChars(0x301))
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "ae$accent")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 3), isRestart = false)
        scheduler.runCurrent()
        // Backspace deletes the accent first and the "e" after it, one code point at a time.
        editor.expectAppStepBack()
        editor.expectAppStepBack()
        editor.expectAppInsert(1)
        editor.report(3, 2)
        assertTrue(editor.isContentBehindEditor())
        editor.report(2, 1)
        assertTrue(editor.isContentBehindEditor())
        editor.report(1, 2)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(2, adjacencyBreaks.size)
    }

    @Test
    fun leftOverTwoAccentsInBurst() {
        val accents = String(Character.toChars(0x323)) + String(Character.toChars(0x301))
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "ae$accents")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 4), isRestart = false)
        scheduler.runCurrent()
        // Left moves over the "e" and both of its accents.
        editor.expectAppStepBack(wholeCluster = true)
        editor.expectAppInsert(1)
        editor.report(4, 2)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(1, adjacencyBreaks.size)
    }

    @Test
    fun leftOverConjunctStopsAtSecondConsonant() {
        val conjunct = String(Character.toChars(0x915)) + String(Character.toChars(0x94D)) +
            String(Character.toChars(0x924))
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "a$conjunct")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 4), isRestart = false)
        scheduler.runCurrent()
        // A TextView's Left stops before the consonant after the virama, then moves over the
        // first consonant and its virama together.
        editor.expectAppStepBack(wholeCluster = true)
        editor.expectAppStepBack(wholeCluster = true)
        editor.expectAppInsert(1)
        editor.report(4, 3)
        assertTrue(editor.isContentBehindEditor())
        editor.report(3, 1)
        assertTrue(editor.isContentBehindEditor())
        editor.report(1, 2)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(2, adjacencyBreaks.size)
    }

    private fun conjunct(): String = String(Character.toChars(0x915)) + String(Character.toChars(0x94D)) +
        String(Character.toChars(0x924))

    @Test
    fun leftOverConjunctMovesOverItWhole() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "a${conjunct()}")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 4), isRestart = false)
        scheduler.runCurrent()
        // A Compose or Chrome field's Left moves over the whole conjunct, then over "a".
        editor.expectAppStepBack(wholeCluster = true)
        editor.expectAppStepBack(wholeCluster = true)
        editor.expectAppInsert(1)
        editor.report(4, 1)
        assertTrue(editor.isContentBehindEditor())
        editor.report(1, 0)
        assertTrue(editor.isContentBehindEditor())
        editor.report(0, 1)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(2, adjacencyBreaks.size)
    }

    @Test
    fun coalescedReportPastConjunctLeft() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "a${conjunct()}")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 4), isRestart = false)
        scheduler.runCurrent()
        // Left and "x" in one report ("ax" plus the conjunct, cursor 2), then "y" on its own.
        editor.expectAppStepBack(wholeCluster = true)
        editor.expectAppInsert(1)
        editor.expectAppInsert(1)
        editor.report(4, 2)
        assertTrue(editor.isContentBehindEditor())
        editor.report(2, 3)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(1, adjacencyBreaks.size)
    }

    @Test
    fun conjunctLeftsSettleOnTheReportOnlyOneFieldExplains() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = conjunct() + conjunct())
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 6), isRestart = false)
        scheduler.runCurrent()
        // Left, Left, "x". A TextView lands at 5, 3, 4; a Compose field at 3, 0, 1. Its first
        // report, 3, is also where a TextView's second Left lands, so only the next one tells.
        editor.expectAppStepBack(wholeCluster = true)
        editor.expectAppStepBack(wholeCluster = true)
        editor.expectAppInsert(1)
        editor.report(6, 3)
        assertTrue(editor.isContentBehindEditor())
        editor.report(3, 0)
        assertTrue(editor.isContentBehindEditor())
        editor.report(0, 1)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(2, adjacencyBreaks.size)
    }

    @Test
    fun leftOverAccentTypedInBurst() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "a")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 1), isRestart = false)
        scheduler.runCurrent()
        // "e" and a combining acute, then Left, which moves over both, then "x".
        editor.expectAppInsert(1, 'e'.code)
        editor.expectAppInsert(1, 0x301)
        editor.expectAppStepBack(wholeCluster = true)
        editor.expectAppInsert(1, 'x'.code)
        editor.report(1, 2)
        assertTrue(editor.isContentBehindEditor())
        editor.report(2, 3)
        assertTrue(editor.isContentBehindEditor())
        editor.report(3, 1)
        assertTrue(editor.isContentBehindEditor())
        editor.report(1, 2)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(1, adjacencyBreaks.size)
    }

    @Test
    fun leftOverConjunctTypedInBurst() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "a")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 1), isRestart = false)
        scheduler.runCurrent()
        // The conjunct typed key by key, then a Compose field's Left over all of it, then "x".
        conjunct().codePoints().toArray().forEach { editor.expectAppInsert(1, it) }
        editor.expectAppStepBack(wholeCluster = true)
        editor.expectAppInsert(1, 'x'.code)
        editor.report(1, 2)
        editor.report(2, 3)
        editor.report(3, 4)
        assertTrue(editor.isContentBehindEditor())
        editor.report(4, 1)
        assertTrue(editor.isContentBehindEditor())
        editor.report(1, 2)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(1, adjacencyBreaks.size)
    }

    @Test
    fun leftOverConjunctTypedInBurstStopsAtSecondConsonant() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "a")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 1), isRestart = false)
        scheduler.runCurrent()
        // The conjunct typed key by key, then a TextView's Left, which stops before the
        // consonant after the virama, then "x".
        conjunct().codePoints().toArray().forEach { editor.expectAppInsert(1, it) }
        editor.expectAppStepBack(wholeCluster = true)
        editor.expectAppInsert(1, 'x'.code)
        editor.report(1, 2)
        editor.report(2, 3)
        editor.report(3, 4)
        editor.report(4, 3)
        assertTrue(editor.isContentBehindEditor())
        editor.report(3, 4)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(1, adjacencyBreaks.size)
    }

    @Test
    fun leftOverSpacingMarkTypedInBurstMovesOverItAlone() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 0), isRestart = false)
        scheduler.runCurrent()
        // Myanmar's vowel sign aa is a spacing mark that starts a cluster of its own, so Left
        // moves over it and not the consonant before it.
        editor.expectAppInsert(1, 0x1000)
        editor.expectAppInsert(1, 0x102C)
        editor.expectAppStepBack(wholeCluster = true)
        editor.expectAppInsert(1, 'x'.code)
        editor.report(0, 1)
        editor.report(1, 2)
        editor.report(2, 1)
        assertTrue(editor.isContentBehindEditor())
        editor.report(1, 2)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(1, adjacencyBreaks.size)
    }

    @Test
    fun leftOverAccentTypedAfterDigitInBurst() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "a")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 1), isRestart = false)
        scheduler.runCurrent()
        // An accent typed after a digit joins its cluster too, so Left moves over both.
        editor.expectAppInsert(1, '1'.code)
        editor.expectAppInsert(1, 0x301)
        editor.expectAppStepBack(wholeCluster = true)
        editor.expectAppInsert(1, 'x'.code)
        editor.report(1, 2)
        editor.report(2, 3)
        editor.report(3, 1)
        assertTrue(editor.isContentBehindEditor())
        editor.report(1, 2)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(1, adjacencyBreaks.size)
    }

    @Test
    fun backspaceAfterLeftOverConjunctStepsFromWhereTheFieldLanded() {
        val emoji = String(Character.toChars(0x1F600))
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "a$emoji${conjunct()}")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 6), isRestart = false)
        scheduler.runCurrent()
        // A Compose field's Left lands before the conjunct, and Backspace then deletes the emoji's
        // two chars. A TextView would land inside the conjunct and delete one.
        editor.expectAppStepBack(wholeCluster = true)
        editor.expectAppStepBack()
        editor.expectAppInsert(1)
        editor.report(6, 3)
        assertTrue(editor.isContentBehindEditor())
        editor.report(3, 1)
        assertTrue(editor.isContentBehindEditor())
        editor.report(1, 2)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(2, adjacencyBreaks.size)
    }

    @Test
    fun knownFieldKindExplainsALaterCoalescedReport() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = conjunct() + conjunct())
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 6), isRestart = false)
        scheduler.runCurrent()
        // The first Left lands where only a Compose field puts it.
        editor.expectAppStepBack(wholeCluster = true)
        connection.textBeforeSelection = conjunct()
        editor.report(6, 3)
        // Left, "x", "y" in one report at 2. A TextView's Left alone would land there too, but
        // this field is known to move over the whole conjunct.
        editor.expectAppStepBack(wholeCluster = true)
        editor.expectAppInsert(1)
        editor.expectAppInsert(1)
        editor.report(3, 2)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(2, adjacencyBreaks.size)
    }

    @Test
    fun jumpLetsTheFieldKindBeLearnedAgain() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = conjunct() + conjunct())
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 6), isRestart = false)
        scheduler.runCurrent()
        // A report reads as a Compose field's Left. If that was a wrong guess, the jump the next
        // unexplained report makes lets a TextView's Left be expected again.
        editor.expectAppStepBack(wholeCluster = true)
        connection.textBeforeSelection = conjunct()
        editor.report(6, 3)
        connection.textBeforeSelection = conjunct() + conjunct()
        editor.report(3, 6)
        editor.expectAppStepBack(wholeCluster = true)
        editor.expectAppInsert(1)
        editor.report(6, 5)
        assertTrue(editor.isContentBehindEditor())
        editor.report(5, 6)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(3, adjacencyBreaks.size)
    }

    @Test
    @Config(sdk = [29])
    fun leftOverConjunctTypedInBurstOnOlderAndroid() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "a")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 1), isRestart = false)
        scheduler.runCurrent()
        // Android 10's grapheme rules (ICU 63, before ICU's conjunct rule) end a cluster at the
        // virama, and a Chrome field's Left still moves over the whole conjunct.
        conjunct().codePoints().toArray().forEach { editor.expectAppInsert(1, it) }
        editor.expectAppStepBack(wholeCluster = true)
        editor.expectAppInsert(1, 'x'.code)
        editor.report(1, 2)
        editor.report(2, 3)
        editor.report(3, 4)
        editor.report(4, 1)
        assertTrue(editor.isContentBehindEditor())
        editor.report(1, 2)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(1, adjacencyBreaks.size)
    }

    @Test
    fun composingReportAfterStepBack() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 0), isRestart = false)
        scheduler.runCurrent()
        // "abx", Backspace, "c": the "c" is expected where "x" was reported.
        repeat(3) { editor.expectAppInsert(1) }
        editor.expectAppStepBack()
        editor.expectAppInsert(1)
        editor.report(0, 1)
        editor.report(1, 2)
        editor.report(2, 3)
        // The app answers a composing region set on "abx" without moving the cursor.
        editor.handleSelectionUpdate(
            oldSelection = EditorRange.cursor(3),
            newSelection = EditorRange.cursor(3),
            composing = EditorRange(0, 3),
        )
        scheduler.runCurrent()
        assertTrue(editor.isContentBehindEditor())
        editor.report(3, 2)
        assertTrue(editor.isContentBehindEditor())
        editor.report(2, 3)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(1, adjacencyBreaks.size)
    }

    @Test
    fun missedStepBackDropsPending() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "purple")

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 6), isRestart = false)
        scheduler.runCurrent()
        // The app's Backspace went further than one char, so nothing expected explains the report,
        // and the keys after it can't be placed either.
        editor.expectAppStepBack()
        editor.expectAppInsert(1)
        editor.report(6, 0)

        assertFalse(editor.isContentBehindEditor())
    }

    @Test
    fun coalescedLoopClearsSoon() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "purple ")

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 7), isRestart = false)
        scheduler.runCurrent()
        // "ab", Backspace, "b" ends where "ab" did, and the app reports all four keys at once.
        repeat(2) { editor.expectAppInsert(1) }
        editor.expectAppStepBack()
        editor.expectAppInsert(1)
        editor.report(7, 9)
        assertTrue(editor.isContentBehindEditor())
        ShadowSystemClock.advanceBy(Duration.ofMillis(400))

        // No report followed, so the app is done, and a space can be corrected again.
        assertFalse(editor.isContentBehindEditor())
    }

    @Test
    fun splitLoopStillMatches() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "purple ")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 7), isRestart = false)
        scheduler.runCurrent()
        // "ab", Backspace, "b", Backspace, "b", with the app reporting "ab" at once and then each
        // key 200 ms apart, so the run outlasts one quiet timeout though no gap does.
        repeat(2) { editor.expectAppInsert(1) }
        repeat(2) {
            editor.expectAppStepBack()
            editor.expectAppInsert(1)
        }
        editor.report(7, 9)
        for ((from, to) in listOf(9 to 8, 8 to 9, 9 to 8)) {
            ShadowSystemClock.advanceBy(Duration.ofMillis(200))
            editor.report(from, to)
            assertTrue("behind after the report at $to", editor.isContentBehindEditor())
        }
        ShadowSystemClock.advanceBy(Duration.ofMillis(200))
        editor.report(8, 9)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(2, adjacencyBreaks.size)
    }

    @Test
    fun overflowHoldsBehind() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "purple ")

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 7), isRestart = false)
        scheduler.runCurrent()
        // More keys than the tracker keeps: the first one's report matches nothing, but the app is
        // still more than a thousand keys behind.
        repeat(1025) { editor.expectAppInsert(1) }
        editor.report(7, 8)
        assertTrue(editor.isContentBehindEditor())
        ShadowSystemClock.advanceBy(Duration.ofSeconds(3))

        assertFalse(editor.isContentBehindEditor())
    }

    @Test
    fun quietLoopYieldsToSpace() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "purple ")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 7), isRestart = false)
        scheduler.runCurrent()
        repeat(2) { editor.expectAppInsert(1) }
        editor.expectAppStepBack()
        editor.expectAppInsert(1)
        connection.textBeforeSelection = "purple ab"
        editor.report(7, 9)
        ShadowSystemClock.advanceBy(Duration.ofMillis(400))
        // The app is done, so the keyboard types the space itself, and "c" lands after it.
        editor.commitText(" ")
        editor.expectAppInsert(1)
        connection.textBeforeSelection = "purple ab "
        editor.report(9, 10)
        assertTrue(editor.isContentBehindEditor())
        connection.textBeforeSelection = "purple ab c"
        editor.report(10, 11)

        assertFalse(editor.isContentBehindEditor())
        // The Backspace in the burst breaks adjacency once, as in backspaceInBurstKeepsKeysTracked.
        assertEquals(1, adjacencyBreaks.size)
    }

    @Test
    fun overflowEndsOnMatch() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "purple ")

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 7), isRestart = false)
        scheduler.runCurrent()
        repeat(1025) { editor.expectAppInsert(1) }
        editor.report(7, 8)
        editor.report(8, 1032)
        assertTrue(editor.isContentBehindEditor())
        // The app caught up and the user types on. Its report matches, so typing in the next two
        // seconds gets corrected again.
        ShadowSystemClock.advanceBy(Duration.ofMillis(100))
        editor.expectAppInsert(1)
        editor.report(1032, 1033)

        assertFalse(editor.isContentBehindEditor())
    }

    @Test
    fun slowLoopKeepsKeys() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "purple ")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 7), isRestart = false)
        scheduler.runCurrent()
        // "ab", Backspace, "b" in an app that takes 350 ms a key, longer than the quiet timeout.
        repeat(2) { editor.expectAppInsert(1) }
        editor.expectAppStepBack()
        editor.expectAppInsert(1)
        for ((from, to) in listOf(7 to 8, 8 to 9, 9 to 8, 8 to 9)) {
            ShadowSystemClock.advanceBy(Duration.ofMillis(350))
            assertTrue("behind before the report at $to", editor.isContentBehindEditor())
            editor.report(from, to)
        }

        assertFalse(editor.isContentBehindEditor())
        assertEquals(1, adjacencyBreaks.size)
    }

    @Test
    fun stalledLoopKeepsKeys() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "purple ")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 7), isRestart = false)
        scheduler.runCurrent()
        // "ab", Backspace, "b", "c": the app reports "a" right away, then stalls past the quiet
        // timeout before reporting the rest one by one.
        repeat(2) { editor.expectAppInsert(1) }
        editor.expectAppStepBack()
        repeat(2) { editor.expectAppInsert(1) }
        editor.report(7, 8)
        ShadowSystemClock.advanceBy(Duration.ofMillis(600))
        for ((from, to) in listOf(8 to 9, 9 to 8, 8 to 9)) {
            editor.report(from, to)
            assertTrue("behind after the report at $to", editor.isContentBehindEditor())
            ShadowSystemClock.advanceBy(Duration.ofMillis(100))
        }
        editor.report(9, 10)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(1, adjacencyBreaks.size)
    }

    /** The editor reports the cursor moving from [from] to [to], and the content read after it lands. */
    private fun TestEditorInstance.report(from: Int, to: Int) {
        handleSelectionUpdate(
            oldSelection = EditorRange.cursor(from),
            newSelection = EditorRange.cursor(to),
            composing = EditorRange.Unspecified,
        )
        scheduler.runCurrent()
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
    var textBeforeSelection: String,
    private val textAfterSelection: String = "",
    private val selectedText: String = "",
) : InvocationHandler {
    private val calls = mutableListOf<String>()
    val composingCalls: List<String>
        get() = calls.filter { call ->
            call == "finishComposingText" || call.startsWith("setComposingRegion")
        }
    val editAndReadCalls: List<String>
        get() = calls.filter { call -> call == "commitText" || call.startsWith("getTextBeforeCursor") }

    val connection: InputConnection = Proxy.newProxyInstance(
        InputConnection::class.java.classLoader,
        arrayOf(InputConnection::class.java),
        this,
    ) as InputConnection

    override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
        return when (method.name) {
            "getTextBeforeCursor" -> {
                calls += "getTextBeforeCursor(${args?.get(0)})"
                textBeforeSelection
            }
            "commitText" -> {
                calls += "commitText"
                true
            }
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
