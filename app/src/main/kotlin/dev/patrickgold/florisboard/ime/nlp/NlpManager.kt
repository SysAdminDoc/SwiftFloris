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

package dev.patrickgold.florisboard.ime.nlp

import android.content.Context
import android.util.LruCache
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.devtools.DevtoolsContentPolicy
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.dictionary.DictionaryManager
import dev.patrickgold.florisboard.ime.dictionary.PersonalBigramStore
import dev.patrickgold.florisboard.ime.dictionary.PersonalTrigramStore
import dev.patrickgold.florisboard.ime.editor.EditorCompatibilityPolicy
import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.editor.EditorRange
import dev.patrickgold.florisboard.ime.media.emoji.EmojiSuggestionProvider
import dev.patrickgold.florisboard.ime.nlp.latin.ColdStartNextWordPriors
import dev.patrickgold.florisboard.ime.smartcompose.SensitiveFieldGuard
import dev.patrickgold.florisboard.ime.smartcompose.SmartComposeContext
import dev.patrickgold.florisboard.ime.smartcompose.NlpAddonHub
import dev.patrickgold.florisboard.ime.smartcompose.SmartComposeResult
import dev.patrickgold.florisboard.ime.text.key.KeyVariation
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.lib.FlorisLocale
import dev.patrickgold.florisboard.lib.util.NetworkUtils
import dev.patrickgold.florisboard.subtypeManager
import org.florisboard.lib.android.AndroidKeyguardManager
import org.florisboard.lib.android.systemServiceOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.florisboard.lib.kotlin.collectLatestIn
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentHashMap
import kotlin.properties.Delegates

private const val BLANK_STR_PATTERN = "^\\s*$"

