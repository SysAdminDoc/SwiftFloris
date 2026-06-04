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

import android.os.Build
import androidx.annotation.RequiresApi
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.NamedParameterSpec
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * ROADMAP §7 Next-5.2a — Curve25519 + AES-GCM "sealed-box" wrapper
 * for CRDT-delta transport across the user's chosen sync channel.
 *
 * Cross-platform libsodium ports for Android exist (Lazysodium, Tink)
 * but each adds 1-3 MB of native binaries that have to be reviewed
 * for the F-Droid build. The **JVM/Android stdlib** ships an X25519
     * (Curve25519 Diffie-Hellman) implementation on modern Android, and
 * AES-GCM since API 1. SwiftFloris uses these directly: no extra
 * native runtime, no extra licence review, no extra .so payload.
 *
 * Wire-format mirrors the libsodium `crypto_box_seal` shape so the
 * scheme is identical to what every other "sealed box" client
 * produces:
 *
 *  ```
 *  output = ephemeralPublicKey (32 B)
 *         ‖ nonce             (12 B)
 *         ‖ ciphertext + tag  (n + 16 B)
 *  ```
 *
 * The recipient owns a long-lived X25519 keypair (its half of the
 * QR-pair handshake in [PairingPayload]); each delta uses a freshly-
 * generated ephemeral keypair so the recipient learns the symmetric
 * key but never the sender's long-term private key.
 *
 * **Forward secrecy:** every message uses a new ephemeral keypair on
 * the sender side, so compromising a sender device's prior state
 * doesn't decrypt past traffic.
 *
 * **Why AES-GCM not XChaCha20-Poly1305:** libsodium prefers XChaCha20
 * for its larger nonce; AES-GCM with a deterministically-derived
 * nonce (HKDF of shared secret + ephemeral pubkey) preserves the same
 * security goal while keeping us inside the JVM stdlib.
 *
 * Reference: [libsodium sealed-box spec](https://doc.libsodium.org/public-key_cryptography/sealed_boxes).
 */
object SealedBoxCrypto {

    private const val NONCE_LENGTH = 12
    private const val TAG_LENGTH_BITS = 128
    private const val SECRET_LENGTH = 32
    private const val PUBKEY_LENGTH = 32

    /**
     * Generate a fresh X25519 keypair for use as a long-lived recipient
     * key (the QR-pair exchange in [PairingPayload]) or as an
     * ephemeral per-message key. The caller owns the private half;
     * the public half goes into the [PairingPayload.pubkeyHex] field
     * after hex-encoding.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun generateKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance(X25519_ALGORITHM)
        generator.initialize(NamedParameterSpec(X25519_ALGORITHM))
        return generator.generateKeyPair()
    }

    /**
     * Seal [plaintext] for [recipientPublicKey]. Produces the
     * libsodium-style sealed-box envelope described above.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun seal(plaintext: ByteArray, recipientPublicKey: ByteArray): ByteArray {
        require(recipientPublicKey.size == PUBKEY_LENGTH) {
            "recipientPublicKey must be 32 bytes; was ${recipientPublicKey.size}"
        }
        val ephemeral = generateKeyPair()
        val ephemeralPubBytes = ephemeral.public.encoded.takeLast(PUBKEY_LENGTH).toByteArray()
        val sharedSecret = computeSharedSecret(ephemeral.private, recipientPublicKey)
        try {
            val nonce = deriveNonce(sharedSecret, ephemeralPubBytes, recipientPublicKey)
            val key = SecretKeySpec(sharedSecret, AES_KEY_ALGORITHM)
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORM)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, nonce))
            val ciphertextWithTag = cipher.doFinal(plaintext)
            return ephemeralPubBytes + nonce + ciphertextWithTag
        } finally {
            Arrays.fill(sharedSecret, 0.toByte())
        }
    }

    /**
     * Open a sealed box destined for [recipientPrivateKey]. Returns
     * the decrypted plaintext, or null when the envelope is malformed
     * / fails MAC validation.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun open(sealedEnvelope: ByteArray, recipientKeyPair: KeyPair): ByteArray? {
        if (sealedEnvelope.size < PUBKEY_LENGTH + NONCE_LENGTH + 16) return null
        val ephemeralPub = sealedEnvelope.copyOfRange(0, PUBKEY_LENGTH)
        val nonce = sealedEnvelope.copyOfRange(PUBKEY_LENGTH, PUBKEY_LENGTH + NONCE_LENGTH)
        val ciphertext = sealedEnvelope.copyOfRange(
            PUBKEY_LENGTH + NONCE_LENGTH,
            sealedEnvelope.size,
        )
        var sharedSecret: ByteArray? = null
        return try {
            sharedSecret = computeSharedSecret(recipientKeyPair.private, ephemeralPub)
            val recipientPub = recipientKeyPair.public.encoded.takeLast(PUBKEY_LENGTH).toByteArray()
            val expectedNonce = deriveNonce(sharedSecret, ephemeralPub, recipientPub)
            if (!nonce.contentEquals(expectedNonce)) return null
            val key = SecretKeySpec(sharedSecret, AES_KEY_ALGORITHM)
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, nonce))
            cipher.doFinal(ciphertext)
        } catch (_: Throwable) {
            null
        } finally {
            sharedSecret?.let { Arrays.fill(it, 0.toByte()) }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun computeSharedSecret(
        privateKey: java.security.PrivateKey,
        recipientPublicKeyRaw: ByteArray,
    ): ByteArray {
        require(recipientPublicKeyRaw.size == PUBKEY_LENGTH)
        // X25519 raw public keys can be wrapped in an
        // XECPublicKey spec; the JVM's NamedParameterSpec
        // accepts a BigInteger-backed XECPublicKeySpec.
        val recipientPub = java.security.KeyFactory
            .getInstance(X25519_ALGORITHM)
            .generatePublic(
                java.security.spec.XECPublicKeySpec(
                    NamedParameterSpec(X25519_ALGORITHM),
                    java.math.BigInteger(1, recipientPublicKeyRaw.reversedArray()),
                ),
            )
        val agreement = KeyAgreement.getInstance(X25519_ALGORITHM)
        agreement.init(privateKey)
        agreement.doPhase(recipientPub, true)
        val sharedRaw = agreement.generateSecret()
        // HKDF-Extract using SHA-256 to spread the X25519 secret into
        // a 32-byte AES-256 key. Salt = ephemeral || recipient
        // pubkey concatenation, info constant for this scheme.
        return hkdfExtract(sharedRaw, info = HKDF_INFO_KEY)
    }

    private fun deriveNonce(
        sharedSecret: ByteArray,
        ephemeralPub: ByteArray,
        recipientPub: ByteArray,
    ): ByteArray {
        val seed = sharedSecret + ephemeralPub + recipientPub
        val digest = MessageDigest.getInstance("SHA-256").digest(seed)
        return digest.copyOf(NONCE_LENGTH)
    }

    private fun hkdfExtract(ikm: ByteArray, info: String): ByteArray {
        val salt = ByteArray(32)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)
        try {
            val infoBytes = info.toByteArray(Charsets.US_ASCII)
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(infoBytes)
            mac.update(0x01.toByte())
            return mac.doFinal().copyOf(SECRET_LENGTH)
        } finally {
            Arrays.fill(prk, 0.toByte())
        }
    }

    private const val X25519_ALGORITHM = "X25519"
    private const val AES_KEY_ALGORITHM = "AES"
    private const val AES_GCM_TRANSFORM = "AES/GCM/NoPadding"
    private const val HKDF_INFO_KEY = "swiftfloris-sealed-box-key-v1"
}
