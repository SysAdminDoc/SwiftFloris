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

package dev.patrickgold.florisboard.ime.nlp.accuracy

import dev.patrickgold.florisboard.ime.nlp.AutoCommitSuppression
import dev.patrickgold.florisboard.ime.nlp.AutoCorrectCommitMode
import dev.patrickgold.florisboard.ime.nlp.AutoCorrectConfidencePolicy
import dev.patrickgold.florisboard.ime.nlp.CandidateAutoCommitPolicy
import dev.patrickgold.florisboard.ime.nlp.CandidateSignalAssembler
import dev.patrickgold.florisboard.ime.nlp.CandidateSignalSources
import dev.patrickgold.florisboard.ime.nlp.CorrectionOutcomeSignal
import dev.patrickgold.florisboard.ime.nlp.ImmediateAutocorrect
import dev.patrickgold.florisboard.ime.nlp.OffensiveWordPolicy
import dev.patrickgold.florisboard.ime.nlp.SwiftKeyCandidateRanker
import dev.patrickgold.florisboard.ime.nlp.SwiftKeyDecoderContext
import dev.patrickgold.florisboard.ime.nlp.TouchDecoderCandidate
import dev.patrickgold.florisboard.ime.nlp.TouchDecoderEvidenceBuffer
import dev.patrickgold.florisboard.ime.nlp.TouchDecoderSample
import dev.patrickgold.florisboard.ime.nlp.WordSuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.candidateSignalKeyOf
import dev.patrickgold.florisboard.ime.nlp.latin.LatinDictionaryAssetReader
import dev.patrickgold.florisboard.ime.nlp.latin.LatinDictionarySnapshot
import dev.patrickgold.florisboard.ime.nlp.latin.LatinDictionaryStore
import dev.patrickgold.florisboard.ime.nlp.latin.LatinDictionarySuggester
import dev.patrickgold.florisboard.ime.text.key.KeyVariation
import dev.patrickgold.florisboard.ime.text.keyboard.NearbyKeyConfidence
import dev.patrickgold.florisboard.lib.FlorisLocale
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Random
import kotlin.math.floor

/**
 * Measures tap-typing autocorrect on the shipped English dictionary, so a change to the suggester, the ranker or
 * the auto-commit policy can be shown to help or hurt (ROADMAP P1 "Measure tap-typing autocorrect accuracy").
 *
 * Each sentence of a fixed Tatoeba subset is typed one word at a time. Every letter lands at a point drawn from
 * a Gaussian around its key's center on a QWERTY grid, the key under that point is what gets typed, and the
 * nearby keys and their confidences come from the same [NearbyKeyConfidence] the keyboard uses, picked from
 * every key on the grid (number row, shift, backspace and the bottom row too) before the non-letters drop out.
 * The first word of a sentence is typed with shift on, as auto-capitalization does by default. The word then
 * goes through the path a fresh install takes: [LatinDictionarySuggester], the default offensive-word filter,
 * [CandidateSignalAssembler] with empty personal stores, [SwiftKeyCandidateRanker] with the touch evidence, and
 * the default settings of [CandidateAutoCommitPolicy.selectSpacebarCandidate] when space ends the word, or of
 * [CandidateAutoCommitPolicy.selectAutoCommitCandidate] when a period, comma, question mark or exclamation mark
 * comes straight after it. Whatever gets committed becomes the context for the next word.
 *
 * Seeds are fixed, so every run gives the same numbers. When a change moves them on purpose, update [Baseline]
 * in the same commit. The report is printed and written to build/reports/autocorrect-accuracy.txt.
 */
