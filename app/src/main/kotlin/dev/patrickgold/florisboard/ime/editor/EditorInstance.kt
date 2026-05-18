/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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

import android.content.ClipDescription
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.view.KeyEvent
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.appContext
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardFileStorage
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import dev.patrickgold.florisboard.ime.input.InputShiftState
import dev.patrickgold.florisboard.ime.keyboard.IncognitoMode
import dev.patrickgold.florisboard.ime.keyboard.KeyboardMode
import dev.patrickgold.florisboard.ime.nlp.SuggestionCandidate
import dev.patrickgold.florisboard.ime.text.composing.Appender
import dev.patrickgold.florisboard.ime.text.composing.Composer
import dev.patrickgold.florisboard.ime.text.key.KeyVariation
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.lib.ext.ExtensionComponentName
import dev.patrickgold.florisboard.nlpManager
import dev.patrickgold.florisboard.subtypeManager
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.florisboard.lib.android.showShortToastSync

class EditorInstance(context: Context) : AbstractEditorInstance(context) {
    companion object {
        private const val SPACE = " "
    }

    private val prefs by FlorisPreferenceStore
    private val appContext by context.appContext()
    private val clipboardManager by context.clipboardManager()
    private val keyboardManager by context.keyboardManager()
    private val subtypeManager by context.subtypeManager()
    private val nlpManager by context.nlpManager()

    private val activeState get() = keyboardManager.activeState
    val autoSpace = AutoSpaceState()
    val phantomSpace = PhantomSpaceState()
    val massSelection = MassSelectionState()

    private fun currentInputConnection() = FlorisImeService.currentInputConnection()

    override fun handleStartInputView(editorInfo: FlorisEditorInfo, isRestart: Boolean) {
        if (!prefs.correction.rememberCapsLockState.get()) {
            // Enable auto-capitalization for the first letter of the text field
            activeState.inputShiftState = InputShiftState.SHIFTED_AUTOMATIC
        }
        activeState.isActionsOverflowVisible = false
        activeState.isActionsEditorVisible = false
        super.handleStartInputView(editorInfo, isRestart)
        val keyboardMode = when (editorInfo.inputAttributes.type) {
            InputAttributes.Type.NUMBER -> {
                // TYPE_NUMBER_VARIATION_PASSWORD (numeric PIN / OTP fields)
                // collapses to InputAttributes.Variation.PASSWORD. Mirror
                // that through to keyVariation so the privacy gates that
                // key off `keyVariation == KeyVariation.PASSWORD`
                // (clipboard history in EditorInstance.performClipboardCut /
                // performClipboardCopy, suggestion suppression here, glide /
                // long-press scaling in TextKeyboardLayout, etc.) all fire
                // for PIN entry. Without this, a numeric-PIN copy lands in
                // the IME-local clipboard history.
                activeState.keyVariation = if (editorInfo.inputAttributes.variation ==
                    InputAttributes.Variation.PASSWORD
                ) {
                    KeyVariation.PASSWORD
                } else {
                    KeyVariation.NORMAL
                }
                KeyboardMode.NUMERIC
            }
            InputAttributes.Type.PHONE -> {
                activeState.keyVariation = KeyVariation.NORMAL
                KeyboardMode.PHONE
            }
            InputAttributes.Type.TEXT -> {
                activeState.keyVariation = when (editorInfo.inputAttributes.variation) {
                    InputAttributes.Variation.EMAIL_ADDRESS,
                    InputAttributes.Variation.WEB_EMAIL_ADDRESS,
                    -> {
                        KeyVariation.EMAIL_ADDRESS
                    }
                    InputAttributes.Variation.PASSWORD,
                    InputAttributes.Variation.VISIBLE_PASSWORD,
                    InputAttributes.Variation.WEB_PASSWORD,
                    -> {
                        KeyVariation.PASSWORD
                    }
                    InputAttributes.Variation.URI -> {
                        KeyVariation.URI
                    }
                    else -> {
                        KeyVariation.NORMAL
                    }
                }
                KeyboardMode.CHARACTERS
            }
            else -> {
                activeState.keyVariation = KeyVariation.NORMAL
                KeyboardMode.CHARACTERS
            }
        }
        activeState.keyboardMode = keyboardMode
        activeState.isComposingEnabled = when (keyboardMode) {
            KeyboardMode.NUMERIC,
            KeyboardMode.PHONE,
            KeyboardMode.PHONE2,
            -> false
            else -> activeState.keyVariation != KeyVariation.PASSWORD &&
                prefs.suggestion.enabled.get()// &&
            //!instance.inputAttributes.flagTextAutoComplete &&
            //!instance.inputAttributes.flagTextNoSuggestions
        }
        // App-declared `IME_FLAG_NO_PERSONALIZED_LEARNING` is a privacy
        // contract from the host app (Signal, ProtonMail, banking,
        // end-to-end encrypted chat), not a user preference. It must be
        // honoured regardless of the user's IncognitoMode setting —
        // otherwise a user who set IncognitoMode.FORCE_OFF to avoid
        // "manually toggling incognito mode all the time" silently
        // overrides every sensitive-field declaration the OS pipeline
        // delivers.
        //
        // The user's IncognitoMode preference still controls *user-
        // requested* incognito (the toggle on the smartbar, the
        // FORCE_ON power-user setting). It just cannot turn the
        // app-declared flag *off*.
        val appDeclaredNoLearning = editorInfo.imeOptions.flagNoPersonalizedLearning
        activeState.isIncognitoMode = appDeclaredNoLearning || when (prefs.suggestion.incognitoMode.get()) {
            IncognitoMode.FORCE_OFF -> false
            IncognitoMode.FORCE_ON -> true
            IncognitoMode.DYNAMIC_ON_OFF -> prefs.suggestion.forceIncognitoModeFromDynamic.get()
        }
    }

