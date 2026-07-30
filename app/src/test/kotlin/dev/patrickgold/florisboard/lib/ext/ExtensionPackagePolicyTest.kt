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

package dev.patrickgold.florisboard.lib.ext

import dev.patrickgold.florisboard.ime.keyboard.KeyboardExtension
import dev.patrickgold.florisboard.ime.keyboard.LayoutArrangementComponent
import dev.patrickgold.florisboard.ime.nlp.LanguagePackExtension
import dev.patrickgold.florisboard.ime.nlp.LanguagePackKind
import dev.patrickgold.florisboard.ime.theme.ThemeExtension
import dev.patrickgold.florisboard.ime.theme.ThemeExtensionComponentImpl
import dev.patrickgold.florisboard.lib.io.loadJsonAsset
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.florisboard.lib.kotlin.io.subDir
import org.florisboard.lib.kotlin.io.subFile
import java.io.File
import java.nio.file.Files

class ExtensionPackagePolicyTest : FunSpec({
    test("manifest and component JSON limits are stable trust-boundary constants") {
        ExtensionPackagePolicy.MAX_MANIFEST_BYTES shouldBe 256L * 1024L
        ExtensionPackagePolicy.MAX_COMPONENT_JSON_BYTES shouldBe 1L * 1024L * 1024L

        ExtensionPackagePolicy.requireManifestSize(ExtensionPackagePolicy.MAX_MANIFEST_BYTES)
        rejectedReason {
            ExtensionPackagePolicy.requireManifestSize(
                ExtensionPackagePolicy.MAX_MANIFEST_BYTES + 1L,
            )
        } shouldBe ExtensionQuarantineReason.MANIFEST_TOO_LARGE
    }

    test("relative component paths must be canonical and stay path-segment safe") {
        listOf(
            "layouts/characters/qwerty.json",
            "stylesheets/a theme.json",
            "han.sqlite3",
        ).forEach(ExtensionPackagePolicy::requireSafeRelativePath)

        listOf(
            "../escape.json",
            "layouts/../escape.json",
            "layouts//qwerty.json",
            "./qwerty.json",
            "/absolute.json",
            "C:/drive.json",
            "layouts\\qwerty.json",
        ).forEach { path ->
            rejectedReason {
                ExtensionPackagePolicy.requireSafeRelativePath(path)
            } shouldBe ExtensionQuarantineReason.UNSAFE_COMPONENT_PATH
        }
    }

    test("keyboard manifests reject unknown layout types before runtime enum lookup") {
        val extension = keyboardExtension(
            layouts = mapOf("future-layout" to listOf(layout("safe"))),
        )

        rejectedReason {
            ExtensionPackagePolicy.inspect(extension)
        } shouldBe ExtensionQuarantineReason.UNKNOWN_LAYOUT_TYPE
    }

    test("component identifiers are safe and unique inside each namespace") {
        rejectedReason {
            ExtensionPackagePolicy.inspect(
                keyboardExtension(
                    layouts = mapOf("characters" to listOf(layout("../escape"))),
                ),
            )
        } shouldBe ExtensionQuarantineReason.INVALID_COMPONENT_ID

        rejectedReason {
            ExtensionPackagePolicy.inspect(
                keyboardExtension(
                    layouts = mapOf("characters" to listOf(layout("same"), layout("same"))),
                ),
            )
        } shouldBe ExtensionQuarantineReason.DUPLICATE_COMPONENT_ID
    }

    test("component count is explicitly capped") {
        val themes = List(ExtensionPackagePolicy.MAX_COMPONENT_COUNT + 1) { index ->
            theme("theme_$index")
        }

        rejectedReason {
            ExtensionPackagePolicy.inspect(
                ThemeExtension(meta(), themes = themes),
            )
        } shouldBe ExtensionQuarantineReason.TOO_MANY_COMPONENTS
    }

    test("extracted component JSON must exist below root and fit the byte budget") {
        val root = Files.createTempDirectory("extension-policy").toFile()
        try {
            val extractedRoot = root.subDir("extension").also { it.mkdirs() }
            val layoutFile = extractedRoot.subFile("layouts/characters/safe.json")
            layoutFile.parentFile?.mkdirs()
            layoutFile.writeText("[]")
            val extension = keyboardExtension(
                layouts = mapOf("characters" to listOf(layout("safe"))),
            )

            ExtensionPackagePolicy.validateExtracted(extension, extractedRoot)

            layoutFile.writeBytes(
                ByteArray((ExtensionPackagePolicy.MAX_COMPONENT_JSON_BYTES + 1L).toInt()),
            )
            rejectedReason {
                ExtensionPackagePolicy.validateExtracted(extension, extractedRoot)
            } shouldBe ExtensionQuarantineReason.COMPONENT_TOO_LARGE

            layoutFile.delete()
            rejectedReason {
                ExtensionPackagePolicy.validateExtracted(extension, extractedRoot)
            } shouldBe ExtensionQuarantineReason.MISSING_COMPONENT_FILE
        } finally {
            root.deleteRecursively()
        }
    }

    test("Han SQLite declarations cannot escape the extracted extension root") {
        val extension = LanguagePackExtension(
            meta = meta(),
            kind = LanguagePackKind.HAN_SHAPE_BASED,
            hanShapeBasedSQLite = "../outside.sqlite3",
        )

        rejectedReason {
            ExtensionPackagePolicy.inspect(extension)
        } shouldBe ExtensionQuarantineReason.UNSAFE_COMPONENT_PATH
    }

    test("theme stylesheet paths receive the same package validation") {
        val extension = ThemeExtension(
            meta = meta(),
            themes = listOf(theme("safe", stylesheet = "stylesheets/../outside.json")),
        )

        rejectedReason {
            ExtensionPackagePolicy.inspect(extension)
        } shouldBe ExtensionQuarantineReason.UNSAFE_COMPONENT_PATH
    }

    test("every bundled extension satisfies manifest structure and size policy") {
        val imeRoot = listOf(
            File("app/src/main/assets/ime"),
            File("src/main/assets/ime"),
        ).first { it.isDirectory }
        val groups: List<Pair<String, (String) -> Extension>> = listOf(
            "keyboard" to { raw ->
                loadJsonAsset(raw, KeyboardExtension.serializer(), ExtensionJsonConfig).getOrThrow()
            },
            "theme" to { raw ->
                loadJsonAsset(raw, ThemeExtension.serializer(), ExtensionJsonConfig).getOrThrow()
            },
            "languagepack" to { raw ->
                loadJsonAsset(raw, LanguagePackExtension.serializer(), ExtensionJsonConfig).getOrThrow()
            },
        )

        for ((relativeGroup, decode) in groups) {
            val groupDir = imeRoot.resolve(relativeGroup)
            groupDir.listFiles().orEmpty()
                .filter { it.isDirectory }
                .forEach { extensionDir ->
                    val manifest = extensionDir.resolve(ExtensionDefaults.MANIFEST_FILE_NAME)
                    ExtensionPackagePolicy.requireManifestSize(manifest.length())
                    val extension = decode(manifest.readText())
                    val inspection = ExtensionPackagePolicy.inspect(extension)
                    inspection.componentJsonPaths.forEach { relativePath ->
                        val componentFile = extensionDir.resolve(relativePath)
                        if (componentFile.isFile) {
                            (componentFile.length() <= ExtensionPackagePolicy.MAX_COMPONENT_JSON_BYTES) shouldBe true
                        }
                    }
                }
        }
    }
})

private fun rejectedReason(block: () -> Unit): ExtensionQuarantineReason {
    return shouldThrow<ExtensionPackageException>(block).reason
}

private fun meta() = ExtensionMeta(
    id = "org.example.extension",
    version = "1.0",
    title = "Example extension",
    maintainers = listOf(ExtensionMaintainer("SwiftFloris")),
    license = "Apache-2.0",
)

private fun keyboardExtension(
    layouts: Map<String, List<LayoutArrangementComponent>>,
) = KeyboardExtension(
    meta = meta(),
    layouts = layouts,
)

private fun layout(id: String) = LayoutArrangementComponent(
    id = id,
    label = id,
    authors = listOf("SwiftFloris"),
    direction = "ltr",
)

private fun theme(
    id: String,
    stylesheet: String? = null,
) = ThemeExtensionComponentImpl(
    id = id,
    label = id,
    authors = listOf("SwiftFloris"),
    stylesheetPath = stylesheet,
)
