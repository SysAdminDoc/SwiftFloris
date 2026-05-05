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
import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.editor.EditorRange
import dev.patrickgold.florisboard.ime.media.emoji.EmojiSuggestionProvider
import dev.patrickgold.florisboard.ime.nlp.advanced.AdvancedPredictionProvider
import dev.patrickgold.florisboard.ime.nlp.advanced.AdvancedSpellingProvider
import dev.patrickgold.florisboard.ime.nlp.han.HanShapeBasedLanguageProvider
import dev.patrickgold.florisboard.ime.nlp.latin.LatinLanguageProvider
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.lib.util.NetworkUtils
import dev.patrickgold.florisboard.subtypeManager
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
import org.florisboard.lib.kotlin.guardedByLock
import org.florisboard.lib.kotlin.collectLatestIn
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentHashMap
import kotlin.properties.Delegates

private const val BLANK_STR_PATTERN = "^\\s*$"

class NlpManager(context: Context) {
    private val blankStrRegex = Regex(BLANK_STR_PATTERN)

    private val prefs by FlorisPreferenceStore
    private val dictionaryManager = DictionaryManager.default()
    private val clipboardManager by context.clipboardManager()
    private val editorInstance by context.editorInstance()
    private val keyboardManager by context.keyboardManager()
    private val subtypeManager by context.subtypeManager()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val clipboardSuggestionProvider = ClipboardSuggestionProvider(context)
    private val emojiSuggestionProvider = EmojiSuggestionProvider(context)
    private val providers = guardedByLock {
        mapOf(
            LatinLanguageProvider.ProviderId to ProviderInstanceWrapper(LatinLanguageProvider(context)),
            HanShapeBasedLanguageProvider.ProviderId to ProviderInstanceWrapper(HanShapeBasedLanguageProvider(context)),
            AdvancedSpellingProvider(context).providerId to ProviderInstanceWrapper(AdvancedSpellingProvider(context)),
            AdvancedPredictionProvider(context).providerId to ProviderInstanceWrapper(AdvancedPredictionProvider(context)),
        )
    }
    // lock unnecessary because values constant
    private val providersForceSuggestionOn = ConcurrentHashMap<String, Boolean>()
    
    // Caches for word lists and frequencies to avoid blocking on repeated calls
    private val wordsListCache = ConcurrentHashMap<String, List<String>>()
    private val frequencyCache = ConcurrentHashMap<String, Double>()
    private val autoCommitSuppression = AutoCommitSuppression()

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

    private suspend fun getSpellingProvider(subtype: Subtype): SpellingProvider {
        return providers.withLock { it[subtype.nlpProviders.spelling] }?.provider as? SpellingProvider
            ?: FallbackNlpProvider
    }

    private suspend fun getSuggestionProvider(subtype: Subtype): SuggestionProvider {
        return providers.withLock { it[subtype.nlpProviders.suggestion] }?.provider as? SuggestionProvider
            ?: FallbackNlpProvider
    }

    fun preload(subtype: Subtype) {
        scope.launch {
            emojiSuggestionProvider.preload(subtype)
            providers.withLock { providers ->
                subtype.nlpProviders.forEach { _, providerId ->
                    providers[providerId]?.let { provider ->
                        provider.createIfNecessary()
                        provider.preload(subtype)
                    }
                }
            }
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
            ensureUserDictionariesLoaded()
            if (dictionaryManager.isKnownUserDictionaryWord(word, subtype.primaryLocale)) {
                return SpellingResult.validWord()
            }
        }
        return getSpellingProvider(subtype).spell(
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
        return getSuggestionProvider(subtypeManager.activeSubtype).determineLocalComposing(
            subtypeManager.activeSubtype, textBeforeSelection, breakIterators, localLastCommitPosition
        )
    }

    fun providerForcesSuggestionOn(subtype: Subtype): Boolean {
        // Using a cache because I have no idea how fast the runBlocking is
        return providersForceSuggestionOn.getOrPut(subtype.nlpProviders.suggestion) {
            runBlocking {
                getSuggestionProvider(subtype).forcesSuggestionOn
            }
        }
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
            val suggestionProvider = getSuggestionProvider(subtype)
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
            internalSuggestionsGuard.withLock {
                if (internalSuggestions.first < requestId) {
                    internalSuggestions = requestId to buildList {
                        addAll(emojiSuggestions)
                        addAll(mergeWordSuggestions(userDictionarySuggestions, suggestions, maxCandidateCount = 8))
                    }
                }
            }
        }
    }

    fun suggestDirectly(suggestions: List<SuggestionCandidate>) {
        val requestId = suggestionsRequestCounter.incrementAndGet()
        scope.launch {
            internalSuggestionsGuard.withLock {
                if (internalSuggestions.first < requestId) {
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
                    internalSuggestions = requestId to emptyList()
                }
            }
        }
    }

    fun getAutoCommitCandidate(): SuggestionCandidate? {
        val content = editorInstance.activeContent
        val currentWord = content.autoCommitWord()
        val currentWordStart = content.autoCommitWordStart()
        return activeCandidates.firstOrNull { candidate ->
            candidate.isEligibleForAutoCommit &&
                !autoCommitSuppression.shouldSuppress(
                    currentWord = currentWord,
                    candidateText = candidate.text,
                    currentWordStart = currentWordStart,
                )
        }
    }

