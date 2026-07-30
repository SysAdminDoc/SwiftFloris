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

package dev.patrickgold.florisboard.app.settings.advanced

import dev.patrickgold.florisboard.lib.io.AtomicFileWriter
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
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
 * Streaming, passphrase-protected envelope for portable backup ZIPs.
 *
 * Clipboard media can make an archive hundreds of MiB, so this codec never
 * materializes the ZIP or ciphertext as one byte array. Both encryption and
 * decryption publish through [AtomicFileWriter]; failed authentication leaves
 * any previous target untouched and removes unauthenticated staging bytes.
 *
 * Header bytes are supplied to AES-GCM as associated data. Changing the schema
 * version, flags, KDF cost, salt, nonce, or declared length therefore fails the
 * same authentication check as changing ciphertext.
 *
 * Wire format (big-endian):
 *
 * ```
 * 8 bytes  magic "SFBACKUP"
 * 2 bytes  schema version (1)
 * 2 bytes  flags
 * 16 bytes PBKDF2 salt
 * 12 bytes AES-GCM nonce
 * 4 bytes  PBKDF2-HMAC-SHA-256 iteration count
 * 8 bytes  plaintext ZIP length
 * N bytes  ciphertext
 * 16 bytes AES-GCM authentication tag
 * ```
 */
internal object PortableBackupEnvelope {
    val Magic: ByteArray = "SFBACKUP".toByteArray(Charsets.US_ASCII)

    const val CurrentVersion: Int = 1
    const val FlagContainsClipboard: Int = 1
    const val SaltBytes: Int = 16
    const val NonceBytes: Int = 12
    const val GcmTagBits: Int = 128
    const val DefaultPbkdf2Iterations: Int = 600_000
    const val MinPbkdf2Iterations: Int = 100_000
    // The iteration count is attacker-controlled until GCM authentication
    // completes. Keep accepted envelopes within a small multiple of the
    // current writer cost so malformed files cannot force unbounded KDF work.
    const val MaxPbkdf2Iterations: Int = 2_000_000
    const val MaxPlaintextBytes: Long = 256L * 1024L * 1024L
    const val HeaderBytes: Int = 8 + 2 + 2 + SaltBytes + NonceBytes + 4 + 8
    const val GcmTagBytes: Int = GcmTagBits / 8
    const val MaxEnvelopeBytes: Long = HeaderBytes + MaxPlaintextBytes + GcmTagBytes

    private const val KnownFlags: Int = FlagContainsClipboard
    private const val BufferBytes: Int = 64 * 1024
    private const val AesKeyBits: Int = 256
    private const val Pbkdf2Algorithm: String = "PBKDF2WithHmacSHA256"
    private const val AesGcmTransformation: String = "AES/GCM/NoPadding"

    data class Info(
        val version: Int,
        val flags: Int,
        val plaintextBytes: Long,
        val iterations: Int,
        internal val salt: ByteArray,
        internal val nonce: ByteArray,
        internal val authenticatedHeader: ByteArray,
    ) {
        val containsClipboard: Boolean
            get() = flags and FlagContainsClipboard != 0
    }

    enum class FailureReason {
        NotAnEnvelope,
        Truncated,
        UnsupportedVersion,
        CorruptHeader,
        Oversized,
        BadPassphraseOrTampered,
    }

    fun isEncryptedEnvelope(file: File): Boolean {
        if (!file.isFile || file.length() < Magic.size) return false
        return runCatching {
            FileInputStream(file).use { input ->
                val candidate = ByteArray(Magic.size)
                DataInputStream(input).readFully(candidate)
                candidate.contentEquals(Magic)
            }
        }.getOrDefault(false)
    }

    fun inspect(file: File): Info {
        if (!file.isFile || file.length() < HeaderBytes) {
            throw PortableBackupEnvelopeException(
                if (isEncryptedEnvelope(file)) FailureReason.Truncated else FailureReason.NotAnEnvelope,
            )
        }
        val header = ByteArray(HeaderBytes)
        FileInputStream(file).use { input ->
            DataInputStream(input).readFully(header)
        }
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
        val magic = ByteArray(Magic.size).also(buffer::get)
        if (!magic.contentEquals(Magic)) {
            throw PortableBackupEnvelopeException(FailureReason.NotAnEnvelope)
        }
        val version = buffer.short.toInt() and 0xFFFF
        if (version != CurrentVersion) {
            throw PortableBackupEnvelopeException(FailureReason.UnsupportedVersion)
        }
        val flags = buffer.short.toInt() and 0xFFFF
        if (flags and KnownFlags.inv() != 0) {
            throw PortableBackupEnvelopeException(FailureReason.CorruptHeader)
        }
        val salt = ByteArray(SaltBytes).also(buffer::get)
        val nonce = ByteArray(NonceBytes).also(buffer::get)
        val iterations = buffer.int
        if (iterations !in MinPbkdf2Iterations..MaxPbkdf2Iterations) {
            throw PortableBackupEnvelopeException(FailureReason.CorruptHeader)
        }
        val plaintextBytes = buffer.long
        if (plaintextBytes < 0L || plaintextBytes > MaxPlaintextBytes) {
            throw PortableBackupEnvelopeException(FailureReason.Oversized)
        }
        val expectedEnvelopeBytes = HeaderBytes.toLong() + plaintextBytes + GcmTagBytes
        if (file.length() != expectedEnvelopeBytes) {
            throw PortableBackupEnvelopeException(FailureReason.Truncated)
        }
        return Info(
            version = version,
            flags = flags,
            plaintextBytes = plaintextBytes,
            iterations = iterations,
            salt = salt,
            nonce = nonce,
            authenticatedHeader = header,
        )
    }

