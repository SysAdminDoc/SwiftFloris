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

package dev.patrickgold.florisboard.ime.nlp.context

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

/**
 * How likely a word is after the one or two words before it, read from a bundled `.sflm` file of pruned word
 * pair and word triple counts. Unseen pairs and triples back off to the shorter context times the file's alpha
 * ("stupid backoff", Brants et al. 2007), so scores rank words within one context but aren't probabilities.
 *
 * Every lookup reads the buffer in place with absolute gets, so a memory-mapped file costs no heap and concurrent
 * lookups are safe. utils/build_english_context_model.py writes the file and documents its layout.
 */
internal class ContextModel private constructor(private val buffer: ByteBuffer) {
    val vocabularySize: Int
    private val alpha: Double
    private val quantScale: Double
    private val wordBytesStart: Int
    private val wordOffsetsStart: Int
    private val unigramStart: Int
    private val bigramOffsetsStart: Int
    private val bigramNextStart: Int
    private val bigramScoreStart: Int
    private val trigramContextCount: Int
    private val trigramContextsStart: Int
    private val trigramOffsetsStart: Int
    private val trigramNextStart: Int
    private val trigramScoreStart: Int

    init {
        buffer.order(ByteOrder.BIG_ENDIAN)
        val size = buffer.capacity().toLong()
        fun intAt(at: Long): Int {
            require(at >= 0 && at + Int.SIZE_BYTES <= size) { "context model cut off" }
            return buffer.getInt(at.toInt())
        }
        fun countAt(at: Long): Int = intAt(at).also { require(it >= 0) { "bad context model section size" } }
        require(intAt(0) == Magic) { "not a context model" }
        require(intAt(4) == Version) { "unsupported context model version ${intAt(4)}" }
        vocabularySize = intAt(8)
        alpha = Float.fromBits(intAt(12)).toDouble()
        quantScale = intAt(16).toDouble()
        require(vocabularySize in 1..MaxVocabularySize && alpha > 0.0 && quantScale > 0.0) { "bad context model header" }
        // Every section's size comes from a count stored just before it, so a count read past the end of a
        // short file or a negative one is refused here rather than turning into a wrong read during a lookup.
        val wordBytes = countAt(20)
        val wordOffsets = HeaderBytes + wordBytes.toLong()
        val unigrams = wordOffsets + (vocabularySize + 1L) * Int.SIZE_BYTES
        val bigramOffsets = unigrams + vocabularySize + 1
        val bigramCountAt = bigramOffsets + (vocabularySize + 2L) * Int.SIZE_BYTES
        val bigramCount = countAt(bigramCountAt)
        val bigramNext = bigramCountAt + Int.SIZE_BYTES
        val bigramScores = bigramNext + bigramCount.toLong() * Short.SIZE_BYTES
        val trigramContextCountAt = bigramScores + bigramCount
        val contextCount = countAt(trigramContextCountAt)
        val trigramContexts = trigramContextCountAt + Int.SIZE_BYTES
        val trigramOffsets = trigramContexts + contextCount.toLong() * Int.SIZE_BYTES
        val trigramCountAt = trigramOffsets + (contextCount + 1L) * Int.SIZE_BYTES
        val trigramCount = countAt(trigramCountAt)
        val trigramNext = trigramCountAt + Int.SIZE_BYTES
        val trigramScores = trigramNext + trigramCount.toLong() * Short.SIZE_BYTES
        require(trigramScores + trigramCount == size) { "context model size mismatch" }
        // Lookups index the word bytes and the pair and triple rows through these tables without bounds checks,
        // so a file whose sizes add up but whose offsets run backwards or past their section is refused here too,
        // where ContextModelStore catches it, rather than throwing on every word typed.
        fun requireOffsets(at: Long, count: Long, end: Int, what: String) {
            var previous = 0
            for (index in 0 until count) {
                val offset = buffer.getInt((at + index * Int.SIZE_BYTES).toInt())
                require(if (index == 0L) offset == 0 else offset >= previous) { "bad context model $what offsets" }
                previous = offset
            }
            require(previous == end) { "bad context model $what offsets" }
        }
        requireOffsets(wordOffsets, vocabularySize + 1L, end = wordBytes + 1, what = "word")
        requireOffsets(bigramOffsets, vocabularySize + 2L, end = bigramCount, what = "word pair")
        requireOffsets(trigramOffsets, contextCount + 1L, end = trigramCount, what = "word triple")
        wordBytesStart = HeaderBytes
        wordOffsetsStart = wordOffsets.toInt()
        unigramStart = unigrams.toInt()
        bigramOffsetsStart = bigramOffsets.toInt()
        bigramNextStart = bigramNext.toInt()
        bigramScoreStart = bigramScores.toInt()
        trigramContextCount = contextCount
        trigramContextsStart = trigramContexts.toInt()
        trigramOffsetsStart = trigramOffsets.toInt()
        trigramNextStart = trigramNext.toInt()
        trigramScoreStart = trigramScores.toInt()
    }