    fun rememberAcceptedAutoCommit(content: EditorContent, candidate: SuggestionCandidate) {
        autoCommitSuppression.rememberAccepted(
            originalText = content.autoCommitWord(),
            correctedText = candidate.text,
            wordStart = content.autoCommitWordStart(),
        )
    }

    fun rejectAcceptedAutoCommitOnBackspace(content: EditorContent): Boolean {
        val cursorPosition = content.selection.takeIf { it.isValid }?.start
        return autoCommitSuppression.rejectAccepted(
            textBeforeSelection = content.textBeforeSelection,
            cursorPosition = cursorPosition,
        )
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
            runBlocking { getSuggestionProvider(subtype).getListOfWords(subtype) }
        }
    }

    fun getFrequencyForWord(subtype: Subtype, word: String): Double {
        val cacheKey = "${subtype}-$word"
        return frequencyCache.getOrPut(cacheKey) {
            // Use blocking only for first-time fetch; results are cached afterward
            runBlocking { getSuggestionProvider(subtype).getFrequencyForWord(subtype, word) }
        }
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
        ensureUserDictionariesLoaded()
        return dictionaryManager.queryUserDictionary(currentWord, subtype.primaryLocale).take(maxCandidateCount)
    }

    private fun mergeWordSuggestions(
        preferred: List<SuggestionCandidate>,
        fallback: List<SuggestionCandidate>,
        maxCandidateCount: Int,
    ): List<SuggestionCandidate> {
        if (maxCandidateCount <= 0) {
            return emptyList()
        }
        val seen = mutableSetOf<String>()
        return buildList {
            for (candidate in preferred + fallback) {
                val key = candidate.text.toString().lowercase()
                if (key.isNotBlank() && seen.add(key)) {
                    add(candidate)
                    if (size >= maxCandidateCount) {
                        break
                    }
                }
            }
        }
    }

    private fun onUserDictionaryConfigurationChanged() {
        ensureUserDictionariesLoaded()
        wordsListCache.clear()
        frequencyCache.clear()
        assembleCandidates()
    }

    private fun ensureUserDictionariesLoaded() {
        dictionaryManager.loadUserDictionariesIfNecessary()
    }

    private fun assembleCandidates() {
        scope.launch {
            val candidates = when {
                isSuggestionOn() -> {
                    buildList {
                        // Clipboard suggestions first
                        addAll(
                            clipboardSuggestionProvider.suggest(
                                subtype = Subtype.DEFAULT,
                                content = editorInstance.activeContent,
                                maxCandidateCount = 8,
                                allowPossiblyOffensive = !prefs.suggestion.blockPossiblyOffensive.get(),
                                isPrivateSession = keyboardManager.activeState.isIncognitoMode,
                            )
                        )
                        // Then add spell + emoji suggestions from internalSuggestions
                        internalSuggestionsGuard.withLock {
                            addAll(internalSuggestions.second)
                        }
                    }
                }
                else -> emptyList()
            }
            activeCandidates = candidates
            autoExpandCollapseSmartbarActions(candidates, NlpInlineAutofill.suggestions.value)
        }
    }

    fun autoExpandCollapseSmartbarActions(list1: List<*>?, list2: List<*>?) {
        if (!prefs.smartbar.enabled.get()) {// || !prefs.smartbar.sharedActionsAutoExpandCollapse.get()) {
            return
        }
        // TODO: this is a mess and needs to be cleaned up in v0.5 with the NLP development
        /*if (keyboardManager.inputEventDispatcher.isRepeatableCodeLastDown()
            && !keyboardManager.inputEventDispatcher.isPressed(KeyCode.DELETE)
            && !keyboardManager.inputEventDispatcher.isPressed(KeyCode.FORWARD_DELETE)
            || keyboardManager.activeState.isActionsOverflowVisible
        ) {
            return // We do not auto switch if a repeatable action key was last pressed or if the actions overflow
                   // menu is visible to prevent annoying UI changes
        }*/
        val isSelection = editorInstance.activeContent.selection.isSelectionMode
        val isExpanded = list1.isNullOrEmpty() && list2.isNullOrEmpty() || isSelection
        scope.launch {
            prefs.smartbar.sharedActionsExpandWithAnimation.set(false)
            prefs.smartbar.sharedActionsExpanded.set(isExpanded)
        }
    }

    fun addToDebugOverlay(word: String, info: SpellingResult) {
        debugOverlaySuggestionsInfos.put(System.currentTimeMillis(), word to info)
        debugOverlayVersion.update { it + 1 }
    }

    fun clearDebugOverlay() {
        debugOverlaySuggestionsInfos.evictAll()
        debugOverlayVersion.update { it + 1 }
    }

    private class ProviderInstanceWrapper(val provider: NlpProvider) {
        private var isInstanceAlive = AtomicBoolean(false)

        suspend fun createIfNecessary() {
            if (isInstanceAlive.compareAndSet(false, true)) {
                try {
                    provider.create()
                } catch (error: Throwable) {
                    isInstanceAlive.set(false)
                    throw error
                }
            }
        }

        suspend fun preload(subtype: Subtype) {
            provider.preload(subtype)
        }

        suspend fun destroyIfNecessary() {
            if (isInstanceAlive.getAndSet(false)) provider.destroy()
        }
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

    inner class ClipboardSuggestionProvider internal constructor(private val context: Context) : SuggestionProvider {
        private var lastClipboardItemId: Long = -1

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
