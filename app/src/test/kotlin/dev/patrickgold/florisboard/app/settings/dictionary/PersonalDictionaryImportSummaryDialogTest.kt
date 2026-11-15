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

package dev.patrickgold.florisboard.app.settings.dictionary

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.dictionary.DictionaryImportFormat
import dev.patrickgold.florisboard.ime.dictionary.PersonalDictionaryImportResult
import dev.patrickgold.florisboard.screenshot.RoborazziHostActivity
import io.kotest.matchers.shouldBe
import org.florisboard.lib.compose.ProvideLocalizedResources
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * The summary after an import offers Keep and Undo. Closing it any other way (Back, or a tap
 * outside, which reach the dialog through the same dismiss request) has to keep the words: the
 * import already happened, and only the Undo button should take it back.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PersonalDictionaryImportSummaryDialogTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<RoborazziHostActivity>()

    private var kept = 0
    private var rolledBack = 0

    @Test
    fun closingTheSummaryWithBackKeepsTheImport() {
        showSummary()

        composeRule.runOnUiThread {
            @Suppress("DEPRECATION")
            ShadowDialog.getLatestDialog().onBackPressed()
        }
        composeRule.waitForIdle()

        rolledBack shouldBe 0
        kept shouldBe 1
    }

    @Test
    fun theUndoButtonStillTakesTheImportBack() {
        showSummary()

        composeRule.onNodeWithText("Undo import").performClick()
        composeRule.waitForIdle()

        rolledBack shouldBe 1
        kept shouldBe 0
    }

    private fun showSummary() {
        val result = PersonalDictionaryImportResult(
            insertedIds = listOf(1L, 2L),
            updatedExistingCount = 0,
            skippedCount = 0,
            excludedCount = 0,
            totalParsedCount = 2,
            format = DictionaryImportFormat.JSON,
        )
        composeRule.setContent {
            ProvideLocalizedResources(
                resourcesContext = LocalContext.current,
                appName = R.string.app_name,
            ) {
                MaterialTheme {
                    PersonalDictionaryImportSummaryDialog(
                        result = result,
                        onKeep = { kept++ },
                        onRollback = { rolledBack++ },
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }
}
