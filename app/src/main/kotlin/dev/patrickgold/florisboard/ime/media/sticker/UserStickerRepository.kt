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
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import dev.patrickgold.florisboard.lib.devtools.flogWarning
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
        // If Android revoked the persistable URI grant since the user picked
        // the folder (uninstall+reinstall of the file-manager that issued
        // the grant, system-wide grant cleanup, factory pattern restore),
        // contentResolver.query throws SecurityException, runCatching
        // swallows it, and the user sees an empty Imported tab with no
        // signal as to why. Log explicitly so the cause shows up in logcat,
        // and let MediaScreen surface a re-pick prompt via the public
        // hasPersistableReadPermission helper.
        if (!hasPersistableReadPermission(context, folderUriRaw)) {
            flogWarning {
                "UserStickerRepository: persistable read grant lost for $folderUriRaw; user needs to re-pick the folder."
            }
            return null
        }
        val documents = runCatching { queryStickerDocuments(context, treeUri) }.getOrDefault(emptyList())
        return packFromDocuments(documents, displayName = treeUri.lastPathSegment?.substringAfterLast(':') ?: PackName)
    }

    /**
     * Returns true when [folderUriRaw] is currently in the IME's
     * `contentResolver.persistedUriPermissions` set with at least read access.
     * Settings should use this to surface a "re-pick folder" prompt when the
     * grant has been revoked between selection and the next process start.
     */
    fun hasPersistableReadPermission(context: Context, folderUriRaw: String): Boolean {
        if (folderUriRaw.isBlank()) return false
        val target = runCatching { Uri.parse(folderUriRaw) }.getOrNull() ?: return false
        val grants = runCatching { context.contentResolver.persistedUriPermissions }
            .getOrDefault(emptyList())
        return grants.any { grant -> grant.uri == target && grant.isReadPermission }
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
        // Confused-deputy guard: the encoded segment arrives back from whatever
        // app received the sticker grant, so it is attacker-controlled. A
        // recipient could forge the Base64 segment to wrap any content:// URI
        // and have StickerMediaProvider proxy-open it with the IME's own
        // grants. Refuse anything that is not a document inside a folder the
        // user actually picked for stickers (= a currently persisted SAF tree
        // read grant).
        if (!isDocumentWithinPersistedGrant(context, uri)) {
            flogWarning {
                "UserStickerRepository: rejected sticker document outside persisted SAF grants: $uri"
            }
            return null
        }
        val document = queryDocument(context, uri) ?: UserStickerDocument(
            uri = rawUri,
            displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "sticker",
            mimeType = context.contentResolver.getType(uri),
        )
        return stickerFromDocument(document)
    }

    /**
     * Returns true when [documentUri] is a SAF tree-anchored document URI
     * (`content://<authority>/tree/<treeId>/document/<docId>` — the only shape
     * [queryStickerDocuments] ever mints via `buildDocumentUriUsingTree`) whose
     * authority + tree document id match a currently persisted read grant. The
     * backing `DocumentsProvider` enforces at open time that `<docId>` really
     * is a child of the tree the URI is anchored to, so this check pins every
     * proxied open inside a folder the user explicitly picked for stickers.
     */
    fun isDocumentWithinPersistedGrant(context: Context, documentUri: Uri): Boolean {
        if (documentUri.scheme != ContentResolver.SCHEME_CONTENT) return false
        val authority = documentUri.authority ?: return false
        val segments = documentUri.pathSegments
        if (segments.size != 4 || segments[0] != "tree" || segments[2] != "document") return false
        val treeDocumentId = runCatching { DocumentsContract.getTreeDocumentId(documentUri) }
            .getOrNull() ?: return false
        val grants = runCatching { context.contentResolver.persistedUriPermissions }
            .getOrDefault(emptyList())
        return grants.any { grant ->
            grant.isReadPermission &&
                grant.uri.scheme == ContentResolver.SCHEME_CONTENT &&
                grant.uri.authority == authority &&
                grant.uri.pathSegments.size == 2 &&
                grant.uri.pathSegments.firstOrNull() == "tree" &&
                runCatching { DocumentsContract.getTreeDocumentId(grant.uri) }.getOrNull() == treeDocumentId
        }
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
        // Cap inside the cursor loop so a 50k-file Downloads folder doesn't
        // allocate 50k UserStickerDocument objects (plus their string fields)
        // only to be `take(MaxStickers)`-trimmed downstream. We collect
        // slightly more than MaxStickers so the downstream `sortedBy` /
        // `distinctBy` can still pick from a wider set than the final
        // displayed count, but cap hard before the cursor walk gets out
        // of hand.
        val enumerationCap = MaxStickers * 4
        val documents = ArrayList<UserStickerDocument>(MaxStickers)
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndex(Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndex(Document.COLUMN_DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndex(Document.COLUMN_MIME_TYPE)
            val sizeCol = cursor.getColumnIndex(Document.COLUMN_SIZE)
            while (cursor.moveToNext()) {
                if (documents.size >= enumerationCap) {
                    flogWarning {
                        "UserStickerRepository: capped folder enumeration at $enumerationCap entries; some files in the picked folder will be ignored. Move the imported-sticker folder to a smaller scope."
                    }
                    break
                }
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
        val declaredLower = declaredMimeType?.lowercase(Locale.ROOT)?.trim()
        // If SAF declares a concrete MIME and it's in the supported image set,
        // honour it.
        if (!declaredLower.isNullOrEmpty() && declaredLower in SupportedMimeTypes) {
            return declaredLower
        }
        // If SAF declares a concrete non-image MIME (e.g. application/octet-stream,
        // text/plain, application/zip), REJECT outright. Previous logic fell
        // back to the filename extension here, which let "evil.bin" renamed to
        // "evil.png" be committed to recipient editors as image/png — a MIME
        // spoof against any app whose commitContent receiver trusts the
        // announced MIME (e.g. messengers that auto-decode and forward
        // attachments).
        if (!declaredLower.isNullOrEmpty()) return null
        // Only when SAF gives us nothing (null / blank) do we trust the
        // extension. Many file managers omit MIME entirely; this keeps the
        // common case working without re-opening the spoof.
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
