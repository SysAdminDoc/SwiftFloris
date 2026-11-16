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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

class ContextModelTest : FunSpec({
    // Scores are 10^(-q / 25): q 25 is 0.1, q 50 is 0.01, q 75 is 0.001.
    val model = TinyModel(
        words = listOf("apple", "bake", "cake", "don't"),
        alpha = 0.4f,
        unigrams = mapOf("apple" to 75, "bake" to 75, "cake" to 75, "don't" to 100),
        pairs = mapOf(("bake" to "cake") to 50, (null to "bake") to 25),
        triples = mapOf(Triple("apple", "bake", "cake") to 25, Triple(null, "bake", "apple") to 50),
    ).build()

    test("a seen word triple scores what the file stores") {
        model.score("apple", "bake", "cake")!! shouldBe (0.1 plusOrMinus 1e-9)
    }

    test("an unseen triple backs off to its pair, and an unseen pair to the word, times alpha each step") {
        model.score("cake", "bake", "cake")!! shouldBe (0.4 * 0.01 plusOrMinus 1e-9)
        model.score("cake", "apple", "cake")!! shouldBe (0.4 * 0.4 * 0.001 plusOrMinus 1e-9)
    }

    test("a missing second word means the first starts the sentence") {
        model.score(null, "bake", "apple")!! shouldBe (0.01 plusOrMinus 1e-9)
        model.score(null, "bake", "cake")!! shouldBe (0.4 * 0.01 plusOrMinus 1e-9)
    }

    test("a second word the model doesn't know leaves only the first as context") {
        model.score("zebra", "bake", "cake")!! shouldBe (0.01 plusOrMinus 1e-9)
    }

    test("the model can't say when the word or the word before it is unknown") {
        model.score("apple", "bake", "zebra") shouldBe null
        model.score("apple", "zebra", "cake") shouldBe null
        model.score(null, "", "cake") shouldBe null
    }

    test("words are looked up lowercased, with the typographic apostrophe folded") {
        model.idOf("CAKE") shouldBe model.idOf("cake")
        model.idOf("don" + Char(0x2019) + "t") shouldBe model.idOf("don't")
        model.idOf("don't") shouldNotBe null
    }

    test("an apostrophe or hyphen at either end of a word is trimmed, the way the training text was split") {
        val quote = Char(0x2019)
        model.idOf("'bake'") shouldBe model.idOf("bake")
        model.idOf("${quote}bake$quote") shouldBe model.idOf("bake")
        model.idOf("apple-") shouldBe model.idOf("apple")
        model.score("'apple", "bake-", "cake'") shouldBe model.score("apple", "bake", "cake")
        model.idOf("'") shouldBe null
        model.idOf("--") shouldBe null
    }

    test("context probability spreads six powers of ten of score over 0 to 1") {
        ContextModel.contextProbabilityOf(1.0) shouldBe 1.0
        ContextModel.contextProbabilityOf(0.001) shouldBe (0.5 plusOrMinus 1e-9)
        ContextModel.contextProbabilityOf(1e-6) shouldBe (0.0 plusOrMinus 1e-9)
        ContextModel.contextProbabilityOf(1e-9) shouldBe 0.0
        ContextModel.contextProbabilityOf(0.0) shouldBe 0.0
        model.contextProbability("apple", "bake", "cake")!! shouldBe (1.0 - 1.0 / 6 plusOrMinus 1e-9)
    }

    test("a file that isn't a context model, a newer version or a cut-off file is refused") {
        val bytes = TinyModel(listOf("a"), 0.4f, mapOf("a" to 10), emptyMap(), emptyMap()).bytes()
        shouldThrow<IllegalArgumentException> {
            ContextModel.read(ByteBuffer.wrap(bytes.copyOf().also { it[0] = 'X'.code.toByte() }))
        }
        shouldThrow<IllegalArgumentException> {
            ContextModel.read(ByteBuffer.wrap(bytes.copyOf().also { it[7] = 2 }))
        }
        shouldThrow<IllegalArgumentException> {
            ContextModel.read(ByteBuffer.wrap(bytes.copyOf().also { it[20] = 0xFF.toByte() }))
        }
        for (length in 0 until bytes.size) {
            shouldThrow<IllegalArgumentException> { ContextModel.read(ByteBuffer.wrap(bytes.copyOf(length))) }
        }
        ContextModel.read(ByteBuffer.wrap(bytes)).vocabularySize shouldBe 1
    }

    test("a file whose sizes add up but whose offsets run backwards or past their section is refused") {
        val bytes = TinyModel(
            words = listOf("a", "b"),
            alpha = 0.4f,
            unigrams = mapOf("a" to 10, "b" to 20),
            pairs = mapOf(("a" to "b") to 5),
            triples = mapOf(Triple(null, "a", "b") to 3),
        ).bytes()
        ContextModel.read(ByteBuffer.wrap(bytes)).score(null, "a", "b") shouldNotBe null
        // The 24-byte header and "a\nb" come first, then 3 word offsets, 3 unigram bytes, 4 pair offsets, the
        // pair count, 1 pair id and score, the triple context count and 1 context, then the 2 triple offsets.
        val secondWordStart = 24 + 3 + 4
        val pairsOfAEnd = 24 + 3 + 3 * 4 + 3 + 2 * 4
        val triplesEnd = 24 + 3 + 3 * 4 + 3 + 4 * 4 + 4 + 2 + 1 + 4 + 4 + 4
        fun ByteArray.intAt(at: Int) = ByteBuffer.wrap(this).getInt(at)
        bytes.intAt(secondWordStart) shouldBe 2
        bytes.intAt(pairsOfAEnd) shouldBe 1
        bytes.intAt(triplesEnd) shouldBe 1
        val corruptions = listOf(secondWordStart to 1000, secondWordStart to -50, pairsOfAEnd to 1000, triplesEnd to 1000)
        for ((at, offset) in corruptions) {
            val corrupt = bytes.copyOf().also { ByteBuffer.wrap(it).putInt(at, offset) }
            shouldThrow<IllegalArgumentException> { ContextModel.read(ByteBuffer.wrap(corrupt)) }
        }
    }

    test("the bundled English model prefers the word that fits") {
        val english = bundledEnglishModel()
        english.vocabularySize shouldBe 50_000
        english.score("to", "the", "store")!! shouldBeGreaterThan english.score("to", "the", "stir")!!
        english.score("i", "came", "from")!! shouldBeGreaterThan english.score("i", "came", "form")!! * 100
        english.score(null, "thank", "you")!! shouldBeGreaterThan english.score(null, "thank", "your")!!
        english.score("to", "the", "qzxv") shouldBe null
    }
})

