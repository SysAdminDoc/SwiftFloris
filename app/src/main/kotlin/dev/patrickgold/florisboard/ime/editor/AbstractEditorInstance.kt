/*
 * Copyright (C) 2022-2025 The FlorisBoard Contributors
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
import android.content.res.Resources
import android.icu.lang.UCharacter
import android.icu.text.BreakIterator
import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.text.TextUtils
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputConnection
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.ime.nlp.BreakIteratorGroup
import dev.patrickgold.florisboard.ime.text.composing.Composer
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.lib.ext.ExtensionComponentName
import dev.patrickgold.florisboard.nlpManager
import dev.patrickgold.florisboard.subtypeManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class OperationUnit {
    CHARACTERS,
    WORDS;
}

enum class OperationScope {
    BEFORE_CURSOR,
    AFTER_CURSOR;
}

@Suppress("BlockingMethodInNonBlockingContext")
abstract class AbstractEditorInstance(context: Context) {
    companion object {
        private const val NumCharsBeforeCursor: Int = 256
        private const val NumCharsAfterCursor: Int = 128
        private const val NumCharsSafeMarginBeforeCursor: Int = 128
        /**
         * Keys the app hasn't reported yet. A key burst (a macro, a scanner) can run well ahead of
         * the reports, and dropping the oldest key makes every later report read as a jump.
         */
        private const val MaxPendingPassThroughKeys: Int = 1024
        /** Chars before the cursor one step back looks at, enough to step back over a typed word. */
        private const val NumKnownBackStepsBeforePassThrough: Int = 16
        /** Text a step back reads its steps from, enough for 16 chars unless they're long emoji sequences. */
        private const val NumCharsForBackStepsBeforePassThrough: Int = 64
        /**
         * Text a pending key remembers on each side of its cursor, as much as is read before it, so a
         * Left arrow can tell which way a paragraph that long runs.
         */
        private const val NumCharsKnownAroundPassThrough: Int = 256
        /** Stands in for a typed char the keyboard doesn't know: it never joins the char before it. */
        private val UnknownCharStandIn: Char = Char(0xFFFC)
        /** The same for a typed char two chars long (a private-use code point). */
        private const val UnknownSurrogatePairStandIn: Int = 0xF0000
        /**
         * The viramas that join two consonants into one Devanagari, Bengali, Gujarati, Odia, Telugu
         * or Malayalam conjunct (Unicode's InCB=Linker).
         */
        private val ConjunctViramas: Set<Int> = setOf(0x094D, 0x09CD, 0x0ACD, 0x0B4D, 0x0C4D, 0x0D4D)
        /**
         * Flags the back step of a consonant after one of the [ConjunctViramas]: a TextView's Left
         * arrow stops before it, a Compose or Chrome field's moves over the whole conjunct.
         */
        private const val ConjunctConsonantStep: Int = 1 shl 16
        /**
         * Flags the back step of a variation selector on a char with no accent between them (an
         * ideograph's): a TextView's or Chrome's Backspace deletes both, a Compose field's deletes
         * the selector alone.
         */
        private const val VariationSelectorStep: Int = 1 shl 17
        /** The combining enclosing keycap, deleted with the digit and selector it closes. */
        private const val KeycapMark: Int = 0x20E3
        /**
         * The selector that makes [InformationSource], the one letter that's also an emoji, an
         * emoji. Every field deletes the two at once, a Compose field too (it deletes an emoji whole).
         */
        private const val EmojiPresentationSelector: Int = 0xFE0F
        private const val InformationSource: Int = 0x2139
        /** An app reports its insert within a frame or two, so a cursor this late isn't that key's. */
        private const val PassThroughInsertTimeoutMillis: Long = 2_000L
        /**
         * How long keys a report may already cover hold the editor behind with no report after it,
         * at least. An app still working through a burst reports again well within this, or within
         * twice how late its last report came if that's longer.
         */
        private const val PassThroughQuietTimeoutMillis: Long = 300L
        //private const val NumCharsSafeMarginAfterCursor: Int = 0

        private const val CursorUpdateAll: Int =
            InputConnection.CURSOR_UPDATE_MONITOR or InputConnection.CURSOR_UPDATE_IMMEDIATE
        private const val CursorUpdateNone: Int = 0
    }

    private val keyboardManager by context.keyboardManager()
    private val subtypeManager by context.subtypeManager()
    private val nlpManager by context.nlpManager()
    private val scope = MainScope()
    private var pendingContentGenerationJob: Job? = null
    /** What [pendingContentGenerationJob] will run, so [settlePendingContent] can run it now instead. */
    private var pendingContentGeneration: PendingContentGeneration? = null
    private var contentGenerationSerial: Long = 0L
    protected val breakIterators = BreakIteratorGroup()

    private val _activeInfoFlow = MutableStateFlow(FlorisEditorInfo.Unspecified)
    val activeInfoFlow = _activeInfoFlow.asStateFlow()
    inline var activeInfo: FlorisEditorInfo
        get() = activeInfoFlow.value
        private set(v) {
            _activeInfoFlow.value = v
        }

    private val _activeCursorCapsModeFlow = MutableStateFlow(InputAttributes.CapsMode.NONE)
    val activeCursorCapsModeFlow = _activeCursorCapsModeFlow.asStateFlow()
    inline var activeCursorCapsMode: InputAttributes.CapsMode
        get() = activeCursorCapsModeFlow.value
        private set(v) {
            _activeCursorCapsModeFlow.value = v
        }

    private val _activeContentFlow = MutableStateFlow(EditorContent.Unspecified)
    val activeContentFlow = _activeContentFlow.asStateFlow()
    inline var activeContent: EditorContent
        get() = expectedContent() ?: activeContentFlow.value
        private set(v) {
            _activeContentFlow.value = v
        }
    private val expectedContentQueue = ExpectedContentQueue { passThroughKeys.keysSoFar() }
    private val passThroughKeys = PassThroughKeyTracker { systemRunsRightToLeft() }
    private val _lastCommitPosition = LastCommitPosition()
    val lastCommitPosition
        get() = LastCommitPosition(_lastCommitPosition)
    private val _commitAdjacencyBrokenFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val commitAdjacencyBrokenFlow = _commitAdjacencyBrokenFlow.asSharedFlow()

    fun expectedContent(): EditorContent? {
        return expectedContentQueue.peekNewestOrNull()
    }

    /**
     * Records that the app is about to insert [length] chars for a key the keyboard passed through
     * to it (a hardware key with no mapped layout), so the cursor move it reports reads as typing.
     * [codePoint] is the char typed, 0 if unknown: a later Backspace or Left arrow steps back over
     * it the way the field reads the text, so an accent typed on its own key goes with its letter.
     */
    fun expectAppInsert(length: Int, codePoint: Int = 0) {
        passThroughKeys.expect(length, codePoint, expectedContentQueue.peekNewestOrNull(), _activeContentFlow.value)
    }

    /**
     * Records that the app is about to step the cursor back one char itself for a plain Backspace
     * or Left arrow the keyboard passed through to it, so the keys typed after it stay tracked
     * instead of reading as jumps. A Left arrow passes [wholeCluster]: it moves over a letter and
     * its accents at once, where a Backspace deletes the accents one at a time. In a right-to-left
     * paragraph (Hebrew, Arabic) a Left arrow moves the cursor forward through the text instead.
     */
    fun expectAppStepBack(wholeCluster: Boolean = false) {
        passThroughKeys.expectStepBack(wholeCluster, expectedContentQueue.peekNewestOrNull(), _activeContentFlow.value)
    }

    /**
     * Records that the app is about to move the cursor a char itself for a plain Right arrow the
     * keyboard passed through to it: forward, or back through a right-to-left paragraph.
     */
    fun expectAppStepForward() {
        passThroughKeys.expectStepForward(expectedContentQueue.peekNewestOrNull(), _activeContentFlow.value)
    }

    /**
     * Records that the app is about to move the cursor to the start of a single-line field's text
     * for a plain Home, or to its end for a plain End when [toEnd], which the keyboard passed
     * through to it.
     */
    fun expectAppLineEdge(toEnd: Boolean) {
        passThroughKeys.expectLineEdge(toEnd, expectedContentQueue.peekNewestOrNull(), _activeContentFlow.value)
    }

    /**
     * Records that the app is about to handle a plain Tab the keyboard passed through to it. A
     * [multiLine] Compose field types a tab, and other fields move the focus or do nothing.
     */
    fun expectAppTab(multiLine: Boolean) {
        passThroughKeys.expectTab(multiLine, expectedContentQueue.peekNewestOrNull(), _activeContentFlow.value)
    }

    /**
     * Records that the app is about to handle a key the keyboard passed through to it that may
     * move the cursor or change the text in a way only its report tells (a paste, an Up arrow,
     * an undo). The keys typed after it stay tracked if it changed nothing, and otherwise the
     * editor holds until the app's reports go quiet and starts again from the last one.
     */
    fun expectAppUnpredictable() {
        passThroughKeys.expectUnpredictable(expectedContentQueue.peekNewestOrNull(), _activeContentFlow.value)
    }

    /**
     * Returns once the app has run every edit the keyboard sent it so far. The keyboard's edits
     * reach the app over the input connection, but a hardware key the keyboard handled goes back
     * over the key event channel, and the app can run the next key of a burst (a letter it types
     * itself) before those edits: "wiht", space, "river" came out "withriv er". A read is answered
     * on the app's main thread after the edits queued ahead of it, so returning from the key only
     * after the read keeps the next key behind them.
     */
    fun awaitAppEdits() {
        currentInputConnection()?.getTextBeforeCursor(1, 0)
    }

    /**
     * True while [activeContent] lags the editor's text: a key the app typed or deleted with itself
     * (see [expectAppInsert] and [expectAppStepBack]) isn't reported yet, or a cursor move the
     * keyboard didn't predict (that report, a jump) was read but isn't published yet. A key burst
     * (a macro, a text expander, a barcode scanner) gets ahead of the reports this way. The
     * keyboard's own edits never count, since their content is predicted and published at once.
     */
    fun isContentBehindEditor(): Boolean {
        return pendingContentGenerationJob != null || passThroughKeys.hasPending()
    }

    protected open fun currentInputConnection() = FlorisImeService.currentInputConnection()

    /**
     * True when the system's layout runs right to left, as most apps' do then. An app's Compose
     * field runs its Left arrow that way, so one that runs right to left is likely only then.
     */
    protected open fun systemRunsRightToLeft(): Boolean {
        return Resources.getSystem().configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
    }

    open fun handleStartInput(editorInfo: FlorisEditorInfo) {
        cancelPendingContentGeneration()
        activeInfo = editorInfo
        activeCursorCapsMode = editorInfo.initialCapsMode
        activeContent = EditorContent.Unspecified
        passThroughKeys.start(editorInfo.initialSelection, editorInfo.isInitialAnchorFirst)
        currentInputConnection()?.requestCursorUpdates(CursorUpdateAll)
    }

    open fun handleStartInputView(editorInfo: FlorisEditorInfo, isRestart: Boolean) {
        if (isRestart) {
            reset() // Just to make sure our state is correct after a restart
        }
        cancelPendingContentGeneration()
        val ic = currentInputConnection()
        activeInfo = editorInfo
        var selection = editorInfo.initialSelection
        if (ic == null || selection.isNotValid || editorInfo.isRawInputEditor) {
            activeCursorCapsMode = InputAttributes.CapsMode.NONE
            activeContent = EditorContent.Unspecified
            reevaluateInputShiftState()
            return
        }

        // Get text (ignore initial text of EditorInfo because some apps like to provide an old or invalid state)
        val textBeforeSelection = ic.getTextBeforeCursor(NumCharsBeforeCursor, 0) ?: ""
        val textAfterSelection = ic.getTextAfterCursor(NumCharsAfterCursor, 0) ?: ""
        val selectedText = ic.getSelectedText(0) ?: ""

        // Adjust initial selection issues as some apps like to do everything but provide the correct initial selection
        if (selection.length != selectedText.length) {
            selection = EditorRange(textBeforeSelection.length, textBeforeSelection.length + selectedText.length)
        } else if (selection.start > 0 || selection.end > 0) {
            if (textBeforeSelection.isEmpty() && textAfterSelection.isEmpty() && selectedText.isEmpty()) {
                selection = EditorRange(0, 0)
            }
        }
        // A selection rebuilt from the text read back runs from its lower end.
        passThroughKeys.start(selection, selection != editorInfo.initialSelection || editorInfo.isInitialAnchorFirst)

        launchContentGeneration(
            inputConnection = ic,
            generate = {
                generateContent(editorInfo, selection, textBeforeSelection, textAfterSelection, selectedText)
            },
            publish = { content ->
                activeCursorCapsMode = content.cursorCapsMode()
                activeContent = content
                reevaluateInputShiftState()
                ic.setComposingRegion(content.composing)
            },
        )
    }

    protected fun handleMassSelectionUpdate(newSelection: EditorRange, composing: EditorRange, isAnchorFirst: Boolean = true) {
        cancelPendingContentGeneration()
        activeCursorCapsMode = InputAttributes.CapsMode.NONE
        activeContent = EditorContent.selectionOnly(newSelection)
        if (composing.isValid) {
            currentInputConnection()?.setComposingRegion(EditorRange.Unspecified)
        }
        _lastCommitPosition.handleUpdateSelection(newSelection)
        passThroughKeys.start(newSelection, isAnchorFirst)
        // Mass selection is the user selecting text, so whatever comes next isn't typed in a row.
        notifyCommitAdjacencyBrokenIfNeeded(true)
    }

    /** Reads the field again once a mass selection ends. */
    protected fun handleMassSelectionEnd() {
        handleSelectionUpdate(
            EditorRange.Unspecified,
            activeContent.selection,
            EditorRange.Unspecified,
            passThroughKeys.isAnchorFirst(),
        )
    }

    /** [isAnchorFirst] says whether the anchor of [newSelection] is its lower end, which it no longer says itself. */
    open fun handleSelectionUpdate(
        oldSelection: EditorRange,
        newSelection: EditorRange,
        composing: EditorRange,
        isAnchorFirst: Boolean = true,
    ) {
        cancelPendingContentGeneration()
        val ic = currentInputConnection()
        val editorInfo = activeInfo
        if (ic == null || newSelection.isNotValid || editorInfo.isRawInputEditor) {
            activeCursorCapsMode = InputAttributes.CapsMode.NONE
            activeContent = EditorContent.Unspecified
            passThroughKeys.start(EditorRange.Unspecified)
            reevaluateInputShiftState()
            return
        }

        // A cursor the keyboard's own edit predicted is typing, not a jump, and so is the cursor after
        // a key the app inserted itself. Anything else that moves the cursor (a tap, an app rewriting
        // the text) means the next word isn't typed after the last.
        val keyboardEdit = expectedContentQueue.editLeaving(newSelection)
        val isKeyboardEdit = keyboardEdit != null
        val appReport = passThroughKeys.handleUpdateSelection(oldSelection, newSelection, isAnchorFirst, keyboardEdit)
        val isOwnEdit = appReport == PassThroughReport.Insert || isKeyboardEdit
        val leftTrackedCommit = _lastCommitPosition.handleUpdateSelection(newSelection)
        val isJump = !isOwnEdit && (leftTrackedCommit || newSelection != oldSelection)
        // An arrow, Home, End or Backspace the app handled is a jump too, but the keys typed after
        // it are still on their way, so their cursors stay expected.
        if (isJump && appReport == PassThroughReport.None) {
            passThroughKeys.dropPending()
        }
        notifyCommitAdjacencyBrokenIfNeeded(isJump)
        val expected = expectedContentQueue.popUntilOrNull {
            it.selection == newSelection && it.composing == composing &&
                it.textBeforeSelection.length >= NumCharsSafeMarginBeforeCursor.coerceAtMost(it.selection.start)
        }
        if (expected != null) {
            activeCursorCapsMode = expected.cursorCapsMode()
            activeContent = expected
            reevaluateInputShiftState()
            return
        }

        // Get Text
        val textBeforeSelection =
            if (newSelection.start > 0) ic.getTextBeforeCursor(NumCharsBeforeCursor, 0) ?: "" else ""
        val textAfterSelection = ic.getTextAfterCursor(NumCharsAfterCursor, 0) ?: ""
        val selectedText = if (newSelection.isSelectionMode) ic.getSelectedText(0) ?: "" else ""

        launchContentGeneration(
            inputConnection = ic,
            generate = {
                generateContent(editorInfo, newSelection, textBeforeSelection, textAfterSelection, selectedText)
            },
            publish = { content ->
                activeCursorCapsMode = content.cursorCapsMode()
                activeContent = content
                reevaluateInputShiftState()
                if (content.composing != composing) {
                    ic.setComposingRegion(content.composing)
                }
            },
        )
    }

    open fun handleFinishInputView() {
        reset()
    }

    open fun handleFinishInput() {
        reset()
        currentInputConnection()?.requestCursorUpdates(CursorUpdateNone)
    }

    protected open fun reset() {
        cancelPendingContentGeneration()
        activeInfo = FlorisEditorInfo.Unspecified
        activeCursorCapsMode = InputAttributes.CapsMode.NONE
        activeContent = EditorContent.Unspecified
        expectedContentQueue.clear()
        passThroughKeys.start(EditorRange.Unspecified)
        _lastCommitPosition.reset()
        // Leaving or restarting the field means the next word isn't typed after the last one. Say
        // so here: the info flow can go Unspecified and back to an equal value (the same chat after
        // a trip to another app) faster than its collector runs, and then it never sees a change.
        notifyCommitAdjacencyBrokenIfNeeded(true)
    }

    protected open fun reevaluateInputShiftState() {
        keyboardManager.reevaluateInputShiftState()
    }

    private fun launchContentGeneration(
        inputConnection: InputConnection,
        generate: suspend () -> EditorContent,
        publish: (EditorContent) -> Unit,
    ) {
        val serial = startContentGeneration()
        pendingContentGeneration = PendingContentGeneration(inputConnection, generate, publish)
        pendingContentGenerationJob = scope.launch {
            try {
                if (!isCurrentContentGeneration(serial, inputConnection)) {
                    return@launch
                }
                val content = generate()
                if (!isCurrentContentGeneration(serial, inputConnection)) {
                    return@launch
                }
                publish(content)
            } finally {
                if (contentGenerationSerial == serial) {
                    pendingContentGenerationJob = null
                    pendingContentGeneration = null
                }
            }
        }
    }

    /**
     * Publishes the content a cursor report is still generating right away, rather than when the
     * main thread gets to it. A hardware key the keyboard types itself can come in between: the
     * keys of a burst wait in line before the report's content does. An edit built on the content
     * from before a tap then predicts the wrong text, every report after it reads as a jump, and
     * the keyboard's copy stays wrong for the rest of the burst ("taht" typed right after a tap
     * was never corrected). Generating content is CPU-only, the way every edit already runs it.
     */
    fun settlePendingContent() {
        val pending = pendingContentGeneration ?: return
        cancelPendingContentGeneration()
        if (currentInputConnection() !== pending.inputConnection) return
        pending.publish(runBlocking { pending.generate() })
    }

    private fun startContentGeneration(): Long {
        pendingContentGenerationJob?.cancel()
        contentGenerationSerial += 1
        return contentGenerationSerial
    }

    private fun cancelPendingContentGeneration() {
        pendingContentGenerationJob?.cancel()
        pendingContentGenerationJob = null
        pendingContentGeneration = null
        contentGenerationSerial += 1
    }

    private fun notifyCommitAdjacencyBrokenIfNeeded(broken: Boolean) {
        if (broken) {
            _commitAdjacencyBrokenFlow.tryEmit(Unit)
        }
    }

    private fun isCurrentContentGeneration(serial: Long, inputConnection: InputConnection): Boolean {
        return contentGenerationSerial == serial && currentInputConnection() === inputConnection
    }

    private suspend fun generateContent(
        editorInfo: FlorisEditorInfo,
        selection: EditorRange,
        textBeforeSelection: CharSequence,
        textAfterSelection: CharSequence,
        selectedText: CharSequence,
    ): EditorContent {
        // Calculate offset and local selection
        val offset = selection.start - textBeforeSelection.length
        val localSelection = EditorRange(
            start = textBeforeSelection.length,
            end = textBeforeSelection.length + selectedText.length,
        )

        // Check consistency and exit if necessary
        if (offset < 0 || selection.translatedBy(-offset) != localSelection) {
            return EditorContent.Unspecified
        }

        // Determine local composing word range, if any
        val localCurrentWord =
            if (shouldDetermineComposingRegion(editorInfo) && localSelection.isCursorMode && textBeforeSelection.isNotEmpty()) {
                determineLocalComposing(textBeforeSelection, _lastCommitPosition.pos - offset)
            } else {
                EditorRange.Unspecified
            }
        val localComposing = if (determineComposingEnabled()) localCurrentWord else EditorRange.Unspecified

        // Build and publish text and content
        val text = buildString {
            append(textBeforeSelection)
            append(selectedText)
            append(textAfterSelection)
        }
        return EditorContent(text, offset, localSelection, localComposing, localCurrentWord)
    }

    private suspend fun EditorContent.generateCopy(
        editorInfo: FlorisEditorInfo = activeInfo,
        selection: EditorRange = this.selection,
        textBeforeSelection: CharSequence = this.textBeforeSelection,
        textAfterSelection: CharSequence = this.textAfterSelection,
        selectedText: CharSequence = this.selectedText,
    ): EditorContent {
        return generateContent(
            editorInfo, selection, textBeforeSelection, textAfterSelection, selectedText
        )
    }

    private fun EditorContent.cursorCapsMode(): InputAttributes.CapsMode {
        return when {
            localSelection.isNotValid -> InputAttributes.CapsMode.NONE
            else -> {
                InputAttributes.CapsMode.fromFlags(
                    TextUtils.getCapsMode(text, localSelection.start, activeInfo.inputAttributes.raw)
                )
            }
        }
    }

    abstract fun determineComposingEnabled(): Boolean

    abstract fun determineComposer(composerName: ExtensionComponentName): Composer

    protected open fun shouldDetermineComposingRegion(editorInfo: FlorisEditorInfo): Boolean {
        return EditorCompatibilityPolicy.snapshot(editorInfo).allowsComposing
    }

    // INVARIANT: must remain CPU-only (no I/O, no suspend delay). Called inside
    // runBlocking on the IME main thread from every commit/delete/selection path.
    private suspend fun determineLocalComposing(
        textBeforeSelection: CharSequence, localLastCommitPosition: Int
    ): EditorRange {
        return nlpManager.determineLocalComposing(textBeforeSelection, breakIterators, localLastCommitPosition)
    }

    private fun InputConnection.setComposingRegion(composing: EditorRange) {
        if (composing.isValid) {
            this.setComposingRegion(composing.start, composing.end)
        } else {
            this.finishComposingText()
        }
    }

    protected fun setSelection(selection: EditorRange): Boolean {
        if (activeInfo.isRawInputEditor) return false
        val content = activeContent
        if (content.selection == selection) return true
        val ic = currentInputConnection() ?: return false
        val newContent = runBlocking {
            val newContent = content
                .copy(localSelection = selection.translatedBy(-content.offset))
                .generateCopy(selection = selection)
            expectedContentQueue.push(newContent)
            newContent
        }
        EditorInputConnectionBatch.applySelection(ic, selection, newContent.composing)
        return true
    }

    open fun commitChar(char: String): Boolean {
        return commitChar(
            char = char,
            deletePreviousSpace = false,
            insertSpaceBeforeChar = false,
            insertSpaceAfterChar = false,
        )
    }

    protected fun commitChar(
        char: String,
        deletePreviousSpace: Boolean,
        insertSpaceBeforeChar: Boolean,
        insertSpaceAfterChar: Boolean,
    ): Boolean {
        val content = activeContent
        val selection = content.selection
        val isSingleChar = breakIterators.measureUCharsSync(
            char,
            1,
            subtypeManager.activeSubtype.primaryLocale,
        ) == char.length
        if (!isSingleChar || selection.isNotValid || selection.isSelectionMode || activeInfo.isRawInputEditor) {
            return commitTextInternal(char)
        }
        val ic = currentInputConnection() ?: return false
        val composer = determineComposer(subtypeManager.activeSubtype.composer)
        val previous = content.textBeforeSelection.takeLast(composer.toRead.coerceAtLeast(if (deletePreviousSpace) 1 else 0))
        val (tempRm, tempText) = composer.getActions(previous, char)
        val rm = if (deletePreviousSpace && previous.isNotEmpty() && previous.last() == ' ') tempRm + 1 else tempRm
        val finalText = buildString(tempText.length + 2) {
            if (insertSpaceBeforeChar) append(' ')
            append(tempText)
            if (insertSpaceAfterChar) append(' ')
        }
        if (rm <= 0) {
            commitTextInternal(finalText)
        } else {
            val newSelection = EditorRange.cursor(selection.start - rm + finalText.length)
            val newContent = runBlocking {
                val newContent = content.generateCopy(
                    selection = newSelection,
                    textBeforeSelection = buildString {
                        append(content.textBeforeSelection.dropLast(rm))
                        append(finalText)
                    },
                    selectedText = "",
                )
                expectedContentQueue.push(newContent)
                newContent
            }
            EditorInputConnectionBatch.replacePreviousWithComposingRegion(
                ic = ic,
                // Some editors mishandle negative composing-region starts when deletePreviousSpace bumps rm.
                replaceStart = (content.selection.start - rm).coerceAtLeast(0),
                replaceEnd = content.selection.start,
                text = finalText,
                composing = newContent.composing,
            )
        }
        return true
    }

    open fun commitText(text: String): Boolean = commitTextInternal(text)

    protected fun commitTextInternal(
        text: String,
        selectedTextSuggestion: Boolean = false,
    ): Boolean {
        val ic = currentInputConnection() ?: return false
        val content = activeContent
        val selection = content.selection
        if (activeInfo.isRawInputEditor) {
            EditorInputConnectionBatch.commitText(
                ic = ic,
                text = text,
                composing = null,
                selectedTextSuggestion = selectedTextSuggestion,
            )
        } else {
            val newContent = runBlocking {
                val newSelection = EditorRange.cursor(selection.start + text.length)
                val newContent = content.generateCopy(
                    selection = newSelection,
                    textBeforeSelection = buildString {
                        append(content.textBeforeSelection)
                        append(text)
                    },
                    selectedText = "",
                )
                expectedContentQueue.push(newContent)
                newContent
            }
            EditorInputConnectionBatch.commitText(
                ic = ic,
                text = text,
                composing = newContent.composing,
                selectedTextSuggestion = selectedTextSuggestion,
            )
        }
        return true
    }

    fun expandSnippet(triggerLength: Int, replacement: String): Boolean {
        if (triggerLength <= 0 || replacement.isEmpty()) return false
        val ic = currentInputConnection() ?: return false
        EditorInputConnectionBatch.runWithBatchEdit(ic) {
            deleteSurroundingText(triggerLength, 0)
            commitText(replacement, 1)
        }
        return true
    }

    fun cycleSelectedTextCase(locale: java.util.Locale): Boolean {
        val content = activeContent
        val selection = content.selection
        if (!selection.isSelectionMode) return false
        val selected = content.selectedText
        if (selected.isEmpty()) return false
        val ic = currentInputConnection() ?: return false
        val currentCase = SelectedTextCasePolicy.detectCase(selected, locale)
        val nextCase = SelectedTextCasePolicy.nextCase(currentCase)
        val replacement = SelectedTextCasePolicy.applyCase(selected, nextCase, locale)
        if (replacement == selected) return false
        EditorInputConnectionBatch.runWithBatchEdit(ic) {
            commitText(replacement, 1)
            setSelection(selection.start, selection.start + replacement.length)
        }
        return true
    }

    open fun finalizeComposingText(
        text: String,
        selectedTextSuggestion: Boolean = false,
    ): Boolean {
        val ic = currentInputConnection() ?: return false
        val content = activeContent
        val composing = content.composing
        // Begin the batch only after the guard: the previous order left an
        // unbalanced beginBatchEdit() on the early-return path, which keeps the
        // target editor in a nested batch and suppresses onUpdateSelection until
        // the input session restarts (stale cursor/selection mirror).
        if (activeInfo.isRawInputEditor || composing.isNotValid) {
            return false
        }
        val newContent = runBlocking {
            val newSelection = EditorRange.cursor(composing.end + (text.length - content.composingText.length))
            val newContent = content.generateCopy(
                selection = newSelection,
                textBeforeSelection = buildString {
                    append(content.textBeforeSelection.removeSuffix(content.composingText))
                    append(text)
                },
                selectedText = "",
            )
            expectedContentQueue.push(newContent)
            newContent
        }
        EditorInputConnectionBatch.finalizeComposingText(
            ic = ic,
            text = text,
            selectedTextSuggestion = selectedTextSuggestion,
        )
        _lastCommitPosition.handleCommit(newContent.selection)
        return true
    }

    /**
     * True when there's a word for [replaceCurrentWord] to replace: a non-empty
     * current word that ends at a collapsed cursor in a rich editor.
     */
    protected fun canReplaceCurrentWord(): Boolean {
        if (activeInfo.isRawInputEditor) return false
        val content = activeContent
        val word = content.currentWord
        val selection = content.selection
        return word.isValid && word.length > 0 && selection.isCursorMode && word.end == selection.start &&
            content.textBeforeSelection.endsWith(content.currentWordText)
    }

    /**
     * Replaces the current word (the word right before the cursor) with [text]
     * by selecting it and committing over it. This is how a candidate for that
     * word lands when the field has no composing region (composing turned off
     * for it), where committing at the cursor would append the candidate to
     * the word instead.
     *
     * @return False, leaving the editor untouched, when [canReplaceCurrentWord]
     * says there's no word or the editor refused the selection.
     */
    protected fun replaceCurrentWord(
        text: String,
        selectedTextSuggestion: Boolean = false,
    ): Boolean {
        if (!canReplaceCurrentWord()) return false
        val ic = currentInputConnection() ?: return false
        val content = activeContent
        val word = content.currentWord
        val newContent = runBlocking {
            content.generateCopy(
                selection = EditorRange.cursor(word.start + text.length),
                textBeforeSelection = buildString {
                    append(content.textBeforeSelection, 0, content.textBeforeSelection.length - word.length)
                    append(text)
                },
                selectedText = "",
            )
        }
        val replaced = EditorInputConnectionBatch.replaceRangeBySelection(
            ic = ic,
            replaceStart = word.start,
            replaceEnd = word.end,
            text = text,
            composing = newContent.composing,
            selectedTextSuggestion = selectedTextSuggestion,
        )
        if (!replaced) return false
        // Pushed only once the editor took the edit, so a refused selection
        // leaves no expected content claiming text that isn't there. The
        // editor's report can't arrive before this: it's posted to this thread.
        expectedContentQueue.push(newContent)
        _lastCommitPosition.handleCommit(newContent.selection)
        return true
    }

    protected suspend fun deleteAroundCursor(unit: OperationUnit, scope: OperationScope, n: Int = 0): Boolean {
        val ic = currentInputConnection()
        if (ic == null || n < 1) return false
        val content = activeContent
        // Cannot perform below check due to editors which lie about their correct selection
        //if (content.selection.isValid && content.selection.start == 0) return true
        val scopeText = when (scope) {
            OperationScope.BEFORE_CURSOR -> content.textBeforeSelection
            OperationScope.AFTER_CURSOR -> content.textAfterSelection
        }
        return (if (activeInfo.isRawInputEditor || scopeText.isEmpty()) {
            // If editor is rich and text before selection is empty we seem to have an invalid state here, so we fall
            // back to emulating a hardware backspace/forward delete.
            val keyEventCode = when (scope) {
                OperationScope.BEFORE_CURSOR -> KeyEvent.KEYCODE_DEL
                OperationScope.AFTER_CURSOR -> KeyEvent.KEYCODE_FORWARD_DEL
            }
            val metaState = when (unit) {
                OperationUnit.CHARACTERS -> meta()
                OperationUnit.WORDS -> meta(ctrl = true)
            }
            sendDownUpKeyEvent(keyEventCode, metaState, count = n)
        } else {
            val locale = subtypeManager.activeSubtype.primaryLocale
            when (scope) {
                OperationScope.BEFORE_CURSOR -> {
                    val length = when (unit) {
                        OperationUnit.CHARACTERS -> breakIterators.measureLastUChars(scopeText, n, locale)
                        OperationUnit.WORDS -> breakIterators.measureLastUWords(scopeText, n, locale)
                    }
                    val selection = content.selection
                    val newSelection = selection.translatedBy(-length)
                    val newContent = content.generateCopy(
                        selection = newSelection,
                        textBeforeSelection = scopeText.dropLast(length),
                    )
                    expectedContentQueue.push(newContent)
                    // ROADMAP §6 N10.3 — Use deleteSurroundingTextInCodePoints (API 24+) so backspace
                    // never splits a surrogate pair in Unicode 16/17 emoji or Indic conjuncts. The
                    // length we just computed is grapheme-aligned via ICU, but the underlying editor
                    // may still receive an unsafe count from deleteSurroundingText if length lands
                    // mid-surrogate after concurrent modifications.
                    val cpCount = scopeText.codePointCount(scopeText.length - length, scopeText.length)
                    ic.beginBatchEdit()
                    ic.finishComposingText()
                    ic.deleteSurroundingTextInCodePoints(cpCount, 0)
                    ic.setComposingRegion(newContent.composing)
                    ic.endBatchEdit()
                }
                OperationScope.AFTER_CURSOR -> {
                    val length = when (unit) {
                        OperationUnit.CHARACTERS -> breakIterators.measureUChars(scopeText, n, locale)
                        OperationUnit.WORDS -> breakIterators.measureUWords(scopeText, n, locale)
                    }
                    val selection = content.selection
                    val newContent = content.generateCopy(
                        selection = selection,
                        textAfterSelection = scopeText.drop(length),
                    )
                    expectedContentQueue.push(newContent)
                    // ROADMAP §6 N10.3 — same as above for forward delete.
                    val cpCount = scopeText.codePointCount(0, length)
                    ic.beginBatchEdit()
                    ic.finishComposingText()
                    ic.deleteSurroundingTextInCodePoints(0, cpCount)
                    ic.setComposingRegion(newContent.composing)
                    ic.endBatchEdit()
                }
            }
            true
        }).also {
            deleteMoveLastCommitPosition()
        }
    }

    fun refreshComposing() {
        val content = activeContent
        val ic = currentInputConnection()
        if (activeInfo.isRawInputEditor || ic == null) return
        runBlocking {
            val newContent = content.generateCopy()
            if (newContent.composing != content.composing) {
                expectedContentQueue.push(newContent)
                ic.setComposingRegion(newContent.composing)
            }
        }
    }

    /**
     * Gets [n] characters before the cursor's current position. The resulting string may be any
     * length ranging from 0 to n.
     *
     * @param n The number of characters to get before the cursor. Must be greater than 0 or this
     *  method will fail. This number indicates the number of Unicode chars, so the returned string
     *  length may be greater than n, due to Java char encoding.
     *
     * @return [n] or less characters before the cursor.
     */
    fun EditorContent.getTextBeforeCursor(n: Int): String {
        if (n < 1 || text.isEmpty()) return ""
        val text = textBeforeSelection
        val length = breakIterators.measureLastUCharsSync(text, n, subtypeManager.activeSubtype.primaryLocale)
        return text.takeLast(length)
    }

    /**
     * Gets [n] characters after the cursor's current position. The resulting string may be any
     * length ranging from 0 to n.
     *
     * @param n The number of characters to get after the cursor. Must be greater than 0 or this
     *  method will fail. This number indicates the number of Unicode chars, so the returned string
     *  length may be greater than n, due to Java char encoding.
     *
     * @return [n] or less characters after the cursor.
     */
    fun EditorContent.getTextAfterCursor(n: Int): String {
        if (n < 1 || text.isEmpty()) return ""
        val text = textAfterSelection
        val length = breakIterators.measureUCharsSync(text, n, subtypeManager.activeSubtype.primaryLocale)
        return text.take(length)
    }

    /**
     * Constructs a meta state integer flag which can be used for setting the `metaState` field when sending a KeyEvent
     * to the input connection. If this method is called without a meta modifier set to true, the default value `0` is
     * returned.
     *
     * @param ctrl Set to true to enable the CTRL meta modifier. Defaults to false.
     * @param alt Set to true to enable the ALT meta modifier. Defaults to false.
     * @param shift Set to true to enable the SHIFT meta modifier. Defaults to false.
     *
     * @return An integer containing all meta flags passed and formatted for use in a [KeyEvent].
     */
    fun meta(
        ctrl: Boolean = false,
        alt: Boolean = false,
        shift: Boolean = false,
    ): Int {
        var metaState = 0
        if (ctrl) {
            metaState = metaState or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        }
        if (alt) {
            metaState = metaState or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        }
        if (shift) {
            metaState = metaState or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        }
        return metaState
    }

    private fun InputConnection.sendDownKeyEvent(eventTime: Long, keyEventCode: Int, metaState: Int, repeat: Int = 0): Boolean {
        return this.sendKeyEvent(
            KeyEvent(
                eventTime,
                eventTime,
                KeyEvent.ACTION_DOWN,
                keyEventCode,
                repeat,
                metaState,
                KeyCharacterMap.VIRTUAL_KEYBOARD,
                0,
                KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE,
                InputDevice.SOURCE_KEYBOARD,
            )
        )
    }

    private fun InputConnection.sendUpKeyEvent(eventTime: Long, keyEventCode: Int, metaState: Int): Boolean {
        return this.sendKeyEvent(
            KeyEvent(
                eventTime,
                SystemClock.uptimeMillis(),
                KeyEvent.ACTION_UP,
                keyEventCode,
                0,
                metaState,
                KeyCharacterMap.VIRTUAL_KEYBOARD,
                0,
                KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE,
                InputDevice.SOURCE_KEYBOARD,
            )
        )
    }

    /**
     * Same as [InputMethodService.sendDownUpKeyEvents] but also allows to set meta state.
     *
     * @param keyEventCode The key code to send, use a key code defined in Android's [KeyEvent].
     * @param metaState Flags indicating which meta keys are currently pressed.
     * @param count How often the key is pressed while the meta keys passed are down. Must be greater than or equal to
     *  `1`, else this method will immediately return false.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun sendDownUpKeyEvent(keyEventCode: Int, metaState: Int = meta(), count: Int = 1): Boolean {
        if (count < 1) return false
        val ic = currentInputConnection() ?: return false
        ic.beginBatchEdit()
        val eventTime = SystemClock.uptimeMillis()
        if (metaState and KeyEvent.META_CTRL_ON != 0) {
            ic.sendDownKeyEvent(eventTime, KeyEvent.KEYCODE_CTRL_LEFT, 0)
        }
        if (metaState and KeyEvent.META_ALT_ON != 0) {
            ic.sendDownKeyEvent(eventTime, KeyEvent.KEYCODE_ALT_LEFT, 0)
        }
        if (metaState and KeyEvent.META_SHIFT_ON != 0) {
            ic.sendDownKeyEvent(eventTime, KeyEvent.KEYCODE_SHIFT_LEFT, 0)
        }
        for (n in 0 until count) {
            ic.sendDownKeyEvent(eventTime, keyEventCode, metaState, n)
        }
        ic.sendUpKeyEvent(eventTime, keyEventCode, metaState)
        if (metaState and KeyEvent.META_SHIFT_ON != 0) {
            ic.sendUpKeyEvent(eventTime, KeyEvent.KEYCODE_SHIFT_LEFT, 0)
        }
        if (metaState and KeyEvent.META_ALT_ON != 0) {
            ic.sendUpKeyEvent(eventTime, KeyEvent.KEYCODE_ALT_LEFT, 0)
        }
        if (metaState and KeyEvent.META_CTRL_ON != 0) {
            ic.sendUpKeyEvent(eventTime, KeyEvent.KEYCODE_CTRL_LEFT, 0)
        }
        ic.endBatchEdit()
        return true
    }

    /**
     * The keyboard's own edits the editor hasn't reported yet. [keysSoFar] counts the keys the app
     * has been passed, so a report for an edit knows which of them came after it.
     */
    private class ExpectedContentQueue(private val keysSoFar: () -> Long) {
        private val lock = Any()
        private val list = mutableListOf<KeyboardEdit>()

        fun popUntilOrNull(predicate: (EditorContent) -> Boolean): EditorContent? {
            return synchronized(lock) {
                while (list.isNotEmpty()) {
                    val item = list.removeAt(0)
                    if (predicate(item.content)) return@synchronized item.content
                }
                return@synchronized null
            }
        }

        fun push(item: EditorContent) {
            val edit = KeyboardEdit(item, keysSoFar(), SystemClock.uptimeMillis())
            synchronized(lock) {
                list.add(edit)
            }
        }

        /** The oldest edit that leaves [selection], null if none does. */
        fun editLeaving(selection: EditorRange): KeyboardEdit? {
            return synchronized(lock) {
                list.firstOrNull { it.content.selection == selection }
            }
        }

        fun peekNewestOrNull(): EditorContent? {
            return synchronized(lock) {
                list.lastOrNull()?.content
            }
        }

        fun clear() {
            synchronized(lock) {
                list.clear()
            }
        }
    }

    private class PendingContentGeneration(
        val inputConnection: InputConnection,
        val generate: suspend () -> EditorContent,
        val publish: (EditorContent) -> Unit,
    )

    /**
     * An edit the keyboard made itself, leaving [content], after the app had been passed
     * [keysBefore] keys in all, at [atMillis].
     */
    private class KeyboardEdit(val content: EditorContent, val keysBefore: Long, val atMillis: Long)

    /** What a reported cursor meant to [PassThroughKeyTracker]. */
    private enum class PassThroughReport {
        /** The app typed keys it was passed, and the cursor is where they left it. */
        Insert,
        /**
         * A key the app was passed that moves the cursor without typing (an arrow, Home, End, a
         * Backspace) landed. The keys typed after it are still tracked.
         */
        Moved,
        /** Nothing the app was passed explains the cursor. */
        None,
    }

    /**
     * Cursors the app will report after handling keys the keyboard passed through to it. The app,
     * not the keyboard, edits the text there, so those moves never show up in [ExpectedContentQueue].
     *
     * Fields handle some keys differently, and EditorInfo doesn't say which way this one does, so
     * the tracker follows every way still possible (a [Track] each) until a report only some of
     * them explain rules the others out. Each way keeps its own count of the keys the app's
     * reports have got past, since a key one way moves the cursor for another may leave in place.
     * Two things differ:
     * - The kind of field. A Compose field's Left arrow moves over a whole conjunct and its
     *   Backspace deletes a variation selector alone (the joined kind). A TextView's Left stops
     *   before the consonant after the virama and its Backspace deletes a selector with the char
     *   it's on. Chrome is a mix (Left as Compose, Backspace as a TextView), and so is a Compose
     *   field on Android 10 and older (its Left splits a conjunct like a TextView's, its Backspace
     *   deletes a selector alone). A burst there that does both reads one of them as a jump, which
     *   lets the next one learn the kind again.
     * - Which way a Left arrow goes. A TextView, a web field set to `dir="auto"` and a Compose field
     *   set to the content's direction run each paragraph the way its first strong letter does, so
     *   their Left moves forward through Hebrew or Arabic. Any other Compose field runs the app's
     *   layout direction, and any other web field the page's, whatever the text: left to right in
     *   an English app or page, right to left in a Hebrew one. A password field runs left to right.
     */
    private class PassThroughKeyTracker(private val systemRunsRightToLeft: () -> Boolean) {
        /**
         * Where the cursor is after the keys so far in one way of handling them, a guess for a
         * step back. [text] holds the last chars right before the cursor, as far as the keyboard
         * knows them: the text read from the field plus what the keys since typed, so a step back
         * over an emoji or a letter with its accents goes the way the field's own would. [after]
         * holds the first chars after it the same way, null when the keyboard doesn't know them,
         * so a Left arrow can move forward through them. [range] is a selection only where the
         * keys start. [reports] is false for a key that left the cursor where it was, which the
         * app doesn't report.
         */
        private class Track(
            val range: EditorRange,
            val text: String,
            val after: After?,
            val reports: Boolean = true,
        )

        /**
         * A key the app handles itself, with its [tracks] indexed by way (see [kindOf]), null for a
         * way already ruled out. [breaksRun] is set for a key that moves the cursor without typing,
         * so what's typed after it isn't in a run with what's before. [mayLandElsewhere] is set for
         * a key some way can only guess at (a paste, a Tab that may move the focus): a report no
         * way explains may be its, and a report a later key explains may be too, where the guess
         * was that it moved nothing.
         */
        private class PendingKey(
            val tracks: Array<Track?>,
            val breaksRun: Boolean,
            val atMillis: Long,
            val mayLandElsewhere: Boolean = false,
        ) {
            fun isExpired(now: Long): Boolean = now - atMillis > PassThroughInsertTimeoutMillis
        }

        /**
         * Where the next key starts in each way, and the text of a selection there, whose anchor
         * is its lower end when [anchorFirst].
         */
        private class Base(val tracks: Array<Track?>, val selected: String = "", val anchorFirst: Boolean = true)

        /**
         * The text after a cursor as far as the keyboard knows it, [reachesEnd] when that's all the
         * field's text. [rightToLeft] is set after a Left arrow moved forward through a paragraph
         * that runs right to left and stayed in it, so the next one moves forward too once the
         * start of the paragraph is no longer in the text kept before the cursor.
         */
        private class After(val text: String, val reachesEnd: Boolean, val rightToLeft: Boolean = false) {
            /** The same text after a line break went in or out, which starts or joins a paragraph. */
            fun splitOrJoined(): After = if (rightToLeft) After(text, reachesEnd) else this
        }

        private companion object {
            const val JoinedKind = 1
            /** A Left arrow goes the way the paragraph's first strong letter runs. */
            const val ContentDirection = 0
            const val LeftToRight = 1
            const val RightToLeft = 2
            const val NumDirections = 3
            const val NumWays = 2 * NumDirections
            const val AllWays = (1 shl NumWays) - 1
            const val RightToLeftWays = (1 shl RightToLeft) or (1 shl (NumDirections + RightToLeft))

            /** A way is the kind of field times [NumDirections] plus the direction of a Left arrow. */
            fun kindOf(way: Int): Int = way / NumDirections
            fun directionOf(way: Int): Int = way % NumDirections
        }

        private val lock = Any()
        /** How many keys the app has been passed in all, the last of them at the end of [pending]. */
        private var keysAdded = 0L
        private var reported = EditorRange.Unspecified
        /** True when the anchor of the [reported] selection is its lower end. */
        private var reportedAnchorFirst = true
        private val pending = ArrayDeque<PendingKey>()
        /**
         * For each way, how many keys at the front of [pending] its reports have passed. A way that
         * explains a report at a key has the app done with the keys up to it, though another way
         * may still expect a report for them.
         */
        private val passed = IntArray(NumWays)
        /**
         * For each way, where the keys its last report may cover end. A step back and the char
         * again leave the cursor in the same place a second time, and the text reads the same
         * either way, so the report may be for those keys too. Past [quietUntil] with no report,
         * the app is done with them, but a late report still matches them.
         */
        private val coveredTo = IntArray(NumWays)
        private val quietUntil = LongArray(NumWays)
        /**
         * For each way, how many keys at the front of [pending] it settled with no report for them,
         * the app having gone quiet. The next key starts from the last report then, but a late
         * report still matches these keys.
         */
        private val settledTo = IntArray(NumWays)
        /**
         * How long the app took to report the last key or keyboard edit it reported, so a slow app
         * gets longer to report the next one.
         */
        private var latencyMillis = 0L
        /**
         * The ways this field likely handles keys in. A Left arrow that runs right to left whatever
         * the text is unlikely where the system's layout runs left to right, since apps follow it.
         */
        private var likelyWays = AllWays
        /** Until when a burst too long to track holds the editor behind. */
        private var overflowUntil = 0L
        /**
         * Until when the keys can't be followed: a key that [PendingKey.mayLandElsewhere] did, and
         * keys came after it. Every report and key until then pushes it out, and the next key after
         * it starts from the last report.
         */
        private var blindUntil = 0L
        /**
         * For each way, until when a report it read as a later key's may still be the report of a
         * key before it that it guessed moved nothing (see [PendingKey.mayLandElsewhere]), so the
         * app may still be behind.
         */
        private val guessedUntil = LongArray(NumWays)
        private var graphemes: BreakIterator? = null
        /** The ways this field may handle keys, one bit per way (see [kindOf]). */
        private var possibleWays = AllWays

        /** [anchorFirst] is true when the anchor of [selection] is its lower end. */
        fun start(selection: EditorRange, anchorFirst: Boolean = true) {
            synchronized(lock) {
                reported = selection
                reportedAnchorFirst = anchorFirst
                clearPending()
                overflowUntil = 0L
                blindUntil = 0L
                latencyMillis = 0L
                possibleWays = AllWays
                likelyWays = if (systemRunsRightToLeft()) AllWays else AllWays and RightToLeftWays.inv()
            }
        }

        fun keysSoFar(): Long = synchronized(lock) { keysAdded }

        /** True when the anchor of the last selection reported is its lower end. */
        fun isAnchorFirst(): Boolean = synchronized(lock) { reportedAnchorFirst }

        private fun isPossible(way: Int): Boolean = possibleWays and (1 shl way) != 0

        /** The ways still possible that are likely the field's, or all of them if none is. */
        private fun likely(): Int = (possibleWays and likelyWays).takeIf { it != 0 } ?: possibleWays

        /** How many keys at the front of [pending] the app is done with in this way. */
        private fun doneTo(way: Int, now: Long): Int {
            val done = max(passed[way], settledTo[way])
            return if (now > quietUntil[way]) max(done, coveredTo[way]) else done
        }

        /**
         * How long a report the app may still send is waited for: twice the time its last one
         * took, at least [PassThroughQuietTimeoutMillis], and never longer than a key is kept.
         */
        private fun holdMillis(): Long {
            return min(PassThroughInsertTimeoutMillis, max(PassThroughQuietTimeoutMillis, 2 * latencyMillis))
        }

        /**
         * [keyboardPending] is the keyboard's newest edit the editor hasn't reported yet (a space it
         * just committed), which is where the app's key lands if nothing of the app's own is
         * pending. [published] is the content last read for a report.
         */
        fun expect(length: Int, codePoint: Int, keyboardPending: EditorContent?, published: EditorContent) {
            synchronized(lock) {
                val now = SystemClock.uptimeMillis()
                dropExpired(now)
                if (staysBlind(now)) return
                val base = baseOf(now, keyboardPending, published) ?: return
                val typed = typedText(length, codePoint)
                val tracks = arrayOfNulls<Track>(NumWays)
                for (way in 0 until NumWays) {
                    val from = base.tracks[way] ?: continue
                    // Ways that agree so far share a track, and typing keeps them agreeing.
                    val same = (0 until way).firstOrNull { base.tracks[it] === from }
                    tracks[way] = if (same != null) {
                        tracks[same]
                    } else {
                        Track(
                            EditorRange.cursor(from.range.start + length),
                            knownTail(from.text + typed),
                            if ('\n' in typed) from.after?.splitOrJoined() else from.after,
                        )
                    }
                }
                add(PendingKey(tracks, breaksRun = false, now))
            }
        }

        /**
         * The chars a key typed as [codePoint] leaves, or for an unknown one, [length] chars that
         * each stand alone, so a later step back still knows how long it is.
         */
        private fun typedText(length: Int, codePoint: Int): String = when {
            codePoint > 0 && Character.isValidCodePoint(codePoint) && Character.charCount(codePoint) == length ->
                String(Character.toChars(codePoint))
            length == 2 -> String(Character.toChars(UnknownSurrogatePairStandIn))
            else -> UnknownCharStandIn.toString().repeat(length)
        }

        /** The end of [text] a key can still need, never starting inside a surrogate pair. */
        private fun knownTail(text: String): String {
            var from = text.length - NumCharsKnownAroundPassThrough
            if (from <= 0) return text
            if (Character.isLowSurrogate(text[from])) from++
            return text.substring(from)
        }

        /**
         * The start of [text] a Left arrow can still need, never ending inside a surrogate pair.
         * [reachesEnd] is true when [text] runs to the end of the field's text.
         */
        private fun knownHead(text: String, reachesEnd: Boolean): After {
            var to = NumCharsKnownAroundPassThrough
            if (to >= text.length) return After(text, reachesEnd)
            if (Character.isHighSurrogate(text[to - 1])) to--
            return After(text.substring(0, to), reachesEnd = false)
        }

        /**
         * [wholeCluster] steps back over a letter and its accents at once, the way a Left arrow
         * moves, where a Backspace deletes the accents one at a time.
         */
        fun expectStepBack(wholeCluster: Boolean, keyboardPending: EditorContent?, published: EditorContent) {
            expectMove(keyboardPending, published) { way, from, base ->
                // Where there's nothing to step back over in this way (the start of the text, the
                // end of it for a Left that moves forward, or a TextView's Backspace that took an
                // ideograph and its selector at once), the cursor stays put and nothing reports it.
                stepBack(from, base, wholeCluster, kindOf(way) == JoinedKind, directionOf(way)) ?: stayPut(from)
            }
        }

        /**
         * A Right arrow moves the other way a Left does: forward through the text, or back through
         * a paragraph where the Left moves forward.
         */
        fun expectStepForward(keyboardPending: EditorContent?, published: EditorContent) {
            expectMove(keyboardPending, published) { way, from, base ->
                val joinedKind = kindOf(way) == JoinedKind
                val selected = selectedAt(from, base)
                val landing = if (goesForward(from, selected, base.anchorFirst, joinedKind, directionOf(way))) {
                    stepBackward(from, selected, wholeCluster = true, joinedKind)
                } else {
                    stepForward(from.range, from.text, selected, from.after, rightToLeft = false)
                }
                // At either end of the text the cursor stays put.
                landing ?: stayPut(from)
            }
        }

        /**
         * Home and End in a single-line field go to the start and the end of its text. A TextView
         * goes to the left and right edge on screen, which swaps them in a line that runs right to
         * left. Compose goes by the text, and how a web field goes there isn't known, so a key in
         * such a line [PendingKey.mayLandElsewhere], and so does an End past the text the keyboard
         * knows, where the cursor stays put in the way it can't place.
         */
        fun expectLineEdge(toEnd: Boolean, keyboardPending: EditorContent?, published: EditorContent) {
            synchronized(lock) {
                val now = SystemClock.uptimeMillis()
                dropExpired(now)
                if (staysBlind(now)) return
                val base = baseOf(now, keyboardPending, published) ?: return
                val tracks = arrayOfNulls<Track>(NumWays)
                var moves = false
                var guessed = false
                for (way in 0 until NumWays) {
                    val from = base.tracks[way] ?: continue
                    val selected = selectedAt(from, base)
                    val swapped = when {
                        kindOf(way) == JoinedKind -> false
                        directionOf(way) == ContentDirection && from.after == null -> null
                        else -> goesForward(from, selected, base.anchorFirst, joinedKind = false, directionOf(way))
                    }
                    if (swapped != false) guessed = true
                    val landing = if (swapped == null) null else lineEdge(from, selected, toEnd = toEnd != swapped)
                    if (landing == null) guessed = true
                    if (landing?.reports == true) moves = true
                    tracks[way] = landing ?: stayPut(from)
                }
                if (!moves && !guessed) return
                add(PendingKey(tracks, breaksRun = true, now, mayLandElsewhere = guessed))
            }
        }

        /**
         * Where Home lands from a [from] track, or End when [toEnd], or null where it can't be told
         * (the end of text the keyboard doesn't know all of). [selected] is the text of its selection.
         */
        private fun lineEdge(from: Track, selected: String, toEnd: Boolean): Track? {
            val range = from.range
            val lower = min(range.start, range.end)
            val upper = max(range.start, range.end)
            val after = from.after
            if (!toEnd) {
                if (!range.isSelectionMode && lower == 0) return stayPut(from)
                // The text after the start is known where all of it before the cursor is.
                val known = from.text.length == lower && selected.length == upper - lower
                val stillAfter = if (known) after?.let { knownHead(from.text + selected + it.text, it.reachesEnd) } else null
                return Track(EditorRange.cursor(0), "", stillAfter)
            }
            if (after == null || !after.reachesEnd) return null
            if (!range.isSelectionMode && after.text.isEmpty()) return stayPut(from)
            return Track(
                EditorRange.cursor(upper + after.text.length),
                knownTail(from.text + selected + after.text),
                After("", reachesEnd = true),
            )
        }

        /**
         * A Tab in a multi-line Compose field types a tab, and in a TextView it does nothing. In a
         * single-line field or a web page it moves the focus, which starts the next field over, or
         * does nothing, so it [PendingKey.mayLandElsewhere] in every way.
         */
        fun expectTab(multiLine: Boolean, keyboardPending: EditorContent?, published: EditorContent) {
            synchronized(lock) {
                val now = SystemClock.uptimeMillis()
                dropExpired(now)
                if (staysBlind(now)) return
                val base = baseOf(now, keyboardPending, published) ?: return
                val tracks = arrayOfNulls<Track>(NumWays)
                for (way in 0 until NumWays) {
                    val from = base.tracks[way] ?: continue
                    tracks[way] = if (multiLine && kindOf(way) == JoinedKind) {
                        Track(EditorRange.cursor(from.range.start + 1), knownTail(from.text + '\t'), from.after)
                    } else {
                        stayPut(from)
                    }
                }
                add(PendingKey(tracks, breaksRun = false, now, mayLandElsewhere = true))
            }
        }

        /**
         * A key that may move the cursor or change the text in a way only the app's report tells
         * (a paste, an Up arrow, an undo). Every way guesses it changes nothing, so the keys after
         * it stay tracked when it doesn't, but the text after the cursor is no longer known.
         */
        fun expectUnpredictable(keyboardPending: EditorContent?, published: EditorContent) {
            synchronized(lock) {
                val now = SystemClock.uptimeMillis()
                dropExpired(now)
                if (staysBlind(now)) return
                val base = baseOf(now, keyboardPending, published) ?: return
                val tracks = arrayOfNulls<Track>(NumWays)
                for (way in 0 until NumWays) {
                    val from = base.tracks[way] ?: continue
                    tracks[way] = Track(from.range, from.text, after = null, reports = false)
                }
                add(PendingKey(tracks, breaksRun = false, now, mayLandElsewhere = true))
            }
        }

        /**
         * Adds a key that moves the cursor without typing, landing where [landing] puts it from
         * each way's track, unless it stays put in every way, where the app reports nothing.
         */
        private fun expectMove(
            keyboardPending: EditorContent?,
            published: EditorContent,
            landing: (way: Int, from: Track, base: Base) -> Track,
        ) {
            synchronized(lock) {
                val now = SystemClock.uptimeMillis()
                dropExpired(now)
                if (staysBlind(now)) return
                val base = baseOf(now, keyboardPending, published) ?: return
                val tracks = arrayOfNulls<Track>(NumWays)
                var moves = false
                for (way in 0 until NumWays) {
                    val from = base.tracks[way] ?: continue
                    val track = landing(way, from, base)
                    if (track.reports) moves = true
                    tracks[way] = track
                }
                if (!moves) return
                add(PendingKey(tracks, breaksRun = true, now))
            }
        }

        /**
         * The cursor where a key leaves it in place, which the app doesn't report. A selection the
         * key leaves selected keeps its range, but the text it holds isn't carried past this key.
         */
        private fun stayPut(from: Track): Track {
            return Track(from.range, from.text, if (from.range.isSelectionMode) null else from.after, reports = false)
        }

        /** The text of the selection a [from] track starts on, as far as [base] knows it. */
        private fun selectedAt(from: Track, base: Base): String {
            // Only the track of a selection the keyboard knows starts on one.
            return if (from.range.isSelectionMode) base.selected else ""
        }

        /**
         * Where a step back from a [from] track lands, or null at the start of the text, in a field
         * of the joined kind or not whose Left goes in [direction]. A selection's text is in [base].
         * A Left arrow in a paragraph that runs right to left moves forward instead (see
         * [stepForward]).
         */
        private fun stepBack(from: Track, base: Base, wholeCluster: Boolean, joinedKind: Boolean, direction: Int): Track? {
            val selected = selectedAt(from, base)
            if (wholeCluster && goesForward(from, selected, base.anchorFirst, joinedKind, direction)) {
                return stepForward(from.range, from.text, selected, from.after, rightToLeft = true)
            }
            return stepBackward(from, selected, wholeCluster, joinedKind)
        }

        /**
         * Where a step back through the text from a [from] track lands, or null at its start.
         * [selected] is the text of its selection.
         */
        private fun stepBackward(from: Track, selected: String, wholeCluster: Boolean, joinedKind: Boolean): Track? {
            val range = from.range
            val text = from.text
            val after = from.after
            if (range.isSelectionMode) {
                // Backspace deletes a selection and Left collapses it, both to its start.
                val start = EditorRange.cursor(min(range.start, range.end))
                if (!wholeCluster) return Track(start, text, after)
                return Track(start, text, after?.let { knownHead(selected + it.text, it.reachesEnd) })
            }
            if (range.start <= 0) return null
            val steps = backStepLengths(text)
            var taken = if (steps.isEmpty()) 0 else 1
            if (wholeCluster) {
                while (taken < steps.size && carriesAlong(steps[steps.size - taken], joinedKind)) taken++
            } else if (!joinedKind && taken < steps.size && (abs(steps.last()) and VariationSelectorStep) != 0) {
                // A TextView's Backspace takes the char a variation selector is on along with it.
                taken++
            }
            // A char the keyboard doesn't know is most likely a single one.
            val step = if (taken == 0) 1 else steps.takeLast(taken).sumOf { stepLength(it) }
            // A Left arrow puts the chars it moves over after the cursor, as far as they're known.
            val moved = text.takeLast(step)
            val stillAfter = when {
                // A Backspace over a line break joins two paragraphs.
                !wholeCluster -> after?.splitOrJoined()
                after == null || moved.length < step -> null
                else -> knownHead(moved + after.text, after.reachesEnd)
            }
            return Track(EditorRange.cursor(max(0, range.start - step)), text.dropLast(step), stillAfter)
        }

        /**
         * Where a step forward through the text lands: at the upper end of a selection, or past
         * the next char, or null at the end of the text, where it doesn't move. A Right arrow goes
         * there in a paragraph that runs left to right, and a Left arrow in one that runs right to
         * left ([rightToLeft]): a TextView moves the cursor left on screen, and Compose and Chrome
         * move it the paragraph's way, which there is forward through the text.
         */
        private fun stepForward(range: EditorRange, text: String, selected: String, after: After?, rightToLeft: Boolean): Track? {
            if (range.isSelectionMode) return Track(EditorRange.cursor(max(range.start, range.end)), knownTail(text + selected), after)
            if (after == null || after.text.isEmpty()) {
                if (after != null && after.reachesEnd) return null
                // Past what the keyboard knows, the text most likely goes on with a single char.
                val unknown = after?.let { After("", reachesEnd = false, rightToLeft = rightToLeft) }
                return Track(EditorRange.cursor(range.start + 1), knownTail(text + UnknownCharStandIn), unknown)
            }
            val clusters = graphemes ?: BreakIterator.getCharacterInstance().also { graphemes = it }
            clusters.setText(after.text)
            val step = clusters.following(0)
            val moved = after.text.substring(0, step)
            // Past a line break the next Left arrow is in another paragraph, which it checks again.
            val stillAfter = After(after.text.substring(step), after.reachesEnd, rightToLeft = rightToLeft && '\n' !in moved)
            return Track(EditorRange.cursor(range.start + step), knownTail(text + moved), stillAfter)
        }

        /**
         * True when a Left arrow from a [from] track moves forward through the text. [selected] is
         * the text of its selection, whose anchor is its lower end when [anchorFirst].
         */
        private fun goesForward(
            from: Track,
            selected: String,
            anchorFirst: Boolean,
            joinedKind: Boolean,
            direction: Int,
        ): Boolean {
            return when (direction) {
                LeftToRight -> false
                RightToLeft -> true
                else -> from.after != null &&
                    runsRightToLeft(from.text, from.range.start, selected, from.after, anchorFirst, joinedKind)
            }
        }

        /**
         * True when the paragraph around a cursor at [start] runs right to left in a field that
         * takes its direction from the text: its first strong letter is a right-to-left one (Hebrew,
         * Arabic). Only a line feed starts a paragraph, as in a text layout. Where [before] doesn't
         * reach back to the start of the paragraph, it goes by an earlier Left ([After.rightToLeft])
         * or else by the first strong letter the keyboard does know. A selection over a line feed
         * collapses the way the paragraph of its anchor runs in a TextView and the way the one of
         * its other end runs in Compose.
         */
        private fun runsRightToLeft(
            before: String,
            start: Int,
            selected: String,
            after: After,
            anchorFirst: Boolean,
            joinedKind: Boolean,
        ): Boolean {
            val lineBreak = selected.lastIndexOf('\n')
            if (lineBreak >= 0 && anchorFirst == joinedKind) {
                // The paragraph the selection's upper end is in.
                return firstStrongRunsRightToLeft(selected.substring(lineBreak + 1) + after.text)
            }
            val from = before.lastIndexOf('\n') + 1
            if (from == 0 && before.length < start && after.rightToLeft) return true
            return firstStrongRunsRightToLeft(before.substring(from) + selected + after.text)
        }

        /**
         * True when the first strong letter of the paragraph [text] starts, up to its first line
         * feed, runs right to left. Text inside a directional isolate doesn't count, as in the
         * Unicode bidi algorithm. With no strong letter at all, the paragraph runs left to right.
         */
        private fun firstStrongRunsRightToLeft(text: String): Boolean {
            var isolates = 0
            var at = 0
            while (at < text.length) {
                val codePoint = text.codePointAt(at)
                at += Character.charCount(codePoint)
                when {
                    codePoint == '\n'.code -> return false
                    // The directional isolates, which older Android doesn't name.
                    codePoint in 0x2066..0x2068 -> isolates++
                    codePoint == 0x2069 -> if (isolates > 0) isolates--
                    isolates > 0 -> {}
                    else -> when (Character.getDirectionality(codePoint)) {
                        Character.DIRECTIONALITY_RIGHT_TO_LEFT,
                        Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> return true
                        Character.DIRECTIONALITY_LEFT_TO_RIGHT -> return false
                    }
                }
            }
            return false
        }

        /** True when a Left arrow over [step] also moves over the char before it. */
        private fun carriesAlong(step: Int, joinedKind: Boolean): Boolean {
            return step < 0 || (joinedKind && (step and ConjunctConsonantStep) != 0)
        }

        private fun stepLength(step: Int): Int = abs(step) and (ConjunctConsonantStep - 1)

        /**
         * Where the next key the app handles starts in each way: the last key still pending, so a
         * fast burst predicts every cursor, or else the keyboard's newest edit, or else the last
         * report. A way the app is done with (see [doneTo]) left the cursor where the last report
         * did, and the keyboard may have typed since. A typed char replaces a selection, so a range
         * is kept lower end first.
         */
        private fun baseOf(now: Long, keyboardPending: EditorContent?, published: EditorContent): Base? {
            val tracks = arrayOfNulls<Track>(NumWays)
            var settled = 0
            for (way in 0 until NumWays) {
                if (!isPossible(way)) continue
                if (pending.lastIndex >= doneTo(way, now)) {
                    tracks[way] = pending.last().tracks[way]
                } else {
                    settled = settled or (1 shl way)
                }
            }
            if (settled == 0) return Base(tracks)
            val keyboard = keyboardPending?.takeIf { it.selection.isValid }
            val range = keyboard?.selection ?: reported.takeIf { it.isValid } ?: return null
            val content = keyboard ?: published.takeIf { it.selection == range }
            val lower = min(range.start, range.end)
            val upper = max(range.start, range.end)
            // The text after a selection only lines up when the whole selection is known.
            val whole = content?.takeIf { it.selectedText.length == upper - lower }
            val track = Track(
                EditorRange(lower, upper),
                knownTail(content?.textBeforeSelection.orEmpty()),
                // A read shorter than asked reaches the end of the text, and so does a longer one,
                // from an app that gives all of it whatever is asked.
                whole?.textAfterSelection?.let { knownHead(it, reachesEnd = it.length != NumCharsAfterCursor) },
            )
            for (way in 0 until NumWays) {
                if (settled and (1 shl way) == 0) continue
                tracks[way] = track
                // The keys this way is done with need no report any more, but a late one still
                // matches them, and a step back among them still breaks the run of typing then.
                settledTo[way] = pending.size
            }
            return Base(
                tracks,
                whole?.selectedText.orEmpty(),
                anchorFirst = if (keyboard != null) range.start <= range.end else reportedAnchorFirst,
            )
        }

        /**
         * How far a Backspace steps back over each of the last chars of [text], nearest last. An
         * emoji sequence (a flag, a skin tone, a keycap, a ZWJ family, a heart or the information
         * letter with its variation selector) and a CR LF go in one step, the way a text field
         * deletes them. The accents and vowel signs after a letter (or a digit, a space) go one
         * code point at a time, as its Backspace takes them, and are negative, since a Left arrow
         * moves over them with the letter. A consonant after a virama is positive with
         * [ConjunctConsonantStep] set, whichever cluster ICU puts it in: a TextView's Left arrow
         * stops before it, though Compose and Chrome move over the whole conjunct. A variation
         * selector on a char with no accent has [VariationSelectorStep] set.
         */
        private fun backStepLengths(text: String): List<Int> {
            var from = max(0, text.length - NumCharsForBackStepsBeforePassThrough)
            if (from > 0 && Character.isLowSurrogate(text[from])) from++
            val window = text.substring(from)
            val clusters = graphemes ?: BreakIterator.getCharacterInstance().also { graphemes = it }
            clusters.setText(window)
            val lengths = ArrayDeque<Int>()
            var end = clusters.last()
            while (end > 0 && lengths.size < NumKnownBackStepsBeforePassThrough) {
                val start = clusters.previous()
                if (Character.isLetter(window.codePointAt(start))) {
                    var at = end
                    // The length of a selector that goes in one step with the char before it.
                    var selector = 0
                    while (at > start && lengths.size < NumKnownBackStepsBeforePassThrough) {
                        val codePoint = window.codePointBefore(at)
                        val length = Character.charCount(codePoint)
                        at -= length
                        if (codePoint == EmojiPresentationSelector && at > start &&
                            window.codePointBefore(at) == InformationSource
                        ) {
                            selector = length
                            continue
                        }
                        val afterVirama = at > 0 && Character.isLetter(codePoint) &&
                            isConjunctConsonant(window.codePointBefore(at), codePoint, inCluster = at > start)
                        val step = length + selector
                        selector = 0
                        lengths.addFirst(
                            when {
                                afterVirama -> step or ConjunctConsonantStep
                                at == start -> step
                                isPlainCharSelector(window, at, codePoint) -> -(step or VariationSelectorStep)
                                else -> -step
                            }
                        )
                    }
                    // The rest of a cluster too long to take char by char goes in one step, so a
                    // Left arrow still moves over all of it.
                    if (at > start) lengths.addFirst(at - start)
                } else {
                    // A Backspace deletes an accent after a digit, a space or a symbol on its own
                    // too, and a variation selector after an accent, and a Left arrow moves over
                    // them with the char they're on.
                    var at = end
                    while (lengths.size < NumKnownBackStepsBeforePassThrough) {
                        val codePoint = window.codePointBefore(at)
                        val before = at - Character.charCount(codePoint)
                        val alone = isLoneMark(codePoint) ||
                            (isVariationSelector(codePoint) && !isPlainCharSelector(window, before, codePoint))
                        if (before <= start || !alone) break
                        lengths.addFirst(before - at)
                        at = before
                    }
                    lengths.addFirst(at - start)
                }
                end = start
            }
            return lengths
        }

        /**
         * True when [codePoint] after [before] is the second consonant of a conjunct. Android 10
         * and older (ICU 63) end a cluster at the virama, though Chrome's own ICU keeps the conjunct
         * together, so a consonant starting its own cluster counts too when it's one of the
         * virama's script's consonants (Unicode's InCB=Consonant). Each of the [ConjunctViramas]
         * sits at 0x4D in its block, and most of their consonants sit at the same offsets.
         */
        private fun isConjunctConsonant(before: Int, codePoint: Int, inCluster: Boolean): Boolean {
            if (before !in ConjunctViramas) return false
            if (inCluster) return true
            return when (codePoint - (before - 0x4D)) {
                in 0x15..0x3A -> true
                // Telugu's NAKAARA POLLU and Malayalam's ARCHAIC II are letters there but not consonants.
                in 0x58..0x5F -> codePoint != 0x0C5D && codePoint != 0x0D5F
                // Devanagari's HIGH SPACING DOT is a letter there but not a consonant.
                in 0x70..0x71 -> codePoint != 0x0971
                // Consonants Unicode added later: Devanagari's for Sindhi and other languages, and Gujarati ZA.
                in 0x78..0x7F -> before == 0x094D || codePoint == 0x0AF9
                else -> false
            }
        }

        /**
         * True for an accent or vowel sign a Backspace deletes on its own wherever it sits, so not
         * a variation selector or the keycap mark, which go with the char before them.
         */
        private fun isLoneMark(codePoint: Int): Boolean {
            if (codePoint == KeycapMark || isVariationSelector(codePoint)) return false
            val type = Character.getType(codePoint)
            return type == Character.NON_SPACING_MARK.toInt() ||
                type == Character.ENCLOSING_MARK.toInt() ||
                type == Character.COMBINING_SPACING_MARK.toInt()
        }

        /** Android's variation selectors, Mongolian's free variation selectors among them. */
        private fun isVariationSelector(codePoint: Int): Boolean {
            return codePoint in 0xFE00..0xFE0F || codePoint in 0xE0100..0xE01EF ||
                codePoint in 0x180B..0x180D || codePoint == 0x180F
        }

        /**
         * True when [codePoint] at [at] in [text] is a variation selector right after a char with
         * combining class 0 that isn't one itself, the pair a TextView's Backspace (and Chrome's)
         * deletes at once. After an accent it deletes the selector alone.
         */
        private fun isPlainCharSelector(text: String, at: Int, codePoint: Int): Boolean {
            if (at <= 0 || !isVariationSelector(codePoint)) return false
            val before = text.codePointBefore(at)
            return !isVariationSelector(before) && UCharacter.getCombiningClass(before) == 0
        }

        private fun add(key: PendingKey) {
            pending.addLast(key)
            keysAdded++
            // The dropped key's report won't match, so it reads as a jump and drops the rest. The
            // app is still that far behind, so hold until the burst has been quiet for a while or
            // a report matches again.
            if (pending.size > MaxPendingPassThroughKeys || key.atMillis < overflowUntil) {
                overflowUntil = key.atMillis + PassThroughInsertTimeoutMillis
            }
            removeFirst(pending.size - MaxPendingPassThroughKeys)
        }

        /** Drops [count] keys from the front of [pending]. */
        private fun removeFirst(count: Int) {
            if (count <= 0) return
            repeat(count) { pending.removeFirst() }
            for (way in 0 until NumWays) {
                passed[way] = max(0, passed[way] - count)
                coveredTo[way] = max(0, coveredTo[way] - count)
                settledTo[way] = max(0, settledTo[way] - count)
            }
        }

        /** Drops the keys the reports of every way still possible have passed. */
        private fun trim() {
            var done = pending.size
            for (way in 0 until NumWays) {
                if (isPossible(way)) done = min(done, passed[way])
            }
            removeFirst(done)
        }

        private fun clearPending() {
            pending.clear()
            passed.fill(0)
            coveredTo.fill(0)
            quietUntil.fill(0L)
            settledTo.fill(0)
            guessedUntil.fill(0L)
        }

        /**
         * Matches a cursor the editor reported against the keys the app hasn't reported yet.
         * [anchorFirst] is true when the anchor of [selection] is its lower end. A [keyboardEdit]
         * report is the keyboard's own edit (a space it committed), which can land where a way
         * that's behind expects a key the app was passed, so it passes none of them (see
         * [coverKeysAfter]).
         */
        fun handleUpdateSelection(
            oldSelection: EditorRange,
            selection: EditorRange,
            anchorFirst: Boolean,
            keyboardEdit: KeyboardEdit?,
        ): PassThroughReport {
            return synchronized(lock) {
                reported = selection
                reportedAnchorFirst = anchorFirst
                // A key the app took as a shortcut never moves the cursor, so its entry would let a
                // tap that lands there much later pass as typing.
                val now = SystemClock.uptimeMillis()
                dropExpired(now)
                // Every key the app is passed moves the cursor, so a report that leaves it in place
                // (the app answering a new composing region) is none of them. After a step back, a
                // later key can expect that same cursor.
                if (selection == oldSelection) return@synchronized PassThroughReport.None
                if (keyboardEdit != null) {
                    if (selection.isCursorMode) coverKeysAfter(keyboardEdit, selection, now)
                    return@synchronized PassThroughReport.None
                }
                if (staysBlind(now)) return@synchronized PassThroughReport.None
                // A key that selects (Ctrl+A) is one the keyboard can only guess at.
                if (!selection.isCursorMode) return@synchronized unexplained(now)
                // Each way's first key past the ones its reports passed that leaves the cursor here.
                // The app may report several keys at once, so it can be a later one. A way past a
                // key's quiet time still matches it, for an app slow to report, and so does a key it
                // settled with no report (see [baseOf]). A key typed after the way settled can leave
                // the cursor here too (a Backspace after a Backspace), and if the app was done, the
                // report is that one's: it's covered then (see [coveredTo]).
                val matchedAt = IntArray(NumWays) { -1 }
                var explaining = 0
                for (way in 0 until NumWays) {
                    if (!isPossible(way)) continue
                    val at = firstLanding(way, selection.start, passed[way], pending.size)
                    if (at < 0) continue
                    matchedAt[way] = at
                    explaining = explaining or (1 shl way)
                }
                if (explaining == 0) return@synchronized unexplained(now)
                // Only some ways explain the report, so the key it matched is one they leave in
                // different places (a Left over a conjunct or through Hebrew, a Backspace over a
                // variation selector), and this field handles keys in one of those. When others do
                // at a later key (a coalesced report), the next report may tell.
                if (explaining != possibleWays) {
                    possibleWays = explaining
                    pending.forEach { key ->
                        for (way in 0 until NumWays) {
                            if (explaining and (1 shl way) == 0) key.tracks[way] = null
                        }
                    }
                }
                // The app is keeping up, so whatever overflowed is behind it.
                overflowUntil = 0L
                // How long ago the earliest key matched was passed is how long the app may take. Ways
                // can read the report as different keys' (a Left that moved the cursor in one and left
                // it in place in another), and the earliest reading is the longest. It can count a
                // pause before a later key, but a hold too long only passes a few more keys through,
                // while one too short lets the keyboard edit text the app hasn't caught up to.
                var earliest = pending.size
                for (way in 0 until NumWays) {
                    if (matchedAt[way] in 0 until earliest) earliest = matchedAt[way]
                }
                latencyMillis = now - pending[earliest].atMillis
                val hold = holdMillis()
                var moved = true
                for (way in 0 until NumWays) {
                    val at = matchedAt[way]
                    if (at < 0) continue
                    // A Left this way leaves in place (at the end of Hebrew text) moved nothing, so
                    // the text around the cursor still runs on. Where the ways disagree, the run
                    // only breaks when every way that explains the report moved the cursor. A move
                    // among keys the way settled counts too.
                    val stepped = (passed[way]..at).any { i ->
                        pending[i].breaksRun && pending[i].tracks[way]?.reports == true
                    }
                    if (!stepped) moved = false
                    // A key before it this way guessed moved nothing (a paste) may have moved the
                    // cursor here instead, and then the app still owes this key's report.
                    val guessed = (passed[way] until at).any { i ->
                        pending[i].mayLandElsewhere && pending[i].tracks[way]?.reports == false
                    }
                    if (guessed) guessedUntil[way] = now + hold
                    passed[way] = at + 1
                    // The app reports late, so it may still report keys this way settled past.
                    settledTo[way] = 0
                    // If no report follows soon, the app is done with the keys this one may cover
                    // too. A slow app reports later, so it gets longer.
                    var lastSameCursor = at
                    for (i in at + 1 until pending.size) {
                        if (pending[i].tracks[way]?.range?.start == selection.start) lastSameCursor = i
                    }
                    coveredTo[way] = lastSameCursor + 1
                    quietUntil[way] = now + hold
                }
                trim()
                if (moved) PassThroughReport.Moved else PassThroughReport.Insert
            }
        }

        /**
         * A report no key explains, which may be one a key the keyboard could only guess at (see
         * [PendingKey.mayLandElsewhere]) put somewhere else. The keys after it went in from there,
         * so with keys after it the editor holds until the app goes quiet, and the next key starts
         * from the last report either way. A report anything else caused is a jump.
         */
        private fun unexplained(now: Long): PassThroughReport {
            var done = pending.size
            for (way in 0 until NumWays) {
                if (isPossible(way)) done = min(done, doneTo(way, now))
            }
            val guessed = (done until pending.size).firstOrNull { pending[it].mayLandElsewhere }
                ?: return PassThroughReport.None
            if (guessed < pending.lastIndex) blindUntil = now + holdMillis()
            clearPending()
            return PassThroughReport.None
        }

        /**
         * True while the keys can't be followed (see [blindUntil]). A key or a report then means
         * the app isn't done yet, so the hold goes on for a while after it.
         */
        private fun staysBlind(now: Long): Boolean {
            if (now >= blindUntil) return false
            blindUntil = max(blindUntil, now + holdMillis())
            return true
        }

        /** The first key from [from] until [to] that this way gets a report for at [cursor], or -1. */
        private fun firstLanding(way: Int, cursor: Int, from: Int, to: Int): Int {
            for (i in from until to) {
                val track = pending[i].tracks[way] ?: continue
                if (track.reports && track.range.start == cursor) return i
            }
            return -1
        }

        /**
         * A report for the keyboard's own [edit] can come with the keys the app was passed after
         * it, from an app that reports a burst once. Each way that leaves the cursor at [selection]
         * after one of those keys may be done with them, so it is if no report follows soon. The
         * time the report took is how long the app takes.
         */
        private fun coverKeysAfter(edit: KeyboardEdit, selection: EditorRange, now: Long) {
            latencyMillis = now - edit.atMillis
            val keysAfter = keysAdded - edit.keysBefore
            val from = if (keysAfter >= pending.size) 0 else pending.size - keysAfter.toInt()
            for (way in 0 until NumWays) {
                if (!isPossible(way)) continue
                var last = -1
                for (i in from until pending.size) {
                    if (pending[i].tracks[way]?.range?.start == selection.start) last = i
                }
                if (last < 0) continue
                // What an earlier report covered and the app went quiet after stays done.
                if (now > quietUntil[way]) settledTo[way] = max(settledTo[way], coveredTo[way])
                coveredTo[way] = max(coveredTo[way], last + 1)
                quietUntil[way] = max(quietUntil[way], now + holdMillis())
            }
        }

        /**
         * True when a key the app hasn't reported yet is still expected. Only the ways likely the
         * field's count (see [likely]), so one that isn't decides nothing, whether it still expects
         * a report or caught up by leaving the cursor in place. Where every likely way expects one,
         * the app is behind. Where some have caught up, the field may handle keys in one of those,
         * and then the app is done, though one that still expects a report holds until the quiet
         * time of a way that caught up is up, since that way may have read a report for several
         * keys as one for a single key.
         */
        fun hasPending(): Boolean {
            return synchronized(lock) {
                val now = SystemClock.uptimeMillis()
                dropExpired(now)
                when {
                    now < overflowUntil || now < blindUntil -> true
                    else -> {
                        val likely = likely()
                        var awaiting = 0
                        var caughtUpInQuiet = false
                        for (way in 0 until NumWays) {
                            if (likely and (1 shl way) == 0) continue
                            if (awaits(way, now)) {
                                awaiting = awaiting or (1 shl way)
                            } else if (now <= quietUntil[way]) {
                                caughtUpInQuiet = true
                            }
                        }
                        awaiting == likely || (awaiting != 0 && caughtUpInQuiet)
                    }
                }
            }
        }

        /**
         * True when this way still expects a report for a key. A key it leaves in place where a
         * likely way moves the cursor (a Left at the end of Hebrew text) gets no report if this is
         * the field's way, so it holds only until the other way's report would have come, and so
         * does a key it can only guess at. So does a report this way read as a later key's that
         * may have been a guessed key's (see [guessedUntil]).
         */
        private fun awaits(way: Int, now: Long): Boolean {
            if (now <= guessedUntil[way]) return true
            val likely = likely()
            for (i in doneTo(way, now) until pending.size) {
                val key = pending[i]
                val track = key.tracks[way] ?: continue
                if (track.reports) return true
                if (now <= key.atMillis + holdMillis() && (key.mayLandElsewhere ||
                        (0 until NumWays).any { other -> likely and (1 shl other) != 0 && key.tracks[other]?.reports == true })
                ) {
                    return true
                }
            }
            return false
        }

        private fun dropExpired(now: Long) {
            var expired = 0
            while (expired < pending.size && pending[expired].isExpired(now)) expired++
            removeFirst(expired)
        }

        fun dropPending() {
            synchronized(lock) {
                clearPending()
                // The field's way may have been read off a report another key caused (one that
                // expired first), so a report nothing explains lets the next key learn it again.
                possibleWays = AllWays
            }
        }
    }

    fun updateLastCommitPosition() = _lastCommitPosition.handleCommit(activeContent.selection)
    fun deleteMoveLastCommitPosition() = _lastCommitPosition.handleDelete(activeContent.selection)

    /**
     * Class for handling history of last commit position.
     */
    data class LastCommitPosition(var pos: Int = -1) {

        constructor(other: LastCommitPosition): this(other.pos)

        fun reset() {
            pos = -1
        }

        fun handleCommit(selection: EditorRange) {
            if (selection.isValid) {
                pos = max(selection.start, selection.end)
            } else {
                reset()
            }
        }

        fun handleUpdateSelection(selection: EditorRange): Boolean {
            val start = min(selection.start, selection.end)
            if (selection.isNotValid || start != pos) {
                val wasTrackingCommit = pos >= 0
                reset()
                return wasTrackingCommit
            }
            return false
        }

        fun handleDelete(selection: EditorRange) {
            val start = min(selection.start, selection.end)
            if (start < pos) {
                pos = start
            }
        }
    }
}
