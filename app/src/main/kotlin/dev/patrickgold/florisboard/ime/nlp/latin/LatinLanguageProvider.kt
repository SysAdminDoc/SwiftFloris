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

package dev.patrickgold.florisboard.ime.nlp.latin

import android.content.Context
import android.os.SystemClock
import android.os.Trace
import android.util.Log
import dev.patrickgold.florisboard.BuildConfig
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.appContext
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.dictionary.PersonalBigramStore
import dev.patrickgold.florisboard.ime.dictionary.PersonalTrigramStore
import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.nlp.ImmediateAutocorrect
import dev.patrickgold.florisboard.ime.nlp.ImmediateAutocorrectCorrection
import dev.patrickgold.florisboard.ime.nlp.NextWordSuggestionContext
import dev.patrickgold.florisboard.ime.nlp.SpellingProvider
import dev.patrickgold.florisboard.ime.nlp.SpellingResult
import dev.patrickgold.florisboard.ime.nlp.SuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.SuggestionProvider
import dev.patrickgold.florisboard.ime.nlp.TypingContextExtractor
import dev.patrickgold.florisboard.ime.nlp.WordSuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.context.ContextModelStore
import dev.patrickgold.florisboard.lib.devtools.flogDebug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt
import dev.patrickgold.florisboard.lib.devtools.flogError

class LatinLanguageProvider(context: Context) : SpellingProvider, SuggestionProvider {
    companion object {
        // Default user ID used for all subtypes, unless otherwise specified.
        // See `ime/core/Subtype.kt` Line 210 and 211 for the default usage
        const val ProviderId = "org.florisboard.nlp.providers.latin"

        /** Every remembered continuation is read, then filtered by the typed prefix (the stores keep 32 / 24). */
        private const val PersonalContinuationLookupCount = 32
    }

    private val appContext by context.appContext()
    private val dictionaryAssets = AddonDictionaryAssetMounts(context.applicationContext)

    private val dictionaryStore = LatinDictionaryStore(
        readAsset = dictionaryAssets,
        assetPlanner = dictionaryAssets,
    )

    override val providerId = ProviderId

    override suspend fun create() {
        // Here we initialize our provider, set up all things which are not language dependent.
    }

    override suspend fun preload(subtype: Subtype) {
        dictionary(subtype)
    }

    override suspend fun spell(
        subtype: Subtype,
        word: String,
        precedingWords: List<String>,
        followingWords: List<String>,
        maxSuggestionCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): SpellingResult {
        val languageCode = LatinDictionaryStore.normalizeLanguageCode(subtype.primaryLocale.language)
        val normalizedWord = LatinDictionarySuggester.normalizeWord(word) ?: return SpellingResult.unspecified()
        val dictionary = dictionary(subtype)
        LatinDictionarySuggester.englishContractionCorrection(
            rawWord = word,
            dictionary = dictionary,
            languageCode = languageCode,
        )?.let { correction ->
            return SpellingResult.typo(
                suggestions = arrayOf(correction.text),
                isHighConfidenceResult = correction.isEligibleForAutoCommit,
            )
        }
        if (dictionary.contains(normalizedWord)) {
            return SpellingResult.validWord()
        }
        // ROADMAP §7 Next-3 — words the user has typed before are valid,
        // regardless of whether SCOWL knows them. This is what stops the
        // spell-checker from underlining the user's own learned vocabulary.
        if (dev.patrickgold.florisboard.ime.dictionary.UserDictionaryOverlay.get()
                .contains(normalizedWord, subtype.primaryLocale)
        ) {
            return SpellingResult.validWord()
        }
        // Lazy hydration so the overlay reflects the user's saved vocab
        // when spell() runs — same idempotent path as suggest().
        dev.patrickgold.florisboard.ime.dictionary.DictionaryManager.default()
            .hydrateOverlay(subtype.primaryLocale)
        val overlay = dev.patrickgold.florisboard.ime.dictionary.UserDictionaryOverlay
            .get()
            .viewFor(subtype.primaryLocale)
        LatinDictionarySuggester.englishMisspellingCorrection(
            rawWord = word,
            dictionary = dictionary,
            languageCode = languageCode,
            userOverlay = overlay,
            region = subtype.primaryLocale.country,
        )?.let { correction ->
            return SpellingResult.typo(
                suggestions = arrayOf(correction.text),
                isHighConfidenceResult = true,
            )
        }
        val corrections = LatinDictionarySuggester.corrections(
            word = normalizedWord,
            dictionary = dictionary,
            maxCandidateCount = maxSuggestionCount,
            userOverlay = overlay,
        )
        return SpellingResult.typo(
            suggestions = corrections.map { it.text }.toTypedArray(),
            isHighConfidenceResult = corrections.firstOrNull()?.isEligibleForAutoCommit == true,
        )
    }

    override suspend fun suggest(
        subtype: Subtype,
        content: EditorContent,
        maxCandidateCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): List<SuggestionCandidate> {
        val shouldLogBenchmark = BuildConfig.BUILD_TYPE == "benchmark"
        val currentWordLength = content.currentWordText.ifBlank { content.composingText }.length
        val suggestStartedAt = if (shouldLogBenchmark) {
            SystemClock.elapsedRealtimeNanos()
        } else {
            0L
        }
        var candidateCount = 0
        Trace.beginSection("swiftfloris.nlp.suggest")
        try {
            val result = suggestImpl(subtype, content, maxCandidateCount, isPrivateSession)
            candidateCount = result.size
            return result
        } finally {
            if (shouldLogBenchmark) {
                val durationMs = (SystemClock.elapsedRealtimeNanos() - suggestStartedAt) / 1_000_000.0
                Log.i(
                    "SwiftFlorisPerf",
                    "swiftfloris.nlp.suggestMs=$durationMs " +
                        "currentWordLength=$currentWordLength candidateCount=$candidateCount",
                )
            }
            Trace.endSection()
        }
    }

    private suspend fun suggestImpl(
        subtype: Subtype,
        content: EditorContent,
        maxCandidateCount: Int,
        isPrivateSession: Boolean,
    ): List<SuggestionCandidate> {
        val currentWord = content.currentWordText.ifBlank { content.composingText }
        if (currentWord.isBlank()) {
            return nextWordSuggestions(subtype, content, maxCandidateCount, isPrivateSession)
        }
        val prefs by FlorisPreferenceStore
        val locales = subtype.locales()
        val multilingual = prefs.correction.multilingualSuggestions.get() && locales.size > 1
        return if (multilingual) {
            suggestMultilingual(currentWord, content, locales, maxCandidateCount, isPrivateSession)
        } else {
            val languageCode = LatinDictionaryStore.normalizeLanguageCode(subtype.primaryLocale.language)
            // Lazy hydrate so a process restart picks up the user's vocabulary
            // on the first suggest after typing resumes. Idempotent + async.
            dev.patrickgold.florisboard.ime.dictionary.DictionaryManager.default()
                .hydrateOverlay(subtype.primaryLocale)
            val overlay = dev.patrickgold.florisboard.ime.dictionary.UserDictionaryOverlay
                .get()
                .viewFor(subtype.primaryLocale)
            LatinDictionarySuggester.suggest(
                rawWord = currentWord,
                dictionary = dictionary(subtype),
                maxCandidateCount = maxCandidateCount,
                languageCode = languageCode,
                userOverlay = overlay,
                contextFit = contextFit(languageCode, content, currentWord),
                region = subtype.primaryLocale.country,
                personalContinuations = personalContinuations(subtype.primaryLocale, content, currentWord, isPrivateSession),
            ).map { candidate ->
                WordSuggestionCandidate(
                    text = candidate.text,
                    confidence = candidate.confidence,
                    isEligibleForAutoCommit = candidate.isEligibleForAutoCommit,
                    sourceProvider = this@LatinLanguageProvider,
                )
            }
        }
    }

    /**
     * How well a word fits the one or two words before [currentWord] by [languageCode]'s bundled context model,
     * or null when the language has no model or [currentWord] starts its sentence.
     */
    private suspend fun contextFit(
        languageCode: String,
        content: EditorContent,
        currentWord: String,
    ): ((String) -> Double?)? {
        val model = ContextModelStore.modelFor(appContext, languageCode) ?: return null
        val previous = TypingContextExtractor.previousWordsBeforeCurrentWord(content.textBeforeSelection, currentWord)
        val prev1 = previous.prev1 ?: return null
        return { word -> model.contextProbability(previous.prev2, prev1, word) }
    }

    /**
     * Words the user has typed after the one or two words before [currentWord] in [locale], best first, for
     * [LatinDictionarySuggester.suggest] to complete from. Empty in a private session, with next-word prediction
     * off (it also turns off learning them), or at the start of a sentence.
     */
    private suspend fun personalContinuations(
        locale: dev.patrickgold.florisboard.lib.FlorisLocale,
        content: EditorContent,
        currentWord: String,
        isPrivateSession: Boolean,
    ): List<String> {
        if (isPrivateSession) return emptyList()
        val prefs by FlorisPreferenceStore
        if (!prefs.suggestion.nextWordPrediction.get()) return emptyList()
        val previous = TypingContextExtractor.previousWordsBeforeCurrentWord(content.textBeforeSelection, currentWord)
        val prev1 = previous.prev1 ?: return emptyList()
        val trigrams = previous.prev2?.let { prev2 ->
            PersonalTrigramStore.get(appContext).predict(prev2, prev1, locale, PersonalContinuationLookupCount)
        }.orEmpty()
        val bigrams = PersonalBigramStore.get(appContext).predict(prev1, locale, PersonalContinuationLookupCount)
        return (trigrams + bigrams).distinct()
    }

