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

package dev.patrickgold.florisboard.ime.dictionary

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.security.SecureRandom

class EncryptedDictionaryExportTest : io.kotest.core.spec.style.FunSpec({

    // Deterministic SecureRandom seed so the salt + nonce don't drift
    // between runs — keeps the envelope-size assertions pinnable.
    fun fixedRandom(seed: Long = 0xDEADBEEF): SecureRandom = SecureRandom().apply {
        setSeed(seed)
    }

    // Use a low iteration count in tests to keep PBKDF2 cheap (real
    // builds ship at 600k; the codec rejects anything below 100k, which
    // is the lowest count we exercise to stay within the OWASP floor).
    val testIterations = 100_000

    test("round-trip recovers the exact plaintext") {
        val plaintext = "w=hello;f=200;l=en\nw=world;f=150;l=en\n".toByteArray(Charsets.UTF_8)
        val envelope = EncryptedDictionaryExport.encrypt(
            plaintext = plaintext,
            passphrase = "correct horse battery staple".toCharArray(),
            iterations = testIterations,
            secureRandom = fixedRandom(),
        )

        val decrypted = EncryptedDictionaryExport.decrypt(
            envelope = envelope,
            passphrase = "correct horse battery staple".toCharArray(),
        )

        decrypted shouldBe plaintext
    }

    test("decrypt with the wrong passphrase fails with BAD_PASSPHRASE (not a generic error)") {
        val envelope = EncryptedDictionaryExport.encrypt(
            plaintext = byteArrayOf(1, 2, 3, 4),
            passphrase = "right".toCharArray(),
            iterations = testIterations,
            secureRandom = fixedRandom(),
        )

        val ex = shouldThrow<EncryptedDictionaryException> {
            EncryptedDictionaryExport.decrypt(envelope, "wrong".toCharArray())
        }
        ex.reason shouldBe EncryptedDictionaryExport.FailureReason.BAD_PASSPHRASE
    }

    test("decrypt of a tampered ciphertext byte fails with BAD_PASSPHRASE (cryptographic indistinguishability)") {
        val envelope = EncryptedDictionaryExport.encrypt(
            plaintext = byteArrayOf(1, 2, 3, 4),
            passphrase = "p".toCharArray(),
            iterations = testIterations,
            secureRandom = fixedRandom(),
        )
        // Flip a bit in the ciphertext (last byte is inside the GCM
        // auth tag, but any tampered byte in the AEAD payload should
        // fail the same way).
        envelope[envelope.size - 1] = (envelope[envelope.size - 1].toInt() xor 0x01).toByte()

        val ex = shouldThrow<EncryptedDictionaryException> {
            EncryptedDictionaryExport.decrypt(envelope, "p".toCharArray())
        }
        ex.reason shouldBe EncryptedDictionaryExport.FailureReason.BAD_PASSPHRASE
    }

    test("decrypt of a truncated envelope reports TRUNCATED before touching the cipher") {
        val ex = shouldThrow<EncryptedDictionaryException> {
            EncryptedDictionaryExport.decrypt(byteArrayOf(1, 2, 3), "p".toCharArray())
        }
        ex.reason shouldBe EncryptedDictionaryExport.FailureReason.TRUNCATED
    }

    test("decrypt of a non-envelope blob reports NOT_AN_ENVELOPE") {
        val garbage = ByteArray(EncryptedDictionaryExport.HEADER_SIZE + 16)
        // garbage is all zeros — first 6 bytes != "SFEXP1"
        val ex = shouldThrow<EncryptedDictionaryException> {
            EncryptedDictionaryExport.decrypt(garbage, "p".toCharArray())
        }
        ex.reason shouldBe EncryptedDictionaryExport.FailureReason.NOT_AN_ENVELOPE
    }

    test("decrypt of a future-version envelope reports UNSUPPORTED_VERSION") {
        val envelope = EncryptedDictionaryExport.encrypt(
            plaintext = byteArrayOf(1),
            passphrase = "p".toCharArray(),
            iterations = testIterations,
            secureRandom = fixedRandom(),
        )
        // Overwrite the version field (bytes 6..7) with v2.
        envelope[6] = 0
        envelope[7] = 2

        val ex = shouldThrow<EncryptedDictionaryException> {
            EncryptedDictionaryExport.decrypt(envelope, "p".toCharArray())
        }
        ex.reason shouldBe EncryptedDictionaryExport.FailureReason.UNSUPPORTED_VERSION
    }

    test("decrypt of an envelope claiming an oversized plaintext reports OVERSIZED") {
        val envelope = EncryptedDictionaryExport.encrypt(
            plaintext = byteArrayOf(1),
            passphrase = "p".toCharArray(),
            iterations = testIterations,
            secureRandom = fixedRandom(),
        )
        // Overwrite the plaintext-length field (bytes 40..43) with a
        // value past the safety limit. The decoder must refuse before
        // touching the cipher — defending against an attacker
        // replacing a real export with a 1 GiB blob to OOM the
        // destination device.
        val oversized = EncryptedDictionaryExport.MAX_PAYLOAD_BYTES + 1
        envelope[40] = (oversized ushr 24).toByte()
        envelope[41] = (oversized ushr 16).toByte()
        envelope[42] = (oversized ushr 8).toByte()
        envelope[43] = oversized.toByte()

        val ex = shouldThrow<EncryptedDictionaryException> {
            EncryptedDictionaryExport.decrypt(envelope, "p".toCharArray())
        }
        ex.reason shouldBe EncryptedDictionaryExport.FailureReason.OVERSIZED
    }

    test("decrypt of an envelope with a negative plaintext length reports OVERSIZED") {
        val envelope = EncryptedDictionaryExport.encrypt(
            plaintext = byteArrayOf(1),
            passphrase = "p".toCharArray(),
            iterations = testIterations,
            secureRandom = fixedRandom(),
        )
        // Set the plaintext length to -1 via two's complement.
        envelope[40] = 0xFF.toByte()
        envelope[41] = 0xFF.toByte()
        envelope[42] = 0xFF.toByte()
        envelope[43] = 0xFF.toByte()

        val ex = shouldThrow<EncryptedDictionaryException> {
            EncryptedDictionaryExport.decrypt(envelope, "p".toCharArray())
        }
        ex.reason shouldBe EncryptedDictionaryExport.FailureReason.OVERSIZED
    }

    test("decrypt of an envelope with iters=0 reports CORRUPT_HEADER") {
        val envelope = EncryptedDictionaryExport.encrypt(
            plaintext = byteArrayOf(1),
            passphrase = "p".toCharArray(),
            iterations = testIterations,
            secureRandom = fixedRandom(),
        )
        // Overwrite the iter count field (bytes 36..39) with 0.
        envelope[36] = 0
        envelope[37] = 0
        envelope[38] = 0
        envelope[39] = 0

        val ex = shouldThrow<EncryptedDictionaryException> {
            EncryptedDictionaryExport.decrypt(envelope, "p".toCharArray())
        }
        ex.reason shouldBe EncryptedDictionaryExport.FailureReason.CORRUPT_HEADER
    }

    test("encrypt rejects an empty passphrase") {
        shouldThrow<IllegalArgumentException> {
            EncryptedDictionaryExport.encrypt(
                plaintext = byteArrayOf(1),
                passphrase = CharArray(0),
                iterations = testIterations,
            )
        }
    }

    test("encrypt rejects an iteration count below the OWASP 2025 floor") {
        shouldThrow<IllegalArgumentException> {
            EncryptedDictionaryExport.encrypt(
                plaintext = byteArrayOf(1),
                passphrase = "p".toCharArray(),
                iterations = 50_000,
            )
        }
    }

    test("encrypt rejects a plaintext past the safety cap") {
        shouldThrow<IllegalArgumentException> {
            EncryptedDictionaryExport.encrypt(
                plaintext = ByteArray(EncryptedDictionaryExport.MAX_PAYLOAD_BYTES + 1),
                passphrase = "p".toCharArray(),
                iterations = testIterations,
            )
        }
    }

    test("envelope size matches HEADER_SIZE + plaintextLength + 16-byte GCM tag") {
        val plaintext = ByteArray(123)
        val envelope = EncryptedDictionaryExport.encrypt(
            plaintext = plaintext,
            passphrase = "p".toCharArray(),
            iterations = testIterations,
            secureRandom = fixedRandom(),
        )
        envelope.size shouldBe (EncryptedDictionaryExport.HEADER_SIZE + plaintext.size + 16)
    }

    test("isEncryptedEnvelope byte-sniff distinguishes SFEXP1 from a plain CSV / JSON / XML payload") {
        val envelope = EncryptedDictionaryExport.encrypt(
            plaintext = byteArrayOf(1),
            passphrase = "p".toCharArray(),
            iterations = testIterations,
            secureRandom = fixedRandom(),
        )
        EncryptedDictionaryExport.isEncryptedEnvelope(envelope) shouldBe true
        // Real plain CSV first bytes.
        EncryptedDictionaryExport.isEncryptedEnvelope("w=hello;f=200".toByteArray()) shouldBe false
        // JSON.
        EncryptedDictionaryExport.isEncryptedEnvelope("{\"predictions\":[]}".toByteArray()) shouldBe false
        // XML.
        EncryptedDictionaryExport.isEncryptedEnvelope("<?xml ...".toByteArray()) shouldBe false
        // Too short to even contain the magic.
        EncryptedDictionaryExport.isEncryptedEnvelope(byteArrayOf(1, 2, 3)) shouldBe false
        EncryptedDictionaryExport.isEncryptedEnvelope(byteArrayOf()) shouldBe false
    }

    test("two encrypts of the same plaintext under the same passphrase produce different envelopes (salt+nonce randomness)") {
        val plaintext = "same plaintext".toByteArray(Charsets.UTF_8)
        val passphrase = "p".toCharArray()
        val a = EncryptedDictionaryExport.encrypt(plaintext, passphrase, testIterations)
        val b = EncryptedDictionaryExport.encrypt(plaintext, passphrase, testIterations)
        a.contentEquals(b) shouldBe false
        // But both decrypt back to the same plaintext.
        EncryptedDictionaryExport.decrypt(a, passphrase) shouldBe plaintext
        EncryptedDictionaryExport.decrypt(b, passphrase) shouldBe plaintext
    }
})
