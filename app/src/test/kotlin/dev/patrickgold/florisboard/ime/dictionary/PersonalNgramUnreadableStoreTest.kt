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
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * What happens when the backing TSV will not parse.
 *
 * A store that cannot read itself is a degraded personal dictionary, not a
 * reason to stop the keyboard. `learn` is fire-and-forget, so nothing up the
 * stack can catch what its coroutine throws, and an unhandled exception in a
 * launched coroutine reaches the thread's default handler regardless of
 * `SupervisorJob`. A half-written file therefore used to take the process down
 * on the first word it tried to learn.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PersonalNgramUnreadableStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun corruptBigramFile(localeTag: String): File {
        val file = File(context.filesDir, "personal_bigrams_$localeTag.tsv")
        file.parentFile?.mkdirs()
        // A count column that is not a number, which is what a torn write or a
        // truncated flush leaves behind.
        file.writeText("swift\tfloris\tnot-a-number\tnope\n")
        return file
    }

    @Test
    fun learningAgainstAnUnreadableStoreDoesNotBringDownTheCaller() = runTest {
        val store = PersonalBigramStore.forTesting(context)
        store.resetAndAwait()
        val locale = FlorisLocale.fromTag("en")
        val corrupt = corruptBigramFile(locale.languageTag())

        // The fire-and-forget path. If this propagated, it would arrive on the
        // default uncaught handler rather than here.
        repeat(5) { store.learn("swift", "floris", locale) }

        assertTrue(corrupt.isFile, "a load failure must not delete the user's file")
    }

    @Test
    fun theSuspendingVariantStillTellsItsCaller() = runTest {
        // Degrading quietly is right for the fire-and-forget path only. A caller
        // that asked to be awaited has somewhere to put the failure.
        val store = PersonalBigramStore.forTesting(context)
        store.resetAndAwait()
        val locale = FlorisLocale.fromTag("en")
        corruptBigramFile(locale.languageTag())

        assertFailsWith<PersonalNgramPersistence.LoadException> {
            store.learnAndAwait("swift", "floris", locale)
        }
    }

    @Test
    fun aFailedLoadIsNotRetriedFromDiskOnEveryWord() = runTest {
        // Nothing rewrites the file in between, so the second attempt fails the
        // same way. Re-reading it per learned word put an unparseable file on
        // the typing path.
        val store = PersonalBigramStore.forTesting(context)
        store.resetAndAwait()
        val locale = FlorisLocale.fromTag("en")
        val corrupt = corruptBigramFile(locale.languageTag())

        assertFailsWith<PersonalNgramPersistence.LoadException> {
            store.learnAndAwait("swift", "floris", locale)
        }

        // Make the file unreadable in a way that would throw differently if it
        // were opened again; the short-circuit means it never is.
        assertTrue(corrupt.delete(), "fixture teardown should be able to remove the file")

        assertFailsWith<PersonalNgramPersistence.LoadException> {
            store.learnAndAwait("swift", "floris", locale)
        }
    }

    @Test
    fun anUnreadableStoreLeavesTheKeyboardAbleToKeepLearningOtherLocales() = runTest {
        val store = PersonalBigramStore.forTesting(context)
        store.resetAndAwait()
        val broken = FlorisLocale.fromTag("en")
        val healthy = FlorisLocale.fromTag("de")
        corruptBigramFile(broken.languageTag())

        repeat(3) { store.learn("swift", "floris", broken) }
        repeat(20) { store.learnAndAwait("swift", "tastatur", healthy) }

        val rows = store.snapshot()
        assertTrue(
            rows.any { it.localeTag == healthy.languageTag() && it.prev == "swift" },
            "one unreadable locale must not stop the others learning",
        )
    }
}
