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

import dev.patrickgold.florisboard.lib.io.ZipUtils
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.florisboard.lib.kotlin.io.subDir
import org.florisboard.lib.kotlin.io.subFile

class PortableBackupEnvelopeTest : FunSpec({
    val iterations = PortableBackupEnvelope.MinPbkdf2Iterations

    fun withFiles(block: (java.io.File, java.io.File, java.io.File) -> Unit) {
        val dir = Files.createTempDirectory("portable-backup-envelope").toFile()
        try {
            block(
                dir.resolve("source.zip"),
                dir.resolve("backup.sfbak"),
                dir.resolve("restored.zip"),
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    test("streaming envelope round-trips a multi-buffer clipboard archive") {
        withFiles { source, envelope, restored ->
            val plaintext = ByteArray(PortableBackupEnvelope.HeaderBytes * 4096) { index ->
                (index * 31).toByte()
            }
            source.writeBytes(plaintext)
            val passphrase = "correct horse battery staple".toCharArray()

            val encrypted = PortableBackupEnvelope.encrypt(
                plaintextZip = source,
                encryptedTarget = envelope,
                passphrase = passphrase,
                containsClipboard = true,
                iterations = iterations,
            )
            val decrypted = PortableBackupEnvelope.decrypt(envelope, restored, passphrase)

            encrypted.version shouldBe PortableBackupEnvelope.CurrentVersion
            encrypted.containsClipboard shouldBe true
            encrypted.plaintextBytes shouldBe plaintext.size.toLong()
            decrypted.plaintextBytes shouldBe plaintext.size.toLong()
            restored.readBytes().contentEquals(plaintext) shouldBe true
            envelope.readBytes().contentEquals(plaintext) shouldBe false
        }
    }

    test("wrong passphrase leaves the previous plaintext target unchanged") {
        withFiles { source, envelope, restored ->
            source.writeText("clipboard payload")
            restored.writeText("last valid restore")
            PortableBackupEnvelope.encrypt(
                plaintextZip = source,
                encryptedTarget = envelope,
                passphrase = "right-passphrase".toCharArray(),
                containsClipboard = true,
                iterations = iterations,
            )

            val error = shouldThrow<PortableBackupEnvelopeException> {
                PortableBackupEnvelope.decrypt(
                    encryptedSource = envelope,
                    plaintextTarget = restored,
                    passphrase = "wrong-passphrase".toCharArray(),
                )
            }

            error.reason shouldBe PortableBackupEnvelope.FailureReason.BadPassphraseOrTampered
            restored.readText() shouldBe "last valid restore"
            requireNotNull(restored.parentFile).listFiles().orEmpty()
                .count { it.name.startsWith(".${restored.name}.") && it.name.endsWith(".tmp") } shouldBe 0
        }
    }

    test("ciphertext tampering is authenticated before plaintext publication") {
        withFiles { source, envelope, restored ->
            source.writeText("clipboard payload")
            restored.writeText("last valid restore")
            val passphrase = "tamper-test-passphrase".toCharArray()
            PortableBackupEnvelope.encrypt(
                plaintextZip = source,
                encryptedTarget = envelope,
                passphrase = passphrase,
                containsClipboard = true,
                iterations = iterations,
            )
            RandomAccessFile(envelope, "rw").use { file ->
                val offset = PortableBackupEnvelope.HeaderBytes.toLong() + 1L
                file.seek(offset)
                val value = file.read()
                file.seek(offset)
                file.write(value.xor(0x01))
            }

            val error = shouldThrow<PortableBackupEnvelopeException> {
                PortableBackupEnvelope.decrypt(envelope, restored, passphrase)
            }

            error.reason shouldBe PortableBackupEnvelope.FailureReason.BadPassphraseOrTampered
            restored.readText() shouldBe "last valid restore"
        }
    }

    test("header flags are authenticated as GCM associated data") {
        withFiles { source, envelope, restored ->
            source.writeText("clipboard payload")
            restored.writeText("last valid restore")
            val passphrase = "header-test-passphrase".toCharArray()
            PortableBackupEnvelope.encrypt(
                plaintextZip = source,
                encryptedTarget = envelope,
                passphrase = passphrase,
                containsClipboard = true,
                iterations = iterations,
            )
            RandomAccessFile(envelope, "rw").use { file ->
                file.seek(PortableBackupEnvelope.Magic.size.toLong() + 3L)
                file.write(0)
            }

            val error = shouldThrow<PortableBackupEnvelopeException> {
                PortableBackupEnvelope.decrypt(envelope, restored, passphrase)
            }

            error.reason shouldBe PortableBackupEnvelope.FailureReason.BadPassphraseOrTampered
            restored.readText() shouldBe "last valid restore"
        }
    }

    test("unsupported versions and oversized headers fail before KDF work") {
        withFiles { source, envelope, _ ->
            source.writeText("clipboard payload")
            PortableBackupEnvelope.encrypt(
                plaintextZip = source,
                encryptedTarget = envelope,
                passphrase = "format-test-passphrase".toCharArray(),
                containsClipboard = true,
                iterations = iterations,
            )
            val unsupported = envelope.readBytes().also { bytes ->
                ByteBuffer.wrap(bytes)
                    .order(ByteOrder.BIG_ENDIAN)
                    .putShort(PortableBackupEnvelope.Magic.size, 2.toShort())
            }
            envelope.writeBytes(unsupported)
            shouldThrow<PortableBackupEnvelopeException> {
                PortableBackupEnvelope.inspect(envelope)
            }.reason shouldBe PortableBackupEnvelope.FailureReason.UnsupportedVersion

            PortableBackupEnvelope.encrypt(
                plaintextZip = source,
                encryptedTarget = envelope,
                passphrase = "format-test-passphrase".toCharArray(),
                containsClipboard = true,
                iterations = iterations,
            )
            val oversized = envelope.readBytes().also { bytes ->
                ByteBuffer.wrap(bytes)
                    .order(ByteOrder.BIG_ENDIAN)
                    .putLong(PortableBackupEnvelope.HeaderBytes - Long.SIZE_BYTES, Long.MAX_VALUE)
            }
            envelope.writeBytes(oversized)
            shouldThrow<PortableBackupEnvelopeException> {
                PortableBackupEnvelope.inspect(envelope)
            }.reason shouldBe PortableBackupEnvelope.FailureReason.Oversized
        }
    }

    test("truncated envelopes fail closed and legacy zip detection stays explicit") {
        withFiles { source, envelope, _ ->
            source.writeBytes(PortableBackupEnvelope.Magic + byteArrayOf(0x00))
            PortableBackupEnvelope.isEncryptedEnvelope(source) shouldBe true
            shouldThrow<PortableBackupEnvelopeException> {
                PortableBackupEnvelope.inspect(source)
            }.reason shouldBe PortableBackupEnvelope.FailureReason.Truncated

            envelope.writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04))
            PortableBackupEnvelope.isEncryptedEnvelope(envelope) shouldBe false
            shouldThrow<PortableBackupEnvelopeException> {
                PortableBackupEnvelope.inspect(envelope)
            }.reason shouldBe PortableBackupEnvelope.FailureReason.NotAnEnvelope
        }
    }

    test("prior plaintext zip archives retain an explicit restore path") {
        val dir = Files.createTempDirectory("portable-backup-legacy").toFile()
        try {
            val legacyArchive = dir.subFile("prior-backup.zip")
            FileOutputStream(legacyArchive).use { fileOut ->
                ZipOutputStream(fileOut).use { zipOut ->
                    zipOut.putNextEntry(ZipEntry(Backup.METADATA_JSON_NAME))
                    zipOut.write("""{"package":"dev.patrickgold.florisboard"}""".toByteArray())
                    zipOut.closeEntry()
                }
            }

            PortableBackupEnvelope.isEncryptedEnvelope(legacyArchive) shouldBe false
            val restored = dir.subDir("restored")
            ZipUtils.unzip(legacyArchive, restored)
            restored.subFile(Backup.METADATA_JSON_NAME).isFile shouldBe true
        } finally {
            dir.deleteRecursively()
        }
    }

    test("encrypted zip fixture authenticates before normal archive extraction") {
        withFiles { source, envelope, restored ->
            FileOutputStream(source).use { fileOut ->
                ZipOutputStream(fileOut).use { zipOut ->
                    zipOut.putNextEntry(ZipEntry(Backup.METADATA_JSON_NAME))
                    zipOut.write("""{"package":"io.github.sysadmindoc.swiftfloris"}""".toByteArray())
                    zipOut.closeEntry()
                    zipOut.putNextEntry(ZipEntry(Backup.CLIPBOARD_TEXT_ITEMS_JSON_NAME))
                    zipOut.write("""[{"text":"portable fixture"}]""".toByteArray())
                    zipOut.closeEntry()
                }
            }
            val passphrase = "portable-fixture-passphrase".toCharArray()

            PortableBackupEnvelope.encrypt(
                plaintextZip = source,
                encryptedTarget = envelope,
                passphrase = passphrase,
                containsClipboard = true,
                iterations = iterations,
            )
            PortableBackupEnvelope.decrypt(envelope, restored, passphrase)
            val extracted = requireNotNull(restored.parentFile).subDir("fixture-output")
            ZipUtils.unzip(restored, extracted)

            extracted.subFile(Backup.METADATA_JSON_NAME).isFile shouldBe true
            extracted.subFile(Backup.CLIPBOARD_TEXT_ITEMS_JSON_NAME)
                .readText() shouldBe """[{"text":"portable fixture"}]"""
        }
    }

    test("independent exports use fresh salt and nonce") {
        val dir = Files.createTempDirectory("portable-backup-randomness").toFile()
        try {
            val source = dir.resolve("source.zip").apply { writeText("same payload") }
            val first = dir.resolve("first.sfbak")
            val second = dir.resolve("second.sfbak")
            val passphrase = "randomness-test-passphrase".toCharArray()

            PortableBackupEnvelope.encrypt(
                source,
                first,
                passphrase,
                containsClipboard = true,
                iterations = iterations,
            )
            PortableBackupEnvelope.encrypt(
                source,
                second,
                passphrase,
                containsClipboard = true,
                iterations = iterations,
            )

            first.readBytes().contentEquals(second.readBytes()) shouldBe false
        } finally {
            dir.deleteRecursively()
        }
    }
})
