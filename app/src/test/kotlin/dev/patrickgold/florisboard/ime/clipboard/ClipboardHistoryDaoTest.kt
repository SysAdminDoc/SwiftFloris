/*
 * Copyright (C) 2026 SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.clipboard

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardHistoryDatabase
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardMediaProvider
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ClipboardHistoryDaoTest {
    private lateinit var database: ClipboardHistoryDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ClipboardHistoryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun deleteIfUnpinnedReportsNoDeletionForPinnedRows() {
        val dao = database.clipboardItemDao()
        val pinned = mediaItem(isPinned = true)
        val id = dao.insert(pinned)

        dao.deleteIfUnpinned(id) shouldBe 0

        dao.getAll().map { it.id }.shouldContainExactly(id)
    }

    @Test
    fun deleteIfUnpinnedReportsDeletionForUnpinnedRows() {
        val dao = database.clipboardItemDao()
        val unpinned = mediaItem(isPinned = false)
        val id = dao.insert(unpinned)

        dao.deleteIfUnpinned(id) shouldBe 1

        dao.getAll() shouldBe emptyList()
    }

    @Test
    fun replaceAllForRestoreCommitsTheRecoverySnapshotWithStableIds() {
        val dao = database.clipboardItemDao()
        dao.insert(mediaItem(isPinned = false).copy(id = 41L))
        val recoveryItem = mediaItem(isPinned = true).copy(
            id = 77L,
            creationTimestampMs = 2_000L,
        )

        dao.replaceAllForRestore(listOf(recoveryItem))

        dao.getAll().shouldContainExactly(recoveryItem)
    }

    private fun mediaItem(isPinned: Boolean): ClipboardItem {
        return ClipboardItem(
            type = ItemType.IMAGE,
            text = null,
            uri = Uri.parse("content://${ClipboardMediaProvider.AUTHORITY}/clips/images/1"),
            creationTimestampMs = 1_000L,
            isPinned = isPinned,
            mimeTypes = listOf("image/png"),
        )
    }
}
