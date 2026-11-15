/*
 * Copyright (C) 2026 The SwiftFloris Contributors
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
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.FlorisApplication
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.AppTheme
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.apptheme.FlorisAppTheme
import dev.patrickgold.florisboard.screenshot.RoborazziHostActivity
import dev.patrickgold.jetpref.datastore.ui.ProvideDefaultDialogPrefStrings
import org.florisboard.lib.compose.ProvideLocalizedResources
import org.florisboard.lib.compose.stringRes
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Turning the phone recreates the Back up screen. What the user picked, and the receipt of a
 * checked save, have to come back with it: a kept archive is offered again only for the
 * sections it was built from.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xxhdpi", sdk = [35])
class BackupScreenRotationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<RoborazziHostActivity>()

    @Before
    fun waitForPreferenceStore() {
        val app = composeRule.activity.application as FlorisApplication
        composeRule.waitUntil(timeoutMillis = 10_000) { app.preferenceStoreLoaded.value }
    }

    @Test
    fun pickedClipboardSectionsSurviveATurn() {
        val tester = StateRestorationTester(composeRule)
        tester.setContent { Frame { BackupScreen() } }
        val clipboard = composeRule.activity.getString(R.string.backup_and_restore__back_up__files_clipboard_history)
        // Shown exactly while clipboard sections are picked.
        val warning = composeRule.activity.getString(R.string.backup_and_restore__back_up__clipboard_privacy_warning_title)
        composeRule.onNodeWithText(warning).assertDoesNotExist()

        composeRule.onNodeWithText(clipboard).performScrollTo().performClick()
        composeRule.onNodeWithText(warning).assertExists()

        tester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText(warning).assertExists()
    }

    @Test
    fun thePickedDestinationSurvivesATurn() {
        val tester = StateRestorationTester(composeRule)
        tester.setContent { Frame { BackupScreen() } }
        val share = composeRule.activity.getString(R.string.backup_and_restore__back_up__destination_share_intent)
        val fileSys = composeRule.activity.getString(R.string.backup_and_restore__back_up__destination_file_sys)
        composeRule.onNodeWithText(fileSys).assertIsSelected()

        composeRule.onNodeWithText(share).performScrollTo().performClick()
        composeRule.onNodeWithText(share).assertIsSelected()

        tester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText(share).assertIsSelected()
        composeRule.onNodeWithText(fileSys).assertIsNotSelected()
    }

    @Test
    fun aReceiptAndItsSectionsSurviveATurn() {
        val receipt = BackupReceipt(
            archiveVersion = Backup.CURRENT_ARCHIVE_FORMAT_VERSION,
            encrypted = true,
            sections = Backup.scheduledSelection(),
            byteSize = 4096,
            sha256 = "ab".repeat(32),
        )
        val tester = StateRestorationTester(composeRule)
        var shown: BackupReceipt? = null
        tester.setContent {
            val saved by rememberSaveable { mutableStateOf<BackupReceipt?>(receipt) }
            val sections by rememberSaveable { mutableStateOf<Backup.FilesSelection?>(receipt.sections) }
            shown = saved?.copy(sections = sections ?: saved!!.sections)
        }
        shown = null

        tester.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()

        assertEquals(receipt, shown)
    }
}

@Composable
private fun Frame(content: @Composable () -> Unit) {
    ProvideLocalizedResources(resourcesContext = LocalContext.current, appName = R.string.app_name) {
        FlorisAppTheme(theme = AppTheme.LIGHT) {
            val navController = rememberNavController()
            CompositionLocalProvider(LocalNavController provides navController) {
                ProvideDefaultDialogPrefStrings(
                    confirmLabel = stringRes(R.string.action__ok),
                    dismissLabel = stringRes(R.string.action__cancel),
                    neutralLabel = stringRes(R.string.action__default),
                ) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        content()
                    }
                }
            }
        }
    }
}