    override fun handleSelectionUpdate(oldSelection: EditorRange, newSelection: EditorRange, composing: EditorRange) {
        autoSpace.setInactiveFromUpdate()
        phantomSpace.setInactiveFromUpdate()
        if (massSelection.isActive) {
            super.handleMassSelectionUpdate(newSelection, composing)
        } else {
            super.handleSelectionUpdate(oldSelection, newSelection, composing)
        }
    }

    override fun determineComposingEnabled(): Boolean {
        return activeState.isComposingEnabled && nlpManager.isSuggestionOn()
    }

    override fun determineComposer(composerName: ExtensionComponentName): Composer {
        return keyboardManager.resources.composers.value[composerName] ?: Appender
    }

    override fun shouldDetermineComposingRegion(editorInfo: FlorisEditorInfo): Boolean {
        return super.shouldDetermineComposingRegion(editorInfo) &&
            (phantomSpace.isInactive || phantomSpace.showComposingRegion)
    }

    /**
     * Sets the selection of the input editor to the specified [start] and [end] values. This method does nothing if
     * the input connection is not valid or if the input editor is raw.
     *
     * @param start The start of the selection (inclusive). May be any value ranging from -1 to positive infinity.
     * @param end The end of the selection (exclusive). May be any value ranging from -1 to positive infinity.
     *
     * @return True on success or if the selection is already at specified position, false otherwise.
     */
    fun setSelection(start: Int, end: Int): Boolean {
        autoSpace.setInactive()
        phantomSpace.setInactive()
        val selection = EditorRange.normalized(start, end)
        return super.setSelection(selection)
    }

    private fun shouldInsertAutoSpaceBefore(text: String): Boolean {
        if (!prefs.correction.autoSpacePunctuation.get() || text.isEmpty()) return false
        if (activeInfo.isRawInputEditor) return false
        if (activeState.keyVariation != KeyVariation.NORMAL) return false

        val punctuationRule = nlpManager.getActivePunctuationRule()
        val textBefore = activeContent.getTextBeforeCursor(1)
        return textBefore.isNotEmpty() && !textBefore.last().isWhitespace() &&
            punctuationRule.symbolsFollowingAutoSpace.contains(text.first())
    }