class NlpManager(
    context: Context,
    internal val addonHub: NlpAddonHub,
) {
    private val blankStrRegex = Regex(BLANK_STR_PATTERN)

    private val appContext = context.applicationContext
    private val prefs by FlorisPreferenceStore
    private val dictionaryManager = DictionaryManager.default()
    private val clipboardManager by context.clipboardManager()
    private val editorInstance by context.editorInstance()
    private val keyboardManager by context.keyboardManager()
    private val subtypeManager by context.subtypeManager()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val clipboardSuggestionProvider = ClipboardSuggestionProvider(context)
    private val emojiSuggestionProvider = EmojiSuggestionProvider(context)
    private val providerRegistry = NlpProviderRegistry(context)
    private val typingTraceRecorder = SwiftKeyTypingTraceRecorder(appContext)
    private val candidateAssembler = NlpCandidateAssembler(
        clipboardSuggestionProvider = clipboardSuggestionProvider,
        editorInstance = editorInstance,
        keyboardManager = keyboardManager,
    )
    private val smartbarAutoExpandController = SmartbarAutoExpandController(
        editorInstance = editorInstance,
        scope = scope,
    )
    
    // Caches for word lists and frequencies to avoid blocking on repeated calls.
    // wordsListCache is keyed by subtype only, so it is bounded by the active subtype
    // count. frequencyCache is keyed by `${subtype}-$word` and is queried from the
    // glide classifier inner loop (StatisticalGlideTypingClassifier:235) — on a long
    // typing session every unique word would otherwise accumulate forever in a
    // long-lived IME process, so it is bounded by an LruCache. 5000 entries is enough
    // for the warm vocabulary of a single language session without unbounded growth.
    private val wordsListCache = ConcurrentHashMap<String, List<String>>()
    private val frequencyCache = LruCache<String, Double>(5000)
    private val autoCommitSuppression = AutoCommitSuppression()
    private val autoCommitUndoSession = AutoCommitUndoSession()
    private val glideAlternativeSession = GlideAlternativeSession()
    private val correctionOutcomePriors = CorrectionOutcomePriors.get(appContext)
    private val touchDecoderEvidence = TouchDecoderEvidenceBuffer()

    private val suggestionsRequestCounter = AtomicLong(0L)
    @Volatile private var activeSuggestionJob: Job? = null
    private val internalSuggestionsGuard = Mutex()
    private var internalSuggestions by Delegates.observable(0L to listOf<SuggestionCandidate>()) { _, _, _ ->
        scope.launch { assembleCandidates() }
    }

    private val _activeCandidatesFlow = MutableStateFlow(listOf<SuggestionCandidate>())
    val activeCandidatesFlow = _activeCandidatesFlow.asStateFlow()
    inline var activeCandidates
        get() = activeCandidatesFlow.value
        private set(v) {
            _activeCandidatesFlow.value = v
        }
    private var activeCandidateSignals: Map<String, SwiftKeyCandidateSignals> = emptyMap()

    val debugOverlaySuggestionsInfos = LruCache<Long, Pair<String, SpellingResult>>(10)
    var debugOverlayVersion = MutableStateFlow(0)

    init {
        clipboardManager.primaryClipFlow.collectLatestIn(scope) {
            assembleCandidates()
        }
        prefs.suggestion.enabled.asFlow().collectLatestIn(scope) {
            assembleCandidates()
        }
        prefs.clipboard.suggestionEnabled.asFlow().collectLatestIn(scope) {
            assembleCandidates()
        }
        prefs.emoji.suggestionEnabled.asFlow().collectLatestIn(scope) {
            assembleCandidates()
        }
        prefs.dictionary.enableFlorisUserDictionary.asFlow().collectLatestIn(scope) {
            onUserDictionaryConfigurationChanged()
        }
        prefs.dictionary.enableSystemUserDictionary.asFlow().collectLatestIn(scope) {
            onUserDictionaryConfigurationChanged()
        }
        subtypeManager.activeSubtypeFlow.collectLatestIn(scope) { subtype ->
            touchDecoderEvidence.clear()
            preload(subtype)
        }
    }

    /**
     * Gets the punctuation rule from the currently active subtype and returns it. Falls back to a default one if the
     * subtype does not exist or defines an invalid punctuation rule.
     *
     * @return The punctuation rule or a fallback.
     */
    fun getActivePunctuationRule(): PunctuationRule {
        return getPunctuationRule(subtypeManager.activeSubtype)
    }

    /**
     * Gets the punctuation rule from the given subtype and returns it. Falls back to a default one if the subtype does
     * not exist or defines an invalid punctuation rule.
     *
     * @return The punctuation rule or a fallback.
     */
    fun getPunctuationRule(subtype: Subtype): PunctuationRule {
        return keyboardManager.resources.punctuationRules.value[subtype.punctuationRule] ?: PunctuationRule.Fallback
    }

    fun preload(subtype: Subtype) {
        scope.launch {
            emojiSuggestionProvider.preload(subtype)
            providerRegistry.preload(subtype)
        }
    }

    /**
     * Spell wrapper helper which calls the spelling provider and returns the result. Coroutine management must be done
     * by the source spell checker service.
     */
    suspend fun spell(
        subtype: Subtype,
        word: String,
        precedingWords: List<String>,
        followingWords: List<String>,
        maxSuggestionCount: Int,
    ): SpellingResult {
        if (prefs.spelling.useUdmEntries.get()) {
            if (dictionaryManager.isKnownUserDictionaryWord(word, subtype.primaryLocale)) {
                return SpellingResult.validWord()
            }
        }
        val result = providerRegistry.spellingProvider(subtype).spell(
            subtype = subtype,
            word = word,
            precedingWords = precedingWords,
            followingWords = followingWords,
            maxSuggestionCount = maxSuggestionCount,
            allowPossiblyOffensive = !prefs.suggestion.blockPossiblyOffensive.get(),
            isPrivateSession = keyboardManager.activeState.isIncognitoMode,
        )
        return OffensiveWordPolicy.filterSpellingResult(
            result = result,
            tier = OffensiveWordPolicy.tier(
                blockPossiblyOffensive = prefs.suggestion.blockPossiblyOffensive.get(),
                blockSlursOnly = prefs.suggestion.blockSlursOnly.get(),
            ),
        )
    }

    suspend fun determineLocalComposing(
        textBeforeSelection: CharSequence, breakIterators: BreakIteratorGroup, localLastCommitPosition: Int
    ): EditorRange {
        return providerRegistry.suggestionProvider(subtypeManager.activeSubtype).determineLocalComposing(
            subtypeManager.activeSubtype, textBeforeSelection, breakIterators, localLastCommitPosition
        )
    }

    fun providerForcesSuggestionOn(subtype: Subtype): Boolean {
        return providerRegistry.providerForcesSuggestionOn(subtype)
    }

    fun isSuggestionOn(): Boolean =
        prefs.suggestion.enabled.get()
            || prefs.emoji.suggestionEnabled.get()
            || prefs.clipboard.suggestionEnabled.get()
            || providerForcesSuggestionOn(subtypeManager.activeSubtype)

    /**
     * Whether the active editor is a password field or declares itself
     * sensitive ([SensitiveFieldGuard]). Used to gate the typing-trace
     * recorder on paths that don't build a [SuggestionPrivacyPolicy]
     * request snapshot.
     */
    private fun isActiveEditorSensitive(): Boolean {
        val editorInfo = editorInstance.activeInfo
        return keyboardManager.activeState.keyVariation == dev.patrickgold.florisboard.ime.text.key.KeyVariation.PASSWORD ||
            SensitiveFieldGuard.isSensitive(
                editorInfo.inputAttributes.raw,
                editorInfo.imeOptions.raw,
            )
    }

    fun suggest(subtype: Subtype, content: EditorContent) {
        if (!EditorCompatibilityPolicy.snapshot(editorInstance.activeInfo).allowsImeSuggestions) {
            clearSuggestions()
            return
        }
        autoCommitSuppression.onContentChanged(content.autoCommitWord(), content.autoCommitWordStart())
        autoCommitUndoSession.onContentChanged(content)
        glideAlternativeSession.onContentChanged(
            content = content,
            allowRetention = !keyboardManager.activeState.isIncognitoMode && !isActiveEditorSensitive(),
        )
        val requestId = suggestionsRequestCounter.incrementAndGet()
        val editorInfo = editorInstance.activeInfo
        val requestPrivacy = SuggestionPrivacyPolicy.snapshotSuggestionRequest(
            emojiSuggestionEnabled = prefs.emoji.suggestionEnabled.get(),
            emojiMaxCandidateCount = prefs.emoji.suggestionCandidateMaxCount.get(),
            wordSuggestionEnabled = prefs.suggestion.enabled.get(),
            blockPossiblyOffensive = prefs.suggestion.blockPossiblyOffensive.get(),
            blockSlursOnly = prefs.suggestion.blockSlursOnly.get(),
            isPrivateSession = keyboardManager.activeState.isIncognitoMode,
            isEditorSensitive = SensitiveFieldGuard.isSensitive(
                editorInfo.inputAttributes.raw,
                editorInfo.imeOptions.raw,
            ),
            keyVariation = keyboardManager.activeState.keyVariation,
        )
        activeSuggestionJob?.cancel()
        activeSuggestionJob = scope.launch {
            val emojiSuggestions = when {
                requestPrivacy.emojiSuggestionEnabled -> {
                    OffensiveWordPolicy.filterCandidates(
                        candidates = emojiSuggestionProvider.suggest(
                            subtype = subtype,
                            content = content,
                            maxCandidateCount = requestPrivacy.emojiMaxCandidateCount,
                            allowPossiblyOffensive = requestPrivacy.allowPossiblyOffensive,
                            isPrivateSession = requestPrivacy.isPrivateSession,
                        ),
                        tier = requestPrivacy.offensiveFilterTier,
                    )
                }
                else -> emptyList()
            }
            val suggestionProvider = providerRegistry.suggestionProvider(subtype)
            val suggestionsEnabled = (requestPrivacy.wordSuggestionEnabled || suggestionProvider.forcesSuggestionOn) &&
                !requestPrivacy.isPasswordEditor
            val userDictionarySuggestions = if (suggestionsEnabled) {
                userDictionarySuggestions(
                    subtype = subtype,
                    content = content,
                    maxCandidateCount = 8,
                )
            } else {
                emptyList()
            }
            val suggestions = if (suggestionsEnabled) {
                OffensiveWordPolicy.filterCandidates(
                    candidates = suggestionProvider.suggest(
                        subtype = subtype,
                        content = content,
                        maxCandidateCount = 8,
                        allowPossiblyOffensive = requestPrivacy.allowPossiblyOffensive,
                        isPrivateSession = requestPrivacy.isPrivateSession,
                    ),
                    tier = requestPrivacy.offensiveFilterTier,
                )
            } else {
                emptyList()
            }
            val currentWord = content.autoCommitWord()
            val currentWordStart = content.autoCommitWordStart()
            val typedWordKnown = suggestionsEnabled && isKnownTypedWord(
                suggestionProvider = suggestionProvider,
                subtype = subtype,
                currentWord = currentWord,
            )
            val candidateSignals = candidateSignals(
                subtype = subtype,
                content = content,
                suggestionProvider = suggestionProvider,
                currentWord = currentWord,
                currentWordStart = currentWordStart,
                candidates = userDictionarySuggestions + suggestions,
            )
            val decoderContext = SwiftKeyDecoderContext(
                currentWord = currentWord,
                maxCandidateCount = 8,
                typedWordKnown = typedWordKnown,
                touchEvidence = touchDecoderEvidence.evidenceFor(currentWord),
                candidateSignals = candidateSignals,
            )
            val wordSuggestions = SwiftKeyCandidateRanker.rank(
                context = decoderContext,
                preferred = userDictionarySuggestions,
                fallback = suggestions,
            )
            if (typingTraceRecorder.isEnabled()) {
                typingTraceRecorder.recordSuggestion(
                    content = content,
                    context = decoderContext,
                    scoredCandidates = SwiftKeyCandidateRanker.scoreCandidates(
                        context = decoderContext,
                        preferred = userDictionarySuggestions,
                        fallback = suggestions,
                    ),
                    rankedCandidates = wordSuggestions,
                    isPrivateSession = requestPrivacy.isPrivateSession,
                    // Same gate every other learning surface honours: never write
                    // password-field or sensitive-field text into the shareable
                    // plaintext trace file.
                    isSensitiveEditor = requestPrivacy.isPasswordEditor || requestPrivacy.isEditorSensitive,
                )
            }
            // ROADMAP §0 P1 — Smart-Compose inline ghost-text. When a
            // SmartComposeProvider is enabled, ask it for a multi-token
            // continuation given the preceding text. The registered
            // heuristic provider is preference-gated and returns NoSuggestion
            // until smart compose is enabled.
            val ghostTextCandidate = if (suggestionsEnabled) {
                buildGhostTextCandidate(
                    subtype = subtype,
                    content = content,
                    currentWord = currentWord,
                    isPrivateSession = requestPrivacy.isPrivateSession,
                    isEditorSensitive = requestPrivacy.isEditorSensitive,
                    inputType = editorInfo.inputAttributes.raw,
                    imeOptions = editorInfo.imeOptions.raw,
                )
            } else {
                null
            }
            internalSuggestionsGuard.withLock {
                if (internalSuggestions.first < requestId) {
                    activeCandidateSignals = candidateSignals
                    internalSuggestions = requestId to buildList {
                        addAll(wordSuggestions)
                        addAll(emojiSuggestions)
                        ghostTextCandidate?.let { add(it) }
                    }
                }
            }
        }
    }

    private suspend fun buildGhostTextCandidate(
        subtype: Subtype,
        content: EditorContent,
        currentWord: String,
        isPrivateSession: Boolean,
        isEditorSensitive: Boolean,
        inputType: Int,
        imeOptions: Int,
    ): GhostTextSuggestionCandidate? {
        // Honor both the user-controlled private session and sensitive fields before
        // deriving ghost text from the user's personal n-gram history. The composing-
        // disabled gate only covers password variations; it cannot see the incognito
        // toggle and is not a substitute for this request-scoped privacy gate.
        if (!SuggestionPrivacyPolicy.allowsGhostText(isPrivateSession, isEditorSensitive)) {
            return null
        }
        val locale = subtype.primaryLocale.languageTag()
        val context = SmartComposeContext(
            precedingText = content.textBeforeSelection,
            composingPrefix = currentWord,
            locale = locale,
            editorPackageName = null,
        )
        val result = addonHub.predictAsync(
            context = context,
            inputType = inputType,
            imeOptions = imeOptions,
            maxCandidates = 1,
        )
        val top = (result as? SmartComposeResult.Suggestion)?.candidates?.firstOrNull()
            ?: return null
        if (top.confidence < 0.45f) return null
        return GhostTextSuggestionCandidate(
            text = top.text,
            confidence = top.confidence.toDouble(),
            tokenCount = top.tokenCount,
            sourceProvider = null,
        )
    }

    fun suggestDirectly(suggestions: List<SuggestionCandidate>) {
        val requestId = suggestionsRequestCounter.incrementAndGet()
        scope.launch {
            internalSuggestionsGuard.withLock {
                if (internalSuggestions.first < requestId) {
                    activeCandidateSignals = emptyMap()
                    internalSuggestions = requestId to suggestions
                }
            }
        }
    }

    fun clearSuggestions() {
        glideAlternativeSession.clear()
        val requestId = suggestionsRequestCounter.incrementAndGet()
        scope.launch {
            internalSuggestionsGuard.withLock {
                if (internalSuggestions.first < requestId) {
                    activeCandidateSignals = emptyMap()
                    internalSuggestions = requestId to emptyList()
                }
            }
        }
    }

    fun rememberAcceptedGlideCommit(
        committedText: String,
        alternatives: List<String>,
        range: EditorRange,
    ) {
        if (keyboardManager.activeState.isIncognitoMode || isActiveEditorSensitive()) {
            glideAlternativeSession.clear()
        } else {
            glideAlternativeSession.remember(
                committedText = committedText,
                alternatives = alternatives,
                range = range,
            )
        }
        assembleCandidates()
    }

    fun consumeGlideAlternative(candidate: GlideAlternativeSuggestionCandidate): Boolean {
        val consumed = glideAlternativeSession.consume(candidate)
        if (consumed) {
            assembleCandidates()
        }
        return consumed
    }

    fun clearGlideAlternatives() {
        glideAlternativeSession.clear()
        assembleCandidates()
    }

    internal fun recordTouchDecoderSample(primaryText: String, alternatives: List<TouchDecoderCandidate>) {
        if (!SuggestionPrivacyPolicy.shouldRecordTouchDecoderSample(
            isAutoCorrectEnabled = prefs.correction.autoCorrect.get(),
            isIncognitoMode = keyboardManager.activeState.isIncognitoMode,
            keyVariation = keyboardManager.activeState.keyVariation,
        )) return
        touchDecoderEvidence.record(
            TouchDecoderSample(
                primaryText = primaryText,
                alternatives = alternatives,
            )
        )
    }

    fun getAutoCommitCandidate(): SuggestionCandidate? {
        val content = editorInstance.activeContent
        val currentWord = content.autoCommitWord()
        val currentWordStart = content.autoCommitWordStart()
        return CandidateAutoCommitPolicy.selectAutoCommitCandidate(
            autoCorrectEnabled = prefs.correction.autoCorrect.get(),
            autoCorrectCommitMode = prefs.correction.autoCorrectCommitMode.get(),
            autoCorrectConfidenceThreshold = AutoCorrectConfidencePolicy.thresholdFor(
                prefs.correction.autoCorrectConfidenceThreshold.get(),
            ),
            keyVariation = keyboardManager.activeState.keyVariation,
            currentWord = currentWord,
            currentWordStart = currentWordStart,
            candidates = activeCandidates,
            candidateSignals = activeCandidateSignals,
            rejectionPolicy = autoCommitSuppression,
            // ROADMAP §6 N5.4 — personal-dictionary shortcuts express explicit
            // user intent and therefore stay ahead of algorithmic guesses.
            userDictionaryShortcutCandidate = userDictionaryShortcutAutoCommitCandidate(currentWord),
            immediatePhraseRepairCandidate = immediatePhraseRepairCandidate(currentWord),
            immediateAutoCommitCandidate = immediateAutoCommitCandidate(currentWord),
        )
    }

    fun getSpacebarCandidate(): SuggestionCandidate? {
        val autoCorrectEnabled = prefs.correction.autoCorrect.get()
        val quickPredictionInsertEnabled = prefs.correction.quickPredictionInsert.get()
        val content = editorInstance.activeContent
        val currentWord = content.autoCommitWord()
        val currentWordStart = content.autoCommitWordStart()
        return CandidateAutoCommitPolicy.selectSpacebarCandidate(
            autoCorrectEnabled = autoCorrectEnabled,
            autoCorrectCommitMode = prefs.correction.autoCorrectCommitMode.get(),
            autoCorrectConfidenceThreshold = AutoCorrectConfidencePolicy.thresholdFor(
                prefs.correction.autoCorrectConfidenceThreshold.get(),
            ),
            quickPredictionInsertEnabled = quickPredictionInsertEnabled,
            keyVariation = keyboardManager.activeState.keyVariation,
            currentWord = currentWord,
            currentWordStart = currentWordStart,
            textBeforeCursor = content.textBeforeSelection,
            candidates = activeCandidates,
            candidateSignals = activeCandidateSignals,
            rejectionPolicy = autoCommitSuppression,
            userDictionaryShortcutCandidate = userDictionaryShortcutAutoCommitCandidate(currentWord),
            immediatePhraseRepairCandidate = immediatePhraseRepairCandidate(currentWord),
            immediateAutoCommitCandidate = immediateAutoCommitCandidate(currentWord),
        )
    }

    fun shouldSuppressPlainSpaceForPrediction(): Boolean {
        val content = editorInstance.activeContent
        return CandidateAutoCommitPolicy.shouldSuppressPlainSpaceForPrediction(
            quickPredictionInsertEnabled = prefs.correction.quickPredictionInsert.get(),
            currentWord = content.autoCommitWord(),
            textBeforeCursor = content.textBeforeSelection,
            candidates = activeCandidates,
            candidateSignals = activeCandidateSignals,
        )
    }

    private fun userDictionaryShortcutAutoCommitCandidate(
        currentWord: String,
    ): SuggestionCandidate? {
        if (currentWord.isBlank()) return null
        val expansion = dictionaryManager.queryUserDictionaryShortcutExact(
            currentWord,
            subtypeManager.activeSubtype.primaryLocale,
        ) ?: return null
        val candidate = WordSuggestionCandidate(
            text = expansion,
            confidence = 1.0,
            isEligibleForAutoCommit = true,
            isEligibleForUserRemoval = false,
        )
        return candidate
    }

    fun rememberAcceptedAutoCommit(content: EditorContent, candidate: SuggestionCandidate) {
        val originalText = content.autoCommitWord()
        autoCommitSuppression.rememberAccepted(
            originalText = originalText,
            correctedText = candidate.text,
            wordStart = content.autoCommitWordStart(),
        )
        autoCommitUndoSession.remember(
            originalText = originalText,
            correctedText = candidate.text,
            wordStart = content.autoCommitWordStart(),
            sourceProvider = candidate.sourceProvider,
        )
        correctionOutcomePriors.recordAccepted(
            originalText = originalText,
            correctedText = candidate.text,
        )
        typingTraceRecorder.recordAutoCommitAccepted(
            content = content,
            candidate = candidate,
            isPrivateSession = keyboardManager.activeState.isIncognitoMode,
            isSensitiveEditor = isActiveEditorSensitive(),
        )
    }

    fun rejectAcceptedAutoCommitOnBackspace(content: EditorContent): Boolean {
        val cursorPosition = content.selection.takeIf { it.isValid }?.start
        val rejected = autoCommitSuppression.rejectAccepted(
            textBeforeSelection = content.textBeforeSelection,
            cursorPosition = cursorPosition,
        )
        if (rejected) {
            autoCommitSuppression.consumeLastRejectedPair()?.let { pair ->
                correctionOutcomePriors.recordRejected(
                    originalText = pair.original,
                    correctedText = pair.corrected,
                )
            }
            typingTraceRecorder.recordAutoCommitRejected(
                content = content,
                isPrivateSession = keyboardManager.activeState.isIncognitoMode,
                isSensitiveEditor = isActiveEditorSensitive(),
            )
        }
        return rejected
    }

    fun rejectAcceptedAutoCommitFromUndo(candidate: AutoCommitUndoSuggestionCandidate): Boolean {
        val correction = autoCommitUndoSession.consume(candidate) ?: return false
        val rejected = autoCommitSuppression.rejectAccepted(
            originalText = correction.original,
            correctedText = correction.corrected,
            wordStart = correction.range.start,
        )
        if (rejected) {
            autoCommitSuppression.consumeLastRejectedPair()?.let { pair ->
                correctionOutcomePriors.recordRejected(
                    originalText = pair.original,
                    correctedText = pair.corrected,
                )
            }
        }
        assembleCandidates()
        return rejected
    }

    fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean {
        // Fire and forget the provider call; don't block
        scope.launch {
            val result = candidate.sourceProvider?.removeSuggestion(subtype, candidate) == true
            if (result) {
                // Need to re-trigger the suggestions algorithm
                if (candidate is ClipboardSuggestionCandidate) {
                    assembleCandidates()
                } else {
                    suggest(subtypeManager.activeSubtype, editorInstance.activeContent)
                }
            }
        }
        // Optimistically return true if eligible for removal
        return candidate.isEligibleForUserRemoval
    }

    fun getListOfWords(subtype: Subtype): List<String> {
        val cacheKey = subtype.toString()
        return wordsListCache.getOrPut(cacheKey) {
            // Use blocking only for first-time fetch; results are cached afterward
            runBlocking { providerRegistry.suggestionProvider(subtype).getListOfWords(subtype) }
        }
    }

    fun getFrequencyForWord(subtype: Subtype, word: String): Double {
        val cacheKey = "${subtype}-$word"
        frequencyCache.get(cacheKey)?.let { return it }
        // Use blocking only on cache miss; for trivial providers (e.g. Latin's pure
        // map lookup) the wrapped suspend fn never actually suspends.
        val value = runBlocking { providerRegistry.suggestionProvider(subtype).getFrequencyForWord(subtype, word) }
        frequencyCache.put(cacheKey, value)
        return value
    }

    suspend fun nextWordContextScore(previousWord: String, nextWord: String): Double {
        val locale = subtypeManager.activeSubtype.primaryLocale
        val bigramStore = PersonalBigramStore.get(appContext)
        val personalScore = bigramStore.score(previousWord, nextWord, locale)
        val rejectionDiscount = 1.0 - bigramStore.rejectionPenalty(previousWord, nextWord, locale)
        val coldStartScore = ColdStartNextWordPriors
            .suggest(
                textBeforeCursor = "${previousWord.trim()} ",
                languageCode = locale.language,
                maxCandidateCount = 8,
            )
            .firstOrNull { prior -> prior.word.equals(nextWord, ignoreCase = true) }
            ?.confidence
            ?: 0.0
        return maxOf(personalScore, coldStartScore * rejectionDiscount).coerceIn(0.0, 1.0)
    }

    private suspend fun userDictionarySuggestions(
        subtype: Subtype,
        content: EditorContent,
        maxCandidateCount: Int,
    ): List<SuggestionCandidate> {
        val currentWord = content.currentWordText.ifBlank { content.composingText }
        if (maxCandidateCount <= 0 || currentWord.isBlank()) {
            return emptyList()
        }
        return withContext(Dispatchers.IO) {
            dictionaryManager.queryUserDictionary(currentWord, subtype.primaryLocale).take(maxCandidateCount)
        }
    }

    private suspend fun isKnownTypedWord(
        suggestionProvider: SuggestionProvider,
        subtype: Subtype,
        currentWord: String,
    ): Boolean {
        val normalizedWord = currentWord.normalizedCandidateSignalKey() ?: return false
        return activeLocales(subtype).any { locale ->
            dictionaryManager.isKnownUserDictionaryWord(normalizedWord, locale) ||
                frequencyForWordInLocale(suggestionProvider, subtype, locale, normalizedWord) > 0.0
        }
    }

    private fun activeLocales(subtype: Subtype): List<FlorisLocale> {
        return subtype.locales().distinctBy { locale -> locale.languageTag() }
    }

    private suspend fun frequencyForWordInLocale(
        suggestionProvider: SuggestionProvider,
        subtype: Subtype,
        locale: FlorisLocale,
        word: String,
    ): Double {
        val normalizedWord = word.normalizedCandidateSignalKey() ?: return 0.0
        val localeSubtype = if (locale == subtype.primaryLocale && subtype.secondaryLocales.isEmpty()) {
            subtype
        } else {
            subtype.copy(primaryLocale = locale, secondaryLocales = emptyList())
        }
        val cacheKey = "${suggestionProvider.javaClass.name}:${locale.languageTag()}-$normalizedWord"
        frequencyCache.get(cacheKey)?.let { return it }
        val value = runCatching {
            suggestionProvider.getFrequencyForWord(localeSubtype, normalizedWord)
        }.getOrDefault(0.0).coerceIn(0.0, 1.0)
        frequencyCache.put(cacheKey, value)
        return value
    }

    private suspend fun candidateSignals(
        subtype: Subtype,
        content: EditorContent,
        suggestionProvider: SuggestionProvider,
        currentWord: String,
        currentWordStart: Int?,
        candidates: List<SuggestionCandidate>,
    ): Map<String, SwiftKeyCandidateSignals> {
        if (candidates.isEmpty()) return emptyMap()
        val locale = subtype.primaryLocale
        val locales = activeLocales(subtype)
        val localeCount = locales.size
        val typedWordKey = currentWord.normalizedCandidateSignalKey()
        val typedWordKnownByUserDictionary = typedWordKey?.let { key ->
            locales.any { locale -> dictionaryManager.isKnownUserDictionaryWord(key, locale) }
        } ?: false
        val previousWords = TypingContextExtractor.previousWordsBeforeCurrentWord(
            textBeforeSelection = content.textBeforeSelection,
            currentWord = currentWord,
        )
        val languageContextWords = TypingContextExtractor.previousWordListBeforeCurrentWord(
            textBeforeSelection = content.textBeforeSelection,
            currentWord = currentWord,
            maxDepth = MaxLanguageContextWords,
        )
        val contextPrefix = TypingContextExtractor.sentenceLocalPrefixBeforeCurrentWord(
            textBeforeSelection = content.textBeforeSelection,
            currentWord = currentWord,
        )
        val bigramStore = PersonalBigramStore.get(appContext)
        val trigramStore = PersonalTrigramStore.get(appContext)
        val contextFrequencyByLocale: Map<FlorisLocale, Map<String, Double>> = buildMap {
            for (activeLocale in locales) {
                put(activeLocale, buildMap {
                    for (word in languageContextWords.distinct()) {
                        put(word, frequencyForWordInLocale(suggestionProvider, subtype, activeLocale, word))
                    }
                })
            }
        }
        // docs/archive/SWIFTKEY_PARITY_ROADMAP_2026-05-17 §B4 — same-sentence
        // language-switch hardening. Previously this map took the
        // MAX trailing-word frequency per locale across the 4-word
        // window, which meant a single trailing word in any locale
        // locked in that locale's signal and the next 3 words could
        // not shift it. Real bilingual sentences mid-switch ("hello
        // mi amigo cómo …") need the recent words to weigh more so
        // the active language tracks the user's writing without
        // flipping on the first recognised word. The geometric
        // weighted average — recent words get full weight, older
        // words decay per step back — is pulled out into
        // `TrailingContextLanguageBlend` so it can be unit-tested
        // independent of Android plumbing.
        val contextLanguageScores = buildMap {
            for (activeLocale in locales) {
                val blended = TrailingContextLanguageBlend.score(
                    contextWordsOldestFirst = languageContextWords,
                    freqLookup = { word ->
                        contextFrequencyByLocale[activeLocale]?.get(word) ?: 0.0
                    },
                    decay = TrailingContextDecay,
                )
                put(activeLocale, blended)
            }
        }
        val languageIdScores = LatinScriptLanguageIdentifier.score(
            currentWord = currentWord,
            previousWords = languageContextWords,
            locales = locales.map { it.language },
        )
        val candidateKeys = candidates.asSequence()
            .filterIsInstance<WordSuggestionCandidate>()
            .mapNotNull { candidate -> candidate.text.toString().normalizedCandidateSignalKey() }
            .distinct()
            .toList()
        val contextLocaleHasTypedPrefixCandidate = typedWordKey
            ?.takeIf { key -> key.length >= MinLanguageSwitchPrefixLength }
            ?.let { prefix ->
                var found = false
                for (contextLocale in locales) {
                    if ((contextLanguageScores[contextLocale] ?: 0.0) <= 0.0) {
                        continue
                    }
                    for (candidateKey in candidateKeys) {
                        if (candidateKey != prefix &&
                            candidateKey.startsWith(prefix) &&
                            frequencyForWordInLocale(suggestionProvider, subtype, contextLocale, candidateKey) > 0.0
                        ) {
                            found = true
                            break
                        }
                    }
                    if (found) break
                }
                found
            } ?: false

        return buildMap {
            for (candidate in candidates) {
                if (candidate !is WordSuggestionCandidate) continue
                val candidateText = candidate.text.toString()
                val key = candidateText.normalizedCandidateSignalKey() ?: continue
                val localeEvidence = locales.map { locale ->
                    TokenLocaleEvidence(
                        typedFrequency = typedWordKey?.let { word ->
                            frequencyForWordInLocale(suggestionProvider, subtype, locale, word)
                        } ?: 0.0,
                        candidateFrequency = frequencyForWordInLocale(
                            suggestionProvider,
                            subtype,
                            locale,
                            key,
                        ),
                        contextFrequency = contextLanguageScores[locale] ?: 0.0,
                        languageIdConfidence = languageIdScores[locale.language] ?: 0.0,
                    )
                }
                val languageSignal = MultilingualTokenScorer.score(
                    localeEvidence = localeEvidence,
                    typedWordKnownByUserDictionary = typedWordKnownByUserDictionary,
                    candidateMatchesTypedWord = typedWordKey == key,
                    candidateIsEligibleForAutoCommit = candidate.isEligibleForAutoCommit,
                    candidateCompletesTypedWord = typedWordKey?.let { typedKey ->
                        typedKey.length >= MinLanguageSwitchPrefixLength &&
                            key != typedKey &&
                            key.startsWith(typedKey)
                    } ?: false,
                    candidateConflictsWithTypedPrefix = typedWordKey?.let { typedKey ->
                        typedKey.length >= MinLanguageSwitchPrefixLength &&
                            key != typedKey &&
                            !key.startsWith(typedKey)
                    } ?: false,
                    contextLocaleHasTypedPrefixCandidate = contextLocaleHasTypedPrefixCandidate,
                )
                val dictionaryFrequency = languageSignal.dictionaryFrequency
                val bigramProbability = previousWords.prev1?.let { prev1 ->
                    bigramStore.score(prev1, candidateText, locale)
                } ?: 0.0
                val trigramProbability = if (previousWords.prev2 != null && previousWords.prev1 != null) {
                    trigramStore.score(previousWords.prev2, previousWords.prev1, candidateText, locale)
                } else {
                    0.0
                }
                val coldStartProbability = ColdStartNextWordPriors.score(
                    textBeforeCursor = contextPrefix,
                    languageCode = locale.language,
                    candidateWord = candidateText,
                    maxCandidateCount = maxOf(MaxColdStartContextCandidates, candidates.size),
                )
                val contextProbability = maxOf(
                    bigramProbability * BigramContextWeight,
                    trigramProbability,
                    coldStartProbability,
                ).coerceIn(0.0, 1.0)
                val languageConfidence = if (localeCount <= 1) 1.0 else languageSignal.languageConfidence
                val outcomeSignal = correctionOutcomePriors.signal(
                    originalText = currentWord,
                    correctedText = candidateText,
                )
                put(
                    key,
                    SwiftKeyCandidateSignals(
                        dictionaryFrequency = dictionaryFrequency,
                        contextProbability = contextProbability,
                        languageConfidence = languageConfidence,
                        acceptedCorrectionConfidence = outcomeSignal.acceptedConfidence,
                        rejectionPenalty = maxOf(
                            autoCommitSuppression.rejectedPairPenalty(
                                currentWord = currentWord,
                                candidateText = candidateText,
                                currentWordStart = currentWordStart,
                            ),
                            outcomeSignal.rejectedConfidence,
                        ),
                        candidateLanguage = dominantCandidateLanguage(locales, localeEvidence),
                    ),
                )
            }
        }
    }

    private fun dominantCandidateLanguage(
        locales: List<FlorisLocale>,
        localeEvidence: List<TokenLocaleEvidence>,
    ): String? {
        var bestIndex = -1
        var bestFrequency = 0.0
        var secondFrequency = 0.0
        for (index in localeEvidence.indices) {
            val candidateFrequency = localeEvidence[index].candidateFrequency
            if (candidateFrequency > bestFrequency) {
                secondFrequency = bestFrequency
                bestFrequency = candidateFrequency
                bestIndex = index
            } else if (candidateFrequency > secondFrequency) {
                secondFrequency = candidateFrequency
            }
        }
        if (bestFrequency <= 0.0 || bestFrequency - secondFrequency <= CandidateLanguageTieTolerance) {
            return null
        }
        return locales.getOrNull(bestIndex)?.language?.takeIf { it.isNotBlank() }
    }

    private fun immediateAutoCommitCandidate(currentWord: String): SuggestionCandidate? {
        if (!prefs.suggestion.enabled.get()) {
            return null
        }
        return ImmediateAutocorrect.englishContractionCandidate(
            rawWord = currentWord,
            languageCode = subtypeManager.activeSubtype.primaryLocale.language,
        )
    }

    private fun immediatePhraseRepairCandidate(currentWord: String): SuggestionCandidate? {
        if (!prefs.suggestion.enabled.get()) {
            return null
        }
        return ImmediateAutocorrect.englishPhraseRepairCandidate(
            rawWord = currentWord,
            languageCode = subtypeManager.activeSubtype.primaryLocale.language,
        )
    }

    private fun onUserDictionaryConfigurationChanged() {
        dictionaryManager.syncUserDictionaryStoresWithPreferences()
        wordsListCache.clear()
        frequencyCache.evictAll()
        assembleCandidates()
    }

    private fun assembleCandidates() {
        scope.launch {
            val suggestions = internalSuggestionsGuard.withLock {
                internalSuggestions.second
            }
            val candidates = candidateAssembler.assemble(
                isSuggestionOn = isSuggestionOn(),
                internalSuggestions = suggestions,
                autoCommitUndoCandidate = autoCommitUndoSession.activeUndoCandidate,
                glideAlternativeCandidates = glideAlternativeSession.activeCandidates(),
            )
            activeCandidates = candidates
            autoExpandCollapseSmartbarActions(candidates, NlpInlineAutofill.suggestions.value)
        }
    }

    fun autoExpandCollapseSmartbarActions(list1: List<*>?, list2: List<*>?) {
        smartbarAutoExpandController.onCandidateStateChanged(list1, list2)
    }

    fun addToDebugOverlay(word: String, info: SpellingResult) {
        val editorInfo = editorInstance.activeInfo
        val isNoPersonalizedLearningField = editorInfo.imeOptions.flagNoPersonalizedLearning
        val canExposeRawContent = DevtoolsContentPolicy.canExposeRawContent(
            isPasswordOrSensitiveField = keyboardManager.activeState.keyVariation == KeyVariation.PASSWORD || (
                SensitiveFieldGuard.isSensitive(
                    inputType = editorInfo.inputAttributes.raw,
                    imeOptions = editorInfo.imeOptions.raw,
                ) && !isNoPersonalizedLearningField
            ),
            isIncognitoMode = keyboardManager.activeState.isIncognitoMode,
            isNoPersonalizedLearningField = isNoPersonalizedLearningField,
        )
        if (!canExposeRawContent) {
            if (debugOverlaySuggestionsInfos.size() > 0) {
                clearDebugOverlay()
            }
            return
        }
        debugOverlaySuggestionsInfos.put(System.currentTimeMillis(), word to info)
        debugOverlayVersion.update { it + 1 }
    }

    fun clearDebugOverlay() {
        debugOverlaySuggestionsInfos.evictAll()
        debugOverlayVersion.update { it + 1 }
    }

    private fun EditorContent.autoCommitWord(): String {
        return currentWordText.ifBlank { composingText }
    }

    private fun EditorContent.autoCommitWordStart(): Int? {
        return when {
            currentWord.isValid -> currentWord.start
            composing.isValid -> composing.start
            else -> null
        }
    }

    private fun String.normalizedCandidateSignalKey(): String? {
        val normalized = trim()
            .trim { char -> !char.isLetter() && char != '\'' && char != '\u2019' }
            .lowercase()
        if (normalized.isBlank() || normalized.none { it.isLetter() }) return null
        return normalized
    }

    private companion object {
        const val BigramContextWeight = 0.75
        const val MaxColdStartContextCandidates = 16
        const val MaxLanguageContextWords = 4
        const val MinLanguageSwitchPrefixLength = 2
        const val CandidateLanguageTieTolerance = 0.0001

        /**
         * docs/archive/SWIFTKEY_PARITY_ROADMAP_2026-05-17 §B4 — geometric decay
         * factor for the trailing-word language-evidence blend. The
         * most-recent word weighs 1.0 and each word further back is
         * scaled by this factor:
         *
         *   weight[0] (most recent)    = 1.0
         *   weight[1]                  = 0.7
         *   weight[2]                  = 0.49
         *   weight[3] (4 back, oldest) = 0.343
         *
         * Empirically — across the trailing-window length of 4 —
         * 0.7 gives roughly a 3× preference for the most-recent
         * word over the oldest one, which is enough to smoothly
         * track a mid-sentence language switch without flipping
         * the locale on the first recognised word.
         */
        const val TrailingContextDecay = 0.7
    }

    inner class ClipboardSuggestionProvider internal constructor(private val context: Context) : SuggestionProvider {
        private var lastClipboardItemId: Long = -1
        private val keyguardManager = context.systemServiceOrNull(AndroidKeyguardManager::class)

        override val providerId = "org.florisboard.nlp.providers.clipboard"

        override suspend fun create() {
            // Do nothing
        }

        override suspend fun preload(subtype: Subtype) {
            // Do nothing
        }

        override suspend fun suggest(
            subtype: Subtype,
            content: EditorContent,
            maxCandidateCount: Int,
            allowPossiblyOffensive: Boolean,
            isPrivateSession: Boolean,
        ): List<SuggestionCandidate> {
            // Check if enabled
            if (!prefs.clipboard.suggestionEnabled.get()) return emptyList()

            // Suppress on locked screen. Matches the existing UI-side gate in `ClipboardInputLayout` so a recently
            // copied 2FA code / OTP / password / address never surfaces in the smartbar above the keyboard while the
            // device is behind the lock screen. See `ClipboardSuggestionLockGate`.
            val keyguard = keyguardManager
            if (keyguard != null && ClipboardSuggestionLockGate.shouldSuppress(
                    isDeviceLocked = keyguard.isDeviceLocked,
                    isKeyguardLocked = keyguard.isKeyguardLocked,
                )
            ) {
                return emptyList()
            }

            val currentItem = validateClipboardItem(clipboardManager.primaryClip, lastClipboardItemId, content.text)
                ?: return emptyList()

            return buildList {
                val now = System.currentTimeMillis()
                if ((now - currentItem.creationTimestampMs) < prefs.clipboard.suggestionTimeout.get() * 1000) {
                    add(ClipboardSuggestionCandidate(currentItem, sourceProvider = this@ClipboardSuggestionProvider, context = context))
                    if (currentItem.isSensitive) {
                        return@buildList
                    }
                    if (currentItem.type == ItemType.TEXT) {
                        val text = currentItem.stringRepresentation()
                        val matches = buildList {
                            addAll(NetworkUtils.getEmailAddresses(text))
                            addAll(NetworkUtils.getUrls(text))
                            addAll(NetworkUtils.getPhoneNumbers(text))
                        }
                        matches.forEachIndexed { i, match ->
                            val isUniqueMatch = matches.subList(0, i).all { prevMatch ->
                                // Endpoint comparison instead of IntRange.intersect():
                                // intersect() materializes both ranges into sets, which
                                // is O(text length) garbage per match pair on every
                                // suggestion request while a fresh clip is suggestible.
                                prevMatch.value != match.value &&
                                    (prevMatch.range.last < match.range.first || prevMatch.range.first > match.range.last)
                            }
                            if (match.value != text && isUniqueMatch) {
                                add(ClipboardSuggestionCandidate(
                                    clipboardItem = currentItem.copy(
                                        // TODO: adjust regex of phone number so we don't need to manually strip the
                                        //  parentheses from the match results
                                        text = if (match.value.startsWith("(") && match.value.endsWith(")")) {
                                            match.value.substring(1, match.value.length - 1)
                                        } else {
                                            match.value
                                        }
                                    ),
                                    sourceProvider = this@ClipboardSuggestionProvider,
                                    context = context,
                                ))
                            }
                        }
                    }
                }
            }
        }

        override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
            if (candidate is ClipboardSuggestionCandidate) {
                lastClipboardItemId = candidate.clipboardItem.id
            }
        }

        override suspend fun notifySuggestionReverted(subtype: Subtype, candidate: SuggestionCandidate) {
            // Do nothing
        }

        override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean {
            if (candidate is ClipboardSuggestionCandidate) {
                lastClipboardItemId = candidate.clipboardItem.id
                return true
            }
            return false
        }

        override suspend fun getListOfWords(subtype: Subtype): List<String> {
            return emptyList()
        }

        override suspend fun getFrequencyForWord(subtype: Subtype, word: String): Double {
            return 0.0
        }

        override suspend fun destroy() {
            // Do nothing
        }

        private fun validateClipboardItem(currentItem: ClipboardItem?, lastItemId: Long, contentText: String) =
            currentItem?.takeIf {
                // Check if already used
                it.id != lastItemId
                    // Check if content is empty
                    && contentText.isBlank()
                    // Check if clipboard content has any valid characters
                    && !currentItem.text.isNullOrBlank()
                    && !blankStrRegex.matches(currentItem.text)
            }
    }
}
