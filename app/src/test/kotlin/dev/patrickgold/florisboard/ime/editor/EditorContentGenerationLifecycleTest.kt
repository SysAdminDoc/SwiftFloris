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
import org.robolectric.annotation.SQLiteMode
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
    fun aCandidateReplacesTheWordBeforeTheCursorWhenComposingIsOff() {
        // Composing off with the word still found is a field whose composing was turned off. The
        // candidate is still for that word, and committing it at the cursor wrote "thiertheir".
        val editor = TestEditorInstance(context).apply { findsWords = true }
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "I typed thier", acceptsSelection = true)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 13), isRestart = false)
        scheduler.runCurrent()
        assertEquals("thier", editor.activeContent.currentWordText)
        assertFalse(editor.activeContent.composing.isValid)
        assertTrue(editor.canReplaceWord())

        assertTrue(editor.replaceWord("their"))

        assertEquals(listOf("setSelection(8,13)"), connection.selectionCalls)
        assertEquals(listOf("their"), connection.committedTexts)
        assertEquals("I typed their", editor.activeContent.textBeforeSelection)
        assertEquals(EditorRange.cursor(13), editor.activeContent.selection)
    }

    @Test
    fun aShorterCandidateLeavesTheCursorAtTheEndOfTheReplacement() {
        val editor = TestEditorInstance(context).apply { findsWords = true }
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "ab untill", acceptsSelection = true)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 9), isRestart = false)
        scheduler.runCurrent()

        assertTrue(editor.replaceWord("until"))

        assertEquals(listOf("setSelection(3,9)"), connection.selectionCalls)
        assertEquals(listOf("until"), connection.committedTexts)
        assertEquals("ab until", editor.activeContent.textBeforeSelection)
        assertEquals(EditorRange.cursor(8), editor.activeContent.selection)
    }

    @Test
    fun noWordBeforeTheCursorMeansNothingIsReplaced() {
        val editor = TestEditorInstance(context).apply { findsWords = true }
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "I typed thier ", acceptsSelection = true)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 14), isRestart = false)
        scheduler.runCurrent()
        assertEquals("", editor.activeContent.currentWordText)

        assertFalse(editor.canReplaceWord())
        assertFalse(editor.replaceWord("their"))

        assertTrue(connection.selectionCalls.isEmpty())
        assertTrue(connection.committedTexts.isEmpty())
        assertEquals("I typed thier ", editor.activeContent.textBeforeSelection)
    }

    @Test
    fun aRefusedSelectionLeavesTheWordAndTheContentAsTheyWere() {
        val editor = TestEditorInstance(context).apply { findsWords = true }
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "I typed thier", acceptsSelection = false)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 13), isRestart = false)
        scheduler.runCurrent()

        assertFalse(editor.replaceWord("their"))

        assertEquals(listOf("setSelection(8,13)"), connection.selectionCalls)
        assertTrue(connection.committedTexts.isEmpty())
        assertEquals("I typed thier", editor.activeContent.textBeforeSelection)
        assertEquals(null, editor.expectedContent())
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
    fun aKeyTypedBeforeATapsContentIsPublishedBuildsOnTheTapsText() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "", textAfterSelection = "go ")

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 0), isRestart = false)
        scheduler.runCurrent()
        // A tap puts the cursor after "go ", and a hardware burst's first key is handled before the
        // main thread gets to the content that report started.
        connection.textBeforeSelection = "go "
        connection.textAfterSelection = ""
        editor.handleSelectionUpdate(
            oldSelection = EditorRange.cursor(0),
            newSelection = EditorRange.cursor(3),
            composing = EditorRange.Unspecified,
        )
        editor.settlePendingContent()
        assertFalse(editor.isContentBehindEditor())
        editor.commitChar("r")

        assertEquals("go r", editor.activeContent.textBeforeSelection)
        assertEquals(EditorRange.cursor(4), editor.activeContent.selection)
        // The report's own refresh was settled, so it never runs later over the keyboard's edit.
        val composingCallsBefore = connection.composingCalls.size
        scheduler.runCurrent()
        assertEquals("go r", editor.activeContent.textBeforeSelection)
        assertEquals(composingCallsBefore, connection.composingCalls.size)
    }

    @Test
    fun contentStartedForAnotherFieldIsNotSettledIntoThisOne() {
        val editor = TestEditorInstance(context)
        val first = LifecycleRecordingInputConnection(textBeforeSelection = "old")
        val second = LifecycleRecordingInputConnection(textBeforeSelection = "new")

        editor.inputConnection = first.connection
        editor.handleStartInputView(editorInfo(selection = 3), isRestart = false)
        editor.inputConnection = second.connection
        editor.settlePendingContent()

        assertEquals(EditorContent.Unspecified, editor.activeContent)
        assertTrue(first.composingCalls.isEmpty())
        assertTrue(second.composingCalls.isEmpty())
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

    // The real app opens its databases on a background thread at startup. Native SQLite would load
    // Robolectric's native runtime there on SDK 29 and can leave its fonts jar open, so the next
    // class on SDK 35 or later fails in maybeCopyFonts.
    @Test
    @Config(sdk = [29])
    @SQLiteMode(SQLiteMode.Mode.LEGACY)
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
    @Config(sdk = [29])
    @SQLiteMode(SQLiteMode.Mode.LEGACY) // See leftOverConjunctTypedInBurstOnOlderAndroid.
    fun leftOverConjunctWithLaterConsonantOnOlderAndroid() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "a")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 1), isRestart = false)
        scheduler.runCurrent()
        // A conjunct ending in a consonant Unicode added later (GGA, for Sindhi), typed key by
        // key, then a Chrome field's Left over all of it, then "x".
        intArrayOf(0x915, 0x94D, 0x97B).forEach { editor.expectAppInsert(1, it) }
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
    @Config(sdk = [29])
    @SQLiteMode(SQLiteMode.Mode.LEGACY) // See leftOverConjunctTypedInBurstOnOlderAndroid.
    fun leftOverGujaratiConjunctWithZaOnOlderAndroid() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "a")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 1), isRestart = false)
        scheduler.runCurrent()
        // Gujarati KA, the virama and ZA, typed key by key, then a Chrome field's Left over all of
        // it, then "x".
        intArrayOf(0xA95, 0xACD, 0xAF9).forEach { editor.expectAppInsert(1, it) }
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
    fun backspaceOverAccentTypedAfterDigitInBurst() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "a")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 1), isRestart = false)
        scheduler.runCurrent()
        // A field's Backspace deletes an accent alone, after a digit as after a letter.
        editor.expectAppInsert(1, '1'.code)
        editor.expectAppInsert(1, 0x301)
        editor.expectAppStepBack()
        editor.expectAppInsert(1, 'x'.code)
        editor.report(1, 2)
        editor.report(2, 3)
        editor.report(3, 2)
        assertTrue(editor.isContentBehindEditor())
        editor.report(2, 3)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(1, adjacencyBreaks.size)
    }

    private fun ideographWith(selector: Int): String =
        String(Character.toChars(0x845B)) + String(Character.toChars(selector))

    @Test
    fun backspaceOverIdeographAndSelectorInTextView() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "a" + ideographWith(0xE0100))
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 4), isRestart = false)
        scheduler.runCurrent()
        // A TextView's Backspace, like Chrome's, deletes the selector with the ideograph.
        editor.expectAppStepBack()
        editor.expectAppInsert(1, 'x'.code)
        editor.report(4, 1)
        assertTrue(editor.isContentBehindEditor())
        editor.report(1, 2)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(1, adjacencyBreaks.size)
    }

    @Test
    fun backspaceOverIdeographAndSelectorInCompose() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "a" + ideographWith(0xFE00))
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 3), isRestart = false)
        scheduler.runCurrent()
        // A Compose field's Backspace deletes the selector alone.
        editor.expectAppStepBack()
        editor.expectAppInsert(1, 'x'.code)
        editor.report(3, 2)
        assertTrue(editor.isContentBehindEditor())
        editor.report(2, 3)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(1, adjacencyBreaks.size)
    }

    @Test
    fun backspaceOverIdeographAndSelectorInSlowCompose() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "a" + ideographWith(0xFE00))

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 3), isRestart = false)
        scheduler.runCurrent()
        // As in backspaceOverIdeographAndSelectorInCompose, in an app that takes 450 ms a key, with
        // "x" typed 300 ms after the Backspace. A TextView would have reported both keys at once,
        // but the Backspace's report came that late, so the "x" can take as long again.
        editor.expectAppStepBack()
        ShadowSystemClock.advanceBy(Duration.ofMillis(300))
        editor.expectAppInsert(1, 'x'.code)
        ShadowSystemClock.advanceBy(Duration.ofMillis(150))
        editor.report(3, 2)
        ShadowSystemClock.advanceBy(Duration.ofMillis(400))
        assertTrue(editor.isContentBehindEditor())
        editor.report(2, 3)

        assertFalse(editor.isContentBehindEditor())
    }

    @Test
    fun backspaceOverSelectorAfterAccent() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(
            textBeforeSelection = "ae" + String(Character.toChars(0x301)) + String(Character.toChars(0xFE00)),
        )
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 4), isRestart = false)
        scheduler.runCurrent()
        // After an accent, every field's Backspace deletes the selector alone.
        editor.expectAppStepBack()
        editor.expectAppInsert(1, 'x'.code)
        editor.report(4, 3)
        assertTrue(editor.isContentBehindEditor())
        editor.report(3, 4)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(1, adjacencyBreaks.size)
    }

    @Test
    fun leftOverIdeographAndSelector() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "a" + ideographWith(0xE0100))
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 4), isRestart = false)
        scheduler.runCurrent()
        // Every field's Left moves over the ideograph and its selector together.
        editor.expectAppStepBack(wholeCluster = true)
        editor.expectAppInsert(1, 'x'.code)
        editor.report(4, 1)
        assertTrue(editor.isContentBehindEditor())
        editor.report(1, 2)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(1, adjacencyBreaks.size)
    }

    @Test
    fun backspacesOverSelectorAtFieldStartInCompose() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = ideographWith(0xE0100))
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 3), isRestart = false)
        scheduler.runCurrent()
        // A Compose field's Backspaces delete the selector, then the ideograph. A TextView's
        // first one deletes both, which leaves its guess nothing for the second.
        editor.expectAppStepBack()
        editor.expectAppStepBack()
        editor.expectAppInsert(1, 'x'.code)
        editor.report(3, 1)
        assertTrue(editor.isContentBehindEditor())
        editor.report(1, 0)
        assertTrue(editor.isContentBehindEditor())
        editor.report(0, 1)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(2, adjacencyBreaks.size)
    }

    @Test
    fun backspaceOverSelectorAfterAccentOnDigit() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(
            textBeforeSelection = "1" + String(Character.toChars(0x301)) + String(Character.toChars(0xFE00)),
        )
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 3), isRestart = false)
        scheduler.runCurrent()
        // After an accent every field's Backspace deletes the selector alone, on a digit too.
        editor.expectAppStepBack()
        editor.expectAppInsert(1, 'x'.code)
        editor.report(3, 2)
        assertTrue(editor.isContentBehindEditor())
        editor.report(2, 3)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(1, adjacencyBreaks.size)
    }

    @Test
    fun backspaceOverMongolianLetterAndSelectorInTextView() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "a" + text(0x1820, 0x180B))
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 3), isRestart = false)
        scheduler.runCurrent()
        // Android counts Mongolian's free variation selectors as variation selectors, so a
        // TextView's Backspace deletes one with its letter.
        editor.expectAppStepBack()
        editor.expectAppInsert(1, 'x'.code)
        editor.report(3, 1)
        assertTrue(editor.isContentBehindEditor())
        editor.report(1, 2)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(1, adjacencyBreaks.size)
    }

    @Test
    fun backspaceOverInfoEmojiLeavesFieldKindOpen() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(
            textBeforeSelection = "a" + conjunct() + text(0x2139, 0xFE0F),
        )
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 6), isRestart = false)
        scheduler.runCurrent()
        // Every field deletes the emoji whole, a Compose field too, so that Backspace can't tell
        // the kinds apart. The Left after it, a Compose field's over the whole conjunct, does.
        editor.expectAppStepBack()
        editor.expectAppStepBack(wholeCluster = true)
        editor.expectAppInsert(1, 'x'.code)
        editor.report(6, 4)
        assertTrue(editor.isContentBehindEditor())
        editor.report(4, 1)
        assertTrue(editor.isContentBehindEditor())
        editor.report(1, 2)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(2, adjacencyBreaks.size)
    }

    @Test
    fun leftOverLongRunOfAccentsAfterDigit() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(
            textBeforeSelection = "1" + String(Character.toChars(0x301)).repeat(20),
        )
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 21), isRestart = false)
        scheduler.runCurrent()
        // Left moves over the digit and all twenty accents at once.
        editor.expectAppStepBack(wholeCluster = true)
        editor.expectAppInsert(1, 'x'.code)
        editor.report(21, 0)
        assertTrue(editor.isContentBehindEditor())
        editor.report(0, 1)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(1, adjacencyBreaks.size)
    }

    @Test
    fun leftOverLongRunOfAccentsOnLetter() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(
            textBeforeSelection = "e" + String(Character.toChars(0x301)).repeat(20),
        )
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 21), isRestart = false)
        scheduler.runCurrent()
        // Left moves over the letter and all twenty accents at once.
        editor.expectAppStepBack(wholeCluster = true)
        editor.expectAppInsert(1, 'x'.code)
        editor.report(21, 0)
        assertTrue(editor.isContentBehindEditor())
        editor.report(0, 1)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(1, adjacencyBreaks.size)
    }

    private fun text(vararg codePoints: Int): String = String(codePoints, 0, codePoints.size)

    @Test
    fun leftInRightToLeftTextMovesForward() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(
            textBeforeSelection = text(0x5E9, 0x5DC),
            textAfterSelection = text(0x5D5, 0x5DD),
        )
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 2), isRestart = false)
        scheduler.runCurrent()
        // In a Hebrew paragraph every field's Left moves forward a letter, then "x".
        editor.expectAppStepBack(wholeCluster = true)
        editor.expectAppInsert(1, 'x'.code)
        editor.report(2, 3)
        assertTrue(editor.isContentBehindEditor())
        editor.report(3, 4)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(1, adjacencyBreaks.size)
    }

    @Test
    fun leftAtEndOfRightToLeftTextStays() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = text(0x5E9, 0x5DC, 0x5D5, 0x5DD))
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 4), isRestart = false)
        scheduler.runCurrent()
        // At the end of Hebrew text a Left has nowhere to go, so "x" goes in right there.
        editor.expectAppStepBack(wholeCluster = true)
        editor.expectAppInsert(1, 'x'.code)
        editor.report(4, 5)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(0, adjacencyBreaks.size)
    }

    @Test
    fun leftInRightToLeftTextTypedInBurst() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(
            textBeforeSelection = text(0x5E9),
            textAfterSelection = text(0x5E9, 0x5C1, 0x5B8, 0x5DC),
        )
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 1), isRestart = false)
        scheduler.runCurrent()
        // A Hebrew letter typed, then Left over a letter and its two points at once, Left over
        // the next letter, and "x".
        editor.expectAppInsert(1, 0x5D5)
        editor.expectAppStepBack(wholeCluster = true)
        editor.expectAppStepBack(wholeCluster = true)
        editor.expectAppInsert(1, 'x'.code)
        editor.report(1, 2)
        editor.report(2, 5)
        assertTrue(editor.isContentBehindEditor())
        editor.report(5, 6)
        assertTrue(editor.isContentBehindEditor())
        editor.report(6, 7)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(2, adjacencyBreaks.size)
    }

    @Test
    fun leftAfterRightToLeftTextTypedInEmptyField() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 0), isRestart = false)
        scheduler.runCurrent()
        // Two Hebrew letters typed make the paragraph right-to-left, so the Left after them stays.
        editor.expectAppInsert(1, 0x5E9)
        editor.expectAppInsert(1, 0x5DC)
        editor.expectAppStepBack(wholeCluster = true)
        editor.expectAppInsert(1, 'x'.code)
        editor.report(0, 1)
        editor.report(1, 2)
        editor.report(2, 3)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(0, adjacencyBreaks.size)
    }

    @Test
    fun leftCollapsesSelectionInRightToLeftTextToItsEnd() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(
            textBeforeSelection = text(0x5E9),
            textAfterSelection = text(0x5DD),
            selectedText = text(0x5DC, 0x5D5),
        )
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 1, selectionEnd = 3), isRestart = false)
        scheduler.runCurrent()
        // Left collapses the selection to its left end, which in Hebrew is where it ends.
        editor.expectAppStepBack(wholeCluster = true)
        editor.expectAppInsert(1, 'x'.code)
        editor.handleSelectionUpdate(EditorRange(1, 3), EditorRange.cursor(3), EditorRange.Unspecified)
        scheduler.runCurrent()
        assertTrue(editor.isContentBehindEditor())
        editor.report(3, 4)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(1, adjacencyBreaks.size)
    }

    @Test
    fun leftAfterLatinInParagraphWithHebrewStepsBack() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(
            textBeforeSelection = "hi " + text(0x5E9, 0x5DC, 0x5D5, 0x5DD) + " ok",
        )
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 10), isRestart = false)
        scheduler.runCurrent()
        // A Hebrew word in a left-to-right paragraph doesn't turn the Left after "ok" around.
        editor.expectAppStepBack(wholeCluster = true)
        editor.expectAppInsert(1, 'x'.code)
        editor.report(10, 9)
        assertTrue(editor.isContentBehindEditor())
        editor.report(9, 10)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(1, adjacencyBreaks.size)
    }

    @Test
    fun leftAfterLongHebrewRunInLatinParagraphStepsBack() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "Hi " + text(0x5D0).repeat(70))
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 73), isRestart = false)
        scheduler.runCurrent()
        // The paragraph starts with Latin far before the cursor, so a Compose field's Left steps
        // back through the Hebrew after it.
        editor.expectAppStepBack(wholeCluster = true)
        editor.expectAppInsert(1, 'x'.code)
        editor.report(73, 72)
        assertTrue(editor.isContentBehindEditor())
        editor.report(72, 73)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(1, adjacencyBreaks.size)
    }

    @Test
    fun leftAfterHebrewWhoseParagraphStartIsUnknownStepsBack() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "Hi " + text(0x5D0).repeat(300))
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 303), isRestart = false)
        scheduler.runCurrent()
        // The keyboard sees only the Hebrew, not the Latin this paragraph starts with, so by the
        // text the Left goes nowhere. The field's report shows it steps back, as one that runs
        // left to right does.
        editor.expectAppStepBack(wholeCluster = true)
        editor.expectAppInsert(1, 'x'.code)
        editor.report(303, 302)
        assertTrue(editor.isContentBehindEditor())
        editor.report(302, 303)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(1, adjacencyBreaks.size)
    }

    @Test
    fun leftsPastKnownTextAfterCursorInHebrew() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(
            textBeforeSelection = text(0x5E9),
            textAfterSelection = text(0x5D0).repeat(300),
        )
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 1), isRestart = false)
        scheduler.runCurrent()
        // More Lefts than the keyboard knows text after the cursor for, then "x". Past what it
        // knows the text goes on, so each Left still moves forward.
        repeat(257) { editor.expectAppStepBack(wholeCluster = true) }
        editor.expectAppInsert(1, 'x'.code)
        for (cursor in 1..257) editor.report(cursor, cursor + 1)
        assertTrue(editor.isContentBehindEditor())
        editor.report(258, 259)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(257, adjacencyBreaks.size)
    }

    @Test
    fun leftOverLineBreakInHebrewKeepsMovingForward() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(
            textBeforeSelection = text(0x5E9),
            textAfterSelection = text(0x5DC) + "\n" + text(0x5D5, 0x5DD),
        )
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 1), isRestart = false)
        scheduler.runCurrent()
        // Left over a letter, over the line break, and into the next Hebrew line, then "x".
        repeat(3) { editor.expectAppStepBack(wholeCluster = true) }
        editor.expectAppInsert(1, 'x'.code)
        editor.report(1, 2)
        editor.report(2, 3)
        editor.report(3, 4)
        assertTrue(editor.isContentBehindEditor())
        editor.report(4, 5)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(3, adjacencyBreaks.size)
    }

    @Test
    fun leftCollapsesSelectionAcrossLineBreakToItsStart() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(
            textBeforeSelection = text(0x5E9, 0x5DC),
            textAfterSelection = "llo",
            selectedText = text(0x5D5, 0x5DD) + "\nhe",
        )
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 2, selectionEnd = 7), isRestart = false)
        scheduler.runCurrent()
        // A selection from a Hebrew line into a Latin one. Compose collapses it the way of the
        // line it ends on, so to its start.
        editor.expectAppStepBack(wholeCluster = true)
        editor.expectAppInsert(1, 'x'.code)
        editor.handleSelectionUpdate(EditorRange(2, 7), EditorRange.cursor(2), EditorRange.Unspecified)
        scheduler.runCurrent()
        assertTrue(editor.isContentBehindEditor())
        editor.report(2, 3)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(1, adjacencyBreaks.size)
    }

    @Test
    fun leftInHebrewAfterLatinAndParagraphSeparatorStepsBack() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(
            textBeforeSelection = "abc" + text(0x2029, 0x5E9, 0x5DC),
            textAfterSelection = text(0x5D5, 0x5DD),
        )
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 6), isRestart = false)
        scheduler.runCurrent()
        // Android's text layout starts a paragraph only after a line feed, so this one starts
        // with Latin and Compose's Left steps back.
        editor.expectAppStepBack(wholeCluster = true)
        editor.expectAppInsert(1, 'x'.code)
        editor.report(6, 5)
        assertTrue(editor.isContentBehindEditor())
        editor.report(5, 6)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(1, adjacencyBreaks.size)
    }

    @Test
    fun leftCollapsesSelectionFromHebrewLineToItsEndInTextView() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(
            textBeforeSelection = text(0x5E9, 0x5DC),
            textAfterSelection = "llo",
            selectedText = text(0x5D5, 0x5DD) + "\nhe",
        )
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 2, selectionEnd = 7), isRestart = false)
        scheduler.runCurrent()
        // A TextView collapses a selection over a line break the way the line its anchor is on
        // runs, here the Hebrew one, so to its left end, which is where it ends.
        editor.expectAppStepBack(wholeCluster = true)
        editor.expectAppInsert(1, 'x'.code)
        editor.handleSelectionUpdate(EditorRange(2, 7), EditorRange.cursor(7), EditorRange.Unspecified)
        scheduler.runCurrent()
        assertTrue(editor.isContentBehindEditor())
        editor.report(7, 8)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(1, adjacencyBreaks.size)
    }

    @Test
    fun leftCollapsesBackwardSelectionByItsAnchorsLine() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(
            textBeforeSelection = text(0x5D0, 0x5D1, 0xFE00),
            textAfterSelection = "c",
            selectedText = "\nab",
        )

        editor.inputConnection = connection.connection
        // Selected from the end back over the line break, so the anchor is on the Latin line.
        editor.handleStartInputView(editorInfo(selection = 6, selectionEnd = 3), isRestart = false)
        scheduler.runCurrent()
        // A TextView collapses it the way the anchor's line runs, to its left end at 3. Then a
        // TextView's Backspace takes the letter with its selector, and its Left moves forward over
        // the line break, since the Hebrew line runs right to left.
        editor.expectAppStepBack(wholeCluster = true)
        connection.textAfterSelection = "\nabc"
        connection.selectedText = ""
        editor.handleSelectionUpdate(EditorRange(3, 6), EditorRange.cursor(3), EditorRange.Unspecified)
        scheduler.runCurrent()
        editor.expectAppStepBack()
        connection.textBeforeSelection = text(0x5D0)
        editor.report(3, 1)
        editor.expectAppStepBack(wholeCluster = true)
        editor.expectAppInsert(1, 'x'.code)
        editor.report(1, 2)
        assertTrue(editor.isContentBehindEditor())
        editor.report(2, 3)

        assertFalse(editor.isContentBehindEditor())
    }

    @Test
    fun massSelectionKeepsTheAnchorOfABackwardSelection() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(
            textBeforeSelection = text(0x5D0, 0x5D1, 0xFE00) + "\nab",
            textAfterSelection = "c",
        )

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 6), isRestart = false)
        scheduler.runCurrent()
        // A swipe selects back from the end over the line break, as the selection in
        // leftCollapsesBackwardSelectionByItsAnchorsLine was, and the field is read once it ends.
        editor.massSelectionUpdate(EditorRange(3, 6), isAnchorFirst = false)
        connection.textBeforeSelection = text(0x5D0, 0x5D1, 0xFE00)
        connection.selectedText = "\nab"
        editor.massSelectionEnd()
        scheduler.runCurrent()
        editor.expectAppStepBack(wholeCluster = true)
        connection.textAfterSelection = "\nabc"
        connection.selectedText = ""
        editor.handleSelectionUpdate(EditorRange(3, 6), EditorRange.cursor(3), EditorRange.Unspecified)
        scheduler.runCurrent()
        editor.expectAppStepBack()
        connection.textBeforeSelection = text(0x5D0)
        editor.report(3, 1)
        editor.expectAppStepBack(wholeCluster = true)
        editor.expectAppInsert(1, 'x'.code)
        editor.report(1, 2)
        assertTrue(editor.isContentBehindEditor())
        editor.report(2, 3)

        assertFalse(editor.isContentBehindEditor())
    }

    @Test
    fun leftInHebrewInLeftToRightFieldStepsBack() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(
            textBeforeSelection = text(0x5E9, 0x5DC),
            textAfterSelection = text(0x5D5, 0x5DD),
        )
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 2), isRestart = false)
        scheduler.runCurrent()
        // A Compose field in an English app and a web field with no direction set run left to
        // right whatever the text, so their Left steps back through Hebrew.
        editor.expectAppStepBack(wholeCluster = true)
        editor.expectAppInsert(1, 'x'.code)
        editor.report(2, 1)
        assertTrue(editor.isContentBehindEditor())
        editor.report(1, 2)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(1, adjacencyBreaks.size)
    }

    @Test
    fun leftInLatinInRightToLeftFieldMovesForward() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "ab", textAfterSelection = "cd")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 2), isRestart = false)
        scheduler.runCurrent()
        // A Compose field in a Hebrew app and a web page set right to left run that way whatever
        // the text, so their Left moves forward through Latin.
        editor.expectAppStepBack(wholeCluster = true)
        editor.expectAppInsert(1, 'x'.code)
        editor.report(2, 3)
        assertTrue(editor.isContentBehindEditor())
        editor.report(3, 4)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(1, adjacencyBreaks.size)
    }

    @Test
    fun leftDirectionIsLearnedFromTheFieldsFirstReport() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(
            textBeforeSelection = text(0x5E9, 0x5DC, 0x5D5, 0x5DD),
            textAfterSelection = text(0x5E2, 0x5D5, 0x5DC, 0x5DD),
        )

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 4), isRestart = false)
        scheduler.runCurrent()
        // This field's Left stepped back through Hebrew, so it runs left to right, and a later
        // Left the field reports as a move forward is something else, like a tap.
        editor.expectAppStepBack(wholeCluster = true)
        editor.expectAppInsert(1, 'x'.code)
        editor.report(4, 3)
        editor.report(3, 4)
        editor.expectAppStepBack(wholeCluster = true)
        editor.expectAppInsert(1, 'y'.code)
        editor.report(4, 5)

        assertFalse(editor.isContentBehindEditor())
    }

    @Test
    fun leftDeepInLongHebrewParagraphMovesForward() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(
            textBeforeSelection = text(0x5D0).repeat(300),
            textAfterSelection = text(0x5D1, 0x5D2),
        )
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 300), isRestart = false)
        scheduler.runCurrent()
        // The field gives fewer chars before the cursor than the paragraph has, so the keyboard
        // never sees where it starts. The Hebrew it does see still moves the Left forward, and so
        // does the next one after a Hebrew letter typed.
        editor.expectAppStepBack(wholeCluster = true)
        editor.expectAppInsert(1, 0x5D3)
        editor.expectAppStepBack(wholeCluster = true)
        editor.report(300, 301)
        editor.report(301, 302)
        assertTrue(editor.isContentBehindEditor())
        editor.report(302, 303)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(2, adjacencyBreaks.size)
    }

    @Test
    fun leftAtEndOfTextFromAppThatReturnsAllOfIt() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(
            textBeforeSelection = "",
            textAfterSelection = text(0x5D0).repeat(130),
            returnsAllText = true,
        )

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 0), isRestart = false)
        scheduler.runCurrent()
        // The app gave all 130 chars after the cursor though only 128 were asked for, so the
        // keyboard knows where the text ends, and the last Left, which has nowhere to go, isn't
        // waited for.
        repeat(131) { editor.expectAppStepBack(wholeCluster = true) }
        editor.expectAppInsert(1, 'x'.code)
        for (cursor in 0 until 130) editor.report(cursor, cursor + 1)
        assertTrue(editor.isContentBehindEditor())
        editor.report(130, 131)

        assertFalse(editor.isContentBehindEditor())
    }

    @Test
    fun leftAtFieldStartDoesNotHoldTheNextSpace() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "", textAfterSelection = "abc")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 0), isRestart = false)
        scheduler.runCurrent()
        // Left at the start of Latin text goes nowhere, then "h" and "i", each reported. A field
        // whose Left moves forward would explain each report one key early, but the field that
        // left the cursor in place has caught up, so a space typed now isn't held back.
        editor.expectAppStepBack(wholeCluster = true)
        editor.expectAppInsert(1, 'h'.code)
        editor.report(0, 1)
        editor.expectAppInsert(1, 'i'.code)
        editor.report(1, 2)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(0, adjacencyBreaks.size)
    }

    @Test
    fun leftAtFieldStartHoldsBrieflyWhereLayoutsRunRightToLeft() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "", textAfterSelection = "abc")

        editor.runsRightToLeft = true
        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 0), isRestart = false)
        scheduler.runCurrent()
        // As in leftAtFieldStartDoesNotHoldTheNextSpace, but the system runs right to left, so a
        // field whose Left moves forward is likely too, and it still owes its "i".
        editor.expectAppStepBack(wholeCluster = true)
        editor.expectAppInsert(1, 'h'.code)
        editor.report(0, 1)
        editor.expectAppInsert(1, 'i'.code)
        editor.report(1, 2)
        assertTrue(editor.isContentBehindEditor())
        ShadowSystemClock.advanceBy(Duration.ofMillis(400))

        assertFalse(editor.isContentBehindEditor())
    }

    @Test
    fun leftBeforeHebrewAtFieldStartWaitsForTheFieldsLastKey() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(
            textBeforeSelection = "",
            textAfterSelection = text(0x5E9, 0x5DC, 0x5D5, 0x5DD),
        )

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 0), isRestart = false)
        scheduler.runCurrent()
        // A TextView's Left moves forward into the Hebrew, where a field that runs left to right
        // goes nowhere and so explains each report one key late. That one has caught up after the
        // second report, but the TextView still owes its second letter.
        editor.expectAppStepBack(wholeCluster = true)
        editor.expectAppInsert(1, 0x5D0)
        editor.expectAppInsert(1, 0x5D1)
        editor.report(0, 1)
        editor.report(1, 2)
        assertTrue(editor.isContentBehindEditor())
        editor.report(2, 3)

        assertFalse(editor.isContentBehindEditor())
    }

    @Test
    fun leftAtEndOfRightToLeftTextWaitsForASlowApp() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = text(0x5E9, 0x5DC, 0x5D5))

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 3), isRestart = false)
        scheduler.runCurrent()
        // The keyboard types the last letter, and the app takes 350 ms to report it. A Left after
        // it, in a field that runs left to right, gets its report as late.
        editor.commitText(text(0x5DD))
        ShadowSystemClock.advanceBy(Duration.ofMillis(350))
        connection.textBeforeSelection = text(0x5E9, 0x5DC, 0x5D5, 0x5DD)
        editor.report(3, 4)
        editor.expectAppStepBack(wholeCluster = true)
        ShadowSystemClock.advanceBy(Duration.ofMillis(310))
        assertTrue(editor.isContentBehindEditor())
        ShadowSystemClock.advanceBy(Duration.ofMillis(40))
        editor.report(4, 3)

        assertFalse(editor.isContentBehindEditor())
    }

    @Test
    fun leftAtEndOfRightToLeftTextHoldsOnlyBriefly() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = text(0x5E9, 0x5DC, 0x5D5, 0x5DD))

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 4), isRestart = false)
        scheduler.runCurrent()
        // A Left at the end of Hebrew text goes nowhere in a TextView, which then reports nothing,
        // and steps back in a field that runs left to right. No report soon means it went nowhere.
        editor.expectAppStepBack(wholeCluster = true)
        assertTrue(editor.isContentBehindEditor())
        ShadowSystemClock.advanceBy(Duration.ofMillis(400))

        assertFalse(editor.isContentBehindEditor())
    }

    @Test
    fun leftThatStaysThenTypingBreaksNoAdjacency() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = text(0x5E9, 0x5DC, 0x5D5, 0x5DD))
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 4), isRestart = false)
        scheduler.runCurrent()
        // Left at the end of Hebrew text, then "x" and "y" before the first report. A field that
        // runs left to right would report its "y" at 5 too, but the report at 6 rules it out, so
        // the Left moved nothing and the typing still runs on.
        editor.expectAppStepBack(wholeCluster = true)
        editor.expectAppInsert(1, 'x'.code)
        editor.expectAppInsert(1, 'y'.code)
        editor.report(4, 5)
        editor.report(5, 6)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(0, adjacencyBreaks.size)
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
    fun keyboardsOwnSpaceAfterQuietLeftIsNotTheApps() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "purple ", textAfterSelection = "end")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 7), isRestart = false)
        scheduler.runCurrent()
        // "ab", Left, "b" before "end", reported at once. A field whose Left moved forward would
        // still owe its Left at 10, which is where the keyboard's own space lands, and that report
        // is the keyboard's, so it doesn't tell which way this field moves a Left.
        repeat(2) { editor.expectAppInsert(1) }
        editor.expectAppStepBack(wholeCluster = true)
        editor.expectAppInsert(1)
        connection.textBeforeSelection = "purple ab"
        editor.report(7, 9)
        ShadowSystemClock.advanceBy(Duration.ofMillis(400))
        editor.commitText(" ")
        editor.expectAppInsert(1)
        connection.textBeforeSelection = "purple ab "
        editor.report(9, 10)
        assertTrue(editor.isContentBehindEditor())
        connection.textBeforeSelection = "purple ab c"
        editor.report(10, 11)

        assertFalse(editor.isContentBehindEditor())
        // The Left in the burst breaks adjacency once, as the Backspace in quietLoopYieldsToSpace.
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

    @Test
    fun stalledLoopMatchesLateReportsAfterQuiet() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "purple ")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 7), isRestart = false)
        scheduler.runCurrent()
        // "ab", Backspace, "b": the app reports "a" and "b" right away, then stalls past the quiet
        // timeout, so "c" is typed after it as if the app were done. Then the rest comes.
        repeat(2) { editor.expectAppInsert(1) }
        editor.expectAppStepBack()
        editor.expectAppInsert(1)
        editor.report(7, 8)
        editor.report(8, 9)
        ShadowSystemClock.advanceBy(Duration.ofMillis(400))
        editor.expectAppInsert(1)
        for ((from, to) in listOf(9 to 8, 8 to 9)) {
            assertTrue("behind before the report at $to", editor.isContentBehindEditor())
            editor.report(from, to)
        }
        assertTrue(editor.isContentBehindEditor())
        editor.report(9, 10)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(1, adjacencyBreaks.size)
    }

    @Test
    fun keyboardsOwnSpaceReportedWithTheAppsKeys() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "purple")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 6), isRestart = false)
        scheduler.runCurrent()
        // The keyboard types a space, then "s" and Backspace go to the app, which reports all of
        // it at once. That report reads as the space alone at first, with the keys still to come.
        editor.commitText(" ")
        editor.expectAppInsert(1, 's'.code)
        editor.expectAppStepBack()
        connection.textBeforeSelection = "purple "
        editor.report(6, 7)
        assertTrue(editor.isContentBehindEditor())
        ShadowSystemClock.advanceBy(Duration.ofMillis(400))
        // No report followed, so it covered them, and the next word is the keyboard's again.
        assertFalse(editor.isContentBehindEditor())
        editor.commitText("dog")
        connection.textBeforeSelection = "purple dog"
        editor.report(7, 10)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(0, adjacencyBreaks.size)
    }

    @Test
    fun leftAtEndOfLatinTextWaitsForItsReport() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "helo")

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 4), isRestart = false)
        scheduler.runCurrent()
        // A Left at the end of Latin text steps back in every field likely here. Only a field
        // whose Left runs right to left whatever the text leaves it in place, and that one isn't
        // likely where the system runs left to right, so a slow report is still waited for.
        editor.expectAppStepBack(wholeCluster = true)
        ShadowSystemClock.advanceBy(Duration.ofMillis(400))
        assertTrue(editor.isContentBehindEditor())
        editor.report(4, 3)

        assertFalse(editor.isContentBehindEditor())
    }

    @Test
    fun lateReportAfterQuietMatchesTheSettledKeyFirst() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "purple ")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 7), isRestart = false)
        scheduler.runCurrent()
        // "ab", Backspace, "b": the app reports "a" and "b" at once, then stalls past the quiet
        // timeout, and a Backspace typed then lands where the first one did. The late report there
        // is the first one's, so the rest still match in order.
        repeat(2) { editor.expectAppInsert(1) }
        editor.expectAppStepBack()
        editor.expectAppInsert(1)
        ShadowSystemClock.advanceBy(Duration.ofMillis(50))
        connection.textBeforeSelection = "purple ab"
        editor.report(7, 9)
        ShadowSystemClock.advanceBy(Duration.ofMillis(350))
        editor.expectAppStepBack()
        ShadowSystemClock.advanceBy(Duration.ofMillis(20))
        for ((from, to) in listOf(9 to 8, 8 to 9)) {
            editor.report(from, to)
            assertTrue("behind after the report at $to", editor.isContentBehindEditor())
        }
        editor.report(9, 8)

        assertFalse(editor.isContentBehindEditor())
        // Each Backspace breaks adjacency once, and no report reads as a jump.
        assertEquals(2, adjacencyBreaks.size)
    }

    @Test
    fun rightMovesForwardInBurst() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "ab")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 2), isRestart = false)
        scheduler.runCurrent()
        // Left, "x", Right back past the "b", and "y".
        editor.expectAppStepBack(wholeCluster = true)
        editor.expectAppInsert(1, 'x'.code)
        editor.expectAppStepForward()
        editor.expectAppInsert(1, 'y'.code)
        for ((from, to) in listOf(2 to 1, 1 to 2, 2 to 3)) {
            editor.report(from, to)
            assertTrue("behind after the report at $to", editor.isContentBehindEditor())
        }
        editor.report(3, 4)

        assertFalse(editor.isContentBehindEditor())
        // The Left and the Right each break the run of typing.
        assertEquals(2, adjacencyBreaks.size)
    }

    @Test
    fun rightAtEndOfTextStays() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "ab")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 2), isRestart = false)
        scheduler.runCurrent()
        // At the end of the text a Right has nowhere to go, so "x" goes in right there.
        editor.expectAppStepForward()
        editor.expectAppInsert(1, 'x'.code)
        editor.report(2, 3)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(0, adjacencyBreaks.size)
    }

    @Test
    fun rightInRightToLeftTextStepsBack() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(
            textBeforeSelection = text(0x5E9, 0x5DC),
            textAfterSelection = text(0x5D5, 0x5DD),
        )
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 2), isRestart = false)
        scheduler.runCurrent()
        // In a Hebrew paragraph a Right moves back a letter, then "x".
        editor.expectAppStepForward()
        editor.expectAppInsert(1, 'x'.code)
        editor.report(2, 1)
        assertTrue(editor.isContentBehindEditor())
        editor.report(1, 2)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(1, adjacencyBreaks.size)
    }

    @Test
    fun rightCollapsesSelectionToItsEnd() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(
            textBeforeSelection = "a",
            textAfterSelection = "d",
            selectedText = "bc",
        )
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 1, selectionEnd = 3), isRestart = false)
        scheduler.runCurrent()
        editor.expectAppStepForward()
        editor.expectAppInsert(1, 'x'.code)
        editor.handleSelectionUpdate(EditorRange(1, 3), EditorRange.cursor(3), EditorRange.Unspecified)
        scheduler.runCurrent()
        assertTrue(editor.isContentBehindEditor())
        editor.report(3, 4)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(1, adjacencyBreaks.size)
    }

    @Test
    fun homeAndEndInBurstInSingleLineField() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "ab")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 2), isRestart = false)
        scheduler.runCurrent()
        // Home, "x", End and "y" leave "xaby".
        editor.expectAppLineEdge(toEnd = false)
        editor.expectAppInsert(1, 'x'.code)
        editor.expectAppLineEdge(toEnd = true)
        editor.expectAppInsert(1, 'y'.code)
        for ((from, to) in listOf(2 to 0, 0 to 1, 1 to 3)) {
            editor.report(from, to)
            assertTrue("behind after the report at $to", editor.isContentBehindEditor())
        }
        editor.report(3, 4)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(2, adjacencyBreaks.size)
    }

    @Test
    fun endPastKnownTextHoldsUntilTheAppGoesQuiet() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "ab", textAfterSelection = "c".repeat(200))

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 2), isRestart = false)
        scheduler.runCurrent()
        // The keyboard read only part of the text after the cursor, so it can't tell where End
        // lands, nor where the "x" after it goes.
        editor.expectAppLineEdge(toEnd = true)
        editor.expectAppInsert(1, 'x'.code)
        editor.report(2, 202)
        assertTrue(editor.isContentBehindEditor())
        editor.report(202, 203)
        assertTrue(editor.isContentBehindEditor())
        ShadowSystemClock.advanceBy(Duration.ofMillis(400))

        assertFalse(editor.isContentBehindEditor())
    }

    @Test
    fun homeInRightToLeftTextViewGoesToTheLineEnd() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(
            textBeforeSelection = text(0x5E9, 0x5DC),
            textAfterSelection = text(0x5D5, 0x5DD),
        )
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 2), isRestart = false)
        scheduler.runCurrent()
        // A TextView's Home goes to the right edge of a Hebrew line, which is its end, then "x".
        editor.expectAppLineEdge(toEnd = false)
        editor.expectAppInsert(1, 'x'.code)
        editor.report(2, 4)
        assertTrue(editor.isContentBehindEditor())
        editor.report(4, 5)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(1, adjacencyBreaks.size)
    }

    @Test
    fun lineEdgeNoWayPredictedResumesFromItsReport() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(
            textBeforeSelection = text(0x5E9, 0x5DC),
            textAfterSelection = text(0x5D5, 0x5DD),
        )
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 2), isRestart = false)
        scheduler.runCurrent()
        // A web field set right to left may take Home somewhere else again. Its report comes
        // before the next key, so that key starts from it.
        editor.expectAppLineEdge(toEnd = false)
        assertTrue(editor.isContentBehindEditor())
        editor.report(2, 1)
        assertFalse(editor.isContentBehindEditor())
        editor.expectAppInsert(1, 'x'.code)
        editor.report(1, 2)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(1, adjacencyBreaks.size)
    }

    @Test
    fun tabTypesATabInMultiLineCompose() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "ab")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 2), isRestart = false)
        scheduler.runCurrent()
        // A multi-line Compose field types a tab, where a TextView leaves the cursor in place.
        editor.expectAppTab(multiLine = true)
        editor.expectAppInsert(1, 'x'.code)
        editor.report(2, 3)
        assertTrue(editor.isContentBehindEditor())
        editor.report(3, 4)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(0, adjacencyBreaks.size)
    }

    @Test
    fun tabLeavesTheCursorInMultiLineTextView() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "ab")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 2), isRestart = false)
        scheduler.runCurrent()
        // The report for "x" could be a Compose field's tab, so the "x" it would still owe is
        // waited for until the quiet time is up.
        editor.expectAppTab(multiLine = true)
        editor.expectAppInsert(1, 'x'.code)
        editor.report(2, 3)
        assertTrue(editor.isContentBehindEditor())
        ShadowSystemClock.advanceBy(Duration.ofMillis(350))

        assertFalse(editor.isContentBehindEditor())
        assertEquals(0, adjacencyBreaks.size)
    }

    @Test
    fun tabInSingleLineFieldMovesNothing() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "ab")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 2), isRestart = false)
        scheduler.runCurrent()
        // A Tab there moves the focus or nothing. The report reads as the "x"'s, though it's held
        // until the quiet time is up, in case the Tab did move the cursor.
        editor.expectAppTab(multiLine = false)
        editor.expectAppInsert(1, 'x'.code)
        editor.report(2, 3)
        assertTrue(editor.isContentBehindEditor())
        ShadowSystemClock.advanceBy(Duration.ofMillis(350))

        assertFalse(editor.isContentBehindEditor())
        assertEquals(0, adjacencyBreaks.size)
    }

    @Test
    fun pasteThenKeysHoldUntilTheAppGoesQuiet() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "ab")

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 2), isRestart = false)
        scheduler.runCurrent()
        // Ctrl+V, then "x" and "y" before the paste is reported: where they go can't be told.
        editor.expectAppUnpredictable()
        editor.expectAppInsert(1, 'x'.code)
        editor.report(2, 7)
        assertTrue(editor.isContentBehindEditor())
        editor.expectAppInsert(1, 'y'.code)
        editor.report(7, 8)
        assertTrue(editor.isContentBehindEditor())
        ShadowSystemClock.advanceBy(Duration.ofMillis(200))
        editor.report(8, 9)
        ShadowSystemClock.advanceBy(Duration.ofMillis(200))
        assertTrue(editor.isContentBehindEditor())
        ShadowSystemClock.advanceBy(Duration.ofMillis(200))

        assertFalse(editor.isContentBehindEditor())
    }

    @Test
    fun pasteAloneResumesFromItsReport() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "ab")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 2), isRestart = false)
        scheduler.runCurrent()
        // The paste's report comes before the next key, so that key starts from it.
        editor.expectAppUnpredictable()
        assertTrue(editor.isContentBehindEditor())
        connection.textBeforeSelection = "ab12345"
        editor.report(2, 7)
        assertFalse(editor.isContentBehindEditor())
        editor.expectAppInsert(1, 'x'.code)
        editor.report(7, 8)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(1, adjacencyBreaks.size)
    }

    @Test
    fun unpredictableKeyThatMovesNothingKeepsTheBurstTracked() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "ab")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 2), isRestart = false)
        scheduler.runCurrent()
        // An Up arrow at the start of a single-line field, then "x" right after it. A paste of one
        // char would be reported at the same cursor, so the next report is waited for a while.
        editor.expectAppUnpredictable()
        editor.expectAppInsert(1, 'x'.code)
        editor.report(2, 3)
        assertTrue(editor.isContentBehindEditor())
        ShadowSystemClock.advanceBy(Duration.ofMillis(350))
        assertFalse(editor.isContentBehindEditor())
        editor.expectAppInsert(1, 'y'.code)
        editor.report(3, 4)

        assertFalse(editor.isContentBehindEditor())
        assertEquals(0, adjacencyBreaks.size)
    }

    @Test
    fun selectAllThenTypingStartsFromTheSelection() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "ab")
        val adjacencyBreaks = collectAdjacencyBreaks(editor)

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 2), isRestart = false)
        scheduler.runCurrent()
        // Ctrl+A, reported before the next key, which types over the selection.
        editor.expectAppUnpredictable()
        connection.textBeforeSelection = ""
        connection.selectedText = "ab"
        editor.handleSelectionUpdate(EditorRange.cursor(2), EditorRange(0, 2), EditorRange.Unspecified)
        scheduler.runCurrent()
        assertFalse(editor.isContentBehindEditor())
        editor.expectAppInsert(1, 'x'.code)
        editor.handleSelectionUpdate(EditorRange(0, 2), EditorRange.cursor(1), EditorRange.Unspecified)
        scheduler.runCurrent()

        assertFalse(editor.isContentBehindEditor())
        assertEquals(1, adjacencyBreaks.size)
    }

    @Test
    fun unpredictableKeyWithNoReportHoldsBriefly() {
        val editor = TestEditorInstance(context)
        val connection = LifecycleRecordingInputConnection(textBeforeSelection = "ab")

        editor.inputConnection = connection.connection
        editor.handleStartInputView(editorInfo(selection = 2), isRestart = false)
        scheduler.runCurrent()
        // A paste with nothing to paste: no report comes, so the hold ends with the quiet time.
        editor.expectAppUnpredictable()
        ShadowSystemClock.advanceBy(Duration.ofMillis(250))
        assertTrue(editor.isContentBehindEditor())
        ShadowSystemClock.advanceBy(Duration.ofMillis(100))

        assertFalse(editor.isContentBehindEditor())
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
    var runsRightToLeft = false
    /** Finds the word before the cursor, with composing still off (a field whose composing was turned off). */
    var findsWords = false

    fun canReplaceWord(): Boolean = canReplaceCurrentWord()

    fun replaceWord(text: String): Boolean = replaceCurrentWord(text)

    fun massSelectionUpdate(newSelection: EditorRange, isAnchorFirst: Boolean = true) {
        handleMassSelectionUpdate(newSelection, EditorRange.Unspecified, isAnchorFirst)
    }

    fun massSelectionEnd() {
        handleMassSelectionEnd()
    }

    override fun currentInputConnection(): InputConnection? = inputConnection

    override fun systemRunsRightToLeft(): Boolean = runsRightToLeft

    override fun determineComposingEnabled(): Boolean = false

    override fun determineComposer(composerName: ExtensionComponentName): Composer = Appender

    override fun shouldDetermineComposingRegion(editorInfo: FlorisEditorInfo): Boolean = findsWords

    override fun reevaluateInputShiftState() = Unit
}