    private fun shouldInsertAutoSpaceAfter(text: String): Boolean {
        if (!prefs.correction.autoSpacePunctuation.get() || text.isEmpty()) return false
        if (activeInfo.isRawInputEditor) return false
        if (activeState.keyVariation != KeyVariation.NORMAL) return false

        val punctuationRule = nlpManager.getActivePunctuationRule()
        val content = activeContent
        val textBefore = content.getTextBeforeCursor(3).let { textBefore ->
            if (autoSpace.isActive && textBefore.isNotEmpty() && textBefore.last() == ' ') {
                textBefore.dropLast(1)
            } else {
                textBefore
            }
        }
        return textBefore.isNotEmpty() && !textBefore.last().isWhitespace() &&
            content.currentWordText.all { !it.isDigit() } &&
            punctuationRule.symbolsPrecedingAutoSpace.contains(text.first())
    }

    private fun isSentenceEndingPunctuation(char: String): Boolean {
        return char.isNotEmpty() && char.first() in ".!?"
    }

    private fun shouldAutoCapitalizeAfter(char: String): Boolean {
        if (!prefs.correction.autoCapitalization.get() || !isSentenceEndingPunctuation(char)) return false
        if (activeInfo.isRawInputEditor) return false
        if (activeState.keyVariation != KeyVariation.NORMAL) return false
        // Only auto-cap when the punctuation actually ends a word (letter immediately before).
        // Skip cases like "3.14", "192.168.0.1", "e.g.", "U.S.A.", URLs, abbreviations, ellipses.
        val textBefore = activeContent.getTextBeforeCursor(2)
        val charBeforePunctuation = textBefore.lastOrNull() ?: return false
        if (!charBeforePunctuation.isLetter()) return false
        // Skip single-letter abbreviations ("e.", "U.", "A.") — heuristic: require at least 2 letters,
        // or that the char before that is itself a letter (i.e. the punct closes a real word).
        val charBeforeWordEnd = textBefore.dropLast(1).lastOrNull()
        if (charBeforeWordEnd != null && charBeforeWordEnd.isLetter().not() &&
            charBeforeWordEnd.isWhitespace().not()
        ) {
            // Pattern like "X.Y." — likely an abbreviation chain, not a sentence end.
            return false
        }
        return true
    }

    override fun commitChar(char: String): Boolean {
        val isInsertAutoSpaceBeforeChar = shouldInsertAutoSpaceBefore(char)
        val isInsertAutoSpaceAfterChar = shouldInsertAutoSpaceAfter(char)
        val shouldCapitalizeAfterPunctuation = shouldAutoCapitalizeAfter(char)
        val isDeletePreviousSpace = isInsertAutoSpaceAfterChar && autoSpace.isActive

        if (isInsertAutoSpaceAfterChar) {
            autoSpace.setActive()
        } else {
            autoSpace.setInactive()
        }
        val isPhantomSpaceActive = phantomSpace.determine(char)
        phantomSpace.setInactive()

        val result = super.commitChar(
            char = char,
            deletePreviousSpace = isDeletePreviousSpace,
            insertSpaceBeforeChar = isInsertAutoSpaceBeforeChar || isPhantomSpaceActive,
            insertSpaceAfterChar = isInsertAutoSpaceAfterChar,
        )

        // Arm auto-capitalization for the next character after sentence-ending punctuation.
        // The shift state is consumed when the next alphabetic character is committed
        // (KeyboardManager.onInputKeyUp resets SHIFTED_AUTOMATIC after consuming a letter).
        if (result && shouldCapitalizeAfterPunctuation) {
            activeState.inputShiftState = InputShiftState.SHIFTED_AUTOMATIC
        }

        return result
    }