    private suspend fun suggestMultilingual(
        rawWord: String,
        content: EditorContent,
        locales: List<dev.patrickgold.florisboard.lib.FlorisLocale>,
        maxCandidateCount: Int,
        isPrivateSession: Boolean,
    ): List<SuggestionCandidate> {
        val normalized = LatinDictionarySuggester.normalizeWord(rawWord) ?: return emptyList()
        // Per-locale: language code, dictionary recognised the typed word, candidate list.
        data class PerLocale(
            val recognised: Boolean,
            val candidates: List<LatinSuggestion>,
        )
        val perLocale = locales.map { locale ->
            val langCode = LatinDictionaryStore.normalizeLanguageCode(locale.language)
            val dict = dictionaryStore.dictionaryForLanguage(locale.language)
            dev.patrickgold.florisboard.ime.dictionary.DictionaryManager.default()
                .hydrateOverlay(locale)
            val overlay = dev.patrickgold.florisboard.ime.dictionary.UserDictionaryOverlay
                .get()
                .viewFor(locale)
            val cands = LatinDictionarySuggester.suggest(
                rawWord = rawWord,
                dictionary = dict,
                maxCandidateCount = maxCandidateCount,
                languageCode = langCode,
                userOverlay = overlay,
                contextFit = contextFit(langCode, content, rawWord),
                region = locale.country,
                personalContinuations = personalContinuations(locale, content, rawWord, isPrivateSession),
            )
            PerLocale(
                recognised = dict.contains(normalized) || overlay.containsKey(normalized),
                candidates = cands,
            )
        }
        val anyRecognised = perLocale.any { it.recognised }
        val merged = HashMap<String, Pair<SuggestionCandidate, Double>>()
        for (slot in perLocale) {
            // If at least one locale recognises the typed word, demote candidates from
            // locales that don't — that's where SwiftKey's "stop bleeding wrong-language
            // autocorrects mid-sentence" property comes from.
            val prior = if (!anyRecognised) 1.0 else if (slot.recognised) 1.0 else 0.4
            for (c in slot.candidates) {
                val key = c.text.lowercase()
                val score = c.confidence * prior
                val candidate = WordSuggestionCandidate(
                    text = c.text,
                    confidence = score,
                    isEligibleForAutoCommit = c.isEligibleForAutoCommit && (!anyRecognised || slot.recognised),
                    sourceProvider = this@LatinLanguageProvider,
                )
                val existing = merged[key]
                if (existing == null || existing.second < score) {
                    merged[key] = candidate to score
                }
            }
        }
        return merged.values
            .sortedByDescending { it.second }
            .map { it.first }
            .take(maxCandidateCount)
    }

    private suspend fun nextWordSuggestions(
        subtype: Subtype,
        content: EditorContent,
        maxCandidateCount: Int,
        isPrivateSession: Boolean,
    ): List<SuggestionCandidate> {
        val prefs by FlorisPreferenceStore
        if (!prefs.suggestion.nextWordPrediction.get()) return emptyList()
        if (isPrivateSession) return emptyList()
        if (maxCandidateCount <= 0) return emptyList()
        val before = content.textBeforeSelection
        val prevWord = previousWordOf(before)
        val prev2Word = previousWordOf(before, depth = 2)
        val bigramStore = PersonalBigramStore.get(appContext)
        val trigramStore = PersonalTrigramStore.get(appContext)
        val nextWordContext = prevWord?.let { word ->
            NextWordSuggestionContext(
                previousWord = word,
                secondPreviousWord = prev2Word,
            )
        }
        // Tier 0: trained trigrams for the (prev2, prev1) context — sharpest predictions.
        val trigramHits = if (prevWord != null && prev2Word != null) {
            trigramStore.predict(prev2Word, prevWord, subtype.primaryLocale, maxCandidateCount)
        } else {
            emptyList()
        }
        // Tier 1: trained bigrams for the actual previous word.
        val bigramHits = if (prevWord != null) {
            bigramStore.predict(prevWord, subtype.primaryLocale, maxCandidateCount)
        } else {
            emptyList()
        }
        val seen = HashSet<String>(maxCandidateCount * 3)
        val merged = ArrayList<Pair<String, Double>>(maxCandidateCount)
        suspend fun adjustedConfidence(word: String, confidence: Double): Double {
            if (prevWord == null) return confidence
            val bigramPenalty = bigramStore.rejectionPenalty(prevWord, word, subtype.primaryLocale)
            val trigramPenalty = prev2Word?.let { prev2 ->
                trigramStore.rejectionPenalty(prev2, prevWord, word, subtype.primaryLocale)
            } ?: 0.0
            val penalty = maxOf(bigramPenalty, trigramPenalty).coerceIn(0.0, 1.0)
            return (confidence * (1.0 - penalty)).coerceIn(0.0, confidence)
        }
        suspend fun addMerged(word: String, confidence: Double) {
            val adjusted = adjustedConfidence(word, confidence)
            if (adjusted > 0.01) {
                merged.add(word to adjusted)
            }
        }
        trigramHits.forEachIndexed { index, word ->
            if (seen.add(word.lowercase())) {
                addMerged(word, 0.80 - 0.05 * index)
            }
        }
        bigramHits.forEachIndexed { index, word ->
            if (seen.add(word.lowercase())) {
                addMerged(word, 0.55 - 0.05 * index)
            }
        }
        // Tier 2: curated cold-start priors supply SwiftKey-like sentence starts
        // and common short continuations before personal history is rich enough.
        ColdStartNextWordPriors.suggest(
            textBeforeCursor = before,
            languageCode = subtype.primaryLocale.language,
            maxCandidateCount = maxCandidateCount,
        ).forEach { prior ->
            if (merged.size < maxCandidateCount && seen.add(prior.word.lowercase())) {
                addMerged(prior.word, prior.confidence)
            }
        }
        if (merged.size < maxCandidateCount) {
            val dict = dictionaryStore.dictionaryForLanguage(subtype.primaryLocale.language)
            val bootstrap = dict.topByFrequency(maxCandidateCount * 2)
            for (word in bootstrap) {
                if (merged.size >= maxCandidateCount) break
                if (seen.add(word.lowercase())) {
                    // frequencyFor returns [0,1], so the old 0.001 multiplier collapsed
                    // every bootstrap word to ~0.300 (the *255 byte-frequency multiplier
                    // was mis-ported). Spread the frequency over a real 0..0.06 band
                    // starting at 0.18 — kept below trained bigram/trigram (0.55+) and
                    // the cold-start prior floor so cross-provider ranking stays correct.
                    addMerged(word, 0.18 + 0.06 * dict.frequencyFor(word))
                }
            }
        }
        return merged.sortedByDescending { (_, confidence) -> confidence }.map { (word, confidence) ->
            WordSuggestionCandidate(
                text = applySentenceCase(word, before),
                confidence = confidence,
                isEligibleForAutoCommit = false,
                isEligibleForUserRemoval = nextWordContext != null,
                sourceProvider = this@LatinLanguageProvider,
                nextWordContext = nextWordContext,
            )
        }
    }

    private fun applySentenceCase(word: String, textBeforeCursor: String): String {
        if (word.isEmpty()) return word
        if (word.equals("i", ignoreCase = true)) return "I"
        if (word.startsWith("i'", ignoreCase = true)) {
            return "I" + word.drop(1)
        }
        val isSentenceStart = textBeforeCursor.isBlank() ||
            textBeforeCursor.trimEnd().lastOrNull()?.let { it == '.' || it == '!' || it == '?' || it == '\n' } == true
        return if (isSentenceStart) {
            word.replaceFirstChar { it.uppercaseChar() }
        } else {
            word
        }
    }

    private fun previousWordOf(textBeforeCursor: String, depth: Int = 1): String? =
        PreviousWords.inSentence(textBeforeCursor, depth)

