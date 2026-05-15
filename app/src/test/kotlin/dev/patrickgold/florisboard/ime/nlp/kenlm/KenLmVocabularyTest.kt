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

package dev.patrickgold.florisboard.ime.nlp.kenlm

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

private fun buildVocabBuffer(tokens: List<String>): ByteBuffer {
    val baos = ByteArrayOutputStream()
    // Concatenate \0-terminated UTF-8 tokens to get string bytes.
    val stringBytes = ByteArrayOutputStream()
    tokens.forEach {
        stringBytes.write(it.toByteArray(Charsets.UTF_8))
        stringBytes.write(0)
    }
    val strings = stringBytes.toByteArray()
    val header = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
    header.putLong(tokens.size.toLong())
    header.putLong(strings.size.toLong())
    baos.write(header.array())
    baos.write(strings)
    return ByteBuffer.wrap(baos.toByteArray()).order(ByteOrder.LITTLE_ENDIAN)
}

class KenLmVocabularyTest : FunSpec({
    test("parses a vocabulary with the canonical <unk> sentinel at index 0") {
        val buffer = buildVocabBuffer(
            listOf("<unk>", "<s>", "</s>", "the", "cat", "sat"),
        )
        val vocab = KenLmVocabulary.parse(buffer).shouldNotBeNull()
        vocab.size shouldBe 6
        vocab.indexOf("<unk>") shouldBe 0
        vocab.indexOf("the") shouldBe 3
        vocab.indexOf("cat") shouldBe 4
        vocab.tokenAt(5) shouldBe "sat"
    }

    test("returns null on a buffer that's too short for the 16-byte header") {
        KenLmVocabulary.parse(ByteBuffer.allocate(8)).shouldBeNull()
    }

    test("returns null on an absurdly large advertised string-count") {
        val buffer = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putLong(Long.MAX_VALUE)
        buffer.putLong(100)
        buffer.flip()
        KenLmVocabulary.parse(buffer).shouldBeNull()
    }

    test("indexOf returns UNK_INDEX (0) for an out-of-vocab token") {
        val buffer = buildVocabBuffer(listOf("<unk>", "the", "cat"))
        val vocab = KenLmVocabulary.parse(buffer).shouldNotBeNull()
        vocab.indexOf("xyzzy") shouldBe KenLmVocabulary.UNK_INDEX
    }

    test("contains() distinguishes real words from the <unk> sentinel") {
        val buffer = buildVocabBuffer(listOf("<unk>", "the", "cat"))
        val vocab = KenLmVocabulary.parse(buffer).shouldNotBeNull()
        vocab.contains("the") shouldBe true
        vocab.contains("<unk>") shouldBe false
        vocab.contains("xyzzy") shouldBe false
    }

    test("preserves UTF-8 multi-byte tokens (CJK)") {
        val buffer = buildVocabBuffer(listOf("<unk>", "你好", "世界"))
        val vocab = KenLmVocabulary.parse(buffer).shouldNotBeNull()
        vocab.tokenAt(1) shouldBe "你好"
        vocab.indexOf("世界") shouldBe 2
    }

    test("stops at the advertised string count even if more strings remain in buffer") {
        // Build a buffer with 4 strings but advertise only 2.
        val baos = ByteArrayOutputStream()
        val strings = "<unk>\u0000the\u0000cat\u0000sat\u0000".toByteArray(Charsets.UTF_8)
        val header = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        header.putLong(2L)                           // count = 2
        header.putLong(strings.size.toLong())        // but full byte length
        baos.write(header.array())
        baos.write(strings)
        val buffer = ByteBuffer.wrap(baos.toByteArray()).order(ByteOrder.LITTLE_ENDIAN)
        val vocab = KenLmVocabulary.parse(buffer).shouldNotBeNull()
        vocab.size shouldBe 2
        vocab.tokenAt(0) shouldBe "<unk>"
        vocab.tokenAt(1) shouldBe "the"
    }
})