class AutocorrectAccuracyHarnessTest : FunSpec({
    val replay by lazy {
        AutocorrectReplay(
            runBlocking {
                LatinDictionaryStore(readAsset = LatinDictionaryAssetReader { path -> bundledAsset(path)?.readText() })
                    .dictionaryForLanguage("en")
            },
        )
    }
    val sentences by lazy { AccuracyCorpus.load(CorpusPath) }

    test("simulated taps over the sentence corpus give the recorded baseline") {
        val results = TapNoiseLevels.associate { noise -> noise.name to replay.typeCorpus(sentences, noise) }
        val misspellings = MustFix.keys.map { typed ->
            typed to replay.space(textBefore = "", typed = typed, samples = centerTaps(typed)).committed
        }
        val report = buildString {
            appendLine("Autocorrect accuracy, ${sentences.size} sentences from $CorpusPath")
            for (noise in TapNoiseLevels) {
                appendLine(results.getValue(noise.name).describe(noise))
            }
            appendLine(
                "Real misspellings typed key for key: " +
                    "${misspellings.count { (typed, committed) -> committed == MustFix[typed] }} of ${MustFix.size} " +
                    "fixed (${misspellings.joinToString { (typed, committed) -> "$typed -> $committed" }}).",
            )
        }
        println(report)
        File("build/reports").apply { mkdirs() }.resolve("autocorrect-accuracy.txt").writeText(report)

        results shouldBe Baseline
    }

    test("the same seed gives the same numbers on every run") {
        val noise = TapNoiseLevels.last()
        val firstSentences = sentences.take(40)
        replay.typeCorpus(firstSentences, noise) shouldBe replay.typeCorpus(firstSentences, noise)
    }

    test("space leaves correctly typed words alone") {
        for (word in MustNotChange) {
            replay.space(textBefore = "", typed = word, samples = centerTaps(word)).committed shouldBe word
            replay.space(textBefore = "we walked by the ", typed = word, samples = centerTaps(word))
                .committed shouldBe word
        }
    }

    test("real misspellings typed key for key") {
        val committed = MustFix.mapValues { (typed, _) ->
            replay.space(textBefore = "", typed = typed, samples = centerTaps(typed)).committed
        }
        committed shouldBe MustFix
    }
})

private const val CorpusPath = "accuracy/tatoeba_en_sentences.tsv"

/** Words space turned into longer ones before the 2026-11-15 fix (riverbed, violets, hedgehogs). */
private val MustNotChange = listOf("river", "violet", "hedgehog")

/**
 * Misspellings people really type, and what space must turn them into. All six are in
 * ime/dict/en_misspellings.tsv; adn and hte showed adnan and hate in the strip before that table shipped.
 */
private val MustFix = mapOf(
    "adn" to "and",
    "hte" to "the",
    "teh" to "the",
    "recieve" to "receive",
    "taht" to "that",
    "wiht" to "with",
)

/** How far, in key widths, a finger lands from the key's center (one standard deviation in each direction). */
private data class TapNoise(val name: String, val sigmaKeyWidths: Double, val seed: Long)

private val TapNoiseLevels = listOf(
    TapNoise(name = "careful", sigmaKeyWidths = 0.20, seed = 20261116L),
    TapNoise(name = "sloppy", sigmaKeyWidths = 0.30, seed = 20261117L),
)

/**
 * Recorded 2026-11-16 on accuracy/tatoeba_en_sentences.tsv (400 sentences, 2,493 scored words) with the
 * dictionary and settings of that day.
 *
 * careful: 101 slipped, 49.5% fixed, 37.6% left as typed, 12.9% changed to another word, 57.4% in the top three.
 * sloppy: 840 slipped, 41.0% fixed, 38.6% left as typed, 20.5% changed to another word, 49.4% in the top three.
 * No correctly typed word was changed at either level, and all six real misspellings were fixed.
 */
private val Baseline = mapOf(
    "careful" to AccuracyCounts(
        slipped = 101,
        fixed = 50,
        leftAlone = 38,
        changedToOther = 13,
        stripHits = 58,
        clean = 2392,
        wronglyChanged = 0,
    ),
    "sloppy" to AccuracyCounts(
        slipped = 840,
        fixed = 344,
        leftAlone = 324,
        changedToOther = 172,
        stripHits = 415,
        clean = 1653,
        wronglyChanged = 0,
    ),
)

/** What happened to the scored words of one run. A word is scored when it's letters only. */
private data class AccuracyCounts(
    /** Words whose taps spelled something else. */
    val slipped: Int = 0,
    /** Slipped words space turned into the intended word. */
    val fixed: Int = 0,
    /** Slipped words space left as typed. */
    val leftAlone: Int = 0,
    /** Slipped words space turned into a different wrong word. */
    val changedToOther: Int = 0,
    /** Slipped words whose intended word was among the first three candidates. */
    val stripHits: Int = 0,
    /** Words whose taps spelled them right. */
    val clean: Int = 0,
    /** Correctly typed words space changed. */
    val wronglyChanged: Int = 0,
) {
    fun describe(noise: TapNoise): String {
        return "${noise.name} taps (sigma ${noise.sigmaKeyWidths} key widths, seed ${noise.seed}): " +
            "${slipped + clean} words. $slipped slipped: $fixed fixed (${percent(fixed, slipped)}), " +
            "$leftAlone left as typed (${percent(leftAlone, slipped)}), " +
            "$changedToOther changed to another word (${percent(changedToOther, slipped)}), " +
            "intended word in the first three candidates $stripHits times (${percent(stripHits, slipped)}). " +
            "$clean typed right: $wronglyChanged changed by space (${percent(wronglyChanged, clean)})."
    }

    private fun percent(part: Int, whole: Int): String {
        return if (whole == 0) "n/a" else "%.1f%%".format(java.util.Locale.ROOT, 100.0 * part / whole)
    }
}

