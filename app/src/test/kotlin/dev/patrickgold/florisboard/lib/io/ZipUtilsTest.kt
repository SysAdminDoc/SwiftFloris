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

    test("unzip ignores path traversal entries") {
        val root = Files.createTempDirectory("floris-zip-test").toFile()
        try {
            val archive = root.subFile("traversal.zip")
            writeZip(archive, "../escape.txt" to "bad", "safe/file.txt" to "good")
            val destination = root.subDir("out")

            ZipUtils.unzip(archive, destination)

            root.subFile("escape.txt").exists() shouldBe false
            destination.subFile("safe/file.txt").readText() shouldBe "good"
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