    /**
     * The backoff score of [word] after [prev2] [prev1], or null when the model can't say because [word] or
     * [prev1] isn't in its vocabulary. A null [prev2] means [prev1] starts the sentence; a [prev2] the model
     * doesn't know leaves only [prev1] as context.
     */
    fun score(prev2: String?, prev1: String, word: String): Double? {
        val wordId = idOf(word) ?: return null
        val prev1Id = idOf(prev1) ?: return null
        val prev2Id = if (prev2 == null) SentenceStart else idOf(prev2)
        val pairOrWord = bigram(prev1Id, wordId) ?: (alpha * unigram(wordId))
        if (prev2Id == null) return pairOrWord
        return trigram(prev2Id, prev1Id, wordId) ?: (alpha * pairOrWord)
    }

    /** [score] as a 0.0 to 1.0 context probability ([contextProbabilityOf]), or null when the model can't say. */
    fun contextProbability(prev2: String?, prev1: String, word: String): Double? {
        return score(prev2, prev1, word)?.let { contextProbabilityOf(it) }
    }

    /**
     * The vocabulary id of [word], lowercased the way the training text was, or null when it isn't there. Training
     * words never start or end with an apostrophe or hyphen, so those are trimmed: the word before 'quoted' text
     * or a dangling "pre-" still finds its id.
     */
    fun idOf(word: String): Int? {
        val key = word.lowercase(Locale.ROOT).replace(RightSingleQuote, '\'').trim('\'', '-')
            .toByteArray(Charsets.UTF_8)
        if (key.isEmpty()) return null
        var low = 1
        var high = vocabularySize
        while (low <= high) {
            val mid = (low + high) ushr 1
            val comparison = compareWord(mid, key)
            when {
                comparison < 0 -> low = mid + 1
                comparison > 0 -> high = mid - 1
                else -> return mid
            }
        }
        return null
    }

    private fun compareWord(id: Int, key: ByteArray): Int {
        val start = buffer.getInt(wordOffsetsStart + (id - 1) * Int.SIZE_BYTES)
        val length = buffer.getInt(wordOffsetsStart + id * Int.SIZE_BYTES) - 1 - start
        for (index in 0 until minOf(length, key.size)) {
            val stored = buffer.get(wordBytesStart + start + index).toInt() and 0xFF
            val wanted = key[index].toInt() and 0xFF
            if (stored != wanted) return stored - wanted
        }
        return length - key.size
    }

    private fun unigram(wordId: Int): Double = decode(buffer.get(unigramStart + wordId))

    private fun bigram(prev1Id: Int, wordId: Int): Double? {
        val from = buffer.getInt(bigramOffsetsStart + prev1Id * Int.SIZE_BYTES)
        val to = buffer.getInt(bigramOffsetsStart + (prev1Id + 1) * Int.SIZE_BYTES)
        val index = searchNext(bigramNextStart, from, to, wordId)
        return if (index < 0) null else decode(buffer.get(bigramScoreStart + index))
    }

    private fun trigram(prev2Id: Int, prev1Id: Int, wordId: Int): Double? {
        val context = (prev2Id shl 16) or prev1Id
        var low = 0
        var high = trigramContextCount - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val stored = buffer.getInt(trigramContextsStart + mid * Int.SIZE_BYTES)
            when {
                stored < context -> low = mid + 1
                stored > context -> high = mid - 1
                else -> {
                    val from = buffer.getInt(trigramOffsetsStart + mid * Int.SIZE_BYTES)
                    val to = buffer.getInt(trigramOffsetsStart + (mid + 1) * Int.SIZE_BYTES)
                    val index = searchNext(trigramNextStart, from, to, wordId)
                    return if (index < 0) null else decode(buffer.get(trigramScoreStart + index))
                }
            }
        }
        return null
    }

    /** The index in [from] until [to] whose unsigned 16-bit word id, in the array at [start], is [wordId], or -1. */
    private fun searchNext(start: Int, from: Int, to: Int, wordId: Int): Int {
        var low = from
        var high = to - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val stored = buffer.getShort(start + mid * Short.SIZE_BYTES).toInt() and 0xFFFF
            when {
                stored < wordId -> low = mid + 1
                stored > wordId -> high = mid - 1
                else -> return mid
            }
        }
        return -1
    }

    private fun decode(quantized: Byte): Double = 10.0.pow(-(quantized.toInt() and 0xFF) / quantScale)

    companion object {
        private const val Magic = 0x53464C4D // "SFLM"
        private const val Version = 1
        private const val HeaderBytes = 24
        private const val SentenceStart = 0
        private const val MaxVocabularySize = 0xFFFE
        private val RightSingleQuote = Char(0x2019)

        /**
         * How many powers of ten of backoff score [contextProbabilityOf] spreads over 0.0 to 1.0. Six beat three,
         * four, five and eight on the autocorrect accuracy harness: one correction fewer than five, more real-word
         * slips caught, and about a third fewer correctly typed words offered a swap in the strip.
         */
        const val ContextDecades = 6.0

        fun read(buffer: ByteBuffer): ContextModel = ContextModel(buffer)

        /**
         * A backoff [score] as a 0.0 to 1.0 context probability: 1.0 for a word that always follows its context,
         * falling linearly in log score to 0.0 for one [decades] powers of ten less likely or worse.
         */
        fun contextProbabilityOf(score: Double, decades: Double = ContextDecades): Double {
            if (score <= 0.0) return 0.0
            return (1.0 + log10(score) / decades).coerceIn(0.0, 1.0)
        }
    }
}
