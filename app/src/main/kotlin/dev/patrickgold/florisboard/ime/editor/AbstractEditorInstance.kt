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
import android.icu.lang.UCharacter
import android.icu.text.BreakIterator
import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.text.TextUtils
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
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
    private val expectedContentQueue = ExpectedContentQueue()
    private val passThroughKeys = PassThroughKeyTracker()
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

    open fun handleStartInput(editorInfo: FlorisEditorInfo) {
        cancelPendingContentGeneration()
        activeInfo = editorInfo
        activeCursorCapsMode = editorInfo.initialCapsMode
        activeContent = EditorContent.Unspecified
        passThroughKeys.start(editorInfo.initialSelection)
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
        passThroughKeys.start(selection)

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

    protected fun handleMassSelectionUpdate(newSelection: EditorRange, composing: EditorRange) {
        cancelPendingContentGeneration()
        activeCursorCapsMode = InputAttributes.CapsMode.NONE
        activeContent = EditorContent.selectionOnly(newSelection)
        if (composing.isValid) {
            currentInputConnection()?.setComposingRegion(EditorRange.Unspecified)
        }
        _lastCommitPosition.handleUpdateSelection(newSelection)
        passThroughKeys.start(newSelection)
        // Mass selection is the user selecting text, so whatever comes next isn't typed in a row.
        notifyCommitAdjacencyBrokenIfNeeded(true)
    }

    open fun handleSelectionUpdate(oldSelection: EditorRange, newSelection: EditorRange, composing: EditorRange) {
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
        val appReport = passThroughKeys.handleUpdateSelection(oldSelection, newSelection)
        val isOwnEdit = appReport == PassThroughReport.Insert || expectedContentQueue.containsSelection(newSelection)
        val leftTrackedCommit = _lastCommitPosition.handleUpdateSelection(newSelection)
        val isJump = !isOwnEdit && (leftTrackedCommit || newSelection != oldSelection)
        // A Backspace or Left arrow the app handled is a jump too, but the keys typed after it are
        // still on their way, so their cursors stay expected.
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
                }
            }
        }
    }

    private fun startContentGeneration(): Long {
        pendingContentGenerationJob?.cancel()
        contentGenerationSerial += 1
        return contentGenerationSerial
    }

    private fun cancelPendingContentGeneration() {
        pendingContentGenerationJob?.cancel()
        pendingContentGenerationJob = null
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

    private class ExpectedContentQueue {
        private val lock = Any()
        private val list = mutableListOf<EditorContent>()

        fun popUntilOrNull(predicate: (EditorContent) -> Boolean): EditorContent? {
            return synchronized(lock) {
                while (list.isNotEmpty()) {
                    val item = list.removeAt(0)
                    if (predicate(item)) return@synchronized item
                }
                return@synchronized null
            }
        }

        fun push(item: EditorContent) {
            synchronized(lock) {
                list.add(item)
            }
        }

        fun containsSelection(selection: EditorRange): Boolean {
            return synchronized(lock) {
                list.any { it.selection == selection }
            }
        }

        fun peekNewestOrNull(): EditorContent? {
            return synchronized(lock) {
                list.lastOrNull()
            }
        }

        fun clear() {
            synchronized(lock) {
                list.clear()
            }
        }
    }

    /** What a reported cursor meant to [PassThroughKeyTracker]. */
    private enum class PassThroughReport {
        /** The app typed keys it was passed, and the cursor is where they left it. */
        Insert,
        /** A Backspace or Left arrow the app was passed landed. The keys typed after it are still tracked. */
        StepBack,
        /** Nothing the app was passed explains the cursor. */
        None,
    }

    /**
     * Cursors the app will report after handling keys the keyboard passed through to it. The app,
     * not the keyboard, edits the text there, so those moves never show up in [ExpectedContentQueue].
     *
     * Fields handle some keys differently, and EditorInfo doesn't say which way this one does, so
     * the tracker follows every way still possible (a [Track] each) until a report only some of
     * them explain rules the others out. Two things differ:
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
    private class PassThroughKeyTracker {
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
         * way already ruled out. [quietUntil] is set while a report may already cover the key: past
         * it the key no longer holds the editor behind, but a late report still matches it.
         */
        private class PendingKey(val tracks: Array<Track?>, val isStepBack: Boolean, val atMillis: Long) {
            var quietUntil: Long = Long.MAX_VALUE

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

            /** A way is the kind of field times [NumDirections] plus the direction of a Left arrow. */
            fun kindOf(way: Int): Int = way / NumDirections
            fun directionOf(way: Int): Int = way % NumDirections
        }

        private val lock = Any()
        private var reported = EditorRange.Unspecified
        private val pending = ArrayDeque<PendingKey>()
        /** Until when a burst too long to track holds the editor behind. */
        private var overflowUntil = 0L
        private var graphemes: BreakIterator? = null
        /** The ways this field may handle keys, one bit per way (see [kindOf]). */
        private var possibleWays = AllWays

        fun start(selection: EditorRange) {
            synchronized(lock) {
                reported = selection
                pending.clear()
                overflowUntil = 0L
                possibleWays = AllWays
            }
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
                add(PendingKey(tracks, isStepBack = false, now))
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
            synchronized(lock) {
                val now = SystemClock.uptimeMillis()
                dropExpired(now)
                val base = baseOf(now, keyboardPending, published) ?: return
                val tracks = arrayOfNulls<Track>(NumWays)
                var moves = false
                for (way in 0 until NumWays) {
                    val from = base.tracks[way] ?: continue
                    val landing = stepBack(from, base, wholeCluster, kindOf(way) == JoinedKind, directionOf(way))
                    // Where there's nothing to step back over in this way (the start of the text, the
                    // end of it for a Left that moves forward, or a TextView's Backspace that took an
                    // ideograph and its selector at once), the cursor stays put and nothing reports it.
                    if (landing != null) moves = true
                    tracks[way] = landing ?: Track(EditorRange.cursor(from.range.start), from.text, from.after, reports = false)
                }
                // Nothing to step back over in any way, so the app reports nothing either.
                if (!moves) return
                add(PendingKey(tracks, isStepBack = true, now))
            }
        }

        /**
         * Where a step back from a [from] track lands, or null at the start of the text, in a field
         * of the joined kind or not whose Left goes in [direction]. A selection's text is in [base].
         * A Left arrow in a paragraph that runs right to left moves forward instead (see
         * [stepForward]).
         */
        private fun stepBack(from: Track, base: Base, wholeCluster: Boolean, joinedKind: Boolean, direction: Int): Track? {
            val range = from.range
            val text = from.text
            val after = from.after
            val selected = base.selected
            if (wholeCluster && goesForward(from, base, joinedKind, direction)) {
                return stepForward(range, text, selected, after)
            }
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
         * Where a Left arrow in a paragraph that runs right to left lands: at the right end of a
         * selection, which is where it ends, or past the next char, or null at the end of the
         * text, where it doesn't move. A TextView moves the cursor left on screen, and Compose and
         * Chrome move it the paragraph's way, which there is forward through the text.
         */
        private fun stepForward(range: EditorRange, text: String, selected: String, after: After?): Track? {
            if (range.isSelectionMode) return Track(EditorRange.cursor(max(range.start, range.end)), knownTail(text + selected), after)
            if (after == null || after.text.isEmpty()) {
                if (after != null && after.reachesEnd) return null
                // Past what the keyboard knows, the text most likely goes on with a single char.
                val unknown = after?.let { After("", reachesEnd = false, rightToLeft = true) }
                return Track(EditorRange.cursor(range.start + 1), knownTail(text + UnknownCharStandIn), unknown)
            }
            val clusters = graphemes ?: BreakIterator.getCharacterInstance().also { graphemes = it }
            clusters.setText(after.text)
            val step = clusters.following(0)
            val moved = after.text.substring(0, step)
            // Past a line break the next Left arrow is in another paragraph, which it checks again.
            val stillAfter = After(after.text.substring(step), after.reachesEnd, rightToLeft = '\n' !in moved)
            return Track(EditorRange.cursor(range.start + step), knownTail(text + moved), stillAfter)
        }

        /** True when a Left arrow from a [from] track moves forward through the text. */
        private fun goesForward(from: Track, base: Base, joinedKind: Boolean, direction: Int): Boolean {
            return when (direction) {
                LeftToRight -> false
                RightToLeft -> true
                else -> from.after != null &&
                    runsRightToLeft(from.text, from.range.start, base.selected, from.after, base.anchorFirst, joinedKind)
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
         * Where the next key the app handles starts: the last key still pending, so a fast burst
         * predicts every cursor, or else the keyboard's newest edit, or else the last report. A typed
         * char replaces a selection, so a range is kept lower end first. A key past its quiet
         * timeout left the cursor where the last report did, and the keyboard may have typed since.
         */
        private fun baseOf(now: Long, keyboardPending: EditorContent?, published: EditorContent): Base? {
            val last = pending.lastOrNull()?.takeIf { now <= it.quietUntil }
            if (last != null) return Base(last.tracks)
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
            val ways = possibleWays
            return Base(
                Array(NumWays) { way -> track.takeIf { ways and (1 shl way) != 0 } },
                whole?.selectedText.orEmpty(),
                anchorFirst = range.start <= range.end,
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
            // The dropped key's report won't match, so it reads as a jump and drops the rest. The
            // app is still that far behind, so hold until the burst has been quiet for a while or
            // a report matches again.
            if (pending.size > MaxPendingPassThroughKeys || key.atMillis < overflowUntil) {
                overflowUntil = key.atMillis + PassThroughInsertTimeoutMillis
            }
            while (pending.size > MaxPendingPassThroughKeys) {
                pending.removeFirst()
            }
        }

        /** Matches a cursor the editor reported against the keys the app hasn't reported yet. */
        fun handleUpdateSelection(oldSelection: EditorRange, selection: EditorRange): PassThroughReport {
            return synchronized(lock) {
                reported = selection
                // A key the app took as a shortcut never moves the cursor, so its entry would let a
                // tap that lands there much later pass as typing.
                val now = SystemClock.uptimeMillis()
                dropExpired(now)
                // Every key the app is passed moves the cursor, so a report that leaves it in place
                // (the app answering a new composing region) is none of them. After a step back, a
                // later key can expect that same cursor.
                val moved = selection.isCursorMode && selection != oldSelection
                var index = -1
                // The ways that explain the report at any key.
                var explaining = 0
                if (moved) {
                    pending.forEachIndexed { i, key ->
                        key.tracks.forEachIndexed { way, track ->
                            if (track != null && track.reports && track.range.start == selection.start) {
                                if (index < 0) index = i
                                explaining = explaining or (1 shl way)
                            }
                        }
                    }
                }
                // Only some ways explain the report, so the key it matched is one they leave in
                // different places (a Left over a conjunct or through Hebrew, a Backspace over a
                // variation selector), and this field handles keys in one of those. When others do
                // at a later key (a coalesced report), the next report may tell.
                if (index >= 0 && explaining != possibleWays) {
                    possibleWays = possibleWays and explaining
                    pending.forEach { key ->
                        for (way in 0 until NumWays) {
                            if (explaining and (1 shl way) == 0) key.tracks[way] = null
                        }
                    }
                }
                // The app may report several keys at once, so drop everything up to the match.
                var steppedBack = false
                var matchedAt = now
                repeat(index + 1) {
                    val key = pending.removeFirst()
                    // A Left the field's ways all leave in place (at the end of Hebrew text) moved
                    // nothing, so the text around the cursor still runs on.
                    if (key.isStepBack && key.tracks.any { it != null && it.reports }) steppedBack = true
                    matchedAt = key.atMillis
                }
                if (index >= 0) {
                    // The app is keeping up, so whatever overflowed is behind it.
                    overflowUntil = 0L
                    // A step back and the char again leave this cursor a second time, and the text
                    // reads the same either way, so this report may cover the keys up to there too.
                    // If no report follows soon, the app is done with them. A slow app reports
                    // later, so it gets longer.
                    val quietFor = max(PassThroughQuietTimeoutMillis, 2 * (now - matchedAt))
                    val lastSameCursor = pending.indexOfLast { key ->
                        key.tracks.any { it != null && it.range.start == selection.start }
                    }
                    pending.forEachIndexed { i, key ->
                        key.quietUntil = if (i <= lastSameCursor) now + quietFor else Long.MAX_VALUE
                    }
                }
                when {
                    index < 0 -> PassThroughReport.None
                    steppedBack -> PassThroughReport.StepBack
                    else -> PassThroughReport.Insert
                }
            }
        }

        /** True when a key the app hasn't reported yet is still expected. */
        fun hasPending(): Boolean {
            return synchronized(lock) {
                val now = SystemClock.uptimeMillis()
                dropExpired(now)
                // The quiet keys are the oldest ones and share one deadline, so the newest key
                // tells whether any key is still expected.
                val newest = pending.lastOrNull()
                (newest != null && now <= newest.quietUntil) || now < overflowUntil
            }
        }

        private fun dropExpired(now: Long) {
            while (pending.isNotEmpty() && pending.first().isExpired(now)) {
                pending.removeFirst()
            }
        }

        fun dropPending() {
            synchronized(lock) {
                pending.clear()
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
