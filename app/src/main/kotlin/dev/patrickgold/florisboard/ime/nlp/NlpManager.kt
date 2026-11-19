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
import dev.patrickgold.florisboard.ime.dictionary.LearnedWordText
import dev.patrickgold.florisboard.ime.dictionary.PersonalBigramStore
import dev.patrickgold.florisboard.ime.dictionary.PersonalTrigramStore
import dev.patrickgold.florisboard.ime.editor.EditorCompatibilityPolicy
import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.editor.EditorRange
import dev.patrickgold.florisboard.ime.media.emoji.EmojiSuggestionProvider
import dev.patrickgold.florisboard.ime.nlp.context.ContextModelStore
import dev.patrickgold.florisboard.ime.nlp.latin.ColdStartNextWordPriors
import dev.patrickgold.florisboard.ime.profile.PerAppKeyboardProfilePolicy
import dev.patrickgold.florisboard.ime.profile.PerAppKeyboardProfiles
import dev.patrickgold.florisboard.ime.profile.PerAppSuggestionAggressiveness
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
    // FrequencyLookupCache never stores a lookup that a newer keystroke cancelled.
    private val wordsListCache = ConcurrentHashMap<String, List<String>>()
    private val frequencyCache = FrequencyLookupCache(5000)
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
    // Set together with internalSuggestions, under its guard.
    private var internalCandidateSignals: Map<String, SwiftKeyCandidateSignals> = emptyMap()
    private var internalSuggestionsRankedFor: CandidateWordKey? = null
    // Whether the last ranked word was in a dictionary; read by the space key's learning.
    private val typedWordKnowledge = TypedWordKnowledge()
    private val learnedWordForgetOffer = LearnedWordForgetOffer()
    // What auto-correct reads: the strip as shown, with the word it was ranked for.
    @Volatile private var publishedCandidates = PublishedCandidates()
    private val candidateAssembly = LatestOnlyAssembly<PublishedCandidates>(scope) { published ->
        publishedCandidates = published
        activeCandidates = published.candidates
        autoExpandCollapseSmartbarActions(published.candidates, NlpInlineAutofill.suggestions.value)
    }

    val debugOverlaySuggestionsInfos = LruCache<Long, Pair<String, SpellingResult>>(10)
    var debugOverlayVersion = MutableStateFlow(0)

    init {
        clipboardManager.primaryClipFlow.collectLatestIn(scope) {
            assembleCandidates()
        }
        prefs.suggestion.enabled.asFlow().collectLatestIn(scope) {
            assembleCandidates()
        }
        prefs.privacy.perAppKeyboardProfiles.asFlow().collectLatestIn(scope) {
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
        wordSuggestionsEnabled()
            || prefs.emoji.suggestionEnabled.get()
            || prefs.clipboard.suggestionEnabled.get()
            || providerForcesSuggestionOn(subtypeManager.activeSubtype)

    /**
     * The global "Display suggestions" switch as it applies to the focused app.
     * A per-app profile set to "Off for this app" turns word suggestions and
     * corrections off there. It used to turn only composing off, so the strip
     * kept suggesting and a space still corrected the word.
     */
    private fun wordSuggestionsEnabled(): Boolean {
        val globalEnabled = prefs.suggestion.enabled.get()
        if (!globalEnabled) return false
        val rawJson = prefs.privacy.perAppKeyboardProfiles.get()
        val packageName = editorInstance.activeInfo.packageName
        val cached = perAppWordSuggestions
        if (cached != null && cached.rawJson == rawJson && cached.packageName == packageName) {
            return cached.enabled
        }
        val enabled = PerAppKeyboardProfilePolicy.shouldOfferWordSuggestions(
            globalEnabled = true,
            suggestions = PerAppKeyboardProfiles.resolve(rawJson, packageName)?.suggestions
                ?: PerAppSuggestionAggressiveness.FOLLOW_GLOBAL,
        )
        perAppWordSuggestions = PerAppWordSuggestions(rawJson, packageName, enabled)
        return enabled
    }

    /** The last app's answer, so typing doesn't parse the profiles on every key. */
    private class PerAppWordSuggestions(val rawJson: String, val packageName: String?, val enabled: Boolean)
    @Volatile private var perAppWordSuggestions: PerAppWordSuggestions? = null

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
            wordSuggestionEnabled = wordSuggestionsEnabled(),
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
            // A rare dictionary word one key off a far more common one ("thos") is decoded as unknown so
            // space can correct it, and learning treats it as unknown too: a known word is learned on its
            // first use, and a learned word is never corrected again, so one uncorrected slip would stick.
            val typedWordLooksLikeSlip = typedWordKnown &&
                suggestionProvider.isLikelySlip(subtype, content, currentWord) &&
                activeLocales(subtype).none { locale ->
                    currentWord.normalizedCandidateSignalKey()
                        ?.let { dictionaryManager.isKnownUserDictionaryWord(it, locale) } == true
                }
            val decoderContext = SwiftKeyDecoderContext(
                currentWord = currentWord,
                maxCandidateCount = 8,
                typedWordKnown = typedWordKnown && !typedWordLooksLikeSlip,
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
                    internalCandidateSignals = candidateSignals
                    internalSuggestionsRankedFor = content.candidateWordKey()
                    typedWordKnowledge.record(
                        word = currentWord,
                        languageTag = subtype.primaryLocale.languageTag(),
                        known = (typedWordKnown && !typedWordLooksLikeSlip).takeIf { suggestionsEnabled },
                    )
                    val merged = InternalSuggestionOrder.merge(
                        wordSuggestions = wordSuggestions,
                        emojiSuggestions = emojiSuggestions,
                        ghostTextCandidate = ghostTextCandidate,
                        composingText = content.composingText,
                        emojiTriggerPrefix = prefs.emoji.suggestionType.get().prefix,
                    )
                    val forgetChip = learnedWordForgetOffer.chipFor(currentWord, content.textBeforeSelection, requestId)
                    internalSuggestions = requestId to
                        (forgetChip?.let { LearnedWordForgetOffer.place(it, merged) } ?: merged)
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
                    internalCandidateSignals = emptyMap()
                    internalSuggestionsRankedFor = null
                    typedWordKnowledge.clear()
                    internalSuggestions = requestId to suggestions
                }
            }
        }
    }

    fun clearSuggestions() {
        glideAlternativeSession.clear()
        learnedWordForgetOffer.clear()
        val requestId = suggestionsRequestCounter.incrementAndGet()
        scope.launch {
            internalSuggestionsGuard.withLock {
                if (internalSuggestions.first < requestId) {
                    internalCandidateSignals = emptyMap()
                    internalSuggestionsRankedFor = null
                    typedWordKnowledge.clear()
                    internalSuggestions = requestId to emptyList()
                }
            }
        }
    }

    /**
     * Whether the last suggestion pass found [word] in the learned words or a dictionary for
     * [locale], or `null` when no pass covered it; [lookUpTypedWordKnown] answers then.
     */
    fun typedWordKnownState(word: String, locale: FlorisLocale): Boolean? =
        typedWordKnowledge.knownState(word, locale.languageTag())

    /**
     * Looks [word] up the way a suggestion pass would, for a word typed faster than its pass or
     * with suggestions off. A word whose learned form differs from the looked-up one (a hyphen at
     * either end) counts as unknown, since that form is what would be learned.
     */
    suspend fun lookUpTypedWordKnown(word: String, subtype: Subtype): Boolean {
        val learnedForm = LearnedWordText.normalize(word) ?: return false
        if (learnedForm != candidateSignalKeyOf(word)) return false
        return isKnownTypedWord(providerRegistry.suggestionProvider(subtype), subtype, learnedForm)
    }

    /**
     * Shows [candidate], the chip that forgets a word the space just learned, on the next
     * passes while the cursor stays right after that word (see [LearnedWordForgetOffer]).
     */
    fun offerForgetLearnedWord(candidate: LearnedWordForgetSuggestionCandidate) {
        learnedWordForgetOffer.offer(candidate, latestRequestId = suggestionsRequestCounter.get())
    }

    /**
     * Adds the offered chip to the strip the last pass published, if that pass was ranked for
     * [content]'s spot. For an offer made after the space's own pass had already published (the
     * word's lookup finished later). Running a new pass from there would race the content
     * collectors, which own [suggest].
     */
    fun showForgetLearnedWordChip(content: EditorContent) {
        val wordKey = content.candidateWordKey()
        val textBeforeSelection = content.textBeforeSelection
        scope.launch {
            internalSuggestionsGuard.withLock {
                val (requestId, suggestions) = internalSuggestions
                if (internalSuggestionsRankedFor != wordKey) return@withLock
                if (suggestions.any { it is LearnedWordForgetSuggestionCandidate }) return@withLock
                val chip = learnedWordForgetOffer.chipAfter(textBeforeSelection) ?: return@withLock
                internalSuggestions = requestId to LearnedWordForgetOffer.place(chip, suggestions)
            }
        }
    }

    /** Ends the "Don't learn" offer, for when the editor changes or the keyboard hides. */
    fun clearForgetLearnedWordOffer() {
        learnedWordForgetOffer.clear()
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
        val published = publishedCandidates.forAutoCommit(content.candidateWordKey())
        return CandidateAutoCommitPolicy.selectAutoCommitCandidate(
            autoCorrectEnabled = prefs.correction.autoCorrect.get(),
            autoCorrectCommitMode = prefs.correction.autoCorrectCommitMode.get(),
            autoCorrectConfidenceThreshold = AutoCorrectConfidencePolicy.thresholdFor(
                prefs.correction.autoCorrectConfidenceThreshold.get(),
            ),
            keyVariation = keyboardManager.activeState.keyVariation,
            currentWord = currentWord,
            currentWordStart = currentWordStart,
            candidates = published.candidates,
            candidateSignals = published.signals,
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
        val published = publishedCandidates.forAutoCommit(content.candidateWordKey())
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
            candidates = published.candidates,
            candidateSignals = published.signals,
            rejectionPolicy = autoCommitSuppression,
            userDictionaryShortcutCandidate = userDictionaryShortcutAutoCommitCandidate(currentWord),
            immediatePhraseRepairCandidate = immediatePhraseRepairCandidate(currentWord),
            immediateAutoCommitCandidate = immediateAutoCommitCandidate(currentWord),
        )
    }

    fun shouldSuppressPlainSpaceForPrediction(): Boolean {
        val content = editorInstance.activeContent
        val published = publishedCandidates.forAutoCommit(content.candidateWordKey())
        return CandidateAutoCommitPolicy.shouldSuppressPlainSpaceForPrediction(
            quickPredictionInsertEnabled = prefs.correction.quickPredictionInsert.get(),
            currentWord = content.autoCommitWord(),
            textBeforeCursor = content.textBeforeSelection,
            candidates = published.candidates,
            candidateSignals = published.signals,
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

    /**
     * Takes back the correction the cursor sits right after, when backspace undoes it. Returns
     * the word the user had typed before that correction, or `null` when nothing was taken back.
     */
    fun rejectAcceptedAutoCommitOnBackspace(content: EditorContent): String? {
        val cursorPosition = content.selection.takeIf { it.isValid }?.start
        val rejected = autoCommitSuppression.rejectAccepted(
            textBeforeSelection = content.textBeforeSelection,
            cursorPosition = cursorPosition,
        )
        if (!rejected) return null
        val pair = autoCommitSuppression.consumeLastRejectedPair()
        if (pair != null) {
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
        return pair?.original
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

    /**
     * Empties the lookups that refill on demand: word frequencies, the per-subtype word lists
     * (the glide classifier keeps its own reference to the active one) and the debug overlay.
     */
    fun trimMemory() {
        frequencyCache.evictAll()
        wordsListCache.clear()
        debugOverlaySuggestionsInfos.evictAll()
        // The overlay redraws on a version change, so it stops showing what was just dropped.
        debugOverlayVersion.update { it + 1 }
    }

    /** How many entries the frequency and word list caches hold, for tests. */
    internal fun cacheSizes(): Pair<Int, Int> = frequencyCache.size() to wordsListCache.size

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
        return frequencyCache.getOrLookup(cacheKey) {
            suggestionProvider.getFrequencyForWord(localeSubtype, normalizedWord)
        }
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
        val bigramStore = PersonalBigramStore.get(appContext)
        val trigramStore = PersonalTrigramStore.get(appContext)
        val sources = object : CandidateSignalSources {
            override suspend fun frequency(locale: FlorisLocale, word: String): Double {
                return frequencyForWordInLocale(suggestionProvider, subtype, locale, word)
            }

            override fun isKnownUserDictionaryWord(word: String, locale: FlorisLocale): Boolean {
                return dictionaryManager.isKnownUserDictionaryWord(word, locale)
            }

            override suspend fun bigramScore(previousWord: String, word: String, locale: FlorisLocale): Double {
                return bigramStore.score(previousWord, word, locale)
            }

            override suspend fun trigramScore(
                secondPreviousWord: String,
                previousWord: String,
                word: String,
                locale: FlorisLocale,
            ): Double {
                return trigramStore.score(secondPreviousWord, previousWord, word, locale)
            }

            override suspend fun contextModelProbability(
                secondPreviousWord: String?,
                previousWord: String,
                word: String,
                locale: FlorisLocale,
            ): Double {
                val model = ContextModelStore.modelFor(appContext, locale.language) ?: return 0.0
                return model.contextProbability(secondPreviousWord, previousWord, word) ?: 0.0
            }

            override fun outcomeSignal(originalText: CharSequence, correctedText: CharSequence): CorrectionOutcomeSignal {
                return correctionOutcomePriors.signal(originalText = originalText, correctedText = correctedText)
            }

            override fun rejectedPairPenalty(
                currentWord: CharSequence,
                candidateText: CharSequence,
                currentWordStart: Int?,
            ): Double {
                return autoCommitSuppression.rejectedPairPenalty(
                    currentWord = currentWord,
                    candidateText = candidateText,
                    currentWordStart = currentWordStart,
                )
            }
        }
        return CandidateSignalAssembler.assemble(
            primaryLocale = subtype.primaryLocale,
            locales = activeLocales(subtype),
            textBeforeSelection = content.textBeforeSelection,
            currentWord = currentWord,
            currentWordStart = currentWordStart,
            candidates = candidates,
            sources = sources,
        )
    }

    private fun immediateAutoCommitCandidate(currentWord: String): SuggestionCandidate? {
        if (!wordSuggestionsEnabled()) {
            return null
        }
        return ImmediateAutocorrect.englishContractionCandidate(
            rawWord = currentWord,
            languageCode = subtypeManager.activeSubtype.primaryLocale.language,
        )
    }

    private fun immediatePhraseRepairCandidate(currentWord: String): SuggestionCandidate? {
        if (!wordSuggestionsEnabled()) {
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
        // Only the newest request publishes, so a slow older assembly can't bring back
        // candidates that a newer, emptier state already cleared.
        candidateAssembly.request {
            val (suggestions, signals, rankedFor) = internalSuggestionsGuard.withLock {
                Triple(internalSuggestions.second, internalCandidateSignals, internalSuggestionsRankedFor)
            }
            PublishedCandidates(
                candidates = candidateAssembler.assemble(
                    isSuggestionOn = isSuggestionOn(),
                    internalSuggestions = suggestions,
                    autoCommitUndoCandidate = autoCommitUndoSession.activeUndoCandidate,
                    glideAlternativeCandidates = glideAlternativeSession.activeCandidates(),
                ),
                signals = signals,
                rankedFor = rankedFor,
            )
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

    private fun EditorContent.candidateWordKey(): CandidateWordKey {
        return CandidateWordKey(
            word = autoCommitWord(),
            wordStart = autoCommitWordStart(),
            cursor = selection.start,
        )
    }

    private fun EditorContent.autoCommitWordStart(): Int? {
        return when {
            currentWord.isValid -> currentWord.start
            composing.isValid -> composing.start
            else -> null
        }
    }

    private fun String.normalizedCandidateSignalKey(): String? = candidateSignalKeyOf(this)

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
