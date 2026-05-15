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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

private const val KENLM_MAGIC = "mmap lm http://kheafield.com/code\n\u0000"
private const val MAGIC_LENGTH = 64

/**
 * Builds a synthetic KenLM-shaped header so the reader can be tested
 * without shipping a real (multi-MB) language-model file in the repo.
 * Layout matches `lm/binary_format.hh`: magic + modelType + order +
 * fixed-width params + per-order n-gram counts.
 */
private fun buildHeader(
    modelType: KenLmModelType = KenLmModelType.TRIE,
    order: Int = 3,
    hasVocabulary: Boolean = true,
    counts: List<Long> = listOf(50_000L, 1_200_000L, 8_000_000L),
): ByteArray {
    val baos = ByteArrayOutputStream()
    val magic = KENLM_MAGIC.toByteArray(Charsets.US_ASCII)
    baos.write(magic)
    baos.write(ByteArray(MAGIC_LENGTH - magic.size))                              // NUL pad
    baos.write(intToLe(modelType.id))
    baos.write(intToLe(order))
    baos.write(byteArrayOf(1))                                                    // probing_multiplier
    baos.write(byteArrayOf(if (hasVocabulary) 1 else 0))                           // has_vocabulary
    baos.write(byteArrayOf(0))                                                    // pointer_bhiksha
    baos.write(byteArrayOf(order.toByte()))                                       // counts_following_order
    counts.forEach { baos.write(longToLe(it)) }
    return baos.toByteArray()
}

private fun intToLe(value: Int): ByteArray = byteArrayOf(
    (value and 0xFF).toByte(),
    ((value shr 8) and 0xFF).toByte(),
    ((value shr 16) and 0xFF).toByte(),
    ((value shr 24) and 0xFF).toByte(),
)

private fun longToLe(value: Long): ByteArray {
    val out = ByteArray(8)
    for (i in 0 until 8) {
        out[i] = ((value shr (i * 8)) and 0xFFL).toByte()
    }
    return out
}

class KenLmBinaryReaderTest : FunSpec({
    test("reads the canonical KenLM TRIE header shape") {
        val header = KenLmBinaryReader.readHeader(ByteArrayInputStream(buildHeader()))
        header.modelType shouldBe KenLmModelType.TRIE
        header.order shouldBe 3
        header.hasVocabulary shouldBe true
        header.ngramCounts shouldBe listOf(50_000L, 1_200_000L, 8_000_000L)
        header.totalNgrams shouldBe 9_250_000L
    }

    test("handles QUANT_ARRAY_TRIE model type and a 5-gram order") {
        val header = KenLmBinaryReader.readHeader(
            ByteArrayInputStream(
                buildHeader(
                    modelType = KenLmModelType.QUANT_ARRAY_TRIE,
                    order = 5,
                    counts = listOf(10_000L, 200_000L, 3_000_000L, 4_000_000L, 5_000_000L),
                ),
            ),
        )
        header.modelType shouldBe KenLmModelType.QUANT_ARRAY_TRIE
        header.order shouldBe 5
        header.ngramCounts.size shouldBe 5
    }

    test("rejects a stream that does not start with the KenLM magic") {
        val bytes = "this is not a kenlm file at all please return nothing".toByteArray()
            .copyOf(MAGIC_LENGTH)
        shouldThrow<KenLmFormatException> {
            KenLmBinaryReader.readHeader(ByteArrayInputStream(bytes))
        }
    }

    test("rejects an unrealistic order value (> 8)") {
        val bytes = buildHeader(order = 12, counts = List(12) { 1L })
        shouldThrow<KenLmFormatException> {
            KenLmBinaryReader.readHeader(ByteArrayInputStream(bytes))
        }
    }

    test("rejects truncated streams with a contextual error") {
        // Magic only — no model type / order / counts.
        val truncated = KENLM_MAGIC.toByteArray(Charsets.US_ASCII).copyOf(MAGIC_LENGTH)
        shouldThrow<KenLmFormatException> {
            KenLmBinaryReader.readHeader(ByteArrayInputStream(truncated))
        }
    }
})
