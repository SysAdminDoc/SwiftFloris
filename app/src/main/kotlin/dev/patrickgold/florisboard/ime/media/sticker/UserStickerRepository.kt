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

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.util.Base64
import java.util.Locale

data class UserStickerDocument(
    val uri: String,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long? = null,
)

object UserStickerRepository {
    const val PackId = "user_imported"
    const val PackName = "Imported"
    const val MaxStickers = 240

    val SupportedMimeTypes = listOf(
        "image/png",
        "image/webp",
        "image/jpeg",
        "image/gif",
    )

    fun loadPack(context: Context, folderUriRaw: String): StickerPack? {
        if (folderUriRaw.isBlank()) return null
        val treeUri = runCatching { Uri.parse(folderUriRaw) }.getOrNull() ?: return null
        val documents = runCatching { queryStickerDocuments(context, treeUri) }.getOrDefault(emptyList())
        return packFromDocuments(documents, displayName = treeUri.lastPathSegment?.substringAfterLast(':') ?: PackName)
    }

    fun packFromDocuments(
        documents: List<UserStickerDocument>,
        displayName: String = PackName,
    ): StickerPack? {
        val stickers = documents
            .asSequence()
            .mapNotNull { document -> stickerFromDocument(document) }
            .distinctBy { it.sourceUri }
            .sortedBy { it.label.lowercase(Locale.ROOT) }
            .take(MaxStickers)
            .toList()
        if (stickers.isEmpty()) return null
        return StickerPack(
            id = PackId,
            name = displayName.ifBlank { PackName },
            stickers = stickers,
        )
    }

    fun stickerForEncodedDocument(context: Context, encodedDocumentUri: String): Sticker? {
        val rawUri = decodeDocumentUri(encodedDocumentUri) ?: return null
        val uri = runCatching { Uri.parse(rawUri) }.getOrNull() ?: return null
        val document = queryDocument(context, uri) ?: UserStickerDocument(
            uri = rawUri,
            displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "sticker",
            mimeType = context.contentResolver.getType(uri),
        )
        return stickerFromDocument(document)
    }

    private fun queryStickerDocuments(context: Context, treeUri: Uri): List<UserStickerDocument> {
        val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocumentId)
        val projection = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
        )
        val documents = mutableListOf<UserStickerDocument>()
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndex(Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndex(Document.COLUMN_DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndex(Document.COLUMN_MIME_TYPE)
            val sizeCol = cursor.getColumnIndex(Document.COLUMN_SIZE)
            while (cursor.moveToNext()) {
                val documentId = cursor.getStringOrNull(idCol) ?: continue
                val mimeType = cursor.getStringOrNull(mimeCol)
                if (mimeType == Document.MIME_TYPE_DIR) continue
                val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                documents += UserStickerDocument(
                    uri = documentUri.toString(),
                    displayName = cursor.getStringOrNull(nameCol) ?: documentId.substringAfterLast('/'),
                    mimeType = mimeType,
                    sizeBytes = cursor.getLongOrNull(sizeCol),
                )
            }
        }
        return documents
    }

    private fun queryDocument(context: Context, uri: Uri): UserStickerDocument? {
        val projection = arrayOf(
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
        )
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val nameCol = cursor.getColumnIndex(Document.COLUMN_DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndex(Document.COLUMN_MIME_TYPE)
            val sizeCol = cursor.getColumnIndex(Document.COLUMN_SIZE)
            return UserStickerDocument(
                uri = uri.toString(),
                displayName = cursor.getStringOrNull(nameCol) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "sticker",
                mimeType = cursor.getStringOrNull(mimeCol) ?: context.contentResolver.getType(uri),
                sizeBytes = cursor.getLongOrNull(sizeCol),
            )
        }
        return null
    }

    private fun stickerFromDocument(document: UserStickerDocument): Sticker? {
        val mimeType = resolveMimeType(document.displayName, document.mimeType) ?: return null
        val sourceUri = document.uri.ifBlank { return null }
        val label = labelFromDisplayName(document.displayName)
        return Sticker(
            packId = PackId,
            id = encodeDocumentUri(sourceUri),
            label = label,
            emoji = "IMG",
            keywords = keywordsFor(label),
            backgroundColor = 0xFF1F2937.toInt(),
            accentColor = 0xFF60A5FA.toInt(),
            mimeType = mimeType,
            sourceUri = sourceUri,
            displayName = document.displayName.ifBlank { "$label.${mimeType.substringAfter('/')}" },
        )
    }

    private fun resolveMimeType(displayName: String, declaredMimeType: String?): String? {
        val declared = declaredMimeType?.lowercase(Locale.ROOT)?.takeIf { it in SupportedMimeTypes }
        if (declared != null) return declared
        return when (displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            else -> null
        }
    }

    private fun labelFromDisplayName(displayName: String): String {
        return displayName
            .substringBeforeLast('.', displayName)
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
            .ifBlank { "Sticker" }
    }

    private fun keywordsFor(label: String): List<String> {
        return label
            .lowercase(Locale.ROOT)
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun encodeDocumentUri(uri: String): String {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(uri.toByteArray(Charsets.UTF_8))
    }

    private fun decodeDocumentUri(encoded: String): String? {
        return runCatching {
            String(Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8)
        }.getOrNull()
    }
}

private fun android.database.Cursor.getStringOrNull(index: Int): String? {
    return if (index >= 0 && !isNull(index)) getString(index) else null
}

private fun android.database.Cursor.getLongOrNull(index: Int): Long? {
    return if (index >= 0 && !isNull(index)) getLong(index) else null
}