    fun encrypt(
        plaintextZip: File,
        encryptedTarget: File,
        passphrase: CharArray,
        containsClipboard: Boolean,
        iterations: Int = DefaultPbkdf2Iterations,
        secureRandom: SecureRandom = SecureRandom(),
    ): Info {
        require(plaintextZip.isFile) { "Backup plaintext must be a regular file." }
        require(plaintextZip.length() in 0L..MaxPlaintextBytes) {
            "Backup plaintext exceeds the $MaxPlaintextBytes byte limit."
        }
        require(passphrase.isNotEmpty()) { "Backup passphrase must not be empty." }
        require(iterations in MinPbkdf2Iterations..MaxPbkdf2Iterations) {
            "PBKDF2 iteration count is outside the supported range."
        }
        require(plaintextZip.canonicalFile != encryptedTarget.canonicalFile) {
            "Encrypted backup target must differ from its plaintext source."
        }

        val flags = if (containsClipboard) FlagContainsClipboard else 0
        val salt = ByteArray(SaltBytes).also(secureRandom::nextBytes)
        val nonce = ByteArray(NonceBytes).also(secureRandom::nextBytes)
        val header = ByteBuffer.allocate(HeaderBytes)
            .order(ByteOrder.BIG_ENDIAN)
            .put(Magic)
            .putShort(CurrentVersion.toShort())
            .putShort(flags.toShort())
            .put(salt)
            .put(nonce)
            .putInt(iterations)
            .putLong(plaintextZip.length())
            .array()
        val key = deriveKey(passphrase, salt, iterations)
        try {
            AtomicFileWriter.replace(
                targetFile = encryptedTarget,
                write = { stagedFile ->
                    val cipher = Cipher.getInstance(AesGcmTransformation)
                    cipher.init(
                        Cipher.ENCRYPT_MODE,
                        SecretKeySpec(key, "AES"),
                        GCMParameterSpec(GcmTagBits, nonce),
                    )
                    cipher.updateAAD(header)
                    FileOutputStream(stagedFile).use { output ->
                        output.write(header)
                        FileInputStream(plaintextZip).use { input ->
                            transform(input, output, cipher)
                        }
                        finish(output, cipher)
                    }
                },
                validate = { stagedFile ->
                    val info = inspect(stagedFile)
                    check(info.flags == flags && info.plaintextBytes == plaintextZip.length()) {
                        "Encrypted backup validation did not match its source."
                    }
                },
            )
        } finally {
            Arrays.fill(key, 0)
        }
        return inspect(encryptedTarget)
    }

    fun decrypt(
        encryptedSource: File,
        plaintextTarget: File,
        passphrase: CharArray,
    ): Info {
        if (passphrase.isEmpty()) {
            throw PortableBackupEnvelopeException(FailureReason.BadPassphraseOrTampered)
        }
        val info = inspect(encryptedSource)
        require(encryptedSource.canonicalFile != plaintextTarget.canonicalFile) {
            "Decrypted backup target must differ from its envelope source."
        }
        val key = deriveKey(passphrase, info.salt, info.iterations)
        try {
            try {
                AtomicFileWriter.replace(
                    targetFile = plaintextTarget,
                    write = { stagedFile ->
                        val cipher = Cipher.getInstance(AesGcmTransformation)
                        cipher.init(
                            Cipher.DECRYPT_MODE,
                            SecretKeySpec(key, "AES"),
                            GCMParameterSpec(GcmTagBits, info.nonce),
                        )
                        cipher.updateAAD(info.authenticatedHeader)
                        FileInputStream(encryptedSource).use { input ->
                            skipFully(input, HeaderBytes.toLong())
                            FileOutputStream(stagedFile).use { output ->
                                transform(input, output, cipher)
                                finish(output, cipher)
                            }
                        }
                    },
                    validate = { stagedFile ->
                        check(stagedFile.length() == info.plaintextBytes) {
                            "Decrypted backup length did not match its authenticated header."
                        }
                    },
                )
            } catch (error: AEADBadTagException) {
                throw PortableBackupEnvelopeException(
                    FailureReason.BadPassphraseOrTampered,
                    error,
                )
            } catch (error: GeneralSecurityException) {
                throw PortableBackupEnvelopeException(FailureReason.CorruptHeader, error)
            }
        } finally {
            Arrays.fill(key, 0)
        }
        return info
    }

    private fun transform(
        input: FileInputStream,
        output: FileOutputStream,
        cipher: Cipher,
    ) {
        val buffer = ByteArray(BufferBytes)
        try {
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                cipher.update(buffer, 0, read)?.let { transformed ->
                    try {
                        output.write(transformed)
                    } finally {
                        Arrays.fill(transformed, 0)
                    }
                }
            }
        } finally {
            Arrays.fill(buffer, 0)
        }
    }

    private fun finish(output: FileOutputStream, cipher: Cipher) {
        val finalBytes = cipher.doFinal()
        try {
            output.write(finalBytes)
        } finally {
            Arrays.fill(finalBytes, 0)
        }
    }

    private fun skipFully(input: FileInputStream, bytes: Long) {
        var remaining = bytes
        while (remaining > 0L) {
            val skipped = input.skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
            } else if (input.read() < 0) {
                throw PortableBackupEnvelopeException(FailureReason.Truncated)
            } else {
                remaining--
            }
        }
    }

    private fun deriveKey(
        passphrase: CharArray,
        salt: ByteArray,
        iterations: Int,
    ): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, iterations, AesKeyBits)
        return try {
            SecretKeyFactory.getInstance(Pbkdf2Algorithm).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}

internal class PortableBackupEnvelopeException(
    val reason: PortableBackupEnvelope.FailureReason,
    cause: Throwable? = null,
) : RuntimeException("Portable backup envelope failure: $reason", cause)
