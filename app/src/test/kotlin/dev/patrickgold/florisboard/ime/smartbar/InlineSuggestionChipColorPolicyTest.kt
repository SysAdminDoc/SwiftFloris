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

import androidx.compose.ui.graphics.Color
import java.io.File
import org.florisboard.lib.snygg.SnyggStylesheet
import org.junit.Assert.assertEquals
import org.junit.Test

class InlineSuggestionChipColorPolicyTest {
    @Test
    fun `before the keyboard has drawn the night theme chip colors come from its stylesheet`() {
        // floris_night.json: inline-autofill-chip is var(--surface) on var(--on-surface),
        // defined as #424242 and #ffffff.
        val colors = InlineSuggestionChipColorPolicy.resolve(
            cached = null,
            stylesheet = load("floris_night.json"),
            isNightTheme = true,
        )
        assertEquals(Color(0xFF424242), colors.background)
        assertEquals(Color(0xFFFFFFFF), colors.foreground)
    }

    @Test
    fun `before the keyboard has drawn a custom theme keeps its own chip colors`() {
        // m3e_catppuccin_mocha.json defines --surface #313244 and --on-surface #cdd6f4.
        val colors = InlineSuggestionChipColorPolicy.resolve(
            cached = null,
            stylesheet = load("m3e_catppuccin_mocha.json"),
            isNightTheme = true,
        )
        assertEquals(Color(0xFF313244), colors.background)
        assertEquals(Color(0xFFCDD6F4), colors.foreground)
    }

    @Test
    fun `a theme that leaves the chip unstyled falls back to the day or night chip`() {
        val bare = SnyggStylesheet.fromJson(
            """{"${'$'}schema": "${SnyggStylesheet.SCHEMA_V2}"}""",
        ).getOrThrow()
        val day = InlineSuggestionChipColorPolicy.resolve(cached = null, stylesheet = bare, isNightTheme = false)
        assertEquals(Color(0xFFFFFFFF), day.background)
        assertEquals(Color(0xFF000000), day.foreground)
        val night = InlineSuggestionChipColorPolicy.resolve(cached = null, stylesheet = bare, isNightTheme = true)
        assertEquals(Color(0xFF424242), night.background)
        assertEquals(Color(0xFFFFFFFF), night.foreground)
    }

    private fun load(name: String): SnyggStylesheet {
        val file = listOf(
            File("src/main/assets/ime/theme/org.florisboard.themes/stylesheets/$name"),
            File("app/src/main/assets/ime/theme/org.florisboard.themes/stylesheets/$name"),
        ).first { it.exists() }
        return SnyggStylesheet.fromJson(file.readText()).getOrThrow()
    }
}
