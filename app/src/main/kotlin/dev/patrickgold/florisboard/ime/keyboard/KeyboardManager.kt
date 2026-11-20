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

package dev.patrickgold.florisboard.ime.keyboard

import android.content.Context
import android.hardware.input.InputManager
import android.icu.lang.UCharacter
import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.appContext
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.extensionManager
import dev.patrickgold.florisboard.ime.snippet.SnippetExpansionPolicy
import dev.patrickgold.florisboard.snippetManager
import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.ime.core.DisplayLanguageNamesIn
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.core.SubtypePreset
import dev.patrickgold.florisboard.ime.editor.EditorInputBehaviorPolicy
import dev.patrickgold.florisboard.ime.dictionary.DictionaryManager
import dev.patrickgold.florisboard.ime.dictionary.LearnedWordText
import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.editor.EditorCompatibilityPolicy
import dev.patrickgold.florisboard.ime.editor.EditorRange
import dev.patrickgold.florisboard.ime.editor.FlorisEditorInfo
import dev.patrickgold.florisboard.ime.editor.ImeOptions
import dev.patrickgold.florisboard.ime.editor.InputAttributes
import dev.patrickgold.florisboard.ime.editor.OperationUnit
import dev.patrickgold.florisboard.ime.hardware.HardwareKeyboardInputPolicy
import dev.patrickgold.florisboard.ime.hardware.HardwareKeyboardKeyDownAction
import dev.patrickgold.florisboard.ime.hardware.HardwareKeyboardKeyUpAction
import dev.patrickgold.florisboard.ime.hardware.HardwareKeyboardLayout
import dev.patrickgold.florisboard.ime.hardware.HardwareKeyboardRuntimeMapper
import dev.patrickgold.florisboard.ime.hardware.PassThroughMove
import dev.patrickgold.florisboard.ime.text.key.KeyVariation
import dev.patrickgold.florisboard.ime.input.InputEventDispatcher
import dev.patrickgold.florisboard.ime.input.InputKeyEventReceiver
import dev.patrickgold.florisboard.ime.input.InputShiftState
import dev.patrickgold.florisboard.ime.nlp.AutoCommitUndoSuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.CandidateCommitSideEffectPolicy
import dev.patrickgold.florisboard.ime.nlp.ClipboardSuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.GlideAlternativeSuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.LearnedWordForgetOffer
import dev.patrickgold.florisboard.ime.nlp.LearnedWordForgetSuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.PunctuationRule
import dev.patrickgold.florisboard.ime.nlp.SuggestionPrivacyPolicy
import dev.patrickgold.florisboard.ime.nlp.SuggestionCandidate
import dev.patrickgold.florisboard.ime.popup.PopupMappingComponent
import dev.patrickgold.florisboard.ime.text.composing.Composer
import dev.patrickgold.florisboard.ime.text.gestures.SwipeAction
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.key.KeyType
import dev.patrickgold.florisboard.ime.text.key.UtilityKeyAction
import dev.patrickgold.florisboard.ime.text.keyboard.BottomRowKey
import dev.patrickgold.florisboard.ime.text.keyboard.BottomRowPreset
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyboardCache
import dev.patrickgold.florisboard.ime.security.AdvancedProtectionPolicy
import dev.patrickgold.florisboard.lib.FlorisLocale
import dev.patrickgold.florisboard.lib.devtools.LogTopic
import dev.patrickgold.florisboard.lib.devtools.flogError
import dev.patrickgold.florisboard.lib.devtools.flogWarning
import dev.patrickgold.florisboard.lib.ext.ExtensionComponentName
import dev.patrickgold.florisboard.lib.titlecase
import dev.patrickgold.florisboard.lib.uppercase
import dev.patrickgold.florisboard.lib.util.InputMethodUtils
import dev.patrickgold.florisboard.nlpManager
import dev.patrickgold.florisboard.subtypeManager
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.florisboard.lib.android.AndroidKeyguardManager
import org.florisboard.lib.android.postShortToast
import org.florisboard.lib.android.showLongToast
import org.florisboard.lib.android.systemService
import org.florisboard.lib.kotlin.collectIn
import org.florisboard.lib.kotlin.collectLatestIn

class KeyboardManager(context: Context) : InputKeyEventReceiver {
    private val prefs by FlorisPreferenceStore
    private val appContext by context.appContext()
    private val clipboardManager by context.clipboardManager()
    private val editorInstance by context.editorInstance()
    private val extensionManager by context.extensionManager()
    private val nlpManager by context.nlpManager()
    private val snippetManager by context.snippetManager()
    private val subtypeManager by context.subtypeManager()
    val accessibilityAnnouncement = MutableStateFlow<String?>(null)

