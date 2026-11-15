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

package dev.patrickgold.florisboard.ime.media.sticker

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.io.ByteArrayInputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class LocalStickerPackRepositoryTest : FunSpec({
    test("imports a local image into an app-private sticker pack") {
        val dir = tempDir()
        val image = dir.resolve("thumbs-up.PNG").apply {
            writeBytes(samplePngBytes())
        }

        LocalStickerPackRepository.importStickerFile(
            storageDir = dir,
            sourceFile = image,
            declaredMimeType = null,
        ) shouldBe LocalStickerPackResult.Success(1)

        val pack = LocalStickerPackRepository.loadPack(dir) { id -> "content://local/$id" }.shouldNotBeNull()
        pack.id shouldBe LocalStickerPackRepository.PackId
        pack.name shouldBe LocalStickerPackRepository.PackName
        pack.stickers shouldHaveSize 1
        val sticker = pack.stickers.single()
        sticker.label shouldBe "thumbs up"
        sticker.mimeType shouldBe "image/png"
        sticker.sourceUri shouldBe "content://local/${sticker.id}"
        StickerSearch.search(listOf(pack), "thumbs").single().id shouldBe sticker.id
    }

    test("subsequent sticker imports preserve existing manifest entries") {
        val dir = tempDir()
        val first = dir.resolve("thumbs-up.png").apply {
            writeBytes(samplePngBytes())
        }
        val second = dir.resolve("Friday_Dance.webp").apply {
            writeBytes(sampleWebpBytes())
        }

        LocalStickerPackRepository.importStickerFile(
            storageDir = dir,
            sourceFile = first,
            declaredMimeType = "image/png",
        ) shouldBe LocalStickerPackResult.Success(1)
        LocalStickerPackRepository.importStickerFile(
            storageDir = dir,
            sourceFile = second,
            declaredMimeType = "image/webp",
        ) shouldBe LocalStickerPackResult.Success(1)

        val pack = LocalStickerPackRepository.loadPack(dir) { id -> "content://local/$id" }.shouldNotBeNull()
        pack.stickers shouldHaveSize 2
        pack.stickers.map { it.label }.toSet() shouldBe setOf("Friday Dance", "thumbs up")
        dir.listFiles { file ->
            file.name.startsWith(LocalStickerPackRepository.ManifestFileName) && file.name.endsWith(".tmp")
        }?.toList().orEmpty() shouldBe emptyList()
    }

    test("exports and imports a portable sticker-pack archive") {
        val sourceDir = tempDir()
        val image = sourceDir.resolve("Friday_Dance.webp").apply {
            writeBytes(sampleWebpBytes())
        }
        LocalStickerPackRepository.importStickerFile(
            storageDir = sourceDir,
            sourceFile = image,
            declaredMimeType = "image/webp",
        ) shouldBe LocalStickerPackResult.Success(1)

        val archive = sourceDir.resolve(LocalStickerPackRepository.DefaultArchiveFileName)
        archive.outputStream().use { output ->
            LocalStickerPackRepository.exportArchive(sourceDir, output) shouldBe LocalStickerPackResult.Success(1)
        }
        ZipFile(archive).use { zip ->
            zip.getEntry(LocalStickerPackRepository.ManifestFileName).shouldNotBeNull()
            zip.entries().asSequence().filter { it.name.startsWith("stickers/") }.toList() shouldHaveSize 1
        }

        val importedDir = tempDir()
        LocalStickerPackRepository.importArchive(importedDir, archive) shouldBe LocalStickerPackResult.Success(1)
        val importedPack = LocalStickerPackRepository.loadPack(importedDir) { id -> "content://imported/$id" }
            .shouldNotBeNull()
        importedPack.stickers shouldHaveSize 1
        importedPack.stickers.single().label shouldBe "Friday Dance"
        importedPack.stickers.single().mimeType shouldBe "image/webp"
    }

    test("rejects unsupported MIME types and oversized sticker files") {
        val dir = tempDir()
        val unsupported = dir.resolve("manual.png").apply {
            writeBytes(byteArrayOf(0x01))
        }
        val unsupportedResult = LocalStickerPackRepository.importStickerFile(
            storageDir = dir,
            sourceFile = unsupported,
            declaredMimeType = "application/pdf",
        ) as LocalStickerPackResult.Failure
        unsupportedResult.reason shouldBe LocalStickerPackFailure.UNSUPPORTED_MIME_TYPE

        val oversized = dir.resolve("huge.png")
        RandomAccessFile(oversized, "rw").use { file ->
            file.setLength(LocalStickerPackRepository.MaxStickerBytes + 1L)
        }
        val oversizedResult = LocalStickerPackRepository.importStickerFile(
            storageDir = dir,
            sourceFile = oversized,
            declaredMimeType = "image/png",
        ) as LocalStickerPackResult.Failure
        oversizedResult.reason shouldBe LocalStickerPackFailure.OVERSIZED
    }

    test("rejects declared image MIME when file bytes are not an image") {
        val dir = tempDir()
        val fake = dir.resolve("fake.png").apply {
            writeText("not actually an image")
        }

        val result = LocalStickerPackRepository.importStickerFile(
            storageDir = dir,
            sourceFile = fake,
            declaredMimeType = "image/png",
        ) as LocalStickerPackResult.Failure

        result.reason shouldBe LocalStickerPackFailure.UNSUPPORTED_MIME_TYPE
        LocalStickerPackRepository.loadPack(dir) shouldBe null
        dir.resolve(LocalStickerPackRepository.ManifestFileName).exists() shouldBe false
    }

    test("does not replace an unreadable manifest with an empty manifest") {
        val dir = tempDir()
        val manifest = dir.resolve(LocalStickerPackRepository.ManifestFileName)
        val originalBytes = byteArrayOf(0x13, 0x37, 0x42)
        manifest.writeBytes(originalBytes)
        val image = dir.resolve("new.png").apply { writeBytes(samplePngBytes()) }

        val result = LocalStickerPackRepository.importStickerFile(
            storageDir = dir,
            sourceFile = image,
            declaredMimeType = "image/png",
        ) as LocalStickerPackResult.Failure

        result.reason shouldBe LocalStickerPackFailure.IO_ERROR
        manifest.readBytes().toList() shouldBe originalBytes.toList()
        LocalStickerPackRepository.storageState.value shouldBe LocalStickerPackStorageState.UNREADABLE
    }

    test("distinguishes a valid empty pack from an unreadable manifest") {
        val dir = tempDir()

        LocalStickerPackRepository.loadPack(dir) shouldBe null
        LocalStickerPackRepository.storageState.value shouldBe LocalStickerPackStorageState.EMPTY

        dir.resolve(LocalStickerPackRepository.ManifestFileName).writeText("not json")
        LocalStickerPackRepository.loadPack(dir) shouldBe null
        LocalStickerPackRepository.storageState.value shouldBe LocalStickerPackStorageState.UNREADABLE
    }

    test("rejects sticker-pack archives with unsafe file paths") {
        val dir = tempDir()
        val archive = dir.resolve("unsafe.sfstickers")
        ZipOutputStream(archive.outputStream()).use { zip ->
            val manifest = LocalStickerPackManifest(
                stickers = listOf(
                    LocalStickerPackEntry(
                        id = "unsafe",
                        fileName = "../escape.png",
                        displayName = "escape.png",
                        mimeType = "image/png",
                        label = "escape",
                    ),
                ),
            )
            zip.putNextEntry(ZipEntry(LocalStickerPackRepository.ManifestFileName))
            zip.write(Json.encodeToString(manifest).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("stickers/../escape.png"))
            zip.write(byteArrayOf(0x01))
            zip.closeEntry()
        }

        val result = LocalStickerPackRepository.importArchive(tempDir(), archive) as LocalStickerPackResult.Failure
        result.reason shouldBe LocalStickerPackFailure.INVALID_ARCHIVE
    }

    test("two imports running at once keep each other's stickers") {
        // A share import still copying its image must not write back the manifest it read
        // before another import finished, or that import's sticker disappears from the pack.
        val dir = tempDir()
        val copyStarted = CountDownLatch(1)
        val finishCopy = CountDownLatch(1)
        val slowImage = object : ByteArrayInputStream(samplePngBytes()) {
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                copyStarted.countDown()
                check(finishCopy.await(5, TimeUnit.SECONDS)) { "The test never released the copy." }
                return super.read(b, off, len)
            }

            override fun read(b: ByteArray): Int = read(b, 0, b.size)
        }
        var slowResult: LocalStickerPackResult? = null
        val slowImport = Thread {
            slowResult = LocalStickerPackRepository.importStickerStream(
                storageDir = dir,
                displayName = "slow.png",
                declaredMimeType = "image/png",
                declaredSizeBytes = null,
                streamProvider = { slowImage },
            )
        }
        slowImport.start()
        copyStarted.await(5, TimeUnit.SECONDS) shouldBe true

        val quickImage = dir.resolve("quick.png").apply { writeBytes(samplePngBytes()) }
        LocalStickerPackRepository.importStickerFile(dir, quickImage) shouldBe LocalStickerPackResult.Success(1)
        finishCopy.countDown()
        slowImport.join(5_000L)

        slowResult shouldBe LocalStickerPackResult.Success(1)
        val pack = LocalStickerPackRepository.loadPack(dir) { id -> "content://local/$id" }.shouldNotBeNull()
        pack.stickers.map { it.label }.sorted() shouldBe listOf("quick", "slow")
    }

    test("an import that finishes during a restore keeps its sticker in the restored pack") {
        // A restore copies the backed-up pack over the folder. If an import saved its manifest
        // in the middle of that copy, the restored manifest would overwrite it and lose the sticker.
        val backup = tempDir()
        val backupImage = tempDir().resolve("restored.png").apply { writeBytes(samplePngBytes()) }
        LocalStickerPackRepository.importStickerFile(backup, backupImage) shouldBe LocalStickerPackResult.Success(1)
        val dir = tempDir()
        val restoreStarted = CountDownLatch(1)
        val finishRestore = CountDownLatch(1)
        val restore = Thread {
            LocalStickerPackRepository.replacingPack {
                restoreStarted.countDown()
                check(finishRestore.await(5, TimeUnit.SECONDS)) { "The test never released the restore." }
                backup.copyRecursively(dir, overwrite = true)
            }
        }.apply { isDaemon = true }
        restore.start()
        restoreStarted.await(5, TimeUnit.SECONDS) shouldBe true

        val quickImage = tempDir().resolve("quick.png").apply { writeBytes(samplePngBytes()) }
        var importResult: LocalStickerPackResult? = null
        val importer = Thread { importResult = LocalStickerPackRepository.importStickerFile(dir, quickImage) }
            .apply { isDaemon = true }
        importer.start()
        val deadline = System.nanoTime() + 5_000_000_000L
        while (importer.state != Thread.State.BLOCKED) {
            check(importer.isAlive) { "The import saved its sticker without waiting for the restore." }
            check(System.nanoTime() < deadline) { "The import never reached the lock." }
            Thread.yield()
        }
        finishRestore.countDown()
        restore.join(5_000L)
        importer.join(5_000L)

        importResult shouldBe LocalStickerPackResult.Success(1)
        val pack = LocalStickerPackRepository.loadPack(dir) { id -> "content://local/$id" }.shouldNotBeNull()
        pack.stickers.map { it.label }.sorted() shouldBe listOf("quick", "restored")
    }

    test("copying the pack for a backup or rollback leaves out an import's staged files") {
        val dir = tempDir()
        val image = tempDir().resolve("saved.png").apply { writeBytes(samplePngBytes()) }
        LocalStickerPackRepository.importStickerFile(dir, image) shouldBe LocalStickerPackResult.Success(1)
        // An import still running has its files staged here, outside the pack.
        val staged = dir.resolve("tmp").resolve("in-flight").apply { mkdirs() }.resolve("sticker.png")
        staged.writeBytes(samplePngBytes())
        val copy = tempDir().resolve("copy")

        LocalStickerPackRepository.copyPack(dir, copy)

        copy.resolve("tmp").exists() shouldBe false
        val pack = LocalStickerPackRepository.loadPack(copy) { id -> "content://local/$id" }.shouldNotBeNull()
        pack.stickers.map { it.label } shouldBe listOf("saved")
        // The staging folder in the pack itself stays for the import that owns it.
        staged.exists() shouldBe true
    }

    test("clearing the pack for a restore or rollback keeps an import's staged files") {
        val dir = tempDir()
        val image = tempDir().resolve("saved.png").apply { writeBytes(samplePngBytes()) }
        LocalStickerPackRepository.importStickerFile(dir, image) shouldBe LocalStickerPackResult.Success(1)
        val staged = dir.resolve("tmp").resolve("in-flight").apply { mkdirs() }.resolve("sticker.png")
        staged.writeBytes(samplePngBytes())

        LocalStickerPackRepository.clearPack(dir) shouldBe true

        dir.list()!!.toList() shouldBe listOf("tmp")
        staged.exists() shouldBe true
        LocalStickerPackRepository.clearPack(tempDir().resolve("missing")) shouldBe true
    }
})

private fun tempDir(): File {
    return Files.createTempDirectory("swiftfloris-local-stickers-").toFile().also { it.deleteOnExit() }
}

private fun samplePngBytes(): ByteArray {
    return byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47,
        0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x00,
    )
}

private fun sampleWebpBytes(): ByteArray {
    return byteArrayOf(
        0x52, 0x49, 0x46, 0x46,
        0x04, 0x00, 0x00, 0x00,
        0x57, 0x45, 0x42, 0x50,
        0x56, 0x50, 0x38, 0x20,
    )
}
