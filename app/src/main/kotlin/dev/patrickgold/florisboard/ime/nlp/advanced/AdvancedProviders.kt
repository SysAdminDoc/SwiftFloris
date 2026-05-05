/*
 * Copyright (C) 2025 SwiftFloris Contributors
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

package dev.patrickgold.florisboard.ime.nlp.advanced

import android.content.Context
import android.util.LruCache
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.nlp.NlpProvider
import dev.patrickgold.florisboard.ime.nlp.SpellingProvider
import dev.patrickgold.florisboard.ime.nlp.SpellingResult
import dev.patrickgold.florisboard.ime.nlp.SuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.SuggestionProvider
import dev.patrickgold.florisboard.ime.nlp.WordSuggestionCandidate
import java.io.BufferedReader
import java.io.InputStreamReader

internal object AdvancedSpellingEngine {
    fun generateCorrections(word: String, dictionary: Set<String>, maxCount: Int): List<String> {
        if (maxCount <= 0) {
            return emptyList()
        }

        val candidates = mutableListOf<Pair<String, Int>>()
        for (dictWord in dictionary) {
            if (dictWord.length >= word.length - 2 && dictWord.length <= word.length + 2) {
                val distance = levenshteinDistance(word, dictWord)
                if (distance in 1..2) {
                    candidates.add(dictWord to distance)
                }
            }
        }

        return candidates
            .sortedWith(
                compareBy<Pair<String, Int>> { it.second }
                    .thenBy { it.first.length }
                    .thenBy { it.first }
            )
            .take(maxCount)
            .map { it.first }
    }

    fun levenshteinDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        val matrix = Array(a.length + 1) { IntArray(b.length + 1) }

        for (i in 0..a.length) matrix[i][0] = i
        for (j in 0..b.length) matrix[0][j] = j

        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                matrix[i][j] = minOf(
                    matrix[i - 1][j] + 1,
                    matrix[i][j - 1] + 1,
                    matrix[i - 1][j - 1] + cost,
                )
            }
        }

        return matrix[a.length][b.length]
    }
}

internal data class AdvancedPredictionSuggestion(
    val text: String,
    val confidence: Double,
    val isEligibleForAutoCommit: Boolean,
)

internal object AdvancedPredictionEngine {
    fun suggest(
        textBeforeSelection: String,
        dictionary: Set<String>,
        bigramPredictions: Map<String, List<Pair<String, Double>>>,
        maxCandidateCount: Int,
        frequencyForWord: (String) -> Double = { 0.5 },
    ): List<AdvancedPredictionSuggestion> {
        if (maxCandidateCount <= 0 || textBeforeSelection.isBlank()) {
            return emptyList()
        }

        val words = textBeforeSelection.trimEnd().split(Regex("\\s+"))
        val currentWord = words.lastOrNull()?.lowercase() ?: return emptyList()
        if (currentWord.length < 2) {
            return emptyList()
        }

        val contextWord = if (words.size >= 2) words[words.size - 2].lowercase() else null
        val suggestions = mutableListOf<AdvancedPredictionSuggestion>()
        val seen = mutableSetOf<String>()

        dictionary.asSequence()
            .filter { it.startsWith(currentWord) && it.length > currentWord.length }
            .sortedWith(
                compareByDescending<String> { frequencyForWord(it) }
                    .thenBy { it.length }
                    .thenBy { it }
            )
            .take(maxCandidateCount)
            .forEach { word ->
                seen.add(word)
                val frequency = frequencyForWord(word)
                suggestions.add(
                    AdvancedPredictionSuggestion(
                        text = word,
                        confidence = frequency,
                        isEligibleForAutoCommit = frequency >= 0.8,
                    )
                )
            }

        if (contextWord != null && suggestions.size < maxCandidateCount) {
            for ((word, score) in bigramPredictions[contextWord].orEmpty()) {
                if (seen.add(word)) {
                    suggestions.add(
                        AdvancedPredictionSuggestion(
                            text = word,
                            confidence = score,
                            isEligibleForAutoCommit = score >= 0.8,
                        )
                    )
                    if (suggestions.size >= maxCandidateCount) {
                        break
                    }
                }
            }
        }

        return suggestions
    }
}

/**
 * Advanced spell checker and autocorrect provider using dictionary-based spell checking
 * and edit distance algorithms for robust error detection and correction.
 */
class AdvancedSpellingProvider(private val context: Context) : SpellingProvider {
    override val providerId = "org.swiftfloris.nlp.providers.advanced_spelling"

    private val dictionaryCache = mutableMapOf<String, Set<String>>()
    private val frequencyCache = LruCache<String, Double>(5000)

    override suspend fun create() {
        // Pre-load English dictionary
        loadDictionary("en")
    }

    override suspend fun preload(subtype: Subtype) {
        val lang = subtype.primaryLocale.language
        if (!dictionaryCache.containsKey(lang)) {
            loadDictionary(lang)
        }
    }

    override suspend fun destroy() {
        dictionaryCache.clear()
        frequencyCache.evictAll()
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
        val lang = subtype.primaryLocale.language
        val dictionary = dictionaryCache[lang] ?: return SpellingResult.unspecified()

        val normalizedWord = word.lowercase()

        // If word is in dictionary, it's correct
        if (dictionary.contains(normalizedWord)) {
            return SpellingResult.validWord()
        }

        // Generate correction suggestions using edit distance
        val suggestions = AdvancedSpellingEngine.generateCorrections(normalizedWord, dictionary, maxSuggestionCount)

        return if (suggestions.isNotEmpty()) {
            SpellingResult.typo(suggestions.toTypedArray(), isHighConfidenceResult = true)
        } else {
            SpellingResult.typo(arrayOf())
        }
    }

