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

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import dev.patrickgold.florisboard.lib.devtools.flogDebug
import dev.patrickgold.florisboard.lib.devtools.flogError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference

@Serializable
data class ImportedHardwareKeyboardLayout(
    val id: String,
    val displayName: String,
    val sourceName: String,
    val sourceFormat: HardwareKeyboardLayoutSourceFormat,
    val importedAtEpochMillis: Long,
    val layout: HardwareKeyboardLayout,
) {
    val locale: String get() = layout.locale
    val keyCount: Int get() = layout.scancodeMap.size
}

@Serializable
enum class HardwareKeyboardLayoutSourceFormat {
    KLC,
    MAC_KEYLAYOUT,
    KEYMAN_LDML_PACKAGE,
}

enum class HardwareKeyboardLayoutImportStatus {
    Imported,
    UnsupportedFileType,
    NoImportableLayout,
    TooLarge,
    ReadFailure,
}

data class HardwareKeyboardLayoutImportResult(
    val status: HardwareKeyboardLayoutImportStatus,
    val importedLayout: ImportedHardwareKeyboardLayout? = null,
    val detail: String? = null,
)

object HardwareKeyboardLayoutImporter {
    const val MaxImportBytes: Int = 16 * 1024 * 1024

    fun importBytes(
        sourceName: String?,
        bytes: ByteArray,
        importedAtEpochMillis: Long,
    ): HardwareKeyboardLayoutImportResult {
        if (bytes.size > MaxImportBytes) {
            return HardwareKeyboardLayoutImportResult(
                status = HardwareKeyboardLayoutImportStatus.TooLarge,
                detail = "Selected layout file is larger than ${MaxImportBytes / 1024 / 1024} MiB.",
            )
        }
        val normalizedSourceName = sourceName?.takeIf { it.isNotBlank() } ?: "hardware-layout"
        val parsed = parseLayout(normalizedSourceName, bytes)
            ?: return HardwareKeyboardLayoutImportResult(
                status = HardwareKeyboardLayoutImportStatus.UnsupportedFileType,
                detail = "Supported formats are .klc, .keylayout, and Keyman .kmp packages containing LDML XML.",
            )
        val (format, layout, detail) = parsed
        if (!layout.isLoaded) {
            return HardwareKeyboardLayoutImportResult(
                status = HardwareKeyboardLayoutImportStatus.NoImportableLayout,
                detail = detail ?: "The selected file did not contain an importable hardware keyboard layout.",
            )
        }
        val displayName = layout.name.ifBlank { normalizedSourceName.substringBeforeLast('.', normalizedSourceName) }
        val imported = ImportedHardwareKeyboardLayout(
            id = stableId(format, normalizedSourceName, bytes),
            displayName = displayName,
            sourceName = normalizedSourceName,
            sourceFormat = format,
            importedAtEpochMillis = importedAtEpochMillis,
            layout = layout.copy(name = displayName),
        )
        return HardwareKeyboardLayoutImportResult(
            status = HardwareKeyboardLayoutImportStatus.Imported,
            importedLayout = imported,
        )
    }