    /**
     * Commits the given [text] to this editor instance and adjusts both the cursor position and
     * composing region, if any.
     *
     * This method overwrites any selected text and replaces it with given [text]. If there is no
     * text selected (selection is in cursor mode), then this method will insert the [text] after
     * the cursor, then set the cursor position to the first character after the inserted text.
     *
     * @param text The text to commit.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    override fun commitText(text: String): Boolean {
        val isPhantomSpaceActive = phantomSpace.determine(text)
        autoSpace.setInactive()
        phantomSpace.setInactive()
        return if (isPhantomSpaceActive) {
            super.commitText("$SPACE$text")
        } else {
            super.commitText(text)
        }
    }

    /**
     * Completes the given [candidate] in the current composing region. Does nothing if the current
     * input editor is not rich or if the input connection is invalid.
     *
     * Current phantom space state is respected and a space char will be inserted accordingly.
     * Phantom space will be activated if the text is committed.
     *
     * @param candidate The candidate to complete in this editor.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun commitCompletion(candidate: SuggestionCandidate): Boolean {
        val text = candidate.text.toString()
        if (text.isEmpty() || activeInfo.isRawInputEditor) return false
        val content = activeContent
        return if (content.composing.isValid) {
            phantomSpace.setActive(showComposingRegion = false, candidate = candidate)
            super.finalizeComposingText(text)
        } else {
            val isPhantomSpaceActive = phantomSpace.determine(text)
            phantomSpace.setActive(showComposingRegion = false, candidate = candidate)
            return if (isPhantomSpaceActive) {
                super.commitText("$SPACE$text")
            } else {
                super.commitText(text)
            }.also {
                // handled in finalizeComposingText if content.composing.isValid
                updateLastCommitPosition()
            }
        }
    }

    /**
     * Commit a word generated by a gesture.
     *
     * Ignores the current phantom space state and will insert a space depending on the character
     * before selection start. Phantom space will be activated if the text is committed.
     *
     * @param text The text to commit in this editor.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun commitGesture(text: String): Boolean {
        if (text.isEmpty() || activeInfo.isRawInputEditor) return false
        val isPhantomSpaceActive = phantomSpace.determine(text, forceActive = true)
        phantomSpace.setActive(showComposingRegion = true)
        return if (isPhantomSpaceActive) {
            super.commitText("$SPACE$text")
        } else {
            super.commitText(text)
        }.also {
            updateLastCommitPosition()
        }
    }

    fun replaceCurrentGestureWord(expectedText: String, replacementText: String): Boolean {
        if (expectedText.isBlank() || replacementText.isBlank() || activeInfo.isRawInputEditor) return false
        val content = activeContent
        val activeWord = content.composingText.ifBlank { content.currentWordText }
        if (!activeWord.sameGestureWordAs(expectedText)) return false
        return if (content.composing.isValid) {
            super.finalizeComposingText(replacementText)
        } else {
            false
        }
    }

    /**
     * Commits the given [ClipboardItem]. If the clip data is text (incl. HTML), it delegates to [commitText].
     * If the item has a content URI (and the EditText supports it), the item is committed as rich data.
     * This allows for committing (e.g) images.
     *
     * @param item The ClipboardItem to commit
     *
     * @return True on success, false if something went wrong.
     */
    fun commitClipboardItem(item: ClipboardItem?): Boolean {
        if (item == null) return false
        val mimeTypes = item.mimeTypes
        return when (item.type) {
            ItemType.TEXT -> {
                commitText(item.text.toString()).also {
                    updateLastCommitPosition()
                }
            }
            ItemType.IMAGE, ItemType.VIDEO -> {
                item.uri ?: return false
                val id = ContentUris.parseId(item.uri)
                val file = ClipboardFileStorage.getFileForId(appContext, id)
                if (!file.exists()) return false
                val inputContentInfo = InputContentInfoCompat(
                    item.uri,
                    ClipDescription("clipboard media file", mimeTypes.toTypedArray()),
                    null,
                )
                val ic = currentInputConnection() ?: return false
                ic.finishComposingText()
                val flags = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
                InputConnectionCompat.commitContent(ic, activeInfo.base, inputContentInfo, flags, null)
            }
        }.also {
            if (prefs.clipboard.historyHideOnPaste.get()) {
                keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT
            }
        }
    }

    fun canCommitMimeType(mimeType: String): Boolean {
        return activeInfo.contentMimeTypes.any { editorMimeType ->
            ClipDescription.compareMimeTypes(mimeType, editorMimeType)
        }
    }

