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

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PersonalNgramFlushIsolationTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun bigramFlushPersistsEachLocaleAndResetRemovesBothFiles() = runTest {
        val store = PersonalBigramStore.forTesting(context)
        val english = FlorisLocale.fromTag("en")
        val german = FlorisLocale.fromTag("de")
        store.resetAndAwait()

        repeat(20) { store.learnAndAwait("swift", "floris", english) }
        repeat(20) { store.learnAndAwait("swift", "tastatur", german) }

        val rows = store.snapshot()
        rows.map { it.localeTag to "${it.prev} ${it.next}" } shouldContain
            (english.languageTag() to "swift floris")
        rows.map { it.localeTag to "${it.prev} ${it.next}" } shouldContain
            (german.languageTag() to "swift tastatur")
        File(context.filesDir, "personal_bigrams_${english.languageTag()}.tsv").isFile shouldBe true
        File(context.filesDir, "personal_bigrams_${german.languageTag()}.tsv").isFile shouldBe true

        store.resetAndAwait()

        File(context.filesDir, "personal_bigrams_${english.languageTag()}.tsv").exists() shouldBe false
        File(context.filesDir, "personal_bigrams_${german.languageTag()}.tsv").exists() shouldBe false
        store.snapshot() shouldBe emptyList()
    }

    @Test
    fun trigramFlushPersistsEachLocaleAndResetRemovesBothFiles() = runTest {
        val store = PersonalTrigramStore.forTesting(context)
        val english = FlorisLocale.fromTag("en")
        val german = FlorisLocale.fromTag("de")
        store.resetAndAwait()

        repeat(20) { store.learnAndAwait("swift", "floris", "keyboard", english) }
        repeat(20) { store.learnAndAwait("swift", "floris", "tastatur", german) }

        val rows = store.snapshot()
        rows.map { it.localeTag to "${it.prev2} ${it.prev1} ${it.next}" } shouldContain
            (english.languageTag() to "swift floris keyboard")
        rows.map { it.localeTag to "${it.prev2} ${it.prev1} ${it.next}" } shouldContain
            (german.languageTag() to "swift floris tastatur")
        File(context.filesDir, "personal_trigrams_${english.languageTag()}.tsv").isFile shouldBe true
        File(context.filesDir, "personal_trigrams_${german.languageTag()}.tsv").isFile shouldBe true

        store.resetAndAwait()

        File(context.filesDir, "personal_trigrams_${english.languageTag()}.tsv").exists() shouldBe false
        File(context.filesDir, "personal_trigrams_${german.languageTag()}.tsv").exists() shouldBe false
        store.snapshot() shouldBe emptyList()
    }
}
