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

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import androidx.core.net.toUri
import dev.patrickgold.florisboard.BuildConfig
import dev.patrickgold.florisboard.lib.devtools.flogError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.FileNotFoundException
import org.florisboard.lib.kotlin.tryOrNull

/**
 * Allows apps to access images and videos on the clipboard.
 *
 * This is sometimes called by the UI thread, so all functions are non blocking.
 * Database accesses are performed async.
 */
class ClipboardMediaProvider : ContentProvider() {
    private val clipboardFilesDaoLock = Any()
    @Volatile
    private var clipboardFilesDao: ClipboardFilesDao? = null
    // ConcurrentHashMap: this cache is mutated/read from binder pool threads
    // (insert/delete/getType) AND from the ioScope init() iteration concurrently.
    // A plain HashMap under concurrent structural modification is undefined
    // behaviour (lost writes, CME during init's iteration, corrupted bucket table).
    private val cachedFileInfos = java.util.concurrent.ConcurrentHashMap<Long, ClipboardFileInfo>()
    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val AUTHORITY = "${BuildConfig.APPLICATION_ID}.provider.clipboard"
        val IMAGE_CLIPS_URI: Uri = "content://$AUTHORITY/clips/images".toUri()
        val VIDEO_CLIPS_URI: Uri = "content://$AUTHORITY/clips/videos".toUri()

        private const val IMAGE_CLIP_ITEM = 0
        private const val IMAGE_CLIPS_TABLE = 1
        private const val VIDEO_CLIP_ITEM = 2
        private const val VIDEO_CLIPS_TABLE = 3

