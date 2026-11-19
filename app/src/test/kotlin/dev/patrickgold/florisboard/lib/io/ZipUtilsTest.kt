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

package dev.patrickgold.florisboard.lib.io

import io.kotest.core.spec.style.FunSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.shouldBe
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.florisboard.lib.kotlin.io.subDir
import org.florisboard.lib.kotlin.io.subFile

class ZipUtilsTest : FunSpec({
    test("unzip creates missing parent directories for nested file entries") {
        val root = Files.createTempDirectory("floris-zip-test").toFile()
        try {
            val archive = root.subFile("nested.zip")
            writeZip(archive, "nested/path/file.txt" to "ok")
            val destination = root.subDir("out")

            ZipUtils.unzip(archive, destination)

            destination.subFile("nested/path/file.txt").readText() shouldBe "ok"
        } finally {
            root.deleteRecursively()
        }
    }

    test("unzip aborts path traversal entries") {
        val root = Files.createTempDirectory("floris-zip-test").toFile()
        try {
            val archive = root.subFile("traversal.zip")
            writeZip(archive, "../escape.txt" to "bad", "safe/file.txt" to "good")
            val destination = root.subDir("out")

            shouldThrow<SecurityException> {
                ZipUtils.unzip(archive, destination)
            }

            root.subFile("escape.txt").exists() shouldBe false
            destination.subFile("safe/file.txt").exists() shouldBe false
        } finally {
            root.deleteRecursively()
        }
    }

    test("unzip stops an archive once its inflated bytes pass the budget, even when entries are left out") {
        val root = Files.createTempDirectory("floris-zip-test").toFile()
        try {
            val archive = root.subFile("budget.zip")
            // Four entries fill most of the budget, then each further entry fits the per-entry cap but not what's
            // left. Leaving those out without charging them let every one inflate the same remainder again.
            val chunk = "a".repeat(60 * 1024)
            val entries = (1..10).map { "big/$it.bin" to chunk }
            writeZip(archive, "first.txt" to "ok", *entries.toTypedArray(), "last.txt" to "never")
            val destination = root.subDir("out")
            val budget = ArchiveWorkBudget(maxBytes = 256L * 1024)

            shouldThrow<SecurityException> {
                ZipUtils.unzip(archive, destination, maxEntryBytes = 64L * 1024, budget = budget)
            }

            budget.usedBytes shouldBeLessThanOrEqual 256L * 1024 + DEFAULT_BUFFER_SIZE
            destination.exists() shouldBe false
            root.walk().filter { it.isFile }.toList() shouldBe listOf(archive)
        } finally {
            root.deleteRecursively()
        }
    }

    test("unzip leaves out an entry over the per-entry cap and extracts the rest") {
        val root = Files.createTempDirectory("floris-zip-test").toFile()
        try {
            val archive = root.subFile("oversized.zip")
            writeZip(archive, "first.txt" to "ok", "big.bin" to "a".repeat(100 * 1024), "last.txt" to "fine")
            val destination = root.subDir("out")

            ZipUtils.unzip(archive, destination, maxEntryBytes = 64L * 1024, budget = ArchiveWorkBudget(1024L * 1024))

            destination.subFile("first.txt").readText() shouldBe "ok"
            destination.subFile("big.bin").exists() shouldBe false
            destination.subFile("last.txt").readText() shouldBe "fine"
        } finally {
            root.deleteRecursively()
        }
    }

    test("a failed unzip removes the files and folders it created") {
        val root = Files.createTempDirectory("floris-zip-test").toFile()
        try {
            val archive = root.subFile("late-traversal.zip")
            writeZip(archive, "safe/nested/file.txt" to "good", "../escape.txt" to "bad")
            val destination = root.subDir("out")

            shouldThrow<SecurityException> {
                ZipUtils.unzip(archive, destination)
            }

            destination.exists() shouldBe false
            root.subFile("escape.txt").exists() shouldBe false
        } finally {
            root.deleteRecursively()
        }
    }

    test("a failed unzip keeps what was in the destination before") {
        val root = Files.createTempDirectory("floris-zip-test").toFile()
        try {
            val archive = root.subFile("late-traversal.zip")
            writeZip(archive, "new.txt" to "good", "../escape.txt" to "bad")
            val destination = root.subDir("out").apply { mkdirs() }
            destination.subFile("kept.txt").writeText("mine")

            shouldThrow<SecurityException> {
                ZipUtils.unzip(archive, destination)
            }

            destination.subFile("kept.txt").readText() shouldBe "mine"
            destination.subFile("new.txt").exists() shouldBe false
        } finally {
            root.deleteRecursively()
        }
    }

    test("archive text reads enforce UTF-8 byte limits before returning content") {
        val root = Files.createTempDirectory("floris-zip-test").toFile()
        val archive = root.subFile("bounded.flex")
        try {
            writeZip(archive, "extension.json" to "éé")

            ZipUtils.readFileFromArchive(
                srcFile = archive,
                relPath = "extension.json",
                maxBytes = 4L,
            ).getOrThrow() shouldBe "éé"
            ZipUtils.readFileFromArchive(
                srcFile = archive,
                relPath = "extension.json",
                maxBytes = 3L,
            ).exceptionOrNull().shouldBeInstanceOf<ArchiveEntryTooLargeException>()
            ZipUtils.validateFileInArchive(
                srcFile = archive,
                relPath = "extension.json",
                maxBytes = 3L,
            ).exceptionOrNull().shouldBeInstanceOf<ArchiveEntryTooLargeException>()
        } finally {
            root.deleteRecursively()
        }
    }

    test("zip replacement validates the staged archive before replacing the previous archive") {
        val root = Files.createTempDirectory("floris-zip-test").toFile()
        val source = root.subFile("source").also { it.mkdirs() }
        val archive = root.subFile("extension.flex")
        try {
            source.subFile("extension.json").writeText("replacement")
            writeZip(archive, "extension.json" to "previous")

            shouldThrow<IllegalStateException> {
                ZipUtils.zip(source, archive) { stagedArchive ->
                    ZipUtils.readFileFromArchive(
                        stagedArchive,
                        "extension.json",
                    ).getOrThrow() shouldBe "replacement"
                    ZipUtils.readFileFromArchive(
                        archive,
                        "extension.json",
                    ).getOrThrow() shouldBe "previous"
                    error("injected archive validation failure")
                }
            }

            ZipUtils.readFileFromArchive(
                archive,
                "extension.json",
            ).getOrThrow() shouldBe "previous"
            root.listFiles().orEmpty()
                .filter { it.name.startsWith(".extension.flex.") && it.name.endsWith(".tmp") }
                .size shouldBe 0
        } finally {
            root.deleteRecursively()
        }
    }
})

private fun writeZip(file: File, vararg entries: Pair<String, String>) {
    FileOutputStream(file).use { fileOut ->
        ZipOutputStream(fileOut).use { zipOut ->
            for ((path, text) in entries) {
                zipOut.putNextEntry(ZipEntry(path))
                zipOut.write(text.toByteArray())
                zipOut.closeEntry()
            }
        }
    }
}