    private val hardwareKeyboardRuntimeMapper = HardwareKeyboardRuntimeMapper {
        appContext.systemService(InputManager::class).inputDeviceIds
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val layoutManager = LayoutManager(context)
    private val keyboardCache = TextKeyboardCache()
    @Volatile
    private var numericPasswordDigitMapping: Map<Int, Int> = emptyMap()

    val resources = KeyboardManagerResources()
    val activeState = ObservableKeyboardState.new()
    private val modeTransitions = KeyboardModeTransitionController()

    /** Typing into a field the keyboard draws itself (#24). */
    val textEntry = InKeyboardTextEntry()
    private var textEntryReturnKeyboardMode: KeyboardMode? = null
    var smartbarVisibleDynamicActionsCount by mutableIntStateOf(0)
    @Volatile
    private var incognitoModeChangedListener: ((Boolean) -> Unit)? = null
    private var lastToastReference = WeakReference<Toast>(null)

    fun configureNumericPasswordScramble(enabled: Boolean) {
        numericPasswordDigitMapping = if (enabled) {
            NumericPasswordScramblePolicy.newMapping()
        } else {
            emptyMap()
        }
        updateActiveEvaluators()
    }

    // Declared before init: the editor collectors init starts on [scope] run on another
    // thread and can reset the chain before the constructor reaches later properties.
    private val learnChain = PersonalLearnChain()

    private val activeEvaluatorGuard = Mutex(locked = false)
    private var activeEvaluatorVersion = AtomicInteger(0)
    val activeEvaluator: StateFlow<ComputingEvaluator>
        field = MutableStateFlow<ComputingEvaluator>(DefaultComputingEvaluator)
    val activeSmartbarEvaluator: StateFlow<ComputingEvaluator>
        field = MutableStateFlow<ComputingEvaluator>(DefaultComputingEvaluator)
    val lastCharactersEvaluator: StateFlow<ComputingEvaluator>
        field = MutableStateFlow<ComputingEvaluator>(DefaultComputingEvaluator)

    val inputEventDispatcher = InputEventDispatcher.new(
        repeatableKeyCodes = intArrayOf(
            KeyCode.ARROW_DOWN,
            KeyCode.ARROW_LEFT,
            KeyCode.ARROW_RIGHT,
            KeyCode.ARROW_UP,
            KeyCode.DELETE,
            KeyCode.FORWARD_DELETE,
            KeyCode.PAGE_UP,
            KeyCode.PAGE_DOWN,
            KeyCode.UNDO,
            KeyCode.REDO,
        )
    ).also { it.keyEventReceiver = this }

    init {
        scope.launch(Dispatchers.Main.immediate) {
            resources.anyChangedVersion.collectIn(scope) {
                // The layout caches key on a component name, so clearing only
                // the keyboard cache recomputed from a layout still parsed out
                // of the previous archive.
                layoutManager.invalidateCaches()
                updateActiveEvaluators {
                    keyboardCache.clear()
                }
            }
            prefs.keyboard.numberRow.asFlow().collectLatestIn(scope) {
                updateActiveEvaluators {
                    keyboardCache.clear(KeyboardMode.CHARACTERS)
                }
            }
            prefs.keyboard.hintedNumberRowEnabled.asFlow().collectLatestIn(scope) {
                updateActiveEvaluators()
            }
            prefs.keyboard.hintedSymbolsEnabled.asFlow().collectLatestIn(scope) {
                updateActiveEvaluators()
            }
            prefs.keyboard.bottomRowPresetJson.asFlow().collectLatestIn(scope) {
                updateActiveEvaluators {
                    keyboardCache.clear(KeyboardMode.CHARACTERS)
                }
            }
            prefs.keyboard.utilityKeyEnabled.asFlow().collectLatestIn(scope) {
                updateActiveEvaluators()
            }
            prefs.keyboard.utilityKeyAction.asFlow().collectLatestIn(scope) {
                updateActiveEvaluators()
            }
            // Every enforcement point reads the live AAPM state, so turning it
            // on already blocks the next clipboard write and the next enrolment.
            // Private typing is the exception: it is decided when a field is
            // focused, so a session already in progress would keep learning
            // until the user moved to another field. Force it here instead.
            // Clipboard retention and addon enrolment already read the live
            // AAPM state at every decision, so a toggle reaches them at once.
            // Private typing is decided when a field is focused, so a session
            // already in progress needs telling. Re-resolving rather than
            // forcing true is what lets a disable hand the decision back to the
            // saved preferences instead of leaving the keyboard private until
            // the user taps elsewhere. On the main thread because activeState
            // packs its flags into one non-atomic word.
            AdvancedProtectionPolicy.decisions.collectLatestIn(scope) {
                withContext(Dispatchers.Main.immediate) {
                    editorInstance.reevaluateIncognitoMode()
                }
            }
            activeState.collectLatestIn(scope) {
                updateActiveEvaluators()
            }
            activeState
                .map { it.isIncognitoMode }
                .distinctUntilChanged()
                .collectIn(scope) {
                    nlpManager.clearGlideAlternatives()
                }
            subtypeManager.subtypesFlow.collectLatestIn(scope) {
                updateActiveEvaluators()
            }
            subtypeManager.activeSubtypeFlow.collectLatestIn(scope) { subtype ->
                reevaluateInputShiftState()
                updateActiveEvaluators()
                editorInstance.refreshComposing()
                nlpManager.clearGlideAlternatives()
                resetSuggestions(editorInstance.activeContent)
                dev.patrickgold.florisboard.ime.text.keyboard.AdaptiveTouchModel
                    .setActiveSubtype("${subtype.id}:${subtype.primaryLocale.languageTag()}")
            }
            clipboardManager.primaryClipFlow.collectLatestIn(scope) {
                updateActiveEvaluators()
            }
            clipboardManager.primaryClipAvailableFlow.collectLatestIn(scope) {
                updateActiveEvaluators()
            }
            editorInstance.activeContentFlow.collectIn(scope) { content ->
                resetSuggestions(content)
            }
            // The editor flags every cursor move that isn't typing (a tap, a selection, an app edit).
            // That ends the "Don't learn" offer too, or a tap on another word and back to the end of
            // the text would bring its chip back.
            editorInstance.commitAdjacencyBrokenFlow.collectIn(scope) {
                resetLearnChain()
                nlpManager.clearForgetLearnedWordOffer()
            }
            // A field/app switch breaks word adjacency — without this reset the
            // first word typed in the new field forms a phantom bigram/trigram
            // with the last word learned in the previous field (e.g. Slack
            // message ending "meeting" + URL bar "tomorrow" would persist the
            // never-typed pair "meeting tomorrow" to the personal n-gram store).
            editorInstance.activeInfoFlow.collectIn(scope) {
                resetLearnChain()
                nlpManager.clearForgetLearnedWordOffer()
                nlpManager.clearGlideAlternatives()
            }
            prefs.devtools.enabled.asFlow().collectLatestIn(scope) {
                reevaluateDebugFlags()
            }
            prefs.devtools.showDragAndDropHelpers.asFlow().collectLatestIn(scope) {
                reevaluateDebugFlags()
            }
        }
    }

    fun updateActiveEvaluators(action: () -> Unit = { }) = scope.launch {
        activeEvaluatorGuard.withLock {
            action()
            val editorInfo = editorInstance.activeInfo
            val state = activeState.snapshot()
            val subtype = subtypeManager.activeSubtype
            val mode = state.keyboardMode
            // We need to reset the snapshot input shift state for non-character layouts, because the shift mechanic
            // only makes sense for the character layouts.
            if (mode != KeyboardMode.CHARACTERS) {
                state.inputShiftState = InputShiftState.UNSHIFTED
            }
            val computedKeyboard = keyboardCache.getOrElseAsync(mode, subtype) {
                layoutManager.computeKeyboardAsync(
                    keyboardMode = mode,
                    subtype = subtype,
                ).await()
            }
            val computingEvaluator = ComputingEvaluatorImpl(
                version = activeEvaluatorVersion.getAndAdd(1),
                keyboard = computedKeyboard,
                editorInfo = editorInfo,
                state = state,
                subtype = subtype,
            )
            for (key in computedKeyboard.keys()) {
                key.compute(computingEvaluator)
                key.computeLabelsAndDrawables(computingEvaluator)
            }
            activeEvaluator.value = computingEvaluator
            activeSmartbarEvaluator.value = computingEvaluator.asSmartbarQuickActionsEvaluator()
            if (computedKeyboard.mode == KeyboardMode.CHARACTERS) {
                lastCharactersEvaluator.value = computingEvaluator
            }
        }
    }

    fun setIncognitoModeChangedListener(listener: ((Boolean) -> Unit)?) {
        incognitoModeChangedListener = listener
    }

    fun transitionToKeyboardMode(mode: KeyboardMode) {
        applyModeTransition(modeTransitions.transitionToKeyboardMode(mode))
    }

    fun transitionToImeUiMode(mode: ImeUiMode) {
        applyModeTransition(modeTransitions.transitionToImeUiMode(mode))
    }

    /**
     * Shows the letter keys with an entry bar for [request] and sends key
     * presses to [textEntry] until the user presses Done, Enter or Back.
     */
    fun beginTextEntry(request: InKeyboardTextEntry.Request) {
        FlorisImeService.inputFeedbackController()?.keyPress(TextKeyData.UNSPECIFIED)
        activeState.batchEdit {
            transitionToImeUiMode(ImeUiMode.TEXT)
            textEntryReturnKeyboardMode = activeState.keyboardMode
            textEntry.begin(request)
            if (activeState.keyboardMode != KeyboardMode.CHARACTERS) {
                transitionToKeyboardMode(KeyboardMode.CHARACTERS)
            }
            activeState.inputShiftState = InputShiftState.UNSHIFTED
            activeState.isActionsOverflowVisible = false
            activeState.isActionsEditorVisible = false
        }
    }

    /** Ends the entry and goes back to the panel that started it, which picks up the outcome. */
    fun finishTextEntry(submitted: Boolean) {
        val returnTo = textEntry.finish(submitted) ?: return
        activeState.batchEdit {
            restoreKeyboardModeAfterTextEntry()
            transitionToImeUiMode(returnTo)
        }
    }

    /** Drops an open entry without an outcome and leaves the letter keys as the app had them. */
    fun abandonTextEntry() {
        if (!textEntry.isActive) return
        textEntry.abandon()
        activeState.batchEdit {
            restoreKeyboardModeAfterTextEntry()
        }
    }

    private fun restoreKeyboardModeAfterTextEntry() {
        val mode = textEntryReturnKeyboardMode ?: return
        textEntryReturnKeyboardMode = null
        if (activeState.keyboardMode != mode) {
            transitionToKeyboardMode(mode)
        }
    }

    /** Returns true when the key was fully handled by the entry. */
    private fun routeKeyToTextEntry(data: KeyData): Boolean {
        return when (TextEntryKeyPolicy.routeFor(data.code, data.type)) {
            TextEntryKeyRoute.Text -> {
                textEntry.type(data.asString(isForDisplay = false))
                if (activeState.inputShiftState != InputShiftState.CAPS_LOCK &&
                    !inputEventDispatcher.isPressed(KeyCode.SHIFT)
                ) {
                    activeState.inputShiftState = InputShiftState.UNSHIFTED
                }
                true
            }
            TextEntryKeyRoute.Space -> textEntry.type(" ")
            TextEntryKeyRoute.DeleteCharacter -> textEntry.deleteBackward()
            TextEntryKeyRoute.DeleteWord -> textEntry.deleteBackward(wholeWord = true)
            TextEntryKeyRoute.Submit -> {
                finishTextEntry(submitted = true)
                true
            }
            TextEntryKeyRoute.Cancel -> {
                finishTextEntry(submitted = false)
                true
            }
            TextEntryKeyRoute.CancelThenPassThrough -> {
                finishTextEntry(submitted = false)
                false
            }
            TextEntryKeyRoute.Ignore -> true
            TextEntryKeyRoute.PassThrough -> false
        }
    }

    private fun isIgnoredByTextEntry(data: KeyData): Boolean {
        return textEntry.isActive &&
            TextEntryKeyPolicy.routeFor(data.code, data.type) == TextEntryKeyRoute.Ignore
    }

    fun prepareForEditor(preserveClipboard: Boolean) {
        applyModeTransition(modeTransitions.prepareForEditor(preserveClipboard))
    }

    fun setKeyboardModeForEditor(mode: KeyboardMode) {
        applyModeTransition(modeTransitions.setKeyboardModeForEditor(mode))
    }

    fun resetUiModeAndHistory() {
        applyModeTransition(modeTransitions.resetUiModeAndHistory())
    }

    private fun applyModeTransition(state: KeyboardModeTransitionState) {
        activeState.batchEdit {
            activeState.keyboardMode = state.keyboardMode
            activeState.imeUiMode = state.imeUiMode
        }
    }

    fun clearIncognitoModeChangedListener(listener: ((Boolean) -> Unit)? = null) {
        // Identity-checked clear: KeyboardManager is a process singleton, so a
        // torn-down IME service must not wipe a listener that a newer service
        // instance already registered during an overlapping teardown/recreate
        // (config change, theme switch, OOM rebind). A null argument keeps the
        // legacy unconditional behaviour for any caller that does not track its
        // own listener reference.
        if (listener == null || incognitoModeChangedListener === listener) {
            incognitoModeChangedListener = null
        }
    }

    fun reevaluateInputShiftState() {
        if (activeState.inputShiftState != InputShiftState.CAPS_LOCK && !inputEventDispatcher.isPressed(KeyCode.SHIFT)) {
            val capsMode = editorInstance.activeCursorCapsMode
            val autoCapEnabled = prefs.correction.autoCapitalization.get()
                && subtypeManager.activeSubtype.primaryLocale.supportsCapitalization

            // Workaround for apps like TikTok that don't report caps mode: force auto-cap at start of field
            // and infer it from the text immediately preceding the cursor when the OS hides it.
            val textBeforeCursor = editorInstance.activeContent.textBeforeSelection
            val isAtStartOfField = textBeforeCursor.isEmpty()
            val isAfterSentenceEnd = isCursorAfterSentenceEnd(textBeforeCursor)

            val shift = autoCapEnabled &&
                (capsMode != InputAttributes.CapsMode.NONE || isAtStartOfField || isAfterSentenceEnd)

            activeState.inputShiftState = when {
                shift -> InputShiftState.SHIFTED_AUTOMATIC
                else -> InputShiftState.UNSHIFTED
            }
        }
    }

    /**
     * Returns true if the text before the cursor genuinely ends a sentence:
     * a letter, then sentence-ending punctuation, then optional whitespace.
     * Avoids false positives for decimals (3.14), IPs (192.168.0.1), abbreviations (e.g.),
     * URLs, and ellipses.
     */
    private fun isCursorAfterSentenceEnd(textBeforeCursor: CharSequence): Boolean {
        if (textBeforeCursor.isEmpty()) return false
        var i = textBeforeCursor.length - 1
        // Skip trailing whitespace (but not newlines used for visual reset).
        while (i >= 0 && textBeforeCursor[i] == ' ') i--
        if (i < 0) return false
        val punct = textBeforeCursor[i]
        if (punct !in ".!?") return false
        // Reject ellipses ("...").
        if (i >= 2 && textBeforeCursor[i - 1] == '.' && textBeforeCursor[i - 2] == '.') return false
        // The character immediately before the punctuation must be a letter
        // (excludes "3.", "X.Y." abbreviation chains, etc.).
        if (i == 0) return false
        val prev = textBeforeCursor[i - 1]
        if (!prev.isLetter()) return false
        // Single-letter chunk preceded by another '.' looks like an abbreviation chain (e.g. "U.S.A.").
        if (i >= 2 && textBeforeCursor[i - 2] == '.') return false
        return true
    }

    fun resetSuggestions(content: EditorContent) {
        val allowsImeSuggestions = EditorCompatibilityPolicy.snapshot(editorInstance.activeInfo)
            .allowsImeSuggestions
        if (!allowsImeSuggestions || !(activeState.isComposingEnabled || nlpManager.isSuggestionOn())) {
            nlpManager.clearSuggestions()
            return
        }
        nlpManager.suggest(subtypeManager.activeSubtype, content)
    }

    /**
     * @return If the language switch should be shown.
     */
    fun shouldShowLanguageSwitch(): Boolean {
        return subtypeManager.subtypes.size > 1
    }

    fun executeSwipeAction(swipeAction: SwipeAction) {
        val keyData = when (swipeAction) {
            SwipeAction.CYCLE_TO_PREVIOUS_KEYBOARD_MODE -> when (activeState.keyboardMode) {
                KeyboardMode.CHARACTERS -> TextKeyData.VIEW_NUMERIC_ADVANCED
                KeyboardMode.NUMERIC_ADVANCED -> TextKeyData.VIEW_SYMBOLS2
                KeyboardMode.SYMBOLS2 -> TextKeyData.VIEW_SYMBOLS
                else -> TextKeyData.VIEW_CHARACTERS
            }
            SwipeAction.CYCLE_TO_NEXT_KEYBOARD_MODE -> when (activeState.keyboardMode) {
                KeyboardMode.CHARACTERS -> TextKeyData.VIEW_SYMBOLS
                KeyboardMode.SYMBOLS -> TextKeyData.VIEW_SYMBOLS2
                KeyboardMode.SYMBOLS2 -> TextKeyData.VIEW_NUMERIC_ADVANCED
                else -> TextKeyData.VIEW_CHARACTERS
            }
            SwipeAction.DELETE_WORD -> TextKeyData.DELETE_WORD
            SwipeAction.HIDE_KEYBOARD -> TextKeyData.IME_HIDE_UI
            SwipeAction.INSERT_SPACE -> TextKeyData.SPACE
            SwipeAction.MOVE_CURSOR_DOWN -> TextKeyData.ARROW_DOWN
            SwipeAction.MOVE_CURSOR_UP -> TextKeyData.ARROW_UP
            SwipeAction.MOVE_CURSOR_LEFT -> TextKeyData.ARROW_LEFT
            SwipeAction.MOVE_CURSOR_RIGHT -> TextKeyData.ARROW_RIGHT
            SwipeAction.MOVE_CURSOR_START_OF_LINE -> TextKeyData.MOVE_START_OF_LINE
            SwipeAction.MOVE_CURSOR_END_OF_LINE -> TextKeyData.MOVE_END_OF_LINE
            SwipeAction.MOVE_CURSOR_START_OF_PAGE -> TextKeyData.MOVE_START_OF_PAGE
            SwipeAction.MOVE_CURSOR_END_OF_PAGE -> TextKeyData.MOVE_END_OF_PAGE
            SwipeAction.SHIFT -> TextKeyData.SHIFT
            SwipeAction.REDO -> TextKeyData.REDO
            SwipeAction.UNDO -> TextKeyData.UNDO
            SwipeAction.SHOW_INPUT_METHOD_PICKER -> TextKeyData.SYSTEM_INPUT_METHOD_PICKER
            SwipeAction.SHOW_SUBTYPE_PICKER -> TextKeyData.SHOW_SUBTYPE_PICKER
            SwipeAction.SWITCH_TO_CLIPBOARD_CONTEXT -> TextKeyData.IME_UI_MODE_CLIPBOARD
            SwipeAction.SWITCH_TO_MEDIA_CONTEXT -> TextKeyData.IME_UI_MODE_MEDIA
            SwipeAction.SWITCH_TO_PREV_SUBTYPE -> TextKeyData.IME_PREV_SUBTYPE
            SwipeAction.SWITCH_TO_NEXT_SUBTYPE -> TextKeyData.IME_NEXT_SUBTYPE
            SwipeAction.SWITCH_TO_PREV_KEYBOARD -> TextKeyData.SYSTEM_PREV_INPUT_METHOD
            SwipeAction.TOGGLE_SMARTBAR_VISIBILITY -> TextKeyData.TOGGLE_SMARTBAR_VISIBILITY
            SwipeAction.TOGGLE_COMPACT_LAYOUT -> TextKeyData.TOGGLE_COMPACT_LAYOUT
            else -> null
        }
        if (keyData != null) {
            inputEventDispatcher.sendDownUp(keyData)
        }
    }

    fun commitCandidate(candidate: SuggestionCandidate): Boolean {
        if (candidate is LearnedWordForgetSuggestionCandidate) {
            forgetLearnedWord(candidate)
            return true
        }
        if (candidate is AutoCommitUndoSuggestionCandidate) {
            return commitAutoCommitUndoCandidate(candidate)
        }
        if (candidate is GlideAlternativeSuggestionCandidate) {
            return commitGlideAlternativeCandidate(candidate)
        }
        val committed = when (candidate) {
            is ClipboardSuggestionCandidate -> editorInstance.commitClipboardItem(candidate.clipboardItem)
            else -> editorInstance.commitCompletion(candidate)
        }
        val sourceProvider = candidate.sourceProvider
        if (CandidateCommitSideEffectPolicy.shouldNotifyAcceptedProvider(
                commitSucceeded = committed,
                hasSourceProvider = sourceProvider != null,
            )
        ) {
            sourceProvider?.let {
                scope.launch {
                    it.notifySuggestionAccepted(subtypeManager.activeSubtype, candidate)
                }
            }
        }
        if (CandidateCommitSideEffectPolicy.shouldLearnCommittedCandidate(
                commitSucceeded = committed,
                isClipboardCandidate = candidate is ClipboardSuggestionCandidate,
            )
        ) {
            // The user explicitly chose this candidate (or auto-commit chose it on their
            // behalf). Reinforce its weight in the personal dictionary so it ranks higher
            // next time. Skipped in incognito.
            learnIfAllowed(candidate.text.toString())
        }
        return committed
    }

    private fun commitAutoCommitUndoCandidate(candidate: AutoCommitUndoSuggestionCandidate): Boolean {
        val committed = editorInstance.commitCompletion(candidate)
        if (committed) {
            nlpManager.rejectAcceptedAutoCommitFromUndo(candidate)
            // Undoing a correction says the typed word was meant, so learn it now. Only the
            // word: the chain still ends on the correction this replaced.
            resetLearnChain()
            learnWordIfAllowed(candidate.original, requireRepeat = false)
            candidate.sourceProvider?.let { sourceProvider ->
                scope.launch {
                    sourceProvider.notifySuggestionReverted(
                        subtype = subtypeManager.activeSubtype,
                        candidate = candidate,
                    )
                }
            }
        }
        return committed
    }

    private fun commitGlideAlternativeCandidate(candidate: GlideAlternativeSuggestionCandidate): Boolean {
        val committed = editorInstance.replaceCommittedGestureWord(
            range = candidate.range,
            expectedText = candidate.committed,
            replacementText = candidate.alternative,
        )
        if (committed) {
            nlpManager.consumeGlideAlternative(candidate)
            learnIfAllowed(candidate.alternative)
        }
        return committed
    }

    /** Returns whether the candidate was committed (and so learned by [commitCandidate]). */
    private fun commitAutoCommitCandidate(candidate: SuggestionCandidate): Boolean {
        val contentBeforeCommit = editorInstance.activeContent
        val committed = commitCandidate(candidate)
        if (committed) {
            nlpManager.rememberAcceptedAutoCommit(contentBeforeCommit, candidate)
        }
        return committed
    }

    private fun forgetLearnedWord(candidate: LearnedWordForgetSuggestionCandidate) {
        scope.launch {
            withContext(Dispatchers.IO) {
                DictionaryManager.default().forgetWord(candidate.word, candidate.locale)
            }
            dev.patrickgold.florisboard.ime.dictionary.PersonalBigramStore.get(appContext)
                .forget(candidate.word, candidate.locale)
            dev.patrickgold.florisboard.ime.dictionary.PersonalTrigramStore.get(appContext)
                .forget(candidate.word, candidate.locale)
            resetLearnChain()
            nlpManager.clearSuggestions()
        }
    }

    /**
     * Breaks the personal bigram/trigram learn chain. Called when word
     * adjacency can no longer be assumed: the active editor changed, or the
     * user is deleting back into / rewriting the previously learned word.
     * Editor changes call this from [scope]'s background collectors.
     */
    private fun resetLearnChain() {
        learnChain.reset()
    }

    /**
     * Auto-learn a freshly-committed word into the personal dictionary so frequently
     * typed words bubble up in suggestions over time. Skipped in incognito mode and
     * when the user has disabled the personal dictionary. Off-thread inside
     * DictionaryManager.
     *
     * ROADMAP §6 N7.2 — Also skipped on password / visible-password / web-password
     * fields *even when the host app forgets to set IME_FLAG_NO_PERSONALIZED_LEARNING*
     * (a depressingly common bug — see HeliBoard #2124, AnySoftKeyboard #1399).
     * Trusting only the incognito flag would bleed credentials into the user's
     * suggestions; the keyVariation check is the belt to incognito's suspenders.
     *
     * Words the user picked or glided are learned right away. The space key's literal word goes
     * through [learnTypedWordIfAllowed] instead.
     */
    private fun learnIfAllowed(rawWord: String) {
        if (!learnWordIfAllowed(rawWord, requireRepeat = false)) return
        learnNextWordChain(rawWord)
    }

    /**
     * Learns the word the space kept as typed: right away when a dictionary or the learned words
     * know it, and on its second sighting when it's new (see [DictionaryManager.learnWord]).
     * [known] is what the last suggestion pass found for it. A word typed faster than its pass, or
     * with suggestions off, gets looked up first. The next-word chain learns it either way.
     */
    private fun learnTypedWordIfAllowed(rawWord: String, known: Boolean?) {
        if (!mayLearn(rawWord)) return
        // Too short or not word-like to learn (see LearnedWordText), so there's nothing to look up.
        if (LearnedWordText.normalize(rawWord) == null) {
            learnNextWordChain(rawWord)
            return
        }
        val subtype = subtypeManager.activeSubtype
        if (known != null) {
            learnWordNow(rawWord, requireRepeat = !known, locale = subtype.primaryLocale)
        } else {
            scope.launch {
                val lookedUp = nlpManager.lookUpTypedWordKnown(rawWord, subtype)
                withContext(Dispatchers.Main) {
                    learnLookedUpWord(rawWord, lookedUp, subtype.primaryLocale)
                }
            }
        }
        learnNextWordChain(rawWord)
    }

    /**
     * Learns a word whose lookup finished after the space. The space's own pass may already be
     * out, so the chip goes onto the strip as shown. It's offered only while the cursor still sits
     * after the word, so a slow lookup can't replace a newer word's chip. If the keyboard hid
     * meanwhile, the hide already saved the pending words, so they're saved again now.
     */
    private fun learnLookedUpWord(rawWord: String, known: Boolean, locale: FlorisLocale) {
        val content = editorInstance.activeContent
        val cursorAfterWord = LearnedWordForgetOffer.endsWithWord(content.textBeforeSelection, rawWord)
        if (learnWordNow(rawWord, requireRepeat = !known, locale = locale, offerForget = cursorAfterWord)) {
            nlpManager.showForgetLearnedWordChip(content)
        }
        if (FlorisImeService.windowControllerOrNull()?.isWindowShown?.value != true) {
            DictionaryManager.default().flushPendingLearnedWords()
        }
    }

    private fun learnNextWordChain(rawWord: String) {
        val locale = subtypeManager.activeSubtype.primaryLocale
        if (prefs.suggestion.nextWordPrediction.get()) {
            val tag = locale.languageTag()
            val snapshot = learnChain.snapshot()
            val chain = snapshot.link
            if (chain != null && chain.localeTag == tag) {
                dev.patrickgold.florisboard.ime.dictionary.PersonalBigramStore.get(appContext)
                    .learn(chain.last, rawWord, locale)
                val prev2 = chain.prev
                if (prev2 != null) {
                    dev.patrickgold.florisboard.ime.dictionary.PersonalTrigramStore.get(appContext)
                        .learn(prev2, chain.last, rawWord, locale)
                }
            }
            // A reset from an editor change that lands meanwhile wins, so the next word isn't
            // paired with this one across the change.
            learnChain.advance(snapshot, rawWord, tag)
        }
    }

    /**
     * The word half of [learnIfAllowed], without the next-word chain. Returns `false` when the
     * privacy gate refused the word.
     */
    private fun learnWordIfAllowed(rawWord: String, requireRepeat: Boolean): Boolean {
        if (!mayLearn(rawWord)) return false
        learnWordNow(rawWord, requireRepeat, subtypeManager.activeSubtype.primaryLocale)
        return true
    }

    private fun mayLearn(rawWord: String): Boolean = SuggestionPrivacyPolicy.shouldLearnCommittedWord(
        rawWord = rawWord,
        isIncognitoMode = activeState.isIncognitoMode,
        keyVariation = activeState.keyVariation,
    )

    /**
     * Learns [rawWord] in [locale], past the privacy gate. A new word the space learned on its
     * second sighting gets the chip that forgets it, on the passes while the cursor stays after
     * it, unless [offerForget] is false; returns whether it got one. Dictionary words and picks
     * don't, so the chip never pushes out a prediction for a word the user meant.
     */
    private fun learnWordNow(
        rawWord: String,
        requireRepeat: Boolean,
        locale: FlorisLocale,
        offerForget: Boolean = true,
    ): Boolean {
        val overlay = dev.patrickgold.florisboard.ime.dictionary.UserDictionaryOverlay.get()
        val wasLearned = overlay.contains(rawWord, locale)
        DictionaryManager.default().learnWord(rawWord, locale, requireRepeat)
        if (!offerForget || !requireRepeat || wasLearned || !overlay.contains(rawWord, locale)) return false
        nlpManager.offerForgetLearnedWord(LearnedWordForgetSuggestionCandidate(word = rawWord, locale = locale))
        return true
    }

    fun commitGesture(word: String, alternatives: List<String> = emptyList()) {
        val cased = fixCase(word)
        val contentBeforeCommit = editorInstance.activeContent
        val committed = editorInstance.commitGesture(cased)
        if (committed && contentBeforeCommit.selection.isCursorMode && alternatives.isNotEmpty()) {
            val end = editorInstance.lastCommitPosition.pos
            val start = end - cased.length
            if (start >= 0 && end >= start) {
                nlpManager.rememberAcceptedGlideCommit(
                    committedText = cased,
                    alternatives = alternatives.map { fixCase(it) },
                    range = EditorRange(start, end),
                )
            }
        }
        learnIfAllowed(cased)
        announceForAccessibility(cased)
    }

    private fun announceForAccessibility(text: String) {
        accessibilityAnnouncement.value = text
    }

    fun replaceLastGestureWordForContext(expectedWord: String, replacementWord: String): Boolean {
        val casedReplacement = replacementWord.matchCaseOf(expectedWord)
        return editorInstance.replaceCurrentGestureWord(expectedWord, casedReplacement)
    }

    private fun String.matchCaseOf(source: String): String {
        val locale = subtypeManager.activeSubtype.primaryLocale.base
        return when {
            source.isNotBlank() && source.all { char -> !char.isLetter() || char.isUpperCase() } -> {
                uppercase(locale)
            }
            source.firstOrNull()?.isUpperCase() == true -> {
                replaceFirstChar { char -> char.uppercase(locale) }
            }
            else -> this
        }
    }

    /**
     * Changes a word to the current case.
     * eg if [KeyboardState.isUppercase] is true, abc -> ABC
     *    if [caps]     is true, abc -> Abc
     *    otherwise            , abc -> abc
     */
    fun fixCase(word: String): String {
        return when(activeState.inputShiftState) {
            InputShiftState.CAPS_LOCK -> {
                word.uppercase(subtypeManager.activeSubtype.primaryLocale)
            }
            InputShiftState.SHIFTED_MANUAL, InputShiftState.SHIFTED_AUTOMATIC -> {
                word.titlecase(subtypeManager.activeSubtype.primaryLocale)
            }
            else -> word
        }
    }

    /**
     * Handles [KeyCode] arrow and move events, behaves differently depending on text selection.
     */
    fun handleArrow(code: Int, count: Int = 1) = editorInstance.apply {
        val isShiftPressed = activeState.isManualSelectionMode || inputEventDispatcher.isPressed(KeyCode.SHIFT)
        val content = activeContent
        val selection = content.selection
        when (code) {
            KeyCode.ARROW_LEFT -> {
                if (!selection.isSelectionMode && activeState.isManualSelectionMode) {
                    activeState.isManualSelectionModeStart = true
                    activeState.isManualSelectionModeEnd = false
                }
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT, meta(shift = isShiftPressed), count)
            }
            KeyCode.ARROW_RIGHT -> {
                if (!selection.isSelectionMode && activeState.isManualSelectionMode) {
                    activeState.isManualSelectionModeStart = false
                    activeState.isManualSelectionModeEnd = true
                }
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT, meta(shift = isShiftPressed), count)
            }
            KeyCode.ARROW_UP -> {
                if (!selection.isSelectionMode && activeState.isManualSelectionMode) {
                    activeState.isManualSelectionModeStart = true
                    activeState.isManualSelectionModeEnd = false
                }
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_UP, meta(shift = isShiftPressed), count)
            }
            KeyCode.ARROW_DOWN -> {
                if (!selection.isSelectionMode && activeState.isManualSelectionMode) {
                    activeState.isManualSelectionModeStart = false
                    activeState.isManualSelectionModeEnd = true
                }
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN, meta(shift = isShiftPressed), count)
            }
            KeyCode.MOVE_START_OF_PAGE -> {
                if (!selection.isSelectionMode && activeState.isManualSelectionMode) {
                    activeState.isManualSelectionModeStart = true
                    activeState.isManualSelectionModeEnd = false
                }
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_UP, meta(alt = true, shift = isShiftPressed), count)
            }
            KeyCode.MOVE_END_OF_PAGE -> {
                if (!selection.isSelectionMode && activeState.isManualSelectionMode) {
                    activeState.isManualSelectionModeStart = false
                    activeState.isManualSelectionModeEnd = true
                }
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN, meta(alt = true, shift = isShiftPressed), count)
            }
            KeyCode.PAGE_UP -> {
                if (!selection.isSelectionMode && activeState.isManualSelectionMode) {
                    activeState.isManualSelectionModeStart = true
                    activeState.isManualSelectionModeEnd = false
                }
                sendDownUpKeyEvent(KeyEvent.KEYCODE_PAGE_UP, meta(shift = isShiftPressed), count)
            }
            KeyCode.PAGE_DOWN -> {
                if (!selection.isSelectionMode && activeState.isManualSelectionMode) {
                    activeState.isManualSelectionModeStart = false
                    activeState.isManualSelectionModeEnd = true
                }
                sendDownUpKeyEvent(KeyEvent.KEYCODE_PAGE_DOWN, meta(shift = isShiftPressed), count)
            }
            KeyCode.MOVE_START_OF_LINE -> {
                if (!selection.isSelectionMode && activeState.isManualSelectionMode) {
                    activeState.isManualSelectionModeStart = true
                    activeState.isManualSelectionModeEnd = false
                }
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT, meta(alt = true, shift = isShiftPressed), count)
            }
            KeyCode.MOVE_END_OF_LINE -> {
                if (!selection.isSelectionMode && activeState.isManualSelectionMode) {
                    activeState.isManualSelectionModeStart = false
                    activeState.isManualSelectionModeEnd = true
                }
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT, meta(alt = true, shift = isShiftPressed), count)
            }
        }
    }

    /**
     * Handles a [KeyCode.CLIPBOARD_SELECT] event.
     */
    private fun handleClipboardSelect() {
        val activeSelection = editorInstance.activeContent.selection
        activeState.isManualSelectionMode = if (activeSelection.isSelectionMode) {
            if (activeState.isManualSelectionMode && activeState.isManualSelectionModeStart) {
                editorInstance.setSelection(activeSelection.start, activeSelection.start)
            } else {
                editorInstance.setSelection(activeSelection.end, activeSelection.end)
            }
            false
        } else {
            !activeState.isManualSelectionMode
        }
    }

    private fun revertPreviouslyAcceptedCandidate() {
        // Deleting backwards rewrites the words the learn chain assumed were
        // final; keep the chain from pairing the next commit with stale text.
        resetLearnChain()
        nlpManager.rejectAcceptedAutoCommitOnBackspace(editorInstance.activeContent)?.let { original ->
            // Counts as a sighting of the typed word, so the space that keeps it when it's typed
            // again learns it. Only notes it: backing into a word never learns it by itself.
            if (mayLearn(original)) {
                DictionaryManager.default()
                    .noteTypedWordSighting(original, subtypeManager.activeSubtype.primaryLocale)
            }
        }
        editorInstance.phantomSpace.candidateForRevert?.let { candidateForRevert ->
            candidateForRevert.sourceProvider?.let { sourceProvider ->
                scope.launch {
                    sourceProvider.notifySuggestionReverted(
                        subtype = subtypeManager.activeSubtype,
                        candidate = candidateForRevert,
                    )
                }
            }
        }
    }

    /**
     * Handles a [KeyCode.DELETE] event.
     */
    private fun handleBackwardDelete(unit: OperationUnit) {
        if (inputEventDispatcher.isPressed(KeyCode.SHIFT)) {
            return handleForwardDelete(unit)
        }
        activeState.batchEdit {
            it.isManualSelectionMode = false
            it.isManualSelectionModeStart = false
            it.isManualSelectionModeEnd = false
        }
        revertPreviouslyAcceptedCandidate()
        editorInstance.deleteBackwards(unit)
    }

    /**
     * Handles a [KeyCode.FORWARD_DELETE] event.
     */
    private fun handleForwardDelete(unit: OperationUnit) {
        activeState.batchEdit {
            it.isManualSelectionMode = false
            it.isManualSelectionModeStart = false
            it.isManualSelectionModeEnd = false
        }
        revertPreviouslyAcceptedCandidate()
        editorInstance.deleteForwards(unit)
    }

    /**
     * Handles a [KeyCode.ENTER] event.
     */
    private fun handleEnter() {
        // Enter ends the word under the cursor like space does, so the last word of a message
        // or line is learned too.
        val typedWordBeforeEnter = editorInstance.activeContent.currentWordText
        SpacebarLearnPolicy.typedWordToLearn(typedWordBeforeEnter, candidateCommitted = false)?.let { word ->
            learnTypedWordIfAllowed(
                word,
                nlpManager.typedWordKnownState(word, subtypeManager.activeSubtype.primaryLocale),
            )
        }
        if (editorInstance.tryPerformEnterCommitRaw()) {
            return
        }
        when (val action = enterEditorAction()) {
            null -> editorInstance.performEnter()
            else -> editorInstance.performEnterAction(action)
        }
        // A new line or a sent message starts a new sentence.
        resetLearnChain()
    }

    /** The action Enter runs in the current field, or null when it types a newline. */
    private fun enterEditorAction(): ImeOptions.Action? {
        val info = editorInstance.activeInfo
        return EnterKeyPolicy.editorAction(
            action = info.imeOptions.action,
            flagNoEnterAction = info.imeOptions.flagNoEnterAction,
            isMultiLine = info.inputAttributes.flagTextMultiLine,
            isShiftPressed = inputEventDispatcher.isPressed(KeyCode.SHIFT),
        )
    }

    /**
     * True when a hardware Enter would only type a newline in a multi-line field, which the app's
     * own key handling does the same way. A single-line field reads a raw Enter as a "next field"
     * or IME_NULL instead, so it stays with the keyboard, and so does an Enter that commits Chinese
     * input as typed. The word the keyboard marks as composing doesn't count: the app's newline
     * lands after it, the same as a space the app types.
     */
    private fun hardwareEnterTypesNewline(): Boolean {
        return editorInstance.activeInfo.inputAttributes.flagTextMultiLine &&
            !editorInstance.enterCommitsComposingRaw() &&
            enterEditorAction() == null
    }

    /**
     * Handles a [KeyCode.LANGUAGE_SWITCH] event. Also handles if the language switch should cycle
     * FlorisBoard internal or system-wide.
     */
    private fun handleLanguageSwitch() {
        when (prefs.keyboard.utilityKeyAction.get()) {
            UtilityKeyAction.DYNAMIC_SWITCH_LANGUAGE_EMOJIS,
            UtilityKeyAction.SWITCH_LANGUAGE -> subtypeManager.switchToNextSubtype()
            else -> FlorisImeService.switchToNextInputMethod()
        }
    }

    /**
     * Handles a [KeyCode.SHIFT] down event.
     */
    private fun handleShiftDown(data: KeyData) {
        activeState.inputShiftState = ShiftStateMachine.onShiftDown(
            current = activeState.inputShiftState,
            behavior = prefs.keyboard.capitalizationBehavior.get(),
            isConsecutiveDown = inputEventDispatcher.isConsecutiveDown(data),
        )
    }

    /**
     * Handles a [KeyCode.SHIFT] up event. When text is selected and the shift
     * press was not part of a chord (no other key pressed), cycles the selected
     * text through lowercase → Title Case → UPPERCASE instead of changing the
     * shift state.
     */
    private fun handleShiftUp(data: KeyData) {
        if (!inputEventDispatcher.isAnyPressed()
            && inputEventDispatcher.isUninterruptedEventSequence(data)
            && editorInstance.activeContent.selection.isSelectionMode
        ) {
            val locale = subtypeManager.activeSubtype.primaryLocale
            if (editorInstance.cycleSelectedTextCase(locale.base)) return
        }
        activeState.inputShiftState = ShiftStateMachine.onShiftUp(
            current = activeState.inputShiftState,
            isAnyKeyPressed = inputEventDispatcher.isAnyPressed(),
            isUninterruptedSequence = inputEventDispatcher.isUninterruptedEventSequence(data),
        )
    }

    /**
     * Handles a [KeyCode.CAPS_LOCK] event.
     */
    private fun handleCapsLock() {
        activeState.inputShiftState = InputShiftState.CAPS_LOCK
    }

    /**
     * Handles a [KeyCode.SHIFT] cancel event.
     */
    private fun handleShiftCancel() {
        activeState.inputShiftState = InputShiftState.UNSHIFTED
    }

    private fun tryExpandSnippet() {
        val content = editorInstance.activeContent
        val textBefore = content.textBeforeSelection
        if (textBefore.isEmpty()) return
        val isSensitive = activeState.isIncognitoMode ||
            activeState.keyVariation == KeyVariation.PASSWORD
        val match = SnippetExpansionPolicy.findMatch(
            textBeforeCursor = textBefore,
            snippets = snippetManager.snippets.value,
            isSensitiveField = isSensitive,
        ) ?: return
        editorInstance.expandSnippet(match.triggerLength, match.replacement)
    }

    /**
     * Handles a hardware [KeyEvent.KEYCODE_SPACE] event. Same as [handleSpace],
     * but skips handling changing to characters keyboard and double space periods.
     */
    fun handleHardwareKeyboardSpace() {
        val typedWordBeforeCommit = editorInstance.activeContent.currentWordText
        val typedWordKnown = nlpManager.typedWordKnownState(
            typedWordBeforeCommit,
            subtypeManager.activeSubtype.primaryLocale,
        )
        val candidate = nlpManager.getSpacebarCandidate()
        val suppressPlainSpace = candidate == null && nlpManager.shouldSuppressPlainSpaceForPrediction()
        val candidateCommitted = candidate?.let { commitAutoCommitCandidate(it) } ?: false
        SpacebarLearnPolicy.typedWordToLearn(typedWordBeforeCommit, candidateCommitted)
            ?.let { learnTypedWordIfAllowed(it, typedWordKnown) }
        if (suppressPlainSpace) {
            return
        }
        // Skip handling changing to characters keyboard and double space periods
        if (shouldCommitPlainSpaceAfterSpacebar(candidate, suppressPlainSpace)) {
            editorInstance.commitText(KeyCode.SPACE.toChar().toString())
        }
    }

    /**
     * Handles a [KeyCode.SPACE] event. Also handles the auto-correction of two space taps if
     * enabled by the user.
     */
    private fun handleSpace(data: KeyData) {
        // Snapshot the word the user actually typed *before* any auto-correct candidate
        // overwrites it. A committed candidate is learned by the commit itself; otherwise
        // the space learns the literal typed word, right away when a dictionary knows it and on
        // its second sighting when it's new. Whether it's known comes from the suggestion pass
        // that ranked it, read before the commit starts the next one.
        val typedWordBeforeCommit = editorInstance.activeContent.currentWordText
        val typedWordKnown = nlpManager.typedWordKnownState(
            typedWordBeforeCommit,
            subtypeManager.activeSubtype.primaryLocale,
        )
        val candidate = nlpManager.getSpacebarCandidate()
        val suppressPlainSpace = candidate == null && nlpManager.shouldSuppressPlainSpaceForPrediction()
        val candidateCommitted = candidate?.let { commitAutoCommitCandidate(it) } ?: false
        SpacebarLearnPolicy.typedWordToLearn(typedWordBeforeCommit, candidateCommitted)
            ?.let { learnTypedWordIfAllowed(it, typedWordKnown) }
        if (prefs.keyboard.spaceBarSwitchesToCharacters.get()) {
            when (activeState.keyboardMode) {
                KeyboardMode.NUMERIC_ADVANCED,
                KeyboardMode.SYMBOLS,
                KeyboardMode.SYMBOLS2 -> {
                    transitionToKeyboardMode(KeyboardMode.CHARACTERS)
                }
                else -> { /* Do nothing */ }
            }
        }
        if (suppressPlainSpace) {
            return
        }
        if (prefs.correction.doubleSpacePeriod.get()) {
            if (inputEventDispatcher.isConsecutiveUp(data)) {
                val text = editorInstance.run { activeContent.getTextBeforeCursor(2) }
                if (EditorInputBehaviorPolicy.shouldConvertDoubleSpaceToPeriod(text)) {
                    editorInstance.deleteBackwards(OperationUnit.CHARACTERS)
                    editorInstance.commitText(". ")
                    return
                }
            }
        }
        if (shouldCommitPlainSpaceAfterSpacebar(candidate, suppressPlainSpace)) {
            editorInstance.commitText(KeyCode.SPACE.toChar().toString())
        }
    }

    private fun shouldCommitPlainSpaceAfterSpacebar(
        candidate: SuggestionCandidate?,
        suppressPlainSpace: Boolean,
    ): Boolean {
        return CandidateCommitSideEffectPolicy.shouldCommitPlainSpaceAfterSpacebar(
            candidate = candidate,
            suppressPlainSpaceForPrediction = suppressPlainSpace,
            supportsAutoSpace = subtypeManager.activeSubtype.primaryLocale.supportsAutoSpace,
        )
    }

    /**
     * Handles a [KeyCode.TOGGLE_INCOGNITO_MODE] event.
     */
    private suspend fun handleToggleIncognitoMode() {
        prefs.suggestion.forceIncognitoModeFromDynamic.set(!prefs.suggestion.forceIncognitoModeFromDynamic.get())
        val newState = !activeState.isIncognitoMode
        activeState.isIncognitoMode = newState
        resetUiModeAndHistory()
        incognitoModeChangedListener?.let { listener ->
            withContext(Dispatchers.Main.immediate) {
                listener(newState)
            }
        }
        lastToastReference.get()?.cancel()
        lastToastReference = WeakReference(
            if (newState) {
                appContext.showLongToast(
                    R.string.incognito_mode__toast_after_enabled,
                    "app_name" to appContext.getString(R.string.app_name),
                )
            } else {
                appContext.showLongToast(
                    R.string.incognito_mode__toast_after_disabled,
                    "app_name" to appContext.getString(R.string.app_name),
                )
            }
        )
    }

    /**
     * Handles a [KeyCode.TOGGLE_AUTOCORRECT] event.
     *
     * Flips the live `prefs.correction.autoCorrect` preference. NlpManager's
     * candidate-commit + spacebar selection paths read the same preference on
     * each suggestion request (`isAutoCorrectEnabled = prefs.correction
     * .autoCorrect.get()`), so the toggle takes effect on the next keystroke
     * without any extra plumbing. The user-facing surface is the existing
     * Settings → Typing → "Auto-correct" switch — this QuickAction is a
     * keyboard-side shortcut to the same setting.
     */
    private suspend fun handleToggleAutocorrect() {
        val newState = !prefs.correction.autoCorrect.get()
        prefs.correction.autoCorrect.set(newState)
        lastToastReference.get()?.cancel()
        lastToastReference = WeakReference(
            if (newState) {
                appContext.showLongToast(R.string.autocorrect_toggle__toast_after_enabled)
            } else {
                appContext.showLongToast(R.string.autocorrect_toggle__toast_after_disabled)
            }
        )
    }

    /**
     * Handles a [KeyCode.KANA_SWITCHER] event
     */
    private fun handleKanaSwitch() {
        activeState.batchEdit {
            it.isKanaKata = !it.isKanaKata
            it.isCharHalfWidth = false
        }
    }

    /**
     * Handles a [KeyCode.KANA_HIRA] event
     */
    private fun handleKanaHira() {
        activeState.batchEdit {
            it.isKanaKata = false
            it.isCharHalfWidth = false
        }
    }

    /**
     * Handles a [KeyCode.KANA_KATA] event
     */
    private fun handleKanaKata() {
        activeState.batchEdit {
            it.isKanaKata = true
            it.isCharHalfWidth = false
        }
    }

    /**
     * Handles a [KeyCode.KANA_HALF_KATA] event
     */
    private fun handleKanaHalfKata() {
        activeState.batchEdit {
            it.isKanaKata = true
            it.isCharHalfWidth = true
        }
    }

    /**
     * Handles a [KeyCode.CHAR_WIDTH_SWITCHER] event
     */
    private fun handleCharWidthSwitch() {
        activeState.isCharHalfWidth = !activeState.isCharHalfWidth
    }

    /**
     * Handles a [KeyCode.CHAR_WIDTH_SWITCHER] event
     */
    private fun handleCharWidthFull() {
        activeState.isCharHalfWidth = false
    }

    /**
     * Handles a [KeyCode.CHAR_WIDTH_SWITCHER] event
     */
    private fun handleCharWidthHalf() {
        activeState.isCharHalfWidth = true
    }

    override fun onInputKeyDown(data: KeyData) {
        if (isIgnoredByTextEntry(data)) return
        val windowController = FlorisImeService.windowControllerOrNull()
        windowController?.editor?.disableIfNoGestureInProgress()
        when (data.code) {
            KeyCode.ARROW_DOWN,
            KeyCode.ARROW_LEFT,
            KeyCode.ARROW_RIGHT,
            KeyCode.ARROW_UP,
            KeyCode.MOVE_START_OF_PAGE,
            KeyCode.MOVE_END_OF_PAGE,
            KeyCode.PAGE_UP,
            KeyCode.PAGE_DOWN,
            KeyCode.MOVE_START_OF_LINE,
            KeyCode.MOVE_END_OF_LINE -> {
                editorInstance.massSelection.begin()
            }
            KeyCode.SHIFT -> handleShiftDown(data)
        }
    }

    override fun onInputKeyUp(data: KeyData) = activeState.batchEdit {
        if (textEntry.isActive && routeKeyToTextEntry(data)) return@batchEdit
        val windowController = FlorisImeService.windowControllerOrNull() ?: return@batchEdit
        when (data.code) {
            KeyCode.ARROW_DOWN,
            KeyCode.ARROW_LEFT,
            KeyCode.ARROW_RIGHT,
            KeyCode.ARROW_UP,
            KeyCode.MOVE_START_OF_PAGE,
            KeyCode.MOVE_END_OF_PAGE,
            KeyCode.PAGE_UP,
            KeyCode.PAGE_DOWN,
            KeyCode.MOVE_START_OF_LINE,
            KeyCode.MOVE_END_OF_LINE -> {
                editorInstance.massSelection.end()
                handleArrow(data.code)
            }
            KeyCode.CAPS_LOCK -> handleCapsLock()
            KeyCode.CHAR_WIDTH_SWITCHER -> handleCharWidthSwitch()
            KeyCode.CHAR_WIDTH_FULL -> handleCharWidthFull()
            KeyCode.CHAR_WIDTH_HALF -> handleCharWidthHalf()
            KeyCode.CLIPBOARD_CUT -> editorInstance.performClipboardCut()
            KeyCode.CLIPBOARD_COPY -> editorInstance.performClipboardCopy()
            KeyCode.CLIPBOARD_PASTE -> editorInstance.performClipboardPaste()
            KeyCode.CLIPBOARD_SELECT -> handleClipboardSelect()
            KeyCode.CLIPBOARD_SELECT_ALL -> editorInstance.performClipboardSelectAll()
            KeyCode.CLIPBOARD_CLEAR_HISTORY -> scope.launch {
                if (!clipboardManager.clearHistory().succeeded) {
                    appContext.postShortToast(R.string.clipboard__delete_failed)
                }
            }
            KeyCode.CLIPBOARD_CLEAR_FULL_HISTORY -> scope.launch {
                if (!clipboardManager.clearFullHistory().succeeded) {
                    appContext.postShortToast(R.string.clipboard__delete_failed)
                }
            }
            KeyCode.CLIPBOARD_CLEAR_PRIMARY_CLIP -> {
                val clip = clipboardManager.primaryClip
                val alsoFromHistory = clip != null && prefs.clipboard.clearPrimaryClipAffectsHistoryIfUnpinned.get()
                clipboardManager.updatePrimaryClip(null)
                scope.launch {
                    // Says it's cleared only once an unpinned history copy is confirmed gone too. A
                    // pinned copy stays, as the setting says.
                    val deleted = clip == null || !alsoFromHistory ||
                        clipboardManager.deleteClip(clip, onlyIfUnpinned = true).succeeded
                    appContext.postShortToast(
                        if (deleted) R.string.clipboard__cleared_primary_clip else R.string.clipboard__delete_failed,
                    )
                }
            }
            KeyCode.TOGGLE_FLOATING_WINDOW -> windowController.actions.toggleFloatingWindow()
            KeyCode.TOGGLE_COMPACT_LAYOUT -> windowController.actions.toggleCompactLayout()
            KeyCode.COMPACT_LAYOUT_TO_LEFT -> windowController.actions.compactLayoutToLeft()
            KeyCode.COMPACT_LAYOUT_TO_RIGHT -> windowController.actions.compactLayoutToRight()
            KeyCode.TOGGLE_RESIZE_MODE -> windowController.editor.toggleEnabled()
            KeyCode.DELETE -> handleBackwardDelete(OperationUnit.CHARACTERS)
            KeyCode.DELETE_WORD -> handleBackwardDelete(OperationUnit.WORDS)
            KeyCode.ENTER -> handleEnter()
            KeyCode.FORWARD_DELETE -> handleForwardDelete(OperationUnit.CHARACTERS)
            KeyCode.FORWARD_DELETE_WORD -> handleForwardDelete(OperationUnit.WORDS)
            KeyCode.IME_SHOW_UI -> FlorisImeService.showUi()
            KeyCode.IME_HIDE_UI -> FlorisImeService.hideUi()
            KeyCode.IME_PREV_SUBTYPE -> subtypeManager.switchToPrevSubtype()
            KeyCode.IME_NEXT_SUBTYPE -> subtypeManager.switchToNextSubtype()
            KeyCode.IME_UI_MODE_TEXT -> transitionToImeUiMode(ImeUiMode.TEXT)
            KeyCode.IME_UI_MODE_MEDIA -> transitionToImeUiMode(ImeUiMode.MEDIA)
            KeyCode.IME_UI_MODE_CLIPBOARD -> transitionToImeUiMode(ImeUiMode.CLIPBOARD)
            KeyCode.VOICE_INPUT -> FlorisImeService.switchToVoiceInputMethod()
            KeyCode.KANA_SWITCHER -> handleKanaSwitch()
            KeyCode.KANA_HIRA -> handleKanaHira()
            KeyCode.KANA_KATA -> handleKanaKata()
            KeyCode.KANA_HALF_KATA -> handleKanaHalfKata()
            KeyCode.LANGUAGE_SWITCH -> handleLanguageSwitch()
            KeyCode.REDO -> editorInstance.performRedo()
            KeyCode.SETTINGS -> FlorisImeService.launchSettings()
            KeyCode.SHIFT -> handleShiftUp(data)
            KeyCode.SPACE -> handleSpace(data)
            KeyCode.SYSTEM_INPUT_METHOD_PICKER -> InputMethodUtils.showImePicker(appContext)
            KeyCode.SHOW_SUBTYPE_PICKER -> {
                appContext.keyboardManager.value.activeState.isSubtypeSelectionVisible = true
            }
            KeyCode.SYSTEM_PREV_INPUT_METHOD -> FlorisImeService.switchToPrevInputMethod()
            KeyCode.SYSTEM_NEXT_INPUT_METHOD -> FlorisImeService.switchToNextInputMethod()
            KeyCode.TOGGLE_SMARTBAR_VISIBILITY -> scope.launch {
                prefs.smartbar.enabled.let { it.set(!it.get()) }
            }
            KeyCode.TOGGLE_ACTIONS_OVERFLOW -> {
                activeState.isActionsOverflowVisible = !activeState.isActionsOverflowVisible
            }
            KeyCode.TOGGLE_ACTIONS_EDITOR -> {
                activeState.isActionsEditorVisible = !activeState.isActionsEditorVisible
            }
            KeyCode.TOGGLE_INCOGNITO_MODE -> scope.launch { handleToggleIncognitoMode() }
            KeyCode.TOGGLE_AUTOCORRECT -> scope.launch { handleToggleAutocorrect() }
            KeyCode.UNDO -> editorInstance.performUndo()
            KeyCode.VIEW_CHARACTERS -> transitionToKeyboardMode(KeyboardMode.CHARACTERS)
            KeyCode.VIEW_NUMERIC -> transitionToKeyboardMode(KeyboardMode.NUMERIC)
            KeyCode.VIEW_NUMERIC_ADVANCED -> transitionToKeyboardMode(KeyboardMode.NUMERIC_ADVANCED)
            KeyCode.VIEW_PHONE -> transitionToKeyboardMode(KeyboardMode.PHONE)
            KeyCode.VIEW_PHONE2 -> transitionToKeyboardMode(KeyboardMode.PHONE2)
            KeyCode.VIEW_SYMBOLS -> transitionToKeyboardMode(KeyboardMode.SYMBOLS)
            KeyCode.VIEW_SYMBOLS2 -> transitionToKeyboardMode(KeyboardMode.SYMBOLS2)
            else -> {
                if (activeState.imeUiMode == ImeUiMode.MEDIA) {
                    val text = data.asString(isForDisplay = false)
                    if (shouldFlushAutoCommitBeforeCommit(data, text)) {
                        nlpManager.getAutoCommitCandidate()?.let { commitAutoCommitCandidate(it) }
                    }
                    editorInstance.commitText(text)
                    return@batchEdit
                }
                when (activeState.keyboardMode) {
                    KeyboardMode.NUMERIC,
                    KeyboardMode.NUMERIC_ADVANCED,
                    KeyboardMode.PHONE,
                    KeyboardMode.PHONE2 -> {
                        when (data.type) {
                            KeyType.CHARACTER,
                            KeyType.NUMERIC -> {
                                val text = data.asString(isForDisplay = false)
                                editorInstance.commitText(text)
                            }
                            else -> when (data.code) {
                                KeyCode.PHONE_PAUSE,
                                KeyCode.PHONE_WAIT -> {
                                    val text = data.asString(isForDisplay = false)
                                    editorInstance.commitText(text)
                                }
                            }
                        }
                        return@batchEdit
                    }
                    else -> when (data.type) {
                        KeyType.CHARACTER, KeyType.NUMERIC ->{
                            val text = data.asString(isForDisplay = false)
                            if (shouldFlushAutoCommitBeforeCommit(data, text)) {
                                commitAutoCommitAndLearnBefore(text)
                            }
                            editorInstance.commitChar(text)
                            if (WordBoundaryLearnPolicy.endsSentence(text)) resetLearnChain()

                            // Matrix #32 — quote / speech-mark auto-close. Insert the matching closer and move
                            // the cursor between the two so the user can keep typing inside the quoted region.
                            // The gate suppresses on sensitive fields (password / URI / email), on mid-word
                            // contexts, when the next char is already the closer (avoid double-up), and on
                            // single-quote apostrophe / foot-shorthand contexts.
                            val content = editorInstance.activeContent
                            val closer = QuoteAutoCloseGate.closerFor(
                                typedChar = text,
                                precedingText = content.textBeforeSelection,
                                followingText = content.textAfterSelection,
                                variation = activeState.keyVariation,
                                autoCloseEnabled = prefs.keyboard.quoteAutoCloseEnabled.get(),
                            )
                            if (closer != null) {
                                if (editorInstance.commitText(closer)) {
                                    val afterCloser = editorInstance.activeContent.selection
                                    val cursor = afterCloser.end - closer.length
                                    editorInstance.setSelection(cursor, cursor)
                                }
                            }

                            // N15.2 Gboard parity — after committing the apostrophe from a symbols
                            // panel, auto-flip back to the letter keyboard so contractions
                            // (e.g. "don't", "I'm") finish without a manual mode switch.
                            if (ApostropheReturnGate.shouldReturnToCharacters(
                                    committedText = text,
                                    currentMode = activeState.keyboardMode,
                                    autoReturnEnabled = prefs.keyboard.autoReturnAfterApostrophe.get(),
                                )) {
                                transitionToKeyboardMode(KeyboardMode.CHARACTERS)
                            }

                            tryExpandSnippet()

                            // Reset SHIFTED_AUTOMATIC after it's been applied to a character
                            if (activeState.inputShiftState == InputShiftState.SHIFTED_AUTOMATIC &&
                                UCharacter.isUAlphabetic(UCharacter.codePointAt(text, 0))) {
                                activeState.inputShiftState = InputShiftState.UNSHIFTED
                            }
                        }
                        else -> {
                            flogError(LogTopic.KEY_EVENTS) { "Received unknown key: $data" }
                        }
                    }
                }
                if (activeState.inputShiftState != InputShiftState.CAPS_LOCK && 
                    activeState.inputShiftState != InputShiftState.SHIFTED_AUTOMATIC &&
                    !inputEventDispatcher.isPressed(KeyCode.SHIFT)) {
                    activeState.inputShiftState = InputShiftState.UNSHIFTED
                }
            }
        }
    }

    /**
     * Commits the pending autocorrection before the non-letter [text] is typed. When [text] ends
     * the word (see [WordBoundaryLearnPolicy]) and nothing was autocorrected, the word as typed is
     * learned, the same way the space key learns it.
     */
    private fun commitAutoCommitAndLearnBefore(text: String) {
        if (!WordBoundaryLearnPolicy.endsWord(text)) {
            nlpManager.getAutoCommitCandidate()?.let { commitAutoCommitCandidate(it) }
            return
        }
        val typedWordBeforeCommit = editorInstance.activeContent.currentWordText
        val typedWordKnown = nlpManager.typedWordKnownState(
            typedWordBeforeCommit,
            subtypeManager.activeSubtype.primaryLocale,
        )
        val candidateCommitted = nlpManager.getAutoCommitCandidate()?.let { commitAutoCommitCandidate(it) } ?: false
        SpacebarLearnPolicy.typedWordToLearn(typedWordBeforeCommit, candidateCommitted)
            ?.let { learnTypedWordIfAllowed(it, typedWordKnown) }
    }

    private fun shouldFlushAutoCommitBeforeCommit(data: KeyData, text: String): Boolean {
        return KeyboardAutoCommitFlushPolicy.shouldFlushBeforeCommit(
            imeUiMode = activeState.imeUiMode,
            keyboardMode = activeState.keyboardMode,
            keyType = data.type,
            text = text,
            isFirstCodePointAlphabetic = text.isNotEmpty() &&
                UCharacter.isUAlphabetic(UCharacter.codePointAt(text, 0)),
        )
    }

    override fun onInputKeyCancel(data: KeyData) {
        when (data.code) {
            KeyCode.ARROW_DOWN,
            KeyCode.ARROW_LEFT,
            KeyCode.ARROW_RIGHT,
            KeyCode.ARROW_UP,
            KeyCode.MOVE_START_OF_PAGE,
            KeyCode.MOVE_END_OF_PAGE,
            KeyCode.PAGE_UP,
            KeyCode.PAGE_DOWN,
            KeyCode.MOVE_START_OF_LINE,
            KeyCode.MOVE_END_OF_LINE -> {
                editorInstance.massSelection.end()
            }
            KeyCode.SHIFT -> handleShiftCancel()
        }
    }

    override fun onInputKeyRepeat(data: KeyData) {
        if (isIgnoredByTextEntry(data)) return
        FlorisImeService.inputFeedbackController()?.keyRepeatedAction(data)
        when (data.code) {
            KeyCode.ARROW_DOWN,
            KeyCode.ARROW_LEFT,
            KeyCode.ARROW_RIGHT,
            KeyCode.ARROW_UP,
            KeyCode.MOVE_START_OF_PAGE,
            KeyCode.MOVE_END_OF_PAGE,
            KeyCode.MOVE_START_OF_LINE,
            KeyCode.MOVE_END_OF_LINE -> handleArrow(data.code)
            else -> onInputKeyUp(data)
        }
    }

    private fun reevaluateDebugFlags() {
        val devtoolsEnabled = prefs.devtools.enabled.get()
        activeState.batchEdit {
            activeState.debugShowDragAndDropHelpers = devtoolsEnabled && prefs.devtools.showDragAndDropHelpers.get()
        }
    }

    fun setHardwareKeyboardLayoutForDevice(deviceId: Int, layout: HardwareKeyboardLayout) {
        hardwareKeyboardRuntimeMapper.setLayoutForDevice(deviceId, layout)
    }

    fun clearHardwareKeyboardLayoutForDevice(deviceId: Int) {
        hardwareKeyboardRuntimeMapper.clearLayoutForDevice(deviceId)
    }

    fun onHardwareKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        hardwareKeyboardRuntimeMapper.pruneDetachedLayouts()
        val mappedKey = hardwareKeyboardRuntimeMapper.map(event)
        if (textEntry.isActive) {
            when (keyCode) {
                KeyEvent.KEYCODE_DEL -> return textEntry.deleteBackward()
                // Back closes the entry, not the keyboard.
                KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_ESCAPE -> {
                    finishTextEntry(submitted = false)
                    return true
                }
            }
        }
        val hasImportedLayout = event != null && hardwareKeyboardRuntimeMapper.layoutForDevice(event.deviceId) != null
        return when (val action = HardwareKeyboardInputPolicy.keyDownAction(
            keyCode = keyCode,
            mappedKey = mappedKey,
            isMappedKeyAlphabetic = mappedKey?.let { UCharacter.isUAlphabetic(it.codePoint) } ?: false,
            // Only a space and Enter look at it, and the keyboard's own text entry has no app behind
            // it. A call with no event has nothing the app could be handed.
            isEditorBehindApp = event != null &&
                (keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_ENTER) &&
                !textEntry.isActive && editorInstance.isContentBehindEditor(),
            isEnterNewline = keyCode == KeyEvent.KEYCODE_ENTER && !textEntry.isActive && hardwareEnterTypesNewline(),
            hasImportedLayout = hasImportedLayout,
            hasCommandModifier = event != null && (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed),
        )) {
            is HardwareKeyboardKeyDownAction.CommitMappedText -> {
                if (textEntry.isActive) {
                    return textEntry.type(action.text)
                }
                // A burst right after a tap gets here before the tap's content is published.
                editorInstance.settlePendingContent()
                if (action.shouldFlushAutoCommitCandidate) {
                    commitAutoCommitAndLearnBefore(action.text)
                }
                val committed = editorInstance.commitChar(action.text)
                if (WordBoundaryLearnPolicy.endsSentence(action.text)) resetLearnChain()
                committed
            }
            HardwareKeyboardKeyDownAction.HandleSpace -> {
                if (textEntry.isActive) {
                    textEntry.type(" ")
                } else {
                    editorInstance.settlePendingContent()
                    handleHardwareKeyboardSpace()
                    // The app may be holding this burst's next keys. It runs the space and any
                    // correction first.
                    if (event != null) editorInstance.awaitAppEdits()
                }
                true
            }
            HardwareKeyboardKeyDownAction.HandleEnter -> {
                if (textEntry.isActive) {
                    finishTextEntry(submitted = true)
                } else {
                    editorInstance.settlePendingContent()
                    handleEnter()
                    if (event != null) editorInstance.awaitAppEdits()
                }
                true
            }
            HardwareKeyboardKeyDownAction.HandleShiftDown -> {
                inputEventDispatcher.sendDown(TextKeyData.SHIFT)
                true
            }
            HardwareKeyboardKeyDownAction.PassThrough -> {
                // The app inserts a printable key itself: no layout is mapped for this keyboard, or
                // it's a space or a newline Enter after keys the app hasn't reported yet. Tell the
                // editor where that leaves the cursor, or the move reads as a jump. A key that moves
                // the cursor (an arrow, Home, End, a Backspace) or may (Tab, a paste) gets noted too,
                // so the keys the app types after it don't read as jumps. With an imported layout the
                // keyboard types the letters itself, so there's nothing after it to keep tracked.
                // A numpad Enter types a newline where the keyboard's Enter would.
                val typesNewline = keyCode == KeyEvent.KEYCODE_ENTER ||
                    (keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER && hardwareEnterTypesNewline())
                if (event != null) {
                    val length = if (typesNewline) {
                        // A newline is a control char, which the general rule counts as nothing.
                        1
                    } else {
                        HardwareKeyboardInputPolicy.passThroughInsertLength(
                            unicodeChar = event.unicodeChar,
                            isCtrlPressed = event.isCtrlPressed,
                            isAltPressed = event.isAltPressed,
                            isMetaPressed = event.isMetaPressed,
                        )
                    }
                    if (length > 0) {
                        val codePoint = if (typesNewline) '\n'.code else event.unicodeChar
                        editorInstance.expectAppInsert(length, codePoint)
                    } else if (!hasImportedLayout) {
                        expectAppMove(keyCode, event)
                    }
                }
                // A space, newline or tab the app types ends a word the keyboard never saw whole, so
                // it isn't learned, and the next word that is must not be learned as following the
                // one before.
                if (keyCode == KeyEvent.KEYCODE_SPACE || typesNewline || keyCode == KeyEvent.KEYCODE_TAB) {
                    resetLearnChain()
                }
                false
            }
        }
    }

    /** Tells the editor where a key the app handles itself and that types nothing leaves the cursor. */
    private fun expectAppMove(keyCode: Int, event: KeyEvent) {
        if (HardwareKeyboardInputPolicy.passThroughStepsBack(
                keyCode = keyCode,
                isShiftPressed = event.isShiftPressed,
                isCtrlPressed = event.isCtrlPressed,
                isAltPressed = event.isAltPressed,
                isMetaPressed = event.isMetaPressed,
            )
        ) {
            editorInstance.expectAppStepBack(wholeCluster = keyCode == KeyEvent.KEYCODE_DPAD_LEFT)
            return
        }
        val isMultiLine = editorInstance.activeInfo.inputAttributes.flagTextMultiLine
        when (HardwareKeyboardInputPolicy.passThroughMove(
            keyCode = keyCode,
            isShiftPressed = event.isShiftPressed,
            isCtrlPressed = event.isCtrlPressed,
            isAltPressed = event.isAltPressed,
            isMetaPressed = event.isMetaPressed,
            isMultiLine = isMultiLine,
        )) {
            PassThroughMove.None -> {}
            PassThroughMove.Right -> editorInstance.expectAppStepForward()
            PassThroughMove.LineStart -> editorInstance.expectAppLineEdge(toEnd = false)
            PassThroughMove.LineEnd -> editorInstance.expectAppLineEdge(toEnd = true)
            PassThroughMove.Tab -> editorInstance.expectAppTab(multiLine = isMultiLine)
            PassThroughMove.Unpredictable -> editorInstance.expectAppUnpredictable()
        }
    }

    fun onHardwareKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return when (HardwareKeyboardInputPolicy.keyUpAction(keyCode)) {
            HardwareKeyboardKeyUpAction.HandleShiftUp -> {
                inputEventDispatcher.sendUp(TextKeyData.SHIFT)
                true
            }
            HardwareKeyboardKeyUpAction.PassThrough -> false
        }
    }

    inner class KeyboardManagerResources {
        val composers = MutableStateFlow<Map<ExtensionComponentName, Composer>>(emptyMap())
        val currencySets = MutableStateFlow<Map<ExtensionComponentName, CurrencySet>>(emptyMap())
        val layouts = MutableStateFlow<Map<LayoutType, Map<ExtensionComponentName, LayoutArrangementComponent>>>(emptyMap())
        val popupMappings = MutableStateFlow<Map<ExtensionComponentName, PopupMappingComponent>>(emptyMap())
        val punctuationRules = MutableStateFlow<Map<ExtensionComponentName, PunctuationRule>>(emptyMap())
        val subtypePresets = MutableStateFlow<List<SubtypePreset>>(emptyList())

        val anyChangedVersion = MutableStateFlow(0)

        init {
            extensionManager.keyboardExtensions.collectIn(scope) { keyboardExtensions ->
                parseKeyboardExtensions(keyboardExtensions)
            }
        }

        private fun parseKeyboardExtensions(keyboardExtensions: List<KeyboardExtension>) {
            val localComposers = mutableMapOf<ExtensionComponentName, Composer>()
            val localCurrencySets = mutableMapOf<ExtensionComponentName, CurrencySet>()
            val localLayouts = mutableMapOf<LayoutType, MutableMap<ExtensionComponentName, LayoutArrangementComponent>>()
            val localPopupMappings = mutableMapOf<ExtensionComponentName, PopupMappingComponent>()
            val localPunctuationRules = mutableMapOf<ExtensionComponentName, PunctuationRule>()
            val localSubtypePresets = mutableListOf<SubtypePreset>()
            for (layoutType in LayoutType.entries) {
                localLayouts[layoutType] = mutableMapOf()
            }
            for (keyboardExtension in keyboardExtensions) {
                keyboardExtension.composers.forEach { composer ->
                    localComposers[ExtensionComponentName(keyboardExtension.meta.id, composer.id)] = composer
                }
                keyboardExtension.currencySets.forEach { currencySet ->
                    localCurrencySets[ExtensionComponentName(keyboardExtension.meta.id, currencySet.id)] = currencySet
                }
                keyboardExtension.layouts.forEach { (type, layoutComponents) ->
                    val layoutType = LayoutType.entries.find { it.id == type }
                    if (layoutType == null) {
                        flogWarning {
                            "Skipping invalid layout type in extension ${keyboardExtension.meta.id}"
                        }
                        return@forEach
                    }
                    for (layoutComponent in layoutComponents) {
                        localLayouts.getValue(layoutType)[
                            ExtensionComponentName(keyboardExtension.meta.id, layoutComponent.id)
                        ] = layoutComponent
                    }
                }
                keyboardExtension.popupMappings.forEach { popupMapping ->
                    localPopupMappings[ExtensionComponentName(keyboardExtension.meta.id, popupMapping.id)] = popupMapping
                }
                keyboardExtension.punctuationRules.forEach { punctuationRule ->
                    localPunctuationRules[ExtensionComponentName(keyboardExtension.meta.id, punctuationRule.id)] = punctuationRule
                }
                localSubtypePresets.addAll(keyboardExtension.subtypePresets)
            }
            localSubtypePresets.sortBy { it.locale.displayName() }
            for (languageCode in listOf("en-CA", "en-AU", "en-UK", "en-US")) {
                val index: Int = localSubtypePresets.indexOfFirst { it.locale.languageTag() == languageCode }
                if (index > 0) {
                    localSubtypePresets.add(0, localSubtypePresets.removeAt(index))
                }
            }
            subtypePresets.value = localSubtypePresets
            composers.value = localComposers
            currencySets.value = localCurrencySets
            layouts.value = localLayouts
            popupMappings.value = localPopupMappings
            punctuationRules.value = localPunctuationRules
            anyChangedVersion.update { it + 1 }
        }
    }

    private inner class ComputingEvaluatorImpl(
        override val version: Int,
        override val keyboard: Keyboard,
        override val editorInfo: FlorisEditorInfo,
        override val state: KeyboardState,
        override val subtype: Subtype,
    ) : ComputingEvaluator {

        override val numericPasswordDigitMapping: Map<Int, Int>
            get() = this@KeyboardManager.numericPasswordDigitMapping

        override fun context(): Context = appContext

        val androidKeyguardManager = context().systemService(AndroidKeyguardManager::class)

        override fun displayLanguageNamesIn(): DisplayLanguageNamesIn {
            return prefs.localization.displayLanguageNamesIn.get()
        }

        override fun evaluateEnabled(data: KeyData): Boolean {
            return when (data.code) {
                KeyCode.CLIPBOARD_COPY,
                KeyCode.CLIPBOARD_CUT -> {
                    state.isSelectionMode && editorInfo.isRichInputEditor
                }
                KeyCode.CLIPBOARD_PASTE -> {
                    !androidKeyguardManager.let { it.isDeviceLocked || it.isKeyguardLocked }
                        && clipboardManager.canPastePrimaryClip()
                }
                KeyCode.CLIPBOARD_CLEAR_PRIMARY_CLIP -> {
                    clipboardManager.canPastePrimaryClip()
                }
                KeyCode.CLIPBOARD_SELECT_ALL -> {
                    editorInfo.isRichInputEditor
                }
                KeyCode.TOGGLE_INCOGNITO_MODE -> SuggestionPrivacyPolicy.canToggleIncognitoMode(
                    preference = prefs.suggestion.incognitoMode.get(),
                    appDeclaredNoPersonalizedLearning = editorInfo.imeOptions.flagNoPersonalizedLearning,
                )
                KeyCode.LANGUAGE_SWITCH -> {
                    subtypeManager.subtypes.size > 1
                }
                else -> true
            }
        }

        override fun evaluateVisible(data: KeyData): Boolean {
            val bottomRowPreset = BottomRowPreset.fromJsonOverride(prefs.keyboard.bottomRowPresetJson.get())
            if (bottomRowPreset != null && data.code == KeyCode.IME_UI_MODE_MEDIA) {
                return bottomRowPreset.contains(BottomRowKey.EMOJI)
            }
            return when (data.code) {
                KeyCode.IME_UI_MODE_TEXT,
                KeyCode.IME_UI_MODE_MEDIA -> {
                    val tempUtilityKeyAction = when {
                        prefs.keyboard.utilityKeyEnabled.get() -> prefs.keyboard.utilityKeyAction.get()
                        else -> UtilityKeyAction.DISABLED
                    }
                    when (tempUtilityKeyAction) {
                        UtilityKeyAction.DISABLED,
                        UtilityKeyAction.SWITCH_LANGUAGE,
                        UtilityKeyAction.SWITCH_KEYBOARD_APP -> false
                        UtilityKeyAction.SWITCH_TO_EMOJIS -> true
                        UtilityKeyAction.DYNAMIC_SWITCH_LANGUAGE_EMOJIS -> !shouldShowLanguageSwitch()
                    }
                }
                KeyCode.LANGUAGE_SWITCH -> {
                    val tempUtilityKeyAction = when {
                        prefs.keyboard.utilityKeyEnabled.get() -> prefs.keyboard.utilityKeyAction.get()
                        else -> UtilityKeyAction.DISABLED
                    }
                    when (tempUtilityKeyAction) {
                        UtilityKeyAction.DISABLED,
                        UtilityKeyAction.SWITCH_TO_EMOJIS -> false
                        UtilityKeyAction.SWITCH_LANGUAGE,
                        UtilityKeyAction.SWITCH_KEYBOARD_APP -> true
                        UtilityKeyAction.DYNAMIC_SWITCH_LANGUAGE_EMOJIS -> shouldShowLanguageSwitch()
                    }
                }
                else -> true
            }
        }

        override fun isSlot(data: KeyData): Boolean {
            return CurrencySet.isCurrencySlot(data.code)
        }

        override fun slotData(data: KeyData): KeyData? {
            return subtypeManager.getCurrencySet(subtype).getSlot(data.code)
        }

        fun asSmartbarQuickActionsEvaluator(): ComputingEvaluatorImpl {
            return ComputingEvaluatorImpl(
                version = version,
                keyboard = SmartbarQuickActionsKeyboard,
                editorInfo = editorInfo,
                state = state,
                subtype = Subtype.DEFAULT,
            )
        }
    }
}
