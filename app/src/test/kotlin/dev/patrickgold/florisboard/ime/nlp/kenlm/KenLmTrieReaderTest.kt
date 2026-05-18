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
import io.kotest.matchers.shouldBe
import java.io.ByteArrayOutputStream
import java.nio.file.Files

private const val MAPPED_KENLM_MAGIC = "mmap lm http://kheafield.com/code\n\u0000"
private const val MAPPED_KENLM_MAGIC_LENGTH = 64

class KenLmTrieReaderTest : FunSpec({
    test("readBytesAt treats offsets as absolute file offsets") {
        val body = byteArrayOf(0x11, 0x22, 0x33, 0x44)
        val path = Files.createTempFile("swiftfloris-kenlm-reader-", ".bin")
        path.toFile().deleteOnExit()
        try {
            Files.write(path, buildMappedKenLmFile(body))

            KenLmTrieReader.openMapped(path).use { reader ->
                reader.bodyStartOffset shouldBe 84L
                reader.bodyByteLength shouldBe body.size.toLong()
                reader.readBytesAt(reader.bodyStartOffset, 2)?.toList() shouldBe listOf(
                    0x11.toByte(),
                    0x22.toByte(),
                )
                reader.readBytesAt(reader.bodyStartOffset - 1, 1) shouldBe null
                reader.readBytesAt(0L, 1) shouldBe null
                reader.readBytesAt(reader.bodyStartOffset + body.size - 1, 2) shouldBe null
            }
        } finally {
            runCatching { Files.deleteIfExists(path) }
        }
    }
})

private fun buildMappedKenLmFile(body: ByteArray): ByteArray {
    val baos = ByteArrayOutputStream()
    val magic = MAPPED_KENLM_MAGIC.toByteArray(Charsets.US_ASCII)
    baos.write(magic)
    baos.write(ByteArray(MAPPED_KENLM_MAGIC_LENGTH - magic.size))
    baos.write(mappedIntToLe(KenLmModelType.TRIE.id))
    baos.write(mappedIntToLe(1))
    baos.write(byteArrayOf(1))
    baos.write(byteArrayOf(1))
    baos.write(byteArrayOf(0))
    baos.write(byteArrayOf(1))
    baos.write(mappedLongToLe(1L))
    baos.write(body)
    return baos.toByteArray()
}

private fun mappedIntToLe(value: Int): ByteArray = byteArrayOf(
    (value and 0xFF).toByte(),
    ((value shr 8) and 0xFF).toByte(),
    ((value shr 16) and 0xFF).toByte(),
    ((value shr 24) and 0xFF).toByte(),
)

private fun mappedLongToLe(value: Long): ByteArray {
    val out = ByteArray(8)
    for (i in 0 until 8) {
        out[i] = ((value shr (i * 8)) and 0xFFL).toByte()
    }
    return out
}
