/*
 * Copyright (C) 2026 SwiftFloris Contributors
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

import dev.patrickgold.florisboard.ime.nlp.latin.ColdStartNextWordPriors
import dev.patrickgold.florisboard.lib.FlorisLocale

/**
 * Where [CandidateSignalAssembler] reads dictionary frequencies, personal word pairs, the bundled context model
 * and past corrections. NlpManager backs it with the live dictionaries and stores; tests back it with fixed data.
 */
internal interface CandidateSignalSources {
    /** How common [word] is in [locale]'s dictionary, 0.0 when it isn't there. [word] isn't normalized yet. */
    suspend fun frequency(locale: FlorisLocale, word: String): Double

    fun isKnownUserDictionaryWord(word: String, locale: FlorisLocale): Boolean

    suspend fun bigramScore(previousWord: String, word: String, locale: FlorisLocale): Double

    suspend fun trigramScore(secondPreviousWord: String, previousWord: String, word: String, locale: FlorisLocale): Double

    /**
     * How well [word] fits after [secondPreviousWord] [previousWord] by [locale]'s bundled context model, 0.0 to
     * 1.0, and 0.0 when the language has no model or the model doesn't know the words. A null
     * [secondPreviousWord] means [previousWord] starts the sentence.
     */
    suspend fun contextModelProbability(
        secondPreviousWord: String?,
        previousWord: String,
        word: String,
        locale: FlorisLocale,
    ): Double

    fun outcomeSignal(originalText: CharSequence, correctedText: CharSequence): CorrectionOutcomeSignal

    fun rejectedPairPenalty(currentWord: CharSequence, candidateText: CharSequence, currentWordStart: Int?): Double
}

/**
 * Builds the per-candidate signals [SwiftKeyCandidateRanker] ranks with and space auto-commits with: dictionary
 * frequency, context probability, language confidence, accepted and rejected corrections, and the candidate's
 * language. Keyed by [candidateSignalKeyOf] of each word candidate's text.
 */
internal object CandidateSignalAssembler {
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

    suspend fun assemble(
        primaryLocale: FlorisLocale,
        locales: List<FlorisLocale>,
        textBeforeSelection: String,
        currentWord: String,
        currentWordStart: Int?,
        candidates: List<SuggestionCandidate>,
        sources: CandidateSignalSources,
    ): Map<String, SwiftKeyCandidateSignals> {
        if (candidates.isEmpty()) return emptyMap()
        val localeCount = locales.size
        val typedWordKey = candidateSignalKeyOf(currentWord)
        val typedWordKnownByUserDictionary = typedWordKey?.let { key ->
            locales.any { locale -> sources.isKnownUserDictionaryWord(key, locale) }
        } ?: false
        val previousWords = TypingContextExtractor.previousWordsBeforeCurrentWord(
            textBeforeSelection = textBeforeSelection,
            currentWord = currentWord,
        )
        val languageContextWords = TypingContextExtractor.previousWordListBeforeCurrentWord(
            textBeforeSelection = textBeforeSelection,
            currentWord = currentWord,
            maxDepth = MaxLanguageContextWords,
        )
        val contextPrefix = TypingContextExtractor.sentenceLocalPrefixBeforeCurrentWord(
            textBeforeSelection = textBeforeSelection,
            currentWord = currentWord,
        )
        val contextFrequencyByLocale: Map<FlorisLocale, Map<String, Double>> = buildMap {
            for (activeLocale in locales) {
                put(activeLocale, buildMap {
                    for (word in languageContextWords.distinct()) {
                        put(word, sources.frequency(activeLocale, word))
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
            .mapNotNull { candidate -> candidateSignalKeyOf(candidate.text.toString()) }
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
                            sources.frequency(contextLocale, candidateKey) > 0.0
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
                val key = candidateSignalKeyOf(candidateText) ?: continue
                val localeEvidence = locales.map { locale ->
                    TokenLocaleEvidence(
                        typedFrequency = typedWordKey?.let { word -> sources.frequency(locale, word) } ?: 0.0,
                        candidateFrequency = sources.frequency(locale, key),
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
                    sources.bigramScore(prev1, candidateText, primaryLocale)
                } ?: 0.0
                val trigramProbability = if (previousWords.prev2 != null && previousWords.prev1 != null) {
                    sources.trigramScore(previousWords.prev2, previousWords.prev1, candidateText, primaryLocale)
                } else {
                    0.0
                }
                val coldStartProbability = ColdStartNextWordPriors.score(
                    textBeforeCursor = contextPrefix,
                    languageCode = primaryLocale.language,
                    candidateWord = candidateText,
                    maxCandidateCount = maxOf(MaxColdStartContextCandidates, candidates.size),
                )
                val modelProbability = previousWords.prev1?.let { prev1 ->
                    sources.contextModelProbability(previousWords.prev2, prev1, candidateText, primaryLocale)
                } ?: 0.0
                val contextProbability = maxOf(
                    bigramProbability * BigramContextWeight,
                    trigramProbability,
                    coldStartProbability,
                    modelProbability,
                ).coerceIn(0.0, 1.0)
                val languageConfidence = if (localeCount <= 1) 1.0 else languageSignal.languageConfidence
                val outcomeSignal = sources.outcomeSignal(
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
                            sources.rejectedPairPenalty(
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
}
