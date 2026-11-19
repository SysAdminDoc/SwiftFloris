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

package dev.patrickgold.florisboard.app.settings.localization

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.AppTheme
import dev.patrickgold.florisboard.app.apptheme.FlorisAppTheme
import dev.patrickgold.florisboard.screenshot.RoborazziHostActivity
import io.kotest.matchers.shouldBe
import org.florisboard.lib.compose.FlorisDropdownLikeButton
import org.florisboard.lib.compose.ProvideLocalizedResources
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The subtype editor's language field opened its picker from the field's pressed state, never from a click.
 * On the S22 a quick tap and an accessibility click didn't open it, only a press held long enough to be seen
 * as pressed. TalkBack's double-tap and Switch Access send a click with no press at all.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xxhdpi", sdk = [35])
class FlorisDropdownLikeButtonClickTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<RoborazziHostActivity>()

    private var clicks = 0

    private fun showButton() {
        composeRule.setContent {
            Frame { FlorisDropdownLikeButton(item = "Polish", onClick = { clicks++ }) }
        }
    }

    @Test
    fun aTapOpensThePicker() {
        showButton()

        composeRule.onNodeWithText("Polish").performClick()
        composeRule.waitForIdle()

        clicks shouldBe 1
    }

    @Test
    fun anAccessibilityClickOpensThePicker() {
        showButton()

        composeRule.onNodeWithText("Polish").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        clicks shouldBe 1
    }

    @Test
    fun screenReadersHearOneDropdownWithTheValue() {
        showButton()

        composeRule.onNodeWithText("Polish")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList))
        composeRule.onAllNodesWithText("Polish").assertCountEquals(1)
    }

    @Test
    fun screenReadersHearTheErrorOnTheDropdown() {
        composeRule.setContent {
            Frame {
                FlorisDropdownLikeButton(
                    item = "- select -",
                    isError = true,
                    errorMessage = "Choose a primary language.",
                    onClick = { clicks++ },
                )
            }
        }

        composeRule.onNodeWithText("- select -")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Choose a primary language."))
    }

    @Test
    fun aDropdownWithoutAnErrorReportsNone() {
        // The subtype editor always hands over its message, so only isError may decide.
        composeRule.setContent {
            Frame {
                FlorisDropdownLikeButton(
                    item = "Polish",
                    isError = false,
                    errorMessage = "Choose a primary language.",
                    onClick = { clicks++ },
                )
            }
        }

        composeRule.onNodeWithText("Polish")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun aKeyboardReachesItInOneStepAndEnterOpensIt() {
        lateinit var focusManager: FocusManager
        composeRule.setContent {
            focusManager = LocalFocusManager.current
            Frame { FlorisDropdownLikeButton(item = "Polish", onClick = { clicks++ }) }
        }
        composeRule.runOnIdle { focusManager.moveFocus(FocusDirection.Next) }

        composeRule.onNodeWithText("Polish").assertIsFocused()
        composeRule.onNodeWithText("Polish").performKeyInput { pressKey(Key.Enter) }
        composeRule.waitForIdle()

        clicks shouldBe 1
    }
}

@Composable
private fun Frame(content: @Composable () -> Unit) {
    ProvideLocalizedResources(resourcesContext = LocalContext.current, appName = R.string.app_name) {
        FlorisAppTheme(theme = AppTheme.LIGHT) {
            Surface(color = MaterialTheme.colorScheme.background) {
                content()
            }
        }
    }
}