    override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
        // We can use flogDebug, flogInfo, flogWarning and flogError for debug logging, which is a wrapper for Logcat
        flogDebug { candidate.toString() }
    }

    override suspend fun notifySuggestionReverted(subtype: Subtype, candidate: SuggestionCandidate) {
        flogDebug { candidate.toString() }
    }

    override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean {
        flogDebug { candidate.toString() }
        if (!candidate.isEligibleForUserRemoval) return false
        val word = candidate.text.toString()
        if (word.isBlank()) return false
        val locale = subtype.primaryLocale
        val nextWordContext = (candidate as? WordSuggestionCandidate)?.nextWordContext
        if (nextWordContext != null) {
            return withContext(Dispatchers.IO) {
                val rejectedBigram = PersonalBigramStore.get(appContext).rejectContinuationAndAwait(
                    prevWord = nextWordContext.previousWord,
                    currWord = word,
                    locale = locale,
                )
                val rejectedTrigram = nextWordContext.secondPreviousWord?.let { prev2 ->
                    PersonalTrigramStore.get(appContext).rejectContinuationAndAwait(
                        prev2 = prev2,
                        prev1 = nextWordContext.previousWord,
                        currWord = word,
                        locale = locale,
                    )
                } ?: false
                rejectedBigram || rejectedTrigram
            }
        }
        // Forget the word from every learned source on this device. Each forget call is
        // idempotent — if the word never lived in that store, nothing happens.
        val removedFromUserDict = withContext(Dispatchers.IO) {
            dev.patrickgold.florisboard.ime.dictionary.DictionaryManager.default()
                .forgetWord(word, locale)
        }
        dev.patrickgold.florisboard.ime.dictionary.PersonalBigramStore.get(appContext).forget(word, locale)
        dev.patrickgold.florisboard.ime.dictionary.PersonalTrigramStore.get(appContext).forget(word, locale)
        // Report the only synchronously-knowable signal. The bigram/trigram forget()
        // calls are fire-and-forget (return Unit), so they can't contribute a result;
        // the old `|| true` made this constant-true and hid genuine failures from callers.
        return removedFromUserDict
    }

    override suspend fun getListOfWords(subtype: Subtype): List<String> {
        return dictionary(subtype).glideWords
    }

    /**
     * The word's weight in [subtype]'s language: the bundled frequency, or the learned weight when
     * the user's own use puts it higher. The ranker scores every candidate with this, so a word
     * only the user has taught the keyboard ("wegovy", a name) scored 0 there and lost to
     * dictionary words even after its completion made the list.
     */
    override suspend fun getFrequencyForWord(subtype: Subtype, word: String): Double {
        val normalizedWord = LatinDictionarySuggester.normalizeWord(word) ?: word.lowercase()
        val bundled = dictionary(subtype).frequencyFor(normalizedWord)
        val learned = dev.patrickgold.florisboard.ime.dictionary.UserDictionaryOverlay.get()
            .frequencyFor(normalizedWord, subtype.primaryLocale)
            .coerceIn(0, 255) / 255.0
        return maxOf(bundled, learned)
    }

    /**
     * Every language of [subtype] is checked, not only those multilingual suggestions would use, because the
     * typed word counts as known through any of them: "como" on an English and Spanish keyboard is Spanish, not
     * a slip for "come".
     */
    override suspend fun isLikelySlip(subtype: Subtype, content: EditorContent, word: String): Boolean {
        val normalizedWord = LatinDictionarySuggester.normalizeWord(word) ?: return false
        var slipSomewhere = false
        for (locale in subtype.locales().distinctBy { it.languageTag() }) {
            val languageCode = LatinDictionaryStore.normalizeLanguageCode(locale.language)
            val dictionary = dictionaryStore.dictionaryForLanguage(languageCode)
            val overlay = dev.patrickgold.florisboard.ime.dictionary.UserDictionaryOverlay.get().viewFor(locale)
            if (overlay.containsKey(normalizedWord)) return false
            if (!dictionary.contains(normalizedWord)) continue
            // Checked before the context model is asked: nearly every known word is vetted.
            if (normalizedWord !in dictionary.unvettedWords) return false
            val fixes = LatinDictionarySuggester.likelySlipFixes(
                word = normalizedWord,
                dictionary = dictionary,
                userOverlay = overlay,
                contextFit = contextFit(languageCode, content, word),
            )
            if (fixes.isEmpty()) return false
            slipSomewhere = true
        }
        return slipSomewhere
    }

    override suspend fun destroy() {
        // Here we have the chance to de-allocate memory and finish our work. However this might never be called if
        // the app process is killed (which will most likely always be the case).
    }

    private suspend fun dictionary(subtype: Subtype): LatinDictionarySnapshot {
        return dictionaryStore.dictionaryForLanguage(subtype.primaryLocale.language)
    }
}

internal fun interface LatinDictionaryAssetReader {
    suspend fun read(path: String): String?
}

internal fun interface LatinDictionaryAssetPlanner {
    suspend fun planForLanguage(languageCode: String): LatinDictionaryAssetPlan
}

internal data class LatinDictionaryAssetPlan(
    val generation: Long,
    val dictionaryPaths: List<String>,
    val zipfPaths: List<String>,
) {
    companion object {
        fun bundled(languageCode: String): LatinDictionaryAssetPlan =
            LatinDictionaryAssetPlan(
                generation = 0L,
                dictionaryPaths = LatinDictionaryStore.assetPathsForLanguage(languageCode),
                zipfPaths = listOf(LatinDictionaryStore.zipfAssetPath(languageCode)),
            )
    }
}

