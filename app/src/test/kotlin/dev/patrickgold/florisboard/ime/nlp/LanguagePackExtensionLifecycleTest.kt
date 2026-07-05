/*
 * Copyright (C) 2026 SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.nlp

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.ime.nlp.han.HanShapeLanguagePackQuery
import dev.patrickgold.florisboard.lib.ext.ExtensionMaintainer
import dev.patrickgold.florisboard.lib.ext.ExtensionMeta
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class LanguagePackExtensionLifecycleTest {
    private lateinit var context: Context
    private lateinit var workDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        workDir = Files.createTempDirectory("swiftfloris-han-pack").toFile()
        createHanDatabase(workDir, "alpha")
    }

    @After
    fun tearDown() {
        workDir.deleteRecursively()
    }

    @Test
    fun unloadWaitsForActiveHanQueryBeforeClosingDatabase() {
        val extension = languagePackExtension()
        extension.workingDir = workDir
        extension.onAfterLoad(context, workDir)

        val unloadStarted = CountDownLatch(1)
        val unloadFinished = AtomicBoolean(false)
        lateinit var unloadThread: Thread

        val result = extension.withHanShapeBasedSQLiteDatabase { database ->
            unloadThread = thread(start = true, name = "han-unload-test") {
                unloadStarted.countDown()
                extension.onBeforeUnload(context, workDir)
                unloadFinished.set(true)
            }
            unloadStarted.await(1, TimeUnit.SECONDS) shouldBe true
            Thread.sleep(100)

            unloadFinished.get() shouldBe false
            database.isOpen shouldBe true
            HanShapeLanguagePackQuery.words(database, "zhengma") shouldContainExactly listOf("alpha")
            true
        }

        result shouldBe true
        unloadThread.join(2_000)
        unloadFinished.get() shouldBe true
        extension.hanShapeBasedSQLiteDatabase.shouldBeNull()
    }

    @Test
    fun repeatedUnloadLoadCyclesReplaceClosedHandles() {
        val extension = languagePackExtension()
        var previousDatabase: SQLiteDatabase? = null

        repeat(3) { cycle ->
            createHanDatabase(workDir, "word$cycle")
            extension.workingDir = workDir
            extension.onAfterLoad(context, workDir)

            val loadedDatabase = extension.hanShapeBasedSQLiteDatabase.shouldNotBeNull()
            loadedDatabase.isOpen shouldBe true
            extension.withHanShapeBasedSQLiteDatabase { database ->
                HanShapeLanguagePackQuery.words(database, "zhengma")
            } shouldContainExactly listOf("word$cycle")

            previousDatabase?.let { it.isOpen shouldBe false }
            extension.onBeforeUnload(context, workDir)
            loadedDatabase.isOpen shouldBe false
            extension.hanShapeBasedSQLiteDatabase.shouldBeNull()
            previousDatabase = loadedDatabase
        }
    }

    private fun createHanDatabase(dir: File, text: String) {
        val databaseFile = File(dir, "han.sqlite3")
        if (databaseFile.exists()) {
            databaseFile.delete()
        }
        SQLiteDatabase.openOrCreateDatabase(databaseFile.path, null).use { database ->
            database.execSQL("CREATE TABLE zhengma (code TEXT, text TEXT, weight DOUBLE)")
            database.execSQL(
                "INSERT INTO zhengma (code, text, weight) VALUES (?, ?, ?)",
                arrayOf<Any>("a", text, 1.0),
            )
        }
    }

    private fun languagePackExtension(): LanguagePackExtension {
        return LanguagePackExtension(
            meta = ExtensionMeta(
                id = "org.example.languagepack.lifecycle",
                version = "1.0",
                title = "Lifecycle language pack",
                maintainers = listOf(ExtensionMaintainer("SwiftFloris")),
                license = "Apache-2.0",
            ),
            items = listOf(
                LanguagePackComponent(
                    id = "zh-Hans",
                    label = "Chinese",
                    authors = listOf("SwiftFloris"),
                ),
            ),
        )
    }
}
