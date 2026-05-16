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

package dev.patrickgold.florisboard.ime.theme

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files

class FlorisAssetResolverTest : FunSpec({
    test("resolveFlexAssetPath resolves flex paths relative to the loaded theme directory") {
        val root = Files.createTempDirectory("theme-root").toFile()
        try {
            val asset = root.resolve("images/icon.png")
            asset.parentFile.mkdirs()
            asset.writeText("png")

            resolveFlexAssetPath(root, "flex:/images/icon.png").getOrThrow() shouldBe asset.canonicalPath
        } finally {
            root.deleteRecursively()
        }
    }

    test("resolveFlexAssetPath rejects sibling-prefix traversal") {
        val parent = Files.createTempDirectory("theme-parent").toFile()
        try {
            val root = parent.resolve("theme")
            val sibling = parent.resolve("theme-sibling")
            root.mkdirs()
            sibling.mkdirs()
            sibling.resolve("stolen.png").writeText("bad")

            resolveFlexAssetPath(root, "flex:/../theme-sibling/stolen.png").isFailure shouldBe true
        } finally {
            parent.deleteRecursively()
        }
    }
})
