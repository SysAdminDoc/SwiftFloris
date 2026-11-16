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

import dev.patrickgold.florisboard.lib.FlorisLocale
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking

class CandidateSignalAssemblerTest : FunSpec({
    val english = FlorisLocale.from("en")
    val german = FlorisLocale.from("de")

    fun assemble(
        sources: CandidateSignalSources,
        locales: List<FlorisLocale> = listOf(english),
        primaryLocale: FlorisLocale = locales.first(),
        textBeforeSelection: String = "zzq qzz teh",
        currentWord: String = "teh",
        candidates: List<SuggestionCandidate> = listOf(WordSuggestionCandidate(text = "the")),
    ) = runBlocking {
        CandidateSignalAssembler.assemble(
            primaryLocale = primaryLocale,
            locales = locales,
            textBeforeSelection = textBeforeSelection,
            currentWord = currentWord,
            currentWordStart = textBeforeSelection.length - currentWord.length,
            candidates = candidates,
            sources = sources,
        )
    }

    test("context probability is the larger of the weighted word pair and the word triple") {
        val pairWins = assemble(FakeSources(bigram = 0.8, trigram = 0.3)).getValue("the")
        pairWins.contextProbability shouldBe (0.8 * CandidateSignalAssembler.BigramContextWeight plusOrMinus 1e-9)

        val tripleWins = assemble(FakeSources(bigram = 0.4, trigram = 0.5)).getValue("the")
        tripleWins.contextProbability shouldBe (0.5 plusOrMinus 1e-9)
    }

    test("word pairs and triples are looked up with the two words before the typed word") {
        val sources = FakeSources(bigram = 0.1, trigram = 0.1)
        assemble(sources)
        sources.bigramCalls shouldBe listOf("qzz the en")
        sources.trigramCalls shouldBe listOf("zzq qzz the en")
    }

    test("word pairs and triples come from the subtype's primary language, not the first active one") {
        val sources = FakeSources(bigram = 0.1, trigram = 0.1)
        assemble(sources, locales = listOf(english, german), primaryLocale = german)
        sources.bigramCalls shouldBe listOf("qzz the de")
        sources.trigramCalls shouldBe listOf("zzq qzz the de")
    }

    test("the context model counts toward context probability when it beats the personal stores") {
        assemble(FakeSources(bigram = 0.4, model = 0.9)).getValue("the").contextProbability shouldBe 0.9
        val pairWins = assemble(FakeSources(bigram = 0.8, model = 0.1)).getValue("the")
        pairWins.contextProbability shouldBe (0.8 * CandidateSignalAssembler.BigramContextWeight plusOrMinus 1e-9)
    }

    test("the context model is asked with the two words before the typed word in the primary language") {
        val sources = FakeSources(model = 0.5)
        assemble(sources, locales = listOf(english, german), primaryLocale = german)
        sources.modelCalls shouldBe listOf("zzq qzz the de")
    }

    test("the context model hears where the sentence starts and isn't asked for its first word") {
        val secondWord = FakeSources(model = 0.5)
        assemble(secondWord, textBeforeSelection = "Ok. Qzz teh")
        secondWord.modelCalls shouldBe listOf("<start> Qzz the en")

        val firstWord = FakeSources(model = 0.5)
        assemble(
            firstWord,
            textBeforeSelection = "Ok. Qzc",
            currentWord = "Qzc",
            candidates = listOf(WordSuggestionCandidate(text = "Qzx")),
        ).getValue("qzx").contextProbability shouldBe 0.0
        firstWord.modelCalls shouldBe emptyList()
    }

    test("the rejection penalty is the larger of a rejected auto-commit and past undos") {
        assemble(FakeSources(rejectedPair = 0.6, rejectedOutcome = 0.2)).getValue("the").rejectionPenalty shouldBe 0.6
        assemble(FakeSources(rejectedPair = 0.1, rejectedOutcome = 0.7)).getValue("the").rejectionPenalty shouldBe 0.7
    }

    test("accepted corrections carry over") {
        assemble(FakeSources(acceptedOutcome = 0.9)).getValue("the").acceptedCorrectionConfidence shouldBe 0.9
    }

    test("one active language gives full language confidence") {
        val signals = assemble(FakeSources(frequencies = mapOf("en" to mapOf("the" to 0.9)))).getValue("the")
        signals.languageConfidence shouldBe 1.0
        signals.candidateLanguage shouldBe "en"
    }

    test("the candidate's language is the one whose dictionary knows it best, and none on a tie") {
        val englishWord = assemble(
            FakeSources(frequencies = mapOf("en" to mapOf("the" to 0.9), "de" to mapOf("the" to 0.1))),
            locales = listOf(english, german),
        ).getValue("the")
        englishWord.candidateLanguage shouldBe "en"

        val tie = assemble(
            FakeSources(frequencies = mapOf("en" to mapOf("the" to 0.5), "de" to mapOf("the" to 0.5))),
            locales = listOf(english, german),
        ).getValue("the")
        tie.candidateLanguage shouldBe null
    }

    test("only word candidates get signals, keyed by their normalized text") {
        val signals = assemble(
            FakeSources(),
            candidates = listOf(
                WordSuggestionCandidate(text = "The"),
                GhostTextSuggestionCandidate(text = "the rest of it", confidence = 0.9, tokenCount = 4),
            ),
        )
        signals.keys shouldBe setOf("the")
        assemble(FakeSources(), candidates = emptyList()) shouldBe emptyMap()
    }
})

private class FakeSources(
    private val frequencies: Map<String, Map<String, Double>> = emptyMap(),
    private val bigram: Double = 0.0,
    private val trigram: Double = 0.0,
    private val model: Double = 0.0,
    private val rejectedPair: Double = 0.0,
    private val acceptedOutcome: Double = 0.0,
    private val rejectedOutcome: Double = 0.0,
) : CandidateSignalSources {
    val bigramCalls = mutableListOf<String>()
    val trigramCalls = mutableListOf<String>()
    val modelCalls = mutableListOf<String>()

    override suspend fun frequency(locale: FlorisLocale, word: String): Double {
        val key = candidateSignalKeyOf(word) ?: return 0.0
        return frequencies[locale.language]?.get(key) ?: 0.0
    }

    override fun isKnownUserDictionaryWord(word: String, locale: FlorisLocale) = false

    override suspend fun bigramScore(previousWord: String, word: String, locale: FlorisLocale): Double {
        bigramCalls += "$previousWord $word ${locale.language}"
        return bigram
    }

    override suspend fun trigramScore(
        secondPreviousWord: String,
        previousWord: String,
        word: String,
        locale: FlorisLocale,
    ): Double {
        trigramCalls += "$secondPreviousWord $previousWord $word ${locale.language}"
        return trigram
    }

    override suspend fun contextModelProbability(
        secondPreviousWord: String?,
        previousWord: String,
        word: String,
        locale: FlorisLocale,
    ): Double {
        modelCalls += "${secondPreviousWord ?: "<start>"} $previousWord $word ${locale.language}"
        return model
    }

    override fun outcomeSignal(originalText: CharSequence, correctedText: CharSequence) =
        CorrectionOutcomeSignal(acceptedConfidence = acceptedOutcome, rejectedConfidence = rejectedOutcome)

    override fun rejectedPairPenalty(currentWord: CharSequence, candidateText: CharSequence, currentWordStart: Int?) =
        rejectedPair
}
