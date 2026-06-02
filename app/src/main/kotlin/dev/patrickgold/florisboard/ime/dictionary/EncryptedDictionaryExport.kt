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

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * docs/archive/SWIFTKEY_PARITY_ROADMAP_2026-05-17 §A3 — portable encrypted-blob
 * envelope for personal-dictionary export.
 *
 * The Floris personal dictionary already encrypts its on-disk Room
 * database with SQLCipher (`PersonalDictionaryEncryptionTest` pins
 * that contract), but the SQLCipher passphrase is held in Android
 * Keystore and is **intentionally non-portable** — that's a feature
 * because it keeps a stolen device backup useless to the thief. The
 * downside is users can't carry their learned vocabulary to a
 * different phone through any user-chosen channel (Syncthing,
 * USB-drag, ProtonDrive, etc.) without first decrypting to plaintext
 * CSV, which then sits on the source filesystem.
 *
 * This envelope fills that gap with a portable AES-256-GCM blob keyed
 * by a passphrase the user types into Settings. The passphrase is
 * derived through PBKDF2-HMAC-SHA-256 at a deliberately-stiff
 * iteration count (600 000 — the OWASP 2025 recommendation for
 * PBKDF2-SHA256) so even a moderately-weak passphrase is expensive to
 * brute-force, and the iteration count is baked into the blob so
 * future bumps decrypt old exports unchanged.
 *
 * The plaintext payload is just bytes — typically the same
 * newline-delimited semicolon-key=value CSV `UserDictionaryDatabase
 * .exportCombinedList` already emits — so a decrypted blob can ride
 * straight back into the existing legacy import path on the
 * destination device.
 *
 * Pure-Kotlin / JVM stdlib only (no Android dependency, no new
 * library), so this is round-trippable in unit tests.
 *
 * ## Wire format (binary, big-endian)
 *
 * ```
 * offset  size  field
 * 0       6     magic = "SFEXP1" (ASCII)
 * 6       2     version (uint16; v1 = 0x0001)
 * 8       16    PBKDF2 salt (random per export)
 * 24      12    AES-GCM nonce / IV (random per export)
 * 36      4     PBKDF2 iteration count (uint32, currently 600 000)
 * 40      4     plaintext payload byte-length (uint32, sanity bound)
 * 44      …     ciphertext + 16-byte GCM auth tag (single sealed block)
 * ```
 *
 * The 4-byte plaintext-length field is **advisory** (the GCM auth
 * tag is the actual integrity gate) but lets the decoder reject an
 * oversized blob before decrypting — defensive against an attacker
 * who replaces a real export with a 1 GiB random blob to OOM the
 * destination device.
 *
 * ## Why GCM and not ChaCha20-Poly1305 or AES-SIV
 *
 * Android 8.0 (minSdk 26) ships AES-GCM through the Conscrypt
 * provider; no shim needed. ChaCha20-Poly1305 needs API 28+ and a
 * `Cipher.getInstance("ChaCha20-Poly1305")` shim for older API
 * levels; AES-SIV is not in the platform AndroidKeyStore provider at
 * all. AES-256-GCM with a unique IV per encryption is the smallest
 * correct choice on this min-SDK floor.
 */
object EncryptedDictionaryExport {

    /** ASCII magic prefix for envelope detection. */
    val MAGIC: ByteArray = "SFEXP1".toByteArray(Charsets.US_ASCII)

    /** Current envelope version. Bumping requires a parser-side migration. */
    const val CURRENT_VERSION: Int = 1

    /** Header size, in bytes, before the ciphertext block. */
    const val HEADER_SIZE: Int = 6 + 2 + 16 + 12 + 4 + 4

    /** PBKDF2 salt length, in bytes. NIST SP 800-132 §5.1 floor is 16. */
    const val SALT_BYTES: Int = 16

    /** AES-GCM nonce length, in bytes. 12 is the GCM standard. */
    const val GCM_NONCE_BYTES: Int = 12

    /** GCM authentication tag length, in bits. 128 is the GCM standard. */
    const val GCM_TAG_BITS: Int = 128

    /**
     * PBKDF2-HMAC-SHA-256 iteration count for v1 exports. Matches the
     * 2025 OWASP recommendation. Baked into every header so older
     * blobs keep decrypting after future bumps.
     */
    const val DEFAULT_PBKDF2_ITERS: Int = 600_000

    /** Floor for an accepted PBKDF2 iteration count (matches the encrypt-side minimum). */
    const val MIN_PBKDF2_ITERS: Int = 100_000

