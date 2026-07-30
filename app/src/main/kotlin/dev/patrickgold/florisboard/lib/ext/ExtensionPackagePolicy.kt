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
import dev.patrickgold.florisboard.ime.keyboard.LayoutType
import dev.patrickgold.florisboard.ime.nlp.LanguagePackExtension
import dev.patrickgold.florisboard.ime.theme.ThemeExtension
import dev.patrickgold.florisboard.lib.io.ArchiveEntryTooLargeException
import dev.patrickgold.florisboard.lib.io.ZipUtils
import dev.patrickgold.florisboard.lib.io.loadJsonAsset
import java.io.File
import kotlinx.serialization.KSerializer
import org.florisboard.lib.kotlin.io.FsDir
import org.florisboard.lib.kotlin.io.FsFile
import org.florisboard.lib.kotlin.io.subFile

internal enum class ExtensionQuarantineReason {
    MANIFEST_TOO_LARGE,
    MANIFEST_MALFORMED,
    INVALID_METADATA,
    TOO_MANY_COMPONENTS,
    INVALID_COMPONENT_ID,
    DUPLICATE_COMPONENT_ID,
    UNKNOWN_LAYOUT_TYPE,
    UNSAFE_COMPONENT_PATH,
    MISSING_COMPONENT_FILE,
    COMPONENT_TOO_LARGE,
    UNREADABLE_ARCHIVE,
}

internal class ExtensionPackageException(
    val reason: ExtensionQuarantineReason,
) : IllegalArgumentException(reason.name)

internal data class ExtensionPackageInspection(
    val componentJsonPaths: Set<String>,
    val requiredBinaryPaths: Set<String>,
)

/**
 * Trust boundary for extension manifests and their referenced files.
 *
 * The manifest cap is intentionally applied before JSON deserialization. The
 * component count and path checks then make the parsed object safe to publish
 * into long-lived IME flows.
 */
internal object ExtensionPackagePolicy {
    const val MAX_MANIFEST_BYTES = 256L * 1024L
    const val MAX_COMPONENT_JSON_BYTES = 1L * 1024L * 1024L
    const val MAX_COMPONENT_COUNT = 2_048

    private const val MAX_COMPONENT_ID_LENGTH = 128
    private val SafeComponentId = Regex("^[A-Za-z][A-Za-z0-9_-]{0,127}$")

    fun requireManifestSize(byteCount: Long) {
        if (byteCount < 0L || byteCount > MAX_MANIFEST_BYTES) {
            reject(ExtensionQuarantineReason.MANIFEST_TOO_LARGE)
        }
    }

    fun inspect(extension: Extension): ExtensionPackageInspection {
        if (!extension.meta.validate()) {
            reject(ExtensionQuarantineReason.INVALID_METADATA)
        }
        return when (extension) {
            is KeyboardExtension -> inspectKeyboard(extension)
            is ThemeExtension -> inspectTheme(extension)
            is LanguagePackExtension -> inspectLanguagePack(extension)
            else -> reject(ExtensionQuarantineReason.MANIFEST_MALFORMED)
        }
    }

    fun validateExtracted(
        extension: Extension,
        extractedRoot: FsDir,
    ): ExtensionPackageInspection {
        val inspection = inspect(extension)
        inspection.componentJsonPaths.forEach { relativePath ->
            val file = resolveRequiredFile(extractedRoot, relativePath)
            if (file.length() > MAX_COMPONENT_JSON_BYTES) {
                reject(ExtensionQuarantineReason.COMPONENT_TOO_LARGE)
            }
        }
        inspection.requiredBinaryPaths.forEach { relativePath ->
            resolveRequiredFile(extractedRoot, relativePath)
        }
        return inspection
    }

    fun validateArchive(
        expected: Extension,
        archiveFile: FsFile,
    ) {
        val actual = when (expected) {
            is KeyboardExtension -> readValidatedArchive(archiveFile, KeyboardExtension.serializer())
            is ThemeExtension -> readValidatedArchive(archiveFile, ThemeExtension.serializer())
            is LanguagePackExtension -> readValidatedArchive(archiveFile, LanguagePackExtension.serializer())
            else -> reject(ExtensionQuarantineReason.MANIFEST_MALFORMED)
        }
        if (actual != expected) {
            reject(ExtensionQuarantineReason.INVALID_METADATA)
        }
    }

    fun <T : Extension> readValidatedArchive(
        archiveFile: FsFile,
        serializer: KSerializer<T>,
    ): T {
        val manifest = ZipUtils.readFileFromArchive(
            srcFile = archiveFile,
            relPath = ExtensionDefaults.MANIFEST_FILE_NAME,
            maxBytes = MAX_MANIFEST_BYTES,
        ).getOrElse { error ->
            if (error is ArchiveEntryTooLargeException) {
                reject(ExtensionQuarantineReason.MANIFEST_TOO_LARGE)
            }
            reject(ExtensionQuarantineReason.UNREADABLE_ARCHIVE)
        }
        val extension = loadJsonAsset(
            manifest,
            serializer,
            ExtensionJsonConfig,
        ).getOrElse {
            reject(ExtensionQuarantineReason.MANIFEST_MALFORMED)
        }
        val inspection = inspect(extension)
        inspection.componentJsonPaths.forEach { componentPath ->
            ZipUtils.validateFileInArchive(
                srcFile = archiveFile,
                relPath = componentPath,
                maxBytes = MAX_COMPONENT_JSON_BYTES,
            ).getOrElse { error ->
                if (error is ArchiveEntryTooLargeException) {
                    reject(ExtensionQuarantineReason.COMPONENT_TOO_LARGE)
                }
                reject(ExtensionQuarantineReason.MISSING_COMPONENT_FILE)
            }
        }
        inspection.requiredBinaryPaths.forEach { componentPath ->
            ZipUtils.validateFileInArchive(
                srcFile = archiveFile,
                relPath = componentPath,
                maxBytes = Long.MAX_VALUE,
            ).getOrElse {
                reject(ExtensionQuarantineReason.MISSING_COMPONENT_FILE)
            }
        }
        return extension
    }

