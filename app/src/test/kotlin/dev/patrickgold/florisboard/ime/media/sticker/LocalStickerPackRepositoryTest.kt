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
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
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