    /**
     * Hard ceiling for the iteration count read from an untrusted envelope header.
     * Without it, a crafted `.sfexp` could request ~2 billion rounds and pin a CPU
     * core for minutes before the GCM tag is ever checked (a trivial decrypt DoS).
     * 10M leaves ample headroom for future security bumps from [DEFAULT_PBKDF2_ITERS].
     */
    const val MAX_PBKDF2_ITERS: Int = 10_000_000

    /**
     * Hard cap on plaintext payload size so an attacker-controlled
     * blob can't force a multi-megabyte allocation on decrypt. 16 MiB
     * mirrors the `DictionaryImporter.MAX_IMPORT_FILE_BYTES` ceiling
     * so a legitimate full-dictionary export still fits comfortably.
     */
    const val MAX_PAYLOAD_BYTES: Int = 16 * 1024 * 1024

    private const val AES_KEY_BITS: Int = 256
    private const val PBKDF2_ALGORITHM: String = "PBKDF2WithHmacSHA256"
    private const val AES_GCM_TRANSFORMATION: String = "AES/GCM/NoPadding"

    /**
     * Encrypt [plaintext] under [passphrase], returning the envelope
     * bytes ready to write to a user-supplied URI.
     *
     * [iterations] is exposed for tests / future hardening — in
     * production the call site should pass [DEFAULT_PBKDF2_ITERS].
     * [secureRandom] is injectable for deterministic tests; production
     * callers should pass the platform default.
     */
    fun encrypt(
        plaintext: ByteArray,
        passphrase: CharArray,
        iterations: Int = DEFAULT_PBKDF2_ITERS,
        secureRandom: SecureRandom = SecureRandom(),
    ): ByteArray {
        require(plaintext.size <= MAX_PAYLOAD_BYTES) {
            "Plaintext exceeds the ${MAX_PAYLOAD_BYTES / (1024 * 1024)} MiB safety limit."
        }
        require(passphrase.isNotEmpty()) {
            "Passphrase must not be empty."
        }
        require(iterations in MIN_PBKDF2_ITERS..MAX_PBKDF2_ITERS) {
            "PBKDF2 iteration count must be within $MIN_PBKDF2_ITERS..$MAX_PBKDF2_ITERS (OWASP 2025 floor + DoS ceiling)."
        }
        val salt = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }
        val nonce = ByteArray(GCM_NONCE_BYTES).also { secureRandom.nextBytes(it) }
        val key = deriveKey(passphrase, salt, iterations)
        val ciphertext = try {
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
            cipher.doFinal(plaintext)
        } finally {
            // Best-effort scrub of the derived key; SecretKeySpec keeps
            // its own copy but the local `key` byte[] is the most
            // likely thing to live in a heap dump.
            Arrays.fill(key, 0)
        }
        val envelope = ByteBuffer.allocate(HEADER_SIZE + ciphertext.size).order(ByteOrder.BIG_ENDIAN)
        envelope.put(MAGIC)
        envelope.putShort(CURRENT_VERSION.toShort())
        envelope.put(salt)
        envelope.put(nonce)
        envelope.putInt(iterations)
        envelope.putInt(plaintext.size)
        envelope.put(ciphertext)
        return envelope.array()
    }

    /**
     * Decrypt [envelope] using [passphrase]. Throws
     * [EncryptedDictionaryException] for every recoverable failure
     * mode so the call site can show one consistent "wrong passphrase
     * or corrupt file" copy without inspecting cause types.
     */
    fun decrypt(
        envelope: ByteArray,
        passphrase: CharArray,
    ): ByteArray {
        if (envelope.size < HEADER_SIZE) {
            throw EncryptedDictionaryException(FailureReason.TRUNCATED)
        }
        if (passphrase.isEmpty()) {
            throw EncryptedDictionaryException(FailureReason.BAD_PASSPHRASE)
        }
        val buf = ByteBuffer.wrap(envelope).order(ByteOrder.BIG_ENDIAN)
        val magic = ByteArray(MAGIC.size).also { buf.get(it) }
        if (!magic.contentEquals(MAGIC)) {
            throw EncryptedDictionaryException(FailureReason.NOT_AN_ENVELOPE)
        }
        val version = buf.short.toInt() and 0xFFFF
        if (version != CURRENT_VERSION) {
            // Forward-compat reject — newer blob versions need a future
            // decoder. Distinct from corruption so the UI can render
            // "this file was made by a newer version of SwiftFloris".
            throw EncryptedDictionaryException(FailureReason.UNSUPPORTED_VERSION)
        }
        val salt = ByteArray(SALT_BYTES).also { buf.get(it) }
        val nonce = ByteArray(GCM_NONCE_BYTES).also { buf.get(it) }
        val iterations = buf.int
        if (iterations < MIN_PBKDF2_ITERS || iterations > MAX_PBKDF2_ITERS) {
            // Reject before deriveKey() so an attacker-chosen iteration count
            // cannot turn a mere import attempt into a multi-minute PBKDF2 hang.
            throw EncryptedDictionaryException(FailureReason.CORRUPT_HEADER)
        }
        val plaintextLen = buf.int
        if (plaintextLen < 0 || plaintextLen > MAX_PAYLOAD_BYTES) {
            throw EncryptedDictionaryException(FailureReason.OVERSIZED)
        }
        // Remaining bytes = ciphertext + 16-byte GCM tag. The tag's
        // size is encoded in GCM_TAG_BITS, so plaintext-size + 16 is
        // the expected ciphertext length.
        val expectedCipherLen = plaintextLen + (GCM_TAG_BITS / 8)
        val remaining = envelope.size - HEADER_SIZE
        if (remaining != expectedCipherLen) {
            throw EncryptedDictionaryException(FailureReason.TRUNCATED)
        }
        val ciphertext = ByteArray(remaining).also { buf.get(it) }
        val key = deriveKey(passphrase, salt, iterations)
        return try {
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
            cipher.doFinal(ciphertext)
        } catch (e: AEADBadTagException) {
            // AES-GCM tag mismatch — either the passphrase is wrong or
            // the ciphertext was modified. The two cases are
            // cryptographically indistinguishable, so we collapse them
            // to one user-facing reason.
            throw EncryptedDictionaryException(FailureReason.BAD_PASSPHRASE, e)
        } catch (e: GeneralSecurityException) {
            throw EncryptedDictionaryException(FailureReason.CORRUPT_HEADER, e)
        } finally {
            Arrays.fill(key, 0)
        }
    }

    /**
     * True iff [candidate] starts with the SFEXP1 magic. Lets the
     * import flow byte-sniff between an encrypted envelope and a
     * plain CSV / JSON / XML payload before asking the user for a
     * passphrase they wouldn't otherwise need.
     */
    fun isEncryptedEnvelope(candidate: ByteArray): Boolean {
        if (candidate.size < MAGIC.size) return false
        for (i in MAGIC.indices) {
            if (candidate[i] != MAGIC[i]) return false
        }
        return true
    }

    private fun deriveKey(
        passphrase: CharArray,
        salt: ByteArray,
        iterations: Int,
    ): ByteArray {
        val keySpec = PBEKeySpec(passphrase, salt, iterations, AES_KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(keySpec).encoded
        } finally {
            keySpec.clearPassword()
        }
    }

    /**
     * Per-blob failure reason. The codec collapses cryptographic
     * indistinguishability (wrong passphrase vs. tampered ciphertext)
     * into a single user-facing reason so the UI can show one honest
     * line of copy instead of leaking which case it actually was.
     */
    enum class FailureReason {
        /** The blob was shorter than the fixed envelope header. */
        TRUNCATED,

        /** The first 6 bytes were not `SFEXP1`. */
        NOT_AN_ENVELOPE,

        /** The envelope's version field was a value this decoder
         *  doesn't know how to read (probably a newer SwiftFloris). */
        UNSUPPORTED_VERSION,

        /** Header parsed but a field is structurally invalid (iters
         *  ≤ 0, negative plaintext length, etc.) — should be rare in
         *  practice; treated like corruption. */
        CORRUPT_HEADER,

        /** Header declares a plaintext size that exceeds [MAX_PAYLOAD_BYTES]. */
        OVERSIZED,

        /** AES-GCM authentication failed. Either the passphrase is
         *  wrong or the ciphertext was modified. Cryptographically
         *  indistinguishable — surfaced as one reason. */
        BAD_PASSPHRASE,
    }
}

/**
 * Thrown for every recoverable failure mode of
 * [EncryptedDictionaryExport]. The single class with a [reason]
 * enum keeps the call site's `when` exhaustive without needing to
 * pattern-match on cause types.
 */
class EncryptedDictionaryException(
    val reason: EncryptedDictionaryExport.FailureReason,
    cause: Throwable? = null,
) : RuntimeException("Encrypted dictionary export failure: $reason", cause)
