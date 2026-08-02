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
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files

class SnippetManagerTest : FunSpec({

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
})