internal class LatinDictionaryStore(
    private val readAsset: LatinDictionaryAssetReader,
    private val assetPlanner: LatinDictionaryAssetPlanner = LatinDictionaryAssetPlanner { languageCode ->
        LatinDictionaryAssetPlan.bundled(languageCode)
    },
    private val json: Json = Json,
) {
    private val wordDataSerializer = MapSerializer(String.serializer(), Int.serializer())
    private val dictionaryLoadGuard = Mutex()
    private val dictionaries = ConcurrentHashMap<String, LatinDictionarySnapshot>()
    // SupervisorJob so an index pre-warm that runs out of heap on a low-memory
    // device cannot cancel the scope and mute every later pre-warm.
    private val preWarmScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    @Volatile
    private var cachedGeneration: Long = Long.MIN_VALUE

    suspend fun dictionaryForLanguage(language: String): LatinDictionarySnapshot {
        val languageCode = normalizeLanguageCode(language)
        val plan = assetPlanner.planForLanguage(languageCode)
        if (cachedGeneration == plan.generation) {
            dictionaries[languageCode]?.let { return it }
        }

        return dictionaryLoadGuard.withLock {
            if (cachedGeneration != plan.generation) {
                dictionaries.clear()
                cachedGeneration = plan.generation
            }
            dictionaries[languageCode]?.let { return@withLock it }

            val dictionary = loadSpecificDictionary(languageCode, plan)
                ?: if (languageCode == DefaultLanguageCode) {
                    LatinDictionarySnapshot.Empty
                } else {
                    val defaultPlan = assetPlanner.planForLanguage(DefaultLanguageCode)
                    dictionaries[DefaultLanguageCode]
                        ?: loadSpecificDictionary(DefaultLanguageCode, defaultPlan)
                            ?.also { dictionaries[DefaultLanguageCode] = it }
                        ?: LatinDictionarySnapshot.Empty
                }
            dictionaries[languageCode] = dictionary
            preWarmSymSpellIndices(dictionary)
            dictionary
        }
    }

    private fun preWarmSymSpellIndices(dictionary: LatinDictionarySnapshot) {
        if (!dictionary.isLoaded) return
        preWarmScope.launch {
            dictionary.symSpellIndex
            dictionary.symSpellDistance2Index
        }
    }

    private suspend fun loadSpecificDictionary(
        languageCode: String,
        plan: LatinDictionaryAssetPlan,
    ): LatinDictionarySnapshot? {
        val shouldLogBenchmark = BuildConfig.BUILD_TYPE == "benchmark"
        val startedAt = if (shouldLogBenchmark) {
            SystemClock.elapsedRealtimeNanos()
        } else {
            0L
        }
        var wordCount = 0
        Trace.beginSection("swiftfloris.dict.load")
        try {
            val result = loadSpecificDictionaryImpl(languageCode, plan)
            wordCount = result?.frequencies?.size ?: 0
            return result
        } finally {
            if (shouldLogBenchmark) {
                val durationMs = (SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000.0
                Log.i(
                    "SwiftFlorisPerf",
                    "swiftfloris.dict.loadMs=$durationMs language=$languageCode wordCount=$wordCount",
                )
            }
            Trace.endSection()
        }
    }

    private suspend fun loadSpecificDictionaryImpl(
        languageCode: String,
        plan: LatinDictionaryAssetPlan,
    ): LatinDictionarySnapshot? {
        val base = loadMergedDictionaries(plan.dictionaryPaths) ?: return null
        val merged = if (languageCode == DefaultLanguageCode) {
            base.mergeWith(loadSupplementalEnglishFrequencies())
        } else {
            base
        }
        // ROADMAP §7 Next-3.2 — overlay subtitle-derived Zipf table for this
        // language. Missing asset is tolerated: the snapshot then carries
        // `ZipfFrequencyTable.Empty` and `frequencyFor` falls back to pure
        // SCOWL frequencies (current behaviour).
        val zipfTsv = loadFirstText(plan.zipfPaths)
        val zipfTable = ZipfFrequencyTable.parse(languageCode, zipfTsv)
        val withZipf = merged.withZipfTable(zipfTable)
        return if (languageCode == DefaultLanguageCode) {
            val misspellingsTsv = readAsset.read(EnglishMisspellingsPath).orEmpty()
            withZipf.withMisspellings(
                parseMisspellings(misspellingsTsv),
                commonwealth = parseCommonwealthMisspellings(misspellingsTsv),
            ).withUnvettedWords(parseWordList(readAsset.read(EnglishUnvettedWordsPath).orEmpty()))
        } else {
            withZipf
        }
    }

    private suspend fun loadMergedDictionaries(paths: List<String>): LatinDictionarySnapshot? {
        var merged: Map<String, Int>? = null
        for (path in paths.distinct()) {
            val rawData = readAsset.read(path) ?: continue
            // An addon-supplied path reaches this loop, and a descriptor that
            // names the wrong format sends its bytes to the JSON parser. The
            // sibling supplemental-English loader already treats a decode as
            // fallible; this one propagated, and it sits on the path a keystroke
            // takes. Skip the asset and keep whatever else loaded.
            val frequencies = runCatching { decodeFrequencies(path, rawData) }
                .onFailure { error ->
                    flogError { "Dictionary asset '$path' could not be decoded: ${error.message}" }
                }
                .getOrNull()
                ?: continue
            if (frequencies.isEmpty()) continue
            merged = mergeFrequencies(merged, frequencies)
        }
        return merged?.let { LatinDictionarySnapshot.from(it) }
    }

    private suspend fun loadFirstText(paths: List<String>): String? {
        for (path in paths.distinct()) {
            return readAsset.read(path) ?: continue
        }
        return null
    }

    private suspend fun loadSupplementalEnglishFrequencies(): Map<String, Int> {
        val rawData = readAsset.read(SupplementalEnglishDictionaryPath) ?: return emptyMap()
        return runCatching {
            decodeFrequencies(SupplementalEnglishDictionaryPath, rawData)
        }.getOrElse {
            emptyMap()
        }
    }

    private fun mergeFrequencies(
        existing: Map<String, Int>?,
        incoming: Map<String, Int>,
    ): Map<String, Int> {
        if (existing == null) return incoming
        if (incoming.isEmpty()) return existing
        val merged = HashMap<String, Int>(existing.size + incoming.size)
        merged.putAll(existing)
        incoming.forEach { (word, frequency) ->
            merged[word] = maxOf(merged[word] ?: 0, frequency)
        }
        return merged
    }

    private fun decodeFrequencies(path: String, rawData: String): Map<String, Int> {
        return if (path.endsWith(FldicExtension)) {
            decodeFldicFrequencies(rawData)
        } else {
            json.decodeFromString(wordDataSerializer, rawData)
                .toNormalizedFrequencyMap()
        }
    }

    private fun decodeFldicFrequencies(rawData: String): Map<String, Int> {
        val scores = mutableMapOf<String, Long>()
        var maxScore = 0L
        var inWordsSection = false
        for (rawLine in rawData.lineSequence()) {
            val line = rawLine.trimEnd()
            when {
                line == FldicWordsSection -> {
                    inWordsSection = true
                    continue
                }
                inWordsSection && line.startsWith("[") -> break
                !inWordsSection || line.isBlank() || line.startsWith("#") -> continue
            }

            val components = line.split('\t')
            if (components.size < 2) continue
            val word = LatinDictionarySuggester.normalizeWord(components[0]) ?: continue
            val score = components[1].toLongOrNull()?.takeIf { it > 0L } ?: continue
            val previousScore = scores[word] ?: 0L
            if (score > previousScore) {
                scores[word] = score
                maxScore = maxOf(maxScore, score)
            }
        }
        return scores.mapValues { (_, score) -> normalizeFldicScore(score, maxScore) }
    }

    private fun Map<String, Int>.toNormalizedFrequencyMap(): Map<String, Int> {
        val frequencies = mutableMapOf<String, Int>()
        forEach { (word, frequency) ->
            val normalizedWord = LatinDictionarySuggester.normalizeWord(word) ?: return@forEach
            val normalizedFrequency = frequency.coerceIn(0, 255)
            frequencies[normalizedWord] = maxOf(frequencies[normalizedWord] ?: 0, normalizedFrequency)
        }
        return frequencies
    }

    private fun normalizeFldicScore(score: Long, maxScore: Long): Int {
        if (score <= 0L || maxScore <= 0L) return 0
        val normalizedScore = ln(score.toDouble() + 1.0) / ln(maxScore.toDouble() + 1.0)
        return (normalizedScore * 255.0).roundToInt().coerceIn(1, 255)
    }

    companion object {
        const val DefaultLanguageCode = "en"
        private const val DictionaryRoot = "ime/dict"
        private const val LegacyEnglishDictionaryPath = "$DictionaryRoot/data.json"
        private const val SupplementalEnglishDictionaryPath = "$DictionaryRoot/en_supplemental.json"
        internal const val EnglishMisspellingsPath = "$DictionaryRoot/en_misspellings.tsv"
        internal const val EnglishUnvettedWordsPath = "$DictionaryRoot/en_unvetted.txt"
        private const val FldicExtension = ".fldic"

        /** One word per line, lowercased, skipping blanks and `#` comments. */
        internal fun parseWordList(raw: String): Set<String> {
            return raw.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .map { it.lowercase() }
                .toHashSet()
        }
        private const val FldicWordsSection = "[words]"

        /**
         * Parses `misspelling<TAB>fix[<TAB>Commonwealth fix]` lines into misspelling to fix (the US one),
         * skipping blanks, `#` comments and malformed rows.
         */
        internal fun parseMisspellings(raw: String): Map<String, String> = parseMisspellingRows(raw, column = 1)

        /** The third column of [parseMisspellings]' rows: the fix British, Australian and similar English spell differently. */
        internal fun parseCommonwealthMisspellings(raw: String): Map<String, String> =
            parseMisspellingRows(raw, column = 2)

        private fun parseMisspellingRows(raw: String, column: Int): Map<String, String> {
            val table = HashMap<String, String>()
            for (line in raw.lineSequence()) {
                if (line.isBlank() || line.startsWith("#")) continue
                val fields = line.split('\t')
                if (fields.size < 2 || fields[0].isEmpty()) continue
                val misspelling = LatinDictionarySuggester.normalizeWord(fields[0]) ?: continue
                if (fields[1].trim().isEmpty()) continue
                val fix = fields.getOrNull(column)?.trim().orEmpty()
                if (fix.isEmpty()) continue
                table[misspelling] = fix
            }
            return table
        }

        fun normalizeLanguageCode(language: String): String {
            return language
                .substringBefore('-')
                .substringBefore('_')
                .lowercase()
                .ifBlank { DefaultLanguageCode }
        }

        fun assetPathsForLanguage(language: String): List<String> {
            val languageCode = normalizeLanguageCode(language)
            return if (languageCode == DefaultLanguageCode) {
                listOf(
                    "$DictionaryRoot/$DefaultLanguageCode.json",
                    "$DictionaryRoot/$DefaultLanguageCode$FldicExtension",
                    LegacyEnglishDictionaryPath,
                )
            } else {
                listOf(
                    "$DictionaryRoot/$languageCode.json",
                    "$DictionaryRoot/$languageCode$FldicExtension",
                )
            }
        }

        fun zipfAssetPath(language: String): String =
            "freq/${normalizeLanguageCode(language)}.tsv"
    }
}

internal data class LatinDictionarySnapshot(
    val frequencies: Map<String, Int>,
    val sortedWords: List<String>,
    val correctionWords: Collection<String> = frequencies.keys,
    val distanceTwoCorrectionWords: Collection<String> = correctionWords,
    val glideWords: List<String> = buildGlideWords(frequencies),
    val zipfTable: ZipfFrequencyTable = ZipfFrequencyTable.Empty,
    /** Known misspelling to its fix, from `ime/dict/en_misspellings.tsv`; English only. */
    val misspellings: Map<String, String> = emptyMap(),
    /** The misspellings whose fix is spelled differently outside the US ("jewelery" to "jewellery"). */
    val commonwealthMisspellings: Map<String, String> = emptyMap(),
    /**
     * Dictionary words no vetted word list backs, from `ime/dict/en_unvetted.txt`; English only. Web-corpus
     * entries like "thos" and "snd" that a slip lands on. Only these can be corrected as a likely slip
     * ([LatinDictionarySuggester.likelySlipFixes]); a rare real word ("snide") never is.
     */
    val unvettedWords: Set<String> = emptySet(),
) {
    val isLoaded: Boolean get() = frequencies.isNotEmpty()

    fun contains(word: String): Boolean = frequencies.containsKey(word)

    /**
     * Returns a usage-weighted frequency in `[0, 1]`. SCOWL provides the
     * baseline spelling-list signal, and the Zipf overlay (ROADMAP §7 Next-3.2)
     * adjusts the ranking with conversational frequency derived from subtitle
     * corpora. When the Zipf table is empty (no `assets/freq/<lang>.tsv` for
     * this language), this reduces to the pure SCOWL ratio that shipped
     * before Next-3.2.
     */
    fun frequencyFor(word: String): Double {
        val scowl = frequencies.getOrDefault(word, 0).coerceIn(0, 255) / 255.0
        return zipfTable.blendedFrequency(word, scowl)
    }

    /** Return a copy with the supplied Zipf overlay (used at load time). */
    fun withZipfTable(table: ZipfFrequencyTable): LatinDictionarySnapshot {
        if (table === zipfTable) return this
        return copy(zipfTable = table)
    }

    /** Return a copy carrying the misspelling table and its Commonwealth fixes (used at load time). */
    fun withMisspellings(
        table: Map<String, String>,
        commonwealth: Map<String, String> = emptyMap(),
    ): LatinDictionarySnapshot {
        if (table.isEmpty()) return this
        return copy(misspellings = table, commonwealthMisspellings = commonwealth)
    }

    /** Return a copy carrying the [unvettedWords] list (used at load time). */
    fun withUnvettedWords(words: Set<String>): LatinDictionarySnapshot {
        if (words.isEmpty()) return this
        return copy(unvettedWords = words)
    }

    /**
     * Lazily-built SymSpell delete-index over the high-confidence correction vocabulary.
     * The full English dictionary intentionally includes hundreds of thousands of rare
     * words for recognition, but indexing all of them multiplies memory during typing.
     * Rare supplemental words should prevent false autocorrect, not become aggressive
     * correction candidates.
     *
     * Wrapped in an OOM guard: if the build exhausts the heap despite all internal
     * budgets and scaling, the keyboard falls back to an empty index (no spell-
     * correction) rather than crashing. This is the last line of defense after the
     * per-word heap checks and budget scaling inside [SymSpellIndex.build].
     */
    val symSpellIndex: SymSpellIndex by lazy {
        val shouldLogBenchmark = BuildConfig.BUILD_TYPE == "benchmark"
        val startedAt = if (shouldLogBenchmark) {
            SystemClock.elapsedRealtimeNanos()
        } else {
            0L
        }
        Trace.beginSection("swiftfloris.nlp.symspell.build")
        try {
            SymSpellIndex.build(
                words = correctionWords,
                maxDeleteEntries = MaxCorrectionIndexDeleteEntries,
            ).also {
                if (shouldLogBenchmark) {
                    val durationMs = (SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000.0
                    Log.i(
                        "SwiftFlorisPerf",
                        "swiftfloris.nlp.symspellBuildMs=$durationMs maxDistance=1 " +
                            "wordCount=${it.indexedWordCount} complete=${it.isComplete} " +
                            "entryCount=${it.entryCount()}",
                    )
                }
            }
        } catch (_: OutOfMemoryError) {
            Log.w("SwiftFlorisNLP", "OOM building d1 SymSpell index; falling back to empty index")
            SymSpellIndex.empty()
        } finally {
            Trace.endSection()
        }
    }

    /**
     * Bounded distance-2 index over common correction words only. The full recognition
     * dictionary can exceed 500k English words; indexing all of it would waste IME heap
     * on rare words that should only block false autocorrect, not drive corrections.
     */
    val symSpellDistance2Index: SymSpellIndex by lazy {
        val shouldLogBenchmark = BuildConfig.BUILD_TYPE == "benchmark"
        val startedAt = if (shouldLogBenchmark) {
            SystemClock.elapsedRealtimeNanos()
        } else {
            0L
        }
        Trace.beginSection("swiftfloris.nlp.symspell.build")
        try {
            SymSpellIndex.build(
                words = distanceTwoCorrectionWords,
                maxDistance = 2,
                maxDeleteEntries = MaxDistanceTwoCorrectionIndexDeleteEntries,
            ).also {
                if (shouldLogBenchmark) {
                    val durationMs = (SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000.0
                    Log.i(
                        "SwiftFlorisPerf",
                        "swiftfloris.nlp.symspellBuildMs=$durationMs maxDistance=2 " +
                            "wordCount=${it.indexedWordCount} complete=${it.isComplete} " +
                            "entryCount=${it.entryCount()}",
                    )
                }
            }
        } catch (_: OutOfMemoryError) {
            Log.w("SwiftFlorisNLP", "OOM building d2 SymSpell index; falling back to empty index")
            SymSpellIndex.empty()
        } finally {
            Trace.endSection()
        }
    }

    private val topByFrequencyCache: List<String> by lazy {
        // Curated bootstrap: high-frequency dictionary words used as next-word suggestions
        // when the user's bigram store has nothing learned for the previous token. Cap at
        // 64 entries to keep the lazy-init cheap on cold start (Pixel 6: <2 ms over a 117k
        // dict). Skips one-letter words other than "a" / "I" to avoid noisy suggestions
        // like "b" or "x".
        frequencies.entries
            .asSequence()
            .filter { (word, _) ->
                word.length >= 2 || word.equals("a", ignoreCase = true) || word.equals("i", ignoreCase = true)
            }
            .sortedByDescending { it.value }
            .take(64)
            .map { it.key }
            .toList()
    }

    fun topByFrequency(n: Int): List<String> {
        if (n <= 0 || !isLoaded) return emptyList()
        return topByFrequencyCache.take(n)
    }

    fun mergeWith(supplementalFrequencies: Map<String, Int>): LatinDictionarySnapshot {
        if (supplementalFrequencies.isEmpty()) return this
        val merged = HashMap<String, Int>(frequencies.size + supplementalFrequencies.size)
        merged.putAll(frequencies)
        supplementalFrequencies.forEach { (word, frequency) ->
            merged[word] = maxOf(merged[word] ?: 0, frequency)
        }
        return from(merged)
    }

    companion object {
        private const val GlideDictionaryMinFrequency = 80
        private const val MaxGlideDictionaryWords = 120_000
        private const val MinGlideWordLength = 2
        private const val MaxGlideWordLength = 24
        private const val CorrectionIndexMinFrequency = 96
        private const val MaxCorrectionIndexWords = 96_000
        private const val MaxCorrectionIndexDeleteEntries = 750_000
        private const val DistanceTwoCorrectionIndexMinFrequency = 192
        private const val MaxDistanceTwoCorrectionIndexWords = 24_000
        private const val MaxDistanceTwoCorrectionIndexDeleteEntries = 320_000
        private const val MaxDistanceTwoCorrectionWordLength = 12

        val Empty = LatinDictionarySnapshot(emptyMap(), emptyList(), emptyList(), emptyList(), emptyList())

        fun from(frequencies: Map<String, Int>): LatinDictionarySnapshot {
            val correctionEntries = frequencies.entries
                .asSequence()
                .filter { (word, frequency) -> word.length >= 2 && frequency >= CorrectionIndexMinFrequency }
                .sortedWith(
                    compareByDescending<Map.Entry<String, Int>> { it.value }
                        .thenBy { it.key.length }
                        .thenBy { it.key }
                )
                .take(MaxCorrectionIndexWords)
                .toList()
            return LatinDictionarySnapshot(
                frequencies = frequencies,
                sortedWords = frequencies.keys.sorted(),
                correctionWords = correctionEntries
                    .map { it.key },
                distanceTwoCorrectionWords = correctionEntries
                    .asSequence()
                    .filter { (word, frequency) ->
                        word.length in 3..MaxDistanceTwoCorrectionWordLength &&
                            frequency >= DistanceTwoCorrectionIndexMinFrequency
                    }
                    .take(MaxDistanceTwoCorrectionIndexWords)
                    .map { it.key }
                    .toList(),
            )
        }

        private fun buildGlideWords(frequencies: Map<String, Int>): List<String> {
            if (frequencies.isEmpty()) return emptyList()
            return frequencies.entries
                .asSequence()
                .filter { (word, frequency) ->
                    word.length in MinGlideWordLength..MaxGlideWordLength &&
                        frequency >= GlideDictionaryMinFrequency
                }
                .sortedWith(
                    compareByDescending<Map.Entry<String, Int>> { it.value }
                        .thenBy { it.key.length }
                        .thenBy { it.key }
                )
                .take(MaxGlideDictionaryWords)
                .map { it.key }
                .toList()
        }
    }
}

internal data class LatinSuggestion(
    val text: String,
    val confidence: Double,
    val isEligibleForAutoCommit: Boolean,
)

internal object LatinDictionarySuggester {
    private const val MinCompletionLength = 2

    /** Above the best dictionary completion (0.2 + 0.6 for a top-frequency word). */
    private const val PersonalCompletionConfidence = 0.9
    private const val MinCorrectionLength = 3
    private const val MinCorrectionOverCompletionLength = 4
    private const val MaxTwoEditWordLength = 8
    // Frequency threshold (0.0–1.0, normalized from a 0–255 dictionary count).
    // SwiftKey-style behavior: only auto-replace the user's typed word when the candidate is
    // genuinely common. Lower thresholds aggressively swap unusual words and proper nouns.
    // 0.78 ≈ frequency 198/255 — covers high-confidence typo fixes like "teh" → "the" while
    // sparing rarer dictionary words that share an edit-distance-1 neighborhood.
    private const val AutoCommitMinFrequency = 0.78

    /**
     * Distance-2 corrections only auto-commit when the candidate is *very* common — covers
     * the canonical SwiftKey-style autocorrections of long-word typos like "recieved" →
     * "received" or "tommorow" → "tomorrow" without aggressively replacing rarer words
     * the user might have meant. 0.92 ≈ frequency 234/255, which lands roughly in the top
     * ~3,000 SCOWL words.
     */
    private const val AutoCommitMinFrequencyDistance2 = 0.92

    /** A dictionary word is a likely real-word slip when it fits the words before it this badly or worse. */
    internal const val RealWordMaxTypedFit = 0.35

    /** And a one-edit dictionary word fits them at least this much better. */
    internal const val RealWordMinFitGain = 0.25

    private const val MaxRealWordCorrections = 2

    /**
     * A dictionary word this rare or rarer is taken for a slip when a one-edit word is at least [SlipFixMinGap]
     * more common: "thos" (0.51) for "this" (0.93), "thd" (0.15) for "the". The bundled list carries thousands of
     * such entries (abbreviations, surnames, archaic forms) that otherwise sit between a slip and its fix.
     */
    private const val SlipTypedMaxFrequency = 0.60
    private const val SlipFixMinGap = 0.25

    /** A typed word up to this common still gives way to one of the very top words: "amd" (0.73) for "and" (0.97). */
    private const val SlipTypedMaxFrequencyForTopWord = 0.75
    private const val SlipTopWordMinFrequency = 0.95
    private const val SlipTopWordMinGap = 0.20

    /**
     * A word shorter than [MinCorrectionOverCompletionLength] keeps its correction eligible over its completions when
     * the correction is this much more common than every one of them: "thw" goes to "the", not "thwart".
     */
    private const val CorrectionOverCompletionMinGap = 0.20

    /**
     * Countries whose English keyboard takes the US fix from the misspelling list: no country (plain "en"), the US
     * and its territories, the countries in free association with it, the Philippines and Liberia. Canada takes
     * the Commonwealth fix except where [canadianKeepsUsSpelling]. The rest, the bundled en-UK and en-AU presets
     * among them, take the Commonwealth fix where the list has one.
     */
    internal val UsSpellingRegions =
        setOf("", "US", "PH", "LR", "PR", "GU", "VI", "AS", "MP", "UM", "MH", "FM", "PW")

    private const val CanadaRegion = "CA"

    private val IzeSpelling = Regex("iz(?=e|ing|ation)")

    /**
     * Canadian spelling writes neighbour, jewellery and licence like British English but keeps the US -ize words
     * (realize) and fulfill, so those Commonwealth fixes ([commonwealthFix] against [usFix]) give way in Canada.
     */
    internal fun canadianKeepsUsSpelling(usFix: String, commonwealthFix: String): Boolean {
        return commonwealthFix == usFix.replace(IzeSpelling, "is") ||
            (usFix.endsWith("ll") && commonwealthFix == usFix.dropLast(1))
    }

    /**
     * [contextFit] tells how well a word fits the words typed before it, 0.0 to 1.0, or null when that can't be
     * told. With it a dictionary word that fits badly, like "form" in "a letter form my mother", gets one-edit
     * words that fit better as candidates. They're never eligible for auto-commit.
     *
     * [personalContinuations] are words the user has typed after the previous one or two words, best first
     * (see `PersonalBigramStore.predict`). Those that complete [rawWord] lead the completions, so after
     * "see you" typing "to" offers "tomorrow" when that's what the user writes there, even though a dozen
     * dictionary words starting with "to" are more common. One typed letter is enough for these.
     */
    fun suggest(
        rawWord: String,
        dictionary: LatinDictionarySnapshot,
        maxCandidateCount: Int,
        languageCode: String = LatinDictionaryStore.DefaultLanguageCode,
        userOverlay: Map<String, Int> = emptyMap(),
        contextFit: ((String) -> Double?)? = null,
        region: String = "",
        personalContinuations: List<String> = emptyList(),
    ): List<LatinSuggestion> {
        if (maxCandidateCount <= 0 || !dictionary.isLoaded) return emptyList()
        val normalizedWord = normalizeWord(rawWord) ?: return emptyList()
        englishContractionCorrection(
            rawWord = rawWord,
            dictionary = dictionary,
            languageCode = languageCode,
        )?.let { return listOf(it) }
        englishMisspellingCorrection(
            rawWord = rawWord,
            dictionary = dictionary,
            languageCode = languageCode,
            userOverlay = userOverlay,
            region = region,
        )?.let { return listOf(it) }
        // ROADMAP §7 Next-3.3a — single-letter proper-noun completion. When
        // the user types a single capital letter (e.g. "F"), surface the
        // most common dictionary words starting with that letter case-matched
        // ("For", "From", "Foo", ...). Skip the path for lowercase single
        // letters so the strip doesn't flood with low-signal completions
        // every time the user begins typing a normal word. The SwiftKey/Gboard
        // "proper-noun completion" surface is the explicit target.
        if (normalizedWord.length == 1 &&
            rawWord.isNotEmpty() &&
            rawWord.first().isUpperCase()
        ) {
            return singleLetterProperNounCompletions(normalizedWord, rawWord, dictionary, maxCandidateCount)
        }
        val personalCompletions = personalCompletions(normalizedWord, personalContinuations, maxCandidateCount)
        if (normalizedWord.length < MinCompletionLength) {
            return personalCompletions.map { it.withTypedCase(rawWord) }
        }

        val completionCandidates = completions(normalizedWord, dictionary, maxCandidateCount, userOverlay)
        val bestCompletionFrequency = completionCandidates.firstOrNull()
            ?.let { blendedFrequencyFor(it.text, dictionary, userOverlay) }
            ?: 0.0
        // ROADMAP §7 Next-3 — when the user has typed [normalizedWord] before
        // (overlay carries it under any frequency), treat it as a known word so
        // we skip running corrections against it.  Otherwise the ranker would
        // happily replace a user-typed proper noun with a SCOWL look-alike.
        val isKnownInOverlay = userOverlay.containsKey(normalizedWord)
        val slipFixes = likelySlipFixes(normalizedWord, dictionary, userOverlay, contextFit)
        val correctionCandidates = if (slipFixes.isNotEmpty()) {
            // Every fix is far more common than the typed word, so each may be committed; the ranker picks
            // between them by the taps and the words before ("tou" to "you" when the t was a slip off y).
            slipFixes.take(maxCandidateCount).map { fix ->
                LatinSuggestion(
                    text = fix,
                    confidence = correctionConfidence(blendedFrequencyForCorrection(fix, dictionary, userOverlay), 1),
                    isEligibleForAutoCommit = true,
                )
            }
        } else if (!dictionary.contains(normalizedWord) &&
            !isKnownInOverlay &&
            normalizedWord.length >= MinCorrectionLength
        ) {
            corrections(
                word = normalizedWord,
                dictionary = dictionary,
                maxCandidateCount = maxCandidateCount,
                userOverlay = userOverlay,
            )
        } else {
            emptyList()
        }.map { candidate ->
            val deferred = shouldDeferCorrectionForActiveCompletion(
                normalizedWord = normalizedWord,
                completionCandidates = completionCandidates,
                correctionCandidate = candidate,
                correctionFrequency = blendedFrequencyForCorrection(candidate.text, dictionary, userOverlay),
                bestCompletionFrequency = bestCompletionFrequency,
            )
            if (deferred) candidate.copy(isEligibleForAutoCommit = false) else candidate
        }
        // ROADMAP §7 Next-3 — when any completion candidate is a word the
        // user has typed before (lives in the overlay), demote SCOWL
        // primary corrections to secondary. The user explicitly taught the
        // keyboard this word; we don't want a SCOWL edit-distance neighbour
        // (e.g. "food" for "foob…") jumping ahead of "foobar" just because
        // the SCOWL word is more common in the general corpus.
        val hasOverlayCompletion = personalCompletions.isNotEmpty() || (
            userOverlay.isNotEmpty() &&
                completionCandidates.any { userOverlay.containsKey(it.text.lowercase()) }
            )
        // Only the leading correction can move ahead of the completions. The others may be eligible too (the
        // ranker weighs the taps between them), but while a prefix is being typed its completions keep the lead.
        fun isPromoted(index: Int, candidate: LatinSuggestion): Boolean =
            index == 0 && shouldPromoteCorrectionBeforeCompletions(normalizedWord, completionCandidates, candidate)
        val primaryCorrections = if (hasOverlayCompletion) {
            emptyList()
        } else {
            correctionCandidates.filterIndexed { index, candidate -> isPromoted(index, candidate) }
        }
        val secondaryCorrections = if (hasOverlayCompletion) {
            correctionCandidates
        } else {
            correctionCandidates.filterIndexed { index, candidate -> !isPromoted(index, candidate) }
        }
        val realWordCorrections = if (contextFit != null && dictionary.contains(normalizedWord)) {
            realWordCorrections(normalizedWord, dictionary, userOverlay, contextFit)
        } else {
            emptyList()
        }

        val seen = mutableSetOf<String>()
        return buildList {
            primaryCorrections.forEach { candidate ->
                if (seen.add(candidate.text.lowercase())) add(candidate.withTypedCase(rawWord))
            }
            realWordCorrections.forEach { candidate ->
                if (seen.add(candidate.text.lowercase())) add(candidate.withTypedCase(rawWord))
            }
            personalCompletions.forEach { candidate ->
                if (seen.add(candidate.text.lowercase())) add(candidate.withTypedCase(rawWord))
            }
            completionCandidates.forEach { candidate ->
                if (seen.add(candidate.text.lowercase())) add(candidate.withTypedCase(rawWord))
            }
            secondaryCorrections.forEach { candidate ->
                if (seen.add(candidate.text.lowercase())) add(candidate.withTypedCase(rawWord))
            }
        }.keepingEligible(maxCandidateCount)
    }

    /**
     * The first [maxCandidateCount] of these, except that candidates eligible for auto-commit past the cut replace
     * the last ones that aren't, so eight completions of a prefix can't push the corrections out before the ranker
     * sees them. Order is kept otherwise.
     */
    private fun List<LatinSuggestion>.keepingEligible(maxCandidateCount: Int): List<LatinSuggestion> {
        if (size <= maxCandidateCount) return this
        val head = take(maxCandidateCount)
        val rescued = drop(maxCandidateCount).filter { it.isEligibleForAutoCommit }
        val replaceable = head.indices.reversed().filter { !head[it].isEligibleForAutoCommit }
        val replaced = replaceable.take(rescued.size).toSet()
        if (replaced.isEmpty()) return head
        return head.filterIndexed { index, _ -> index !in replaced } + rescued.take(replaced.size)
    }

    /**
     * The [continuations] that complete [prefix], in their order, as candidates that rank above dictionary
     * completions. Never eligible for auto-commit: the user is still typing the word.
     */
    private fun personalCompletions(
        prefix: String,
        continuations: List<String>,
        maxCandidateCount: Int,
    ): List<LatinSuggestion> {
        if (continuations.isEmpty()) return emptyList()
        return continuations
            .asSequence()
            .map { it.lowercase() }
            .filter { it.length > prefix.length && it.startsWith(prefix) }
            .distinct()
            .take(maxCandidateCount)
            .mapIndexed { index, word ->
                LatinSuggestion(
                    text = word,
                    confidence = (PersonalCompletionConfidence - 0.02 * index).coerceAtLeast(0.5),
                    isEligibleForAutoCommit = false,
                )
            }
            .toList()
    }

    /**
     * One-edit dictionary words that fit the context at least [RealWordMinFitGain] better than [word], best fit
     * first, when [word] fits it [RealWordMaxTypedFit] or worse. None are eligible for auto-commit.
     */
    private fun realWordCorrections(
        word: String,
        dictionary: LatinDictionarySnapshot,
        userOverlay: Map<String, Int>,
        contextFit: (String) -> Double?,
    ): List<LatinSuggestion> {
        if (word.length < MinCorrectionLength) return emptyList()
        val typedFit = contextFit(word) ?: return emptyList()
        if (typedFit > RealWordMaxTypedFit) return emptyList()
        return knownEdits1(word, dictionary)
            .asSequence()
            .filter { (_, distance) -> distance == 1 }
            .mapNotNull { (candidate, _) ->
                contextFit(candidate)?.takeIf { fit -> fit >= typedFit + RealWordMinFitGain }?.let { candidate to it }
            }
            .sortedByDescending { (_, fit) -> fit }
            .take(MaxRealWordCorrections)
            .map { (candidate, _) ->
                LatinSuggestion(
                    text = candidate,
                    confidence = correctionConfidence(
                        blendedFrequencyForCorrection(candidate, dictionary, userOverlay),
                        distance = 1,
                    ),
                    isEligibleForAutoCommit = false,
                )
            }
            .toList()
    }

    /**
     * ROADMAP §7 Next-3.3a — single-letter proper-noun completion helper.
     * Returns the top-frequency dictionary words starting with [normalizedWord]
     * (single lowercase letter, e.g. "f"), case-matched against [rawWord] via
     * [withTypedCase] (so "F" → "For" not "for"). Single-letter words "a" and
     * "i" are skipped because they're the typed letter itself; otherwise we
     * surface up to [maxCandidateCount] candidates ranked by dictionary frequency.
     * Never returns auto-commit candidates — single-letter prefixes carry too
     * much ambiguity to autocommit safely.
     */
    private fun singleLetterProperNounCompletions(
        normalizedWord: String,
        rawWord: String,
        dictionary: LatinDictionarySnapshot,
        maxCandidateCount: Int,
    ): List<LatinSuggestion> {
        if (normalizedWord.length != 1) return emptyList()
        val prefix = normalizedWord.first()
        val seen = HashSet<String>(maxCandidateCount * 2)
        val result = ArrayList<LatinSuggestion>(maxCandidateCount)
        // Use the cached topByFrequency list so we don't sort the whole 117k-word
        // dictionary on every keystroke; this lives behind `by lazy` in the snapshot.
        for (word in dictionary.topByFrequency(SingleLetterCandidatePool)) {
            if (result.size >= maxCandidateCount) break
            if (word.length < 2) continue                     // skip the typed letter itself
            if (word.first().lowercaseChar() != prefix) continue
            val lowered = word.lowercase()
            if (!seen.add(lowered)) continue
            result += LatinSuggestion(
                text = word,
                confidence = correctionConfidence(dictionary.frequencyFor(word), distance = 1) - 0.10,
                isEligibleForAutoCommit = false,
            ).withTypedCase(rawWord)
        }
        return result
    }

    /**
     * How many of the top-frequency dictionary words to scan when synthesising
     * single-letter completions. 2,048 covers virtually every letter's top-N
     * useful completions on a typical SCOWL-merged dictionary while staying
     * within the lazy-cache budget.
     */
    private const val SingleLetterCandidatePool = 2048

    private fun shouldDeferCorrectionForActiveCompletion(
        normalizedWord: String,
        completionCandidates: List<LatinSuggestion>,
        correctionCandidate: LatinSuggestion,
        correctionFrequency: Double,
        bestCompletionFrequency: Double,
    ): Boolean {
        if (completionCandidates.isEmpty()) return false
        if (normalizedWord.length < MinCorrectionOverCompletionLength) {
            return correctionFrequency < bestCompletionFrequency + CorrectionOverCompletionMinGap
        }
        return correctionCandidate.text.length < normalizedWord.length
    }

    /**
     * One-edit dictionary words, most common first, that make [word] more likely a slip than meant, or empty when
     * it isn't one. [word] must be one of the dictionary's [LatinDictionarySnapshot.unvettedWords] and rare (see
     * [SlipTypedMaxFrequency]), and each fix far more common. A word the user has typed before ([userOverlay]) is
     * never a slip, and neither is one that fits the words before it ([contextFit] above [RealWordMaxTypedFit]).
     */
    internal fun likelySlipFixes(
        word: String,
        dictionary: LatinDictionarySnapshot,
        userOverlay: Map<String, Int>,
        contextFit: ((String) -> Double?)?,
    ): List<String> {
        if (word.length < MinCorrectionLength || userOverlay.containsKey(word) || word !in dictionary.unvettedWords) {
            return emptyList()
        }
        val typedFrequency = dictionary.frequencyFor(word)
        if (typedFrequency > SlipTypedMaxFrequencyForTopWord) return emptyList()
        val typedFit = contextFit?.invoke(word)
        if (typedFit != null && typedFit > RealWordMaxTypedFit) return emptyList()
        return knownEdits1(word, dictionary)
            .asSequence()
            .filter { (_, distance) -> distance == 1 }
            .map { (candidate, _) -> candidate to blendedFrequencyForCorrection(candidate, dictionary, userOverlay) }
            .filter { (_, frequency) -> isSlipFix(typedFrequency, frequency) }
            .filter { (candidate, _) -> typedFit == null || (contextFit(candidate) ?: 0.0) >= typedFit }
            .sortedWith(
                compareByDescending<Pair<String, Double>> { (_, frequency) -> frequency }
                    .thenBy { (candidate, _) -> candidate.length }
                    .thenBy { (candidate, _) -> candidate },
            )
            .map { (candidate, _) -> candidate }
            .toList()
    }

    private fun isSlipFix(typedFrequency: Double, fixFrequency: Double): Boolean {
        if (fixFrequency < AutoCommitMinFrequency) return false
        if (typedFrequency <= SlipTypedMaxFrequency && fixFrequency >= typedFrequency + SlipFixMinGap) return true
        return fixFrequency >= SlipTopWordMinFrequency && fixFrequency >= typedFrequency + SlipTopWordMinGap
    }

    private fun shouldPromoteCorrectionBeforeCompletions(
        normalizedWord: String,
        completionCandidates: List<LatinSuggestion>,
        correctionCandidate: LatinSuggestion,
    ): Boolean {
        if (completionCandidates.isEmpty()) return false
        if (!correctionCandidate.isEligibleForAutoCommit) return false
        return !correctionCandidate.text.startsWith(normalizedWord)
    }

    fun corrections(
        word: String,
        dictionary: LatinDictionarySnapshot,
        maxCandidateCount: Int,
        userOverlay: Map<String, Int> = emptyMap(),
    ): List<LatinSuggestion> {
        if (maxCandidateCount <= 0 || word.length < MinCorrectionLength || !dictionary.isLoaded) return emptyList()
        val oneEditCandidates = knownEdits1(word, dictionary)
        val scowlCandidates = if (oneEditCandidates.isNotEmpty()) {
            oneEditCandidates
        } else if (word.length <= MaxTwoEditWordLength) {
            knownEdits2(word, dictionary)
        } else {
            emptyList()
        }
        // ROADMAP §7 Next-3 — also scan the user-overlay for edit-distance
        // candidates so a typo of a learned word ("Patrk" → "Patrick")
        // surfaces the user's word as the correction. The overlay is small
        // (typically a few hundred entries per locale even for heavy users)
        // so a linear scan is faster than building a delete-index for it.
        val overlayCandidates = overlayEditDistanceCandidates(word, userOverlay)
        val candidateDistances = if (scowlCandidates.isEmpty()) {
            overlayCandidates
        } else if (overlayCandidates.isEmpty()) {
            scowlCandidates
        } else {
            scowlCandidates + overlayCandidates
        }

        return candidateDistances
            .asSequence()
            .filter { (candidate, _) -> candidate != word }
            .distinctBy { (candidate, _) -> candidate }
            .sortedWith(
                compareBy<Pair<String, Int>> { (_, distance) -> distance }
                    .thenByDescending { (candidate, _) ->
                        blendedFrequencyForCorrection(candidate, dictionary, userOverlay)
                    }
                    .thenBy { (candidate, _) -> candidate.length }
                    .thenBy { (candidate, _) -> candidate }
            )
            .take(maxCandidateCount)
            // Each correction common enough may be committed, not only the most common one, so the ranker
            // weighs the taps and the words before between them instead of a lone eligible pick winning on role.
            .map { (candidate, distance) ->
                val frequency =
                    blendedFrequencyForCorrection(candidate, dictionary, userOverlay)
                val autoCommitThreshold = when (distance) {
                    1 -> AutoCommitMinFrequency
                    2 -> AutoCommitMinFrequencyDistance2
                    else -> Double.POSITIVE_INFINITY
                }
                LatinSuggestion(
                    text = candidate,
                    confidence = correctionConfidence(frequency, distance),
                    isEligibleForAutoCommit = distance in 1..2 &&
                        word.length >= MinCorrectionLength &&
                        frequency >= autoCommitThreshold,
                )
            }
            .toList()
    }

    /**
     * Pure linear scan over the user-overlay returning `(word, distance)`
     * pairs for every overlay word within edit-distance 1 or 2 of the
     * typed [word]. Returns empty when the overlay is empty.
     */
    private fun overlayEditDistanceCandidates(
        word: String,
        userOverlay: Map<String, Int>,
    ): List<Pair<String, Int>> {
        if (userOverlay.isEmpty()) return emptyList()
        val maxDistance = if (word.length <= MaxTwoEditWordLength) 2 else 1
        val out = ArrayList<Pair<String, Int>>(4)
        for (key in userOverlay.keys) {
            // Length-based prune first — distance is at least |len(a) - len(b)|.
            if (kotlin.math.abs(key.length - word.length) > maxDistance) continue
            // Damerau (not plain Levenshtein): overlay candidates are merged with the
            // SCOWL candidates and ranked/auto-committed by a shared distance field, so
            // both sources must use the same metric. Plain Levenshtein counted an
            // adjacent transposition (e.g. learned "patrick" typed "patrcik") as 2,
            // sinking it below distance-1 look-alikes and to the stricter threshold.
            val d = boundedDamerauLevenshteinDistance(word, key, maxDistance) ?: continue
            if (d in 1..maxDistance) out.add(key to d)
        }
        return out
    }

    /**
     * Frequency lookup for correction ranking that consults the user-overlay
     * as a fallback. Overlay-only words (not in SCOWL) get their stored
     * frequency; words in both pick the higher signal.
     */
    private fun blendedFrequencyForCorrection(
        word: String,
        dictionary: LatinDictionarySnapshot,
        userOverlay: Map<String, Int>,
    ): Double {
        val scowl = dictionary.frequencyFor(word)
        val overlay = (userOverlay[word] ?: 0).coerceIn(0, 255) / 255.0
        return maxOf(scowl, overlay)
    }

    fun normalizeWord(rawWord: String): String? {
        val trimmedWord = rawWord.trim().trim { char -> !char.isLetter() && char != '\'' }
        if (trimmedWord.isEmpty() || trimmedWord.none { it.isLetter() }) return null
        if (trimmedWord.any { !it.isLetter() && it != '\'' }) return null
        return trimmedWord.lowercase()
    }

    fun englishContractionCorrection(
        rawWord: String,
        dictionary: LatinDictionarySnapshot,
        languageCode: String,
    ): LatinSuggestion? {
        if (LatinDictionaryStore.normalizeLanguageCode(languageCode) != LatinDictionaryStore.DefaultLanguageCode) {
            return null
        }
        val correction = ImmediateAutocorrect.englishContraction(rawWord, languageCode) ?: return null
        if (!dictionary.contains(correction.dictionaryWord)) return null
        // For DICTIONARY_GATED contractions ("ill", "id", "im", "well", "hell", "shell",
        // "wed", "shed", "lets", "wont", "cant", "its", ...) only commit when the typed
        // word is NOT itself a valid dictionary word — otherwise the user genuinely meant
        // the standalone form. SAFE-tier contractions ("dont", "youre", "weve", ...) have
        // no real-word collision and always auto-commit.
        if (correction.tier == ImmediateAutocorrectCorrection.Tier.DICTIONARY_GATED) {
            val typedNormalized = normalizeWord(rawWord)
            if (typedNormalized != null && dictionary.contains(typedNormalized)) {
                return null
            }
        }
        return LatinSuggestion(
            text = correction.text,
            confidence = 1.0,
            isEligibleForAutoCommit = true,
        )
    }

    /**
     * Fixes a word from the bundled English misspelling list ("teh", "recieve", "untill")
     * with full confidence, the way SwiftKey does on space. Edit-distance corrections can't
     * do this reliably: "teh" sits one edit from "ten" and "tea", and distance-2 fixes need
     * a very common target. The list only holds words no English spell-check dictionary
     * accepts, and the build scripts keep them out of the word lists, so the dictionary
     * check here only matters when an addon dictionary lists the word. A word the user
     * taught the keyboard stays as typed, and ALL CAPS input is left alone like contractions.
     * [region] is the keyboard's country: outside [UsSpellingRegions] a fix that's spelled
     * differently there ("jewelery" to "jewellery", "relize" to "realise") uses that spelling,
     * and in Canada only where [canadianKeepsUsSpelling] doesn't say otherwise.
     */
    fun englishMisspellingCorrection(
        rawWord: String,
        dictionary: LatinDictionarySnapshot,
        languageCode: String,
        userOverlay: Map<String, Int> = emptyMap(),
        region: String = "",
    ): LatinSuggestion? {
        if (dictionary.misspellings.isEmpty()) return null
        if (LatinDictionaryStore.normalizeLanguageCode(languageCode) != LatinDictionaryStore.DefaultLanguageCode) {
            return null
        }
        val normalizedWord = normalizeWord(rawWord) ?: return null
        val usFix = dictionary.misspellings[normalizedWord] ?: return null
        val country = region.uppercase()
        val fix = dictionary.commonwealthMisspellings[normalizedWord]
            ?.takeIf { country !in UsSpellingRegions }
            ?.takeUnless { country == CanadaRegion && canadianKeepsUsSpelling(usFix, it) }
            ?: usFix
        if (dictionary.contains(normalizedWord) || userOverlay.containsKey(normalizedWord)) return null
        val typedWord = rawWord.trim().trim { char -> !char.isLetter() && char != '\'' }
        val letters = typedWord.filter { it.isLetter() }
        if (letters.length > 1 && letters.all { it.isUpperCase() }) return null
        return LatinSuggestion(
            text = ImmediateAutocorrect.applyTypedCase(fix, typedWord),
            confidence = 1.0,
            isEligibleForAutoCommit = true,
        )
    }

    private fun completions(
        prefix: String,
        dictionary: LatinDictionarySnapshot,
        maxCandidateCount: Int,
        userOverlay: Map<String, Int> = emptyMap(),
    ): List<LatinSuggestion> {
        // Merge SCOWL prefix-matches with user-overlay prefix-matches so
        // user-typed words compete for the candidate slots. Dedup via a set
        // because a SCOWL word the user has also typed shouldn't appear twice.
        val scowlMatches = wordsWithPrefix(prefix, dictionary.sortedWords)
        val overlayMatches = if (userOverlay.isEmpty()) {
            emptyList()
        } else {
            userOverlay.keys.filter { it.startsWith(prefix) }
        }
        val merged = LinkedHashSet<String>(scowlMatches.size + overlayMatches.size)
        merged.addAll(scowlMatches)
        merged.addAll(overlayMatches)
        return merged
            .asSequence()
            .filter { it.length > prefix.length }
            .sortedWith(
                compareByDescending<String> { blendedFrequencyFor(it, dictionary, userOverlay) }
                    .thenBy { it.length }
                    .thenBy { it }
            )
            .take(maxCandidateCount)
            .map { word ->
                val blended = blendedFrequencyFor(word, dictionary, userOverlay)
                LatinSuggestion(
                    text = word,
                    confidence = (0.2 + blended * 0.6).coerceIn(0.0, 1.0),
                    isEligibleForAutoCommit = false,
                )
            }
            .toList()
    }

    /**
     * ROADMAP §7 Next-3 — blended SCOWL + user-overlay weight on the same
     * `[0, 1]` scale as [LatinDictionarySnapshot.frequencyFor]. Picks the
     * **max** of the two signals (rather than an additive blend) so:
     *
     *  - A word the user has typed lots of times (overlay freq → 250) ranks
     *    above its SCOWL look-alikes for the same prefix.
     *  - A word that's both common in SCOWL and typed by the user still
     *    uses the SCOWL signal if SCOWL says it's a top-1000 word.
     *  - A word ONLY in the overlay (proper noun the user invented) ranks
     *    using its overlay frequency alone.
     */
    private fun blendedFrequencyFor(
        word: String,
        dictionary: LatinDictionarySnapshot,
        userOverlay: Map<String, Int>,
    ): Double {
        val scowl = dictionary.frequencyFor(word)
        if (userOverlay.isEmpty()) return scowl
        val overlay = (userOverlay[word] ?: 0).coerceIn(0, 255) / 255.0
        return maxOf(scowl, overlay)
    }

    private fun wordsWithPrefix(prefix: String, sortedWords: List<String>): List<String> {
        val start = lowerBound(sortedWords, prefix)
        val matches = mutableListOf<String>()
        var index = start
        while (index < sortedWords.size) {
            val word = sortedWords[index]
            if (!word.startsWith(prefix)) break
            matches.add(word)
            index++
        }
        return matches
    }

    private fun lowerBound(words: List<String>, target: String): Int {
        var low = 0
        var high = words.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (words[mid] < target) {
                low = mid + 1
            } else {
                high = mid
            }
        }
        return low
    }

    private fun knownEdits1(word: String, dictionary: LatinDictionarySnapshot): List<Pair<String, Int>> {
        // SymSpell-based fast path: the precomputed delete-index returns every dict word
        // within edit-distance ≤ 1 in O(L²) instead of generating L · |alphabet| ≈ L · 54
        // candidate strings per call. The shared-delete test is a SUPERSET (a single
        // delete applied to both query and dict word can collide at true distance 2), so
        // we must verify each candidate's actual Damerau distance — exactly as knownEdits2
        // does — instead of labelling them all distance 1, otherwise a true-distance-2
        // typo auto-commits at the looser distance-1 frequency threshold.
        return dictionary.symSpellIndex.candidatesAtDistance1(word)
            .asSequence()
            .filter { it != word }
            .mapNotNull { candidate ->
                val distance = boundedDamerauLevenshteinDistance(word, candidate, maxDistance = 2)
                    ?: return@mapNotNull null
                candidate to distance
            }
            .filter { (_, distance) -> distance in 1..2 }
            .toList()
    }

    private fun knownEdits2(word: String, dictionary: LatinDictionarySnapshot): List<Pair<String, Int>> {
        return dictionary.symSpellDistance2Index.candidates(word)
            .asSequence()
            .filter { candidate -> candidate != word }
            .mapNotNull { candidate ->
                val distance = boundedDamerauLevenshteinDistance(word, candidate, maxDistance = 2)
                    ?: return@mapNotNull null
                candidate to distance
            }
            .filter { (_, distance) -> distance in 1..2 }
            .toList()
    }

    private fun boundedDamerauLevenshteinDistance(left: String, right: String, maxDistance: Int): Int? {
        if (abs(left.length - right.length) > maxDistance) return null
        if (left == right) return 0
        val rows = Array(left.length + 1) { IntArray(right.length + 1) }
        for (i in 0..left.length) rows[i][0] = i
        for (j in 0..right.length) rows[0][j] = j
        for (i in 1..left.length) {
            var rowMin = maxDistance + 1
            for (j in 1..right.length) {
                val cost = if (left[i - 1] == right[j - 1]) 0 else 1
                var value = minOf(
                    rows[i - 1][j] + 1,
                    rows[i][j - 1] + 1,
                    rows[i - 1][j - 1] + cost,
                )
                if (i > 1 &&
                    j > 1 &&
                    left[i - 1] == right[j - 2] &&
                    left[i - 2] == right[j - 1]
                ) {
                    value = minOf(value, rows[i - 2][j - 2] + 1)
                }
                rows[i][j] = value
                rowMin = minOf(rowMin, value)
            }
            if (rowMin > maxDistance) return null
        }
        return rows[left.length][right.length].takeIf { it <= maxDistance }
    }

    private fun correctionConfidence(frequency: Double, distance: Int): Double {
        val editDistanceBonus = if (distance == 1) 0.1 else 0.0
        return (0.35 + frequency * 0.55 + editDistanceBonus).coerceIn(0.0, 1.0)
    }

    private fun LatinSuggestion.withTypedCase(rawWord: String): LatinSuggestion {
        return copy(text = applyTypedCase(text, rawWord))
    }

    /**
     * ROADMAP §7 Next-3.3 — capitalization-aware suggestions. Apply the typed
     * prefix's case pattern to a candidate so the suggestion strip surfaces
     * "Foo" when the user types "F", "FOO" when they type "FO", and "foo" when
     * they type "f". Closes FlorisBoard #1007.
     *
     * Rules (Title Case beats ALL_CAPS only for single-letter prefixes since
     * a lone capital is ambiguous):
     *  - prefix has ≥2 letters AND all are uppercase → candidate uppercased
     *  - prefix's first letter is uppercase → candidate titlecased on first char
     *  - else → candidate returned as-is (typically already lowercase)
     *
     * Covered by LatinDictionarySuggesterTest.
     */
    private fun applyTypedCase(candidate: String, rawWord: String): String {
        val letters = rawWord.filter { it.isLetter() }
        return when {
            letters.length > 1 && letters.all { it.isUpperCase() } -> candidate.uppercase()
            letters.firstOrNull()?.isUpperCase() == true -> candidate.replaceFirstChar { it.titlecase() }
            else -> candidate
        }
    }
}
