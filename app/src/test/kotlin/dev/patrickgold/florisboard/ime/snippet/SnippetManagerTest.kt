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

package dev.patrickgold.florisboard.ime.snippet

import io.kotest.core.spec.style.FunSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files

class SnippetManagerTest : FunSpec({

    test("an imported file keeps a loadable extension whatever the source name was") {
        // The Settings screen passes uri.lastPathSegment, which for a SAF
        // document URI is an id rather than a filename. Mapping the disallowed
        // characters to underscores used to strip the extension with them, and
        // loadAll lists .yml/.yaml only, so the file was written, counted as
        // imported, and then invisible in the list and undeletable.
        val root = Files.createTempDirectory("snippet-names").toFile()
        try {
            val manager = SnippetManager(root)
            manager.sanitizeFileName("msf:1000000123") shouldBe "msf_1000000123.yml"
            manager.sanitizeFileName("primary:Download/snips.yml") shouldBe
                "primary_Download_snips.yml"
            manager.sanitizeFileName("plain.yaml") shouldBe "plain.yaml"
            manager.sanitizeFileName("UPPER.YML") shouldBe "UPPER.YML"
            manager.sanitizeFileName("") shouldBe "import.yml"
            manager.sanitizeFileName("...") shouldBe "import.yml"
        } finally {
            root.deleteRecursively()
        }
    }

    test("a document-id import round-trips through the file list and delete") {
        val root = Files.createTempDirectory("snippet-import").toFile()
        try {
            val manager = SnippetManager(root)
            runBlocking { manager.initialize().join() }

            val imported = runBlocking {
                manager.importYaml(
                    """
                        matches:
                          - trigger: ":sig"
                            replace: "Sent from SwiftFloris"
                    """.trimIndent(),
                    filename = "msf:1000000123",
                )
            }

            imported.importedCount shouldBe 1
            // Visible: the screen lists what loadAll found, so an unlisted file
            // is one the user cannot see or remove.
            val listed = manager.fileStates.value.single()
            listed.filename shouldBe "msf_1000000123.yml"
            manager.snippets.value.single() shouldBe EspansoMatch(":sig", "Sent from SwiftFloris")

            // Removable: the delete action passes the listed name straight back.
            runBlocking { manager.removeFile(listed.filename) } shouldBe true
            manager.fileStates.value.isEmpty() shouldBe true
        } finally {
            root.deleteRecursively()
        }
    }

    test("initialize loads snippets for IME expansion without opening Settings") {
        val root = Files.createTempDirectory("snippet-manager").toFile()
        try {
            val snippetsDirectory = File(root, "snippets").apply { mkdirs() }
            File(snippetsDirectory, "personal.yml").writeText(
                """
                    matches:
                      - trigger: ":addr"
                        replace: "123 Privacy Lane"
                """.trimIndent(),
            )
            val manager = SnippetManager(root)

            runBlocking { manager.initialize().join() }

            manager.snippets.value.single() shouldBe EspansoMatch(":addr", "123 Privacy Lane")
            val expansion = SnippetExpansionPolicy.findMatch(
                textBeforeCursor = "Mail :addr",
                snippets = manager.snippets.value,
                isSensitiveField = false,
            )
            expansion?.triggerLength shouldBe 5
            expansion?.replacement shouldBe "123 Privacy Lane"
        } finally {
            root.deleteRecursively()
        }
    }

    test("load keeps valid files and reports oversized files without swallowing the failure") {
        val root = Files.createTempDirectory("snippet-manager-load").toFile()
        try {
            val snippetsDirectory = File(root, "snippets").apply { mkdirs() }
            File(snippetsDirectory, "valid.yml").writeText(
                """
                    matches:
                      - trigger: ":ok"
                        replace: "Loaded"
                """.trimIndent(),
            )
            File(snippetsDirectory, "oversized.yml").writeText(
                "x".repeat((SnippetImportPolicy.MaxYamlBytes + 1L).toInt()),
            )
            val manager = SnippetManager(root)

            runBlocking { manager.loadAll() }

            manager.snippets.value.map { it.trigger } shouldBe listOf(":ok")
            manager.fileStates.value shouldBe listOf(SnippetFileInfo("valid.yml", 1))
            manager.loadReport.value.skippedFileCount shouldBe 1
        } finally {
            root.deleteRecursively()
        }
    }

    test("import runs through the bounded parser and sanitizes hostile filenames") {
        val root = Files.createTempDirectory("snippet-manager-import").toFile()
        try {
            val manager = SnippetManager(root)
            val result = runBlocking {
                manager.importYaml(
                    """
                        matches:
                          - trigger: ":imported"
                            replace: "Imported"
                    """.trimIndent(),
                    "../private/notes.yml",
                )
            }

            result.importedCount shouldBe 1
            manager.sanitizeFileName("../private/notes.yml") shouldBe ".._private_notes.yml"
            manager.fileStates.value shouldBe listOf(SnippetFileInfo(".._private_notes.yml", 1))
            File(root, "snippets/.._private_notes.yml").isFile shouldBe true
        } finally {
            root.deleteRecursively()
        }
    }

    test("import rejects YAML over the safety limit before writing") {
        val root = Files.createTempDirectory("snippet-manager-limit").toFile()
        try {
            val manager = SnippetManager(root)
            shouldThrow<IllegalArgumentException> {
                runBlocking {
                    manager.importYaml(
                        "x".repeat((SnippetImportPolicy.MaxYamlBytes + 1L).toInt()),
                        "too-large.yml",
                    )
                }
            }

            File(root, "snippets/too-large.yml").exists() shouldBe false
        } finally {
            root.deleteRecursively()
        }
    }

    test("remove and clear surface deletion failures instead of reporting success") {
        val root = Files.createTempDirectory("snippet-manager-delete-failure").toFile()
        try {
            val snippetsDirectory = File(root, "snippets").apply { mkdirs() }
            File(snippetsDirectory, "blocked.yml").apply {
                mkdirs()
                File(this, "child.yml").writeText("not a file")
            }
            val manager = SnippetManager(root)

            runBlocking { manager.removeFile("blocked.yml") } shouldBe false
            runBlocking { manager.clearAll() } shouldBe false
            File(snippetsDirectory, "blocked.yml").isDirectory shouldBe true
        } finally {
            root.deleteRecursively()
        }
    }

    test("clearAll removes imported files and resets file state") {
        val root = Files.createTempDirectory("snippet-manager-clear").toFile()
        try {
            val manager = SnippetManager(root)
            runBlocking {
                manager.importYaml(
                    """
                        matches:
                          - trigger: ":one"
                            replace: "One"
                    """.trimIndent(),
                    "one.yml",
                )
                manager.clearAll() shouldBe true
            }

            manager.snippets.value shouldBe emptyList()
            manager.fileStates.value shouldBe emptyList()
        } finally {
            root.deleteRecursively()
        }
    }
})
