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

package dev.patrickgold.florisboard.ime.hardware

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * ROADMAP Tier-3 #34 — Keyman `.kmp` package import foundation.
 *
 * Keyman's `.kmp` format is a ZIP-compatible package containing `kmp.json`
 * metadata plus keyboard files (`.kmx` / `.js`), fonts, documentation, visual
 * keyboard files, and sometimes lexical models. SwiftFloris already has a
 * Keyman LDML XML parser; this parser safely opens the package container,
 * normalizes metadata, extracts any LDML XML layouts that are actually present,
 * and classifies packages that still require a full Keyman-compatible runtime.
 *
 * This intentionally does not execute `.kmx` bytecode or JavaScript. Those
 * remain addon/runtime work, not a silent dependency inside the base APK.
 */
object KeymanPackageParser {
    const val MimeType = "application/vnd.keyman.kmp+zip"
    private const val MetadataFileName = "kmp.json"
    private const val MaxMetadataBytes = 512 * 1024
    private const val MaxLdmlBytes = 1024 * 1024
    // Per-entry byte caps alone don't bound a crafted .kmp: thousands of max-size
    // LDML XMLs, each read into a String and parsed into a retained layout, can OOM
    // the IME (and OOM is an Error that escapes the runCatching below, crashing the
    // process instead of degrading). Cap the entry count and the cumulative LDML
    // bytes parsed; legitimate packages carry a handful of keyboards.
    private const val MaxEntries = 4096
    private const val MaxTotalLdmlBytes = 8L * 1024 * 1024

    private val JsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(bytes: ByteArray): KeymanPackage {
        if (bytes.isEmpty()) return KeymanPackage.Empty
        return parse(ByteArrayInputStream(bytes))
    }

