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
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.appContext
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.dictionary.PersonalBigramStore
import dev.patrickgold.florisboard.ime.dictionary.PersonalTrigramStore
import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.nlp.ImmediateAutocorrect
import dev.patrickgold.florisboard.ime.nlp.ImmediateAutocorrectCorrection
import dev.patrickgold.florisboard.ime.nlp.SpellingProvider
import dev.patrickgold.florisboard.ime.nlp.SpellingResult
import dev.patrickgold.florisboard.ime.nlp.SuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.SuggestionProvider
import dev.patrickgold.florisboard.ime.nlp.WordSuggestionCandidate
import dev.patrickgold.florisboard.lib.devtools.flogDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.florisboard.lib.android.readText
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

class LatinLanguageProvider(context: Context) : SpellingProvider, SuggestionProvider {
    companion object {
        // Default user ID used for all subtypes, unless otherwise specified.
        // See `ime/core/Subtype.kt` Line 210 and 211 for the default usage
        const val ProviderId = "org.florisboard.nlp.providers.latin"
    }

    private val appContext by context.appContext()

    private val dictionaryStore = LatinDictionaryStore(
        readAsset = LatinDictionaryAssetReader { path ->
            withContext(Dispatchers.IO) {
                try {
                    appContext.assets.readText(path)
                } catch (_: IOException) {
                    null
                }
            }
        },
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
        val corrections = LatinDictionarySuggester.corrections(normalizedWord, dictionary, maxSuggestionCount)
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
        val currentWord = content.currentWordText.ifBlank { content.composingText }
        if (currentWord.isBlank()) {
            return nextWordSuggestions(subtype, content, maxCandidateCount, isPrivateSession)
        }
        val prefs by FlorisPreferenceStore
        val locales = subtype.locales()
        val multilingual = prefs.correction.multilingualSuggestions.get() && locales.size > 1
        return if (multilingual) {
            suggestMultilingual(currentWord, locales, maxCandidateCount)
        } else {
            val languageCode = LatinDictionaryStore.normalizeLanguageCode(subtype.primaryLocale.language)
            // Lazy hydrate so a process restart picks up the user's vocabulary
            // on the first suggest after typing resumes. Idempotent + async.
            dev.patrickgold.florisboard.ime.dictionary.DictionaryManager.default()
                .hydrateOverlay(subtype.primaryLocale)
            val overlay = dev.patrickgold.florisboard.ime.dictionary.UserDictionaryOverlay
                .get()
                .snapshotFor(subtype.primaryLocale)
            LatinDictionarySuggester.suggest(
                rawWord = currentWord,
                dictionary = dictionary(subtype),
                maxCandidateCount = maxCandidateCount,
                languageCode = languageCode,
                userOverlay = overlay,
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

    private suspend fun suggestMultilingual(
        rawWord: String,
        locales: List<dev.patrickgold.florisboard.lib.FlorisLocale>,
        maxCandidateCount: Int,
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
                .snapshotFor(locale)
            val cands = LatinDictionarySuggester.suggest(
                rawWord = rawWord,
                dictionary = dict,
                maxCandidateCount = maxCandidateCount,
                languageCode = langCode,
                userOverlay = overlay,
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
        trigramHits.forEachIndexed { index, word ->
            if (seen.add(word.lowercase())) {
                merged.add(word to (0.80 - 0.05 * index))
            }
        }
        bigramHits.forEachIndexed { index, word ->
            if (seen.add(word.lowercase())) {
                merged.add(word to (0.55 - 0.05 * index))
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
                merged.add(prior.word to prior.confidence)
            }
        }
        if (merged.size < maxCandidateCount) {
            val dict = dictionaryStore.dictionaryForLanguage(subtype.primaryLocale.language)
            val bootstrap = dict.topByFrequency(maxCandidateCount * 2)
            for (word in bootstrap) {
                if (merged.size >= maxCandidateCount) break
                if (seen.add(word.lowercase())) {
                    merged.add(word to (0.30 + 0.001 * dict.frequencyFor(word)))
                }
            }
        }
        return merged.map { (word, confidence) ->
            WordSuggestionCandidate(
                text = applySentenceCase(word, before),
                confidence = confidence,
                isEligibleForAutoCommit = false,
                isEligibleForUserRemoval = true,
                sourceProvider = this@LatinLanguageProvider,
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

    private fun previousWordOf(textBeforeCursor: String, depth: Int = 1): String? {
        if (textBeforeCursor.isBlank() || depth < 1) return null
        var working = textBeforeCursor
        var lastFound: String? = null
        for (n in 1..depth) {
            if (working.isBlank()) return null
            val trimmed = working.trimEnd()
            if (trimmed.isEmpty()) return null
            var end = trimmed.length
            while (end > 0 && !trimmed[end - 1].isLetter() && trimmed[end - 1] != '\'' && trimmed[end - 1] != '-') end--
            if (end == 0) return null
            var start = end
            while (start > 0) {
                val ch = trimmed[start - 1]
                if (!ch.isLetter() && ch != '\'' && ch != '-') break
                start--
            }
            if (start == end) return null
            lastFound = trimmed.substring(start, end)
            if (n == depth) return lastFound
            working = trimmed.substring(0, start)
        }
        return lastFound
    }

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
        // Forget the word from every learned source on this device. Each forget call is
        // idempotent — if the word never lived in that store, nothing happens.
        val removedFromUserDict = withContext(Dispatchers.IO) {
            dev.patrickgold.florisboard.ime.dictionary.DictionaryManager.default()
                .forgetWord(word, locale)
        }
        dev.patrickgold.florisboard.ime.dictionary.PersonalBigramStore.get(appContext).forget(word, locale)
        dev.patrickgold.florisboard.ime.dictionary.PersonalTrigramStore.get(appContext).forget(word, locale)
        return removedFromUserDict || true
    }

    override suspend fun getListOfWords(subtype: Subtype): List<String> {
        return dictionary(subtype).glideWords
    }

    override suspend fun getFrequencyForWord(subtype: Subtype, word: String): Double {
        val normalizedWord = LatinDictionarySuggester.normalizeWord(word) ?: word.lowercase()
        return dictionary(subtype).frequencyFor(normalizedWord)
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

internal class LatinDictionaryStore(
    private val readAsset: LatinDictionaryAssetReader,
    private val json: Json = Json,
) {
    private val wordDataSerializer = MapSerializer(String.serializer(), Int.serializer())
    private val dictionaryLoadGuard = Mutex()
    private val dictionaries = ConcurrentHashMap<String, LatinDictionarySnapshot>()

    suspend fun dictionaryForLanguage(language: String): LatinDictionarySnapshot {
        val languageCode = normalizeLanguageCode(language)
        dictionaries[languageCode]?.let { return it }

        return dictionaryLoadGuard.withLock {
            dictionaries[languageCode]?.let { return@withLock it }

            val dictionary = loadSpecificDictionary(languageCode)
                ?: if (languageCode == DefaultLanguageCode) {
                    LatinDictionarySnapshot.Empty
                } else {
                    dictionaries[DefaultLanguageCode]
                        ?: loadSpecificDictionary(DefaultLanguageCode)
                            ?.also { dictionaries[DefaultLanguageCode] = it }
                        ?: LatinDictionarySnapshot.Empty
                }
            dictionaries[languageCode] = dictionary
            dictionary
        }
    }

    private suspend fun loadSpecificDictionary(languageCode: String): LatinDictionarySnapshot? {
        val base = loadFirstDictionary(languageCode) ?: return null
        val merged = if (languageCode == DefaultLanguageCode) {
            base.mergeWith(loadSupplementalEnglishFrequencies())
        } else {
            base
        }
        // ROADMAP §7 Next-3.2 — overlay subtitle-derived Zipf table for this
        // language. Missing asset is tolerated: the snapshot then carries
        // `ZipfFrequencyTable.Empty` and `frequencyFor` falls back to pure
        // SCOWL frequencies (current behaviour).
        val zipfTsv = readAsset.read(zipfAssetPath(languageCode))
        val zipfTable = ZipfFrequencyTable.parse(languageCode, zipfTsv)
        return merged.withZipfTable(zipfTable)
    }

    private suspend fun loadFirstDictionary(languageCode: String): LatinDictionarySnapshot? {
        for (path in assetPathsForLanguage(languageCode)) {
            val rawData = readAsset.read(path) ?: continue
            val frequencies = decodeFrequencies(path, rawData)
            if (frequencies.isEmpty()) continue
            return LatinDictionarySnapshot.from(frequencies)
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
        private const val FldicExtension = ".fldic"
        private const val FldicWordsSection = "[words]"

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

    /**
     * Lazily-built SymSpell delete-index over the high-confidence correction vocabulary.
     * The full English dictionary intentionally includes hundreds of thousands of rare
     * words for recognition, but indexing all of them multiplies memory during typing.
     * Rare supplemental words should prevent false autocorrect, not become aggressive
     * correction candidates.
     */
    val symSpellIndex: SymSpellIndex by lazy {
        SymSpellIndex.build(correctionWords)
    }

    /**
     * Bounded distance-2 index over common correction words only. The full recognition
     * dictionary can exceed 500k English words; indexing all of it would waste IME heap
     * on rare words that should only block false autocorrect, not drive corrections.
     */
    val symSpellDistance2Index: SymSpellIndex by lazy {
        SymSpellIndex.build(distanceTwoCorrectionWords, maxDistance = 2)
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
        private const val DistanceTwoCorrectionIndexMinFrequency = 192
        private const val MaxDistanceTwoCorrectionIndexWords = 24_000
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

    fun suggest(
        rawWord: String,
        dictionary: LatinDictionarySnapshot,
        maxCandidateCount: Int,
        languageCode: String = LatinDictionaryStore.DefaultLanguageCode,
        userOverlay: Map<String, Int> = emptyMap(),
    ): List<LatinSuggestion> {
        if (maxCandidateCount <= 0 || !dictionary.isLoaded) return emptyList()
        val normalizedWord = normalizeWord(rawWord) ?: return emptyList()
        englishContractionCorrection(
            rawWord = rawWord,
            dictionary = dictionary,
            languageCode = languageCode,
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
        if (normalizedWord.length < MinCompletionLength) return emptyList()

        val completionCandidates = completions(normalizedWord, dictionary, maxCandidateCount, userOverlay)
        // ROADMAP §7 Next-3 — when the user has typed [normalizedWord] before
        // (overlay carries it under any frequency), treat it as a known word so
        // we skip running corrections against it.  Otherwise the ranker would
        // happily replace a user-typed proper noun with a SCOWL look-alike.
        val isKnownInOverlay = userOverlay.containsKey(normalizedWord)
        val correctionCandidates = if (!dictionary.contains(normalizedWord) &&
            !isKnownInOverlay &&
            normalizedWord.length >= MinCorrectionLength
        ) {
            corrections(normalizedWord, dictionary, maxCandidateCount).map { candidate ->
                if (shouldDeferCorrectionForActiveCompletion(normalizedWord, completionCandidates, candidate)) {
                    candidate.copy(isEligibleForAutoCommit = false)
                } else {
                    candidate
                }
            }
        } else {
            emptyList()
        }
        // ROADMAP §7 Next-3 — when any completion candidate is a word the
        // user has typed before (lives in the overlay), demote SCOWL
        // primary corrections to secondary. The user explicitly taught the
        // keyboard this word; we don't want a SCOWL edit-distance neighbour
        // (e.g. "food" for "foob…") jumping ahead of "foobar" just because
        // the SCOWL word is more common in the general corpus.
        val hasOverlayCompletion = userOverlay.isNotEmpty() &&
            completionCandidates.any { userOverlay.containsKey(it.text.lowercase()) }
        val primaryCorrections = if (hasOverlayCompletion) {
            emptyList()
        } else {
            correctionCandidates.filter { candidate ->
                shouldPromoteCorrectionBeforeCompletions(normalizedWord, completionCandidates, candidate)
            }
        }
        val secondaryCorrections = if (hasOverlayCompletion) {
            correctionCandidates
        } else {
            correctionCandidates.filterNot { candidate ->
                shouldPromoteCorrectionBeforeCompletions(normalizedWord, completionCandidates, candidate)
            }
        }

        val seen = mutableSetOf<String>()
        return buildList {
            primaryCorrections.forEach { candidate ->
                if (seen.add(candidate.text.lowercase())) add(candidate.withTypedCase(rawWord))
            }
            completionCandidates.forEach { candidate ->
                if (seen.add(candidate.text.lowercase())) add(candidate.withTypedCase(rawWord))
            }
            secondaryCorrections.forEach { candidate ->
                if (seen.add(candidate.text.lowercase())) add(candidate.withTypedCase(rawWord))
            }
        }.take(maxCandidateCount)
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
    ): Boolean {
        if (completionCandidates.isEmpty()) return false
        if (normalizedWord.length < MinCorrectionOverCompletionLength) return true
        return correctionCandidate.text.length < normalizedWord.length
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
    ): List<LatinSuggestion> {
        if (maxCandidateCount <= 0 || word.length < MinCorrectionLength || !dictionary.isLoaded) return emptyList()
        val oneEditCandidates = knownEdits1(word, dictionary)
        val candidateDistances = if (oneEditCandidates.isNotEmpty()) {
            oneEditCandidates.map { it to 1 }
        } else if (word.length <= MaxTwoEditWordLength) {
            knownEdits2(word, dictionary)
        } else {
            emptyList()
        }

        return candidateDistances
            .asSequence()
            .filter { (candidate, _) -> candidate != word }
            .distinctBy { (candidate, _) -> candidate }
            .sortedWith(
                compareBy<Pair<String, Int>> { (_, distance) -> distance }
                    .thenByDescending { (candidate, _) -> dictionary.frequencyFor(candidate) }
                    .thenBy { (candidate, _) -> candidate.length }
                    .thenBy { (candidate, _) -> candidate }
            )
            .take(maxCandidateCount)
            .mapIndexed { index, (candidate, distance) ->
                val frequency = dictionary.frequencyFor(candidate)
                val autoCommitThreshold = when (distance) {
                    1 -> AutoCommitMinFrequency
                    2 -> AutoCommitMinFrequencyDistance2
                    else -> Double.POSITIVE_INFINITY
                }
                LatinSuggestion(
                    text = candidate,
                    confidence = correctionConfidence(frequency, distance),
                    isEligibleForAutoCommit = index == 0 &&
                        distance in 1..2 &&
                        word.length >= MinCorrectionLength &&
                        frequency >= autoCommitThreshold,
                )
            }
            .toList()
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

    private fun knownEdits1(word: String, dictionary: LatinDictionarySnapshot): Set<String> {
        // SymSpell-based fast path: the precomputed delete-index returns every dict word
        // within edit-distance ≤ 1 in O(L²) instead of generating L · |alphabet| ≈ L · 54
        // candidate strings per call.
        return dictionary.symSpellIndex.candidatesAtDistance1(word)
            .filterTo(HashSet()) { it != word }
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
