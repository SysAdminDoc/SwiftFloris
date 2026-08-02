/*
 * Copyright (C) 2022-2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.clipboard.provider

import android.content.Context
import android.net.Uri
import dev.patrickgold.florisboard.lib.devtools.LogTopic
import dev.patrickgold.florisboard.lib.devtools.flogDebug
import org.florisboard.lib.kotlin.io.FsFile
import org.florisboard.lib.kotlin.io.subFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicLong

/**
 * Backend helper object which is used by [ClipboardMediaProvider] to serve content.
 */
object ClipboardFileStorage {
    const val CLIPBOARD_FILES_PATH = "clipboard_files"
    const val MAX_IMAGE_CLIP_BYTES = 32L * 1024L * 1024L
    const val MAX_VIDEO_CLIP_BYTES = 128L * 1024L * 1024L

    /**
     * Monotonic id source for cloned files. Seeded from [System.nanoTime] so a fresh
     * process is very unlikely to immediately reuse an id from the previous run, but
     * because `nanoTime()` is boot-relative (and can restart low after a reboot) the
     * actual collision guard is the on-disk `exists()` check in [cloneUri] — never the
     * counter alone.
     */
    private val idSource = AtomicLong(System.nanoTime())

    private val Context.clipboardFilesDir: FsFile
        get() = FsFile(this.noBackupFilesDir, "clipboard_files").also { it.mkdirs() }

    private val Context.clipboardTransientDir: FsFile
        get() = FsFile(this.cacheDir, "clipboard_media").also { it.mkdirs() }

    enum class MediaKind {
        IMAGE,
        VIDEO;

        val maxCloneBytes: Long
            get() = when (this) {
                IMAGE -> MAX_IMAGE_CLIP_BYTES
                VIDEO -> MAX_VIDEO_CLIP_BYTES
            }
    }

    data class RestoredFile(
        val id: Long,
        val file: FsFile,
        val plaintextSize: Long,
    )

    private data class AvailableFile(
        val id: Long,
        val file: FsFile,
    )

    private fun nextAvailableFile(context: Context): AvailableFile {
        val dir = context.clipboardFilesDir
        var id = idSource.getAndIncrement()
        var file = dir.subFile(id.toString())
        while (file.exists()) {
            id = idSource.getAndIncrement()
            file = dir.subFile(id.toString())
        }
        return AvailableFile(id, file)
    }

    /**
     * Clones a content URI to internal storage.
     *
     * @param uri The URI
     *
     * @return the stored file id and plaintext size
     */
    @Synchronized
    fun cloneUri(context: Context, uri: Uri, mediaKind: MediaKind): RestoredFile {
        // Pick an id that does not already name a stored file. nanoTime() alone is
        // boot-relative and can collide with files written before a reboot, which
        // would silently overwrite an existing clipboard entry; the exists() guard
        // makes the id genuinely unique against what is on disk.
        val availableFile = nextAvailableFile(context)
        try {
            val input = requireNotNull(context.contentResolver.openInputStream(uri)) {
                "Unable to open clipboard media URI: $uri"
            }
            val plaintextSize = input.use {
                ClipboardMediaEncryption.encrypt(
                    context = context,
                    input = it,
                    target = availableFile.file,
                    maxPlaintextBytes = mediaKind.maxCloneBytes,
                )
            }
            return RestoredFile(availableFile.id, availableFile.file, plaintextSize)
        } catch (e: Exception) {
            availableFile.file.delete()
            throw e
        }
    }

    /**
     * Deletes the file corresponding to an id.
     */
    fun deleteById(context: Context, id: Long) {
        flogDebug(LogTopic.CLIPBOARD) { "Cleaning up $id" }
        val file = context.clipboardFilesDir.subFile(id.toString())
        file.delete()
        deleteTransientFilesForId(context, id)
    }

    fun getFileForId(context: Context, id: Long): FsFile {
        return context.clipboardFilesDir.subFile(id.toString())
    }

    fun listStoredFileIds(context: Context): Set<Long> {
        return context.clipboardFilesDir.listFiles()
            ?.mapNotNull { it.name.toLongOrNull() }
            ?.toSet()
            ?: emptySet()
    }

    /**
     * Imports backup media under a fresh provider id. Portable archives can
     * originate on another device, so their numeric ids must never alias an
     * unrelated file already present in a merge restore.
     */
    @Synchronized
    fun insertFileFromBackup(
        context: Context,
        source: FsFile,
        mediaKind: MediaKind,
    ): RestoredFile {
        require(source.isFile) { "Clipboard backup media file is missing." }
        val availableFile = nextAvailableFile(context)
        try {
            val plaintextSize = if (ClipboardMediaEncryption.isEncrypted(source)) {
                ClipboardMediaEncryption.decrypt(
                    context = context,
                    source = source,
                    target = availableFile.file,
                    maxPlaintextBytes = mediaKind.maxCloneBytes,
                )
            } else {
                source.inputStream().use {
                    ClipboardMediaEncryption.encrypt(
                        context = context,
                        input = it,
                        target = availableFile.file,
                        maxPlaintextBytes = mediaKind.maxCloneBytes,
                    )
                }
            }
            return RestoredFile(availableFile.id, availableFile.file, plaintextSize)
        } catch (error: Throwable) {
            availableFile.file.delete()
            throw error
        }
    }

