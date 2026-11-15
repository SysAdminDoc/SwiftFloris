/*
 * Copyright (C) 2026 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.screenshot.RoborazziHostActivity
import java.io.File
import org.florisboard.lib.snygg.SnyggSinglePropertySet
import org.florisboard.lib.snygg.SnyggStylesheet
import org.florisboard.lib.snygg.ui.ProvideSnyggTheme
import org.florisboard.lib.snygg.ui.rememberSnyggTheme
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Issue #23 ("janky" resize): ProvideSnyggTheme runs again on every frame of a resize and on every shift
 * or mode change. It used to build new Material You color schemes on every run, and since a ColorScheme
 * has no structural equality every remembered style query below it ran again, restyling the whole
 * keyboard. A style whose inputs didn't change must come back as the instance already resolved.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SnyggThemeSchemeReuseTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<RoborazziHostActivity>()

    @Test
    fun recomposingTheThemeWithTheSameInputsKeepsResolvedStyles() {
        val stylesheet = loadStylesheet("floris_night.json")
        val recompositions = mutableIntStateOf(0)
        val resolved = mutableListOf<SnyggSinglePropertySet>()

        composeRule.setContent {
            val theme = rememberSnyggTheme(stylesheet)
            // Typing flips the shift state, which FlorisImeTheme passes down as a root attribute. This
            // stylesheet styles plain keys the same either way.
            val shiftState = if (recompositions.intValue == 0) "unshifted" else "shifted"
            val attributes = mapOf(
                FlorisImeUi.Attr.Mode to "characters",
                FlorisImeUi.Attr.ShiftState to shiftState,
            )
            MaterialTheme {
                ProvideSnyggTheme(
                    snyggTheme = theme,
                    dynamicAccentColor = Color(0xFF4CAF50),
                    rootAttributes = attributes,
                ) {
                    val pass = recompositions.intValue
                    val style = rememberSnyggThemeQuery(FlorisImeUi.Key.elementName)
                    SideEffect {
                        if (resolved.size == pass) resolved += style
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { recompositions.intValue = 1 }
        composeRule.waitForIdle()

        assertEquals(2, resolved.size)
        assertSame(resolved[0], resolved[1])
    }

    private fun loadStylesheet(fileName: String): SnyggStylesheet {
        val file = listOf(
            File("src/main/assets/ime/theme/org.florisboard.themes/stylesheets/$fileName"),
            File("app/src/main/assets/ime/theme/org.florisboard.themes/stylesheets/$fileName"),
        ).firstOrNull { it.exists() } ?: error("$fileName not reachable from ${File(".").absolutePath}")
        return SnyggStylesheet.fromJson(file.readText()).getOrThrow()
    }
}
