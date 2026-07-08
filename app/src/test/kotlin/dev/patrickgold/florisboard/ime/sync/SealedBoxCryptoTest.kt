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

package dev.patrickgold.florisboard.ime.sync

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.spec.NamedParameterSpec
import java.security.spec.XECPrivateKeySpec
import java.security.spec.XECPublicKeySpec

private const val VECTOR_PLAINTEXT = "SwiftFloris sync vector v1"
private const val VECTOR_EPHEMERAL_PRIVATE_HEX =
    "77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a"
private const val VECTOR_EPHEMERAL_PUBLIC_HEX =
    "8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a"
private const val VECTOR_RECIPIENT_PRIVATE_HEX =
    "5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb"
private const val VECTOR_RECIPIENT_PUBLIC_HEX =
    "de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f"
private const val VECTOR_NONCE_HEX = "1018f7d42cf3af02ed00f8f9"
private const val VECTOR_ENVELOPE_HEX =
    "8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a" +
        "1018f7d42cf3af02ed00f8f9" +
        "0ff1ef58e1b648002adfa0bf0fbf5d995c5e9a20611c362944a65b21efcea29ac9fb23b70bd6e0b7decf"

private fun fixedX25519KeyPair(
    privateScalarHex: String,
    publicKeyHex: String,
): KeyPair {
    val keyFactory = KeyFactory.getInstance("X25519")
    val publicRaw = publicKeyHex.hexToBytes()
    val publicKey = keyFactory.generatePublic(
        XECPublicKeySpec(
            NamedParameterSpec("X25519"),
            BigInteger(1, publicRaw.reversedArray()),
        ),
    )
    val privateKey = keyFactory.generatePrivate(
        XECPrivateKeySpec(
            NamedParameterSpec("X25519"),
            privateScalarHex.hexToBytes(),
        ),
    )
    return KeyPair(publicKey, privateKey)
}

private fun KeyPair.rawPublicHex(): String =
    public.encoded.takeLast(32).toByteArray().toHex()

private fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "hex must contain an even number of chars" }
    return chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}

private fun ByteArray.toHex(): String =
    joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }

class SealedBoxCryptoTest : FunSpec({
    test("v1 envelope constants stay explicit") {
        SealedBoxCrypto.ENVELOPE_SCHEMA_VERSION shouldBe 1
        SealedBoxCrypto.ENVELOPE_HEADER_LENGTH shouldBe 44
        SealedBoxCrypto.MIN_ENVELOPE_LENGTH shouldBe 60
    }

    test("deterministic v1 envelope vector stays stable") {
        val recipient = fixedX25519KeyPair(
            privateScalarHex = VECTOR_RECIPIENT_PRIVATE_HEX,
            publicKeyHex = VECTOR_RECIPIENT_PUBLIC_HEX,
        )
        val ephemeral = fixedX25519KeyPair(
            privateScalarHex = VECTOR_EPHEMERAL_PRIVATE_HEX,
            publicKeyHex = VECTOR_EPHEMERAL_PUBLIC_HEX,
        )
        val recipientPubBytes = VECTOR_RECIPIENT_PUBLIC_HEX.hexToBytes()

        recipient.rawPublicHex() shouldBe VECTOR_RECIPIENT_PUBLIC_HEX
        ephemeral.rawPublicHex() shouldBe VECTOR_EPHEMERAL_PUBLIC_HEX

        val envelope = SealedBoxCrypto.sealWithEphemeralForTest(
            plaintext = VECTOR_PLAINTEXT.toByteArray(Charsets.UTF_8),
            recipientPublicKey = recipientPubBytes,
            ephemeral = ephemeral,
        )

        envelope.toHex() shouldBe VECTOR_ENVELOPE_HEX
        envelope.copyOfRange(0, 32).toHex() shouldBe VECTOR_EPHEMERAL_PUBLIC_HEX
        envelope.copyOfRange(32, 44).toHex() shouldBe VECTOR_NONCE_HEX
        envelope.size shouldBe 86
        SealedBoxCrypto.open(envelope, recipient)
            .shouldNotBeNull()
            .decodeToString() shouldBe VECTOR_PLAINTEXT
    }

    test("deterministic v1 envelope rejects nonce tampering without diagnostics") {
        val recipient = fixedX25519KeyPair(
            privateScalarHex = VECTOR_RECIPIENT_PRIVATE_HEX,
            publicKeyHex = VECTOR_RECIPIENT_PUBLIC_HEX,
        )
        val tampered = VECTOR_ENVELOPE_HEX.hexToBytes()
        tampered[32] = (tampered[32].toInt() xor 0x01).toByte()

        SealedBoxCrypto.open(tampered, recipient).shouldBeNull()
    }

    test("seal + open round-trip recovers the plaintext") {
        val recipient = SealedBoxCrypto.generateKeyPair()
        val recipientPubBytes = recipient.public.encoded.takeLast(32).toByteArray()
        val plaintext = "Hello, sealed box!".toByteArray()
        val envelope = SealedBoxCrypto.seal(plaintext, recipientPubBytes)
        val recovered = SealedBoxCrypto.open(envelope, recipient).shouldNotBeNull()
        recovered.contentEquals(plaintext) shouldBe true
    }

    test("envelope size = 32 (ephemeral pub) + 12 (nonce) + plaintext.size + 16 (tag)") {
        val recipient = SealedBoxCrypto.generateKeyPair()
        val recipientPubBytes = recipient.public.encoded.takeLast(32).toByteArray()
        val plaintext = ByteArray(100) { it.toByte() }
        val envelope = SealedBoxCrypto.seal(plaintext, recipientPubBytes)
        envelope.size shouldBe (32 + 12 + plaintext.size + 16)
    }

    test("opening with the wrong keypair returns null") {
        val alice = SealedBoxCrypto.generateKeyPair()
        val bob = SealedBoxCrypto.generateKeyPair()
        val alicePub = alice.public.encoded.takeLast(32).toByteArray()
        val envelope = SealedBoxCrypto.seal("for alice".toByteArray(), alicePub)
        SealedBoxCrypto.open(envelope, bob).shouldBeNull()
    }

    test("opening a truncated envelope returns null") {
        SealedBoxCrypto.open(ByteArray(10), SealedBoxCrypto.generateKeyPair()).shouldBeNull()
    }

    test("two seals of the same plaintext produce different envelopes (ephemeral key freshness)") {
        val recipient = SealedBoxCrypto.generateKeyPair()
        val recipientPubBytes = recipient.public.encoded.takeLast(32).toByteArray()
        val plaintext = "same plaintext".toByteArray()
        val envelopeA = SealedBoxCrypto.seal(plaintext, recipientPubBytes)
        val envelopeB = SealedBoxCrypto.seal(plaintext, recipientPubBytes)
        envelopeA.contentEquals(envelopeB) shouldBe false
    }

    test("seal rejects non-32-byte recipient public keys") {
        runCatching {
            SealedBoxCrypto.seal("x".toByteArray(), ByteArray(31))
        }.isFailure shouldBe true
    }

    test("low-order all-zero public keys never derive usable secrets") {
        val keyPair = SealedBoxCrypto.generateKeyPair()
        val lowOrderPublicKey = ByteArray(32)

        runCatching {
            SealedBoxCrypto.seal("x".toByteArray(), lowOrderPublicKey)
        }.isFailure shouldBe true
        runCatching {
            SealedBoxCrypto.deriveAuthenticationKey(keyPair.private, lowOrderPublicKey)
        }.isFailure shouldBe true
    }
})
