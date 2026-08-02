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
import android.content.Context
import android.net.Uri
import android.view.KeyEvent
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.appContext
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardFileStorage
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import dev.patrickgold.florisboard.ime.cjk.MixedScriptSpacing
import dev.patrickgold.florisboard.ime.input.InputShiftState
import dev.patrickgold.florisboard.ime.keyboard.KeyboardMode
import dev.patrickgold.florisboard.ime.nlp.SuggestionCandidate
import dev.patrickgold.florisboard.ime.profile.PerAppBooleanOverride
import dev.patrickgold.florisboard.ime.profile.PerAppKeyboardProfilePolicy
import dev.patrickgold.florisboard.ime.profile.PerAppKeyboardProfiles
import dev.patrickgold.florisboard.ime.profile.PerAppSuggestionAggressiveness
import dev.patrickgold.florisboard.ime.profile.ResolvedPerAppKeyboardProfile
import dev.patrickgold.florisboard.ime.text.composing.Appender
import dev.patrickgold.florisboard.ime.text.composing.Composer
import dev.patrickgold.florisboard.ime.text.key.KeyVariation
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.lib.ext.ExtensionComponentName
import dev.patrickgold.florisboard.nlpManager
import dev.patrickgold.florisboard.subtypeManager
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.florisboard.lib.android.postShortToast

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

    protected override fun currentInputConnection() = FlorisImeService.currentInputConnection()

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
        val profile = activePerAppProfile(editorInfo.packageName)
        val baseComposingEnabled = when (keyboardMode) {
            KeyboardMode.NUMERIC,
            KeyboardMode.PHONE,
            KeyboardMode.PHONE2,
            -> false
            else -> activeState.keyVariation != KeyVariation.PASSWORD &&
                prefs.suggestion.enabled.get()// &&
            //!instance.inputAttributes.flagTextAutoComplete &&
            //!instance.inputAttributes.flagTextNoSuggestions
        }
        activeState.isComposingEnabled = PerAppKeyboardProfilePolicy.shouldEnableComposing(
            baseEnabled = baseComposingEnabled,
            suggestions = profile?.suggestions ?: PerAppSuggestionAggressiveness.FOLLOW_GLOBAL,
        )
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
        activeState.isIncognitoMode = PerAppKeyboardProfilePolicy.resolveIncognitoMode(
            appDeclaredNoPersonalizedLearning = editorInfo.imeOptions.flagNoPersonalizedLearning,
            globalPreference = prefs.suggestion.incognitoMode.get(),
            isDynamicIncognitoForced = prefs.suggestion.forceIncognitoModeFromDynamic.get(),
            override = profile?.incognito ?: PerAppBooleanOverride.FOLLOW_GLOBAL,
        )
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
        return EditorInputBehaviorPolicy.shouldInsertAutoSpaceBefore(
            text = text,
            textBeforeCursor = activeContent.getTextBeforeCursor(1),
            punctuationRule = nlpManager.getActivePunctuationRule(),
            isAutoSpacePunctuationEnabled = prefs.correction.autoSpacePunctuation.get(),
            isRawInputEditor = activeInfo.isRawInputEditor,
            isNormalKeyVariation = activeState.keyVariation == KeyVariation.NORMAL,
        )
    }

    private fun shouldInsertAutoSpaceAfter(text: String): Boolean {
        val content = activeContent
        return EditorInputBehaviorPolicy.shouldInsertAutoSpaceAfter(
            text = text,
            textBeforeCursor = content.getTextBeforeCursor(3),
            currentWordText = content.currentWordText,
            punctuationRule = nlpManager.getActivePunctuationRule(),
            isAutoSpaceActive = autoSpace.isActive,
            isAutoSpacePunctuationEnabled = prefs.correction.autoSpacePunctuation.get(),
            isRawInputEditor = activeInfo.isRawInputEditor,
            isNormalKeyVariation = activeState.keyVariation == KeyVariation.NORMAL,
        )
    }

    private fun shouldAutoCapitalizeAfter(char: String): Boolean {
        return EditorInputBehaviorPolicy.shouldAutoCapitalizeAfter(
            char = char,
            textBeforeCursor = activeContent.getTextBeforeCursor(2),
            isAutoCapitalizationEnabled = prefs.correction.autoCapitalization.get(),
            isRawInputEditor = activeInfo.isRawInputEditor,
            isNormalKeyVariation = activeState.keyVariation == KeyVariation.NORMAL,
        )
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
        // Han→Latin/Digit boundary (e.g. typing "A" right after a committed "你"):
        // the typed char arrives via commitChar, so the mixed-script boundary
        // space has to be requested here rather than in commitText.
        val isMixedScriptSpaceBeforeChar = shouldInsertMixedScriptSpaceBefore(char)

        val result = super.commitChar(
            char = char,
            deletePreviousSpace = isDeletePreviousSpace,
            insertSpaceBeforeChar = isInsertAutoSpaceBeforeChar || isPhantomSpaceActive || isMixedScriptSpaceBeforeChar,
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
        return if (isPhantomSpaceActive || shouldInsertMixedScriptSpaceBefore(text)) {
            super.commitText("$SPACE$text")
        } else {
            super.commitText(text)
        }
    }

    /**
     * ROADMAP §CJK — pangu-style mixed-script spacing. When the user enables
     * "CJK mixed-script spacing", inserts a single boundary space whenever a
     * commit crosses a Han↔Latin/Digit boundary (`安装App` → `安装 App`). The
     * Han boundary requirement scopes this strictly to CJK usage: Latin-only
     * and digit-only typing never crosses the boundary, so default behavior
     * is untouched. See [MixedScriptSpacing] for the full matrix.
     */
    private fun shouldInsertMixedScriptSpaceBefore(committing: CharSequence): Boolean {
        if (!prefs.localization.cjkMixedScriptSpacing.get()) return false
        if (activeInfo.isRawInputEditor) return false
        return MixedScriptSpacing.shouldInsertLeadingSpace(activeContent.textBeforeSelection, committing)
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
        // Composing is force-disabled in password fields, so the commitText
        // fallback below would APPEND the candidate after the typed text
        // instead of replacing it — silently corrupting masked input.
        // NlpManager suppresses candidates for password fields upstream;
        // this gate keeps a stray commit from ever reaching the editor.
        if (activeState.keyVariation == KeyVariation.PASSWORD) return false
        val content = activeContent
        val selectedTextSuggestion = candidate.isTextSuggestionSelected
        return if (content.composing.isValid) {
            phantomSpace.setActive(showComposingRegion = false, candidate = candidate)
            super.finalizeComposingText(
                text = text,
                selectedTextSuggestion = selectedTextSuggestion,
            )
        } else {
            val isPhantomSpaceActive = phantomSpace.determine(text)
            phantomSpace.setActive(showComposingRegion = false, candidate = candidate)
            return if (isPhantomSpaceActive) {
                commitTextInternal(
                    text = "$SPACE$text",
                    selectedTextSuggestion = selectedTextSuggestion,
                )
            } else {
                commitTextInternal(
                    text = text,
                    selectedTextSuggestion = selectedTextSuggestion,
                )
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
            super.finalizeComposingText(
                text = replacementText,
                selectedTextSuggestion = false,
            )
        } else {
            false
        }
    }

    /**
     * Replaces a previously committed glide word after verifying that the editor still contains the expected text at
     * the original range. Unlike [commitCompletion], this also works after the composing region has been finalized.
     */
    fun replaceCommittedGestureWord(
        range: EditorRange,
        expectedText: String,
        replacementText: String,
    ): Boolean {
        if (
            expectedText.isBlank() ||
            replacementText.isBlank() ||
            activeInfo.isRawInputEditor ||
            activeState.keyVariation == KeyVariation.PASSWORD ||
            !range.isValid ||
            range.start > range.end ||
            range.length != expectedText.length
        ) {
            return false
        }
        val content = activeContent
        if (content.offset < 0) return false
        val localStart = range.start - content.offset
        val localEnd = range.end - content.offset
        if (localStart < 0 || localEnd > content.text.length || localStart >= localEnd) return false
        if (content.text.substring(localStart, localEnd) != expectedText) return false
        if (!setSelection(range.start, range.end)) return false
        return super.commitText(replacementText).also {
            if (it) updateLastCommitPosition()
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
                val id = ClipboardCommitMediaPolicy.providerFileId(item.uri) ?: return false
                val file = ClipboardFileStorage.getFileForId(appContext, id)
                if (!file.exists()) return false
                val inputContentInfo = InputContentInfoCompat(
                    item.uri,
                    ClipDescription("clipboard media file", mimeTypes.toTypedArray()),
                    null,
                )
                val ic = currentInputConnection() ?: return false
                ic.beginBatchEdit()
                ic.finishComposingText()
                val flags = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
                val result = InputConnectionCompat.commitContent(ic, activeInfo.base, inputContentInfo, flags, null)
                ic.endBatchEdit()
                result
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
        if (EditorInputBehaviorPolicy.shouldEscalateGlideBackspaceToWordDelete(
            operationUnit = unit,
            isPhantomSpaceActive = phantomSpace.isActive,
            isCurrentWordValid = content.currentWord.isValid,
            immediateBackspaceDeletesWord = prefs.glide.immediateBackspaceDeletesWord.get(),
        )) {
            return deleteBackwards(OperationUnit.WORDS)
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
        // Selection-only content (mass-selection updates) and Unspecified
        // content carry offset = -1 with an EMPTY text window but an absolute
        // localSelection — the raw substring below would throw and
        // safeEditorBounds collapses to (0,0), so a precise select/delete
        // swipe arriving mid mass-selection must no-op instead of crashing
        // the IME or teleporting the cursor to 0.
        if (content.offset < 0) return false
        when (scope) {
            OperationScope.BEFORE_CURSOR -> {
                if (n <= 0) {
                    return setSelection(selection.end, selection.end)
                }
                val textToAnalyze = content.text.substring(0, content.localSelection.end)
                val length = when (unit) {
                    OperationUnit.CHARACTERS -> breakIterators.measureLastUCharsSync(textToAnalyze, n)
                    OperationUnit.WORDS -> breakIterators.measureLastUWordsSync(textToAnalyze, n)
                }
                return setSelection((selection.end - length).coerceAtLeast(safeEditorBounds.start), selection.end)
            }
            OperationScope.AFTER_CURSOR -> {
                if (n <= 0) {
                    return setSelection(selection.start, selection.start)
                }
                val textToAnalyze = content.text.substring(content.localSelection.start)
                val length = when (unit) {
                    OperationUnit.CHARACTERS -> breakIterators.measureUCharsSync(textToAnalyze, n)
                    OperationUnit.WORDS -> breakIterators.measureUWordsSync(textToAnalyze, n)
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
            // clipboard history. The system clipboard must still receive it —
            // the user explicitly asked to cut, and the cut below deletes the
            // selection, so dropping the text entirely would be silent data
            // loss. Suppression only skips the IME-local history insert.
            //
            // Suppress history when the active field is a password field or
            // incognito (user-toggled or app-declared
            // `IME_FLAG_NO_PERSONALIZED_LEARNING`). Without this gate, a user
            // typing in Signal who hits Cut would leak the selected text into
            // the IME-local clipboard palette where it can be re-pasted to any
            // other app via the history surface — bypassing the host-app
            // sensitive-field declaration that the rest of the IME (dictionary
            // learn, bigram store, smart-compose) honours.
            if (shouldSuppressClipboardHistory()) {
                setSensitivePrimaryClipWithoutHistory(text.toString())
            } else {
                clipboardManager.addNewPlaintext(text.toString())
            }
        } else {
            appContext.postShortToast(R.string.clipboard__cut_selection_failed)
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
            // ROADMAP §6 N7.2 — same gating as performClipboardCut: the system
            // clipboard always receives the copy, only the IME-local history
            // insert is suppressed for password/incognito fields.
            if (shouldSuppressClipboardHistory()) {
                setSensitivePrimaryClipWithoutHistory(text.toString())
            } else {
                clipboardManager.addNewPlaintext(text.toString())
            }
        } else {
            appContext.postShortToast(R.string.clipboard__copy_selection_failed)
        }
        val activeSelection = activeContent.selection
        return setSelection(activeSelection.end, activeSelection.end)
    }

    private fun isPasswordField(): Boolean {
        return keyboardManager.activeState.keyVariation == KeyVariation.PASSWORD
    }

    /**
     * Places [text] on the system primary clip WITHOUT inserting it into the
     * IME-local clipboard history. Used by cut/copy in password/incognito
     * fields: the user explicitly requested the clipboard operation, so the
     * system clipboard must receive the text, but the IME-local history must
     * not retain it. Forced `isSensitive` so the Android 13+ clipboard
     * preview redacts it once the sensitive flag propagates to the ClipData.
     */
    private fun setSensitivePrimaryClipWithoutHistory(text: String) {
        clipboardManager.setPlaintextWithoutHistory(text, isSensitive = true)
    }

    /**
     * Whether the IME-local clipboard history MUST refuse to retain the
     * about-to-be-cut/copied text. Returns true when:
     *
     *  - the active field is a password / numeric-PIN / web-password
     *    field (covered by [isPasswordField] via the v1.8.86
     *    `keyVariation` propagation for `TYPE_NUMBER_VARIATION_PASSWORD`); or
     *  - the active state is in incognito mode (user-toggled smartbar
     *    incognito, or app-declared `IME_FLAG_NO_PERSONALIZED_LEARNING`
     *    that v1.8.104 now forces on regardless of the user's
     *    IncognitoMode preference).
     *
     * Both signals are existing privacy contracts elsewhere in the IME
     * (dictionary learn, bigram store, smart-compose, voice — pending
     * v1.8.106). This helper unifies the gate so future callers can read
     * one source of truth.
     */
    private fun shouldSuppressClipboardHistory(): Boolean {
        val state = keyboardManager.activeState
        val profile = activePerAppProfile(activeInfo.packageName)
        return state.keyVariation == KeyVariation.PASSWORD ||
            state.isIncognitoMode ||
            PerAppKeyboardProfilePolicy.shouldSuppressClipboardHistory(
                profile?.clipboardHistory ?: PerAppBooleanOverride.FOLLOW_GLOBAL,
            )
    }

    private fun activePerAppProfile(packageName: String?): ResolvedPerAppKeyboardProfile? {
        return PerAppKeyboardProfiles.resolve(
            rawJson = prefs.privacy.perAppKeyboardProfiles.get(),
            packageName = packageName,
        )
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
        val directPasteText = clipboardManager.primaryTextForDirectPaste()
        val result = if (directPasteText != null) {
            commitText(directPasteText.toString()).also {
                updateLastCommitPosition()
                if (prefs.clipboard.historyHideOnPaste.get()) {
                    keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT
                }
            }
        } else {
            commitClipboardItem(clipboardManager.primaryClip)
        }
        return result.also {
            if (!result) {
                appContext.postShortToast(R.string.clipboard__paste_failed)
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
         return EditorInputBehaviorPolicy.shouldInsertPhantomSpace(
             text = text,
             textBeforeCursor = content.getTextBeforeCursor(1),
             punctuationRule = nlpManager.getActivePunctuationRule(),
             isActive = isActive,
             forceActive = forceActive,
             isSelectionValid = selection.isValid,
             selectionStart = selection.start,
             supportsAutoSpace = subtypeManager.activeSubtype.primaryLocale.supportsAutoSpace,
         )
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
            val newValue = state.updateAndGet { maxOf(it - 1, 0) }
            if (newValue == 0) {
                handleSelectionUpdate(EditorRange.Unspecified, activeContent.selection, EditorRange.Unspecified)
            }
        }

        fun reset() {
            state.set(0)
        }
    }
}