/** What ended the word: space, or a punctuation key typed straight after it. */
private enum class CommitTrigger { Space, Punctuation }

private data class CommitOutcome(val committed: String, val strip: List<String>)

private val SentenceEnds = setOf('.', '?', '!')

/** One noise level run over [sentences], each word typed with taps drawn from one [Random] seeded by [noise]. */
private fun AutocorrectReplay.typeCorpus(sentences: List<List<String>>, noise: TapNoise): AccuracyCounts {
    val random = Random(noise.seed)
    var counts = AccuracyCounts()
    for (tokens in sentences) {
        var textBefore = ""
        for ((index, token) in tokens.withIndex()) {
            if (!token.first().isLetter()) {
                textBefore = textBefore.trimEnd() + token + " "
                continue
            }
            val intended = token.lowercase()
            val scored = intended.all { it in 'a'..'z' }
            val taps = if (scored) noisyTaps(intended, noise.sigmaKeyWidths, random) else centerTaps(intended)
            val typedLower = if (scored) taps.joinToString(separator = "") { it.primaryText } else intended
            val sentenceStart = textBefore.isBlank() || textBefore.trimEnd().last() in SentenceEnds
            val typed = if (sentenceStart) typedLower.replaceFirstChar { it.uppercaseChar() } else typedLower
            val samples = if (sentenceStart) taps.withFirstShifted() else taps
            val next = tokens.getOrNull(index + 1)
            val punctuationNext = next != null && !next.first().isLetter()
            val trigger = if (punctuationNext) CommitTrigger.Punctuation else CommitTrigger.Space
            val outcome = commit(textBefore, typed, samples, trigger)
            textBefore += outcome.committed + " "
            if (!scored) continue
            val committed = outcome.committed.lowercase()
            counts = if (typedLower == intended) {
                counts.copy(
                    clean = counts.clean + 1,
                    wronglyChanged = counts.wronglyChanged + if (committed != intended) 1 else 0,
                )
            } else {
                counts.copy(
                    slipped = counts.slipped + 1,
                    fixed = counts.fixed + if (committed == intended) 1 else 0,
                    leftAlone = counts.leftAlone + if (committed == typedLower) 1 else 0,
                    changedToOther = counts.changedToOther +
                        if (committed != intended && committed != typedLower) 1 else 0,
                    stripHits = counts.stripHits + if (outcome.strip.any { it.lowercase() == intended }) 1 else 0,
                )
            }
        }
    }
    return counts
}

/**
 * What a fresh English install commits when a word ends: no learned words, no word pairs, no past corrections.
 */
private class AutocorrectReplay(private val dictionary: LatinDictionarySnapshot) {
    private val english = FlorisLocale.from("en")
    private val suppression = AutoCommitSuppression()
    private val sources = object : CandidateSignalSources {
        override suspend fun frequency(locale: FlorisLocale, word: String): Double {
            return candidateSignalKeyOf(word)?.let { dictionary.frequencyFor(it) } ?: 0.0
        }

        override fun isKnownUserDictionaryWord(word: String, locale: FlorisLocale) = false

        override suspend fun bigramScore(previousWord: String, word: String, locale: FlorisLocale) = 0.0

        override suspend fun trigramScore(
            secondPreviousWord: String,
            previousWord: String,
            word: String,
            locale: FlorisLocale,
        ) = 0.0

        override fun outcomeSignal(originalText: CharSequence, correctedText: CharSequence) = CorrectionOutcomeSignal()

        override fun rejectedPairPenalty(
            currentWord: CharSequence,
            candidateText: CharSequence,
            currentWordStart: Int?,
        ) = suppression.rejectedPairPenalty(currentWord, candidateText, currentWordStart)
    }

    /** What the strip shows and what space commits once [typed] was tapped as [samples] after [textBefore]. */
    fun space(textBefore: String, typed: String, samples: List<TouchDecoderSample>): CommitOutcome {
        return commit(textBefore, typed, samples, CommitTrigger.Space)
    }

