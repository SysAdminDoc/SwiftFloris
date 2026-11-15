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

import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.florisboard.lib.io.ZipUtils
import dev.patrickgold.jetpref.datastore.runtime.AndroidAppDataStorage
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.florisboard.lib.kotlin.io.subDir
import org.florisboard.lib.kotlin.io.subFile
import org.florisboard.lib.kotlin.io.writeJson

/**
 * A storage provider stand-in. It keeps one document and can drop the end of a write, change
 * or add bytes on the way back, or fail to open, the ways a real provider has been seen to.
 */
private class FakeDocumentProvider {
    var document = ByteArray(0)
    var dropOnWrite = 0
    var flipOnRead: Int? = null
    var extraOnRead = 0
    var openFailure: Throwable? = null
    var opensNothing = false
    /** Fails every open after this many, for a provider that stops reading partway through a save. */
    var failOpensAfter: Int? = null
    private var opens = 0
    var refusesDelete = false
    var writes = 0
        private set
    var deleted = false
        private set

    fun write(archive: File) {
        val bytes = archive.readBytes()
        writes++
        document = bytes.copyOf(bytes.size - dropOnWrite)
    }

    fun delete(): Boolean {
        if (refusesDelete) return false
        deleted = true
        document = ByteArray(0)
        return true
    }

    fun open(): InputStream? {
        openFailure?.let { throw it }
        opens++
        failOpensAfter?.takeIf { opens > it }?.let { throw IOException("provider went away") }
        if (opensNothing) return null
        var bytes = document.copyOf()
        // Only a document long enough to have that byte gets it changed, so the empty document
        // a save starts from still reads as empty.
        flipOnRead?.takeIf { it < bytes.size }?.let { index -> bytes[index] = (bytes[index].toInt() xor 0xFF).toByte() }
        if (extraOnRead > 0 && bytes.isNotEmpty()) bytes += ByteArray(extraOnRead)
        // Hand bytes back a few at a time, the way a slow provider does.
        return object : ByteArrayInputStream(bytes) {
            override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, minOf(len, 997))
        }
    }
}

