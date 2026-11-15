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
import dev.patrickgold.florisboard.lib.devtools.flogInfo
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

/** The folder operations publishing needs, apart from SAF so the steps can be tested. */
internal interface BackupFolder<D> {
    fun create(name: String): D
    fun write(document: D, archive: File)
    fun open(document: D): InputStream?
    /** The renamed document, or null when the provider can't rename. */
    fun rename(document: D, name: String): D?
    fun delete(document: D)
}

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
        receipt: BackupReceipt,
    ): PublishedScheduledBackup {
        require(archive.isFile) { "Scheduled backup archive is missing." }
        require(archive.length() <= PortableBackupEnvelope.MaxEnvelopeBytes) {
            "Scheduled backup archive exceeds the supported size limit."
        }
        val resolver = context.contentResolver
        val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocumentId)
        val folder = object : BackupFolder<Uri> {
            override fun create(name: String): Uri =
                DocumentsContract.createDocument(resolver, parentUri, ArchiveMimeType, name)
                    ?: throw FileNotFoundException("Could not create a temporary SAF backup document.")

            override fun write(document: Uri, archive: File) = writeArchive(resolver, document, archive)

            override fun open(document: Uri): InputStream? = resolver.openInputStream(document)

            override fun rename(document: Uri, name: String): Uri? =
                DocumentsContract.renameDocument(resolver, document, name)

            override fun delete(document: Uri) {
                DocumentsContract.deleteDocument(resolver, document)
            }
        }
        return PublishedScheduledBackup(publishTo(folder, archive, finalName, receipt), finalName)
    }

    /**
     * Writes [archive] under a hidden temporary name, reads it back, renames it to [finalName]
     * and reads it back again. A failed step deletes what it wrote, so only a checked archive
     * ever carries a backup's name.
     */
    internal fun <D> publishTo(
        folder: BackupFolder<D>,
        archive: File,
        finalName: String,
        receipt: BackupReceipt,
    ): D {
        val temporaryName = ".${finalName.removeSuffix(ScheduledBackupPolicy.ArchiveExtension)}" +
            "_${UUID.randomUUID()}$TemporaryNameSuffix"
        val temporary = folder.create(temporaryName)

        var published = false
        try {
            folder.write(temporary, archive)
            BackupPublicationVerifier.verifyReadback(receipt) { folder.open(temporary) }
            val renamed = folder.rename(temporary, finalName)
                ?: throw IllegalStateException("The selected SAF provider cannot atomically rename backups.")
            // A rename can hand back a different document, so the copy under the name restore
            // will see is read back too. One that doesn't match is removed, not left to look
            // like a good backup.
            try {
                BackupPublicationVerifier.verifyReadback(receipt) { folder.open(renamed) }
            } catch (error: Throwable) {
                runCatching { folder.delete(renamed) }
                throw error
            }
            published = true
            return renamed
        } finally {
            if (!published) {
                runCatching { folder.delete(temporary) }
            }
        }
    }

    /**
     * Deletes the archives past the newest [retentionCount] that open with [passphrase].
     * [checkedKeys] are archives already known to open (this run's included), so they aren't
     * decrypted again. Returns the keys known to open that are still in the folder, for the
     * next run.
     */
    suspend fun pruneVerified(
        context: Context,
        treeUri: Uri,
        retentionCount: Int,
        passphrase: CharArray,
        checkedKeys: Set<String>,
    ): Set<String> {
        val newestFirst = listDocuments(context, treeUri)
            .mapNotNull { document ->
                ScheduledBackupPolicy.timestampFromArchiveName(document.name)?.let { timestamp ->
                    timestamp to document
                }
            }
            .sortedByDescending { it.first }
            .map { it.second }
        val keep = ScheduledBackupPolicy.normalizeRetention(retentionCount)
        val plan = rememberingRetention(newestFirst, keep, checkedKeys, { checkedKey(it.name, it.size) }) { document ->
            isRestorableArchive(context, document, passphrase)
        }
        plan.deletions.forEach { document ->
            DocumentsContract.deleteDocument(context.contentResolver, document.uri)
        }
        return plan.checkedKeys
    }

    /**
     * How a checked archive is remembered: its name and size, so a file replaced under the same
     * name with different contents is opened again. Null when the folder doesn't report a size,
     * since nothing then tells a replaced file apart, so it's opened on every run.
     */
    internal fun checkedKey(name: String, size: Long): String? = if (size >= 0L) "$name/$size" else null

    internal class RetentionPlan<D>(val deletions: List<D>, val checkedKeys: Set<String>)

    /**
     * [retentionDeletions] that only calls [isRestorable] for archives whose key isn't in
     * [checkedKeys], and remembers the ones it accepts. An archive without a key is always
     * checked. Nothing is checked while the folder holds no more than [keep] archives, since
     * nothing would be deleted. The returned keys are the checked ones still in the folder once
     * the deletions are done.
     */
    internal inline fun <D> rememberingRetention(
        newestFirst: List<D>,
        keep: Int,
        checkedKeys: Set<String>,
        keyOf: (D) -> String?,
        isRestorable: (D) -> Boolean,
    ): RetentionPlan<D> {
        val checked = checkedKeys.toMutableSet()
        val deletions = if (newestFirst.size <= keep) {
            emptyList()
        } else {
            retentionDeletions(newestFirst, keep) { document ->
                val key = keyOf(document)
                (key != null && key in checked) ||
                    isRestorable(document).also { if (it && key != null) checked += key }
            }
        }
        val remaining = newestFirst.mapNotNull(keyOf).toSet() - deletions.mapNotNull(keyOf).toSet()
        return RetentionPlan(deletions, checked.intersect(remaining))
    }

    /**
     * The archives to delete from [newestFirst]: every one [isRestorable] accepts after the
     * newest [keep] it accepts. The ones it turns down are never deleted and don't count toward
     * [keep], so a damaged or foreign file can't push a good backup out.
     */
    internal inline fun <D> retentionDeletions(
        newestFirst: List<D>,
        keep: Int,
        isRestorable: (D) -> Boolean,
    ): List<D> {
        var kept = 0
        return newestFirst.filter { document ->
            when {
                !isRestorable(document) -> false
                kept < keep -> {
                    kept++
                    false
                }
                else -> true
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

    /** Whether [document] is an encrypted archive [passphrase] opens and restore would accept. */
    private suspend fun isRestorableArchive(
        context: Context,
        document: ScheduledBackupDocument,
        passphrase: CharArray,
    ): Boolean {
        if (document.mimeType == Document.MIME_TYPE_DIR) return false
        if (document.size > PortableBackupEnvelope.MaxEnvelopeBytes) return false
        val root = File(context.cacheDir, "scheduled-backup-verification/${UUID.randomUUID()}")
        val source = File(root, "archive.sfbak")
        return try {
            check(root.mkdirs()) { "Could not create the private backup verification directory." }
            copyDocumentToFile(context, document.uri, source)
            BackupPublicationVerifier.inspect(source, passphrase, root).encrypted
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            // Any failure means it isn't provably restorable, so it neither counts nor gets pruned.
            flogInfo { "Retention skipped ${document.name}: ${error::class.java.simpleName}" }
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
