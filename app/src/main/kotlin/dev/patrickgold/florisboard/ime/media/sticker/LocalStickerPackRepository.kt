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

package dev.patrickgold.florisboard.ime.media.sticker

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.florisboard.lib.kotlin.io.subDir
import org.florisboard.lib.kotlin.io.subFile

@Serializable
data class LocalStickerPackManifest(
    val version: Int = 1,
    val name: String = LocalStickerPackRepository.PackName,
    val stickers: List<LocalStickerPackEntry> = emptyList(),
)

@Serializable
data class LocalStickerPackEntry(
    val id: String,
    val fileName: String,
    val displayName: String,
    val mimeType: String,
    val label: String,
    val keywords: List<String> = emptyList(),
)

sealed class LocalStickerPackResult {
    data class Success(val stickerCount: Int) : LocalStickerPackResult()
    data class Failure(
        val reason: LocalStickerPackFailure,
        val details: String? = null,
    ) : LocalStickerPackResult()
}

enum class LocalStickerPackFailure {
    UNSUPPORTED_MIME_TYPE,
    OVERSIZED,
    EMPTY,
    INVALID_ARCHIVE,
    TOO_MANY_STICKERS,
    IO_ERROR,
}

object LocalStickerPackRepository {
    const val PackId = "local_imported"
    const val PackName = "Local stickers"
    const val StorageDirName = "stickers/local"
    const val ManifestFileName = "swiftfloris-sticker-pack.json"
    const val ArchiveMimeType = "application/zip"
    const val DefaultArchiveFileName = "swiftfloris-stickers.sfstickers"
    const val MaxStickerBytes = 8L * 1024L * 1024L
    private const val MaxArchiveBytes = 256L * 1024L * 1024L
    private const val MaxManifestBytes = 256L * 1024L
    private const val FilesDirName = "files"
    private const val ArchiveStickersDir = "stickers"
    private const val TempDirName = "tmp"
    private val JsonCodec = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }
    private val SafeIdRegex = Regex("[A-Za-z0-9._-]{1,96}")

    fun storageDir(context: Context): File {
        return context.filesDir.subDir(StorageDirName)
    }

    fun hasLocalPack(context: Context): Boolean {
        return loadPack(context) != null
    }

    fun loadPack(context: Context): StickerPack? {
        return loadPack(storageDir(context)) { id -> localStickerContentUri(id).toString() }
    }

    fun loadPack(
        storageDir: File,
        contentUriForId: (String) -> String = { "" },
    ): StickerPack? {
        val manifest = runCatching { readManifest(storageDir) }.getOrNull() ?: return null
        val stickers = manifest.stickers
            .asSequence()
            .mapNotNull { entry -> stickerFromEntry(storageDir, entry, contentUriForId) }
            .sortedBy { it.label.lowercase(Locale.ROOT) }
            .take(UserStickerRepository.MaxStickers)
            .toList()
        if (stickers.isEmpty()) return null
        return StickerPack(
            id = PackId,
            name = manifest.name.ifBlank { PackName },
            stickers = stickers,
        )
    }

    fun find(context: Context, stickerId: String): Sticker? {
        val entry = entryForStickerId(storageDir(context), stickerId) ?: return null
        return stickerFromEntry(storageDir(context), entry) { id -> localStickerContentUri(id).toString() }
    }

    fun fileForSticker(context: Context, stickerId: String): File? {
        val storageDir = storageDir(context)
        val entry = entryForStickerId(storageDir, stickerId) ?: return null
        return fileForEntry(storageDir, entry)?.takeIf { it.isFile }
    }

    fun importSharedImage(context: Context, uri: Uri): LocalStickerPackResult {
        if (uri.scheme?.lowercase(Locale.ROOT) != ContentResolver.SCHEME_CONTENT) {
            return LocalStickerPackResult.Failure(LocalStickerPackFailure.UNSUPPORTED_MIME_TYPE)
        }
        val document = querySharedStickerDocument(context, uri)
        val streamProvider = {
            context.contentResolver.openInputStream(uri)
                ?: throw FileNotFoundException("Cannot open sticker image: $uri")
        }
        return importStickerStream(
            storageDir = storageDir(context),
            displayName = document.displayName,
            declaredMimeType = document.mimeType,
            declaredSizeBytes = document.sizeBytes,
            streamProvider = streamProvider,
        )
    }

    fun importStickerFile(
        storageDir: File,
        sourceFile: File,
        displayName: String = sourceFile.name,
        declaredMimeType: String? = null,
    ): LocalStickerPackResult {
        return importStickerStream(
            storageDir = storageDir,
            displayName = displayName,
            declaredMimeType = declaredMimeType,
            declaredSizeBytes = sourceFile.length().takeIf { sourceFile.isFile },
            streamProvider = { sourceFile.inputStream() },
        )
    }

    fun importArchive(context: Context, uri: Uri): LocalStickerPackResult {
        val tempFile = File.createTempFile("swiftfloris-stickers-", ".zip", context.cacheDir)
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                copyCapped(input, tempFile, MaxArchiveBytes)
            } ?: return LocalStickerPackResult.Failure(LocalStickerPackFailure.IO_ERROR)
            importArchive(storageDir(context), tempFile)
        } catch (error: LocalStickerPackException) {
            LocalStickerPackResult.Failure(error.reason, error.message)
        } catch (error: Throwable) {
            LocalStickerPackResult.Failure(LocalStickerPackFailure.IO_ERROR, error.message)
        } finally {
            tempFile.delete()
        }
    }

    fun importArchive(storageDir: File, archiveFile: File): LocalStickerPackResult {
        return try {
            ZipFile(archiveFile).use { zip ->
                val manifest = readArchiveManifest(zip) ?: return LocalStickerPackResult.Failure(
                    LocalStickerPackFailure.INVALID_ARCHIVE,
                    "Missing $ManifestFileName",
                )
                if (manifest.version != 1) {
                    return LocalStickerPackResult.Failure(
                        LocalStickerPackFailure.INVALID_ARCHIVE,
                        "Unsupported sticker-pack version ${manifest.version}",
                    )
                }
                if (manifest.stickers.isEmpty()) {
                    return LocalStickerPackResult.Failure(LocalStickerPackFailure.EMPTY)
                }

                val currentManifest = runCatching { readManifest(storageDir) }
                    .getOrElse { LocalStickerPackManifest() }
                if (currentManifest.stickers.size + manifest.stickers.size > UserStickerRepository.MaxStickers) {
                    return LocalStickerPackResult.Failure(LocalStickerPackFailure.TOO_MANY_STICKERS)
                }

                val tempDir = storageDir.subDir(TempDirName).subDir(UUID.randomUUID().toString())
                val imported = mutableListOf<LocalStickerPackEntry>()
                try {
                    manifest.stickers.forEach { entry ->
                        val sourceFileName = safeFileNameOrNull(entry.fileName)
                            ?: throw LocalStickerPackException(
                                LocalStickerPackFailure.INVALID_ARCHIVE,
                                "Unsafe sticker filename ${entry.fileName}",
                            )
                        val mimeType = UserStickerRepository.resolveMimeType(
                            displayName = entry.displayName.ifBlank { sourceFileName },
                            declaredMimeType = entry.mimeType,
                        ) ?: throw LocalStickerPackException(
                            LocalStickerPackFailure.UNSUPPORTED_MIME_TYPE,
                            "Unsupported MIME ${entry.mimeType}",
                        )
                        val zipEntry = zip.getEntry("$ArchiveStickersDir/$sourceFileName")
                            ?: throw LocalStickerPackException(
                                LocalStickerPackFailure.INVALID_ARCHIVE,
                                "Missing sticker file $sourceFileName",
                            )
                        if (zipEntry.isDirectory) {
                            throw LocalStickerPackException(
                                LocalStickerPackFailure.INVALID_ARCHIVE,
                                "Sticker entry is a directory: $sourceFileName",
                            )
                        }
                        if (zipEntry.size > MaxStickerBytes) {
                            throw LocalStickerPackException(LocalStickerPackFailure.OVERSIZED)
                        }
                        val id = newStickerId()
                        val internalFileName = "$id.${extensionForMimeType(mimeType)}"
                        val tempFile = tempDir.subFile(internalFileName)
                        zip.getInputStream(zipEntry).use { input ->
                            val copied = copyCapped(input, tempFile, MaxStickerBytes)
                            if (copied == 0L) {
                                throw LocalStickerPackException(LocalStickerPackFailure.EMPTY)
                            }
                        }
                        if (!fileHeaderMatchesMimeType(tempFile, mimeType)) {
                            throw LocalStickerPackException(
                                LocalStickerPackFailure.UNSUPPORTED_MIME_TYPE,
                                "Sticker file bytes do not match $mimeType",
                            )
                        }
                        imported += LocalStickerPackEntry(
                            id = id,
                            fileName = internalFileName,
                            displayName = entry.displayName.ifBlank { sourceFileName },
                            mimeType = mimeType,
                            label = entry.label.ifBlank {
                                UserStickerRepository.labelFromDisplayName(entry.displayName.ifBlank { sourceFileName })
                            },
                            keywords = entry.keywords.ifEmpty {
                                UserStickerRepository.keywordsFor(
                                    label = UserStickerRepository.labelFromDisplayName(
                                        entry.displayName.ifBlank { sourceFileName },
                                    ),
                                    displayName = entry.displayName.ifBlank { sourceFileName },
                                )
                            },
                        )
                    }
                    commitImportedFiles(storageDir, tempDir, imported)
                    writeManifest(
                        storageDir,
                        currentManifest.copy(
                            name = currentManifest.name.ifBlank { PackName },
                            stickers = currentManifest.stickers + imported,
                        ),
                    )
                    LocalStickerPackResult.Success(imported.size)
                } finally {
                    tempDir.deleteRecursively()
                }
            }
        } catch (error: LocalStickerPackException) {
            LocalStickerPackResult.Failure(error.reason, error.message)
        } catch (error: Throwable) {
            LocalStickerPackResult.Failure(LocalStickerPackFailure.INVALID_ARCHIVE, error.message)
        }
    }

    fun exportArchive(context: Context, uri: Uri): LocalStickerPackResult {
        return try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                exportArchive(storageDir(context), output)
            } ?: LocalStickerPackResult.Failure(LocalStickerPackFailure.IO_ERROR)
        } catch (error: LocalStickerPackException) {
            LocalStickerPackResult.Failure(error.reason, error.message)
        } catch (error: Throwable) {
            LocalStickerPackResult.Failure(LocalStickerPackFailure.IO_ERROR, error.message)
        }
    }

    fun exportArchive(storageDir: File, outputStream: OutputStream): LocalStickerPackResult {
        return try {
            val manifest = readManifest(storageDir)
            val exportEntries = manifest.stickers.filter { entry -> fileForEntry(storageDir, entry)?.isFile == true }
            if (exportEntries.isEmpty()) {
                return LocalStickerPackResult.Failure(LocalStickerPackFailure.EMPTY)
            }
            ZipOutputStream(outputStream).use { zip ->
                zip.putNextEntry(ZipEntry(ManifestFileName))
                zip.write(JsonCodec.encodeToString(manifest.copy(stickers = exportEntries)).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                exportEntries.forEach { entry ->
                    val file = fileForEntry(storageDir, entry)
                        ?: throw LocalStickerPackException(LocalStickerPackFailure.IO_ERROR, "Missing ${entry.fileName}")
                    zip.putNextEntry(ZipEntry("$ArchiveStickersDir/${entry.fileName}"))
                    file.inputStream().use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            LocalStickerPackResult.Success(exportEntries.size)
        } catch (error: LocalStickerPackException) {
            LocalStickerPackResult.Failure(error.reason, error.message)
        } catch (error: Throwable) {
            LocalStickerPackResult.Failure(LocalStickerPackFailure.IO_ERROR, error.message)
        }
    }

    fun clear(context: Context) {
        storageDir(context).deleteRecursively()
    }

    private fun importStickerStream(
        storageDir: File,
        displayName: String,
        declaredMimeType: String?,
        declaredSizeBytes: Long?,
        streamProvider: () -> InputStream,
    ): LocalStickerPackResult {
        return try {
            if (declaredSizeBytes != null && declaredSizeBytes > MaxStickerBytes) {
                return LocalStickerPackResult.Failure(LocalStickerPackFailure.OVERSIZED)
            }
            val normalizedDeclaredMimeType = declaredMimeType
                ?.trim()
                ?.lowercase(Locale.ROOT)
                ?.takeUnless { it == "image/*" }
            val mimeType = UserStickerRepository.resolveMimeType(displayName, normalizedDeclaredMimeType)
                ?: return LocalStickerPackResult.Failure(LocalStickerPackFailure.UNSUPPORTED_MIME_TYPE)
            val currentManifest = runCatching { readManifest(storageDir) }
                .getOrElse { LocalStickerPackManifest() }
            if (currentManifest.stickers.size >= UserStickerRepository.MaxStickers) {
                return LocalStickerPackResult.Failure(LocalStickerPackFailure.TOO_MANY_STICKERS)
            }
            val id = newStickerId()
            val internalFileName = "$id.${extensionForMimeType(mimeType)}"
            val tempDir = storageDir.subDir(TempDirName).subDir(UUID.randomUUID().toString())
            val tempFile = tempDir.subFile(internalFileName)
            try {
                streamProvider().use { input ->
                    val copied = copyCapped(input, tempFile, MaxStickerBytes)
                    if (copied == 0L) {
                        return LocalStickerPackResult.Failure(LocalStickerPackFailure.EMPTY)
                    }
                }
                if (!fileHeaderMatchesMimeType(tempFile, mimeType)) {
                    return LocalStickerPackResult.Failure(
                        LocalStickerPackFailure.UNSUPPORTED_MIME_TYPE,
                        "Sticker file bytes do not match $mimeType",
                    )
                }
                val label = UserStickerRepository.labelFromDisplayName(displayName)
                val entry = LocalStickerPackEntry(
                    id = id,
                    fileName = internalFileName,
                    displayName = displayName.ifBlank { "sticker.${extensionForMimeType(mimeType)}" },
                    mimeType = mimeType,
                    label = label,
                    keywords = UserStickerRepository.keywordsFor(label, displayName),
                )
                commitImportedFiles(storageDir, tempDir, listOf(entry))
                writeManifest(
                    storageDir,
                    currentManifest.copy(
                        name = currentManifest.name.ifBlank { PackName },
                        stickers = currentManifest.stickers + entry,
                    ),
                )
                LocalStickerPackResult.Success(1)
            } finally {
                tempDir.deleteRecursively()
            }
        } catch (error: LocalStickerPackException) {
            LocalStickerPackResult.Failure(error.reason, error.message)
        } catch (error: Throwable) {
            LocalStickerPackResult.Failure(LocalStickerPackFailure.IO_ERROR, error.message)
        }
    }

    private fun stickerFromEntry(
        storageDir: File,
        entry: LocalStickerPackEntry,
        contentUriForId: (String) -> String,
    ): Sticker? {
        if (!isSafeStickerId(entry.id)) return null
        val file = fileForEntry(storageDir, entry)?.takeIf { it.isFile } ?: return null
        val mimeType = UserStickerRepository.resolveMimeType(entry.displayName, entry.mimeType) ?: return null
        val sourceUri = contentUriForId(entry.id).ifBlank { return null }
        return Sticker(
            packId = PackId,
            id = entry.id,
            label = entry.label.ifBlank { UserStickerRepository.labelFromDisplayName(entry.displayName) },
            emoji = "IMG",
            keywords = entry.keywords.ifEmpty {
                UserStickerRepository.keywordsFor(
                    label = entry.label.ifBlank { UserStickerRepository.labelFromDisplayName(entry.displayName) },
                    displayName = entry.displayName,
                )
            },
            backgroundColor = 0xFF111827.toInt(),
            accentColor = 0xFF34D399.toInt(),
            mimeType = mimeType,
            sourceUri = sourceUri,
            displayName = entry.displayName.ifBlank { file.name },
        )
    }

    private fun entryForStickerId(storageDir: File, stickerId: String): LocalStickerPackEntry? {
        if (!isSafeStickerId(stickerId)) return null
        return runCatching {
            readManifest(storageDir).stickers.firstOrNull { it.id == stickerId }
        }.getOrNull()
    }

    private fun fileForEntry(storageDir: File, entry: LocalStickerPackEntry): File? {
        val fileName = safeFileNameOrNull(entry.fileName) ?: return null
        val filesDir = storageDir.subDir(FilesDirName)
        val file = filesDir.subFile(fileName)
        val parentPath = runCatching { filesDir.canonicalFile.toPath() }.getOrNull() ?: return null
        val filePath = runCatching { file.canonicalFile.toPath() }.getOrNull() ?: return null
        return file.takeIf { filePath.startsWith(parentPath) }
    }

    private fun readManifest(storageDir: File): LocalStickerPackManifest {
        val manifestFile = storageDir.subFile(ManifestFileName)
        if (!manifestFile.isFile) return LocalStickerPackManifest()
        if (manifestFile.length() > MaxManifestBytes) {
            error("Sticker manifest exceeds maximum size")
        }
        return JsonCodec.decodeFromString(manifestFile.readText())
    }

    private fun writeManifest(storageDir: File, manifest: LocalStickerPackManifest) {
        storageDir.mkdirs()
        val manifestFile = storageDir.subFile(ManifestFileName)
        val tempFile = File.createTempFile("$ManifestFileName-", ".tmp", storageDir)
        try {
            FileOutputStream(tempFile).use { output ->
                output.write(JsonCodec.encodeToString(manifest).toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            moveReplacing(tempFile, manifestFile)
        } catch (error: Throwable) {
            tempFile.delete()
            throw error
        }
    }

    private fun moveReplacing(tempFile: File, targetFile: File) {
        try {
            Files.move(
                tempFile.toPath(),
                targetFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                tempFile.toPath(),
                targetFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private fun readArchiveManifest(zip: ZipFile): LocalStickerPackManifest? {
        val entry = zip.getEntry(ManifestFileName) ?: return null
        if (entry.isDirectory || entry.size > MaxManifestBytes) {
            throw LocalStickerPackException(LocalStickerPackFailure.INVALID_ARCHIVE, "Invalid manifest entry")
        }
        return zip.getInputStream(entry).use { input ->
            val bytes = input.readBytesLimited(MaxManifestBytes)
            JsonCodec.decodeFromString(bytes.toString(Charsets.UTF_8))
        }
    }

    private fun commitImportedFiles(
        storageDir: File,
        tempDir: File,
        imported: List<LocalStickerPackEntry>,
    ) {
        val filesDir = storageDir.subDir(FilesDirName)
        filesDir.mkdirs()
        imported.forEach { entry ->
            val source = tempDir.subFile(entry.fileName)
            val target = fileForEntry(storageDir, entry)
                ?: throw LocalStickerPackException(LocalStickerPackFailure.IO_ERROR, "Invalid target ${entry.fileName}")
            source.copyTo(target, overwrite = true)
        }
    }

    private fun querySharedStickerDocument(context: Context, uri: Uri): UserStickerDocument {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        var displayName: String? = null
        var sizeBytes: Long? = null
        runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameCol = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameCol >= 0 && !cursor.isNull(nameCol)) {
                        displayName = cursor.getString(nameCol)
                    }
                    if (sizeCol >= 0 && !cursor.isNull(sizeCol)) {
                        sizeBytes = cursor.getLong(sizeCol).takeIf { it >= 0L }
                    }
                }
            }
        }
        return UserStickerDocument(
            uri = uri.toString(),
            displayName = displayName ?: uri.lastPathSegment?.substringAfterLast('/') ?: "sticker",
            mimeType = context.contentResolver.getType(uri),
            sizeBytes = sizeBytes,
        )
    }

    private fun localStickerContentUri(stickerId: String): Uri {
        return Uri.Builder()
            .scheme(ContentResolver.SCHEME_CONTENT)
            .authority(StickerMediaProvider.AUTHORITY)
            .appendPath("stickers")
            .appendPath(PackId)
            .appendPath(stickerId)
            .build()
    }

    private fun safeFileNameOrNull(fileName: String): String? {
        val trimmed = fileName.trim()
        if (trimmed.isBlank()) return null
        if (trimmed.contains('/') || trimmed.contains('\\')) return null
        if (trimmed == "." || trimmed == "..") return null
        return trimmed
    }

    private fun isSafeStickerId(id: String): Boolean {
        return id.matches(SafeIdRegex)
    }

    private fun newStickerId(): String {
        return UUID.randomUUID().toString()
    }

    private fun extensionForMimeType(mimeType: String): String {
        return when (mimeType) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/jpeg" -> "jpg"
            "image/gif" -> "gif"
            else -> "bin"
        }
    }

    private fun fileHeaderMatchesMimeType(file: File, mimeType: String): Boolean {
        val header = file.inputStream().use { input ->
            ByteArray(16).also { buffer ->
                val read = input.read(buffer)
                if (read < buffer.size) {
                    buffer.fill(0.toByte(), fromIndex = read.coerceAtLeast(0))
                }
            }
        }
        return when (mimeType) {
            "image/png" -> header.size >= 8 &&
                header[0] == 0x89.toByte() &&
                header[1] == 0x50.toByte() &&
                header[2] == 0x4E.toByte() &&
                header[3] == 0x47.toByte() &&
                header[4] == 0x0D.toByte() &&
                header[5] == 0x0A.toByte() &&
                header[6] == 0x1A.toByte() &&
                header[7] == 0x0A.toByte()
            "image/jpeg" -> header.size >= 3 &&
                header[0] == 0xFF.toByte() &&
                header[1] == 0xD8.toByte() &&
                header[2] == 0xFF.toByte()
            "image/gif" -> header.copyOfRange(0, 6).toString(Charsets.US_ASCII) in setOf("GIF87a", "GIF89a")
            "image/webp" -> header.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF" &&
                header.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WEBP"
            else -> false
        }
    }

    private fun copyCapped(input: InputStream, target: File, maxBytes: Long): Long {
        target.parentFile?.mkdirs()
        var copied = 0L
        target.outputStream().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) return copied
                copied += read
                if (copied > maxBytes) {
                    throw LocalStickerPackException(LocalStickerPackFailure.OVERSIZED)
                }
                output.write(buffer, 0, read)
            }
        }
    }

    private fun InputStream.readBytesLimited(maxBytes: Long): ByteArray {
        val bytes = java.io.ByteArrayOutputStream()
        copyCapped(this, bytes, maxBytes)
        return bytes.toByteArray()
    }

    private fun copyCapped(input: InputStream, output: OutputStream, maxBytes: Long): Long {
        var copied = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return copied
            copied += read
            if (copied > maxBytes) {
                throw LocalStickerPackException(LocalStickerPackFailure.OVERSIZED)
            }
            output.write(buffer, 0, read)
        }
    }

    private class LocalStickerPackException(
        val reason: LocalStickerPackFailure,
        message: String? = null,
    ) : IllegalStateException(message)
}
