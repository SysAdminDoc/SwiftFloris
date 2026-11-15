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

package dev.patrickgold.florisboard.ime.keyboard

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.screenshot.RoborazziHostActivity
import io.kotest.matchers.shouldBe
import java.io.File
import org.florisboard.lib.compose.ProvideLocalizedResources
import org.florisboard.lib.snygg.SnyggStylesheet
import org.florisboard.lib.snygg.ui.ProvideSnyggTheme
import org.florisboard.lib.snygg.ui.rememberSnyggTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Issue #24: fields inside the keyboard were text fields the system never
 * lets the keyboard type into. They are now buttons that hand the letter keys
 * to the field, with an entry bar showing what is being typed.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class InKeyboardTextEntryUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<RoborazziHostActivity>()

    @Test
    fun `tapping a field inside the keyboard starts typing into it`() {
        var taps by mutableIntStateOf(0)
        setContent {
            InKeyboardTextField(
                modifier = Modifier.width(300.dp),
                text = "",
                placeholder = "Add a tag",
                contentDescription = "Custom emoji tag",
                color = Color.White,
                fontSize = 14.sp,
                onClick = { taps++ },
            )
        }

        composeRule.onNodeWithText("Add a tag").assertExists()
        // Not an editable text field: those can't receive this keyboard's keys.
        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetText)).assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Custom emoji tag").performClick()
        composeRule.waitForIdle()

        taps shouldBe 1
    }

    @Test
    fun `the field shows the text that was typed`() {
        setContent {
            InKeyboardTextField(
                modifier = Modifier.width(300.dp),
                text = "freedom",
                placeholder = "Add a tag",
                contentDescription = "Custom emoji tag",
                color = Color.White,
                fontSize = 14.sp,
                onClick = {},
            )
        }

        composeRule.onNodeWithText("freedom").assertExists()
        composeRule.onNodeWithText("Add a tag").assertDoesNotExist()
    }

    @Test
    fun `the entry bar shows typed text and Done applies it`() {
        val entry = InKeyboardTextEntry()
        entry.begin(
            InKeyboardTextEntry.Request(
                fieldKey = InKeyboardTextEntry.EmojiSearch,
                hint = "Search emoji",
                initialText = "",
                maxLength = 40,
                returnTo = ImeUiMode.MEDIA,
            ),
        )
        entry.type("pensive")
        var done by mutableIntStateOf(0)
        var cancelled by mutableIntStateOf(0)
        var session by mutableStateOf(entry.session.value!!)
        setContent {
            InKeyboardTextEntryBar(
                session = session,
                onCancel = { cancelled++ },
                onClear = { entry.clear(); session = entry.session.value!! },
                onDone = { done++ },
            )
        }

        composeRule.onNodeWithText("pensive").assertExists()
        composeRule.onNodeWithContentDescription(string(R.string.action__done)).performClick()
        composeRule.waitForIdle()
        done shouldBe 1
        cancelled shouldBe 0

        composeRule.onNodeWithContentDescription(string(R.string.text_entry__clear)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Search emoji").assertExists()
        // Nothing left to clear.
        composeRule.onNodeWithContentDescription(string(R.string.text_entry__clear)).assertDoesNotExist()

        composeRule.onNodeWithContentDescription(string(R.string.action__back)).performClick()
        composeRule.waitForIdle()
        cancelled shouldBe 1
    }

    private fun string(id: Int): String = composeRule.activity.getString(id)

    private fun setContent(content: @Composable () -> Unit) {
        composeRule.setContent {
            ProvideLocalizedResources(
                resourcesContext = LocalContext.current,
                appName = R.string.app_name,
            ) {
                val theme = rememberSnyggTheme(remember { loadStylesheet() })
                MaterialTheme {
                    ProvideSnyggTheme(theme) {
                        content()
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun loadStylesheet(): SnyggStylesheet {
        val name = "floris_night.json"
        val file = listOf(
            File("src/main/assets/ime/theme/org.florisboard.themes/stylesheets/$name"),
            File("app/src/main/assets/ime/theme/org.florisboard.themes/stylesheets/$name"),
        ).first { it.exists() }
        return SnyggStylesheet.fromJson(file.readText()).getOrThrow()
    }
}
