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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.nlp.ClipboardSuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.SuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.WordSuggestionCandidate
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.screenshot.RoborazziHostActivity
import java.io.File
import org.florisboard.lib.compose.ProvideLocalizedResources
import org.florisboard.lib.snygg.SnyggStylesheet
import org.florisboard.lib.snygg.ui.ProvideSnyggTheme
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.rememberSnyggTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A suggestion too long for its third of the strip used to trail off in an ellipsis at the theme's 14 sp. It now
 * shrinks to fit, and only a word that doesn't fit even at the smallest size is ellipsized.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xxhdpi", sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CandidateTextShrinkToFitTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<RoborazziHostActivity>()

    @Test
    fun longWordShrinksToFit() {
        showStrip(words("the", LONG_WORD, "and"))

        val long = layoutOf(LONG_WORD)
        assertTrue(long.atThemeSize.isLineEllipsized(0), "precondition: $LONG_WORD must not fit its slot at the theme size")
        assertFalse(long.shown.isLineEllipsized(0), "a word that fits at a smaller size must not trail off")
        assertFalse(long.shown.hasVisualOverflow)
        assertEquals(LONG_WORD.length, long.shown.getLineEnd(0, visibleEnd = true))
    }

    @Test
    fun shortWordKeepsThemeSize() {
        showStrip(words("the", LONG_WORD, "and"))

        for (word in listOf("the", "and")) {
            val short = layoutOf(word)
            assertEquals(short.atThemeSize.lineWidth(), short.shown.lineWidth(), 0.5f, "'$word' must keep the theme size")
        }
    }

    @Test
    fun hugeWordStopsAtMinAndEllipsizes() {
        showStrip(words("the", HUGE_WORD, "and"))

        val huge = layoutOf(HUGE_WORD)
        assertTrue(huge.shown.isLineEllipsized(0), "past the smallest size the theme's ellipsis still applies")
        val shownChars = huge.shown.getLineEnd(0, visibleEnd = true)
        val themeSizeChars = huge.atThemeSize.getLineEnd(0, visibleEnd = true)
        // At half the size about twice as many letters fit before the ellipsis.
        assertTrue(shownChars >= themeSizeChars * 1.4f, "expected well over $themeSizeChars letters shown, got $shownChars")
    }

    @Test
    fun clipboardChipKeepsThemeSize() {
        val clip = ClipboardSuggestionCandidate(
            clipboardItem = ClipboardItem.text(CLIP_TEXT),
            sourceProvider = null,
            context = composeRule.activity,
        )
        showStrip(listOf(clip))

        val layout = layoutOf(CLIP_TEXT)
        assertTrue(layout.shown.isLineEllipsized(0))
        assertEquals(
            layout.atThemeSize.getLineEnd(0, visibleEnd = true),
            layout.shown.getLineEnd(0, visibleEnd = true),
        )
    }

    private fun words(vararg words: String): List<SuggestionCandidate> =
        words.map { WordSuggestionCandidate(it, confidence = 0.5) }

    private fun showStrip(candidates: List<SuggestionCandidate>) {
        composeRule.setContent {
            val stylesheet = remember { loadBundledStylesheet("floris_night.json") }
            val theme = rememberSnyggTheme(stylesheet)
            ProvideLocalizedResources(resourcesContext = LocalContext.current, appName = R.string.app_name) {
                MaterialTheme {
                    ProvideSnyggTheme(theme) {
                        SnyggBox(
                            elementName = FlorisImeUi.Smartbar.elementName,
                            modifier = Modifier.size(width = 360.dp, height = 40.dp),
                        ) {
                            CandidateStripContent(
                                candidates = candidates,
                                displayMode = CandidatesDisplayMode.CLASSIC,
                                longPressDelay = 500L,
                                onCandidateClick = { },
                                onCandidateLongPress = { false },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** What the strip drew, and the same text laid out in the same slot at the theme's fixed size. */
    private class StripText(val shown: TextLayoutResult, val atThemeSize: TextLayoutResult)

    private fun layoutOf(text: String): StripText {
        val node = composeRule.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        val shown = results.single()
        // The reported input style stays the theme's; only the drawn paragraph carries the shrunk size.
        val input = shown.layoutInput
        assertEquals(THEME_FONT_SIZE_SP, input.style.fontSize.value)
        val atThemeSize = TextMeasurer(input.fontFamilyResolver, input.density, input.layoutDirection).measure(
            text = input.text,
            style = input.style,
            overflow = input.overflow,
            softWrap = input.softWrap,
            maxLines = input.maxLines,
            constraints = input.constraints,
        )
        return StripText(shown, atThemeSize)
    }

    private fun TextLayoutResult.lineWidth(): Float = getLineRight(0) - getLineLeft(0)

    private fun loadBundledStylesheet(fileName: String): SnyggStylesheet {
        val file = listOf(
            File("src/main/assets/ime/theme/org.florisboard.themes/stylesheets/$fileName"),
            File("app/src/main/assets/ime/theme/org.florisboard.themes/stylesheets/$fileName"),
        ).firstOrNull { it.exists() } ?: error("$fileName not reachable from ${File(".").absolutePath}")
        return SnyggStylesheet.fromJson(file.readText()).getOrThrow()
    }

    private companion object {
        const val THEME_FONT_SIZE_SP = 14f
        // About 110 dp at 14 sp, against a slot of about 95 dp (the S25's are about the same).
        const val LONG_WORD = "counterrevolutionaries"
        const val HUGE_WORD = "pneumonoultramicroscopicsilicovolcanoconiosis"
        const val CLIP_TEXT = "Meet me at the north entrance after the game, I'll be wearing the blue jacket"
    }
}