    fun commitRichContent(
        uri: Uri,
        mimeTypes: List<String>,
        descriptionLabel: String,
    ): Boolean {
        if (mimeTypes.isEmpty() || mimeTypes.none { canCommitMimeType(it) }) return false
        val ic = currentInputConnection() ?: return false
        val inputContentInfo = InputContentInfoCompat(
            uri,
            ClipDescription(descriptionLabel, mimeTypes.toTypedArray()),
            null,
        )
        ic.finishComposingText()
        val flags = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
        return InputConnectionCompat.commitContent(ic, activeInfo.base, inputContentInfo, flags, null)
    }

    /**
     * Executes a backward delete on this editor's text. If a text selection is active, all
     * characters inside this selection will be removed, else only the left-most character from
     * the cursor's position.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun deleteBackwards(unit: OperationUnit): Boolean {
        val content = activeContent
        if (unit == OperationUnit.CHARACTERS) {
            if (phantomSpace.isActive && content.currentWord.isValid && prefs.glide.immediateBackspaceDeletesWord.get()) {
                return deleteBackwards(OperationUnit.WORDS)
            }
        }
        autoSpace.setInactive()
        phantomSpace.setInactive()
        return if (content.selection.isSelectionMode) {
            commitText("")
        } else runBlocking {
            deleteAroundCursor(unit, OperationScope.BEFORE_CURSOR, n = 1)
        }
    }

    /**
     * Executes a backward delete on this editor's text. If a text selection is active, all
     * characters inside this selection will be removed, else only the left-most character from
     * the cursor's position.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun deleteForwards(unit: OperationUnit): Boolean {
        val content = activeContent
        autoSpace.setInactive()
        phantomSpace.setInactive()
        return if (content.selection.isSelectionMode) {
            commitText("")
        } else runBlocking {
            deleteAroundCursor(unit, OperationScope.AFTER_CURSOR, n = 1)
        }
    }

    fun setSelectionSurrounding(n: Int, unit: OperationUnit, scope: OperationScope): Boolean {
        autoSpace.setInactive()
        phantomSpace.setInactive()
        val content = activeContent
        val selection = content.selection
        val safeEditorBounds = content.safeEditorBounds
        if (selection.isNotValid) return false
        when (scope) {
            OperationScope.BEFORE_CURSOR -> {
                if (n <= 0) {
                    return setSelection(selection.end, selection.end)
                }
                val textToAnalyze = content.text.substring(0, content.localSelection.end)
                val length = runBlocking {
                    when (unit) {
                        OperationUnit.CHARACTERS -> breakIterators.measureLastUChars(textToAnalyze, n)
                        OperationUnit.WORDS -> breakIterators.measureLastUWords(textToAnalyze, n)
                    }
                }
                return setSelection((selection.end - length).coerceAtLeast(safeEditorBounds.start), selection.end)
            }
            OperationScope.AFTER_CURSOR -> {
                if (n <= 0) {
                    return setSelection(selection.start, selection.start)
                }
                val textToAnalyze = content.text.substring(content.localSelection.start)
                val length = runBlocking {
                    when (unit) {
                        OperationUnit.CHARACTERS -> breakIterators.measureUChars(textToAnalyze, n)
                        OperationUnit.WORDS -> breakIterators.measureUWords(textToAnalyze, n)
                    }
                }
                return setSelection(selection.start, (selection.start + length).coerceAtMost(safeEditorBounds.end))
            }
        }
    }

    /**
     * Performs a cut command on this editor instance and adjusts both the cursor position and
     * composing region, if any.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun performClipboardCut(): Boolean {
        autoSpace.setInactive()
        phantomSpace.setInactive()
        val text = activeContent.selectedText.ifBlank { currentInputConnection()?.getSelectedText(0) }
        if (text != null) {
            // ROADMAP §6 N7.2 — Never write password-field text into the keyboard
            // clipboard history. The system clipboard still receives it (the host app
            // initiated the cut), but our IME-local history must not retain it.
            if (!isPasswordField()) {
                clipboardManager.addNewPlaintext(text.toString())
            }
        } else {
            appContext.showShortToastSync("Failed to retrieve selected text requested to cut: Eiter selection state is invalid or an error occurred within the input connection.")
        }
        return deleteBackwards(OperationUnit.CHARACTERS)
    }

    /**
     * Performs a copy command on this editor instance and adjusts both the cursor position and
     * composing region, if any.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun performClipboardCopy(): Boolean {
        autoSpace.setInactive()
        phantomSpace.setInactive()
        val text = activeContent.selectedText.ifBlank { currentInputConnection()?.getSelectedText(0) }
        if (text != null) {
            // ROADMAP §6 N7.2 — same gating as performClipboardCut.
            if (!isPasswordField()) {
                clipboardManager.addNewPlaintext(text.toString())
            }
        } else {
            appContext.showShortToastSync("Failed to retrieve selected text requested to copy: Eiter selection state is invalid or an error occurred within the input connection.")
        }
        val activeSelection = activeContent.selection
        return setSelection(activeSelection.end, activeSelection.end)
    }

    private fun isPasswordField(): Boolean {
        return keyboardManager.activeState.keyVariation == KeyVariation.PASSWORD
    }

    /**
     * Performs a paste command on this editor instance and adjusts both the cursor position and
     * composing region, if any.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun performClipboardPaste(): Boolean {
        autoSpace.setInactive()
        phantomSpace.setInactive()
        return commitClipboardItem(clipboardManager.primaryClip).also { result ->
            if (!result) {
                appContext.showShortToastSync("Failed to paste item.")
            }
        }
    }

    /**
     * Performs a select all on this editor instance and adjusts both the cursor position and
     * composing region, if any.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun performClipboardSelectAll(): Boolean {
        autoSpace.setInactive()
        phantomSpace.setInactive()
        val ic = currentInputConnection() ?: return false
        ic.finishComposingText()
        return if (activeInfo.isRawInputEditor) {
            sendDownUpKeyEvent(KeyEvent.KEYCODE_A, meta(ctrl = true))
        } else {
            ic.performContextMenuAction(android.R.id.selectAll)
        }
    }

    /**
     * Requests one-shot capitalization for the next typed word.
     */
    fun performCapitalizeNextWord(): Boolean {
        autoSpace.setInactive()
        phantomSpace.setInactive()
        activeState.inputShiftState = InputShiftState.SHIFTED_AUTOMATIC
        return true
    }

