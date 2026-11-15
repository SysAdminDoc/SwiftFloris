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

package dev.patrickgold.florisboard.ime.clipboard

import android.content.ContentUris
import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardFileInfo
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardFilesDatabase
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardHistoryDao
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardHistoryDatabase
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardMediaProvider
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

private const val FILES_DATABASE = "clipboard_files"

/**
 * Runs clipboard deletion against the real media provider, a real history table and real files,
 * with one store at a time made to fail. Whatever fails must keep its history row so the clip stays
 * visible for a retry, and success must leave no file, file metadata or history row behind.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ClipboardHistoryDeletionTest {
    private lateinit var context: Context
    private lateinit var history: ClipboardHistoryDatabase
    private lateinit var dao: ClipboardHistoryDao
    private lateinit var files: ClipboardFilesDatabase
    private lateinit var mediaDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // The app's start-up reconciliation deletes media its own history doesn't hold. Let it
        // finish first, or it can take the files planted below while a test runs. Bounded, so a
        // start-up that never gets there fails the test instead of hanging it.
        runBlocking { withTimeout(10_000) { context.clipboardManager().value.snapshotHistoryForRestore() } }
        Robolectric.setupContentProvider(ClipboardMediaProvider::class.java, ClipboardMediaProvider.AUTHORITY)
        history = Room.inMemoryDatabaseBuilder(context, ClipboardHistoryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = history.clipboardItemDao()
        files = Room.databaseBuilder(context, ClipboardFilesDatabase::class.java, FILES_DATABASE)
            .allowMainThreadQueries()
            .build()
        mediaDir = File(context.noBackupFilesDir, "clipboard_files")
    }

    @After
    fun tearDown() {
        history.close()
        files.close()
        mediaDir.deleteRecursively()
        tempDir().deleteRecursively()
        context.deleteDatabase(FILES_DATABASE)
    }

    @Test
    fun deletingOneImageRemovesItsFileMetadataAndRow() {
        val image = storedImage(9001)

        val result = ClipboardHistoryDeletion.deleteOne(context, dao, image, onlyIfUnpinned = false)

        result.succeeded shouldBe true
        assertNothingLeftOf(image)
    }

    @Test
    fun anImageFileThatWontDeleteKeepsTheClipForARetry() {
        val image = storedImage(9002)
        jamFile(9002)

        val failed = ClipboardHistoryDeletion.deleteOne(context, dao, image, onlyIfUnpinned = false)

        failed.succeeded shouldBe false
        failed.retained.map { it.id } shouldBe listOf(image.id)
        dao.getById(image.id).shouldNotBeNull()
        files.clipboardFilesDao().getById(9002).shouldNotBeNull()

        unjamFile(9002)
        val retried = ClipboardHistoryDeletion.deleteExact(context, dao, failed.retained, onlyIfUnpinned = false)

        retried.succeeded shouldBe true
        assertNothingLeftOf(image)
    }

    @Test
    fun aMetadataRowThatWontDeleteKeepsTheClipForARetry() {
        val image = storedImage(9003)
        execOnFiles("CREATE TRIGGER refuse_delete BEFORE DELETE ON $FILES_DATABASE BEGIN SELECT RAISE(ABORT, 'injected'); END")

        val failed = ClipboardHistoryDeletion.deleteOne(context, dao, image, onlyIfUnpinned = false)

        failed.succeeded shouldBe false
        failed.retained.map { it.id } shouldBe listOf(image.id)
        dao.getById(image.id).shouldNotBeNull()
        files.clipboardFilesDao().getById(9003).shouldNotBeNull()

        execOnFiles("DROP TRIGGER refuse_delete")
        val retried = ClipboardHistoryDeletion.deleteExact(context, dao, failed.retained, onlyIfUnpinned = false)

        retried.succeeded shouldBe true
        assertNothingLeftOf(image)
    }

    @Test
    fun aFilteredClearWhoseRowsWontDeleteKeepsEveryClip() {
        val text = storedText("first")
        val other = storedText("second")
        val image = storedImage(9004)
        val refusing = RefusingRowDeletes(dao)

        val result = ClipboardHistoryDeletion.deleteExact(context, refusing, listOf(text, other, image), onlyIfUnpinned = false)

        result.succeeded shouldBe false
        result.retained.map { it.id } shouldContainExactlyInAnyOrder listOf(text.id, other.id, image.id)
        dao.getAll().map { it.id } shouldContainExactlyInAnyOrder listOf(text.id, other.id, image.id)
    }

    @Test
    fun clearingUnpinnedKeepsPinnedClipsAndAnyClipWhoseFileStayed() {
        val pinned = storedText("pinned", isPinned = true)
        val unpinned = storedText("unpinned")
        val image = storedImage(9005)
        jamFile(9005)

        val result = ClipboardHistoryDeletion.deleteUnpinned(context, dao)

        result.succeeded shouldBe false
        result.retained.map { it.id } shouldBe listOf(image.id)
        dao.getAll().map { it.id } shouldContainExactlyInAnyOrder listOf(pinned.id, image.id)
        dao.getById(unpinned.id).shouldBeNull()
    }

    @Test
    fun clearingFullHistoryRemovesPinnedClipsAndMediaToo() {
        storedText("pinned", isPinned = true)
        storedText("unpinned")
        val image = storedImage(9006)

        val result = ClipboardHistoryDeletion.deleteAll(context, dao)

        result.succeeded shouldBe true
        dao.getAll().shouldBeEmpty()
        assertNothingLeftOf(image)
    }

    @Test
    fun clearingFullHistoryWithAFileThatWontDeleteReportsIt() {
        storedText("text")
        val image = storedImage(9007)
        jamFile(9007)

        val result = ClipboardHistoryDeletion.deleteAll(context, dao)

        result.succeeded shouldBe false
        result.retained.map { it.id } shouldBe listOf(image.id)
        dao.getAll().map { it.id } shouldBe listOf(image.id)
    }

    @Test
    fun noHistoryStoreReportsThatNothingWasConfirmed() {
        val text = ClipboardItem.text("never stored")

        ClipboardHistoryDeletion.deleteAll(context, dao = null).succeeded shouldBe false
        ClipboardHistoryDeletion.deleteUnpinned(context, dao = null).historyReachable shouldBe false
        val exact = ClipboardHistoryDeletion.deleteExact(context, dao = null, listOf(text), onlyIfUnpinned = false)
        exact.succeeded shouldBe false
        exact.retained shouldBe listOf(text)
    }

    @Test
    fun clearingAPinnedPrimaryClipLeavesIt() {
        val image = storedImage(9008, isPinned = true)

        val result = ClipboardHistoryDeletion.deleteOne(context, dao, image, onlyIfUnpinned = true)

        result.succeeded shouldBe true
        dao.getById(image.id).shouldNotBeNull()
        File(mediaDir, "9008").exists() shouldBe true
    }

    @Test
    fun aFilteredClearLeavesAClipPinnedAfterTheListWasTaken() {
        val text = storedText("first")
        val image = storedImage(9009)
        dao.update(image.copy(isPinned = true))

        val result = ClipboardHistoryDeletion.deleteExact(context, dao, listOf(text, image), onlyIfUnpinned = true)

        result.succeeded shouldBe true
        dao.getById(text.id).shouldBeNull()
        dao.getById(image.id).shouldNotBeNull()
        File(mediaDir, "9009").exists() shouldBe true
    }

    @Test
    fun clearingAPrimaryClipWhoseIdIsStaleFindsItsRowByWhatItHolds() {
        val keep = storedText("something else")
        val text = storedText("copied once")
        val image = storedImage(9010)
        // The primary clip before its row was written: id 0, same content.
        val primaryText = ClipboardItem.text("copied once")
        val primaryImage = image.copy(id = 0)

        ClipboardHistoryDeletion.deleteOne(context, dao, primaryText, onlyIfUnpinned = true).succeeded shouldBe true
        ClipboardHistoryDeletion.deleteOne(context, dao, primaryImage, onlyIfUnpinned = true).succeeded shouldBe true

        dao.getById(text.id).shouldBeNull()
        assertNothingLeftOf(image)
        dao.getById(keep.id).shouldNotBeNull()
    }

    @Test
    fun clearingAPrimaryClipMatchesWhatItHoldsNotItsId() {
        val marked = storedText("copied once")
        // Marked sensitive in history after it was copied; the primary clip still says it isn't.
        dao.update(marked.copy(isSensitive = true))
        val unrelated = storedText("restored from a backup")
        // The primary clip carries an id that a restore since gave to another clip.
        val primary = ClipboardItem.text("copied once").also { it.id = unrelated.id }

        ClipboardHistoryDeletion.deleteOne(context, dao, primary, onlyIfUnpinned = true).succeeded shouldBe true

        dao.getById(marked.id).shouldBeNull()
        dao.getById(unrelated.id).shouldNotBeNull()
    }

    @Test
    fun aFilteredClearWithAFileThatWontDeleteKeepsThatClip() {
        val text = storedText("text")
        val image = storedImage(9012)
        jamFile(9012)

        val result = ClipboardHistoryDeletion.deleteExact(context, dao, listOf(text, image), onlyIfUnpinned = true)

        result.succeeded shouldBe false
        result.retained.map { it.id } shouldBe listOf(image.id)
        dao.getAll().map { it.id } shouldBe listOf(image.id)
    }

    @Test
    fun clearingFullHistoryWhoseRowsWontDeleteKeepsEveryClip() {
        val text = storedText("text")
        val image = storedImage(9013)

        val result = ClipboardHistoryDeletion.deleteAll(context, RefusingRowDeletes(dao))

        result.succeeded shouldBe false
        result.retained.map { it.id } shouldContainExactlyInAnyOrder listOf(text.id, image.id)
        dao.getAll().map { it.id } shouldContainExactlyInAnyOrder listOf(text.id, image.id)
    }

    @Test
    fun aHistoryThatCantBeReadReportsNothingDeleted() {
        val text = storedText("text")
        val refusing = RefusingReads(dao)

        ClipboardHistoryDeletion.deleteAll(context, refusing).succeeded shouldBe false
        ClipboardHistoryDeletion.deleteOne(context, refusing, text, onlyIfUnpinned = true).succeeded shouldBe false
        val exact = ClipboardHistoryDeletion.deleteExact(context, refusing, listOf(text), onlyIfUnpinned = false)
        exact.succeeded shouldBe false
        exact.retained shouldBe listOf(text)
        dao.getById(text.id).shouldNotBeNull()
    }

    @Test
    fun aClipWhoseRowIsAlreadyGoneCountsAsDeleted() {
        val text = storedText("gone already")
        dao.delete(text.id)

        ClipboardHistoryDeletion.deleteExact(context, dao, listOf(text), onlyIfUnpinned = false).succeeded shouldBe true
        ClipboardHistoryDeletion.deleteOne(context, dao, text, onlyIfUnpinned = false).succeeded shouldBe true
    }

    @Test
    fun aTempFolderThatCantBeListedKeepsTheClipForARetry() {
        val image = storedImage(9011)
        // A file where the decrypted-copy folder goes: it exists but can't be listed.
        val blocked = File(context.cacheDir, "clipboard_media")
        blocked.deleteRecursively()
        blocked.writeText("blocked")
        try {
            val failed = ClipboardHistoryDeletion.deleteOne(context, dao, image, onlyIfUnpinned = false)

            failed.succeeded shouldBe false
            dao.getById(image.id).shouldNotBeNull()
        } finally {
            blocked.delete()
        }

        ClipboardHistoryDeletion.deleteExact(context, dao, listOf(image), onlyIfUnpinned = false).succeeded shouldBe true
        assertNothingLeftOf(image)
    }

    private fun storedText(text: String, isPinned: Boolean = false): ClipboardItem {
        val item = ClipboardItem.text(text).copy(isPinned = isPinned)
        item.id = dao.insert(item)
        return item
    }

    private fun storedImage(fileId: Long, isPinned: Boolean = false): ClipboardItem {
        mediaDir.mkdirs()
        // Starts with the encrypted-file header, so the provider's start-up migration leaves it be.
        File(mediaDir, fileId.toString()).writeBytes("SFCLIPM1".toByteArray() + ByteArray(64) { 7 })
        files.clipboardFilesDao().insert(ClipboardFileInfo(fileId, "photo.png", 64, 0, listOf("image/png")))
        // The decrypted copy handed to the last app that pasted it.
        File(tempDir().apply { mkdirs() }, "$fileId-1.plain").writeText("plain")
        val item = ClipboardItem(
            type = ItemType.IMAGE,
            text = null,
            uri = ContentUris.withAppendedId(ClipboardMediaProvider.IMAGE_CLIPS_URI, fileId),
            creationTimestampMs = System.currentTimeMillis(),
            isPinned = isPinned,
            mimeTypes = listOf("image/png"),
        )
        item.id = dao.insert(item)
        return item
    }

    /** A non-empty directory where the file was: File.delete() refuses it, like a file in use. */
    private fun jamFile(fileId: Long) {
        val jammed = File(mediaDir, fileId.toString())
        jammed.delete()
        File(jammed, "held").apply { parentFile!!.mkdirs() }.writeText("held")
    }

    private fun unjamFile(fileId: Long) {
        File(mediaDir, fileId.toString()).deleteRecursively()
    }

    private fun execOnFiles(sql: String) {
        files.openHelper.writableDatabase.execSQL(sql)
    }

    private fun assertNothingLeftOf(item: ClipboardItem) {
        dao.getById(item.id).shouldBeNull()
        val fileId = ContentUris.parseId(item.uri!!)
        File(mediaDir, fileId.toString()).exists() shouldBe false
        files.clipboardFilesDao().getById(fileId).shouldBeNull()
        tempDir().listFiles().orEmpty().filter { it.name.startsWith("$fileId-") }.shouldBeEmpty()
    }

    private fun tempDir() = File(context.cacheDir, "clipboard_media")
}

/** The real history table, except that reading it fails the way a locked or corrupt database does. */
private class RefusingReads(real: ClipboardHistoryDao) : ClipboardHistoryDao by real {
    override fun getAll(): List<ClipboardItem> {
        throw SQLiteException("injected")
    }
}

/** The real history table, except that deleting rows fails the way a locked or full database does. */
private class RefusingRowDeletes(real: ClipboardHistoryDao) : ClipboardHistoryDao by real {
    override fun delete(items: List<ClipboardItem>) {
        throw SQLiteException("injected")
    }
}