private class LifecycleRecordingInputConnection(
    var textBeforeSelection: String,
    var textAfterSelection: String = "",
    var selectedText: String = "",
    /** Like an app that ignores how many chars a read asks for. */
    private val returnsAllText: Boolean = false,
    /** Whether setSelection succeeds; false like an unset proxy call so older cases keep their behavior. */
    private val acceptsSelection: Boolean = false,
) : InvocationHandler {
    private val calls = mutableListOf<String>()
    val composingCalls: List<String>
        get() = calls.filter { call ->
            call == "finishComposingText" || call.startsWith("setComposingRegion")
        }
    val editAndReadCalls: List<String>
        get() = calls.filter { call -> call == "commitText" || call.startsWith("getTextBeforeCursor") }
    val selectionCalls: List<String>
        get() = calls.filter { call -> call.startsWith("setSelection") }
    val committedTexts = mutableListOf<String>()

    val connection: InputConnection = Proxy.newProxyInstance(
        InputConnection::class.java.classLoader,
        arrayOf(InputConnection::class.java),
        this,
    ) as InputConnection

    override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
        return when (method.name) {
            "getTextBeforeCursor" -> {
                calls += "getTextBeforeCursor(${args?.get(0)})"
                if (returnsAllText) textBeforeSelection else textBeforeSelection.takeLast(args?.get(0) as Int)
            }
            "commitText" -> {
                calls += "commitText"
                committedTexts += args?.get(0).toString()
                true
            }
            "setSelection" -> {
                calls += "setSelection(${args?.get(0)},${args?.get(1)})"
                acceptsSelection
            }
            "getTextAfterCursor" ->
                if (returnsAllText) textAfterSelection else textAfterSelection.take(args?.get(0) as Int)
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

private fun editorInfo(selection: Int, selectionEnd: Int = selection): FlorisEditorInfo {
    return FlorisEditorInfo.wrap(
        EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
            initialSelStart = selection
            initialSelEnd = selectionEnd
        },
    )
}