private fun bundledEnglishModel(): ContextModel {
    val path = "lm/en_context.sflm"
    val file = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path")).first { it.isFile }
    return FileChannel.open(file.toPath(), StandardOpenOption.READ).use { channel ->
        ContextModel.read(channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size()))
    }
}

/**
 * A context model file written the way utils/build_english_context_model.py writes one. [words] must be in UTF-8
 * byte order; a null first word in [pairs] or [triples] is the sentence start.
 */
private class TinyModel(
    val words: List<String>,
    val alpha: Float,
    val unigrams: Map<String, Int>,
    val pairs: Map<Pair<String?, String>, Int>,
    val triples: Map<Triple<String?, String, String>, Int>,
) {
    private fun id(word: String?) = if (word == null) 0 else words.indexOf(word) + 1

    fun build(): ContextModel = ContextModel.read(ByteBuffer.wrap(bytes()))

    fun bytes(): ByteArray {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).run {
            writeBytes("SFLM")
            writeInt(1)
            writeInt(words.size)
            writeFloat(alpha)
            writeInt(25)
            val blob = words.joinToString("\n").toByteArray(Charsets.UTF_8)
            writeInt(blob.size)
            write(blob)
            var start = 0
            for (word in words) {
                writeInt(start)
                start += word.toByteArray(Charsets.UTF_8).size + 1
            }
            writeInt(blob.size + 1)
            writeByte(0)
            for (word in words) writeByte(unigrams.getValue(word))

            val pairRows = pairs.map { (key, q) -> Triple(id(key.first), id(key.second), q) }
                .sortedWith(compareBy({ it.first }, { it.second }))
            for (context in 0..words.size + 1) writeInt(pairRows.count { it.first < context })
            writeInt(pairRows.size)
            for (row in pairRows) writeShort(row.second)
            for (row in pairRows) writeByte(row.third)

            val tripleRows = triples.map { (key, q) -> Triple((id(key.first) shl 16) or id(key.second), id(key.third), q) }
                .sortedWith(compareBy({ it.first }, { it.second }))
            val contexts = tripleRows.map { it.first }.distinct()
            writeInt(contexts.size)
            for (context in contexts) writeInt(context)
            for (context in contexts) writeInt(tripleRows.indexOfFirst { it.first == context })
            writeInt(tripleRows.size)
            writeInt(tripleRows.size)
            for (row in tripleRows) writeShort(row.second)
            for (row in tripleRows) writeByte(row.third)
        }
        return buffer.toByteArray()
    }
}