    /**
     * Copies media into a portable-backup workspace in plaintext. The caller
     * immediately seals that workspace inside [PortableBackupEnvelope]; the
     * app-private workspace is deleted before any SAF/share operation.
     */
    fun copyDecryptedTo(
        context: Context,
        id: Long,
        target: FsFile,
        mediaKind: MediaKind,
    ): Long {
        val source = getFileForId(context, id)
        require(source.isFile) { "Clipboard media file $id is missing." }
        return try {
            if (ClipboardMediaEncryption.isEncrypted(source)) {
                ClipboardMediaEncryption.decrypt(
                    context = context,
                    source = source,
                    target = target,
                    maxPlaintextBytes = mediaKind.maxCloneBytes,
                )
            } else {
                source.inputStream().use {
                    ClipboardMediaEncryption.copyPlaintext(
                        input = it,
                        target = target,
                        maxPlaintextBytes = mediaKind.maxCloneBytes,
                    )
                }
            }
        } catch (error: Throwable) {
            target.delete()
            throw error
        }
    }

    /** Migrates legacy plaintext media without deleting the last good copy first. */
    @Synchronized
    fun migratePlaintextFiles(context: Context) {
        context.clipboardFilesDir.listFiles()
            ?.filter { it.isFile && it.name.toLongOrNull() != null }
            ?.forEach { file ->
                if (ClipboardMediaEncryption.isEncrypted(file)) return@forEach
                val replacement = FsFile.createTempFile(
                    ".${file.name}.encrypted-",
                    ".tmp",
                    file.parentFile,
                )
                try {
                    file.inputStream().use {
                        ClipboardMediaEncryption.encrypt(
                            context = context,
                            input = it,
                            target = replacement,
                            maxPlaintextBytes = MAX_VIDEO_CLIP_BYTES,
                        )
                    }
                    runCatching {
                        Files.move(
                            replacement.toPath(),
                            file.toPath(),
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING,
                        )
                    }.recoverCatching {
                        Files.move(
                            replacement.toPath(),
                            file.toPath(),
                            StandardCopyOption.REPLACE_EXISTING,
                        )
                    }.getOrThrow()
                } finally {
                    replacement.delete()
                }
            }
    }

    /** Creates a seekable, short-lived plaintext copy for a receiving app. */
    fun openDecryptedTempFile(
        context: Context,
        id: Long,
        mediaKind: MediaKind,
    ): FsFile {
        val source = getFileForId(context, id)
        require(source.isFile) { "Clipboard media file $id is missing." }
        if (!ClipboardMediaEncryption.isEncrypted(source)) {
            migratePlaintextFile(context, source)
        }
        val tempFile = FsFile.createTempFile(
            "$id-",
            ".plain",
            context.clipboardTransientDir,
        )
        return try {
            ClipboardMediaEncryption.decrypt(
                context = context,
                source = source,
                target = tempFile,
                maxPlaintextBytes = mediaKind.maxCloneBytes,
            )
            tempFile
        } catch (error: Throwable) {
            tempFile.delete()
            throw error
        }
    }

    fun cleanupTransientFiles(context: Context) {
        context.clipboardTransientDir.listFiles()?.forEach { it.delete() }
    }

    private fun deleteTransientFilesForId(context: Context, id: Long) {
        context.clipboardTransientDir.listFiles()
            ?.filter { it.name.startsWith("$id-") }
            ?.forEach { it.delete() }
    }

    @Synchronized
    private fun migratePlaintextFile(context: Context, file: FsFile) {
        if (ClipboardMediaEncryption.isEncrypted(file)) return
        val replacement = FsFile.createTempFile(
            ".${file.name}.encrypted-",
            ".tmp",
            file.parentFile,
        )
        try {
            file.inputStream().use {
                ClipboardMediaEncryption.encrypt(
                    context = context,
                    input = it,
                    target = replacement,
                    maxPlaintextBytes = MAX_VIDEO_CLIP_BYTES,
                )
            }
            runCatching {
                Files.move(
                    replacement.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }.recoverCatching {
                Files.move(
                    replacement.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }.getOrThrow()
        } finally {
            replacement.delete()
        }
    }

    /**
     * Deletes all files from the clipboard subdirectory
     *
     * @param context the application context
     */
    fun resetClipboardFileStorage(context: Context) {
        context.clipboardFilesDir.listFiles()?.forEach {
            it.deleteRecursively()
        }
        cleanupTransientFiles(context)
    }

}