    /**
     * Performs an enter key press on the current input editor.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun performEnter(): Boolean {
        autoSpace.setInactive()
        phantomSpace.setInactive()
        return if (activeInfo.isRawInputEditor) {
            sendDownUpKeyEvent(KeyEvent.KEYCODE_ENTER)
        } else {
            commitText("\n")
        }
    }

    fun tryPerformEnterCommitRaw(): Boolean {
        return if (subtypeManager.activeSubtype.primaryLocale.language.startsWith("zh") && activeContent.composing.length > 0) {
            finalizeComposingText(activeContent.composingText)
        } else {
            false
        }
    }

    /**
     * Performs a given [action] on the current input editor.
     *
     * @param action The action to be performed on this editor instance.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun performEnterAction(action: ImeOptions.Action): Boolean {
        autoSpace.setInactive()
        phantomSpace.setInactive()
        val ic = currentInputConnection() ?: return false
        return ic.performEditorAction(action.toInt())
    }

    /**
     * Undoes the last action.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun performUndo(): Boolean {
        autoSpace.setInactive()
        phantomSpace.setInactive()
        return sendDownUpKeyEvent(KeyEvent.KEYCODE_Z, meta(ctrl = true))
    }

    /**
     * Redoes the last Undo action.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun performRedo(): Boolean {
        autoSpace.setInactive()
        phantomSpace.setInactive()
        return sendDownUpKeyEvent(KeyEvent.KEYCODE_Z, meta(ctrl = true, shift = true))
    }

    override fun reset() {
        super.reset()
        autoSpace.setInactive()
        phantomSpace.setInactive()
        massSelection.reset()
    }

    private fun PhantomSpaceState.determine(text: String, forceActive: Boolean = false): Boolean {
         val content = activeContent
         val selection = content.selection
         if (!(isActive || forceActive) || selection.isNotValid || selection.start <= 0 || text.isEmpty()) return false
         val textBefore = content.getTextBeforeCursor(1)
         val punctuationRule = nlpManager.getActivePunctuationRule()
         if (!subtypeManager.activeSubtype.primaryLocale.supportsAutoSpace) return false;
         return textBefore.isNotEmpty() &&
             (punctuationRule.symbolsPrecedingPhantomSpace.contains(textBefore[textBefore.length - 1]) ||
                 textBefore[textBefore.length - 1].isLetterOrDigit()) &&
             (punctuationRule.symbolsFollowingPhantomSpace.contains(text[0]) || text[0].isLetterOrDigit())
    }

    private fun String.sameGestureWordAs(other: String): Boolean {
        return normalizedGestureWord() == other.normalizedGestureWord()
    }

    private fun String.normalizedGestureWord(): String {
        return trim()
            .trim { char -> !char.isLetter() && char != '\'' && char != '\u2019' }
            .lowercase()
    }

    class AutoSpaceState {
        companion object {
            private const val F_IS_ACTIVE = 0x1
            private const val F_STAY_ACTIVE_NEXT_UPDATE = 0x4
        }

        private val state = AtomicInteger(0)

        val isActive: Boolean
            get() = state.get() and F_IS_ACTIVE != 0

        val isInactive: Boolean
            get() = !isActive

        fun setActive(stayActiveNextUpdate: Boolean = true) {
            state.set(F_IS_ACTIVE or (if (stayActiveNextUpdate) F_STAY_ACTIVE_NEXT_UPDATE else 0))
        }

        fun setInactive() {
            state.set(0)
        }

        fun setInactiveFromUpdate() {
            state.updateAndGet { state ->
                if ((state and F_STAY_ACTIVE_NEXT_UPDATE) != 0) (state and F_STAY_ACTIVE_NEXT_UPDATE.inv()) else 0
            }
        }
    }

    class PhantomSpaceState {
        companion object {
            private const val F_IS_ACTIVE = 0x1
            private const val F_SHOW_COMPOSING_REGION = 0x2
            private const val F_STAY_ACTIVE_NEXT_UPDATE = 0x4
        }

        private val state = AtomicInteger(0)
        var candidateForRevert: SuggestionCandidate? = null
            private set

        val isActive: Boolean
            get() = state.get() and F_IS_ACTIVE != 0

        val isInactive: Boolean
            get() = !isActive

        val showComposingRegion: Boolean
            get() = state.get() and F_SHOW_COMPOSING_REGION != 0

        fun setActive(
            showComposingRegion: Boolean,
            stayActiveNextUpdate: Boolean = true,
            candidate: SuggestionCandidate? = null,
        ) {
            state.set(
                F_IS_ACTIVE
                    or (if (showComposingRegion) F_SHOW_COMPOSING_REGION else 0)
                    or (if (stayActiveNextUpdate) F_STAY_ACTIVE_NEXT_UPDATE else 0)
            )
            candidateForRevert = candidate
        }

        fun setInactive() {
            state.set(0)
            candidateForRevert = null
        }

        fun setInactiveFromUpdate() {
            val prevStateValue = state.getAndUpdate { state ->
                if ((state and F_STAY_ACTIVE_NEXT_UPDATE) != 0) (state and F_STAY_ACTIVE_NEXT_UPDATE.inv()) else 0
            }
            if ((prevStateValue and F_STAY_ACTIVE_NEXT_UPDATE) == 0) {
                candidateForRevert = null
            }
        }
    }

    inner class MassSelectionState {
        private val state = AtomicInteger(0)

        val isActive: Boolean
            get() = state.get() > 0

        val isInactive: Boolean
            get() = !isActive

        fun begin() {
            state.incrementAndGet()
        }

        fun end() {
            if (state.decrementAndGet() == 0) {
                // We need to emulate a selection update to update the content if mass selection has ended
                handleSelectionUpdate(EditorRange.Unspecified, activeContent.selection, EditorRange.Unspecified)
            }
        }

        fun reset() {
            state.set(0)
        }
    }
}
