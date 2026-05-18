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
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.dictionary.DictionaryManager
import dev.patrickgold.florisboard.ime.dictionary.PersonalBigramStore
import dev.patrickgold.florisboard.ime.dictionary.PersonalTrigramStore
import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.editor.EditorRange
import dev.patrickgold.florisboard.ime.media.emoji.EmojiSuggestionProvider
import dev.patrickgold.florisboard.ime.nlp.latin.ColdStartNextWordPriors
import dev.patrickgold.florisboard.ime.text.key.KeyVariation
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.lib.FlorisLocale
import dev.patrickgold.florisboard.lib.util.NetworkUtils
import dev.patrickgold.florisboard.subtypeManager
import org.florisboard.lib.android.AndroidKeyguardManager
import org.florisboard.lib.android.systemServiceOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.florisboard.lib.kotlin.collectLatestIn
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentHashMap
import kotlin.properties.Delegates

private const val BLANK_STR_PATTERN = "^\\s*$"

class NlpManager(context: Context) {
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
    private val correctionOutcomePriors = CorrectionOutcomePriors.get(appContext)
    private val touchDecoderEvidence = TouchDecoderEvidenceBuffer()

    private val suggestionsRequestCounter = AtomicLong(0L)
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
        return providerRegistry.spellingProvider(subtype).spell(
            subtype = subtype,
            word = word,
            precedingWords = precedingWords,
            followingWords = followingWords,
            maxSuggestionCount = maxSuggestionCount,
            allowPossiblyOffensive = !prefs.suggestion.blockPossiblyOffensive.get(),
            isPrivateSession = keyboardManager.activeState.isIncognitoMode,
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

    fun suggest(subtype: Subtype, content: EditorContent) {
        autoCommitSuppression.onContentChanged(content.autoCommitWord(), content.autoCommitWordStart())
        val requestId = suggestionsRequestCounter.incrementAndGet()
        scope.launch {
            val emojiSuggestions = when {
                prefs.emoji.suggestionEnabled.get() -> {
                    emojiSuggestionProvider.suggest(
                        subtype = subtype,
                        content = content,
                        maxCandidateCount = prefs.emoji.suggestionCandidateMaxCount.get(),
                        allowPossiblyOffensive = !prefs.suggestion.blockPossiblyOffensive.get(),
                        isPrivateSession = keyboardManager.activeState.isIncognitoMode,
                    )
                }
                else -> emptyList()
            }
            val suggestionProvider = providerRegistry.suggestionProvider(subtype)
            val suggestionsEnabled = prefs.suggestion.enabled.get() || suggestionProvider.forcesSuggestionOn
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
                suggestionProvider.suggest(
                    subtype = subtype,
                    content = content,
                    maxCandidateCount = 8,
                    allowPossiblyOffensive = !prefs.suggestion.blockPossiblyOffensive.get(),
                    isPrivateSession = keyboardManager.activeState.isIncognitoMode,
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
                )
            }
            // ROADMAP §0 P1 — Smart-Compose inline ghost-text. When a
            // SmartComposeProvider addon is bound, ask it for a multi-token
            // continuation given the preceding text. The default no-op
            // provider returns NoSuggestion, so this path is invisible
            // until the L1.1a addon is installed.
            val ghostTextCandidate = if (suggestionsEnabled) {
                buildGhostTextCandidate(subtype, content, currentWord)
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

    private fun buildGhostTextCandidate(
        subtype: Subtype,
        content: EditorContent,
        currentWord: String,
    ): GhostTextSuggestionCandidate? {
        val provider = dev.patrickgold.florisboard.ime.smartcompose
            .SmartComposeProviderRegistry.active
        val locale = subtype.primaryLocale.languageTag()
        if (!provider.isReady(locale)) return null
        val context = dev.patrickgold.florisboard.ime.smartcompose.SmartComposeContext(
            precedingText = content.textBeforeSelection.toString(),
            composingPrefix = currentWord,
            locale = locale,
            editorPackageName = null,
        )
        val result = provider.predictNextTokens(context, maxCandidates = 1)
        val top = (result as? dev.patrickgold.florisboard.ime.smartcompose
            .SmartComposeResult.Suggestion)?.candidates?.firstOrNull()
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

    internal fun recordTouchDecoderSample(primaryText: String, alternatives: List<TouchDecoderCandidate>) {
        if (!prefs.correction.autoCorrect.get()) return
        if (keyboardManager.activeState.isIncognitoMode) return
        if (keyboardManager.activeState.keyVariation == KeyVariation.PASSWORD) return
        touchDecoderEvidence.record(
            TouchDecoderSample(
                primaryText = primaryText,
                alternatives = alternatives,
            )
        )
    }

    fun getAutoCommitCandidate(): SuggestionCandidate? {
        if (!prefs.correction.autoCorrect.get()) {
            return null
        }
        val content = editorInstance.activeContent
        val currentWord = content.autoCommitWord()
        val currentWordStart = content.autoCommitWordStart()
        if (autoCommitSuppression.shouldKeepTypedLiteral(
                currentWord = currentWord,
                currentWordStart = currentWordStart,
            )
        ) {
            return null
        }

        // ROADMAP §6 N5.4 — Personal-dictionary shortcut auto-replace runs *before*
        // the in-strip auto-commit candidate and the English contraction fallback,
        // because user-defined shortcuts ("omw" → "on my way") express explicit user
        // intent that should win over algorithmic guesses.
        userDictionaryShortcutAutoCommitCandidate(currentWord, currentWordStart)?.let { return it }
        immediatePhraseRepairCandidate(currentWord, currentWordStart)?.let { return it }

        val activeCandidate = activeCandidates.firstOrNull { candidate ->
            candidate.isEligibleForAutoCommit &&
                SwiftKeyCandidateRanker.languageConfidenceAllowsAutoCommit(candidate, activeCandidateSignals) &&
                !autoCommitSuppression.shouldSuppress(
                    currentWord = currentWord,
                    candidateText = candidate.text,
                    currentWordStart = currentWordStart,
                )
        }
        if (activeCandidate != null) {
            return activeCandidate
        }
        return immediateAutoCommitCandidate(currentWord, currentWordStart)
    }

    fun getSpacebarCandidate(): SuggestionCandidate? {
        val autoCorrectEnabled = prefs.correction.autoCorrect.get()
        val quickPredictionInsertEnabled = prefs.correction.quickPredictionInsert.get()
        if (!autoCorrectEnabled && !quickPredictionInsertEnabled) {
            return null
        }
        val content = editorInstance.activeContent
        val currentWord = content.autoCommitWord()
        val currentWordStart = content.autoCommitWordStart()
        if (autoCommitSuppression.shouldKeepTypedLiteral(
                currentWord = currentWord,
                currentWordStart = currentWordStart,
            )
        ) {
            return null
        }

        if (autoCorrectEnabled) {
            userDictionaryShortcutAutoCommitCandidate(currentWord, currentWordStart)?.let { return it }
            immediatePhraseRepairCandidate(currentWord, currentWordStart)?.let { return it }
        }

        val candidate = SwiftKeyCandidateRanker.selectSpacebarCandidate(
            currentWord = currentWord,
            candidates = activeCandidates,
            quickPredictionInsert = quickPredictionInsertEnabled,
            textBeforeCursor = content.textBeforeSelection,
            candidateSignals = activeCandidateSignals,
        ) ?: return if (autoCorrectEnabled) {
            immediateAutoCommitCandidate(currentWord, currentWordStart)
        } else {
            null
        }

        return candidate.takeUnless {
            autoCommitSuppression.shouldSuppress(
                currentWord = currentWord,
                candidateText = it.text,
                currentWordStart = currentWordStart,
            )
        }
    }

    fun shouldSuppressPlainSpaceForPrediction(): Boolean {
        if (!prefs.correction.quickPredictionInsert.get()) {
            return false
        }
        val content = editorInstance.activeContent
        if (content.autoCommitWord().isNotBlank()) {
            return false
        }
        return SwiftKeyCandidateRanker.selectSpacebarCandidate(
            currentWord = "",
            candidates = activeCandidates,
            quickPredictionInsert = true,
            textBeforeCursor = content.textBeforeSelection,
            candidateSignals = activeCandidateSignals,
        ) != null
    }

    private fun userDictionaryShortcutAutoCommitCandidate(
        currentWord: String,
        currentWordStart: Int?,
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
        if (autoCommitSuppression.shouldSuppress(
                currentWord = currentWord,
                candidateText = expansion,
                currentWordStart = currentWordStart,
            )
        ) {
            return null
        }
        return candidate
    }

    fun rememberAcceptedAutoCommit(content: EditorContent, candidate: SuggestionCandidate) {
        val originalText = content.autoCommitWord()
        autoCommitSuppression.rememberAccepted(
            originalText = originalText,
            correctedText = candidate.text,
            wordStart = content.autoCommitWordStart(),
        )
        correctionOutcomePriors.recordAccepted(
            originalText = originalText,
            correctedText = candidate.text,
        )
        typingTraceRecorder.recordAutoCommitAccepted(content, candidate)
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
            typingTraceRecorder.recordAutoCommitRejected(content)
        }
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
        val personalScore = PersonalBigramStore.get(appContext).score(previousWord, nextWord, locale)
        val coldStartScore = ColdStartNextWordPriors
            .suggest(
                textBeforeCursor = "${previousWord.trim()} ",
                languageCode = locale.language,
                maxCandidateCount = 8,
            )
            .firstOrNull { prior -> prior.word.equals(nextWord, ignoreCase = true) }
            ?.confidence
            ?: 0.0
        return maxOf(personalScore, coldStartScore).coerceIn(0.0, 1.0)
    }

    private fun userDictionarySuggestions(
        subtype: Subtype,
        content: EditorContent,
        maxCandidateCount: Int,
    ): List<SuggestionCandidate> {
        val currentWord = content.currentWordText.ifBlank { content.composingText }
        if (maxCandidateCount <= 0 || currentWord.isBlank()) {
            return emptyList()
        }
        return dictionaryManager.queryUserDictionary(currentWord, subtype.primaryLocale).take(maxCandidateCount)
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
        // SWIFTKEY_PARITY_ROADMAP_2026-05-17 §B4 — same-sentence
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

    private fun immediateAutoCommitCandidate(currentWord: String, currentWordStart: Int?): SuggestionCandidate? {
        if (!prefs.suggestion.enabled.get()) {
            return null
        }
        val candidate = ImmediateAutocorrect.englishContractionCandidate(
            rawWord = currentWord,
            languageCode = subtypeManager.activeSubtype.primaryLocale.language,
        ) ?: return null
        return candidate.takeUnless {
            autoCommitSuppression.shouldSuppress(
                currentWord = currentWord,
                candidateText = it.text,
                currentWordStart = currentWordStart,
            )
        }
    }

    private fun immediatePhraseRepairCandidate(currentWord: String, currentWordStart: Int?): SuggestionCandidate? {
        if (!prefs.suggestion.enabled.get()) {
            return null
        }
        val candidate = ImmediateAutocorrect.englishPhraseRepairCandidate(
            rawWord = currentWord,
            languageCode = subtypeManager.activeSubtype.primaryLocale.language,
        ) ?: return null
        return candidate.takeUnless {
            autoCommitSuppression.shouldSuppress(
                currentWord = currentWord,
                candidateText = it.text,
                currentWordStart = currentWordStart,
            )
        }
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
            )
            activeCandidates = candidates
            autoExpandCollapseSmartbarActions(candidates, NlpInlineAutofill.suggestions.value)
        }
    }

    fun autoExpandCollapseSmartbarActions(list1: List<*>?, list2: List<*>?) {
        smartbarAutoExpandController.onCandidateStateChanged(list1, list2)
    }

    fun addToDebugOverlay(word: String, info: SpellingResult) {
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
         * SWIFTKEY_PARITY_ROADMAP_2026-05-17 §B4 — geometric decay
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
                                prevMatch.value != match.value && prevMatch.range.intersect(match.range).isEmpty()
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