    private fun loadDictionary(language: String) {
        try {
            val assetName = "dictionaries/${language}.txt"
            val words = mutableSetOf<String>()

            context.assets.open(assetName).use { stream ->
                BufferedReader(InputStreamReader(stream)).use { reader ->
                    reader.forEachLine { line ->
                        val word = line.trim().lowercase()
                        if (word.isNotEmpty()) {
                            words.add(word)
                        }
                    }
                }
            }

            dictionaryCache[language] = words
            android.util.Log.d("AdvancedSpelling", "Loaded dictionary for $language: ${words.size} words")
        } catch (e: Exception) {
            android.util.Log.w("AdvancedSpelling", "Failed to load dictionary for $language", e)
            dictionaryCache[language] = emptySet()
        }
    }
}

/**
 * Advanced next-word prediction provider using n-gram language models and frequency analysis.
 * Provides context-aware word suggestions similar to SwiftKey.
 */
class AdvancedPredictionProvider(private val context: Context) : SuggestionProvider {
    override val providerId = "org.swiftfloris.nlp.providers.advanced_prediction"

    private val bigramCache = mutableMapOf<String, Map<String, List<Pair<String, Double>>>>()
    private val unigramFrequency = LruCache<String, Double>(10000)
    private val dictionaryCache = mutableMapOf<String, Set<String>>()
    private val contextCache = LruCache<String, List<WordSuggestionCandidate>>(100)

    override suspend fun create() {
        // Pre-load English models
        loadLanguageModel("en")
    }

    override suspend fun preload(subtype: Subtype) {
        val lang = subtype.primaryLocale.language
        if (!bigramCache.containsKey(lang)) {
            loadLanguageModel(lang)
        }
    }

    override suspend fun destroy() {
        bigramCache.clear()
        unigramFrequency.evictAll()
        dictionaryCache.clear()
        contextCache.evictAll()
    }

    override suspend fun suggest(
        subtype: Subtype,
        content: EditorContent,
        maxCandidateCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): List<SuggestionCandidate> {
        val lang = subtype.primaryLocale.language
        val textBeforeSelection = content.textBeforeSelection

        val cacheKey = "$lang:$textBeforeSelection:$maxCandidateCount"
        contextCache[cacheKey]?.let { return it }

        val dictionary = dictionaryCache[lang] ?: emptySet()
        val bigrams = bigramCache[lang] ?: emptyMap()
        val suggestions = AdvancedPredictionEngine.suggest(
            textBeforeSelection = textBeforeSelection,
            dictionary = dictionary,
            bigramPredictions = bigrams,
            maxCandidateCount = maxCandidateCount,
            frequencyForWord = { word -> unigramFrequency[word] ?: 0.5 },
        ).map { prediction ->
            WordSuggestionCandidate(
                text = prediction.text,
                confidence = prediction.confidence,
                isEligibleForAutoCommit = prediction.isEligibleForAutoCommit,
                sourceProvider = this,
            )
        }

        contextCache.put(cacheKey, suggestions)
        return suggestions
    }

    override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
        // Could be used to learn user preferences
    }

    override suspend fun notifySuggestionReverted(subtype: Subtype, candidate: SuggestionCandidate) {
        // Could be used to adjust model
    }

    override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean {
        return true
    }

    override suspend fun getListOfWords(subtype: Subtype): List<String> {
        return dictionaryCache[subtype.primaryLocale.language]?.toList() ?: emptyList()
    }

    override suspend fun getFrequencyForWord(subtype: Subtype, word: String): Double {
        return unigramFrequency[word.lowercase()] ?: 0.0
    }

    private fun loadLanguageModel(language: String) {
        try {
            // Load unigram frequencies
            val unigramAsset = "lm/${language}_unigrams.txt"
            context.assets.open(unigramAsset).use { stream ->
                BufferedReader(InputStreamReader(stream)).use { reader ->
                    reader.forEachLine { line ->
                        val parts = line.split("\t")
                        if (parts.size == 2) {
                            val word = parts[0].lowercase()
                            val freq = parts[1].toDoubleOrNull() ?: 0.0
                            unigramFrequency.put(word, freq)
                        }
                    }
                }
            }

            // Load bigram predictions
            val bigramAsset = "lm/${language}_bigrams.txt"
            val bigrams = mutableMapOf<String, MutableList<Pair<String, Double>>>()
            context.assets.open(bigramAsset).use { stream ->
                BufferedReader(InputStreamReader(stream)).use { reader ->
                    reader.forEachLine { line ->
                        val parts = line.split("\t")
                        if (parts.size == 3) {
                            val word1 = parts[0].lowercase()
                            val word2 = parts[1].lowercase()
                            val score = parts[2].toDoubleOrNull() ?: 0.0
                            bigrams.getOrPut(word1) { mutableListOf() }.add(word2 to score)
                        }
                    }
                }
            }
            bigramCache[language] = bigrams.mapValues { (_, v) ->
                v.sortedByDescending { it.second }
            }

            // Load dictionary for word completion
            val dictAsset = "dictionaries/${language}.txt"
            val words = mutableSetOf<String>()
            context.assets.open(dictAsset).use { stream ->
                BufferedReader(InputStreamReader(stream)).use { reader ->
                    reader.forEachLine { line ->
                        val word = line.trim().lowercase()
                        if (word.isNotEmpty()) words.add(word)
                    }
                }
            }
            dictionaryCache[language] = words

            android.util.Log.d("AdvancedPrediction", "Loaded language model for $language")
        } catch (e: Exception) {
            android.util.Log.w("AdvancedPrediction", "Failed to load language model for $language", e)
        }
    }

    override val forcesSuggestionOn = true
}
