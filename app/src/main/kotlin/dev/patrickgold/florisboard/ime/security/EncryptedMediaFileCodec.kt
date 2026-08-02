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

import dev.patrickgold.florisboard.lib.io.AtomicFileWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Streaming AES-GCM codec for app-private media files.
 *
 * The file header and every ciphertext byte are authenticated. Plaintext is
 * published only through [AtomicFileWriter] after the tag verifies, so a
 * failed decrypt cannot replace an existing target with unauthenticated data.
 * The format intentionally contains no plaintext media metadata: the
 * plaintext length is derived from the authenticated ciphertext length.
 */
internal object EncryptedMediaFileCodec {
    val Magic: ByteArray = "SFCLIPM1".toByteArray(Charsets.US_ASCII)

    const val CurrentVersion: Int = 1
    const val NonceBytes: Int = 12
    const val GcmTagBits: Int = 128
    const val GcmTagBytes: Int = GcmTagBits / 8
    val HeaderBytes: Int = Magic.size + 1 + NonceBytes

    private const val BufferBytes: Int = 64 * 1024
    private const val AesKeyBytes: Int = 32
    private const val AesGcmTransformation: String = "AES/GCM/NoPadding"

    data class Info(
        val version: Int,
        val plaintextBytes: Long,
        internal val header: ByteArray,
        internal val nonce: ByteArray,
    )

    fun isEncrypted(file: File): Boolean {
        if (!file.isFile || file.length() < Magic.size) return false
        return runCatching {
            FileInputStream(file).use { input ->
                val candidate = ByteArray(Magic.size)
                readFully(input, candidate)
                candidate.contentEquals(Magic)
            }
        }.getOrDefault(false)
    }

    fun inspect(file: File, maxPlaintextBytes: Long): Info {
        require(maxPlaintextBytes > 0L) { "Media size limit must be positive." }
        require(file.isFile) { "Encrypted media file is missing." }
        require(file.length() >= HeaderBytes + GcmTagBytes) {
            "Encrypted media file is truncated."
        }

        val header = ByteArray(HeaderBytes)
        FileInputStream(file).use { input -> readFully(input, header) }
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
        val magic = ByteArray(Magic.size).also(buffer::get)
        require(magic.contentEquals(Magic)) { "Clipboard media is not encrypted." }
        val version = buffer.get().toInt() and 0xFF
        require(version == CurrentVersion) { "Unsupported clipboard media version: $version" }
        val nonce = ByteArray(NonceBytes).also(buffer::get)
        val plaintextBytes = file.length() - HeaderBytes - GcmTagBytes
        require(plaintextBytes in 0L..maxPlaintextBytes) {
            "Encrypted clipboard media exceeds its size limit."
        }
        return Info(version, plaintextBytes, header, nonce)
    }

    fun encrypt(
        input: InputStream,
        target: File,
        key: ByteArray,
        maxPlaintextBytes: Long,
        secureRandom: SecureRandom = SecureRandom(),
    ): Long {
        requireValidKey(key)
        require(maxPlaintextBytes > 0L) { "Media size limit must be positive." }
        val nonce = ByteArray(NonceBytes).also(secureRandom::nextBytes)
        val header = ByteBuffer.allocate(HeaderBytes)
            .order(ByteOrder.BIG_ENDIAN)
            .put(Magic)
            .put(CurrentVersion.toByte())
            .put(nonce)
            .array()
        var plaintextBytes = 0L
        val workingKey = key.copyOf()
        try {
            AtomicFileWriter.replace(
                targetFile = target,
                write = { stagedFile ->
                    val cipher = Cipher.getInstance(AesGcmTransformation)
                    cipher.init(
                        Cipher.ENCRYPT_MODE,
                        SecretKeySpec(workingKey, "AES"),
                        GCMParameterSpec(GcmTagBits, nonce),
                    )
                    cipher.updateAAD(header)
                    FileOutputStream(stagedFile).use { output ->
                        output.write(header)
                        plaintextBytes = transformEncrypt(
                            input = input,
                            output = output,
                            cipher = cipher,
                            maxPlaintextBytes = maxPlaintextBytes,
                        )
                        finish(output, cipher)
                    }
                },
                validate = { stagedFile ->
                    val info = inspect(stagedFile, maxPlaintextBytes)
                    check(info.plaintextBytes == plaintextBytes) {
                        "Encrypted clipboard media length did not match its source."
                    }
                },
            )
        } finally {
            Arrays.fill(workingKey, 0)
        }
        return plaintextBytes
    }

