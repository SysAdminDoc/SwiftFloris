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

package dev.patrickgold.florisboard.lib.cache

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.File
import java.io.InterruptedIOException
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.LockSupport
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/** [CacheManager.importFromUris] keeps nothing when its caller goes away during the copy. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ImportFromUrisTest {
    private lateinit var source: HeldImportSource
    private lateinit var stalling: StallingImportSource
    private lateinit var cache: CacheManager
    private val uri = Uri.parse("content://${HeldImportSource.AUTHORITY}/pack.zip")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        source = Robolectric.setupContentProvider(HeldImportSource::class.java, HeldImportSource.AUTHORITY)
        stalling = Robolectric.setupContentProvider(StallingImportSource::class.java, StallingImportSource.AUTHORITY)
        cache = CacheManager(context)
    }

    @Test
    fun aFinishedCopyIsKeptWithItsSources() {
        source.release()
        val workspace = runBlocking { cache.importFromUris(listOf(uri)) }

        workspace.isOpen() shouldBe true
        workspace.sources shouldBe listOf(uri)
        workspace.inputFileInfos.map { it.file.name } shouldBe listOf("pack.zip")
        cache.importer.getWorkspaceByUuid(workspace.uuid) shouldBe workspace
        workspace.close()
    }

    @Test
    fun aCancelledCopyStopsAtOnceAndLeavesNoWorkspace() = runBlocking<Unit> {
        val copy = async(Dispatchers.Default) { cache.importFromUris(listOf(uri)) }
        source.awaitReached()
        copy.cancel()
        try {
            // The source still holds the copy, so only the cancellation can end it.
            withTimeout(5_000) { copy.join() }
        } finally {
            source.release()
        }

        source.served shouldBe false
        cache.importer.dir.listFiles().orEmpty().toList().shouldBeEmpty()
    }

    @Test
    fun aCancelTheQueryDropsStopsBeforeTheCopy() = runBlocking<Unit> {
        stalling.hold = StallingImportSource.Hold.QUERY
        val copy = async(Dispatchers.Default) { cache.importFromUris(listOf(stalled("one.zip"))) }
        cancelWhileHeld(copy)

        runCatching { copy.await() }.exceptionOrNull().shouldBeInstanceOf<CancellationException>()
        stalling.opened.shouldBeEmpty()
        cache.importer.dir.listFiles().orEmpty().toList().shouldBeEmpty()
    }

    @Test
    fun aCancelTheOpenDropsStopsBeforeTheNextFile() = runBlocking<Unit> {
        stalling.hold = StallingImportSource.Hold.OPEN
        val copy = async(Dispatchers.Default) {
            cache.importFromUris(listOf(stalled("one.zip"), stalled("two.zip")))
        }
        cancelWhileHeld(copy)

        runCatching { copy.await() }.exceptionOrNull().shouldBeInstanceOf<CancellationException>()
        stalling.queried.toList() shouldBe listOf("one.zip")
        cache.importer.dir.listFiles().orEmpty().toList().shouldBeEmpty()
    }

    @Test
    fun aStrayInterruptFailsTheImportInsteadOfCancelling() {
        stalling.interruptedWaitOnType = true
        val error = runBlocking {
            runCatching { cache.importFromUris(listOf(stalled("one.zip"))) }.exceptionOrNull()
        }

        stalling.typeAsked shouldBe true
        error.shouldBeInstanceOf<InterruptedIOException>()
        cache.importer.dir.listFiles().orEmpty().toList().shouldBeEmpty()
    }

    private fun stalled(name: String) = Uri.parse("content://${StallingImportSource.AUTHORITY}/$name")

    /** Cancels [copy] while the source holds it, then lets the source go with the interrupt dropped. */
    private suspend fun cancelWhileHeld(copy: Deferred<*>) {
        try {
            stalling.awaitHeld()
            copy.cancel()
            stalling.awaitDroppedInterrupt()
        } finally {
            stalling.release()
        }
        withTimeout(5_000) { copy.join() }
    }
}

/**
 * Serves small files and can hold the first file's query or open the way a binder call to an app
 * that's still starting does: it waits through the interrupt and drops it.
 */
class StallingImportSource : ContentProvider() {
    enum class Hold { QUERY, OPEN }

    @Volatile
    var hold: Hold? = null

    /**
     * getType throws InterruptedException, as a blocking wait on the import's path does when it
     * meets an interrupt nobody cancelled for (a contended `runBlocking` in `importer.add`, say).
     */
    @Volatile
    var interruptedWaitOnType = false

    val queried: MutableList<String> = Collections.synchronizedList(mutableListOf())
    val opened: MutableList<String> = Collections.synchronizedList(mutableListOf())

    @Volatile
    var typeAsked = false
        private set

    private val held = CountDownLatch(1)
    private val interruptDropped = CountDownLatch(1)

    @Volatile
    private var released = false

    fun awaitHeld() {
        check(held.await(5, TimeUnit.SECONDS)) { "The copy never reached the held call" }
    }

    fun awaitDroppedInterrupt() {
        check(interruptDropped.await(5, TimeUnit.SECONDS)) { "The cancel never interrupted the held call" }
    }

    fun release() {
        released = true
    }

    private fun holdThroughInterrupts() {
        held.countDown()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (!released) {
            check(System.nanoTime() < deadline) { "The test never released the held call" }
            if (Thread.interrupted()) interruptDropped.countDown()
            LockSupport.parkNanos(1_000_000)
        }
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val name = uri.lastPathSegment!!
        queried += name
        if (hold == Hold.QUERY && queried.size == 1) holdThroughInterrupts()
        return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
            addRow(arrayOf<Any>(name, CONTENT.size.toLong()))
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val name = uri.lastPathSegment!!
        opened += name
        if (hold == Hold.OPEN && opened.size == 1) holdThroughInterrupts()
        val file = File(context!!.filesDir, "stalling-$name").apply { writeBytes(CONTENT) }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String? {
        typeAsked = true
        if (interruptedWaitOnType) throw InterruptedException("A wait met a stray interrupt")
        return null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    companion object {
        const val AUTHORITY = "swiftfloris.test.stallingsource"
        private val CONTENT = byteArrayOf(0x4, 0x5, 0x6)
    }
}

/** Serves one small file, holding the first query until [release]. */
class HeldImportSource : ContentProvider() {
    private val reached = CountDownLatch(1)
    private val released = CountDownLatch(1)

    @Volatile
    var served = false
        private set

    fun awaitReached() {
        check(reached.await(5, TimeUnit.SECONDS)) { "The copy never reached the source" }
    }

    fun release() {
        released.countDown()
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        reached.countDown()
        check(released.await(30, TimeUnit.SECONDS)) { "The test never released the copy" }
        return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
            addRow(arrayOf<Any>(uri.lastPathSegment!!, CONTENT.size.toLong()))
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val file = File(context!!.filesDir, "held-source").apply { writeBytes(CONTENT) }
        served = true
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    companion object {
        const val AUTHORITY = "swiftfloris.test.importsource"
        private val CONTENT = byteArrayOf(0x1, 0x2, 0x3)
    }
}
