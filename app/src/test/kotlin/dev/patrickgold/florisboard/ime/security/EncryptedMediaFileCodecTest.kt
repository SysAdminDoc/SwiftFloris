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

package dev.patrickgold.florisboard.ime.security

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.security.GeneralSecurityException

class EncryptedMediaFileCodecTest : FunSpec({
    val key = ByteArray(32) { index -> (index + 1).toByte() }

    fun tempDir(): File = Files.createTempDirectory("encrypted-clipboard-media").toFile()

    test("streaming media encryption round-trips and does not expose plaintext bytes") {
        val dir = tempDir()
        try {
            val plaintext = ByteArray(EncryptedMediaFileCodec.HeaderBytes * 4096) { index ->
                (index * 17).toByte()
            }
            val encrypted = File(dir, "7")
            val restored = File(dir, "restored")

            EncryptedMediaFileCodec.encrypt(
                input = ByteArrayInputStream(plaintext),
                target = encrypted,
                key = key,
                maxPlaintextBytes = plaintext.size.toLong(),
            ) shouldBe plaintext.size.toLong()

            EncryptedMediaFileCodec.isEncrypted(encrypted) shouldBe true
            EncryptedMediaFileCodec.inspect(
                encrypted,
                maxPlaintextBytes = plaintext.size.toLong(),
            ).plaintextBytes shouldBe plaintext.size.toLong()
            EncryptedMediaFileCodec.decrypt(
                source = encrypted,
                target = restored,
                key = key,
                maxPlaintextBytes = plaintext.size.toLong(),
            ) shouldBe plaintext.size.toLong()

            restored.readBytes().contentEquals(plaintext) shouldBe true
            encrypted.readBytes().contentEquals(plaintext) shouldBe false
        } finally {
            dir.deleteRecursively()
        }
    }

    test("authentication failure leaves the previous plaintext target untouched") {
        val dir = tempDir()
        try {
            val encrypted = File(dir, "7")
            val restored = File(dir, "restored").apply { writeText("last valid preview") }
            EncryptedMediaFileCodec.encrypt(
                input = ByteArrayInputStream("clipboard image bytes".toByteArray()),
                target = encrypted,
                key = key,
                maxPlaintextBytes = 1024L,
            )
            encrypted.writeBytes(encrypted.readBytes().also { bytes ->
                bytes[EncryptedMediaFileCodec.HeaderBytes] =
                    (bytes[EncryptedMediaFileCodec.HeaderBytes].toInt() xor 0x01).toByte()
            })

            shouldThrow<GeneralSecurityException> {
                EncryptedMediaFileCodec.decrypt(
                    source = encrypted,
                    target = restored,
                    key = key,
                    maxPlaintextBytes = 1024L,
                )
            }
            restored.readText() shouldBe "last valid preview"
        } finally {
            dir.deleteRecursively()
        }
    }

    test("size-limit failures do not leave a staged target") {
        val dir = tempDir()
        try {
            val target = File(dir, "7")
            shouldThrow<IllegalStateException> {
                EncryptedMediaFileCodec.encrypt(
                    input = ByteArrayInputStream(ByteArray(1025)),
                    target = target,
                    key = key,
                    maxPlaintextBytes = 1024L,
                )
            }
            target.exists() shouldBe false
            dir.listFiles().orEmpty().none { it.name.endsWith(".tmp") } shouldBe true
        } finally {
            dir.deleteRecursively()
        }
    }
})
