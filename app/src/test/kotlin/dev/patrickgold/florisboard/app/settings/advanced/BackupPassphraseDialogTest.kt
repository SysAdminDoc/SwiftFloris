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

package dev.patrickgold.florisboard.app.settings.advanced

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.screenshot.RoborazziHostActivity
import io.kotest.matchers.shouldBe
import org.florisboard.lib.compose.ProvideLocalizedResources
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * After a wrong passphrase the restore and dictionary screens show this dialog again for the same
 * file. The error has to be read out and focus has to be back in the field, so a TalkBack or
 * keyboard user can type again without hunting for what went wrong.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BackupPassphraseDialogTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<RoborazziHostActivity>()

    private val wrongPassphrase = "That passphrase didn't open this file."
    private var confirmed: CharArray? = null

    @Test
    fun aRetryAnnouncesTheErrorAndPutsFocusBackInTheField() {
        showDialog(errorMessage = wrongPassphrase)

        composeRule.onNodeWithText(wrongPassphrase, useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Assertive))
        val field = composeRule.onNode(hasSetTextAction())
        field.assertIsFocused()
        field.assert(
            SemanticsMatcher("carries the specific error") { node ->
                node.config.getOrNull(SemanticsProperties.Error) == wrongPassphrase
            },
        )

        field.performTextInput("right one")
        composeRule.onNodeWithText("Restore").performClick()
        composeRule.waitForIdle()

        confirmed?.concatToString() shouldBe "right one"
    }

    @Test
    fun aFirstAskShowsNoError() {
        showDialog(errorMessage = null)

        composeRule.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().size shouldBe 0
        composeRule.onNode(hasSetTextAction()).assert(
            SemanticsMatcher("has no specific error") { node ->
                node.config.getOrNull(SemanticsProperties.Error) == null
            },
        )
    }

    private fun showDialog(errorMessage: String?) {
        composeRule.setContent {
            ProvideLocalizedResources(
                resourcesContext = LocalContext.current,
                appName = R.string.app_name,
            ) {
                MaterialTheme {
                    BackupPassphraseDialog(
                        title = "Encrypted backup",
                        message = "Enter the passphrase for this backup.",
                        confirmLabel = "Restore",
                        requireConfirmation = false,
                        onDismiss = {},
                        onConfirm = { confirmed = it },
                        errorMessage = errorMessage,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }
}
