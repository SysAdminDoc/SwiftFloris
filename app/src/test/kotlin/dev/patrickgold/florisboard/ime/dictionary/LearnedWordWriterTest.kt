/*
 * Copyright (C) 2026 SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.dictionary

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.lib.FlorisLocale
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class LearnedWordWriterTest {
    private lateinit var database: FlorisUserDictionaryDatabase
    private val english = FlorisLocale.fromTag("en-US")
    private var storeOpen = true

    private fun writer(maxWaiting: Int = 500) = LearnedWordWriter(
        dao = { if (storeOpen) database.userDictionaryDao() else null },
        initialFrequency = 245,
        increment = 1,
        maxFrequency = 255,
        maxWaiting = maxWaiting,
    )

    private fun frequencyOf(word: String): Int? =
        database.userDictionaryDao().query(word, english).firstOrNull { it.word == word }?.freq

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            FlorisUserDictionaryDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun eachUseRaisesTheWordOneStepUpToTheCap() {
        val writer = writer()

        writer.write("zorbit", english) shouldBe true
        frequencyOf("zorbit") shouldBe 245
        repeat(3) { writer.write("zorbit", english) }
        frequencyOf("zorbit") shouldBe 248
        repeat(20) { writer.write("zorbit", english) }
        frequencyOf("zorbit") shouldBe 255
    }

    @Test
    fun wordsLearnedWhileTheStoreIsClosedAreWrittenWhenItOpens() {
        val writer = writer()
        storeOpen = false

        writer.write("wegovy", english) shouldBe false
        writer.write("wegovy", english) shouldBe false
        writer.write("bupropion", english) shouldBe false
        writer.waitingSize shouldBe 3
        writer.retry() shouldBe 0
        writer.waitingSize shouldBe 3

        storeOpen = true
        writer.retry() shouldBe 0

        writer.waitingSize shouldBe 0
        frequencyOf("wegovy") shouldBe 246
        frequencyOf("bupropion") shouldBe 245
    }

    @Test
    fun theNextWriteAlsoWritesWhatWaited() {
        val writer = writer()
        storeOpen = false
        writer.write("wegovy", english)

        storeOpen = true
        writer.write("zorbit", english) shouldBe true

        frequencyOf("wegovy") shouldBe 245
        frequencyOf("zorbit") shouldBe 245
        writer.waitingSize shouldBe 0
    }

    @Test
    fun onlyTheNewestUsesWaitPastTheLimit() {
        val writer = writer(maxWaiting = 2)
        storeOpen = false
        writer.write("first", english)
        writer.write("second", english)
        writer.write("third", english)
        writer.waitingSize shouldBe 2

        storeOpen = true
        writer.retry()

        frequencyOf("first") shouldBe null
        frequencyOf("second") shouldBe 245
        frequencyOf("third") shouldBe 245
    }
}