        private val Matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "clips/images/#", IMAGE_CLIP_ITEM)
            addURI(AUTHORITY, "clips/images", IMAGE_CLIPS_TABLE)
            addURI(AUTHORITY, "clips/videos/#", VIDEO_CLIP_ITEM)
            addURI(AUTHORITY, "clips/videos", VIDEO_CLIPS_TABLE)
        }
    }

    object Columns {
        const val MediaUri = "media_uri"
        const val MimeTypes = "mime_types"
    }

    fun init() {
        val appContext = requireNotNull(context)
        ClipboardFileStorage.cleanupTransientFiles(appContext)
        ClipboardFileStorage.migratePlaintextFiles(appContext)
        val dao = requireClipboardFilesDao()
        for (clipboardFileInfo in dao.getAll()) {
            cachedFileInfos[clipboardFileInfo.id] = clipboardFileInfo
        }
    }

    private fun requireClipboardFilesDao(): ClipboardFilesDao {
        clipboardFilesDao?.let { return it }
        return synchronized(clipboardFilesDaoLock) {
            clipboardFilesDao ?: ClipboardFilesDatabase.new(context!!).clipboardFilesDao().also { dao ->
                clipboardFilesDao = dao
            }
        }
    }

    private fun cachedFileInfo(id: Long): ClipboardFileInfo? {
        return cachedFileInfos[id] ?: requireClipboardFilesDao().getById(id)?.also { fileInfo ->
            cachedFileInfos[id] = fileInfo
        }
    }

    override fun onCreate(): Boolean {
        ioScope.launch {
            init()
        }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val id = tryOrNull { ContentUris.parseId(uri) } ?: return null
        if (projection != null) {
            return if (projection.contains(MediaStore.Images.Media.ORIENTATION)) {
                // Callers (notably the platform image-paste path) request
                // just the ORIENTATION column. Return that cursor instead
                // of discarding it and falling through to the full row.
                requireClipboardFilesDao().getOrientationCursorById(id)
            } else {
                //Return null if the projection query is invalid
                null
            }
        }
        return requireClipboardFilesDao().getCursorById(id)
    }

    override fun getType(uri: Uri): String? {
        return when (Matcher.match(uri)) {
            IMAGE_CLIP_ITEM, VIDEO_CLIP_ITEM -> {
                cachedFileInfo(ContentUris.parseId(uri))?.mimeTypes?.getOrNull(0)
            }
            IMAGE_CLIPS_TABLE -> "${ContentResolver.CURSOR_DIR_BASE_TYPE}/vnd.florisboard.image_clip_table"
            VIDEO_CLIPS_TABLE -> "${ContentResolver.CURSOR_DIR_BASE_TYPE}/vnd.florisboard.video_clip_table"
            else -> null
        }
    }

    override fun getStreamTypes(uri: Uri, mimeTypeFilter: String): Array<String>? {
        return when (Matcher.match(uri)) {
            IMAGE_CLIP_ITEM, VIDEO_CLIP_ITEM -> {
                cachedFileInfo(ContentUris.parseId(uri))?.mimeTypes?.toTypedArray()
            }
            else -> null
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (!mode.startsWith("r")) {
            throw FileNotFoundException("Clipboard media is read-only")
        }
        val mediaKind = when (Matcher.match(uri)) {
            IMAGE_CLIP_ITEM -> ClipboardFileStorage.MediaKind.IMAGE
            VIDEO_CLIP_ITEM -> ClipboardFileStorage.MediaKind.VIDEO
            else -> throw FileNotFoundException("Unknown clipboard media URI: $uri")
        }
        val id = ContentUris.parseId(uri)
        val plaintextFile = try {
            ClipboardFileStorage.openDecryptedTempFile(
                context = requireNotNull(context),
                id = id,
                mediaKind = mediaKind,
            )
        } catch (error: Throwable) {
            throw FileNotFoundException("Cannot decrypt clipboard media URI: $uri").also {
                it.initCause(error)
            }
        }
        return try {
            // The descriptor is seekable for receivers such as video decoders,
            // but its plaintext backing file is deleted as soon as the grant
            // closes. Eviction also scans and removes still-open transient files.
            ParcelFileDescriptor.open(
                plaintextFile,
                ParcelFileDescriptor.MODE_READ_ONLY,
                Handler(Looper.getMainLooper()),
                ParcelFileDescriptor.OnCloseListener { plaintextFile.delete() },
            )
        } catch (error: Throwable) {
            plaintextFile.delete()
            throw error
        }
    }

    private fun readImageRotation(context: Context, mediaUri: Uri): Int {
        context.contentResolver.openInputStream(mediaUri).use { inputStream ->
            requireNotNull(inputStream) {
                "Unable to open clipboard image URI for orientation"
            }
            val exifInterface = ExifInterface(inputStream)
            return when (exifInterface.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri {
        when (val m = Matcher.match(uri)) {
            IMAGE_CLIPS_TABLE, VIDEO_CLIPS_TABLE -> {
                return try {
                    values as ContentValues
                    val context = context!!
                    val mediaUri = values.getAsString(Columns.MediaUri).toUri()
                    val mediaKind = when (m) {
                        IMAGE_CLIPS_TABLE -> ClipboardFileStorage.MediaKind.IMAGE
                        VIDEO_CLIPS_TABLE -> ClipboardFileStorage.MediaKind.VIDEO
                        else -> error("Unexpected media table $m")
                    }
                    val rotation = if (ClipboardMediaClonePolicy.shouldReadExifOrientation(mediaKind)) {
                        readImageRotation(context, mediaUri)
                    } else {
                        0
                    }
                    val clonedFile = ClipboardFileStorage.cloneUri(context, mediaUri, mediaKind)
                    val mimeTypes = values.getAsString(Columns.MimeTypes).split(",")
                    val displayName = values.getAsString(OpenableColumns.DISPLAY_NAME)
                    val fileInfo = ClipboardFileInfo(
                        clonedFile.id,
                        displayName,
                        clonedFile.plaintextSize,
                        rotation,
                        mimeTypes,
                    )
                    cachedFileInfos[clonedFile.id] = fileInfo
                    ioScope.launch {
                        requireClipboardFilesDao().insert(fileInfo)
                    }
                    if (m == IMAGE_CLIPS_TABLE) {
                        ContentUris.withAppendedId(IMAGE_CLIPS_URI, clonedFile.id)
                    } else {
                        ContentUris.withAppendedId(VIDEO_CLIPS_URI, clonedFile.id)
                    }
                } catch (e: Exception) {
                    flogError { "Failed to clone clipboard media URI: ${e.message.orEmpty()}" }
                    throw e
                }
            }
            else -> error("Unable to identify type of $uri")
        }
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        when (Matcher.match(uri)) {
            IMAGE_CLIP_ITEM, VIDEO_CLIP_ITEM -> {
                val id = ContentUris.parseId(uri)
                ClipboardFileStorage.deleteById(context!!, id)
                cachedFileInfos.remove(id)
                context?.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                ioScope.launch {
                    requireClipboardFilesDao().delete(id)
                }
                return 1
            }
            else -> error("Unable to identify type of $uri")
        }
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        error("This ContentProvider does not support update.")
    }
}