    fun parse(inputStream: InputStream): KeymanPackage {
        val warnings = mutableListOf<String>()
        val entries = mutableListOf<KeymanPackageEntry>()
        val ldmlLayouts = mutableListOf<KeymanPackageLdmlLayout>()
        var metadataJson: String? = null

        var entryCount = 0
        var totalLdmlBytes = 0L
        runCatching {
            ZipInputStream(inputStream).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    if (++entryCount > MaxEntries) {
                        warnings += "Package has more than $MaxEntries entries; stopped parsing the remainder."
                        break
                    }
                    if (!entry.name.isSafePackageEntryName()) {
                        warnings += "Skipped unsafe package entry: ${entry.name}"
                        continue
                    }
                    val entryName = entry.name.normalizeZipEntryName()

                    val fileType = KeymanPackageFileType.fromEntryName(entryName)
                    entries += KeymanPackageEntry(
                        name = entryName,
                        sizeBytes = entry.size.takeIf { it >= 0L },
                        fileType = fileType,
                    )

                    when {
                        entryName.equals(MetadataFileName, ignoreCase = true) -> {
                            metadataJson = zip.readEntryTextLimited(MaxMetadataBytes)
                        }
                        fileType == KeymanPackageFileType.LdmlKeyboard -> {
                            if (totalLdmlBytes >= MaxTotalLdmlBytes) {
                                warnings += "Skipped LDML keyboard '$entryName': cumulative LDML size budget exceeded."
                            } else {
                                val xml = zip.readEntryTextLimited(MaxLdmlBytes)
                                totalLdmlBytes += xml.length.toLong()
                                val layout = KeymanLdmlParser.parse(xml)
                                if (layout.isLoaded) {
                                    ldmlLayouts += KeymanPackageLdmlLayout(
                                        entryName = entryName,
                                        layout = layout,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }.onFailure {
            return KeymanPackage.Empty.copy(
                status = KeymanPackageImportStatus.Invalid,
                warnings = listOf("Invalid or unreadable KMP ZIP package"),
            )
        }

        val metadata = parseMetadata(metadataJson, warnings)
        return KeymanPackage(
            info = metadata.info,
            options = metadata.options,
            files = metadata.files,
            keyboards = metadata.keyboards,
            lexicalModels = metadata.lexicalModels,
            entries = entries,
            ldmlLayouts = ldmlLayouts,
            status = statusFor(metadata, entries, ldmlLayouts),
            warnings = warnings,
        )
    }

    private fun parseMetadata(
        metadataJson: String?,
        warnings: MutableList<String>,
    ): KeymanPackageMetadata {
        if (metadataJson.isNullOrBlank()) {
            warnings += "Package is missing kmp.json metadata"
            return KeymanPackageMetadata.Empty
        }
        val root = runCatching { JsonParser.parseToJsonElement(metadataJson).jsonObject }.getOrNull()
        if (root == null) {
            warnings += "Package has unreadable kmp.json metadata"
            return KeymanPackageMetadata.Empty
        }
        return KeymanPackageMetadata(
            info = root["info"].asObjectOrNull()?.let { parseInfo(it) } ?: KeymanPackageInfo.Empty,
            options = root["options"].asObjectOrNull()?.let { parseOptions(it) } ?: KeymanPackageOptions.Empty,
            files = root["files"].asObjectList().mapNotNull { parseFile(it) },
            keyboards = root["keyboards"].asObjectList().mapNotNull { parseKeyboard(it) },
            lexicalModels = root["lexicalModels"].asObjectList().mapNotNull { parseLexicalModel(it) },
        )
    }

    private fun parseInfo(obj: JsonObject): KeymanPackageInfo {
        return KeymanPackageInfo(
            name = obj["name"].stringOrEmpty(),
            version = obj["version"].stringOrNull() ?: "1.0",
            author = obj["author"].stringOrNull(),
            copyright = obj["copyright"].stringOrNull(),
            website = obj["website"].stringOrNull(),
        )
    }

    private fun parseOptions(obj: JsonObject): KeymanPackageOptions {
        return KeymanPackageOptions(
            readmeFile = obj["readmeFile"].stringOrNull(),
            welcomeFile = obj["welcomeFile"].stringOrNull(),
            licenseFile = obj["licenseFile"].stringOrNull(),
            graphicFile = obj["graphicFile"].stringOrNull(),
        )
    }

    private fun parseFile(obj: JsonObject): KeymanPackageFile? {
        val name = obj["name"].stringOrNull()?.normalizeZipEntryName() ?: return null
        return KeymanPackageFile(
            name = name,
            description = obj["description"].stringOrNull(),
            fileType = KeymanPackageFileType.fromEntryName(name),
        )
    }

    private fun parseKeyboard(obj: JsonObject): KeymanPackageKeyboard? {
        val id = obj["id"].stringOrNull() ?: return null
        return KeymanPackageKeyboard(
            id = id,
            name = obj["name"].stringOrNull() ?: id,
            version = obj["version"].stringOrNull() ?: "1.0",
            isRtl = obj["rtl"].booleanOrFalse(),
            languages = obj["languages"].asObjectList().mapNotNull { parseLanguage(it) },
            displayFont = obj["displayFont"].stringOrNull(),
            oskFont = obj["oskFont"].stringOrNull(),
            examples = obj["examples"].asObjectList().mapNotNull { parseExample(it) },
        )
    }

    private fun parseLexicalModel(obj: JsonObject): KeymanPackageLexicalModel? {
        val id = obj["id"].stringOrNull() ?: return null
        return KeymanPackageLexicalModel(
            id = id,
            name = obj["name"].stringOrNull() ?: id,
            version = obj["version"].stringOrNull() ?: "1.0",
            isRtl = obj["rtl"].booleanOrFalse(),
            languages = obj["languages"].asObjectList().mapNotNull { parseLanguage(it) },
        )
    }

    private fun parseLanguage(obj: JsonObject): KeymanPackageLanguage? {
        val id = obj["id"].stringOrNull() ?: return null
        return KeymanPackageLanguage(
            id = id,
            name = obj["name"].stringOrNull() ?: id,
        )
    }

    private fun parseExample(obj: JsonObject): KeymanPackageExample? {
        val text = obj["text"].stringOrNull() ?: return null
        return KeymanPackageExample(
            languageId = obj["id"].stringOrNull(),
            keys = obj["keys"].stringOrNull(),
            text = text,
            note = obj["note"].stringOrNull(),
        )
    }

    private fun statusFor(
        metadata: KeymanPackageMetadata,
        entries: List<KeymanPackageEntry>,
        ldmlLayouts: List<KeymanPackageLdmlLayout>,
    ): KeymanPackageImportStatus {
        val declaredFileTypes = metadata.files.map { it.fileType }
        val hasKeyboardSignal = metadata.keyboards.isNotEmpty() ||
            entries.any { it.fileType.isKeyboardType } ||
            declaredFileTypes.any { it.isKeyboardType }
        val hasLexicalModelSignal = metadata.lexicalModels.isNotEmpty() ||
            entries.any { it.fileType == KeymanPackageFileType.LexicalModel } ||
            KeymanPackageFileType.LexicalModel in declaredFileTypes

        if (hasKeyboardSignal && hasLexicalModelSignal) {
            return KeymanPackageImportStatus.MixedPackageUnsupported
        }
        if (ldmlLayouts.isNotEmpty()) return KeymanPackageImportStatus.LdmlReady
        if (hasLexicalModelSignal) {
            return KeymanPackageImportStatus.LexicalModelOnly
        }
        if (hasKeyboardSignal) {
            return KeymanPackageImportStatus.CompiledEngineRequired
        }
        return if (entries.isEmpty()) {
            KeymanPackageImportStatus.Invalid
        } else {
            KeymanPackageImportStatus.MetadataOnly
        }
    }

    private fun InputStream.readEntryTextLimited(maxBytes: Int): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) {
                throw IllegalArgumentException("Package entry exceeds $maxBytes bytes")
            }
            out.write(buffer, 0, read)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    private fun String.normalizeZipEntryName(): String {
        return replace('\\', '/')
    }

    private fun String.isSafePackageEntryName(): Boolean {
        val normalized = normalizeZipEntryName()
        if (normalized.isBlank()) return false
        if (normalized.startsWith('/')) return false
        if (normalized.contains(':')) return false
        return normalized.split('/').none { it == ".." || it.isBlank() }
    }

    private fun JsonElement?.asObjectOrNull(): JsonObject? {
        return this as? JsonObject
    }

    private fun JsonElement?.asObjectList(): List<JsonObject> {
        return when (this) {
            is JsonArray -> mapNotNull { it as? JsonObject }
            is JsonObject -> values.mapNotNull { it as? JsonObject }
            else -> emptyList()
        }
    }

    private fun JsonElement?.stringOrEmpty(): String {
        return stringOrNull().orEmpty()
    }

    private fun JsonElement?.stringOrNull(): String? {
        return when (this) {
            is JsonPrimitive -> contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            is JsonObject -> {
                this["description"].stringOrNull()
                    ?: this["name"].stringOrNull()
                    ?: this["value"].stringOrNull()
            }
            else -> null
        }
    }

    private fun JsonElement?.booleanOrFalse(): Boolean {
        return (this as? JsonPrimitive)?.booleanOrNull ?: false
    }
}

data class KeymanPackage(
    val info: KeymanPackageInfo,
    val options: KeymanPackageOptions,
    val files: List<KeymanPackageFile>,
    val keyboards: List<KeymanPackageKeyboard>,
    val lexicalModels: List<KeymanPackageLexicalModel>,
    val entries: List<KeymanPackageEntry>,
    val ldmlLayouts: List<KeymanPackageLdmlLayout>,
    val status: KeymanPackageImportStatus,
    val warnings: List<String> = emptyList(),
) {
    val isKeyboardPackage: Boolean get() = keyboards.isNotEmpty()
    val isLexicalModelPackage: Boolean get() = lexicalModels.isNotEmpty()

    companion object {
        val Empty = KeymanPackage(
            info = KeymanPackageInfo.Empty,
            options = KeymanPackageOptions.Empty,
            files = emptyList(),
            keyboards = emptyList(),
            lexicalModels = emptyList(),
            entries = emptyList(),
            ldmlLayouts = emptyList(),
            status = KeymanPackageImportStatus.Invalid,
        )
    }
}

data class KeymanPackageInfo(
    val name: String,
    val version: String,
    val author: String? = null,
    val copyright: String? = null,
    val website: String? = null,
) {
    companion object {
        val Empty = KeymanPackageInfo(name = "", version = "")
    }
}

data class KeymanPackageOptions(
    val readmeFile: String? = null,
    val welcomeFile: String? = null,
    val licenseFile: String? = null,
    val graphicFile: String? = null,
) {
    companion object {
        val Empty = KeymanPackageOptions()
    }
}

data class KeymanPackageFile(
    val name: String,
    val description: String? = null,
    val fileType: KeymanPackageFileType,
)

data class KeymanPackageEntry(
    val name: String,
    val sizeBytes: Long?,
    val fileType: KeymanPackageFileType,
)

data class KeymanPackageKeyboard(
    val id: String,
    val name: String,
    val version: String,
    val isRtl: Boolean,
    val languages: List<KeymanPackageLanguage>,
    val displayFont: String? = null,
    val oskFont: String? = null,
    val examples: List<KeymanPackageExample> = emptyList(),
)

data class KeymanPackageLexicalModel(
    val id: String,
    val name: String,
    val version: String,
    val isRtl: Boolean,
    val languages: List<KeymanPackageLanguage>,
)

data class KeymanPackageLanguage(
    val id: String,
    val name: String,
)

data class KeymanPackageExample(
    val languageId: String?,
    val keys: String?,
    val text: String,
    val note: String? = null,
)

data class KeymanPackageLdmlLayout(
    val entryName: String,
    val layout: HardwareKeyboardLayout,
)

enum class KeymanPackageImportStatus {
    LdmlReady,
    CompiledEngineRequired,
    LexicalModelOnly,
    MixedPackageUnsupported,
    MetadataOnly,
    Invalid,
}

enum class KeymanPackageFileType {
    Metadata,
    CompiledKeyboard,
    WebKeyboard,
    LdmlKeyboard,
    TouchLayout,
    VisualKeyboard,
    Font,
    Documentation,
    LexicalModel,
    Unknown,
    ;

    val isKeyboardType: Boolean
        get() = when (this) {
            CompiledKeyboard, WebKeyboard, LdmlKeyboard -> true
            else -> false
        }

    companion object {
        fun fromEntryName(entryName: String): KeymanPackageFileType {
            val name = entryName.lowercase(Locale.ROOT)
            return when {
                name == "kmp.json" || name == "kmp.inf" -> Metadata
                name.endsWith(".kmx") -> CompiledKeyboard
                name.endsWith(".model.js") -> LexicalModel
                name.endsWith(".js") -> WebKeyboard
                name.endsWith(".xml") -> LdmlKeyboard
                name.endsWith(".keyman-touch-layout") -> TouchLayout
                name.endsWith(".kvk") -> VisualKeyboard
                name.endsWith(".ttf") || name.endsWith(".otf") || name.endsWith(".ttc") -> Font
                name.endsWith(".htm") || name.endsWith(".html") || name.endsWith(".md") || name.endsWith(".txt") -> Documentation
                else -> Unknown
            }
        }
    }
}

private data class KeymanPackageMetadata(
    val info: KeymanPackageInfo,
    val options: KeymanPackageOptions,
    val files: List<KeymanPackageFile>,
    val keyboards: List<KeymanPackageKeyboard>,
    val lexicalModels: List<KeymanPackageLexicalModel>,
) {
    companion object {
        val Empty = KeymanPackageMetadata(
            info = KeymanPackageInfo.Empty,
            options = KeymanPackageOptions.Empty,
            files = emptyList(),
            keyboards = emptyList(),
            lexicalModels = emptyList(),
        )
    }
}