    /**
     * What the strip shows and what [trigger] commits once [typed] was tapped as [samples] after [textBefore].
     * A punctuation key goes through KeyboardManager's flush before the character, which asks
     * [CandidateAutoCommitPolicy.selectAutoCommitCandidate] instead of the space policy.
     */
    fun commit(
        textBefore: String,
        typed: String,
        samples: List<TouchDecoderSample>,
        trigger: CommitTrigger,
    ): CommitOutcome = runBlocking {
        val touchEvidence = TouchDecoderEvidenceBuffer().apply { samples.forEach(::record) }.evidenceFor(typed)
        val suggestions = OffensiveWordPolicy.filterCandidates(
            candidates = LatinDictionarySuggester.suggest(
                rawWord = typed,
                dictionary = dictionary,
                maxCandidateCount = 8,
                languageCode = "en",
                userOverlay = emptyMap(),
            ).map { suggestion ->
                WordSuggestionCandidate(
                    text = suggestion.text,
                    confidence = suggestion.confidence,
                    isEligibleForAutoCommit = suggestion.isEligibleForAutoCommit,
                )
            },
            tier = OffensiveWordPolicy.tier(blockPossiblyOffensive = false, blockSlursOnly = false),
        )
        val textBeforeCursor = textBefore + typed
        val typedKey = candidateSignalKeyOf(typed)
        val typedWordKnown = typedKey != null && sources.frequency(english, typedKey) > 0.0
        val signals = CandidateSignalAssembler.assemble(
            primaryLocale = english,
            locales = listOf(english),
            textBeforeSelection = textBeforeCursor,
            currentWord = typed,
            currentWordStart = textBefore.length,
            candidates = suggestions,
            sources = sources,
        )
        val ranked = SwiftKeyCandidateRanker.rank(
            context = SwiftKeyDecoderContext(
                currentWord = typed,
                maxCandidateCount = 8,
                typedWordKnown = typedWordKnown,
                touchEvidence = touchEvidence,
                candidateSignals = signals,
            ),
            preferred = emptyList(),
            fallback = suggestions,
        )
        val threshold = AutoCorrectConfidencePolicy.thresholdFor(AutoCorrectConfidencePolicy.DEFAULT_PERCENT)
        val phraseRepair = ImmediateAutocorrect.englishPhraseRepairCandidate(typed, "en")
        val contraction = ImmediateAutocorrect.englishContractionCandidate(typed, "en")
        val pick = when (trigger) {
            CommitTrigger.Space -> CandidateAutoCommitPolicy.selectSpacebarCandidate(
                autoCorrectEnabled = true,
                autoCorrectCommitMode = AutoCorrectCommitMode.NORMAL,
                autoCorrectConfidenceThreshold = threshold,
                quickPredictionInsertEnabled = false,
                keyVariation = KeyVariation.NORMAL,
                currentWord = typed,
                currentWordStart = textBefore.length,
                textBeforeCursor = textBeforeCursor,
                candidates = ranked,
                candidateSignals = signals,
                rejectionPolicy = suppression,
                userDictionaryShortcutCandidate = null,
                immediatePhraseRepairCandidate = phraseRepair,
                immediateAutoCommitCandidate = contraction,
            )
            CommitTrigger.Punctuation -> CandidateAutoCommitPolicy.selectAutoCommitCandidate(
                autoCorrectEnabled = true,
                autoCorrectCommitMode = AutoCorrectCommitMode.NORMAL,
                autoCorrectConfidenceThreshold = threshold,
                keyVariation = KeyVariation.NORMAL,
                currentWord = typed,
                currentWordStart = textBefore.length,
                candidates = ranked,
                candidateSignals = signals,
                rejectionPolicy = suppression,
                userDictionaryShortcutCandidate = null,
                immediatePhraseRepairCandidate = phraseRepair,
                immediateAutoCommitCandidate = contraction,
            )
        }
        CommitOutcome(
            committed = pick?.let { it.text.toString() } ?: typed,
            strip = ranked.take(3).map { it.text.toString() },
        )
    }
}

/**
 * A phone QWERTY keyboard in key widths, laid out like the default layout with the number row on: rows 1.4 key
 * widths apart, the number row above the letters, the second letter row shifted half a key and the third a key
 * and a half with shift and backspace beside it, and a bottom row of symbols, comma, emoji, space, period and
 * enter. Each key is drawn 0.1 narrower than its cell and 1.15 tall.
 *
 * A touch counts for the letter key whose cell it lands in, and a touch past the end of a letter row or above or
 * below the letter rows counts for the nearest letter key. On the real keyboard such a touch types a digit,
 * shifts or deletes instead, which this replay doesn't model: it would need a word with a letter missing or
 * extra, and the corpus scores what a word's taps spelled.
 */
private object QwertyGrid {
    private const val RowPitch = 1.4
    private const val KeyGap = 0.1
    private const val KeyHeight = 1.15
    private val letterRows = listOf("qwertyuiop" to 0.0, "asdfghjkl" to 0.5, "zxcvbnm" to 1.5)

