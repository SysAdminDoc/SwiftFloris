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
import org.florisboard.lib.android.readToFile
import org.florisboard.lib.kotlin.io.FsFile
import org.florisboard.lib.kotlin.io.subFile
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
    )

    private fun nextAvailableFile(context: Context): RestoredFile {
        val dir = context.clipboardFilesDir
        var id = idSource.getAndIncrement()
        var file = dir.subFile(id.toString())
        while (file.exists()) {
            id = idSource.getAndIncrement()
            file = dir.subFile(id.toString())
        }
        return RestoredFile(id, file)
    }

    /**
     * Clones a content URI to internal storage.
     *
     * @param uri The URI
     *
     * @return The file's name which is a unique long
     */
    @Synchronized
    fun cloneUri(context: Context, uri: Uri, mediaKind: MediaKind): Long {
        // Pick an id that does not already name a stored file. nanoTime() alone is
        // boot-relative and can collide with files written before a reboot, which
        // would silently overwrite an existing clipboard entry; the exists() guard
        // makes the id genuinely unique against what is on disk.
        val restoredFile = nextAvailableFile(context)
        try {
            context.contentResolver.readToFile(
                uri,
                restoredFile.file,
                mediaKind.maxCloneBytes,
            )
        } catch (e: Exception) {
            restoredFile.file.delete()
            throw e
        }
        return restoredFile.id
    }

    /**
     * Deletes the file corresponding to an id.
     */
    fun deleteById(context: Context, id: Long) {
        flogDebug(LogTopic.CLIPBOARD) { "Cleaning up $id" }
        val file = context.clipboardFilesDir.subFile(id.toString())
        file.delete()
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
    fun insertFileFromBackup(context: Context, source: FsFile): RestoredFile {
        require(source.isFile) { "Clipboard backup media file is missing." }
        val restoredFile = nextAvailableFile(context)
        try {
            source.copyTo(restoredFile.file, overwrite = false)
            return restoredFile
        } catch (error: Throwable) {
            restoredFile.file.delete()
            throw error
        }
    }

    /**
     * Deletes all files from the clipboard subdirectory
     *
     * @param context the application context
     */
    fun resetClipboardFileStorage(context: Context) {
        context.clipboardFilesDir.listFiles()?.forEach {
            it.delete()
        }
    }

}
