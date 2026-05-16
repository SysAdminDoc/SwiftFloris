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

package dev.patrickgold.florisboard.app.ext

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.nio.file.Files

class ExtensionEditorFileNamesTest : FunSpec({
    test("sanitizeFileName removes provider path and platform-unsafe characters") {
        ExtensionEditorFileNames.sanitizeFileName("../unsafe:name?.png", "fallback.png") shouldBe
            "unsafe_name_.png"
        ExtensionEditorFileNames.sanitizeFileName("..\\nested\\font.ttf", "fallback.ttf") shouldBe
            "font.ttf"
        ExtensionEditorFileNames.sanitizeFileName("...", "fallback.ttf") shouldBe "fallback.ttf"
    }

    test("safeFileIn accepts only direct child file names") {
        val root = Files.createTempDirectory("extension-files").toFile()
        try {
            ExtensionEditorFileNames.safeFileIn(root, "image.png")?.canonicalPath shouldBe
                root.resolve("image.png").canonicalPath
            ExtensionEditorFileNames.safeFileIn(root, "../escape.png").shouldBeNull()
            ExtensionEditorFileNames.safeFileIn(root, "nested/file.png").shouldBeNull()
            ExtensionEditorFileNames.safeFileIn(root, "").shouldBeNull()
            ExtensionEditorFileNames.safeFileIn(root, "..").shouldBeNull()
        } finally {
            root.deleteRecursively()
        }
    }
})
