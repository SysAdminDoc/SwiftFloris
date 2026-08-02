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

package dev.patrickgold.florisboard.app.settings.advanced

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

internal data class ScheduledBackupDocument(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val size: Long,
)

internal data class PublishedScheduledBackup(
    val uri: Uri,
    val name: String,
)

/** SAF publication and authenticated retention for scheduled archives. */
internal object ScheduledBackupSaf {
    private const val ArchiveMimeType = "application/octet-stream"
    private const val TemporaryNameSuffix = ".swiftfloris.tmp"
    private const val CopyBufferBytes = 64 * 1024

    fun publish(
        context: Context,
        treeUri: Uri,
        archive: File,
        finalName: String,
    ): PublishedScheduledBackup {
        require(archive.isFile) { "Scheduled backup archive is missing." }
        require(archive.length() <= PortableBackupEnvelope.MaxEnvelopeBytes) {
            "Scheduled backup archive exceeds the supported size limit."
        }
        val resolver = context.contentResolver
        val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocumentId)
        val temporaryName = ".${finalName.removeSuffix(ScheduledBackupPolicy.ArchiveExtension)}" +
            "_${UUID.randomUUID()}$TemporaryNameSuffix"
        val temporaryUri = DocumentsContract.createDocument(
            resolver,
            parentUri,
            ArchiveMimeType,
            temporaryName,
        ) ?: throw FileNotFoundException("Could not create a temporary SAF backup document.")

        var published = false
        try {
            writeArchive(resolver, temporaryUri, archive)
            check(archiveDigest(archive).contentEquals(documentDigest(resolver, temporaryUri))) {
                "SAF backup verification did not match the local archive."
            }
            val finalUri = DocumentsContract.renameDocument(resolver, temporaryUri, finalName)
                ?: throw IllegalStateException("The selected SAF provider cannot atomically rename backups.")
            published = true
            return PublishedScheduledBackup(finalUri, finalName)
        } finally {
            if (!published) {
                runCatching { DocumentsContract.deleteDocument(resolver, temporaryUri) }
            }
        }
    }

    fun pruneVerified(
        context: Context,
        treeUri: Uri,
        retentionCount: Int,
        passphrase: CharArray,
    ) {
        val candidates = listDocuments(context, treeUri)
            .mapNotNull { document ->
                ScheduledBackupPolicy.timestampFromArchiveName(document.name)?.let { timestamp ->
                    timestamp to document
                }
            }
            .sortedBy { it.first }
        var remaining = candidates.size - ScheduledBackupPolicy.normalizeRetention(retentionCount)
        if (remaining <= 0) return

        for ((_, document) in candidates) {
            if (remaining <= 0) break
            if (!verifyEncryptedDocument(context, document, passphrase)) continue
            if (DocumentsContract.deleteDocument(context.contentResolver, document.uri)) {
                remaining--
            }
        }
    }

    internal fun listDocuments(context: Context, treeUri: Uri): List<ScheduledBackupDocument> {
        val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocumentId)
        val projection = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
        )
        val documents = mutableListOf<ScheduledBackupDocument>()
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndex(Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndex(Document.COLUMN_DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndex(Document.COLUMN_MIME_TYPE)
            val sizeColumn = cursor.getColumnIndex(Document.COLUMN_SIZE)
            while (cursor.moveToNext()) {
                val documentId = cursor.getStringOrNull(idColumn) ?: continue
                val name = cursor.getStringOrNull(nameColumn) ?: continue
                val mimeType = cursor.getStringOrNull(mimeColumn).orEmpty()
                val size = if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) {
                    cursor.getLong(sizeColumn)
                } else {
                    -1L
                }
                documents += ScheduledBackupDocument(
                    uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId),
                    name = name,
                    mimeType = mimeType,
                    size = size,
                )
            }
        }
        return documents
    }

    private fun writeArchive(
        resolver: android.content.ContentResolver,
        targetUri: Uri,
        archive: File,
    ) {
        val descriptor = resolver.openFileDescriptor(targetUri, "w")
            ?: throw FileNotFoundException(targetUri.toString())
        ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use { output ->
            FileInputStream(archive).use { input ->
                input.copyTo(output, CopyBufferBytes)
            }
            output.fd.sync()
        }
    }

    private fun documentDigest(
        resolver: android.content.ContentResolver,
        uri: Uri,
    ): ByteArray {
        val input = resolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())
        return input.use { digest(it, PortableBackupEnvelope.MaxEnvelopeBytes) }
    }

    private fun archiveDigest(archive: File): ByteArray =
        FileInputStream(archive).use { digest(it, PortableBackupEnvelope.MaxEnvelopeBytes) }

    private fun digest(input: InputStream, maxBytes: Long): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(CopyBufferBytes)
        var total = 0L
        try {
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                total += read
                check(total <= maxBytes) { "SAF backup document exceeds the supported size limit." }
                digest.update(buffer, 0, read)
            }
            return digest.digest()
        } finally {
            buffer.fill(0)
        }
    }

    private fun verifyEncryptedDocument(
        context: Context,
        document: ScheduledBackupDocument,
        passphrase: CharArray,
    ): Boolean {
        if (document.mimeType == Document.MIME_TYPE_DIR) return false
        if (document.size > PortableBackupEnvelope.MaxEnvelopeBytes) return false
        val root = File(context.cacheDir, "scheduled-backup-verification/${UUID.randomUUID()}")
        val source = File(root, "archive.sfbak")
        val plaintext = File(root, "archive.zip")
        return try {
            check(root.mkdirs()) { "Could not create the private backup verification directory." }
            copyDocumentToFile(context, document.uri, source)
            PortableBackupEnvelope.decrypt(source, plaintext, passphrase)
            true
        } catch (_: Throwable) {
            false
        } finally {
            root.deleteRecursively()
        }
    }

    private fun copyDocumentToFile(context: Context, uri: Uri, target: File) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw FileNotFoundException(uri.toString())
        input.use { source ->
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(CopyBufferBytes)
                var total = 0L
                try {
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        total += read
                        check(total <= PortableBackupEnvelope.MaxEnvelopeBytes) {
                            "SAF backup document exceeds the supported size limit."
                        }
                        output.write(buffer, 0, read)
                    }
                } finally {
                    buffer.fill(0)
                }
                output.fd.sync()
            }
        }
    }

    private fun android.database.Cursor.getStringOrNull(index: Int): String? =
        if (index >= 0 && !isNull(index)) getString(index) else null
}
