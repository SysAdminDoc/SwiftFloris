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

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File
import java.io.FileNotFoundException
import java.io.FileInputStream
import java.util.Base64
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/**
 * Trust-boundary tests for [StickerMediaProvider]'s user-sticker proxy path.
 *
 * The last path segment of a user-sticker URI is a Base64-encoded
 * `content://` document URI that comes back from whatever app received the
 * sticker grant — i.e. it is attacker-controlled. The provider must refuse
 * to proxy-open anything that is not a document inside a SAF tree the user
 * actually granted (confused-deputy guard), and must reject with
 * [FileNotFoundException] — never an uncaught crash.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class StickerMediaProviderTest {
    private lateinit var context: Context
    private lateinit var provider: StickerMediaProvider

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        provider = Robolectric.setupContentProvider(
            StickerMediaProvider::class.java,
            StickerMediaProvider.AUTHORITY,
        )
        Robolectric.setupContentProvider(FakeDocumentsBackend::class.java, DOCS_AUTHORITY)
    }

    @Test
    fun documentInsidePersistedGrantIsServed() {
        val treeUri = grantStickerFolder()
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, "$TREE_ID/laugh.png")
        val encoded = encode(docUri.toString())

        // The trust gate itself must accept the document.
        val sticker = UserStickerRepository.stickerForEncodedDocument(context, encoded).shouldNotBeNull()
        sticker.sourceUri shouldBe docUri.toString()
        sticker.mimeType shouldBe "image/png"

        // The provider must get past the gate to the actual open attempt.
        val result = runCatching { provider.openFile(stickerUri(encoded), "r") }
        val thrown = result.exceptionOrNull()
        if (thrown != null) {
            // Only acceptable failure: the open attempt itself (test
            // file-descriptor plumbing) — never the trust gate.
            (thrown is FileNotFoundException) shouldBe true
            (thrown.message ?: "") shouldContain "Cannot open sticker URI"
        } else {
            result.getOrNull().shouldNotBeNull().close()
        }
    }

    @Test
    fun forgedDocumentOutsideAnyPersistedGrantIsRejectedWithoutCrash() {
        grantStickerFolder()
        // Same documents provider, different (non-granted) tree — the shape a
        // recipient app would forge to read documents the IME can reach.
        val forged = DocumentsContract.buildDocumentUriUsingTree(
            DocumentsContract.buildTreeDocumentUri(DOCS_AUTHORITY, "primary:Private"),
            "primary:Private/passport.png",
        )
        val encoded = encode(forged.toString())

        UserStickerRepository.stickerForEncodedDocument(context, encoded).shouldBeNull()

        val thrown = runCatching { provider.openFile(stickerUri(encoded), "r") }.exceptionOrNull()
        (thrown is FileNotFoundException) shouldBe true
        (thrown?.message ?: "") shouldContain "Unknown sticker URI"
    }

    @Test
    fun nonTreeDocumentUrisAreRejectedEvenWithAGrantPresent() {
        grantStickerFolder()
        listOf(
            // Plain (non-tree) document URI — not mintable by the repository.
            "content://com.android.providers.downloads.documents/document/1234",
            // Arbitrary non-document content URI.
            "content://sms/inbox/1",
            // Non-content scheme.
            "file:///data/data/dev.patrickgold.florisboard/databases/secret.db",
        ).forEach { raw ->
            UserStickerRepository.stickerForEncodedDocument(context, encode(raw)).shouldBeNull()
            val thrown = runCatching {
                provider.openFile(stickerUri(encode(raw)), "r")
            }.exceptionOrNull()
            (thrown is FileNotFoundException) shouldBe true
        }
    }

    @Test
    fun malformedBase64SegmentIsRejectedWithoutCrash() {
        grantStickerFolder()
        UserStickerRepository.stickerForEncodedDocument(context, "!!not-base64!!").shouldBeNull()
        val thrown = runCatching {
            provider.openFile(stickerUri("!!not-base64!!"), "r")
        }.exceptionOrNull()
        (thrown is FileNotFoundException) shouldBe true
    }

    @Test
    fun unsupportedMimeTypeInsideGrantIsRejected() {
        val treeUri = grantStickerFolder()
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, "$TREE_ID/manual.pdf")
        val encoded = encode(docUri.toString())
        UserStickerRepository.stickerForEncodedDocument(context, encoded).shouldBeNull()
        val thrown = runCatching { provider.openFile(stickerUri(encoded), "r") }.exceptionOrNull()
        (thrown is FileNotFoundException) shouldBe true
    }

    @Test
    fun localStickerPackFilesAreServedFromAppPrivateStorage() {
        val storageDir = LocalStickerPackRepository.storageDir(context)
        storageDir.deleteRecursively()
        val source = File.createTempFile("local-sticker", ".png", context.cacheDir).apply {
            writeBytes(byteArrayOf(0x21, 0x22, 0x23))
        }
        LocalStickerPackRepository.importStickerFile(
            storageDir = storageDir,
            sourceFile = source,
            displayName = "local-sticker.png",
            declaredMimeType = "image/png",
        ) shouldBe LocalStickerPackResult.Success(1)
        val sticker = LocalStickerPackRepository.loadPack(context).shouldNotBeNull().stickers.single()
        val uri = StickerMediaProvider.uriFor(sticker)

        provider.getType(uri) shouldBe "image/png"
        provider.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)
            .shouldNotBeNull()
            .use { cursor ->
                cursor.moveToFirst() shouldBe true
                cursor.getLong(0) shouldBe 3L
            }
        val bytes = provider.openFile(uri, "r").use { pfd ->
            FileInputStream(pfd.fileDescriptor).use { input -> input.readBytes() }
        }
        bytes.toList() shouldBe listOf(0x21.toByte(), 0x22.toByte(), 0x23.toByte())
    }

    private fun grantStickerFolder(): Uri {
        val treeUri = DocumentsContract.buildTreeDocumentUri(DOCS_AUTHORITY, TREE_ID)
        context.contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        return treeUri
    }

    private fun encode(uri: String): String {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(uri.toByteArray(Charsets.UTF_8))
    }

    private fun stickerUri(encodedDocument: String): Uri {
        return Uri.Builder()
            .scheme("content")
            .authority(StickerMediaProvider.AUTHORITY)
            .appendPath("stickers")
            .appendPath(UserStickerRepository.PackId)
            .appendPath(encodedDocument)
            .build()
    }

    companion object {
        private const val DOCS_AUTHORITY = "com.example.documents"
        private const val TREE_ID = "primary:Stickers"
    }
}

/** Minimal documents backend serving the granted tree in tests. */
class FakeDocumentsBackend : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? = when {
        uri.toString().endsWith(".png") -> "image/png"
        uri.toString().endsWith(".pdf") -> "application/pdf"
        else -> null
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val file = File.createTempFile("granted-sticker", ".png", context!!.cacheDir)
        file.writeBytes(byteArrayOf(0x1, 0x2, 0x3))
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
