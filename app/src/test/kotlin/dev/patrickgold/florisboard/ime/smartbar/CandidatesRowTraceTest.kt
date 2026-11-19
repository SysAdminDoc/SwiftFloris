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

package dev.patrickgold.florisboard.ime.smartbar

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.nlp.WordSuggestionCandidate
import dev.patrickgold.florisboard.lib.compose.CountingTraceSink
import dev.patrickgold.florisboard.lib.compose.LocalAsyncTraceSink
import dev.patrickgold.florisboard.nlpManager
import dev.patrickgold.florisboard.screenshot.RoborazziHostActivity
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import java.io.File
import kotlinx.coroutines.runBlocking
import org.florisboard.lib.compose.ProvideLocalizedResources
import org.florisboard.lib.snygg.SnyggStylesheet
import org.florisboard.lib.snygg.ui.ProvideSnyggTheme
import org.florisboard.lib.snygg.ui.rememberSnyggTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The real candidate row, not a stand-in: its benchmark section has to begin and end on every composition. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xxhdpi", sdk = [35])
class CandidatesRowTraceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<RoborazziHostActivity>()

    @Test
    fun everyCandidateRowCompositionEndsItsSection() {
        val nlpManager by composeRule.activity.nlpManager()
        val prefs by FlorisPreferenceStore
        runBlocking {
            prefs.smartbar.enabled.set(true)
            prefs.suggestion.enabled.set(true)
        }
        nlpManager.clearSuggestions()
        val sink = CountingTraceSink()

        composeRule.setContent {
            val stylesheet = remember { loadBundledStylesheet("floris_night.json") }
            val theme = rememberSnyggTheme(stylesheet)
            CompositionLocalProvider(LocalAsyncTraceSink provides sink) {
                ProvideLocalizedResources(resourcesContext = LocalContext.current, appName = R.string.app_name) {
                    MaterialTheme {
                        ProvideSnyggTheme(theme) {
                            CandidatesRow(Modifier.size(width = 360.dp, height = 42.dp))
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        val candidates = listOf(
            WordSuggestionCandidate("hello", confidence = 0.9),
            WordSuggestionCandidate("help", confidence = 0.8),
        )
        nlpManager.suggestDirectly(candidates)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            nlpManager.activeCandidatesFlow.value.map { it.text } == candidates.map { it.text }
        }
        composeRule.waitForIdle()

        val begins = sink.events.count { it == "begin $CandidatesRowTraceSection" }
        begins shouldBeGreaterThanOrEqual 2
        sink.events.count { it == "end $CandidatesRowTraceSection" } shouldBe begins
        sink.open shouldBe emptySet()
    }

    private fun loadBundledStylesheet(fileName: String): SnyggStylesheet {
        val file = listOf(
            File("src/main/assets/ime/theme/org.florisboard.themes/stylesheets/$fileName"),
            File("app/src/main/assets/ime/theme/org.florisboard.themes/stylesheets/$fileName"),
        ).firstOrNull { it.exists() } ?: error("$fileName not reachable from ${File(".").absolutePath}")
        return SnyggStylesheet.fromJson(file.readText()).getOrThrow()
    }
}