    /**
     * A key in the cell from [cellLeft] to [cellRight] of [row], -1 being the number row. [char] is null for the
     * keys that aren't letters.
     */
    class Key(val char: Char?, row: Int, cellLeft: Double, cellRight: Double) {
        val centerX = (cellLeft + cellRight) / 2
        val centerY = row * RowPitch + RowPitch / 2
        val width = cellRight - cellLeft - KeyGap
        val left = (centerX - width / 2).toFloat()
        val top = (centerY - KeyHeight / 2).toFloat()
    }

    private val letterKeys: List<Key> = letterRows.flatMapIndexed { row, (letters, offset) ->
        letters.mapIndexed { col, char -> Key(char, row, offset + col, offset + col + 1) }
    }
    private val otherKeys: List<Key> = (0 until 10).map { col -> Key(null, -1, col.toDouble(), col + 1.0) } +
        Key(null, 2, 0.0, 1.5) +
        Key(null, 2, 8.5, 10.0) +
        listOf(0.0 to 1.0, 1.0 to 2.0, 2.0 to 3.0, 3.0 to 7.4, 7.4 to 8.4, 8.4 to 10.0)
            .map { (cellLeft, cellRight) -> Key(null, 3, cellLeft, cellRight) }
    private val allKeys = letterKeys + otherKeys
    private val byChar = letterKeys.associateBy { it.char }

    fun key(char: Char): Key = byChar.getValue(char)

    /** The touch sample the keyboard records for a touch at [x], [y]: the key hit and the nearby letters. */
    fun sampleAt(x: Double, y: Double): TouchDecoderSample {
        val row = floor(y / RowPitch).toInt().coerceIn(0, letterRows.lastIndex)
        val (letters, offset) = letterRows[row]
        val hit = key(letters[floor(x - offset).toInt().coerceIn(0, letters.lastIndex)])
        // TextKeyboard.getNearbyKeysForPos keeps the five most confident of all keys, then
        // TextKeyboardLayout.recordTouchDecoderEvidence drops the key it hit and every key that isn't a letter.
        val alternatives = allKeys.mapNotNull { key ->
            val width = key.width.toFloat()
            NearbyKeyConfidence.of(x.toFloat(), y.toFloat(), key.left, key.top, width, KeyHeight.toFloat())
                ?.let { confidence -> key to confidence }
        }
            .sortedByDescending { (_, confidence) -> confidence }
            .take(5)
            .mapNotNull { (key, confidence) ->
                val letter = key.char?.takeIf { key !== hit } ?: return@mapNotNull null
                TouchDecoderCandidate(text = letter.toString(), confidence = confidence)
            }
        return TouchDecoderSample(primaryText = checkNotNull(hit.char).toString(), alternatives = alternatives)
    }
}

/** What shift does to the first tap of a sentence: the key hit and its nearby letters come out as capitals. */
private fun List<TouchDecoderSample>.withFirstShifted(): List<TouchDecoderSample> {
    val first = firstOrNull() ?: return this
    val shifted = first.copy(
        primaryText = first.primaryText.uppercase(),
        alternatives = first.alternatives.map { it.copy(text = it.text.uppercase()) },
    )
    return listOf(shifted) + drop(1)
}

private fun noisyTaps(word: String, sigma: Double, random: Random): List<TouchDecoderSample> {
    return word.map { char ->
        val key = QwertyGrid.key(char)
        QwertyGrid.sampleAt(key.centerX + random.nextGaussian() * sigma, key.centerY + random.nextGaussian() * sigma)
    }
}

/** Taps at each letter's key center. Letters off the grid (an apostrophe) get no touch evidence. */
private fun centerTaps(word: String): List<TouchDecoderSample> {
    if (!word.all { it in 'a'..'z' }) return emptyList()
    return word.map { char -> QwertyGrid.key(char).let { QwertyGrid.sampleAt(it.centerX, it.centerY) } }
}

private object AccuracyCorpus {
    private val Token = Regex("""[A-Za-z]+(?:'[a-z]+)?|[.,?!]""")

    /** The sentences of [path] as word and punctuation tokens, skipping the # header. */
    fun load(path: String): List<List<String>> {
        val file = bundledTestResource(path) ?: error("missing test resource $path")
        return file.readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line -> Token.findAll(line.substringAfter('\t')).map { it.value }.toList() }
    }
}

private fun bundledAsset(path: String): File? {
    return listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path")).firstOrNull { it.isFile }
}

private fun bundledTestResource(path: String): File? {
    return listOf(File("src/test/resources/$path"), File("app/src/test/resources/$path")).firstOrNull { it.isFile }
}
