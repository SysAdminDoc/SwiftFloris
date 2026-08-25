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

package dev.patrickgold.florisboard.ime.input

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The signal that tells a running keyboard its keypress sounds changed.
 *
 * `InputFeedbackController` loads each sample into a SoundPool and holds the
 * handle for as long as the IME service lives. Without this revision an import
 * made in Settings stayed inaudible until the service was destroyed, and a
 * deleted sample carried on playing from the handle still loaded. Settings and
 * the IME are both alive while the preview keyboard is on screen, which is
 * exactly when a user changes these.
 */
@RunWith(AndroidJUnit4::class)
class KeypressSoundRevisionTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        KeypressSoundStore.deleteAll(context)
    }

    private fun writeSample(soundClass: KeypressSoundClass) {
        KeypressSoundStore.file(context, soundClass).apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(16) { 3 })
        }
    }

    @Test
    fun deletingAnExistingSampleAdvancesTheRevision() {
        writeSample(KeypressSoundClass.STANDARD)
        val before = KeypressSoundStore.revision.get()

        assertTrue(KeypressSoundStore.delete(context, KeypressSoundClass.STANDARD))

        assertEquals(before + 1, KeypressSoundStore.revision.get())
    }

    @Test
    fun clearingEverythingAdvancesTheRevision() {
        writeSample(KeypressSoundClass.DELETE)
        val before = KeypressSoundStore.revision.get()

        KeypressSoundStore.deleteAll(context)

        assertTrue(
            KeypressSoundStore.revision.get() > before,
            "a running keyboard has to be told the samples are gone",
        )
    }

    @Test
    fun theRevisionOnlyEverMovesForward() {
        // The controller compares against the value it last loaded, so a
        // revision that went backwards would leave it holding stale handles.
        val seen = mutableListOf<Long>()
        repeat(3) {
            writeSample(KeypressSoundClass.SPACEBAR)
            KeypressSoundStore.delete(context, KeypressSoundClass.SPACEBAR)
            seen += KeypressSoundStore.revision.get()
        }

        assertEquals(seen.sorted(), seen)
        assertEquals(seen.distinct(), seen)
    }

    @Test
    fun availableReflectsWhatIsOnDiskAfterEachMutation() {
        writeSample(KeypressSoundClass.STANDARD)
        assertEquals(setOf(KeypressSoundClass.STANDARD), KeypressSoundStore.available(context))

        KeypressSoundStore.delete(context, KeypressSoundClass.STANDARD)

        assertEquals(emptySet(), KeypressSoundStore.available(context))
    }
}