    fun decrypt(
        source: File,
        target: File,
        key: ByteArray,
        maxPlaintextBytes: Long,
    ): Long {
        requireValidKey(key)
        val info = inspect(source, maxPlaintextBytes)
        var plaintextBytes = 0L
        val workingKey = key.copyOf()
        try {
            AtomicFileWriter.replace(
                targetFile = target,
                write = { stagedFile ->
                    val cipher = Cipher.getInstance(AesGcmTransformation)
                    cipher.init(
                        Cipher.DECRYPT_MODE,
                        SecretKeySpec(workingKey, "AES"),
                        GCMParameterSpec(GcmTagBits, info.nonce),
                    )
                    cipher.updateAAD(info.header)
                    FileInputStream(source).use { input ->
                        val header = ByteArray(HeaderBytes)
                        readFully(input, header)
                        FileOutputStream(stagedFile).use { output ->
                            plaintextBytes = transformDecrypt(
                                input = input,
                                output = output,
                                cipher = cipher,
                                ciphertextBytes = info.plaintextBytes + GcmTagBytes,
                            )
                            plaintextBytes += finish(output, cipher)
                        }
                    }
                },
                validate = { stagedFile ->
                    check(plaintextBytes == info.plaintextBytes) {
                        "Decrypted clipboard media length did not match its header."
                    }
                    check(stagedFile.length() == info.plaintextBytes) {
                        "Decrypted clipboard media size did not match its header."
                    }
                },
            )
        } finally {
            Arrays.fill(workingKey, 0)
        }
        return plaintextBytes
    }

    fun copyPlaintext(
        input: InputStream,
        target: File,
        maxPlaintextBytes: Long,
    ): Long {
        require(maxPlaintextBytes > 0L) { "Media size limit must be positive." }
        var plaintextBytes = 0L
        AtomicFileWriter.replace(
            targetFile = target,
            write = { stagedFile ->
                FileOutputStream(stagedFile).use { output ->
                    plaintextBytes = copyLimited(input, output, maxPlaintextBytes)
                }
            },
            validate = { stagedFile ->
                check(stagedFile.length() == plaintextBytes) {
                    "Copied clipboard media length did not match its source."
                }
            },
        )
        return plaintextBytes
    }

    private fun transformEncrypt(
        input: InputStream,
        output: OutputStream,
        cipher: Cipher,
        maxPlaintextBytes: Long,
    ): Long {
        val buffer = ByteArray(BufferBytes)
        var total = 0L
        try {
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                total += read
                check(total <= maxPlaintextBytes) {
                    "Clipboard media exceeds the $maxPlaintextBytes byte limit."
                }
                cipher.update(buffer, 0, read)?.let(output::write)
            }
            return total
        } finally {
            Arrays.fill(buffer, 0)
        }
    }

    private fun transformDecrypt(
        input: InputStream,
        output: OutputStream,
        cipher: Cipher,
        ciphertextBytes: Long,
    ): Long {
        var remaining = ciphertextBytes
        var total = 0L
        val buffer = ByteArray(BufferBytes)
        try {
            while (remaining > 0L) {
                val requested = minOf(remaining, buffer.size.toLong()).toInt()
                val read = input.read(buffer, 0, requested)
                check(read > 0) { "Encrypted clipboard media is truncated." }
                remaining -= read
                cipher.update(buffer, 0, read)?.let {
                    output.write(it)
                    total += it.size
                }
            }
            return total
        } finally {
            Arrays.fill(buffer, 0)
        }
    }

    private fun copyLimited(
        input: InputStream,
        output: OutputStream,
        maxPlaintextBytes: Long,
    ): Long {
        val buffer = ByteArray(BufferBytes)
        var total = 0L
        try {
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                total += read
                check(total <= maxPlaintextBytes) {
                    "Clipboard media exceeds the $maxPlaintextBytes byte limit."
                }
                output.write(buffer, 0, read)
            }
            return total
        } finally {
            Arrays.fill(buffer, 0)
        }
    }

    private fun finish(output: OutputStream, cipher: Cipher): Int {
        val finalBytes = cipher.doFinal()
        try {
            output.write(finalBytes)
            return finalBytes.size
        } finally {
            Arrays.fill(finalBytes, 0)
        }
    }

    private fun readFully(input: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            check(read > 0) { "Encrypted clipboard media is truncated." }
            offset += read
        }
    }

    private fun requireValidKey(key: ByteArray) {
        require(key.size == AesKeyBytes) { "Clipboard media key must be 256 bits." }
    }
}
