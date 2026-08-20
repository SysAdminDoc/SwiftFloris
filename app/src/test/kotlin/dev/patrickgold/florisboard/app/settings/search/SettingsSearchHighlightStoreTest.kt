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

package dev.patrickgold.florisboard.app.settings.search

import dev.patrickgold.florisboard.R
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsSearchHighlightStoreTest {
    @Before
    fun setUp() {
        SettingsSearchHighlightStore.clear()
    }

    @After
    fun tearDown() {
        SettingsSearchHighlightStore.clear()
    }

    @Test
    fun searchTargetStoresResolvedLabelsForDestinationHighlight() {
        val result = SettingsSearchIndex.search("futo", ::resolve).first()

        SettingsSearchHighlightStore.mark(result.entry, " futo ", ::resolve)

        assertEquals(
            SettingsSearchTarget(
                entryId = "voice",
                screenTitle = "Voice input",
                title = "Voice input",
                summary = "FUTO setup, offline language models, and voice keyboard status",
                query = "futo",
            ),
            SettingsSearchHighlightStore.activeTarget,
        )
    }

    @Test
    fun searchTargetIsClaimedByItsMatchingPreferenceRow() {
        val result = SettingsSearchIndex.search("futo", ::resolve).first()

        SettingsSearchHighlightStore.mark(result.entry, "futo", ::resolve)

        assertNull(SettingsSearchHighlightStore.targetFor("Typing"))
        assertEquals("voice", SettingsSearchHighlightStore.activeTarget?.entryId)

        assertEquals(
            SettingsSearchTarget(
                entryId = "voice",
                screenTitle = "Voice input",
                title = "Voice input",
                summary = "FUTO setup, offline language models, and voice keyboard status",
                query = "futo",
            ),
            SettingsSearchHighlightStore.targetFor("Voice input"),
        )
        assertTrue(!SettingsSearchHighlightStore.claimTargetForRow("Voice input", "Other row"))
        assertTrue(SettingsSearchHighlightStore.claimTargetForRow("Voice input", "Voice input"))
        assertNull(SettingsSearchHighlightStore.activeTarget)
    }
}

private val highlightTestStrings = mapOf(
    R.string.settings__typing__title to "Typing",
    R.string.settings__voice_input__title to "Voice input",
    R.string.settings__home__voice_input_summary to
        "FUTO setup, offline language models, and voice keyboard status",
)

private fun resolve(resId: Int): String = highlightTestStrings[resId] ?: "res-$resId"