    internal fun readBytesLimited(inputStream: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = inputStream.read(buffer)
            if (read == -1) break
            total += read
            if (total > MaxImportBytes) {
                throw HardwareKeyboardLayoutImportTooLargeException()
            }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun parseLayout(
        sourceName: String,
        bytes: ByteArray,
    ): Triple<HardwareKeyboardLayoutSourceFormat, HardwareKeyboardLayout, String?>? {
        val lowerName = sourceName.lowercase()
        return when {
            lowerName.endsWith(".kmp") -> parseKeymanPackage(bytes)
            lowerName.endsWith(".keylayout") -> Triple(
                HardwareKeyboardLayoutSourceFormat.MAC_KEYLAYOUT,
                MacKeylayoutParser.parse(bytes.decodeLayoutText()),
                null,
            )
            lowerName.endsWith(".klc") -> Triple(
                HardwareKeyboardLayoutSourceFormat.KLC,
                KlcLayoutParser.parse(bytes.decodeLayoutText()),
                null,
            )
            bytes.isZipLike() -> parseKeymanPackage(bytes)
            else -> parseTextByContent(bytes.decodeLayoutText())
        }
    }

    private fun parseTextByContent(
        text: String,
    ): Triple<HardwareKeyboardLayoutSourceFormat, HardwareKeyboardLayout, String?>? {
        val trimmed = text.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        return when {
            trimmed.startsWith("<") && "<keyboard" in trimmed.take(512).lowercase() -> Triple(
                HardwareKeyboardLayoutSourceFormat.MAC_KEYLAYOUT,
                MacKeylayoutParser.parse(text),
                null,
            )
            KbdHeaderRegex.containsMatchIn(text) || LayoutHeaderRegex.containsMatchIn(text) -> Triple(
                HardwareKeyboardLayoutSourceFormat.KLC,
                KlcLayoutParser.parse(text),
                null,
            )
            else -> null
        }
    }

    private fun parseKeymanPackage(
        bytes: ByteArray,
    ): Triple<HardwareKeyboardLayoutSourceFormat, HardwareKeyboardLayout, String?> {
        val pkg = KeymanPackageParser.parse(bytes)
        val ldmlLayout = pkg.ldmlLayouts.firstOrNull { it.layout.isLoaded }
        if (ldmlLayout != null) {
            val fallbackName = pkg.keyboards.firstOrNull()?.name
                ?: pkg.info.name.ifBlank { ldmlLayout.entryName.substringAfterLast('/') }
            val layout = ldmlLayout.layout.copy(
                name = ldmlLayout.layout.name.ifBlank { fallbackName },
            )
            return Triple(HardwareKeyboardLayoutSourceFormat.KEYMAN_LDML_PACKAGE, layout, null)
        }
        val detail = when (pkg.status) {
            KeymanPackageImportStatus.CompiledEngineRequired ->
                "This Keyman package requires a compiled Keyman engine and does not include importable LDML XML."
            KeymanPackageImportStatus.LexicalModelOnly ->
                "This Keyman package contains a lexical model, not a hardware keyboard layout."
            KeymanPackageImportStatus.MixedPackageUnsupported ->
                "Mixed Keyman keyboard and lexical-model packages are not importable here."
            KeymanPackageImportStatus.MetadataOnly,
            KeymanPackageImportStatus.Invalid,
            KeymanPackageImportStatus.LdmlReady,
            -> "The Keyman package did not contain an importable LDML layout."
        }
        return Triple(
            HardwareKeyboardLayoutSourceFormat.KEYMAN_LDML_PACKAGE,
            HardwareKeyboardLayout.Empty,
            detail,
        )
    }

    private fun stableId(
        format: HardwareKeyboardLayoutSourceFormat,
        sourceName: String,
        bytes: ByteArray,
    ): String {
        val messageDigest = MessageDigest.getInstance("SHA-256")
        messageDigest.update(format.name.toByteArray())
        messageDigest.update(0.toByte())
        messageDigest.update(sourceName.toByteArray())
        messageDigest.update(0.toByte())
        val digest = messageDigest.digest(bytes)
            .joinToString("") { "%02x".format(it) }
        return "hardware-layout-${digest.take(24)}"
    }

    private fun ByteArray.isZipLike(): Boolean {
        return size >= 4 && this[0] == 'P'.code.toByte() && this[1] == 'K'.code.toByte()
    }

    private fun ByteArray.decodeLayoutText(): String {
        return when {
            size >= 2 && this[0] == 0xFF.toByte() && this[1] == 0xFE.toByte() ->
                toString(Charsets.UTF_16LE)
            size >= 2 && this[0] == 0xFE.toByte() && this[1] == 0xFF.toByte() ->
                toString(Charsets.UTF_16BE)
            else -> toString(Charsets.UTF_8)
        }
    }

    private val KbdHeaderRegex = Regex("""(?im)^\s*KBD\s+""")
    private val LayoutHeaderRegex = Regex("""(?im)^\s*LAYOUT\s*$""")
}

class HardwareKeyboardLayoutStore(
    private val storageFile: File,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val cache = AtomicReference<List<ImportedHardwareKeyboardLayout>>(emptyList())
    private val writeLock = Any()

    fun layouts(): List<ImportedHardwareKeyboardLayout> = cache.get()

    fun layout(id: String?): ImportedHardwareKeyboardLayout? {
        if (id == null) return null
        return cache.get().firstOrNull { it.id == id }
    }

    fun importLayout(
        sourceName: String?,
        inputStream: InputStream,
    ): HardwareKeyboardLayoutImportResult = synchronized(writeLock) {
        val bytes = try {
            inputStream.use { HardwareKeyboardLayoutImporter.readBytesLimited(it) }
        } catch (_: HardwareKeyboardLayoutImportTooLargeException) {
            return HardwareKeyboardLayoutImportResult(
                status = HardwareKeyboardLayoutImportStatus.TooLarge,
                detail = "Selected layout file is larger than ${HardwareKeyboardLayoutImporter.MaxImportBytes / 1024 / 1024} MiB.",
            )
        } catch (cause: Throwable) {
            return HardwareKeyboardLayoutImportResult(
                status = HardwareKeyboardLayoutImportStatus.ReadFailure,
                detail = cause.message,
            )
        }
        val result = HardwareKeyboardLayoutImporter.importBytes(
            sourceName = sourceName,
            bytes = bytes,
            importedAtEpochMillis = clock(),
        )
        val imported = result.importedLayout ?: return result
        val updated = (cache.get().filterNot { it.id == imported.id } + imported)
            .takeLast(MaxImportedLayouts)
        cache.set(updated)
        flush(updated)
        return result
    }

    fun deleteLayout(id: String): Boolean = synchronized(writeLock) {
        val current = cache.get()
        val updated = current.filterNot { it.id == id }
        if (updated.size == current.size) return false
        cache.set(updated)
        flush(updated)
        return true
    }

    private fun flush(layouts: List<ImportedHardwareKeyboardLayout>) {
        try {
            storageFile.parentFile?.mkdirs()
            val tmp = File(storageFile.parentFile, storageFile.name + ".tmp")
            tmp.writeText(JsonConfig.encodeToString(StoreFile(layouts = layouts)))
            moveReplacing(tmp, storageFile)
        } catch (cause: Throwable) {
            flogError { "HardwareKeyboardLayoutStore.flush failed: $cause" }
        }
    }

    private fun load() {
        try {
            if (!storageFile.exists()) {
                cache.set(emptyList())
                return
            }
            val decoded = JsonConfig.decodeFromString<StoreFile>(storageFile.readText())
            cache.set(decoded.layouts.takeLast(MaxImportedLayouts))
            flogDebug { "HardwareKeyboardLayoutStore.load loaded ${decoded.layouts.size} layouts" }
        } catch (cause: Throwable) {
            flogError { "HardwareKeyboardLayoutStore.load failed: $cause - starting fresh" }
            cache.set(emptyList())
        }
    }

    private fun moveReplacing(tmp: File, target: File) {
        try {
            Files.move(
                tmp.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                tmp.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    @Serializable
    private data class StoreFile(
        val version: Int = 1,
        val layouts: List<ImportedHardwareKeyboardLayout> = emptyList(),
    )

    companion object {
        const val MaxImportedLayouts: Int = 16

        private val JsonConfig = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun default(context: Context): HardwareKeyboardLayoutStore {
            val file = File(context.applicationContext.filesDir, "hardware_keyboard_layouts.json")
            return HardwareKeyboardLayoutStore(file).also { it.load() }
        }

        internal fun forStorageFile(
            file: File,
            clock: () -> Long = { System.currentTimeMillis() },
        ): HardwareKeyboardLayoutStore {
            return HardwareKeyboardLayoutStore(file, clock).also { it.load() }
        }
    }
}

class HardwareKeyboardLayoutRepository(
    private val context: Context,
    private val store: HardwareKeyboardLayoutStore = HardwareKeyboardLayoutStore.default(context),
) {
    suspend fun layouts(): List<ImportedHardwareKeyboardLayout> = withContext(Dispatchers.IO) {
        store.layouts()
    }

    suspend fun importFromUri(uri: Uri): HardwareKeyboardLayoutImportResult = withContext(Dispatchers.IO) {
        val sourceName = context.displayName(uri)
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: return@withContext HardwareKeyboardLayoutImportResult(
                status = HardwareKeyboardLayoutImportStatus.ReadFailure,
                detail = "Unable to open selected layout file.",
            )
        store.importLayout(sourceName = sourceName, inputStream = inputStream)
    }

    suspend fun deleteLayout(id: String): Boolean = withContext(Dispatchers.IO) {
        store.deleteLayout(id)
    }

    private fun Context.displayName(uri: Uri): String? {
        return contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getStringOrNull(OpenableColumns.DISPLAY_NAME)
            } else {
                null
            }
        } ?: uri.lastPathSegment
    }

    private fun Cursor.getStringOrNull(columnName: String): String? {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getString(index) else null
    }
}

private class HardwareKeyboardLayoutImportTooLargeException : RuntimeException()
