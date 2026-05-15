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

class SealedBoxCryptoTest : FunSpec({
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
})