class BackupPublicationVerifierTest : FunSpec({
    val passphrase = "correct horse".toCharArray()
    lateinit var root: File
    lateinit var scratch: File

    beforeTest {
        root = Files.createTempDirectory("backup-verifier").toFile()
        scratch = root.subDir("scratch").also { it.mkdirs() }
    }

    afterTest {
        root.deleteRecursively()
    }

    fun plaintextArchive(
        archiveVersion: Int = Backup.CURRENT_ARCHIVE_FORMAT_VERSION,
        withMetadata: Boolean = true,
        settings: String? = null,
    ): File {
        val source = root.subDir("source-${System.nanoTime()}")
        source.mkdirs()
        if (withMetadata) {
            source.subFile(Backup.METADATA_JSON_NAME).writeJson(
                Backup.Metadata(
                    packageName = Restore.ACCEPTED_PACKAGE_PREFIXES.first(),
                    versionCode = Restore.MIN_VERSION_CODE,
                    versionName = "test",
                    timestamp = 42L,
                    archiveVersion = archiveVersion,
                ),
            )
        }
        source.subDir("files").subDir(BackupArchiveStores.SnippetsDirName).also { it.mkdirs() }
            .subFile("snippets.json").writeText("[]")
        source.subDir("clipboard").also { it.mkdirs() }
            .subFile(Backup.CLIPBOARD_TEXT_ITEMS_JSON_NAME).writeText("[]")
        if (settings != null) {
            source.subDir(AndroidAppDataStorage.JETPREF_DIR_NAME).also { it.mkdirs() }
                .subFile("${FlorisPreferenceModel.NAME}.${AndroidAppDataStorage.JETPREF_FILE_EXT}")
                .writeText(settings)
        }
        val zip = root.subFile("archive-${System.nanoTime()}.zip")
        ZipUtils.zip(source, zip)
        return zip
    }

    fun encryptedArchive(): File {
        val zip = plaintextArchive()
        val envelope = root.subFile("archive-${System.nanoTime()}.sfbak")
        PortableBackupEnvelope.encrypt(
            plaintextZip = zip,
            encryptedTarget = envelope,
            passphrase = passphrase,
            containsClipboard = true,
            iterations = PortableBackupEnvelope.MinPbkdf2Iterations,
        )
        return envelope
    }

    suspend fun problemOf(block: suspend () -> Unit): BackupVerificationException.Problem =
        shouldThrow<BackupVerificationException> { block() }.problem

    test("an intact save reads back, and the receipt names what the archive holds") {
        val archive = encryptedArchive()
        val receipt = BackupPublicationVerifier.inspect(archive, passphrase, scratch)

        receipt.archiveVersion shouldBe Backup.CURRENT_ARCHIVE_FORMAT_VERSION
        receipt.encrypted shouldBe true
        receipt.sections.snippets shouldBe true
        receipt.sections.clipboardTextItems shouldBe true
        receipt.sections.jetprefDatastore shouldBe false
        receipt.sections.clipboardImageItems shouldBe false
        receipt.byteSize shouldBe archive.length()
        receipt.sha256 shouldBe MessageDigest.getInstance("SHA-256").digest(archive.readBytes())
            .joinToString(separator = "") { "%02x".format(it) }

        val provider = FakeDocumentProvider()
        provider.write(archive)
        shouldNotThrowAny { BackupPublicationVerifier.verifyReadback(receipt, provider::open) }
    }

    test("a plaintext archive is inspected without a passphrase") {
        val receipt = BackupPublicationVerifier.inspect(plaintextArchive(), null, scratch)
        receipt.encrypted shouldBe false
        receipt.sections.snippets shouldBe true
    }

    test("archived settings restore's import accepts pass the check") {
        val receipt = BackupPublicationVerifier.inspect(
            plaintextArchive(settings = "b;keyboard__number_row;true\n"),
            null,
            scratch,
        )
        receipt.sections.jetprefDatastore shouldBe true
    }

    test("archived settings restore's import rejects are reported as damaged") {
        // jetpref skips a line without separators but lets a value's serializer throw, and
        // the accent color's serializer throws on anything that isn't hex.
        problemOf {
            BackupPublicationVerifier.inspect(
                plaintextArchive(settings = "b;keyboard__number_row;true\ns;theme__accent_color;not-a-color\n"),
                null,
                scratch,
            )
        } shouldBe BackupVerificationException.Problem.Damaged
        scratch.listFiles()!!.toList().shouldBeEmpty()
    }

    test("a short write is reported as truncated") {
        val archive = encryptedArchive()
        val receipt = BackupPublicationVerifier.inspect(archive, passphrase, scratch)
        for (dropped in listOf(1, archive.length().toInt() / 2, archive.length().toInt())) {
            val provider = FakeDocumentProvider().apply { dropOnWrite = dropped }
            provider.write(archive)
            problemOf { BackupPublicationVerifier.verifyReadback(receipt, provider::open) } shouldBe
                BackupVerificationException.Problem.Truncated
        }
    }

    test("an altered or longer read is reported as altered") {
        val archive = encryptedArchive()
        val receipt = BackupPublicationVerifier.inspect(archive, passphrase, scratch)
        val flipped = FakeDocumentProvider().apply { flipOnRead = archive.length().toInt() / 2 }
        flipped.write(archive)
        problemOf { BackupPublicationVerifier.verifyReadback(receipt, flipped::open) } shouldBe
            BackupVerificationException.Problem.Altered
        val longer = FakeDocumentProvider().apply { extraOnRead = 1 }
        longer.write(archive)
        problemOf { BackupPublicationVerifier.verifyReadback(receipt, longer::open) } shouldBe
            BackupVerificationException.Problem.Altered
    }

    test("a destination that can't be read back is reported") {
        val archive = encryptedArchive()
        val receipt = BackupPublicationVerifier.inspect(archive, passphrase, scratch)
        val failing = FakeDocumentProvider().apply { openFailure = IOException("provider went away") }
        problemOf { BackupPublicationVerifier.verifyReadback(receipt, failing::open) } shouldBe
            BackupVerificationException.Problem.ReadbackFailed
        val empty = FakeDocumentProvider().apply { opensNothing = true }
        problemOf { BackupPublicationVerifier.verifyReadback(receipt, empty::open) } shouldBe
            BackupVerificationException.Problem.ReadbackFailed
    }

    test("a wrong passphrase is reported and leaves the archive as it was") {
        val archive = encryptedArchive()
        val before = archive.readBytes()
        problemOf { BackupPublicationVerifier.inspect(archive, "wrong".toCharArray(), scratch) } shouldBe
            BackupVerificationException.Problem.WrongPassphrase
        problemOf { BackupPublicationVerifier.inspect(archive, null, scratch) } shouldBe
            BackupVerificationException.Problem.WrongPassphrase
        archive.readBytes().contentEquals(before) shouldBe true
        scratch.listFiles()!!.toList().shouldBeEmpty()
    }

    test("a cut-off or unreadable archive is reported as damaged") {
        val archive = encryptedArchive()
        val cut = root.subFile("cut.sfbak").also { it.writeBytes(archive.readBytes().copyOf(archive.length().toInt() / 2)) }
        // Nothing has been saved when inspect runs, so a short archive is a bad build. Truncated
        // is kept for a saved copy that comes back short.
        problemOf { BackupPublicationVerifier.inspect(cut, passphrase, scratch) } shouldBe
            BackupVerificationException.Problem.Damaged
        val garbage = root.subFile("garbage.zip").also { it.writeText("not an archive") }
        problemOf { BackupPublicationVerifier.inspect(garbage, null, scratch) } shouldBe
            BackupVerificationException.Problem.Damaged
    }

    test("an archive restore would refuse is reported as invalid") {
        problemOf {
            BackupPublicationVerifier.inspect(plaintextArchive(archiveVersion = 99), null, scratch)
        } shouldBe BackupVerificationException.Problem.Invalid
        problemOf {
            BackupPublicationVerifier.inspect(plaintextArchive(withMetadata = false), null, scratch)
        } shouldBe BackupVerificationException.Problem.Invalid
    }

    test("inspection leaves no decrypted copy behind") {
        BackupPublicationVerifier.inspect(encryptedArchive(), passphrase, scratch)
        scratch.listFiles()!!.toList().shouldBeEmpty()
    }

    test("an archive past restore's size limit is reported as too large") {
        val archive = encryptedArchive()
        problemOf {
            BackupPublicationVerifier.inspect(archive, passphrase, scratch, maxBytes = archive.length() - 1)
        } shouldBe BackupVerificationException.Problem.TooLarge
    }

    /** [plaintextArchive] plus one more file of [size] zero bytes, written straight into the ZIP. */
    fun archiveWithExtraFile(name: String, size: Long): File {
        val base = plaintextArchive()
        val zip = root.subFile("extra-${System.nanoTime()}.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            ZipFile(base).use { source ->
                for (entry in source.entries()) {
                    out.putNextEntry(ZipEntry(entry.name))
                    source.getInputStream(entry).use { it.copyTo(out) }
                    out.closeEntry()
                }
            }
            out.putNextEntry(ZipEntry(name))
            val zeros = ByteArray(1 shl 20)
            var left = size
            while (left > 0L) {
                val chunk = minOf(left, zeros.size.toLong()).toInt()
                out.write(zeros, 0, chunk)
                left -= chunk
            }
            out.closeEntry()
        }
        return zip
    }

    test("a file restore's unzip would leave out fails the check instead of going missing") {
        // Unzip skips a name this long without failing, and restore would bring back the rest.
        val archive = archiveWithExtraFile("files/${BackupArchiveStores.SnippetsDirName}/${"a".repeat(260)}", 3L)
        problemOf {
            BackupPublicationVerifier.inspect(archive, null, scratch)
        } shouldBe BackupVerificationException.Problem.Damaged
    }

    test("a file past restore's per-file limit is reported as too large") {
        // The archive itself is tiny, since zeros compress, but unzip would leave the file out.
        val archive = archiveWithExtraFile(
            "files/${BackupArchiveStores.SnippetsDirName}/big.bin",
            ZipUtils.MaxUnzippedEntrySizeBytes + 1L,
        )
        problemOf {
            BackupPublicationVerifier.inspect(archive, null, scratch)
        } shouldBe BackupVerificationException.Problem.TooLarge
    }

    test("a file unzip skips at the archive limit is told apart from a damaged one") {
        val limit = ZipUtils.MaxUnzippedArchiveSizeBytes
        val tooLarge = BackupVerificationException.Problem.TooLarge
        val damaged = BackupVerificationException.Problem.Damaged
        // The files before it used up the whole limit, so unzip skips even an empty one.
        BackupPublicationVerifier.missingFileProblem(entrySize = 0L, listedBytes = limit) shouldBe tooLarge
        BackupPublicationVerifier.missingFileProblem(entrySize = 1L, listedBytes = limit + 1L) shouldBe tooLarge
        BackupPublicationVerifier.missingFileProblem(
            entrySize = ZipUtils.MaxUnzippedEntrySizeBytes + 1L,
            listedBytes = ZipUtils.MaxUnzippedEntrySizeBytes + 1L,
        ) shouldBe tooLarge
        // These fit exactly, so unzip writes them, and a missing one means something else broke.
        BackupPublicationVerifier.missingFileProblem(entrySize = 10L, listedBytes = limit) shouldBe damaged
        BackupPublicationVerifier.missingFileProblem(entrySize = 0L, listedBytes = limit - 1L) shouldBe damaged
        BackupPublicationVerifier.missingFileProblem(
            entrySize = ZipUtils.MaxUnzippedEntrySizeBytes,
            listedBytes = ZipUtils.MaxUnzippedEntrySizeBytes,
        ) shouldBe damaged
    }

    fun saveInto(provider: FakeDocumentProvider, archive: File, receipt: BackupReceipt) =
        ManualBackupSave.save(
            archive = archive,
            receipt = receipt,
            open = provider::open,
            write = provider::write,
            delete = provider::delete,
        )

    fun failedSave(provider: FakeDocumentProvider, archive: File, receipt: BackupReceipt) =
        shouldThrow<ManualBackupSaveException> { saveInto(provider, archive, receipt) }

    fun refusedSave(provider: FakeDocumentProvider, archive: File, receipt: BackupReceipt) =
        shouldThrow<PickedDocumentRefusedException> { saveInto(provider, archive, receipt) }

    test("a manual save into a new file leaves the checked archive") {
        val archive = encryptedArchive()
        val receipt = BackupPublicationVerifier.inspect(archive, passphrase, scratch)
        val provider = FakeDocumentProvider()
        saveInto(provider, archive, receipt)
        provider.document.contentEquals(archive.readBytes()) shouldBe true
        provider.deleted shouldBe false
    }

    test("a short write into a new file deletes it and says so") {
        val archive = encryptedArchive()
        val receipt = BackupPublicationVerifier.inspect(archive, passphrase, scratch)
        val provider = FakeDocumentProvider().apply { dropOnWrite = 10 }
        val error = failedSave(provider, archive, receipt)
        error.cleanup shouldBe FailedSaveCleanup.Removed
        (error.cause as BackupVerificationException).problem shouldBe BackupVerificationException.Problem.Truncated
        provider.deleted shouldBe true
    }

    test("an altered read after a manual save deletes the file and says so") {
        val archive = encryptedArchive()
        val receipt = BackupPublicationVerifier.inspect(archive, passphrase, scratch)
        val provider = FakeDocumentProvider().apply { flipOnRead = archive.length().toInt() / 2 }
        val error = failedSave(provider, archive, receipt)
        error.cleanup shouldBe FailedSaveCleanup.Removed
        (error.cause as BackupVerificationException).problem shouldBe BackupVerificationException.Problem.Altered
        provider.deleted shouldBe true
    }

    test("a picked file that already has something in it is refused and left as it was") {
        val archive = encryptedArchive()
        val receipt = BackupPublicationVerifier.inspect(archive, passphrase, scratch)
        val prior = "an older backup the user chose to replace".toByteArray()
        val provider = FakeDocumentProvider().apply { document = prior }
        refusedSave(provider, archive, receipt).reason shouldBe PickedDocumentRefusedException.Reason.NotEmpty
        provider.writes shouldBe 0
        provider.deleted shouldBe false
        provider.document.contentEquals(prior) shouldBe true
    }

    test("a picked file that can't be opened is refused and never written") {
        val archive = encryptedArchive()
        val receipt = BackupPublicationVerifier.inspect(archive, passphrase, scratch)
        val failing = FakeDocumentProvider().apply { openFailure = IOException("no read permission") }
        refusedSave(failing, archive, receipt).reason shouldBe PickedDocumentRefusedException.Reason.Unreadable
        failing.writes shouldBe 0
        val nothing = FakeDocumentProvider().apply { opensNothing = true }
        refusedSave(nothing, archive, receipt).reason shouldBe PickedDocumentRefusedException.Reason.Unreadable
        nothing.writes shouldBe 0
    }

    test("a delete the provider won't do is reported as left in place") {
        val archive = encryptedArchive()
        val receipt = BackupPublicationVerifier.inspect(archive, passphrase, scratch)
        val provider = FakeDocumentProvider().apply {
            dropOnWrite = 10
            refusesDelete = true
        }
        failedSave(provider, archive, receipt).cleanup shouldBe FailedSaveCleanup.LeftInPlace
    }

    test("a save the provider won't read back after writing deletes the file and says so") {
        // The empty check before the write opens fine, so only the readback fails.
        val archive = encryptedArchive()
        val receipt = BackupPublicationVerifier.inspect(archive, passphrase, scratch)
        val provider = FakeDocumentProvider().apply { failOpensAfter = 1 }
        val error = failedSave(provider, archive, receipt)
        provider.writes shouldBe 1
        error.cleanup shouldBe FailedSaveCleanup.Removed
        (error.cause as BackupVerificationException).problem shouldBe BackupVerificationException.Problem.ReadbackFailed
        provider.deleted shouldBe true
    }

    test("a full phone is told apart from a damaged archive") {
        IOException("write failed: ENOSPC (No space left on device)").isOutOfSpace() shouldBe true
        IllegalStateException("unzip failed", IOException("No space left on device")).isOutOfSpace() shouldBe true
        IOException("Unexpected end of ZLIB input stream").isOutOfSpace() shouldBe false
    }

    test("only a failed or refused save keeps the checked archive to save again") {
        val readback = BackupVerificationException(BackupVerificationException.Problem.ReadbackFailed)
        ManualBackupSaveException(FailedSaveCleanup.Removed, readback).leavesArchiveToSaveAgain() shouldBe true
        PickedDocumentRefusedException(PickedDocumentRefusedException.Reason.NotEmpty).leavesArchiveToSaveAgain() shouldBe true
        // A check that failed while the archive was prepared means there's nothing checked to save.
        BackupVerificationException(BackupVerificationException.Problem.Damaged).leavesArchiveToSaveAgain() shouldBe false
        IllegalStateException("Prepared backup was not checked.").leavesArchiveToSaveAgain() shouldBe false
    }

    test("a save that ends while the screen is turning still reaches the new screen") {
        val uuid = "turned-${System.nanoTime()}"
        val failed = ManualBackupSaveOutcomes.Outcome.Failed("write failed", hasOwnAdvice = false, keptToSaveAgain = false)
        // Reported before the recreated screen asks, as when the old one is already gone.
        ManualBackupSaveOutcomes.report(uuid, failed)

        ManualBackupSaveOutcomes.take(uuid) shouldBe failed
    }

    test("a workspace stays held until every screen holding it lets go") {
        val uuid = "held-${System.nanoTime()}"
        ManualBackupSaveOutcomes.isHeld(uuid) shouldBe false
        ManualBackupSaveOutcomes.hold(uuid)
        ManualBackupSaveOutcomes.hold(uuid)
        ManualBackupSaveOutcomes.release(uuid)
        ManualBackupSaveOutcomes.isHeld(uuid) shouldBe true
        ManualBackupSaveOutcomes.release(uuid)
        ManualBackupSaveOutcomes.isHeld(uuid) shouldBe false
    }

    test("a workspace stays held while the phone turns, between the old screen and the new one") {
        val uuid = "turning-${System.nanoTime()}"
        ManualBackupSaveOutcomes.hold(uuid)
        // The old screen is disposed before the recreated one composes.
        ManualBackupSaveOutcomes.handOver(uuid)
        ManualBackupSaveOutcomes.isHeld(uuid) shouldBe true

        // The recreated screen takes that hold over instead of adding one, so leaving lets go.
        ManualBackupSaveOutcomes.hold(uuid)
        ManualBackupSaveOutcomes.isHeld(uuid) shouldBe true
        ManualBackupSaveOutcomes.release(uuid)
        ManualBackupSaveOutcomes.isHeld(uuid) shouldBe false
    }

    test("leaving for good drops an outcome nobody took, and one still to come") {
        val kept = ManualBackupSaveOutcomes.Outcome.Failed("write failed", hasOwnAdvice = false, keptToSaveAgain = true)

        // Reported while the screen held it, then Back before the screen took it.
        val waiting = "waiting-${System.nanoTime()}"
        ManualBackupSaveOutcomes.hold(waiting)
        ManualBackupSaveOutcomes.report(waiting, kept)
        ManualBackupSaveOutcomes.release(waiting)
        ManualBackupSaveOutcomes.abandon(waiting, saveRunning = true) shouldBe kept
        withTimeoutOrNull(100L) { ManualBackupSaveOutcomes.take(waiting) } shouldBe null

        // Back while the save still runs: its later outcome isn't left waiting for nobody.
        val running = "running-${System.nanoTime()}"
        ManualBackupSaveOutcomes.hold(running)
        ManualBackupSaveOutcomes.release(running)
        ManualBackupSaveOutcomes.abandon(running, saveRunning = true) shouldBe null
        ManualBackupSaveOutcomes.report(running, kept)
        withTimeoutOrNull(100L) { ManualBackupSaveOutcomes.take(running) } shouldBe null
        // Only that one outcome is dropped: a later save under the same workspace reports again.
        ManualBackupSaveOutcomes.report(running, kept)
        ManualBackupSaveOutcomes.take(running) shouldBe kept
    }

    test("a screen still holding the workspace keeps its outcome when another leaves") {
        val uuid = "shared-${System.nanoTime()}"
        val failed = ManualBackupSaveOutcomes.Outcome.Failed("write failed", hasOwnAdvice = false, keptToSaveAgain = false)
        ManualBackupSaveOutcomes.hold(uuid)
        ManualBackupSaveOutcomes.hold(uuid)
        ManualBackupSaveOutcomes.report(uuid, failed)
        ManualBackupSaveOutcomes.release(uuid)

        ManualBackupSaveOutcomes.abandon(uuid, saveRunning = true) shouldBe null
        ManualBackupSaveOutcomes.take(uuid) shouldBe failed
        ManualBackupSaveOutcomes.release(uuid)
    }

    test("a screen going for a turn keeps its hold for the new one, and one leaving for good lets go") {
        val kept = ManualBackupSaveOutcomes.Outcome.Failed("write failed", hasOwnAdvice = false, keptToSaveAgain = true)

        val turned = "screen-turned-${System.nanoTime()}"
        ManualBackupSaveOutcomes.hold(turned)
        ManualBackupSaveOutcomes.screenGone(turned, turning = true, stillShown = true, saveRunning = true) shouldBe null
        ManualBackupSaveOutcomes.isHeld(turned) shouldBe true
        // A save failing before the recreated screen holds the workspace still finds it held.
        ManualBackupSaveOutcomes.report(turned, kept)
        ManualBackupSaveOutcomes.hold(turned)
        ManualBackupSaveOutcomes.take(turned) shouldBe kept
        // Then that screen leaves for good, with nothing left waiting.
        ManualBackupSaveOutcomes.screenGone(turned, turning = false, stillShown = true, saveRunning = false) shouldBe null
        ManualBackupSaveOutcomes.isHeld(turned) shouldBe false

        val left = "screen-left-${System.nanoTime()}"
        ManualBackupSaveOutcomes.hold(left)
        ManualBackupSaveOutcomes.report(left, kept)
        // Leaving for good hands back the outcome nobody took, so its kept archive gets closed.
        ManualBackupSaveOutcomes.screenGone(left, turning = false, stillShown = true, saveRunning = false) shouldBe kept
        ManualBackupSaveOutcomes.isHeld(left) shouldBe false
        withTimeoutOrNull(100L) { ManualBackupSaveOutcomes.take(left) } shouldBe null

        // Moving on from a workspace, turning or not, lets go of it without dropping anything.
        for (turning in listOf(false, true)) {
            val movedOn = "screen-moved-on-$turning-${System.nanoTime()}"
            ManualBackupSaveOutcomes.hold(movedOn)
            ManualBackupSaveOutcomes.screenGone(movedOn, turning = turning, stillShown = false, saveRunning = true) shouldBe null
            ManualBackupSaveOutcomes.isHeld(movedOn) shouldBe false
            ManualBackupSaveOutcomes.report(movedOn, kept)
            ManualBackupSaveOutcomes.take(movedOn) shouldBe kept
        }
    }

    test("an archive that changed size since its check isn't offered to save again") {
        val root = Files.createTempDirectory("kept-archive").toFile()
        try {
            val bytes = ByteArray(1024) { it.toByte() }
            val archive = File(root, "kept.sfbak").also { it.writeBytes(bytes) }
            val receipt = BackupReceipt(
                archiveVersion = Backup.CURRENT_ARCHIVE_FORMAT_VERSION,
                encrypted = false,
                sections = Backup.scheduledSelection(),
                byteSize = bytes.size.toLong(),
                sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },
            )
            receipt.stillDescribes(archive) shouldBe true

            archive.writeBytes(bytes.copyOf(512))
            receipt.stillDescribes(archive) shouldBe false
            archive.delete()
            receipt.stillDescribes(archive) shouldBe false
        } finally {
            root.deleteRecursively()
        }
    }
})
