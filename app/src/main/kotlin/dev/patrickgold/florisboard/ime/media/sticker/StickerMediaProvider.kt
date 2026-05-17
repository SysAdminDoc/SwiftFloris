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

import android.content.ClipDescription
import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import dev.patrickgold.florisboard.BuildConfig
import java.io.File
import java.io.FileNotFoundException

class StickerMediaProvider : ContentProvider() {
    companion object {
        const val AUTHORITY = "${BuildConfig.APPLICATION_ID}.provider.sticker"

        fun uriFor(sticker: Sticker): Uri {
            return Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(AUTHORITY)
                .appendPath("stickers")
                .appendPath(sticker.packId)
                .appendPath(sticker.id)
                .build()
        }
    }

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? {
        return stickerFor(uri)?.mimeType
    }

    override fun getStreamTypes(uri: Uri, mimeTypeFilter: String): Array<String>? {
        val sticker = stickerFor(uri) ?: return null
        return if (ClipDescription.compareMimeTypes(sticker.mimeType, mimeTypeFilter)) {
            arrayOf(sticker.mimeType)
        } else {
            null
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val sticker = stickerFor(uri) ?: return null
        val size = sticker.sourceUri?.let { sizeOfSourceUri(it) } ?: ensureStickerFile(sticker).length()
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns).apply {
            val row = newRow()
            for (column in columns) {
                when (column) {
                    OpenableColumns.DISPLAY_NAME -> row.add(sticker.displayName)
                    OpenableColumns.SIZE -> row.add(size)
                    else -> row.add(null)
                }
            }
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (!mode.startsWith("r")) {
            throw FileNotFoundException("Stickers are read-only")
        }
        val sticker = stickerFor(uri) ?: throw FileNotFoundException("Unknown sticker URI: $uri")
        sticker.sourceUri?.let { sourceUri ->
            return context!!.contentResolver.openFileDescriptor(Uri.parse(sourceUri), "r")
                ?: throw FileNotFoundException("Cannot open sticker URI: $sourceUri")
        }
        val file = ensureStickerFile(sticker)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw UnsupportedOperationException("StickerMediaProvider does not support insert")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        throw UnsupportedOperationException("StickerMediaProvider does not support delete")
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        throw UnsupportedOperationException("StickerMediaProvider does not support update")
    }

    private fun stickerFor(uri: Uri): Sticker? {
        val segments = uri.pathSegments
        if (segments.size != 3 || segments[0] != "stickers") return null
        if (segments[1] == UserStickerRepository.PackId) {
            return UserStickerRepository.stickerForEncodedDocument(context!!, segments[2])
        }
        return BundledStickerRepository.find(packId = segments[1], stickerId = segments[2])
    }

    private fun ensureStickerFile(sticker: Sticker): File {
        val file = File(context!!.cacheDir, "stickers/${sticker.fileName}")
        if (!file.isFile || file.length() == 0L) {
            StickerRenderer.renderPng(sticker, file)
        }
        return file
    }

    private fun sizeOfSourceUri(sourceUri: String): Long? {
        return runCatching {
            context!!.contentResolver.openFileDescriptor(Uri.parse(sourceUri), "r")?.use { pfd ->
                pfd.statSize.takeIf { it >= 0L }
            }
        }.getOrNull()
    }
}
