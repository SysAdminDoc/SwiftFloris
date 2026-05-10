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
        val languageCode = LatinDictionaryStore.normalizeLanguageCode(subtype.primaryLocale.language)
        if (currentWord.isBlank()) {
            return nextWordSuggestions(subtype, content, maxCandidateCount, isPrivateSession)
        }
        return LatinDictionarySuggester.suggest(
            rawWord = currentWord,
            dictionary = dictionary(subtype),
            maxCandidateCount = maxCandidateCount,
            languageCode = languageCode,
        ).map { candidate ->
            WordSuggestionCandidate(
                text = candidate.text,
                confidence = candidate.confidence,
                isEligibleForAutoCommit = candidate.isEligibleForAutoCommit,
                sourceProvider = this@LatinLanguageProvider,
            )
        }
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
        val prevWord = previousWordOf(before) ?: return emptyList()
        val store = PersonalBigramStore.get(appContext)
        val predicted = store.predict(prevWord, subtype.primaryLocale, maxCandidateCount)
        if (predicted.isEmpty()) return emptyList()
        return predicted.mapIndexed { index, word ->
            WordSuggestionCandidate(
                text = word,
                confidence = 0.5 - 0.05 * index,
                isEligibleForAutoCommit = false,
                sourceProvider = this@LatinLanguageProvider,
            )
        }
    }

    private fun previousWordOf(textBeforeCursor: String): String? {
        if (textBeforeCursor.isBlank()) return null
        val trimmed = textBeforeCursor.trimEnd()
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
        return trimmed.substring(start, end)
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
        return false
    }

    override suspend fun getListOfWords(subtype: Subtype): List<String> {
        return dictionary(subtype).sortedWords
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
        for (path in assetPathsForLanguage(languageCode)) {
            val rawData = readAsset.read(path) ?: continue
            val frequencies = decodeFrequencies(path, rawData)
            if (frequencies.isEmpty()) continue
            return LatinDictionarySnapshot(
                frequencies = frequencies,
                sortedWords = frequencies.keys.sorted(),
            )
        }
        return null
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
    }
}

internal data class LatinDictionarySnapshot(
    val frequencies: Map<String, Int>,
    val sortedWords: List<String>,
) {
    val isLoaded: Boolean get() = frequencies.isNotEmpty()

    fun contains(word: String): Boolean = frequencies.containsKey(word)

    fun frequencyFor(word: String): Double = frequencies.getOrDefault(word, 0).coerceIn(0, 255) / 255.0

    companion object {
        val Empty = LatinDictionarySnapshot(emptyMap(), emptyList())
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
    private const val MaxTwoEditWordLength = 8
    // Frequency threshold (0.0–1.0, normalized from a 0–255 dictionary count).
    // SwiftKey-style behavior: only auto-replace the user's typed word when the candidate is
    // genuinely common. Lower thresholds aggressively swap unusual words and proper nouns.
    // 0.78 ≈ frequency 198/255 — covers high-confidence typo fixes like "teh" → "the" while
    // sparing rarer dictionary words that share an edit-distance-1 neighborhood.
    private const val AutoCommitMinFrequency = 0.78
    private val Alphabet = ('a'..'z').toList()

    fun suggest(
        rawWord: String,
        dictionary: LatinDictionarySnapshot,
        maxCandidateCount: Int,
        languageCode: String = LatinDictionaryStore.DefaultLanguageCode,
    ): List<LatinSuggestion> {
        if (maxCandidateCount <= 0 || !dictionary.isLoaded) return emptyList()
        val normalizedWord = normalizeWord(rawWord) ?: return emptyList()
        englishContractionCorrection(
            rawWord = rawWord,
            dictionary = dictionary,
            languageCode = languageCode,
        )?.let { return listOf(it) }
        if (normalizedWord.length < MinCompletionLength) return emptyList()

        val completionCandidates = completions(normalizedWord, dictionary, maxCandidateCount)
        val correctionCandidates = if (!dictionary.contains(normalizedWord) && normalizedWord.length >= MinCorrectionLength) {
            corrections(normalizedWord, dictionary, maxCandidateCount).map { candidate ->
                if (completionCandidates.isNotEmpty()) {
                    candidate.copy(isEligibleForAutoCommit = false)
                } else {
                    candidate
                }
            }
        } else {
            emptyList()
        }

        val seen = mutableSetOf<String>()
        return buildList {
            completionCandidates.forEach { candidate ->
                if (seen.add(candidate.text.lowercase())) add(candidate.withTypedCase(rawWord))
            }
            correctionCandidates.forEach { candidate ->
                if (seen.add(candidate.text.lowercase())) add(candidate.withTypedCase(rawWord))
            }
        }.take(maxCandidateCount)
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
            knownEdits2(word, dictionary).map { it to 2 }
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
                LatinSuggestion(
                    text = candidate,
                    confidence = correctionConfidence(frequency, distance),
                    isEligibleForAutoCommit = index == 0 &&
                        distance == 1 &&
                        word.length >= MinCorrectionLength &&
                        frequency >= AutoCommitMinFrequency,
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
    ): List<LatinSuggestion> {
        return wordsWithPrefix(prefix, dictionary.sortedWords)
            .asSequence()
            .filter { it.length > prefix.length }
            .sortedWith(
                compareByDescending<String> { dictionary.frequencyFor(it) }
                    .thenBy { it.length }
                    .thenBy { it }
            )
            .take(maxCandidateCount)
            .map { word ->
                LatinSuggestion(
                    text = word,
                    confidence = (0.2 + dictionary.frequencyFor(word) * 0.6).coerceIn(0.0, 1.0),
                    isEligibleForAutoCommit = false,
                )
            }
            .toList()
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
        return edits1(word).filterTo(mutableSetOf()) { dictionary.contains(it) }
    }

    private fun knownEdits2(word: String, dictionary: LatinDictionarySnapshot): Set<String> {
        val known = mutableSetOf<String>()
        for (edit in edits1(word)) {
            for (candidate in edits1(edit)) {
                if (dictionary.contains(candidate)) {
                    known.add(candidate)
                }
            }
        }
        return known
    }

    private fun edits1(word: String): Set<String> {
        val edits = mutableSetOf<String>()
        for (i in 0..word.length) {
            val left = word.substring(0, i)
            val right = word.substring(i)
            if (right.isNotEmpty()) {
                edits.add(left + right.drop(1))
            }
            if (right.length > 1) {
                edits.add(left + right[1] + right[0] + right.drop(2))
            }
            if (right.isNotEmpty()) {
                for (char in Alphabet) {
                    edits.add(left + char + right.drop(1))
                }
            }
            for (char in Alphabet) {
                edits.add(left + char + right)
            }
        }
        return edits
    }

    private fun correctionConfidence(frequency: Double, distance: Int): Double {
        val editDistanceBonus = if (distance == 1) 0.1 else 0.0
        return (0.35 + frequency * 0.55 + editDistanceBonus).coerceIn(0.0, 1.0)
    }

    private fun LatinSuggestion.withTypedCase(rawWord: String): LatinSuggestion {
        return copy(text = applyTypedCase(text, rawWord))
    }

    private fun applyTypedCase(candidate: String, rawWord: String): String {
        val letters = rawWord.filter { it.isLetter() }
        return when {
            letters.length > 1 && letters.all { it.isUpperCase() } -> candidate.uppercase()
            letters.firstOrNull()?.isUpperCase() == true -> candidate.replaceFirstChar { it.titlecase() }
            else -> candidate
        }
    }
}