    fun resolveRequiredFile(root: FsDir, relativePath: String): FsFile {
        requireSafeRelativePath(relativePath)
        val canonicalRoot = root.canonicalFile
        val canonicalFile = canonicalRoot.subFile(relativePath).canonicalFile
        if (!canonicalFile.toPath().startsWith(canonicalRoot.toPath())) {
            reject(ExtensionQuarantineReason.UNSAFE_COMPONENT_PATH)
        }
        if (!canonicalFile.isFile) {
            reject(ExtensionQuarantineReason.MISSING_COMPONENT_FILE)
        }
        return canonicalFile
    }

    fun readComponentJson(file: FsFile): String {
        if (!file.isFile) {
            reject(ExtensionQuarantineReason.MISSING_COMPONENT_FILE)
        }
        if (file.length() > MAX_COMPONENT_JSON_BYTES) {
            reject(ExtensionQuarantineReason.COMPONENT_TOO_LARGE)
        }
        return file.readText(Charsets.UTF_8)
    }

    fun requireSafeRelativePath(relativePath: String) {
        if (!isSafeRelativePath(relativePath)) {
            reject(ExtensionQuarantineReason.UNSAFE_COMPONENT_PATH)
        }
    }

    internal fun isSafeRelativePath(relativePath: String): Boolean {
        if (relativePath.isBlank() || relativePath != relativePath.trim()) return false
        if (relativePath.contains('\u0000') || relativePath.contains('\\')) return false
        if (File(relativePath).isAbsolute) return false
        val segments = relativePath.split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." || ':' in it }) return false
        return segments.joinToString("/") == relativePath
    }

    private fun inspectKeyboard(extension: KeyboardExtension): ExtensionPackageInspection {
        val componentJsonPaths = linkedSetOf<String>()
        var count = extension.subtypePresets.size

        validateIds(extension.composers.map { it.id })
        count += extension.composers.size
        validateIds(extension.currencySets.map { it.id })
        count += extension.currencySets.size
        validateIds(extension.punctuationRules.map { it.id })
        count += extension.punctuationRules.size
        validateIds(extension.popupMappings.map { it.id })
        count += extension.popupMappings.size

        extension.popupMappings.forEach { popup ->
            popup.mappingFile().also {
                requireSafeRelativePath(it)
                componentJsonPaths += it
            }
        }

        for ((layoutTypeId, layouts) in extension.layouts) {
            val layoutType = LayoutType.entries.find { it.id == layoutTypeId }
                ?: reject(ExtensionQuarantineReason.UNKNOWN_LAYOUT_TYPE)
            validateIds(layouts.map { it.id })
            count += layouts.size
            layouts.forEach { layout ->
                layout.arrangementFile(layoutType).also {
                    requireSafeRelativePath(it)
                    componentJsonPaths += it
                }
            }
        }
        requireComponentCount(count)
        return ExtensionPackageInspection(
            componentJsonPaths = componentJsonPaths,
            requiredBinaryPaths = emptySet(),
        )
    }

    private fun inspectTheme(extension: ThemeExtension): ExtensionPackageInspection {
        requireComponentCount(extension.themes.size)
        validateIds(extension.themes.map { it.id })
        val paths = extension.themes.mapTo(linkedSetOf()) { theme ->
            theme.stylesheetPath().also(::requireSafeRelativePath)
        }
        return ExtensionPackageInspection(
            componentJsonPaths = paths,
            requiredBinaryPaths = emptySet(),
        )
    }

    private fun inspectLanguagePack(extension: LanguagePackExtension): ExtensionPackageInspection {
        requireComponentCount(extension.items.size)
        validateIds(extension.items.map { it.id })
        val binaryPaths = if (extension.supportsHanShapeBased()) {
            setOf(extension.hanShapeBasedSQLite.also(::requireSafeRelativePath))
        } else {
            emptySet()
        }
        return ExtensionPackageInspection(
            componentJsonPaths = emptySet(),
            requiredBinaryPaths = binaryPaths,
        )
    }

    private fun validateIds(ids: List<String>) {
        if (ids.any { it.length > MAX_COMPONENT_ID_LENGTH || !SafeComponentId.matches(it) }) {
            reject(ExtensionQuarantineReason.INVALID_COMPONENT_ID)
        }
        if (ids.toSet().size != ids.size) {
            reject(ExtensionQuarantineReason.DUPLICATE_COMPONENT_ID)
        }
    }

    private fun requireComponentCount(count: Int) {
        if (count > MAX_COMPONENT_COUNT) {
            reject(ExtensionQuarantineReason.TOO_MANY_COMPONENTS)
        }
    }

    private fun reject(reason: ExtensionQuarantineReason): Nothing {
        throw ExtensionPackageException(reason)
    }
}
