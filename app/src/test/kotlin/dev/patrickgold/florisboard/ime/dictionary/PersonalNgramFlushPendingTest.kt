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

package dev.patrickgold.florisboard.ime.dictionary

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.lib.FlorisLocale
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * The keyboard saves learning that is still under the 20-commit batch when it
 * hides, because a process killed off screen (Android 17's memory limiter)
 * never gets another chance. A second store instance stands in for the
 * restarted process: it starts empty and can only know what reached disk.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PersonalNgramFlushPendingTest {
    private lateinit var context: Context
    private val english = FlorisLocale.fromTag("en")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun bigramsUnderTheBatchSurviveARestart() = runTest {
        val store = PersonalBigramStore.forTesting(context)
        store.resetAndAwait()
        repeat(3) { store.learnAndAwait("swift", "floris", english) }
        val file = File(context.filesDir, "personal_bigrams_${english.languageTag()}.tsv")
        file.exists() shouldBe false

        store.flushPendingAndAwait() shouldBe listOf(english.languageTag())

        PersonalBigramStore.forTesting(context).predict("swift", english, 3) shouldContain "floris"
        store.flushPendingAndAwait() shouldBe emptyList()
        store.resetAndAwait()
    }

    @Test
    fun trigramsUnderTheBatchSurviveARestart() = runTest {
        val store = PersonalTrigramStore.forTesting(context)
        store.resetAndAwait()
        repeat(3) { store.learnAndAwait("swift", "floris", "keyboard", english) }
        val file = File(context.filesDir, "personal_trigrams_${english.languageTag()}.tsv")
        file.exists() shouldBe false

        store.flushPendingAndAwait() shouldBe listOf(english.languageTag())

        PersonalTrigramStore.forTesting(context)
            .predict("swift", "floris", english, 3) shouldContain "keyboard"
        store.flushPendingAndAwait() shouldBe emptyList()
        store.resetAndAwait()
    }

    /**
     * learn() returns before its coroutine has loaded the table and counted the pair, and the
     * keyboard can hide in that gap. The flush has to wait for it, or the word stays in memory.
     * Repeated because a flush that doesn't wait only loses the race some of the time.
     */
    @Test
    fun aBigramStillBeingLearnedWhenTheKeyboardHidesIsSaved() = runTest {
        repeat(20) {
            val store = PersonalBigramStore.forTesting(context)
            store.resetAndAwait()

            store.learn("swift", "floris", english)
            store.flushPending()
            store.awaitIdleForTesting()

            savedBigrams() shouldBe listOf("swift floris 1")
            store.resetAndAwait()
        }
    }

    @Test
    fun aTrigramStillBeingLearnedIsSavedByFlushPendingAndAwait() = runTest {
        repeat(20) {
            val store = PersonalTrigramStore.forTesting(context)
            store.resetAndAwait()

            store.learn("swift", "floris", "keyboard", english)

            store.flushPendingAndAwait() shouldBe listOf(english.languageTag())
            savedTrigrams() shouldBe listOf("swift floris keyboard 1")
            store.resetAndAwait()
        }
    }

    @Test
    fun aFailedBigramWriteIsTriedAgainOnTheNextFlush() = runTest {
        val store = PersonalBigramStore.forTesting(context)
        store.resetAndAwait()
        store.learnAndAwait("swift", "floris", english)
        val file = File(context.filesDir, "personal_bigrams_${english.languageTag()}.tsv")
        // A non-empty directory where the table goes makes the write fail.
        File(file, "blocker").apply { parentFile!!.mkdirs(); writeText("x") }

        store.flushPendingAndAwait() shouldBe emptyList()
        file.deleteRecursively() shouldBe true

        store.flushPendingAndAwait() shouldBe listOf(english.languageTag())
        savedBigrams() shouldBe listOf("swift floris 1")
        store.resetAndAwait()
    }

    @Test
    fun aFailedTrigramWriteIsTriedAgainOnTheNextFlush() = runTest {
        val store = PersonalTrigramStore.forTesting(context)
        store.resetAndAwait()
        store.learnAndAwait("swift", "floris", "keyboard", english)
        val file = File(context.filesDir, "personal_trigrams_${english.languageTag()}.tsv")
        File(file, "blocker").apply { parentFile!!.mkdirs(); writeText("x") }

        store.flushPendingAndAwait() shouldBe emptyList()
        file.deleteRecursively() shouldBe true

        store.flushPendingAndAwait() shouldBe listOf(english.languageTag())
        savedTrigrams() shouldBe listOf("swift floris keyboard 1")
        store.resetAndAwait()
    }

    /** What a restarted process reads back. predict() needs a pair seen twice, so it can't check one learn. */
    private suspend fun savedBigrams(): List<String> =
        PersonalBigramStore.forTesting(context).snapshot().map { "${it.prev} ${it.next} ${it.count}" }

    private suspend fun savedTrigrams(): List<String> =
        PersonalTrigramStore.forTesting(context).snapshot().map { "${it.prev2} ${it.prev1} ${it.next} ${it.count}" }

    @Test
    fun nothingPendingWritesNothing() = runTest {
        val store = PersonalBigramStore.forTesting(context)
        store.resetAndAwait()

        store.flushPendingAndAwait() shouldBe emptyList()

        File(context.filesDir, "personal_bigrams_${english.languageTag()}.tsv").exists() shouldBe false
    }
}
