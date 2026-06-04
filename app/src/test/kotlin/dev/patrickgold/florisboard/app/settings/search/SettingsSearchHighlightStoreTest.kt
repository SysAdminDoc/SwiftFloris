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
import io.kotest.matchers.string.shouldContain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
    fun searchTargetIsConsumedOnceForItsMatchingDestinationScreen() {
        val result = SettingsSearchIndex.search("futo", ::resolve).first()

        SettingsSearchHighlightStore.mark(result.entry, "futo", ::resolve)

        assertNull(SettingsSearchHighlightStore.consumeTargetFor("Typing"))
        assertEquals("voice", SettingsSearchHighlightStore.activeTarget?.entryId)

        assertEquals(
            SettingsSearchTarget(
                entryId = "voice",
                screenTitle = "Voice input",
                title = "Voice input",
                summary = "FUTO setup, offline language models, and voice keyboard status",
                query = "futo",
            ),
            SettingsSearchHighlightStore.consumeTargetFor("Voice input"),
        )
        assertNull(SettingsSearchHighlightStore.activeTarget)
        assertNull(SettingsSearchHighlightStore.consumeTargetFor("Voice input"))
    }

    @Test
    fun settingsScaffoldConsumesAndDismissesSearchHighlightLocally() {
        val source = locateFlorisScreenSource().readText()

        source shouldContain "SettingsSearchHighlightStore.consumeTargetFor(title)"
        source shouldContain "displayedSearchTarget"
        source shouldContain "onDismiss = { displayedSearchTarget = null }"
        source shouldContain "SettingsSearchHighlightCard"
        source shouldContain "Icons.Default.Close"
    }
}

private val highlightTestStrings = mapOf(
    R.string.settings__typing__title to "Typing",
    R.string.settings__voice_input__title to "Voice input",
    R.string.settings__home__voice_input_summary to
        "FUTO setup, offline language models, and voice keyboard status",
)

private fun resolve(resId: Int): String = highlightTestStrings[resId] ?: "res-$resId"

private fun locateFlorisScreenSource(): File {
    val candidates = listOf(
        "app/src/main/kotlin/dev/patrickgold/florisboard/lib/compose/FlorisScreen.kt",
        "src/main/kotlin/dev/patrickgold/florisboard/lib/compose/FlorisScreen.kt",
    )
    return candidates.map(::File).firstOrNull { it.exists() && it.canRead() }
        ?: error("FlorisScreen.kt not reachable from working directory ${File(".").absolutePath}")
}
